/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.apache.grails.forge.cli.mcp

import java.nio.charset.StandardCharsets
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.function.BooleanSupplier

import groovy.json.JsonSlurper
import io.modelcontextprotocol.modern.McpSchema.DiscoverResult
import io.modelcontextprotocol.modern.McpSchema.Implementation
import io.modelcontextprotocol.modern.McpSchema.Tool
import io.modelcontextprotocol.modern.client.McpClient
import io.modelcontextprotocol.modern.client.McpSyncClient
import io.modelcontextprotocol.modern.client.transport.StdioMcpClientTransport
import org.apache.grails.forge.cli.GrailsCli
import org.apache.grails.forge.cli.console.CommandStreams
import org.apache.grails.forge.cli.console.ConsoleCommands
import org.grails.forge.application.ApplicationType
import org.grails.forge.cli.CommandFixture
import org.grails.forge.cli.CommandSpec
import org.grails.forge.cli.ForgeCli
import org.grails.forge.cli.ForgeContext
import org.grails.forge.cli.SpecWaits
import spock.lang.Timeout

/**
 * {@code grails mcp}, the command: how it is used, where it is refused, and a whole session of it in this
 * JVM, on standard streams that a guard keeps for it as the program {@code grails} keeps the process's own.
 */
@Timeout(600)
class McpCommandSpec extends CommandSpec implements CommandFixture {

    PrintStream originalErr = System.err
    CommandStreams.Guard guard

    void cleanup() {
        guard?.close()
        McpCommand.setTerminal(null)
        ConsoleCommands.setSession(ConsoleCommands.Session.NONE)
        System.setErr(originalErr)
        McpLeftovers.assertNoneLeft()
    }

    void "grails mcp --help shows how it is used, and the help of the command line does not list it"() {
        given:
        CommandStreams usage = CommandStreams.capture()
        CommandStreams listing = CommandStreams.capture()
        CommandStreams document = CommandStreams.capture()

        when:
        int shown = ForgeCli.execute(ForgeContext.of(beanContext), usage, '--plain-output', 'mcp', '--help')
        ForgeCli.execute(ForgeContext.of(beanContext), listing, '--plain-output', 'help')
        ForgeCli.execute(ForgeContext.of(beanContext), document, '--plain-output', 'help', '--json')

        then:
        shown == 0
        usage.standardOutput().contains('Usage: grails-forge-cli mcp')
        usage.standardOutput().contains('--directory=DIRECTORY')
        usage.standardOutput().contains('--read-only')
        usage.standardOutput().replaceAll(/\s+/, ' ').contains('Serves the commands of grails, and the tools of the project, to an AI agent over the Model Context Protocol, ' +
                'on the standard input and output. An MCP client starts it, as with: claude mcp add grails -- grails mcp')

        and: 'it is hidden, as install is'
        !listing.standardOutput().readLines().any { it.trim().startsWith('mcp ') || it.trim() == 'mcp' }
        !((List<Map>) ((Map) new JsonSlurper().parseText(document.standardOutput())).commands).any { it.name == 'mcp' }
    }

    void "grails mcp is refused at the prompt of the interactive console, with exit code 2"() {
        given:
        ConsoleCommands.setSession(ConsoleCommands.Session.CONSOLE)
        CommandStreams streams = CommandStreams.capture()

        when:
        int exit = ForgeCli.execute(ForgeContext.of(beanContext), streams, '--plain-output', 'mcp')

        then:
        exit == 2
        streams.standardError().trim() == "| Error ${McpCommand.AT_THE_PROMPT}".toString()
        streams.standardOutput() == ''
    }

    void "grails mcp is refused where the command line runs in the JVM of a program, which does not keep its standard output for it, with exit code 1"() {
        given:
        ByteArrayOutputStream said = new ByteArrayOutputStream()
        System.setErr(new PrintStream(said, true, StandardCharsets.UTF_8.name()))

        when:
        int exit = GrailsCli.execute('--plain-output', 'mcp')

        then:
        exit == 1
        said.toString(StandardCharsets.UTF_8.name()).trim() == "| Error ${McpCommand.NOT_ITS_OWN_PROCESS}".toString()
    }

    void "a directory that is not there is a mistake of the command line, with exit code 2"() {
        given:
        CommandStreams.Guard kept = CommandStreams.guard(new ByteArrayInputStream(new byte[0]), new ByteArrayOutputStream(), new PrintStream(new ByteArrayOutputStream()))
        guard = kept
        CommandStreams streams = CommandStreams.capture()

        when:
        int exit = ForgeCli.execute(ForgeContext.of(beanContext), streams, '--plain-output', 'mcp', '--directory', 'no/such/directory')

        then:
        exit == 2
        streams.standardError().contains('--directory names no directory: no/such/directory')
    }

    void "a session in this JVM serves the directory --directory names, read-only where it is asked to, says once that it is for an agent, and ends with 0 at the end of its input"() {
        given: 'a project, and the standard streams of the process as an MCP client gives them'
        File project = new File(dir, 'shop').canonicalFile
        project.mkdirs()
        File previous = dir
        dir = project
        generateProject(ApplicationType.WEB)
        dir = previous
        PipedOutputStream clientWrites = new PipedOutputStream()
        PipedInputStream serverReads = new PipedInputStream(clientWrites, 1 << 20)
        PipedOutputStream serverWrites = new PipedOutputStream()
        PipedInputStream clientReads = new PipedInputStream(serverWrites, 1 << 20)
        ByteArrayOutputStream processErr = new ByteArrayOutputStream()
        guard = CommandStreams.guard(serverReads, serverWrites, new PrintStream(processErr, true, StandardCharsets.UTF_8.name()))
        McpCommand.setTerminal({ -> true } as BooleanSupplier)

        when: 'the program grails runs mcp, and a client speaks to it'
        CompletableFuture<Integer> served = CompletableFuture.supplyAsync { GrailsCli.execute('mcp', '--directory', 'shop', '--read-only') }
        StdioMcpClientTransport transport = StdioMcpClientTransport.builder(clientReads, clientWrites).start()
        McpSyncClient client = McpClient.sync(transport).clientInfo(Implementation.builder('grails-mcp-spec', '1.0').build()).roundTimeout(SpecWaits.BOUND).build()
        DiscoverResult discovered = client.discover()
        List<Tool> tools = client.tools().list().tools()

        then: 'it serves the directory it was given, and only the tools that change nothing'
        discovered.instructions().startsWith("grails mcp is the Grails command line of one directory, ${project.path}, which is in a Grails project. ".toString())
        discovered.instructions().contains('(--read-only)')
        discovered.meta()['io.modelcontextprotocol/serverInfo'].name == 'grails'
        tools*.name().containsAll(['project_show', 'project_health', 'feature_list', 'plugin_list'])
        tools.every { Tool tool -> tool.annotations().readOnlyHint() }
        !tools*.name().contains('refresh_tools')

        and: 'it said once, on the standard error of the process, that it is for an agent'
        processErr.toString(StandardCharsets.UTF_8.name()).readLines().first() == McpCommand.TERMINAL

        when: 'the client closes its end'
        transport.close()

        then: 'the session ends with 0, and the current directory of the JVM is what it was'
        served.get(SpecWaits.BOUND.toMillis(), TimeUnit.MILLISECONDS) == 0
        System.getProperty('user.dir') == dir.absolutePath
        ConsoleCommands.getSession() == ConsoleCommands.Session.NONE
    }
}

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
package org.apache.grails.forge.cli.console

import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import groovy.json.JsonSlurper
import io.modelcontextprotocol.json.McpJsonDefaults
import io.modelcontextprotocol.modern.McpSchema
import io.modelcontextprotocol.modern.client.McpClient
import io.modelcontextprotocol.modern.client.McpSyncClient
import io.modelcontextprotocol.modern.client.transport.StdioMcpClientTransport
import io.modelcontextprotocol.modern.server.McpRequestContext
import io.modelcontextprotocol.modern.server.McpServer
import io.modelcontextprotocol.modern.server.McpSyncResponse
import io.modelcontextprotocol.modern.server.feature.McpSyncTool
import io.modelcontextprotocol.modern.server.feature.McpSyncToolRepository
import io.modelcontextprotocol.modern.server.feature.ToolsFeature
import io.modelcontextprotocol.modern.server.feature.ToolsPage
import io.modelcontextprotocol.modern.server.transport.StdioMcpTransport
import org.apache.grails.forge.cli.GrailsCli
import org.apache.grails.forge.cli.mcp.McpLeftovers
import org.grails.forge.application.ApplicationType
import org.grails.forge.cli.CommandFixture
import org.grails.forge.cli.CommandSpec
import org.grails.forge.cli.ForgeCli
import org.grails.forge.cli.ForgeContext
import org.grails.forge.cli.ForkedCli
import org.grails.forge.cli.SpecWaits
import org.grails.forge.util.VersionInfo
import spock.lang.IgnoreIf
import spock.lang.Requires
import spock.lang.Timeout
import spock.lang.Unroll

/**
 * The standard output of {@code grails mcp} is the protocol and nothing else. A session on the modern
 * server of the MCP Java SDK, in this JVM and driven by the SDK's own client, has calls that run
 * command lines in the process as {@code grails mcp} runs them, and calls that write to
 * {@code System.out} and {@code System.err} from their own thread, from a thread they start and from a
 * thread of no call: every line on the standard output is a JSON-RPC message. And the program
 * {@code grails} keeps its standard output for {@code mcp} before anything else runs, the replacement of
 * the wrapper of an earlier version of Grails among it.
 */
@IgnoreIf({ os.windows })
@Timeout(600)
class McpStdoutPuritySpec extends CommandSpec implements CommandFixture {

    // the Gradle wrapper of the project: it writes on both streams
    static final String WRAPPER = '#!/bin/sh\necho "the build writes its output"\necho "the build writes an error" >&2\n'

    PrintStream originalOut
    PrintStream originalErr
    InputStream originalIn
    ByteArrayOutputStream processErr = new ByteArrayOutputStream()
    CommandStreams.Guard guard

    void setup() {
        dir = dir.canonicalFile
        System.setProperty('user.dir', dir.absolutePath)
        originalOut = System.out
        originalErr = System.err
        originalIn = System.in
    }

    void cleanup() {
        guard?.close()
        PlainOutput.leave()
        ConsoleCommands.setSession(ConsoleCommands.Session.NONE)
        GradleBuild.setRunner(null)
        GradleBuild.setNonInteractive(false)
        GradleBuild.outputOfProcess(null, null)
        System.setOut(originalOut)
        System.setErr(originalErr)
        System.setIn(originalIn)
        McpLeftovers.assertNoneLeft()
    }

    void "a session on the SDK's modern server in this process writes only JSON-RPC messages on the standard output"() {
        given: 'a project, whose build writes on both streams'
        generateProject(ApplicationType.WEB)
        File wrapper = new File(dir, 'gradlew')
        wrapper.text = WRAPPER
        wrapper.setExecutable(true, false)
        GradleBuild.setRunner(GradleBuild.PROCESS)

        and: 'the standard streams of the process, as grails mcp is given them by its client'
        PipedOutputStream clientWrites = new PipedOutputStream()
        PipedInputStream serverReads = new PipedInputStream(clientWrites, 1 << 20)
        PipedOutputStream serverWrites = new PipedOutputStream()
        PipedInputStream clientReads = new PipedInputStream(serverWrites, 1 << 20)
        ByteArrayOutputStream stdout = new ByteArrayOutputStream()
        OutputStream standardOutput = new Recorded(stdout, serverWrites)

        and: 'a thread that runs no call, started before the server'
        ExecutorService noCall = Executors.newSingleThreadExecutor()
        noCall.submit({ } as Runnable).get()

        and: 'what grails mcp does before it serves'
        GradleBuild.outputOfProcess(System.out, System.err)
        guard = CommandStreams.guard(serverReads, standardOutput, new PrintStream(processErr, true))
        ConsoleCommands.setSession(ConsoleCommands.Session.MCP)
        PlainOutput.enter()
        ForgeContext context = ForgeContext.of(beanContext)

        and: 'a server with a tool that runs a command line, and one that writes everywhere'
        McpSchema.Tool grails = McpSchema.Tool.builder('grails', [type: 'object', properties: [arguments: [type: 'array', items: [type: 'string']]]] as Map<String, Object>).build()
        McpSchema.Tool stray = McpSchema.Tool.builder('stray', [type: 'object'] as Map<String, Object>).build()
        McpSyncTool runsGrails = McpSyncTool.of(grails, { McpRequestContext request, McpSchema.CallToolRequest call ->
            CommandStreams streams = CommandStreams.capture()
            List<String> arguments = (List<String>) call.arguments().get('arguments')
            int exit = ForgeCli.execute(context, streams, (['--plain-output', '--non-interactive'] + arguments) as String[])
            return McpSyncResponse.result(result(streams.merged(), exit != 0))
        } as McpSyncTool.Handler)
        McpSyncTool writesEverywhere = McpSyncTool.of(stray, { McpRequestContext request, McpSchema.CallToolRequest call ->
            CommandStreams streams = CommandStreams.capture()
            CommandStreams.bind(streams, {
                System.out.println('stray output of the call')
                System.err.println('stray error of the call')
                println 'stray println of the call'
                Thread started = new Thread({ System.out.println('stray output of a thread the call started') })
                started.start()
                started.join()
                noCall.submit({ System.out.println('stray output of a thread of no call') } as Runnable).get()
                return System.in.read()
            })
            return McpSyncResponse.result(result(streams.merged(), false))
        } as McpSyncTool.Handler)
        McpSyncToolRepository tools = new McpSyncToolRepository() {

            @Override
            ToolsPage list(McpRequestContext request, String cursor) {
                return ToolsPage.of([grails, stray])
            }

            @Override
            McpSyncTool find(McpRequestContext request, String name) {
                return name == 'grails' ? runsGrails : name == 'stray' ? writesEverywhere : null
            }
        }
        McpServer server = McpServer.builder()
                .serverInfo(McpSchema.Implementation.builder('grails', VersionInfo.grailsVersion).build())
                .feature(ToolsFeature.ofSync(tools))
                .build()
        StdioMcpTransport transport = new StdioMcpTransport(server, McpJsonDefaults.getMapper(), guard.takeInput(), guard.takeOutput())
        transport.start().subscribe()
        StdioMcpClientTransport clientTransport = StdioMcpClientTransport.builder(clientReads, clientWrites).start()
        McpSyncClient client = McpClient.sync(clientTransport).roundTimeout(Duration.ofSeconds(120)).build()

        when:
        client.discover()
        List<String> listed = client.listTools().list().tools()*.name()
        String strayText = text(client.tool('stray').call())
        Map<String, String> results = [
                help      : ['help', '--json'],
                show      : ['project', 'show', '--json'],
                controller: ['create-controller', 'Book'],
                build     : ['compile'],
                generator : ['create-app', 'shop'],
                refused   : ['no-such-command']
        ].collectEntries { String name, List<String> arguments -> [(name): text(client.tool('grails').arguments([arguments: arguments] as Map<String, Object>).call())] }

        then: 'every line on the standard output is a JSON-RPC message'
        List<String> lines = stdout.toString(StandardCharsets.UTF_8).readLines()
        lines.size() >= 9
        lines.every { String line -> ((Map) new JsonSlurper().parseText(line)).jsonrpc == '2.0' }
        listed == ['grails', 'stray']

        and: 'what a call wrote reached it, and what no call wrote reached the standard error of the process'
        List<String> ofTheCall = ['stray output of the call', 'stray error of the call', 'stray println of the call', 'stray output of a thread the call started']
        strayText.readLines() == ofTheCall
        processErr.toString(StandardCharsets.UTF_8).readLines().contains('stray output of a thread of no call')
        ofTheCall.every { String line -> !processErr.toString(StandardCharsets.UTF_8).contains(line) }

        and: 'the command lines wrote to their calls, the build among them from the threads that copy what it writes'
        new JsonSlurper().parseText(results.help) instanceof Map
        new JsonSlurper().parseText(results.show) instanceof Map
        results.controller.contains('BookController.groovy')
        results.build.readLines() as Set == ['the build writes its output', 'the build writes an error'] as Set
        results.generator.contains('shop')
        results.refused.contains('no-such-command')

        cleanup:
        clientTransport?.close()
        transport?.closeGracefully()?.block(SpecWaits.BOUND)
        server?.closeGracefully()
        noCall.shutdownNow()
    }

    @Unroll
    @Requires({ ForkedCli.available() })
    void "grails #arguments keeps its standard output #kept before the wrapper of an earlier version is replaced"() {
        given: 'a project of this version whose wrapper is that of Grails 8'
        File project = new File(dir, 'shop').canonicalFile
        new File(project, 'grails-app').mkdirs()
        new File(project, 'settings.gradle').text = "rootProject.name = 'shop'\n"
        new File(project, 'gradle.properties').text = "grailsVersion=${VersionInfo.grailsVersion}\n"
        UpdateWrapperCommandSpec.earlierWrapper(project, '8.1.0')
        File stdout = new File(dir, 'stdout.txt')
        File stderr = new File(dir, 'stderr.txt')

        when: 'the earlier wrapper starts the command line, with nothing on its input'
        Process process = earlierWrapperStarts(project, arguments)
                .redirectInput(ProcessBuilder.Redirect.from(new File('/dev/null')))
                .redirectOutput(stdout)
                .redirectError(stderr)
                .start()
        boolean ended = process.waitFor(SpecWaits.BOUND.toMillis(), TimeUnit.MILLISECONDS)
        String replaced = "The Grails wrapper of the project in ${project.path} was that of Grails 8.1.0 and is now that of Grails ${VersionInfo.grailsVersion}".toString()

        then: 'the wrapper was replaced'
        ended
        new File(project, 'grailsw').text != '#!/bin/sh\n# the script of an earlier version\n'

        and: 'and said so where the standard output is not kept'
        stdout.text.contains(replaced) == !guarded
        stderr.text.contains(replaced) == guarded

        and: 'the standard output of grails mcp has nothing that is no message of the protocol'
        !guarded || stdout.length() == 0

        cleanup:
        process?.destroyForcibly()

        where:
        arguments         | guarded
        ['mcp']           | true
        ['mcp', '--help'] | false

        kept = guarded ? 'for the protocol' : 'for the user, who asked for the help,'
    }

    // the JVM of the command line as the earlier wrapper starts it: the class of the command line, told that it was
    private ProcessBuilder earlierWrapperStarts(File project, List<String> arguments) {
        ProcessBuilder builder = ForkedCli.builder(project, ['-Dgrails.cli.earlierWrapper=true'])
        List<String> command = builder.command()
        // the class ForkedCli starts is replaced by the one the wrapper starts
        command.set(command.size() - 1, GrailsCli.name)
        command.addAll(arguments)
        return builder
    }

    private static McpSchema.CallToolResult result(String text, boolean error) {
        return McpSchema.CallToolResult.builder().addContent(McpSchema.TextContent.builder(text).build()).isError(error).build()
    }

    private static String text(McpSchema.CallToolResult result) {
        return ((McpSchema.TextContent) result.content().first()).text()
    }

    /** Writes what it is given to a record and on to where it goes. */
    static final class Recorded extends OutputStream {

        private final OutputStream record
        private final OutputStream to

        Recorded(OutputStream record, OutputStream to) {
            this.record = record
            this.to = to
        }

        @Override
        synchronized void write(int b) {
            record.write(b)
            to.write(b)
        }

        @Override
        synchronized void write(byte[] bytes, int offset, int length) {
            record.write(bytes, offset, length)
            to.write(bytes, offset, length)
        }

        @Override
        void flush() {
            to.flush()
        }
    }
}

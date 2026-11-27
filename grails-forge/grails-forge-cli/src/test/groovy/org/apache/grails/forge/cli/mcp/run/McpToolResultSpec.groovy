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
package org.apache.grails.forge.cli.mcp.run

import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.concurrent.TimeUnit
import java.util.function.IntConsumer

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import io.modelcontextprotocol.modern.McpSchema.CallToolResult
import io.modelcontextprotocol.modern.McpSchema.ResourceLink
import io.modelcontextprotocol.modern.McpSchema.TextContent
import io.modelcontextprotocol.modern.client.McpTimeoutException
import org.apache.grails.forge.cli.console.ConsoleCommands
import org.apache.grails.forge.cli.console.GradleBuild
import org.apache.grails.forge.cli.console.PlainOutput
import org.apache.grails.forge.cli.console.RunDirectory
import org.apache.grails.forge.cli.console.ToolCatalogFile
import org.apache.grails.forge.cli.mcp.McpNames
import org.apache.grails.forge.cli.mcp.protocol.InProcessMcp
import org.apache.grails.forge.cli.mcp.McpLeftovers
import org.grails.forge.application.ApplicationType
import org.grails.forge.cli.CommandFixture
import org.grails.forge.cli.CommandSpec
import org.grails.forge.cli.ForgeContext
import spock.lang.IgnoreIf
import spock.lang.Timeout
import spock.lang.Unroll

/**
 * The result a tool of the project gives besides what it prints ({@code executionContext.result}), over MCP:
 * the structured content of the call, with its JSON as a text block beside what the tool wrote, for a tool
 * called by its name and through {@code run-tool}. The project's Gradle wrapper is no Gradle: it writes what
 * the specification gives it where the class that runs a tool writes the result, as that class does.
 */
@IgnoreIf({ os.windows })
@Timeout(600)
class McpToolResultSpec extends CommandSpec implements CommandFixture {

    // the Gradle wrapper of the project: it writes the result it is given beside the file of arguments, and runs until it is
    // stopped where the specification asks it to
    static final String WRAPPER = '''\
        #!/bin/sh
        here="$(pwd)"
        for argument in "$@"; do
            case "$argument" in
                --args-file=*) arguments="$here/${argument#--args-file=}" ;;
            esac
        done
        number="$(basename "$arguments" .json | sed 's/^command-args-//')"
        result="$(dirname "$arguments")/tool-result-$number.json"
        if [ -e "$result" ]; then echo "a result was there before the tool ran"; fi
        if [ -f result-to-give ]; then cp result-to-give "$result"; fi
        echo "the tool ran"
        if [ -f slow ]; then
            echo $$ > build.pid
            sleep 300 &
            echo $! > child.pid
            wait
        fi
        '''.stripIndent()

    ByteArrayOutputStream processErr = new ByteArrayOutputStream()
    GrailsMcpServer server
    InProcessMcp mcp
    File run

    void setup() {
        dir = dir.canonicalFile
        System.setProperty('user.dir', dir.path)
        GradleBuild.setRunner(GradleBuild.PROCESS)
        ConsoleCommands.setSession(ConsoleCommands.Session.MCP)
        PlainOutput.enter()
        run = new File(dir, RunDirectory.LOCATION)
    }

    void cleanup() {
        System.err.print(processErr.toString(StandardCharsets.UTF_8.name()))
        mcp?.close()
        server?.close()
        PlainOutput.leave()
        ConsoleCommands.setSession(ConsoleCommands.Session.NONE)
        GradleBuild.setRunner(null)
        GradleBuild.closeConnections()
        ['build.pid', 'child.pid'].each { String name ->
            File pid = new File(dir, name)
            if (pid.isFile()) {
                ProcessHandle.of(pid.text.trim() as long).ifPresent { ProcessHandle each -> each.destroyForcibly() }
            }
        }
        McpLeftovers.assertNoneLeft()
    }

    @Unroll
    void "the result a tool sets is the structured content of its call, with its JSON as text, when the tool is called #how and its result is #what"() {
        given:
        project()
        new File(dir, 'result-to-give').text = given
        serve()

        when:
        CallToolResult result = mcp.client.tool(tool).arguments(arguments).call()
        List<String> texts = result.content().findAll { it instanceof TextContent }.collect { ((TextContent) it).text() }

        then: 'what the tool printed is the first text, and the result is not in it'
        !result.isError()
        texts[0].readLines().contains('the tool ran')
        !texts[0].contains('a result was there before the tool ran')
        !texts[0].contains(given)

        and: 'the result is the structured content, an object, and its JSON is the second text'
        texts.size() == 2
        texts[1] == json
        new JsonSlurper().parseText(texts[1]) == result.structuredContent()
        result.structuredContent() == structured

        and: 'the whole output is linked as for any call, and the command line is that of the call'
        result.content().last() instanceof ResourceLink
        ((Map<String, Object>) result.meta()[McpNames.META_RESULT]).commandLine == commandLine

        and: 'nothing of the result, or of the arguments, is left in the project'
        left() == []

        where:
        how           | tool          | arguments                                    | what                | given                                         || json                                          | structured                                             | commandLine
        'by its name' | 'count-todos' | [folder: 'grails-app']                       | 'an object'         | '{"count":3,"files":["a.groovy","b.groovy"]}' || '{"count":3,"files":["a.groovy","b.groovy"]}' | [count: 3, files: ['a.groovy', 'b.groovy']]            | ['--plain-output', '--non-interactive', 'count-todos', 'grails-app']
        'by its name' | 'count-todos' | [:]                                          | 'an array'          | '[1,2,3]'                                     || '{"result":[1,2,3]}'                          | [result: [1, 2, 3]]                                    | ['--plain-output', '--non-interactive', 'count-todos']
        'by run-tool' | 'run-tool'    | [name: 'count-todos', arg: ['grails-app']]   | 'an object'         | '{"count":0,"done":true,"note":null}'         || '{"count":0,"done":true,"note":null}'         | [count: 0, done: true, note: null]                     | ['--plain-output', '--non-interactive', 'run-tool', 'count-todos', 'grails-app']
        'by run-tool' | 'run-tool'    | [name: 'count-todos']                        | 'a string'          | '"done"'                                      || '{"result":"done"}'                           | [result: 'done']                                       | ['--plain-output', '--non-interactive', 'run-tool', 'count-todos']
    }

    void "a tool that sets no result gives what it wrote, and no structured content"() {
        given:
        project()
        serve()

        when:
        CallToolResult result = mcp.client.tool('count-todos').call()

        then:
        !result.isError()
        result.structuredContent() == null
        result.content().findAll { it instanceof TextContent }.size() == 1
        left() == []
    }

    void "a call that is cancelled after the tool set its result gives none, and leaves no file of it; the next call gives its own"() {
        given: 'a tool that sets its result and then runs until it is stopped'
        project()
        new File(dir, 'result-to-give').text = '{"stale":true}'
        new File(dir, 'slow').text = ''
        serve()

        when: 'the client gives up on the call, and cancels it'
        mcp.client.tool('count-todos').callTimeout(Duration.ofSeconds(3)).call()

        then:
        thrown(McpTimeoutException)

        and: 'the build was stopped, and neither the result nor the arguments are left in the project'
        long build = new File(dir, 'build.pid').text.trim() as long
        long child = new File(dir, 'child.pid').text.trim() as long
        eventually { !alive(build) && !alive(child) }
        eventually { mcp.server.calls.running().isEmpty() }
        eventually { left() == [] }

        when: 'the tool runs again to its end, with another result'
        new File(dir, 'slow').delete()
        new File(dir, 'result-to-give').text = '{"fresh":true}'
        CallToolResult next = mcp.client.tool('count-todos').call()

        then: 'it gives its own result, and nothing of the cancelled one'
        !next.isError()
        next.structuredContent() == [fresh: true]
        !next.content().any { it instanceof TextContent && ((TextContent) it).text().contains('stale') }
        left() == []
    }

    // a web application, whose Gradle wrapper is the script above, and whose build has listed one tool of the application
    private void project() {
        generateProject(ApplicationType.WEB)
        File wrapper = new File(dir, 'gradlew')
        wrapper.text = WRAPPER
        wrapper.setExecutable(true, false)
        File catalog = new File(dir, ToolCatalogFile.LOCATION)
        catalog.parentFile.mkdirs()
        catalog.setText(JsonOutput.toJson([format: 1, complete: true, diagnostics: [], tools: [
                [name: 'count-todos', aliases: [], description: 'Counts the TODOs', origin: 'application', target: 'application', registration: 'descriptor',
                 parameters: [[label: 'FOLDER', arity: '0..1', type: 'string', description: 'The folder to count in']]]]]), StandardCharsets.US_ASCII.name())
        // newer than every file the catalog is made from, so that the command line takes it as current
        catalog.setLastModified(System.currentTimeMillis() + 60_000)
    }

    private void serve() {
        GrailsMcpServer.Settings settings = new GrailsMcpServer.Settings()
        settings.progressEvery = Duration.ofMillis(200)
        server = new GrailsMcpServer(ForgeContext.of(beanContext), dir.toPath(), false, settings, new PrintStream(processErr, true, StandardCharsets.UTF_8.name()))
        mcp = new InProcessMcp(server)
        server.start(mcp.server.calls, { int exitCode -> } as IntConsumer)
        mcp.server.start()
    }

    // the files of results and of arguments in the directory the command line runs tools from
    private List<String> left() {
        return (run.list() ?: new String[0]).findAll { String name -> name.startsWith('tool-result-') || name.startsWith('command-args-') }
    }

    private static boolean alive(long pid) {
        return ProcessHandle.of(pid).map { ProcessHandle handle -> handle.alive }.orElse(false)
    }

    private static void eventually(Closure<Boolean> condition) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60)
        while (!condition.call()) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError('It did not happen in 60 s')
            }
            Thread.sleep(20)
        }
    }
}

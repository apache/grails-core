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
import java.util.concurrent.BlockingQueue
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import io.modelcontextprotocol.modern.McpSchema.CallToolResult
import io.modelcontextprotocol.modern.McpSchema.DiscoverResult
import io.modelcontextprotocol.modern.McpSchema.Implementation
import io.modelcontextprotocol.modern.McpSchema.TextContent
import io.modelcontextprotocol.modern.client.McpClient
import io.modelcontextprotocol.modern.client.McpSyncClient
import io.modelcontextprotocol.modern.client.McpSyncSubscriptionListener
import io.modelcontextprotocol.modern.client.SubscriptionEvent
import io.modelcontextprotocol.modern.client.transport.StdioMcpClientTransport
import org.apache.grails.forge.cli.GrailsCli
import org.apache.grails.forge.cli.console.ToolCatalogFile
import org.grails.forge.application.ApplicationType
import org.grails.forge.cli.CommandFixture
import org.grails.forge.cli.CommandSpec
import org.grails.forge.cli.ForkedCli
import org.grails.forge.cli.Handshake
import org.grails.forge.cli.SpecWaits
import spock.lang.IgnoreIf
import spock.lang.Timeout

/**
 * {@code grails mcp} as the program it is: {@code GrailsCli} in a JVM of its own, started in a project as an
 * MCP client starts it, and driven by the SDK's modern client on the process's standard input and output.
 * The project's Gradle wrapper is a script that writes down the tasks it was given, and for {@code testApp}
 * runs until it is stopped, with a process of its own below it. Every line the process writes on its standard
 * output is a message of the protocol.
 */
@IgnoreIf({ os.windows || !ForkedCli.available() })
@Timeout(600)
class McpServerModernClientSpec extends CommandSpec implements CommandFixture {

    // after the functions of the handshake: testApp runs until it is ended, with a process it started
    static final String WRAPPER = '''\
        case "$*" in
          *testApp*) task=testApp ;;
          *classes*) task=classes ;;
          *) task=other ;;
        esac
        echo "$task" >> build-calls.txt
        if [ "$task" = testApp ]; then
          hold child &
          say testApp $!
          echo "the tests run"
          wait
        fi
        echo "the build ran $task"
        '''.stripIndent()

    Process server
    StdioMcpClientTransport transport
    McpSyncClient client
    List<String> written = new CopyOnWriteArrayList<>()
    File stderr

    void setup() {
        dir = dir.canonicalFile
        System.setProperty('user.dir', dir.path)
        stderr = File.createTempFile('grails-mcp-stderr', '.txt')
    }

    void cleanup() {
        transport?.close()
        if (server != null) {
            List<ProcessHandle> below = server.descendants().toList()
            server.destroyForcibly()
            server.waitFor(SpecWaits.BOUND.toMillis(), TimeUnit.MILLISECONDS)
            below.each { ProcessHandle each -> each.destroyForcibly() }
        }
        handshake.said('testApp').each { Handshake.Step build ->
            [build.pid, build.number(0)].each { long pid -> ProcessHandle.of(pid).ifPresent { ProcessHandle each -> each.destroyForcibly() } }
        }
        stderr?.delete()
    }

    void "grails mcp serves its project as a process: it lists the tools, runs a build through the project's Gradle wrapper and a code generation in its process, and writes only the protocol"() {
        given:
        project()
        start()

        when:
        DiscoverResult discovered = client.discover()
        List<String> tools = client.tools().list().tools()*.name()
        CallToolResult compiled = client.tool('compile').call()
        CallToolResult controller = client.tool('create-controller').arguments(['controller-name': 'Book']).call()

        then: 'it is the server of the project'
        discovered.supportedVersions() == ['2026-07-28']
        discovered.meta()['io.modelcontextprotocol/serverInfo'].name == 'grails'
        discovered.instructions().contains(dir.path)
        tools.containsAll(['compile', 'test-app', 'create-controller', 'project_show', 'feature_add', 'refresh_tools'])

        and: 'compile ran the Gradle wrapper of the project with the task classes'
        !compiled.isError()
        text(compiled).contains('the build ran classes')
        calls() == ['classes']

        and: 'create-controller wrote its file'
        !controller.isError()
        new File(dir, 'grails-app/controllers/example/grails/BookController.groovy').isFile()

        and:
        protocolOnly()
    }

    void "a new catalog of the tools of the project changes the list of the tools, and the client is told"() {
        given:
        project()
        start()
        BlockingQueue<SubscriptionEvent> events = new LinkedBlockingQueue<>()
        McpSyncClient.Listening listening = client.listen().toolsChanged()
                .open({ SubscriptionEvent event -> events.put(event) } as McpSyncSubscriptionListener)
        assert next(events) instanceof SubscriptionEvent.Subscribed
        assert !client.tools().list().tools()*.name().contains('audit-report')

        when: 'the build writes the catalog of the tools of the project, as it does when it compiles the project'
        File catalog = new File(dir, ToolCatalogFile.LOCATION)
        catalog.parentFile.mkdirs()
        catalog.setText(JsonOutput.toJson([format: 1, complete: true, diagnostics: [], tools: [
                [name: 'audit-report', aliases: [], description: 'Writes the audit report', origin: 'application', target: 'application',
                 startsApplication: true, registration: 'descriptor']]]), StandardCharsets.US_ASCII.name())
        catalog.setLastModified(System.currentTimeMillis() + 60_000)

        then:
        SubscriptionEvent changed = next(events)
        changed instanceof SubscriptionEvent.ToolsListChanged
        client.tools().list().tools()*.name().contains('audit-report')

        cleanup:
        listening?.close()
    }

    void "a test-app the client gives up on is cancelled: the Gradle wrapper and the process it started are stopped"() {
        given:
        project()
        start()
        client.tools().list()

        when: 'the client gives up on it once the build has started'
        List<Throwable> failed = new CopyOnWriteArrayList<>()
        Thread call = new Thread({
            try {
                client.tool('test-app').call()
            } catch (Throwable e) {
                failed << e
            }
        })
        call.start()
        Handshake.Step build = handshake.awaitStep('testApp')
        call.interrupt()
        SpecWaits.join(call, 'the end of the cancelled call')

        then: 'the wrapper and its child are stopped, though nothing released them'
        failed*.class == [CancellationException]
        Handshake.awaitExit(build.pid)
        Handshake.awaitExit(build.number(0))

        and: 'the server goes on'
        !client.tool('compile').call().isError()
        protocolOnly()
    }

    void "the end of its input during a call ends grails mcp at once, with exit code 0, and stops the build"() {
        given:
        project()
        start()
        client.tools().list()
        CompletableFuture<CallToolResult> running = CompletableFuture.supplyAsync { client.tool('test-app').call() }
        Handshake.Step build = handshake.awaitStep('testApp')

        when: 'the client closes the standard input of the server'
        server.outputStream.close()
        int exit = SpecWaits.exited(server, 'the end of grails mcp')

        then: 'it ended, with the build that would not have ended by itself'
        exit == 0
        Handshake.awaitExit(build.pid)
        Handshake.awaitExit(build.number(0))
        protocolOnly()

        cleanup:
        running?.cancel(true)
    }

    void "a client that starts with initialize, as one of revision 2025-11-25 does, is told with the error -32022 which revision the server speaks"() {
        given:
        project()
        start(false)

        when:
        server.outputStream.write(('{"jsonrpc":"2.0","id":"legacy-1","method":"initialize","params":{"protocolVersion":"2025-11-25","capabilities":{},' +
                '"clientInfo":{"name":"legacy","version":"1.0"}}}\n').getBytes(StandardCharsets.UTF_8))
        server.outputStream.flush()
        Map<String, Object> answer = (Map<String, Object>) new JsonSlurper().parseText(new BufferedReader(new InputStreamReader(server.inputStream, StandardCharsets.UTF_8)).readLine())

        then:
        answer.id == 'legacy-1'
        answer.error.code == -32022
        answer.error.message == 'Unsupported protocol version 2025-11-25: this server does not support the initialize handshake; supported protocol versions: 2026-07-28'

        when:
        server.outputStream.close()

        then:
        SpecWaits.exited(server, 'the end of grails mcp') == 0
    }

    // a web application, whose Gradle wrapper is the script above
    private void project() {
        generateProject(ApplicationType.WEB)
        stubWrapper(dir, WRAPPER)
    }

    // grails mcp in the project, as an MCP client starts it; with the SDK's client on its streams, or none
    private void start(boolean withClient = true) {
        ProcessBuilder builder = ForkedCli.builder(dir, [])
        List<String> command = builder.command()
        // the class the program grails starts, which keeps the standard output for the protocol
        command.set(command.size() - 1, GrailsCli.name)
        command.add('mcp')
        builder.environment().put('GRAILS_GRADLE_RUNNER', 'process')
        builder.redirectError(stderr)
        server = builder.start()
        if (!withClient) {
            return
        }
        transport = StdioMcpClientTransport.builder(new Kept(server.inputStream, written), server.outputStream).start()
        client = McpClient.sync(transport).clientInfo(Implementation.builder('grails-mcp-spec', '1.0').build()).roundTimeout(SpecWaits.BOUND).build()
    }

    // every line the server wrote on its standard output is a message of JSON-RPC
    private boolean protocolOnly() {
        assert !written.isEmpty()
        written.each { String line ->
            assert ((Map) new JsonSlurper().parseText(line)).jsonrpc == '2.0': "not a message of the protocol: ${line}\n${stderr.text}"
        }
        return true
    }

    private List<String> calls() {
        File calls = new File(dir, 'build-calls.txt')
        return calls.exists() ? calls.readLines() : []
    }

    private static String text(CallToolResult result) {
        return result.content().findAll { it instanceof TextContent }.collect { ((TextContent) it).text() }.join('\n')
    }

    private static SubscriptionEvent next(BlockingQueue<SubscriptionEvent> events) {
        SubscriptionEvent event = events.poll(SpecWaits.BOUND.toMillis(), TimeUnit.MILLISECONDS)
        assert event != null: "no event of the subscription came within ${SpecWaits.boundSeconds} s"
        return event
    }

    /** What the server writes, passed on to the client, and kept line by line. */
    static final class Kept extends FilterInputStream {

        private final List<String> lines
        private final ByteArrayOutputStream line = new ByteArrayOutputStream()

        Kept(InputStream from, List<String> lines) {
            super(from)
            this.lines = lines
        }

        @Override
        int read() {
            int b = super.read()
            if (b >= 0) {
                keep(b)
            }
            return b
        }

        @Override
        int read(byte[] bytes, int offset, int length) {
            int count = super.read(bytes, offset, length)
            for (int i = offset; i < offset + count; i++) {
                keep(bytes[i])
            }
            return count
        }

        private synchronized void keep(int b) {
            if (b == ('\n' as char) as int) {
                lines << line.toString(StandardCharsets.UTF_8.name())
                line.reset()
            } else {
                line.write(b)
            }
        }
    }
}

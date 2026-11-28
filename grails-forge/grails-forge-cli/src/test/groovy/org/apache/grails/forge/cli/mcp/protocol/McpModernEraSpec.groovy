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
package org.apache.grails.forge.cli.mcp.protocol

import java.time.Duration
import java.util.concurrent.BlockingQueue
import java.util.concurrent.CancellationException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.function.Consumer

import io.modelcontextprotocol.modern.McpException as ProtocolError
import io.modelcontextprotocol.modern.McpSchema.CallToolResult
import io.modelcontextprotocol.modern.McpSchema.DiscoverResult
import io.modelcontextprotocol.modern.McpSchema.ProgressParams
import io.modelcontextprotocol.modern.McpSchema.ReadResourceResult
import io.modelcontextprotocol.modern.McpSchema.TextContent
import io.modelcontextprotocol.modern.McpSchema.TextResourceContents
import io.modelcontextprotocol.modern.McpSchema.Tool
import io.modelcontextprotocol.modern.client.McpSyncClient
import io.modelcontextprotocol.modern.client.McpSyncSubscriptionListener
import io.modelcontextprotocol.modern.client.SubscriptionEvent
import org.apache.grails.forge.cli.mcp.McpHandler
import org.apache.grails.forge.cli.mcp.McpNames
import org.grails.forge.cli.SpecWaits
import spock.lang.Specification
import spock.lang.Timeout

/**
 * {@code grails mcp} on the modern server of the MCP Java SDK, driven by the SDK's modern client in the same
 * JVM, and by lines a client that is not the SDK's would write: what the server says it is, its tools and
 * resources as the handler has them, calls with their results, errors and progress, list changes on
 * {@code subscriptions/listen}, cancellation, a line that is not JSON, and a client that starts with
 * {@code initialize}.
 */
@Timeout(600)
class McpModernEraSpec extends Specification {

    StubMcpHandler handler = new StubMcpHandler()
    InProcessMcp mcp = new InProcessMcp(handler)

    void setup() {
        mcp.server.start()
    }

    void cleanup() {
        handler.release.countDown()
        mcp.close()
    }

    void "server/discover says who the server is, that it has tools and resources whose lists change, and how it is used"() {
        when:
        DiscoverResult discovered = mcp.client.discover()

        then:
        discovered.supportedVersions() == ['2026-07-28']
        discovered.capabilities().tools().listChanged()
        discovered.capabilities().resources().listChanged()
        !discovered.capabilities().resources().subscribe()
        discovered.capabilities().prompts() == null
        discovered.capabilities().completions() == null
        discovered.instructions() == StubMcpHandler.INSTRUCTIONS
        discovered.meta()['io.modelcontextprotocol/serverInfo'] == [name: 'grails', title: 'Grails', version: '9.0.0-SPEC', websiteUrl: 'https://grails.apache.org']
    }

    void "tools/list lists the tools the handler has when it is asked, with all the handler says of each"() {
        when:
        List<Tool> tools = mcp.client.tools().list().tools()
        Tool echo = tools.find { Tool tool -> tool.name() == 'echo' }

        then:
        tools*.name() == StubMcpHandler.TOOLS
        echo.title() == 'grails echo'
        echo.description() == 'The tool echo'
        echo.inputSchema() == [type: 'object', properties: [text: [type: 'string', description: 'What is said again']], required: ['text'], additionalProperties: false]
        echo.annotations().readOnlyHint()
        echo.annotations().destructiveHint() == null
        echo.meta() == [(McpNames.META_COMMAND): [name: 'echo']]
        tools.find { Tool tool -> tool.name() == 'fail' }.annotations() == null

        when: 'the handler has other tools, and tells no one'
        handler.toolSet = StubMcpHandler.toolSet(['echo', 'fail'])

        then: 'the next list has them'
        mcp.client.tools().list().tools()*.name() == ['echo', 'fail']
    }

    void "a call runs the handler with its arguments, and is answered with what the handler gives"() {
        when:
        CallToolResult echoed = mcp.client.tool('echo').arguments([text: 'hello']).call()

        then:
        !echoed.isError()
        text(echoed) == 'echo hello'
        handler.received == [[name: 'echo', arguments: [text: 'hello']]]

        when: 'the command line fails'
        CallToolResult failed = mcp.client.tool('fail').call()

        then: 'the result is an error, and the call is not'
        failed.isError()
        text(failed) == 'it failed'

        when: 'the arguments are not what the schema of the tool allows'
        CallToolResult refused = mcp.client.tool('echo').arguments([text: 42]).call()

        then: 'the SDK refuses them with a result that is an error, and the handler is not called'
        refused.isError()
        text(refused).startsWith('Invalid arguments')
        handler.received*.name == ['echo', 'fail']
    }

    void "a tool or a resource the handler does not have is answered with the error -32602"() {
        when: 'a tool that is not listed'
        mcp.client.tool(Tool.builder('nothing', [type: 'object'] as Map<String, Object>).build()).call()

        then:
        ProtocolError notListed = thrown()
        notListed.error().code() == -32602

        when: 'a tool that is listed, but the handler says there is no such tool when it is called'
        mcp.client.tool('vanishing').call()

        then:
        ProtocolError vanished = thrown()
        vanished.error().code() == -32602
        vanished.error().message() == 'Unknown tool: vanishing'

        when:
        mcp.client.resource('grails://nothing').read()

        then:
        ProtocolError noResource = thrown()
        noResource.error().code() == -32602
        noResource.error().message() == 'Unknown resource: grails://nothing'
        noResource.error().data() == [uri: 'grails://nothing']
    }

    void "the resources and their templates are listed, and read, as the handler has them"() {
        when:
        ReadResourceResult read = mcp.client.resource('grails://commands').read()

        then:
        mcp.client.resources().list().resources()*.uri() == ['grails://commands']
        mcp.client.resources().list().resources()[0].mimeType() == 'application/json'
        mcp.client.resourceTemplates().list().resourceTemplates()*.uriTemplate() == ['grails://tools/{name}']
        read.contents().size() == 1
        TextResourceContents contents = (TextResourceContents) read.contents()[0]
        contents.uri() == 'grails://commands'
        contents.mimeType() == 'application/json'
        contents.text() == '{"commands":[]}'
    }

    void "a call that asks for progress is told of it, in order, and one that does not ask is told nothing"() {
        given:
        List<ProgressParams> progress = new CopyOnWriteArrayList<>()

        when:
        CallToolResult told = mcp.client.tool('progress').progress({ ProgressParams params -> progress << params } as Consumer<ProgressParams>).call()

        then:
        progress*.message() == ['one', 'two']
        progress*.progress() == [1.0d, 2.0d]
        progress*.progressToken().unique().size() == 1
        text(told) == "token ${progress[0].progressToken()}".toString()

        when:
        CallToolResult untold = mcp.client.tool('progress').call()

        then:
        text(untold) == 'token null'
        progress.size() == 2
    }

    void "a change of the tools or of the resources reaches each subscriptions/listen that asked for it"() {
        given:
        BlockingQueue<SubscriptionEvent> events = new LinkedBlockingQueue<>()
        McpSyncClient.Listening listening = mcp.client.listen().toolsChanged().resourcesChanged()
                .open({ SubscriptionEvent event -> events.put(event) } as McpSyncSubscriptionListener)

        when:
        SubscriptionEvent subscribed = next(events)

        then:
        subscribed instanceof SubscriptionEvent.Subscribed
        ((SubscriptionEvent.Subscribed) subscribed).missing().isEmpty()

        when:
        handler.fire(McpHandler.TOOLS)

        then:
        next(events) instanceof SubscriptionEvent.ToolsListChanged

        when:
        handler.fire(McpHandler.RESOURCES)

        then:
        next(events) instanceof SubscriptionEvent.ResourcesListChanged

        cleanup:
        listening?.close()
    }

    void "a call the client cancels is cancelled, its thread is interrupted, and nothing it gives is sent"() {
        when: 'the client gives up on the call once it runs, and cancels it'
        List<Throwable> failed = cancelledOnceStarted('block')

        then:
        failed*.class == [CancellationException]
        SpecWaits.await(handler.interrupted, 'the interrupt of the call')
        handler.cancelledWhenInterrupted

        when: 'the call has ended, and the server has answered a request after it'
        InProcessMcp.await { mcp.server.calls.running().isEmpty() }
        mcp.client.tools().list()

        then: 'what it gave was not sent, and its thread went back to the SDK without the interrupt'
        !mcp.rawLines().any { String line -> line.contains('too late') }
        !handler.callThread.isInterrupted()
    }

    void "a cancelled call that does not stop is a running call that was interrupted, until it ends or is abandoned"() {
        when: 'the client gives up on the call once it runs, and cancels it'
        List<Throwable> failed = cancelledOnceStarted('stubborn')

        then:
        failed*.class == [CancellationException]
        SpecWaits.await(handler.interrupted, 'the interrupt of the call')

        when:
        List<CallThreads.RunningCall> stuck = mcp.server.calls.interruptedFor(Duration.ZERO)

        then:
        stuck*.tool == ['stubborn']
        stuck[0].interrupted
        stuck[0].interruptedFor != null
        stuck[0].runningFor >= stuck[0].interruptedFor
        stuck[0].context.cancelled
        stuck[0].thread.is(handler.callThread)
        mcp.server.calls.interruptedFor(Duration.ofMinutes(10)).isEmpty()
        !mcp.server.calls.awaitIdle(Duration.ofMillis(10))

        when:
        boolean abandoned = mcp.server.calls.abandon(stuck[0])

        then: 'it is no longer waited for, though its thread goes on'
        abandoned
        mcp.server.calls.running().isEmpty()
        mcp.server.calls.awaitIdle(Duration.ZERO)
        handler.callThread.alive
        !mcp.server.calls.abandon(stuck[0])
    }

    void "a call that arrives once the server takes no more calls is answered with the error -32603"() {
        when:
        mcp.server.calls.stopTaking()
        mcp.client.tool('echo').arguments([text: 'late']).call()

        then:
        ProtocolError ending = thrown()
        ending.error().code() == -32603
        ending.error().message() == CallThreads.ENDING
        handler.received.isEmpty()
        !mcp.server.calls.taking
    }

    void "a line that is not JSON is answered with the error -32700, and the server reads on"() {
        when:
        mcp.send('{"jsonrpc": "2.0", "id": 1, "method": ')
        Map<String, Object> answer = mcp.awaitLine { Map<String, Object> line -> line.error != null }

        then:
        answer.id == null
        answer.error == [code: -32700, message: 'Parse error']

        and:
        mcp.client.tools().list().tools()*.name() == StubMcpHandler.TOOLS
    }

    void "a client that starts with initialize is told, with the error -32022, which revision the server speaks"() {
        when:
        mcp.send('{"jsonrpc":"2.0","id":"legacy-1","method":"initialize","params":{"protocolVersion":"2025-11-25","capabilities":{},' +
                '"clientInfo":{"name":"legacy","version":"1.0"}}}')
        Map<String, Object> answer = mcp.awaitLine { Map<String, Object> line -> line.id == 'legacy-1' }

        then:
        answer.error.code == -32022
        answer.error.message == 'Unsupported protocol version 2025-11-25: this server does not support the initialize handshake; ' +
                'supported protocol versions: 2026-07-28'
        answer.error.data == [supported: ['2026-07-28'], requested: '2025-11-25']
    }

    private static String text(CallToolResult result) {
        return ((TextContent) result.content()[0]).text()
    }

    // A call on a thread of the feature's own, cancelled by interrupting the thread once the handler runs it, as a client
    // does that cancels a call: the SDK's client then tells the server. How the call ended for the client is given.
    private List<Throwable> cancelledOnceStarted(String tool) {
        List<Throwable> failed = new CopyOnWriteArrayList<>()
        Thread call = new Thread({
            try {
                mcp.client.tool(tool).call()
            } catch (Throwable e) {
                failed << e
            }
        }, "call of ${tool}")
        call.start()
        SpecWaits.await(handler.started, 'the start of the call')
        call.interrupt()
        SpecWaits.join(call, 'the end of the cancelled call')
        return failed
    }

    private static SubscriptionEvent next(BlockingQueue<SubscriptionEvent> events) {
        SubscriptionEvent event = events.poll(SpecWaits.BOUND.toMillis(), TimeUnit.MILLISECONDS)
        assert event != null: "no event of the subscription came within ${SpecWaits.boundSeconds} s"
        return event
    }
}

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
import java.util.concurrent.Callable
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean
import groovy.transform.CompileStatic
import groovy.transform.PackageScope
import io.modelcontextprotocol.json.McpJsonDefaults
import io.modelcontextprotocol.json.McpJsonMapper
import io.modelcontextprotocol.modern.McpSchema.CacheScope
import io.modelcontextprotocol.modern.McpSchema.CallToolOutcome
import io.modelcontextprotocol.modern.McpSchema.CallToolRequest
import io.modelcontextprotocol.modern.McpSchema.CallToolResult
import io.modelcontextprotocol.modern.McpSchema.Implementation
import io.modelcontextprotocol.modern.McpSchema.ReadResourceOutcome
import io.modelcontextprotocol.modern.McpSchema.ReadResourceRequest
import io.modelcontextprotocol.modern.McpSchema.ReadResourceResult
import io.modelcontextprotocol.modern.McpSchema.Resource
import io.modelcontextprotocol.modern.McpSchema.ResourceTemplate
import io.modelcontextprotocol.modern.McpSchema.Tool
import io.modelcontextprotocol.modern.server.McpAsyncNotifier
import io.modelcontextprotocol.modern.server.McpAsyncResponse
import io.modelcontextprotocol.modern.server.McpRequestContext
import io.modelcontextprotocol.modern.server.McpServer
import io.modelcontextprotocol.modern.server.McpSyncResponse
import io.modelcontextprotocol.modern.server.feature.McpAsyncTool
import io.modelcontextprotocol.modern.server.feature.McpAsyncToolRepository
import io.modelcontextprotocol.modern.server.feature.McpChangeFeed
import io.modelcontextprotocol.modern.server.feature.McpSyncResource
import io.modelcontextprotocol.modern.server.feature.McpSyncResourceRepository
import io.modelcontextprotocol.modern.server.feature.ResourceTemplatesPage
import io.modelcontextprotocol.modern.server.feature.ResourcesFeature
import io.modelcontextprotocol.modern.server.feature.ResourcesPage
import io.modelcontextprotocol.modern.server.feature.ServerChange
import io.modelcontextprotocol.modern.server.feature.ToolsFeature
import io.modelcontextprotocol.modern.server.feature.ToolsPage
import io.modelcontextprotocol.modern.server.transport.StdioMcpTransport
import org.apache.grails.forge.cli.mcp.McpCallContext
import org.apache.grails.forge.cli.mcp.McpException
import org.apache.grails.forge.cli.mcp.McpHandler
import org.apache.grails.forge.cli.mcp.McpTool
import org.apache.grails.forge.cli.mcp.McpToolSet
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

/**
 * {@code grails mcp} on the modern server of the MCP Java SDK: the server of the protocol's revision
 * 2026-07-28, on the standard input and output it is given, serving what an {@link McpHandler} has.
 *
 * <p>The SDK does the protocol: {@code server/discover}, the lists, the calls and the reads, the
 * validation of the arguments of a call against the input schema of its tool, progress,
 * {@code subscriptions/listen}, cancellation, the answer {@code -32022} to a client that starts with
 * {@code initialize}, and the answer {@code -32700} to a line that is not JSON, after which it reads on.
 * This class only hands the SDK what the handler has and the handler what the SDK asks, and is the one
 * class that knows the SDK's API.</p>
 *
 * <ul>
 * <li>The server says it has tools and resources, both with list changes on {@code subscriptions/listen},
 * and has no prompts and no completions. Who it is, and its instructions, are what the handler says when
 * the server is made.</li>
 * <li>{@code tools/list} lists the tools the handler has at that moment. A change the handler tells of
 * reaches every {@code subscriptions/listen} that asked for it.</li>
 * <li>A call runs the handler on a thread of the SDK, as one of the {@linkplain #getCalls() running calls}.
 * Its progress goes to the client when the client asked for progress. When the client cancels it, the
 * call is cancelled and its thread is interrupted, and nothing it gives is sent.</li>
 * <li>A tool or a resource the handler does not have is answered with the error {@code -32602}, and a call
 * that arrives while the server ends with {@code -32603}.</li>
 * </ul>
 *
 * <p>It writes nothing but the protocol, and only to the output it is given.</p>
 *
 * @since 9.0
 */
@CompileStatic
final class ModernServer {

    /** How long {@link #close()} waits for the transport to have closed. */
    static final Duration CLOSE_TIMEOUT = Duration.ofSeconds(5)

    private final McpHandler handler
    private final McpJsonMapper json = McpJsonDefaults.getMapper()
    private final McpChangeFeed changes = new McpChangeFeed()
    private final CallThreads calls = new CallThreads()
    private final McpServer server
    private final StdioMcpTransport transport
    private final AtomicBoolean closed = new AtomicBoolean()

    // the tools as the SDK lists them, for the tools of the handler they were made from
    private volatile Listed listed

    /**
     * @param handler what the server serves
     * @param input where the client's messages are read from: the standard input of the process
     * @param output where the server's messages are written to: the standard output of the process, which
     * nothing else may write to
     */
    ModernServer(McpHandler handler, InputStream input, OutputStream output) {
        this.handler = Objects.requireNonNull(handler, 'handler')
        Objects.requireNonNull(input, 'input')
        Objects.requireNonNull(output, 'output')
        this.server = McpServer.builder()
                .serverInfo(json.convertValue(handler.serverInfo(), Implementation))
                .instructions(handler.instructions())
                .jsonMapper(json)
                .feature(ToolsFeature.ofAsync(new Tools(this), json, McpJsonDefaults.getSchemaValidator(), 0L, CacheScope.PRIVATE))
                .feature(ResourcesFeature.ofSync(new Resources(this), json, 0L, CacheScope.PRIVATE))
                .subscriptions(changes)
                .build()
        this.transport = new StdioMcpTransport(server, json, input, output)
        handler.onListChanged { String list ->
            if (list == McpHandler.TOOLS) {
                changes.broadcast(new ServerChange.ToolsListChanged())
            } else if (list == McpHandler.RESOURCES) {
                changes.broadcast(new ServerChange.ResourcesListChanged())
            }
        }
    }

    /**
     * @return the calls that are running
     */
    CallThreads getCalls() {
        return calls
    }

    /**
     * Starts reading the input, and serving each message on a thread of the SDK. Reading stops at the end
     * of the input, and when the server is {@linkplain #close() closed}; then every request that is being
     * answered is cancelled, and nothing more is sent. The SDK's threads do not keep the process alive.
     *
     * @return what completes when reading has stopped
     * @throws IllegalStateException when the server was started before
     */
    CompletableFuture<Void> start() {
        return transport.start().toFuture()
    }

    /**
     * Closes the server, in the order the SDK asks for: every {@code subscriptions/listen} is ended with
     * its result, and then the transport stops reading, cancels the requests it is answering without
     * sending what they give, and stops writing. Closing again does nothing.
     */
    void close() {
        if (!closed.compareAndSet(false, true)) {
            return
        }
        server.closeGracefully()
        try {
            transport.closeGracefully().block(CLOSE_TIMEOUT)
        } catch (RuntimeException ignored) {
            // not closed in time: its threads do not keep the process alive, and it writes nothing more
        }
    }

    /**
     * @return whether the server has been {@linkplain #close() closed}
     */
    boolean isClosed() {
        return closed.get()
    }

    @PackageScope
    Mono<ToolsPage> listTools(String cursor) {
        if (cursor != null) {
            return Mono.error(io.modelcontextprotocol.modern.McpException.invalidParams("Unknown cursor: ${cursor}".toString()))
        }
        return Mono.fromCallable({ -> ToolsPage.of(listed().definitions) } as Callable<ToolsPage>)
                .subscribeOn(Schedulers.boundedElastic())
    }

    @PackageScope
    Mono<McpAsyncTool> findTool(String name) {
        return Mono.fromCallable({ ->
            Tool definition = listed().byName.get(name)
            definition == null ? null : McpAsyncTool.of(definition,
                    (McpRequestContext context, CallToolRequest request) -> call(context, request))
        } as Callable<McpAsyncTool>).subscribeOn(Schedulers.boundedElastic())
    }

    @PackageScope
    List<Resource> resources() {
        return handler.listResources().collect { Map<String, Object> resource -> json.convertValue(resource, Resource) }
    }

    @PackageScope
    List<ResourceTemplate> resourceTemplates() {
        return handler.listResourceTemplates().collect { Map<String, Object> template -> json.convertValue(template, ResourceTemplate) }
    }

    @PackageScope
    ReadResourceResult read(String uri) {
        try {
            return json.convertValue(handler.readResource(uri), ReadResourceResult)
        } catch (McpException e) {
            throw protocolError(e, [uri: uri] as Map<String, Object>)
        }
    }

    private Listed listed() {
        McpToolSet tools = handler.tools()
        Listed known = listed
        if (known == null || !known.tools.is(tools)) {
            known = new Listed(tools, json)
            listed = known
        }
        return known
    }

    private Mono<McpAsyncResponse<CallToolOutcome>> call(McpRequestContext context, CallToolRequest request) {
        if (context.progressToken() == null) {
            return running(context, request, null).map((CallToolOutcome outcome) -> McpAsyncResponse.<CallToolOutcome>result(outcome))
        }
        return Mono.just(McpAsyncResponse.<CallToolOutcome>streaming((McpAsyncNotifier notifier) -> running(context, request, notifier)))
    }

    // the call on a thread of the SDK's own, which the SDK interrupts when it cancels the call
    private Mono<CallToolOutcome> running(McpRequestContext context, CallToolRequest request, McpAsyncNotifier notifier) {
        Call call = new Call(context.progressToken(), notifier)
        Object id = context.requestId()
        String name = request.name()
        Map<String, Object> arguments = request.arguments() ?: Collections.<String, Object>emptyMap()
        return Mono.fromCallable({ -> outcome(id, name, arguments, call) } as Callable<CallToolOutcome>)
                .subscribeOn(Schedulers.boundedElastic())
                // before the SDK interrupts the thread, which it does when it disposes what is above
                .doOnCancel { ->
                    call.cancel()
                    calls.interrupted(call)
                }
                .doFinally { call.end() }
    }

    private CallToolOutcome outcome(Object id, String name, Map<String, Object> arguments, Call call) {
        Map<String, Object> result
        try {
            result = calls.run(id, name, call, { -> call.cancel() } as Runnable,
                    { -> handler.callTool(name, arguments, call) } as Callable<Map<String, Object>>)
        } catch (McpException e) {
            throw protocolError(e, null)
        }
        return json.convertValue(result, CallToolResult)
    }

    private static RuntimeException protocolError(McpException e, Map<String, Object> data) {
        return data == null || e.code != McpException.INVALID_PARAMS
                ? new io.modelcontextprotocol.modern.McpException(e.code, e.message)
                : new io.modelcontextprotocol.modern.McpException(e.code, e.message, data)
    }

    // the tools of the handler as the SDK lists them, made once for each set of tools the handler has
    @CompileStatic
    private static final class Listed {

        final McpToolSet tools
        final List<Tool> definitions
        final Map<String, Tool> byName

        Listed(McpToolSet tools, McpJsonMapper json) {
            this.tools = tools
            List<Tool> definitions = []
            Map<String, Tool> byName = [:]
            tools.tools.each { McpTool tool ->
                Map<String, Object> definition = [name: tool.name, title: tool.title, description: tool.description, inputSchema: tool.inputSchema] as Map<String, Object>
                if (tool.annotations) {
                    definition.annotations = tool.annotations
                }
                if (tool.meta) {
                    definition._meta = tool.meta
                }
                Tool converted = json.convertValue(definition, Tool)
                definitions << converted
                byName.put(tool.name, converted)
            }
            this.definitions = Collections.unmodifiableList(definitions)
            this.byName = Collections.unmodifiableMap(byName)
        }
    }

    // a call, as the handler is given it
    @CompileStatic
    private static final class Call implements McpCallContext {

        private final Object progressToken
        private final McpAsyncNotifier notifier
        private final AtomicBoolean cancelled = new AtomicBoolean()

        // guarded by this: the SDK takes one notification of a call at a time, and their values increase
        private long progressCount
        private boolean ended

        Call(Object progressToken, McpAsyncNotifier notifier) {
            this.progressToken = progressToken
            this.notifier = notifier
        }

        @Override
        Object getProgressToken() {
            return progressToken
        }

        @Override
        synchronized void progress(String message) {
            if (notifier == null || ended || cancelled.get()) {
                return
            }
            progressCount++
            notifier.progress((double) progressCount, (Double) null, message).onErrorComplete().subscribe()
        }

        @Override
        boolean isCancelled() {
            return cancelled.get()
        }

        void cancel() {
            cancelled.set(true)
        }

        synchronized void end() {
            ended = true
        }
    }

    @CompileStatic
    private static final class Tools implements McpAsyncToolRepository {

        private final ModernServer server

        Tools(ModernServer server) {
            this.server = server
        }

        @Override
        Mono<ToolsPage> list(McpRequestContext context, String cursor) {
            return server.listTools(cursor)
        }

        @Override
        Mono<McpAsyncTool> find(McpRequestContext context, String name) {
            return server.findTool(name)
        }
    }

    @CompileStatic
    private static final class Resources implements McpSyncResourceRepository {

        private final ModernServer server

        Resources(ModernServer server) {
            this.server = server
        }

        @Override
        ResourcesPage list(McpRequestContext context, String cursor) {
            noCursor(cursor)
            return ResourcesPage.of(server.resources())
        }

        @Override
        ResourceTemplatesPage listTemplates(McpRequestContext context, String cursor) {
            noCursor(cursor)
            return ResourceTemplatesPage.of(server.resourceTemplates())
        }

        @Override
        McpSyncResource find(McpRequestContext context, String uri) {
            // whether there is a resource at the URI is what reading it says
            return McpSyncResource.of((McpRequestContext read, ReadResourceRequest request) ->
                    McpSyncResponse.<ReadResourceOutcome>result(server.read(uri)))
        }

        private static void noCursor(String cursor) {
            if (cursor != null) {
                throw io.modelcontextprotocol.modern.McpException.invalidParams("Unknown cursor: ${cursor}".toString())
            }
        }
    }
}

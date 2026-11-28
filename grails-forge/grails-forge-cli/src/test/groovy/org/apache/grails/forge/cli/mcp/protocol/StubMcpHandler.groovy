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

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.function.Consumer

import org.apache.grails.forge.cli.mcp.Lane
import org.apache.grails.forge.cli.mcp.McpCallContext
import org.apache.grails.forge.cli.mcp.McpException
import org.apache.grails.forge.cli.mcp.McpHandler
import org.apache.grails.forge.cli.mcp.McpNames
import org.apache.grails.forge.cli.mcp.McpTool
import org.apache.grails.forge.cli.mcp.McpToolSet

/**
 * A handler of {@code grails mcp} for the specs of the protocol: a few tools that each do one thing a
 * call can do, two resources, and lists that change when a spec says so.
 *
 * <ul>
 * <li>{@code echo} answers with its {@code text};</li>
 * <li>{@code progress} tells of its progress twice, and answers with the progress token of the call;</li>
 * <li>{@code fail} answers with a result that is an error;</li>
 * <li>{@code vanishing} is listed, but the handler says there is no such tool when it is called;</li>
 * <li>{@code block} runs until it is interrupted, and then ends;</li>
 * <li>{@code stubborn} runs until a spec {@linkplain #release releases} it, whether it is interrupted or not.</li>
 * </ul>
 */
class StubMcpHandler implements McpHandler {

    static final String INSTRUCTIONS = 'The stub of grails mcp, for the specs of the protocol'

    static final List<String> TOOLS = ['echo', 'progress', 'fail', 'vanishing', 'block', 'stubborn']

    /** The tools the handler has; a spec replaces them, and tells of it with {@link #fire} or not. */
    volatile McpToolSet toolSet = toolSet(TOOLS)

    /** What happened, in order, on whichever thread: the calls interrupted, and whatever a spec adds. */
    final List<String> events = new CopyOnWriteArrayList<>()

    /** The calls the handler ran, with their arguments. */
    final List<Map<String, Object>> received = new CopyOnWriteArrayList<>()

    final CountDownLatch started = new CountDownLatch(1)
    final CountDownLatch interrupted = new CountDownLatch(1)
    final CountDownLatch release = new CountDownLatch(1)

    volatile Thread callThread
    volatile Boolean cancelledWhenInterrupted

    private final List<Consumer<String>> listeners = new CopyOnWriteArrayList<>()

    static McpToolSet toolSet(List<String> names) {
        return new McpToolSet(names.collect { String name -> tool(name) }, [commands: []], false, names.join(','))
    }

    static McpTool tool(String name) {
        Map<String, Object> schema = name == 'echo'
                ? [type: 'object', properties: [text: [type: 'string', description: 'What is said again']], required: ['text'], additionalProperties: false]
                : [type: 'object', properties: [:], additionalProperties: false]
        Map<String, Object> annotations = name == 'echo' ? [readOnlyHint: true] : [:]
        return new McpTool(name, "grails ${name}".toString(), "The tool ${name}".toString(), schema, annotations, [(McpNames.META_COMMAND): [name: name]],
                Lane.DIRECT, new McpTool.Mapping() {
            @Override
            List<String> commandLine(Map<String, Object> arguments) throws McpException {
                return [name]
            }

            @Override
            boolean jsonResult(Map<String, Object> arguments) {
                return false
            }
        })
    }

    static Map<String, Object> text(String text) {
        return [content: [[type: 'text', text: text]], isError: false]
    }

    /** Tells the listeners that a list changed, as the server does when its tools or resources change. */
    void fire(String list) {
        listeners.each { Consumer<String> listener -> listener.accept(list) }
    }

    @Override
    Map<String, Object> serverInfo() {
        return [name: McpNames.SERVER_NAME, title: McpNames.SERVER_TITLE, version: '9.0.0-SPEC', websiteUrl: 'https://grails.apache.org']
    }

    @Override
    String instructions() {
        return INSTRUCTIONS
    }

    @Override
    McpToolSet tools() {
        return toolSet
    }

    @Override
    Map<String, Object> callTool(String name, Map<String, Object> arguments, McpCallContext context) throws McpException {
        received << [name: name, arguments: arguments]
        switch (name) {
            case 'echo':
                return text("echo ${arguments.text}".toString())
            case 'progress':
                context.progress('one')
                context.progress('two')
                return text("token ${context.progressToken}".toString())
            case 'fail':
                return [content: [[type: 'text', text: 'it failed']], isError: true]
            case 'block':
                callThread = Thread.currentThread()
                started.countDown()
                try {
                    // a latch nothing opens: the call runs until it is interrupted
                    new CountDownLatch(1).await()
                } catch (InterruptedException ignored) {
                    interruptedNow(context)
                }
                return text('too late')
            case 'stubborn':
                callThread = Thread.currentThread()
                started.countDown()
                while (true) {
                    try {
                        release.await()
                        break
                    } catch (InterruptedException ignored) {
                        interruptedNow(context)
                    }
                }
                return text('too late')
            default:
                throw new McpException(McpException.INVALID_PARAMS, "Unknown tool: ${name}".toString())
        }
    }

    @Override
    List<Map<String, Object>> listResources() {
        return [[uri: 'grails://commands', name: 'commands', title: 'The commands', description: 'The help document', mimeType: 'application/json']]
    }

    @Override
    List<Map<String, Object>> listResourceTemplates() {
        return [[uriTemplate: 'grails://tools/{name}', name: 'tool', title: 'A tool', description: 'One command or tool', mimeType: 'application/json']]
    }

    @Override
    Map<String, Object> readResource(String uri) throws McpException {
        if (uri != 'grails://commands') {
            throw new McpException(McpException.INVALID_PARAMS, "Unknown resource: ${uri}".toString())
        }
        return [contents: [[uri: uri, mimeType: 'application/json', text: '{"commands":[]}']]]
    }

    @Override
    void onListChanged(Consumer<String> listener) {
        listeners << listener
    }

    private void interruptedNow(McpCallContext context) {
        if (interrupted.count > 0) {
            cancelledWhenInterrupted = context.cancelled
            events << 'call interrupted'
            interrupted.countDown()
        }
    }
}

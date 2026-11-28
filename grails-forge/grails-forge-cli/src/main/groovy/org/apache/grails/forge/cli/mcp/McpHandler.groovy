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

import java.util.function.Consumer
import groovy.transform.CompileStatic

/**
 * What {@code grails mcp} serves, as the adapter to the protocol's library asks for it: who the server
 * is, its tools and what a call of one gives, and its resources. Every answer is made of maps, lists,
 * strings, numbers and booleans, in the shape the protocol gives the answer, so nothing here depends
 * on the library.
 *
 * @since 9.0
 */
@CompileStatic
interface McpHandler {

    /** What {@link #onListChanged} tells of a change of the tools. */
    String TOOLS = 'tools'

    /** What {@link #onListChanged} tells of a change of the resources. */
    String RESOURCES = 'resources'

    /**
     * @return who the server is: {@code name}, {@code title}, {@code version} and {@code websiteUrl}
     */
    Map<String, Object> serverInfo()

    /**
     * @return what the server tells the client of itself and how its tools are used
     */
    String instructions()

    /**
     * @return the tools the server offers now
     */
    McpToolSet tools()

    /**
     * Runs a call of a tool, on the thread that calls this, until it has ended or the thread is
     * interrupted, as it is when the client cancels the call.
     *
     * @param name the name of the tool
     * @param arguments the arguments of the call, as the client gave them
     * @param context the call, as the protocol has it
     * @return the result of the call, as the protocol's {@code CallToolResult}: {@code content},
     * {@code isError}, and where there is one {@code structuredContent} and {@code _meta}. Arguments the
     * tool cannot be given, and a command line that fails, are a result with {@code isError} {@code true}
     * @throws McpException with the code {@link McpException#INVALID_PARAMS} for a tool there is not, and
     * {@link McpException#INTERNAL_ERROR} for a call that arrives while the server ends
     */
    Map<String, Object> callTool(String name, Map<String, Object> arguments, McpCallContext context) throws McpException

    /**
     * @return the resources there are, as the protocol lists them: each with {@code uri}, {@code name},
     * {@code title}, {@code description} and {@code mimeType}
     */
    List<Map<String, Object>> listResources()

    /**
     * @return the templates of the resources there are, as the protocol lists them: each with
     * {@code uriTemplate}, {@code name}, {@code title}, {@code description} and {@code mimeType}
     */
    List<Map<String, Object>> listResourceTemplates()

    /**
     * @param uri the URI of a resource
     * @return the resource, as the protocol's {@code ReadResourceResult}: {@code contents}, each with
     * {@code uri}, {@code mimeType} and {@code text}
     * @throws McpException with the code {@link McpException#INVALID_PARAMS} for a resource there is not
     */
    Map<String, Object> readResource(String uri) throws McpException

    /**
     * Tells a listener when a list changes, with {@link #TOOLS} or {@link #RESOURCES} for which. The
     * listener can be told on any thread, and is told again for each change.
     *
     * @param listener what is told
     */
    void onListChanged(Consumer<String> listener)

}

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

import groovy.transform.CompileStatic
import groovy.transform.PackageScope

/**
 * One tool {@code grails mcp} offers: a command of the command line, or a tool of the application or
 * one of its plugins, as the protocol shows it, and how a call of it becomes a command line. It holds
 * nothing of the protocol's library: its schema, annotations and {@code _meta} are maps, lists,
 * strings, numbers and booleans, ready to be written as JSON, and none of them can be changed.
 *
 * @since 9.0
 */
@CompileStatic
final class McpTool {

    /**
     * How the arguments of a call of a tool become the command line the call runs: what makes a tool,
     * besides how the protocol shows it.
     */
    interface Mapping {

        /**
         * @param arguments the arguments of the call, as the client gave them; never {@code null}
         * @return the arguments of the command line, which come after {@code --plain-output --non-interactive}
         * @throws McpException for arguments the tool cannot be given, with a message that names the
         * argument and says what is wrong with it
         */
        List<String> commandLine(Map<String, Object> arguments) throws McpException

        /**
         * @param arguments the arguments of the call, as the client gave them
         * @return whether the command line writes a JSON document as its output for these arguments
         */
        boolean jsonResult(Map<String, Object> arguments)

    }

    /** The name of the tool. */
    final String name

    /** The title of the tool, as {@code grails create-controller}. */
    final String title

    /** What the tool does, and how it runs. */
    final String description

    /** The JSON schema of the arguments of a call. */
    final Map<String, Object> inputSchema

    /** The annotations of the tool, its hints; empty where the tool has the defaults of the protocol. */
    final Map<String, Object> annotations

    /** What the server adds to the tool for a client that reads more than the protocol has. */
    final Map<String, Object> meta

    /** The lane a call of the tool runs in. */
    final Lane lane

    private final Mapping mapping

    /**
     * @param name the name of the tool
     * @param title its title
     * @param description what it does
     * @param inputSchema the JSON schema of its arguments
     * @param annotations its hints, or {@code null} for none
     * @param meta what the server adds to it, or {@code null} for nothing
     * @param lane the lane its calls run in
     * @param mapping how the arguments of a call become a command line
     */
    McpTool(String name, String title, String description, Map<String, Object> inputSchema, Map<String, Object> annotations, Map<String, Object> meta,
            Lane lane, Mapping mapping) {
        this.name = Objects.requireNonNull(name, 'name')
        this.title = Objects.requireNonNull(title, 'title')
        this.description = Objects.requireNonNull(description, 'description')
        this.inputSchema = frozen(Objects.requireNonNull(inputSchema, 'inputSchema'))
        this.annotations = frozen(annotations ?: Collections.<String, Object>emptyMap())
        this.meta = frozen(meta ?: Collections.<String, Object>emptyMap())
        this.lane = Objects.requireNonNull(lane, 'lane')
        this.mapping = Objects.requireNonNull(mapping, 'mapping')
    }

    /**
     * @param arguments the arguments of a call, as the client gave them; {@code null} is none
     * @return the arguments of the command line the call runs, which come after
     * {@code --plain-output --non-interactive}; the list cannot be changed
     * @throws McpException for arguments the tool cannot be given, with a message that names the
     * argument and says what is wrong with it. Such arguments are a mistake of the call, which the
     * server answers with a result that is an error, and not with an error of the protocol
     */
    List<String> commandLine(Map<String, Object> arguments) throws McpException {
        return Collections.unmodifiableList(new ArrayList<String>(mapping.commandLine(arguments ?: Collections.<String, Object>emptyMap())))
    }

    /**
     * @param arguments the arguments of a call, as the client gave them; {@code null} is none
     * @return whether the command line of the call writes a JSON document as its output, which is then
     * the structured content of the result
     */
    boolean jsonResult(Map<String, Object> arguments) {
        return mapping.jsonResult(arguments ?: Collections.<String, Object>emptyMap())
    }

    @Override
    String toString() {
        return name
    }

    /**
     * @return a copy of a map that is JSON, of which nothing can be changed, the maps and lists in it neither
     */
    @PackageScope
    static Map<String, Object> frozen(Map<String, ?> map) {
        Map<String, Object> copy = new LinkedHashMap<>()
        map.each { String key, Object value -> copy.put(key, frozenValue(value)) }
        return Collections.unmodifiableMap(copy)
    }

    private static Object frozenValue(Object value) {
        if (value instanceof Map) {
            return frozen((Map<String, ?>) value)
        }
        if (value instanceof Collection) {
            return Collections.unmodifiableList(((Collection<?>) value).collect { Object each -> frozenValue(each) })
        }
        return value
    }
}

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

/**
 * The names {@code grails mcp} gives itself and what it serves: the name of the server, the keys of
 * what it adds to the {@code _meta} of a tool and of a result, the name of its own tool, and the name
 * of a tool of the protocol for a command of the command line.
 *
 * @since 9.0
 */
@CompileStatic
final class McpNames {

    /** The name of the server. */
    public static final String SERVER_NAME = 'grails'

    /** The title of the server. */
    public static final String SERVER_TITLE = 'Grails'

    /** The key of {@code _meta} of a tool with the entry {@code help --json} has for its command, without its commands. */
    public static final String META_COMMAND = 'org.apache.grails/command'

    /** The key of {@code _meta} of a tool with the command line a call of it runs. */
    public static final String META_RUN = 'org.apache.grails/run'

    /** The key of {@code _meta} of the result of a call with how the command line it ran ended. */
    public static final String META_RESULT = 'org.apache.grails/result'

    /** The tool of the server itself, which has the build list the tools of the project again. */
    public static final String REFRESH_TOOLS = 'refresh_tools'

    /** The directory below {@code .grails/run} of a project that the whole output of each call is kept in. */
    public static final String LOG_DIRECTORY = 'mcp'

    private McpNames() {
    }

    /**
     * @param commandPath the words of a command as they are typed, as {@code ['feature', 'add']}
     * @return the name of the tool of the protocol for the command: its words joined by {@code _}, as
     * {@code feature_add}; a command of one word, and a tool of the application, keep their names
     */
    static String toolName(List<String> commandPath) {
        if (commandPath == null || commandPath.isEmpty()) {
            throw new IllegalArgumentException('A command has at least one word')
        }
        return commandPath.join('_')
    }
}

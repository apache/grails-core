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
package org.apache.grails.forge.cli.mcp.tools

import groovy.json.JsonOutput
import groovy.transform.CompileStatic
import org.apache.grails.forge.cli.mcp.McpException
import org.apache.grails.forge.cli.mcp.McpNames
import org.apache.grails.forge.cli.mcp.McpTool
import org.apache.grails.forge.cli.mcp.McpToolSet

/**
 * The resources of {@code grails mcp}, each a JSON document:
 * <ul>
 * <li>{@code grails://commands}, the document of {@code help --json} of the directory, with
 * {@code complete} and the {@code diagnostics}, which also say why a command or tool is not offered;</li>
 * <li>{@code grails://tools/{name}}, a template: the entry of the command or tool of one tool, and how
 * the server runs it;</li>
 * <li>in a project, {@code grails://project} and {@code grails://project/health}, what
 * {@code project show --json} and {@code project health --json} write.</li>
 * </ul>
 * The first two are read from the tool set. The two of the project are written by their commands, which
 * the server runs when they are read, through {@link Documents}. The whole output of a call, in
 * {@code .grails/run/mcp}, is a resource too, which the result of the call links and nothing lists: the
 * server reads it itself.
 *
 * @since 9.0
 */
@CompileStatic
final class McpResources {

    /** The document of {@code help --json}. */
    public static final String COMMANDS = 'grails://commands'

    /** What the URI of one tool starts with, before its name. */
    public static final String TOOL_PREFIX = 'grails://tools/'

    /** The template of the URI of one tool. */
    public static final String TOOL_TEMPLATE = TOOL_PREFIX + '{name}'

    /** What {@code project show --json} writes. */
    public static final String PROJECT = 'grails://project'

    /** What {@code project health --json} writes. */
    public static final String PROJECT_HEALTH = 'grails://project/health'

    /** The media type of every resource. */
    public static final String MIME_TYPE = 'application/json'

    /** The command line, after the options in front of a command, whose output is {@link #PROJECT}. */
    public static final List<String> PROJECT_COMMAND = ['project', 'show', CommandPolicy.JSON].asImmutable()

    /** The command line, after the options in front of a command, whose output is {@link #PROJECT_HEALTH}. */
    public static final List<String> PROJECT_HEALTH_COMMAND = ['project', 'health', CommandPolicy.JSON].asImmutable()

    /**
     * Runs a command line that writes one JSON document, for a resource that is read: in the server's
     * process, in the lane of the calls that change nothing, as a call of the command is run.
     */
    interface Documents {

        /**
         * @param commandLine the arguments after {@code --plain-output --non-interactive}
         * @return what the command wrote on its standard output, which is the document whatever its exit code
         * @throws McpException when it wrote no document
         */
        String read(List<String> commandLine) throws McpException

    }

    private McpResources() {
    }

    /**
     * @param tools the tools of the directory
     * @return the resources, as the protocol lists them: {@code grails://commands}, and in a project
     * {@code grails://project} and {@code grails://project/health}
     */
    static List<Map<String, Object>> list(McpToolSet tools) {
        List<Map<String, Object>> resources = [resource(COMMANDS, 'commands', 'The commands of grails',
                'The document of help --json of the directory: every command of grails and every tool of the project, with their options and parameters, ' +
                        'whether the list is complete, and diagnostics that say why a command or tool is not offered.')]
        if (tools.project) {
            resources.add(resource(PROJECT, 'project', 'The project',
                    'What project show --json writes: how the application of the directory was generated, its type, options and features.'))
            resources.add(resource(PROJECT_HEALTH, 'project-health', 'The health of the project',
                    'What project health --json writes: what makes the build of the project fail, and what the upgrade notes ask for.'))
        }
        return resources
    }

    /**
     * @return the templates of the resources, as the protocol lists them: {@code grails://tools/{name}}
     */
    static List<Map<String, Object>> templates() {
        Map<String, Object> template = new LinkedHashMap<>()
        template.put('uriTemplate', TOOL_TEMPLATE)
        template.put('name', 'tool')
        template.put('title', 'A tool of grails mcp')
        template.put('description', 'The entry help --json has for the command or tool of a tool, and how grails mcp runs it: the command line every call starts with, ' +
                'its lane, and whether its output is JSON.')
        template.put('mimeType', MIME_TYPE)
        return [template]
    }

    /**
     * Reads a resource.
     *
     * @param tools the tools of the directory
     * @param uri the URI of the resource
     * @param documents what runs the commands the resources of a project are written by
     * @return the resource, as the protocol's {@code ReadResourceResult}: {@code contents}, one with
     * {@code uri}, {@code mimeType} and {@code text}
     * @throws McpException with the code {@link McpException#INVALID_PARAMS} for a resource there is not,
     * and as {@link Documents#read} throws it
     */
    static Map<String, Object> read(McpToolSet tools, String uri, Documents documents) throws McpException {
        String text
        if (uri == COMMANDS) {
            text = JsonOutput.toJson(tools.helpDocument)
        } else if (uri != null && uri.startsWith(TOOL_PREFIX)) {
            String name = uri.substring(TOOL_PREFIX.length())
            McpTool tool = tools.tools.find { McpTool each -> each.name == name }
            if (tool == null) {
                throw unknown(uri)
            }
            text = JsonOutput.toJson(described(tool))
        } else if (tools.project && uri == PROJECT) {
            text = documents.read(PROJECT_COMMAND)
        } else if (tools.project && uri == PROJECT_HEALTH) {
            text = documents.read(PROJECT_HEALTH_COMMAND)
        } else {
            throw unknown(uri)
        }
        Map<String, Object> content = new LinkedHashMap<>()
        content.put('uri', uri)
        content.put('mimeType', MIME_TYPE)
        content.put('text', text)
        Map<String, Object> result = new LinkedHashMap<>()
        result.put('contents', [content])
        return result
    }

    /**
     * @param tool a tool
     * @return what {@code grails://tools/{name}} says of it: its name, the entry {@code help --json} has
     * for its command or tool, where it has one, and how the server runs it
     */
    static Map<String, Object> described(McpTool tool) {
        Map<String, Object> described = new LinkedHashMap<>()
        described.put('name', tool.name)
        if (tool.meta.containsKey(McpNames.META_COMMAND)) {
            described.put('command', tool.meta.get(McpNames.META_COMMAND))
        }
        described.put('run', tool.meta.get(McpNames.META_RUN))
        return described
    }

    private static Map<String, Object> resource(String uri, String name, String title, String description) {
        Map<String, Object> resource = new LinkedHashMap<>()
        resource.put('uri', uri)
        resource.put('name', name)
        resource.put('title', title)
        resource.put('description', description)
        resource.put('mimeType', MIME_TYPE)
        return resource
    }

    private static McpException unknown(String uri) {
        return new McpException(McpException.INVALID_PARAMS, "There is no resource ${uri}".toString())
    }
}

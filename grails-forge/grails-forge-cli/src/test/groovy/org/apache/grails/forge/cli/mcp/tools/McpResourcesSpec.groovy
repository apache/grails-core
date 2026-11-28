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

import groovy.json.JsonSlurper
import org.apache.grails.forge.cli.mcp.McpException
import org.apache.grails.forge.cli.mcp.McpNames
import org.apache.grails.forge.cli.mcp.McpToolSet
import spock.lang.Specification

/**
 * The resources of {@code grails mcp}: the document of {@code help --json}, one tool, and in a project
 * what {@code project show --json} and {@code project health --json} write, read through what runs those
 * commands.
 */
class McpResourcesSpec extends Specification {

    List<List<String>> ran = []

    McpResources.Documents documents = new McpResources.Documents() {
        @Override
        String read(List<String> commandLine) throws McpException {
            ran << commandLine
            return '{"ran":"' + commandLine.join(' ') + '"}'
        }
    }

    void "outside a project only the help document is listed, and in one the project and its health as well"() {
        expect:
        McpResources.list(McpToolSamples.tools('outside-project'))*.uri == [McpResources.COMMANDS]
        McpResources.list(McpToolSamples.tools('web-application'))*.uri == [McpResources.COMMANDS, McpResources.PROJECT, McpResources.PROJECT_HEALTH]
        McpResources.list(McpToolSamples.tools('web-application')).every { it.keySet() == ['uri', 'name', 'title', 'description', 'mimeType'] as Set && it.mimeType == 'application/json' }

        and:
        McpResources.templates() == [[uriTemplate: 'grails://tools/{name}', name: 'tool', title: 'A tool of grails mcp',
                                      description: McpResources.templates()[0].description, mimeType: 'application/json']]
    }

    void "grails://commands is the help document, with the diagnostics of what is not offered"() {
        given:
        McpToolSet tools = McpToolSamples.tools('application-with-tools')

        when:
        Map<String, Object> read = McpResources.read(tools, 'grails://commands', documents)
        Map<String, Object> content = ((List<Map<String, Object>>) read.contents)[0]
        Map<String, Object> document = (Map<String, Object>) new JsonSlurper().parseText((String) content.text)

        then:
        content.uri == 'grails://commands'
        content.mimeType == 'application/json'
        document.format == 1
        document.complete
        ((List) document.commands).size() == ((List) McpToolSamples.sample('application-with-tools').commands).size()
        ((List<String>) document.diagnostics).any { it.startsWith('The tool import-data is not offered') }
        ran.isEmpty()
    }

    void "grails://tools/NAME is the entry of the command of a tool and how the server runs it"() {
        given:
        McpToolSet tools = McpToolSamples.tools('application-with-tools')

        when:
        Map<String, Object> described = (Map<String, Object>) new JsonSlurper().parseText((String) ((List<Map>) McpResources.read(tools, 'grails://tools/run-app', documents).contents)[0].text)

        then:
        described.name == 'run-app'
        described.command.name == 'run-app'
        described.command.origin == 'built-in'

        and: 'the whole entry of the command, which the list of the tools does not repeat'
        described.command.description == ToolProjection.entry(tools.helpDocument, 'run-app').description
        ((List<Map>) described.command.options)*.names.flatten().contains('--port')
        !((Map) tools.tools.find { it.name == 'run-app' }.meta[McpNames.META_COMMAND]).containsKey('options')
        described.run == [commandLine: ['--plain-output', '--non-interactive', 'run-app', '--detach'], arguments: 'declared', lane: 'build', json: 'never',
                          leftOut: ['--detach', '--foreground', '--debug-jvm']]

        when: 'the tool of the server has no command'
        Map<String, Object> refresh = (Map<String, Object>) new JsonSlurper().parseText((String) ((List<Map>) McpResources.read(tools, 'grails://tools/refresh_tools', documents).contents)[0].text)

        then:
        refresh.keySet() == ['name', 'run'] as Set
        refresh.run.commandLine == ['--plain-output', '--non-interactive', 'gradle', 'grailsToolCatalog', '--rerun']
        McpResources.described(tools, tools.tools.find { it.name == McpNames.REFRESH_TOOLS }).name == McpNames.REFRESH_TOOLS
    }

    void "the resources of a project are what their commands write, and are read only in a project"() {
        when:
        Map<String, Object> project = McpResources.read(McpToolSamples.tools('web-application'), 'grails://project', documents)
        Map<String, Object> health = McpResources.read(McpToolSamples.tools('web-application'), 'grails://project/health', documents)

        then:
        ((List<Map>) project.contents)[0].text == '{"ran":"project show --json"}'
        ((List<Map>) health.contents)[0].text == '{"ran":"project health --json"}'
        ran == [McpResources.PROJECT_COMMAND, McpResources.PROJECT_HEALTH_COMMAND]

        when:
        McpResources.read(McpToolSamples.tools('outside-project'), 'grails://project', documents)

        then:
        McpException e = thrown()
        e.code == McpException.INVALID_PARAMS
        e.message == 'There is no resource grails://project'
        ran.size() == 2
    }

    void "a resource there is not is an error of the request"() {
        when:
        McpResources.read(McpToolSamples.tools('application-with-tools'), uri, documents)

        then:
        McpException e = thrown()
        e.code == McpException.INVALID_PARAMS
        e.message == "There is no resource ${uri}".toString()

        where:
        uri << ['grails://tools/import-data', 'grails://tools/gradle', 'grails://tools/', 'grails://nothing', 'file:///etc/passwd', null]
    }
}

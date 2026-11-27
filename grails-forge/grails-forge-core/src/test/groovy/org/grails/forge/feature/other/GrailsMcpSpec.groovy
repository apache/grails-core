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
package org.grails.forge.feature.other

import java.util.regex.Matcher

import groovy.json.JsonSlurper
import org.grails.forge.ApplicationContextSpec
import org.grails.forge.application.ApplicationType
import org.grails.forge.feature.Category
import org.grails.forge.feature.Feature
import org.grails.forge.fixture.CommandOutputFixture
import org.grails.forge.util.VersionInfo

/**
 * The feature {@code grails-mcp}: an application that asks for it has a {@code .mcp.json} that registers
 * {@code ./grailsw mcp} as its MCP server, and a paragraph in its README that gives the entry for Windows and
 * the place of the file for Cursor. No application has it without asking for it.
 */
class GrailsMcpSpec extends ApplicationContextSpec implements CommandOutputFixture {

    // what an MCP client reads: the server named grails, started as the Grails wrapper with the command mcp
    static final Map<String, Object> SERVERS = [mcpServers: [grails: [command: './grailsw', args: ['mcp']]]]

    void "grails-mcp is a development tool that says in one line what it does"() {
        when:
        Feature feature = getFeatures(['grails-mcp']).getFeatures().find { Feature each -> each.name == 'grails-mcp' }

        then:
        feature != null
        feature.category == Category.DEV_TOOLS
        feature.visible
        feature.description == 'Writes .mcp.json, which registers ./grailsw mcp as the MCP server of the project for AI agents.'
        feature.documentation == "https://grails.apache.org/docs/${VersionInfo.documentationVersion}/guide/commandLine.html#mcp".toString()
    }

    void "an application of the kind #applicationType with grails-mcp registers the Grails wrapper as its MCP server in .mcp.json"(ApplicationType applicationType) {
        when:
        Map<String, String> output = generate(applicationType, ['grails-mcp'])

        then: 'the file is JSON with the one server, which is the wrapper the application has'
        new JsonSlurper().parseText(output['.mcp.json']) == SERVERS
        output.containsKey('grailsw')

        where:
        applicationType << ApplicationType.values()
    }

    void "the README of an application with grails-mcp gives the entry for Windows and the file Cursor reads"() {
        when:
        String readme = generate(ApplicationType.WEB, ['grails-mcp'])['README.md']
        Matcher windows = readme =~ /`("command": "cmd", "args": \[[^\]]*\])`/

        then: 'the entry for Windows is the server of .mcp.json, started by the batch file of the wrapper'
        readme.contains('`.mcp.json` registers `./grailsw mcp`')
        windows.find()
        new JsonSlurper().parseText("{${windows.group(1)}}") == [command: 'cmd', args: ['/c', 'grailsw.bat', 'mcp']]

        and:
        readme.contains('Cursor reads the same entry from `.cursor/mcp.json`')
    }

    void "an application of the kind #applicationType has no .mcp.json unless it asks for grails-mcp"(ApplicationType applicationType) {
        when:
        Map<String, String> output = generate(applicationType)

        then:
        !output.containsKey('.mcp.json')
        !(output['README.md'] ?: '').contains('grailsw mcp')

        where:
        applicationType << ApplicationType.values()
    }
}

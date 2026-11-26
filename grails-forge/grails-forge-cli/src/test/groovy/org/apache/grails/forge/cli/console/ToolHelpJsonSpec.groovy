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
package org.apache.grails.forge.cli.console

import java.nio.charset.StandardCharsets
import groovy.json.JsonSlurper
import org.grails.forge.application.ApplicationType
import org.grails.forge.cli.CommandFixture
import org.grails.forge.cli.CommandSpec
import org.grails.forge.cli.ForgeCli

/**
 * What a tool that reads {@code help --json}, as an IDE does, is given of the tools of a project and of
 * the commands that create and run them: every tool is in the one array {@code commands}, with its
 * target, {@code application} or {@code standalone} and never {@code gradle}, whether it starts the
 * application, how it is registered, and what of it is deprecated where something is.
 */
class ToolHelpJsonSpec extends CommandSpec implements CommandFixture {

    static final Map APPLICATION_TOOL = [name: 'report', aliases: ['rep'], description: 'Reports', origin: 'application', target: 'application', startsApplication: true,
                                         registration: 'descriptor']
    static final Map WITHOUT_APPLICATION = [name: 'query-tail', aliases: [], description: 'Tails the queries', origin: 'org.apache.grails:grails-datamapping-core-cli:9.0.0',
                                            target: 'application', startsApplication: false, registration: 'descriptor']
    static final Map STANDALONE = [name: 'generate-all', aliases: [], description: 'Generates', origin: 'org.apache.grails:grails-scaffolding-cli-standalone:9.0.0',
                                   target: 'standalone', registration: 'descriptor']
    static final Map DEPRECATED = [name: 'old-report', aliases: [], description: 'Written for Grails 8', origin: 'application', target: 'application', registration: 'factories',
                                   deprecated: ['type', 'folder']]

    PrintStream originalOut
    PrintStream originalErr
    ByteArrayOutputStream out = new ByteArrayOutputStream()
    ByteArrayOutputStream err = new ByteArrayOutputStream()

    void setup() {
        originalOut = System.out
        originalErr = System.err
        System.setOut(new PrintStream(out, true))
        System.setErr(new PrintStream(err, true))
    }

    void cleanup() {
        System.setOut(originalOut)
        System.setErr(originalErr)
    }

    void "every tool has the target application or standalone, whether it starts the application, its registration, and what of it is deprecated"() {
        given: 'a project with a tool of each kind, one that is deprecated, and a script'
        new File(dir, 'grails-app').mkdirs()
        File catalog = new File(dir, ToolCatalogFile.LOCATION)
        catalog.parentFile.mkdirs()
        catalog.bytes = ProjectToolsSpec.doc([APPLICATION_TOOL, WITHOUT_APPLICATION, STANDALONE, DEPRECATED]).getBytes(StandardCharsets.UTF_8)
        File script = new File(dir, 'src/main/scripts/clean-cache.groovy')
        script.parentFile.mkdirs()
        script.text = ''

        when:
        Map document = document()
        Map<String, Map> tools = ((List<Map>) document.commands).findAll { it.project && it.origin != 'built-in' }.collectEntries { [(it.name): it] }

        then: 'the members, in their order, the last only where something is deprecated'
        document.diagnostics == []
        tools.keySet() == ['clean-cache', 'generate-all', 'old-report', 'query-tail', 'report'] as Set
        tools.report.keySet() as List == ['name', 'aliases', 'description', 'project', 'origin', 'target', 'startsApplication', 'registration', 'commands']
        tools['old-report'].keySet() as List == ['name', 'aliases', 'description', 'project', 'origin', 'target', 'startsApplication', 'registration', 'deprecated', 'commands']

        and: '1. the targets are application and standalone'
        tools.values()*.target as Set == ['application', 'standalone'] as Set

        and: '2. every tool says whether it starts the application, a script too'
        tools.collectEntries { String name, Map tool -> [(name): [tool.target, tool.startsApplication]] } == [
                'report'      : ['application', true],
                'query-tail'  : ['application', false],
                'generate-all': ['standalone', false],
                'old-report'  : ['application', true],
                'clean-cache' : ['application', false]]

        and: '3. what is deprecated'
        tools['old-report'].deprecated == ['type', 'folder']

        and: '5. the origins as the build says them, and the registrations there are'
        tools.collectEntries { String name, Map tool -> [(name): [tool.origin, tool.registration]] } == [
                'report'      : ['application', 'descriptor'],
                'query-tail'  : ['org.apache.grails:grails-datamapping-core-cli:9.0.0', 'descriptor'],
                'generate-all': ['org.apache.grails:grails-scaffolding-cli-standalone:9.0.0', 'descriptor'],
                'old-report'  : ['application', 'factories'],
                'clean-cache' : ['application', 'script']]
        tools.report.aliases == ['rep']
        tools.values().every { it.commands == [] }
    }

    void "4. the commands that create and run tools are those of the command line, create-command is gone, and run-command is another name of run-tool"() {
        given: 'a generated application'
        generateProject(ApplicationType.WEB)

        when:
        Map<String, Map> commands = ((List<Map>) document().commands).collectEntries { [(it.name): it] }

        then:
        commands['create-tool'].origin == 'built-in'
        commands['create-tool'].project == true
        ((List<Map>) commands['create-tool'].options)*.names.flatten().contains('--standalone')
        ((List<Map>) commands['create-tool'].parameters)*.label == ['TOOL-NAME']
        commands['run-tool'].origin == 'built-in'
        commands['run-tool'].aliases == ['run-command']
        !commands.containsKey('create-command')
        !commands.containsKey('run-command')
    }

    void "6. a tool that reads the catalog itself finds the tools in its member tools"() {
        given:
        new File(dir, 'grails-app').mkdirs()
        File catalog = new File(dir, ToolCatalogFile.LOCATION)
        catalog.parentFile.mkdirs()
        catalog.bytes = ProjectToolsSpec.doc([APPLICATION_TOOL]).getBytes(StandardCharsets.UTF_8)

        expect:
        ((Map) new JsonSlurper().parseText(catalog.text)).tools*.name == ['report']
        ToolCatalogFile.LOCATION == '.grails/cache/tool-catalog.json'
        ToolCatalogFile.TASK == 'grailsToolCatalog'
    }

    void "7. the help of a tool says which Gradle task runs it, for a tool that runs Gradle tasks only"() {
        given:
        new File(dir, 'grails-app').mkdirs()
        File catalog = new File(dir, ToolCatalogFile.LOCATION)
        catalog.parentFile.mkdirs()
        catalog.bytes = ProjectToolsSpec.doc([APPLICATION_TOOL, WITHOUT_APPLICATION]).getBytes(StandardCharsets.UTF_8)

        when:
        ForgeCli.execute(beanContext, 'help', 'report')
        String report = out.toString().replaceAll(/\s+/, ' ')
        out.reset()
        ForgeCli.execute(beanContext, 'help', 'query-tail')
        String queryTail = out.toString().replaceAll(/\s+/, ' ')

        then:
        report.contains('A tool of the application, which runs in the application: it is started by the Gradle task runTool of the project.')
        queryTail.contains('A tool of org.apache.grails:grails-datamapping-core-cli:9.0.0, which the build runs without the application: ' +
                'it is started by the Gradle task runTool of the project.')
        ToolCommand.TASK == 'runTool'
    }

    private Map document() {
        out.reset()
        assert ForgeCli.execute(beanContext, 'help', '--json') == 0
        return (Map) new JsonSlurper().parseText(out.toString().readLines().last())
    }
}

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

import java.nio.charset.StandardCharsets

import org.apache.grails.forge.cli.console.CommandCatalog
import org.apache.grails.forge.cli.mcp.Lane
import org.apache.grails.forge.cli.mcp.McpNames
import org.apache.grails.forge.cli.mcp.McpTool
import org.apache.grails.forge.cli.mcp.McpToolSet
import spock.lang.Specification
import spock.lang.Unroll

/**
 * The tools {@code grails mcp} makes from {@code help --json}, for the three samples of the document:
 * outside a project, in a web application whose build has not listed its tools, and in one with tools of
 * its own and of its plugins. Each sample's tools are held to a golden file under
 * {@code src/test/resources/mcp/tools}: names, titles, lanes, descriptions, annotations, input schemas, and
 * how each runs. After a change of the projection that is meant, write the golden files again by running
 * this spec with the environment variable {@code FORGE_UPDATE_GOLDEN} set to {@code true}, and read the
 * difference before it is committed.
 */
class ToolProjectionSpec extends Specification {

    static final List<String> OUTSIDE_PROJECT = ['create-app', 'create-plugin', 'create-web-plugin', 'create-rest-api-plugin', 'create-restapi', 'feature_find', 'feature_show',
                                                 'feature_diff', 'plugin_list', 'plugin_find', 'plugin_show']

    static final List<String> IN_A_PROJECT = ['create-app', 'create-plugin', 'create-web-plugin', 'create-rest-api-plugin', 'create-restapi', 'feature_add', 'feature_list',
                                              'feature_find', 'feature_show', 'feature_diff', 'project_show', 'project_regenerate', 'project_migrate', 'project_health',
                                              'create-controller', 'create-service', 'create-domain-class', 'create-taglib', 'create-interceptor', 'add-property',
                                              'create-unit-test', 'create-integration-test', 'create-functional-test', 'create-tool', 'install-templates', 'compile', 'clean',
                                              'assemble', 'dependency-report', 'test-app', 'stats', 'run-app', 'stop-app', 'run-tool', 'update-wrapper', 'plugin_list',
                                              'plugin_find', 'plugin_show']

    @Unroll
    void "the tools of the sample #sample are those of the golden file"() {
        given:
        McpToolSet tools = McpToolSamples.tools(sample)
        String written = McpToolSamples.text(golden(sample, tools))
        File file = McpToolSamples.golden(sample)
        if (System.getenv('FORGE_UPDATE_GOLDEN') == 'true' && !System.getenv('CI')) {
            file.parentFile.mkdirs()
            file.setText(written, StandardCharsets.UTF_8.name())
        }

        expect:
        file.isFile()
        McpToolSamples.difference(file.getText(StandardCharsets.UTF_8.name()), written) == null

        where:
        sample << McpToolSamples.SAMPLES
    }

    void "outside a project the commands that create a project, and those of features and plugins that need none, are offered"() {
        when:
        McpToolSet tools = McpToolSamples.tools('outside-project')

        then:
        !tools.project
        tools.fingerprint == 'fingerprint'
        tools.tools*.name == OUTSIDE_PROJECT

        and: 'the commands of a project are not, and the diagnostics say why'
        ((List) tools.helpDocument.diagnostics) == [ToolProjection.OUTSIDE_A_PROJECT]

        and: 'the commands that create a project say that the new project gets a server of its own'
        tools.tools.findAll { it.name.startsWith('create-') }.every { it.description.endsWith('started in its directory or with grails mcp --directory DIRECTORY.') }
    }

    void "in a web application the commands of a project and refresh_tools are offered, and refresh_tools says to call it while the tools are not listed"() {
        when:
        McpToolSet tools = McpToolSamples.tools('web-application')

        then:
        tools.project
        tools.tools*.name == IN_A_PROJECT + [McpNames.REFRESH_TOOLS]

        and: 'the document is kept as it is, with what the projection adds to its diagnostics'
        !tools.helpDocument.complete
        ((List) tools.helpDocument.diagnostics) == McpToolSamples.sample('web-application').diagnostics

        and:
        McpTool refresh = tools.tools.last()
        refresh.description.contains('call it to list them')
        refresh.commandLine([:]) == ToolProjection.REFRESH_COMMAND
        refresh.lane == Lane.BUILD
        refresh.annotations == [readOnlyHint: false, destructiveHint: false, idempotentHint: true, openWorldHint: true]
        refresh.inputSchema == [type: 'object', properties: [:], additionalProperties: false]
        !refresh.meta.containsKey(McpNames.META_COMMAND)
    }

    void "a tool whose JSON document is a report says in how it runs which outcomes of the document are a report, and in its description what that means"() {
        when:
        Map<String, McpTool> byName = McpToolSamples.tools('web-application').tools.collectEntries { [(it.name): it] }

        then:
        byName.project_regenerate.meta[McpNames.META_RUN][ToolProjection.REPORT_OUTCOMES] == ['changes']
        byName.project_health.meta[McpNames.META_RUN][ToolProjection.REPORT_OUTCOMES] == ['checked']
        byName.feature_add.meta[McpNames.META_RUN][ToolProjection.REPORT_OUTCOMES] == ['conflicts']
        byName.project_regenerate.description.contains('Files that differ, as in any project that was edited, are a result and not an error, with the outcome changes, ' +
                'though the command ends with exit code 1.')
        byName.findAll { String name, McpTool tool -> !(name in ['project_regenerate', 'project_health', 'feature_add']) }
                .every { String name, McpTool tool -> !((Map) tool.meta[McpNames.META_RUN]).containsKey(ToolProjection.REPORT_OUTCOMES) }
    }

    void "the tools of the application and its plugins are offered, but not one that reads what is typed"() {
        when:
        McpToolSet tools = McpToolSamples.tools('application-with-tools')

        then:
        tools.tools*.name == IN_A_PROJECT + ['audit-report', 'legacy-task', 'dbm-update', 'example-report', 'generate-all', 'clean-cache', McpNames.REFRESH_TOOLS]
        !('import-data' in tools.tools*.name)

        and: 'the diagnostics name it, with the reason'
        ((List<String>) tools.helpDocument.diagnostics).any { it.startsWith('The tool import-data is not offered: it reads what is typed') }

        and: 'the refresh tool does not ask to be called while the list is complete'
        !tools.tools.last().description.contains('call it to list them')
    }

    void "a tool that declares its options and parameters takes them, and one that declares nothing takes its arguments as they are"() {
        when:
        McpToolSet tools = McpToolSamples.tools('application-with-tools')
        Map<String, McpTool> byName = tools.tools.collectEntries { [(it.name): it] }

        then: 'a declared tool of the application'
        byName['audit-report'].inputSchema == [type: 'object', properties: [
                format           : [type: 'string', enum: ['TEXT', 'JSON'], default: 'TEXT', description: 'The format of the report'],
                'include-deleted': [type: 'boolean', description: 'Lists deleted records as well'],
                since            : [type: 'string', description: 'The first day of the report'],
                output           : [type: 'string', description: 'The file to write']],
                required: ['output'], additionalProperties: false]
        byName['audit-report'].meta[McpNames.META_RUN].arguments == ToolProjection.DECLARED

        and: 'its released camel-case options keep their names'
        ((Map) byName['dbm-update'].inputSchema.get('properties')).keySet() == ['contexts', 'dataSource', 'defaultSchema'] as Set

        and: 'a tool and a script that declare nothing'
        ['legacy-task', 'clean-cache'].every { String name ->
            byName[name].inputSchema == [type: 'object', properties: [arguments: [type: 'array', items: [type: 'string'], description: InputSchemas.ARGUMENTS_DESCRIPTION]],
                                         additionalProperties: false] &&
                    byName[name].meta[McpNames.META_RUN].arguments == ToolProjection.FREE_FORM
        }
    }

    void "what a tool lacks, how it runs and where it comes from are in its description"() {
        when:
        Map<String, McpTool> byName = McpToolSamples.tools('application-with-tools').tools.collectEntries { [(it.name): it] }

        then:
        byName['legacy-task'].description.startsWith('The tool legacy-task has no description of its own.')
        byName['legacy-task'].description.contains('Its arguments are not declared')
        byName['legacy-task'].description.contains('It does not say what it changes')
        byName['legacy-task'].description.contains('which the build runs without the application')

        and:
        byName['audit-report'].description.startsWith('Writes the audit report. A tool of the application, which runs in the application')
        byName['audit-report'].description.contains('run-tool with system-properties')
        !byName['audit-report'].description.contains('not declared')

        and:
        byName['generate-all'].description.contains('A tool of org.apache.grails:grails-scaffolding-cli-standalone:9.0.0, which runs as a process of its own')
        !byName['generate-all'].description.contains('environment')

        and: 'a script is deprecated'
        byName['clean-cache'].description.contains('A script of the application, in src/main/scripts')
        byName['clean-cache'].description.contains('Script commands are deprecated')
    }

    void "the hints of a tool are passed on only for the origins the server vouches for"() {
        when:
        Map<String, McpTool> byName = McpToolSamples.tools('application-with-tools').tools.collectEntries { [(it.name): it] }

        then: 'of the application, and of a plugin of Grails'
        byName['audit-report'].annotations == [readOnlyHint: true, destructiveHint: true, idempotentHint: false, openWorldHint: true]
        byName['dbm-update'].annotations == [readOnlyHint: false, destructiveHint: true, idempotentHint: false, openWorldHint: true]
        byName['generate-all'].annotations == [readOnlyHint: false, destructiveHint: true, idempotentHint: false, openWorldHint: false]

        and: 'not of another plugin, whose declaration is still in _meta; nor where a tool declares none'
        byName['example-report'].annotations == [:]
        byName['example-report'].meta[McpNames.META_COMMAND].hints == [readOnlyHint: true, destructiveHint: false, idempotentHint: true, openWorldHint: false]
        byName['legacy-task'].annotations == [:]
    }

    @Unroll
    void "with --read-only only the tools that change nothing are offered in #sample"() {
        when:
        McpToolSet tools = McpToolSamples.tools(sample, true)

        then:
        tools.tools*.name == offered
        tools.tools.every { it.annotations.readOnlyHint == true }

        where:
        sample                   | offered
        'outside-project'        | ['feature_find', 'feature_show', 'feature_diff', 'plugin_list', 'plugin_find', 'plugin_show']
        'web-application'        | ['feature_list', 'feature_find', 'feature_show', 'feature_diff', 'project_show', 'project_regenerate', 'project_health',
                                    'dependency-report', 'stats', 'plugin_list', 'plugin_find', 'plugin_show']
        'application-with-tools' | ['feature_list', 'feature_find', 'feature_show', 'feature_diff', 'project_show', 'project_regenerate', 'project_health',
                                    'dependency-report', 'stats', 'plugin_list', 'plugin_find', 'plugin_show', 'audit-report']
    }

    @Unroll
    void "every tool of #sample has the whole entry of its command in _meta, a schema clients take, and a description of at most 2,000 characters"() {
        given:
        Map<String, Object> document = McpToolSamples.sample(sample)
        Map<String, Map<String, Object>> entries = McpToolSamples.leaves((List<Map<String, Object>>) document.commands).collectEntries { List<String> path, Map entry ->
            [(McpNames.toolName(path)): entry]
        }

        when:
        McpToolSet tools = ToolProjection.of(document, false, 'fingerprint')

        then:
        tools.tools.findAll { it.name != McpNames.REFRESH_TOOLS }.every { McpTool tool ->
            tool.meta[McpNames.META_COMMAND] == entries[tool.name].findAll { it.key != 'commands' } && tool.title == "grails ${tool.name.replace('_', ' ')}"
        }
        tools.tools.every { McpTool tool ->
            tool.inputSchema.type == 'object' && tool.inputSchema.additionalProperties == false &&
                    ((Map<String, Object>) tool.inputSchema.get('properties')).keySet().every { it ==~ /[A-Za-z0-9-]{1,64}/ } &&
                    tool.name ==~ /[A-Za-z0-9_-]{1,64}/ && tool.description.length() <= ToolProjection.MAX_DESCRIPTION &&
                    tool.meta[McpNames.META_RUN].commandLine.take(2) == ToolProjection.ROOT_OPTIONS
        }

        and: 'no option of the command line in front of a command is offered as an argument of one'
        tools.tools.every { McpTool tool ->
            !((Map<String, Object>) tool.inputSchema.get('properties')).keySet().any { it in ['help', 'version', 'verbose', 'stacktrace', 'plain-output', 'non-interactive'] } ||
                    tool.name == 'example-report'
        }

        where:
        sample << McpToolSamples.SAMPLES
    }

    void "a command of the command line the server has no policy for is not offered, and the diagnostics say so"() {
        given:
        Map<String, Object> document = McpToolSamples.sample('web-application')
        ((List) document.commands) << [name: 'frobnicate', aliases: [], description: 'Does something new', project: false, origin: CommandCatalog.BUILT_IN, options: [],
                                       parameters: [], relations: [], commands: []]

        when:
        McpToolSet tools = ToolProjection.of(document, false, 'fingerprint')

        then:
        !('frobnicate' in tools.tools*.name)
        ((List<String>) tools.helpDocument.diagnostics).contains('The command frobnicate is not offered: grails mcp has no policy for it')
    }

    void "a command a feature of the application gives it is offered as the commands that generate code are"() {
        given:
        Map<String, Object> document = McpToolSamples.sample('web-application')
        ((List) document.commands) << [name: 'create-audit-store', aliases: [], description: 'Creates an audit store (feature acme-audit)', project: true,
                                       origin: 'feature:acme-audit', options: [[names: ['-f', '--force'], arity: '0', type: 'boolean', label: null, required: false,
                                                                                description: 'Whether to overwrite existing files', repeatable: false, split: null,
                                                                                choices: null, default: null]],
                                       parameters: [[label: 'NAME', arity: '1', type: 'string', description: 'The name of the class to create', repeatable: false,
                                                     split: null, choices: null]], relations: [], commands: []]

        when:
        McpTool tool = ToolProjection.of(document, false, 'fingerprint').tools.find { it.name == 'create-audit-store' }

        then:
        tool.title == 'grails create-audit-store'
        tool.lane == Lane.DIRECT
        tool.annotations == [readOnlyHint: false, destructiveHint: true, idempotentHint: false, openWorldHint: false]
        tool.commandLine([force: true, name: 'Ledger']) == ['create-audit-store', '--force', 'Ledger']
    }

    void "a name that is not one of a tool, or that is taken, is not offered, and an option with only another short name neither"() {
        given:
        Map<String, Object> document = McpToolSamples.sample('application-with-tools')
        String tooLong = 'a' * 65
        ((List) document.commands) << tool(tooLong) << tool('audit-report') << tool('odd-options', [names: ['-q'], arity: '0', type: 'boolean'])

        when:
        McpToolSet tools = ToolProjection.of(document, false, 'fingerprint')
        List<String> diagnostics = (List<String>) tools.helpDocument.diagnostics

        then:
        !(tooLong in tools.tools*.name)
        diagnostics.contains("The command ${tooLong} is not offered: its name as a tool, ${tooLong}, is not 1 to 64 letters, digits, _ and -".toString())
        tools.tools.count { it.name == 'audit-report' } == 1
        diagnostics.contains('The command audit-report is not offered: another tool is named audit-report')

        and:
        ((Map) tools.tools.find { it.name == 'odd-options' }.inputSchema.get('properties')).isEmpty()
        diagnostics.contains('The option -q of odd-options is not offered: an option with only a short name is offered only as -D or -P')
    }

    void "a description is cut at 2,000 characters in what the command says of itself, and what the server adds to it is whole"() {
        given:
        Map<String, Object> document = McpToolSamples.sample('application-with-tools')
        ((List) document.commands) << tool('long-story') + [description: 'word ' * 600]

        when:
        McpToolSet tools = ToolProjection.of(document, false, 'fingerprint')
        McpTool tool = tools.tools.find { it.name == 'long-story' }
        McpTool migrate = tools.tools.find { it.name == 'project_migrate' }

        then:
        tool.description.length() == ToolProjection.MAX_DESCRIPTION
        tool.description.startsWith('word word')
        tool.description.contains('… A tool of the application, which the build runs without the application')
        tool.description.endsWith('Its arguments are not declared: each of arguments is given to it as it is.')

        and: 'a command of the command line whose description is long'
        migrate.description.length() <= ToolProjection.MAX_DESCRIPTION
        migrate.description.endsWith(CommandPolicy.of(['project', 'migrate']).note)
        migrate.description.contains('… ')

        and:
        ToolProjection.capped('short') == 'short'
        ToolProjection.capped('x' * 2001).length() == 2000
        ToolProjection.capped('own', ['', 'added']) == 'own added'
        ToolProjection.capped('', ['added']) == 'added'
    }

    void "a parameter whose name an option has is offered with -argument after its name"() {
        given:
        Map<String, Object> document = McpToolSamples.sample('application-with-tools')
        ((List) document.commands) << tool('clash', [names: ['--target'], arity: '1', type: 'string'], [label: 'TARGET', arity: '1', type: 'string'])

        when:
        McpTool tool = ToolProjection.of(document, false, 'fingerprint').tools.find { it.name == 'clash' }

        then:
        ((Map) tool.inputSchema.get('properties')).keySet() == ['target', 'target-argument'] as Set
        tool.commandLine([target: 'a', 'target-argument': 'b']) == ['clash', '--target=a', 'b']
    }

    // what the golden file holds of the tools of a sample
    private static Map<String, Object> golden(String sample, McpToolSet tools) {
        return [
                sample     : sample,
                project    : tools.project,
                readOnly   : McpToolSamples.tools(sample, true).tools*.name,
                diagnostics: tools.helpDocument.diagnostics,
                tools      : tools.tools.collect { McpTool tool ->
                    [name       : tool.name,
                     title      : tool.title,
                     lane       : tool.lane.name().toLowerCase(Locale.ROOT),
                     description: tool.description,
                     annotations: tool.annotations,
                     inputSchema: tool.inputSchema,
                     run        : tool.meta[McpNames.META_RUN]]
                }
        ]
    }

    private static Map<String, Object> tool(String name, Map<String, Object> option = null, Map<String, Object> parameter = null) {
        return [name: name, aliases: [], description: 'A tool', project: true, origin: 'application', target: 'application', startsApplication: false,
                registration: 'descriptor', options: option == null ? [] : [option + [label: null, required: false, description: null, repeatable: false, split: null,
                                                                                       choices: null, default: null]],
                parameters: parameter == null ? [] : [parameter + [description: null, repeatable: false, split: null, choices: null]], commands: []]
    }
}

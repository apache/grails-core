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

import org.apache.grails.forge.cli.StartupPreload
import org.apache.grails.forge.cli.console.CommandCatalog
import org.apache.grails.forge.cli.mcp.Lane
import org.apache.grails.forge.cli.mcp.McpTool
import org.grails.forge.cli.CommandSpec
import org.grails.forge.cli.ForgeCli
import picocli.CommandLine
import spock.lang.Unroll

import static org.apache.grails.forge.cli.mcp.tools.CommandPolicy.JsonForm.ALWAYS
import static org.apache.grails.forge.cli.mcp.tools.CommandPolicy.JsonForm.NEVER
import static org.apache.grails.forge.cli.mcp.tools.CommandPolicy.JsonForm.WITH_DRY_RUN

/**
 * Every command of the command line has a row of {@link CommandPolicy}, which says whether and how
 * {@code grails mcp} offers it: the commands of the samples of {@code help --json}, and every command
 * the command line registers, also those it hides and those that are commands only in some projects. A
 * new command of the command line fails this spec until it has a row.
 */
class CommandPolicySpec extends CommandSpec {

    static final Map<String, Object> READS_LOCAL = [readOnlyHint: true, openWorldHint: false]
    static final Map<String, Object> READS_REMOTE = [readOnlyHint: true, openWorldHint: true]

    @Unroll
    void "every command of the command line in the sample #sample has a row"() {
        given:
        Map<String, Object> document = McpToolSamples.sample(sample)

        expect:
        withoutRows(document) == []

        where:
        sample << McpToolSamples.SAMPLES
    }

    void "every command the command line registers has a row, those it hides and those of only some projects too"() {
        given:
        CommandLine root = ForgeCli.createCommandLine(beanContext, true, true)

        when:
        List<List<String>> registered = commands(root, [])

        then: 'outside a project every command of a project is registered, those that generate code hidden'
        ['install'] in registered
        ['create-job'] in registered
        ['create-restful-controller'] in registered
        ['package-plugin'] in registered
        ['feature', 'add'] in registered

        and:
        registered.findAll { CommandPolicy.of(it) == null } == []
    }

    void "a command the samples do not have fails the check until it has a row"() {
        given:
        Map<String, Object> document = McpToolSamples.sample('outside-project')
        ((List) document.commands) << [name: 'frobnicate', aliases: [], description: 'Something new', project: false, origin: CommandCatalog.BUILT_IN, options: [],
                                       parameters: [], relations: [], commands: []]

        expect:
        withoutRows(document) == [['frobnicate']]
    }

    @Unroll
    void "#name: offered #exposed, in the lane #lane, JSON #json, given #forced on every call, with #leftOut left out"() {
        when:
        CommandPolicy row = CommandPolicy.rows().find { it.name == name }

        then:
        row != null
        row.exposed == exposed
        !exposed || (row.lane == lane && row.json == json && row.forcedOptions == forced && row.leftOutOptions == leftOut && row.annotations == annotations)
        exposed || row.reason != null
        !row.isolated

        where:
        name                      | exposed | lane           | json         | forced               | leftOut                                                    | annotations
        'create-app'              | true    | Lane.GENERATOR | NEVER        | []                   | CommandPolicy.SOURCES_OF_CODE + ['--list-features', '--list-catalog-features'] | changes(false, false, true)
        'create-restapi'          | true    | Lane.GENERATOR | NEVER        | []                   | CommandPolicy.SOURCES_OF_CODE + ['--list-features', '--list-catalog-features'] | changes(false, false, true)
        'create-plugin'           | true    | Lane.GENERATOR | NEVER        | []                   | CommandPolicy.SOURCES_OF_CODE + ['--list-features', '--list-catalog-features'] | changes(false, false, true)
        'create-web-plugin'       | true    | Lane.GENERATOR | NEVER        | []                   | CommandPolicy.SOURCES_OF_CODE + ['--list-features', '--list-catalog-features'] | changes(false, false, true)
        'create-rest-api-plugin'  | true    | Lane.GENERATOR | NEVER        | []                   | CommandPolicy.SOURCES_OF_CODE + ['--list-features', '--list-catalog-features'] | changes(false, false, true)
        'create-webapp'           | false   | null           | null         | null                 | null                                                       | null
        'feature_add'             | true    | Lane.GENERATOR | WITH_DRY_RUN | []                   | CommandPolicy.SOURCES_OF_CODE + ['--json']                 | changes(true, true, true)
        'feature_list'            | true    | Lane.GENERATOR | NEVER        | []                   | CommandPolicy.SOURCES_OF_CODE                              | READS_REMOTE
        'feature_find'            | true    | Lane.GENERATOR | ALWAYS       | ['--json']           | CommandPolicy.SOURCES_OF_CODE + ['--json']                 | READS_REMOTE
        'feature_show'            | true    | Lane.GENERATOR | ALWAYS       | ['--json']           | CommandPolicy.SOURCES_OF_CODE + ['--json']                 | READS_REMOTE
        'feature_diff'            | true    | Lane.GENERATOR | NEVER        | []                   | CommandPolicy.SOURCES_OF_CODE                              | READS_REMOTE
        'plugin_list'             | true    | Lane.DIRECT    | ALWAYS       | ['--json']           | ['--plugin-index', '--json']                               | READS_REMOTE
        'plugin_find'             | true    | Lane.DIRECT    | ALWAYS       | ['--json']           | ['--plugin-index', '--json']                               | READS_REMOTE
        'plugin_show'             | true    | Lane.DIRECT    | ALWAYS       | ['--json']           | ['--plugin-index', '--json']                               | READS_REMOTE
        'list-plugins'            | false   | null           | null         | null                 | null                                                       | null
        'plugin-info'             | false   | null           | null         | null                 | null                                                       | null
        'project_show'            | true    | Lane.DIRECT    | ALWAYS       | ['--json']           | ['--json']                                                 | READS_LOCAL
        'project_health'          | true    | Lane.DIRECT    | ALWAYS       | ['--json']           | ['--json']                                                 | READS_LOCAL
        'project_migrate'         | true    | Lane.GENERATOR | NEVER        | []                   | CommandPolicy.SOURCES_OF_CODE                              | changes(true, false, false)
        'project_regenerate'      | true    | Lane.GENERATOR | ALWAYS       | ['--diff', '--json'] | CommandPolicy.SOURCES_OF_CODE + ['--diff', '--json', '--output', '--force'] | READS_LOCAL
        'create-controller'       | true    | Lane.DIRECT    | NEVER        | []                   | []                                                         | changes(true, false, false)
        'create-service'          | true    | Lane.DIRECT    | NEVER        | []                   | []                                                         | changes(true, false, false)
        'create-domain-class'     | true    | Lane.DIRECT    | NEVER        | []                   | []                                                         | changes(true, false, false)
        'create-taglib'           | true    | Lane.DIRECT    | NEVER        | []                   | []                                                         | changes(true, false, false)
        'create-interceptor'      | true    | Lane.DIRECT    | NEVER        | []                   | []                                                         | changes(true, false, false)
        'create-job'              | true    | Lane.DIRECT    | NEVER        | []                   | []                                                         | changes(true, false, false)
        'add-property'            | true    | Lane.DIRECT    | NEVER        | []                   | []                                                         | changes(true, false, false)
        'create-unit-test'        | true    | Lane.DIRECT    | NEVER        | []                   | []                                                         | changes(true, false, false)
        'create-integration-test' | true    | Lane.DIRECT    | NEVER        | []                   | []                                                         | changes(true, false, false)
        'create-functional-test'  | true    | Lane.DIRECT    | NEVER        | []                   | []                                                         | changes(true, false, false)
        'create-restful-controller' | true  | Lane.DIRECT    | NEVER        | []                   | []                                                         | changes(true, false, false)
        'create-domain-resource'  | true    | Lane.DIRECT    | NEVER        | []                   | []                                                         | changes(true, false, false)
        'create-tool'             | true    | Lane.DIRECT    | NEVER        | []                   | []                                                         | changes(true, false, false)
        'install-templates'       | true    | Lane.DIRECT    | NEVER        | []                   | []                                                         | changes(true, false, false)
        'compile'                 | true    | Lane.BUILD     | NEVER        | []                   | []                                                         | changes(false, true, true)
        'clean'                   | true    | Lane.BUILD     | NEVER        | []                   | []                                                         | changes(false, true, true)
        'assemble'                | true    | Lane.BUILD     | NEVER        | []                   | []                                                         | changes(false, true, true)
        'package-plugin'          | true    | Lane.BUILD     | NEVER        | []                   | []                                                         | changes(false, true, true)
        'dependency-report'       | true    | Lane.BUILD     | NEVER        | []                   | []                                                         | READS_REMOTE
        'test-app'                | true    | Lane.BUILD     | NEVER        | []                   | ['--continuous', '--debug-jvm']                            | changes(false, false, true)
        'run-app'                 | true    | Lane.BUILD     | NEVER        | ['--detach']         | ['--detach', '--foreground', '--debug-jvm']                | changes(false, false, true)
        'stop-app'                | true    | Lane.DIRECT    | NEVER        | []                   | []                                                         | changes(false, true, false)
        'run-tool'                | true    | Lane.BUILD     | NEVER        | []                   | []                                                         | changes(true, false, true)
        'publish-plugin'          | true    | Lane.BUILD     | NEVER        | []                   | []                                                         | changes(true, false, true)
        'stats'                   | true    | Lane.DIRECT    | NEVER        | []                   | []                                                         | READS_LOCAL
        'update-wrapper'          | true    | Lane.DIRECT    | NEVER        | []                   | []                                                         | changes(true, true, false)
        'gradle'                  | false   | null           | null         | null                 | null                                                       | null
        'shell'                   | false   | null           | null         | null                 | null                                                       | null
        'console'                 | false   | null           | null         | null                 | null                                                       | null
        'run-script'              | false   | null           | null         | null                 | null                                                       | null
        'bug-report'              | false   | null           | null         | null                 | null                                                       | null
        'help'                    | false   | null           | null         | null                 | null                                                       | null
        'install'                 | false   | null           | null         | null                 | null                                                       | null
        'mcp'                     | false   | null           | null         | null                 | null                                                       | null
    }

    void "the table above is every row"() {
        expect:
        CommandPolicy.rows().size() == 54
        CommandPolicy.rows()*.name.toUnique().size() == CommandPolicy.rows().size()
    }

    void "the JSON documents that are a report although their command ends with exit code 1 are those of three rows, which say so in their notes"() {
        given:
        List<CommandPolicy> reporting = CommandPolicy.rows().findAll { !it.reportOutcomes.isEmpty() }

        expect: 'the outcomes that are a report, and not those of a failure, which these commands write a document for too'
        reporting.collectEntries { [(it.name): it.reportOutcomes] } ==
                [feature_add: [CommandPolicy.CONFLICTS], project_health: [CommandPolicy.CHECKED], project_regenerate: [CommandPolicy.CHANGES]]
        reporting.every { CommandPolicy row -> row.json != NEVER && row.note.contains(' a result and not an error') && row.note.contains('exit code 1') }
        !reporting*.reportOutcomes.flatten().any { it in ['refused', 'failed', 'invalid-record', 'no-changes'] }
        CommandPolicy.ofFeatureCommand().reportOutcomes.isEmpty()
    }

    void "the commands that start the project generator run in its lane, and the others not"() {
        expect:
        CommandPolicy.rows().findAll { it.exposed }.every { CommandPolicy row ->
            boolean generator = row.path.size() == 1 ?
                    row.path[0] in ['create-app', 'create-restapi', 'create-plugin', 'create-web-plugin', 'create-rest-api-plugin'] :
                    (StartupPreload.GENERATOR_SUBCOMMANDS[row.path[0]]?.contains(row.path[1]) ?: false)
            generator == (row.lane == Lane.GENERATOR)
        }
    }

    void "a row that changes nothing gives only the hints that mean something then, and every other row all four"() {
        expect:
        CommandPolicy.rows().findAll { it.exposed }.every { CommandPolicy row ->
            row.annotations.readOnlyHint ? row.annotations.keySet() == ['readOnlyHint', 'openWorldHint'] as Set :
                    row.annotations.keySet() == ProjectToolHints.HINTS as Set
        }
        CommandPolicy.ofFeatureCommand().exposed
        CommandPolicy.ofFeatureCommand().lane == Lane.DIRECT
        CommandPolicy.ofFeatureCommand().annotations == changes(true, false, false)
        CommandPolicy.REFRESH_TOOLS_ANNOTATIONS == changes(false, true, true)
    }

    @Unroll
    void "over MCP no new source of code can be named, and nothing that stays with the application, in #sample"() {
        given:
        List<McpTool> tools = McpToolSamples.tools(sample).tools

        expect:
        tools.every { McpTool tool ->
            Set<String> properties = ((Map<String, Object>) tool.inputSchema.get('properties')).keySet()
            !properties.any { it in ['feature-catalog', 'feature-pack-dir', 'feature-pack-repository', 'feature-pack-sha256', 'preset', 'plugin-index', 'list-features',
                                     'list-catalog-features'] } &&
                    (tool.name != 'project_regenerate' || !properties.any { it in ['output', 'force', 'diff', 'json'] }) &&
                    (tool.name != 'run-app' || !properties.any { it in ['detach', 'foreground', 'debug-jvm'] }) &&
                    (tool.name != 'test-app' || !properties.any { it in ['continuous', 'debug-jvm'] })
        }
        !tools.any { it.name in ['gradle', 'shell', 'console', 'run-script', 'bug-report', 'help', 'create-webapp', 'list-plugins', 'plugin-info'] }

        where:
        sample << McpToolSamples.SAMPLES
    }

    // the commands of the command line in a document that have no row
    private static List<List<String>> withoutRows(Map<String, Object> document) {
        return McpToolSamples.leaves((List<Map<String, Object>>) document.commands).findAll { List<String> path, Map entry ->
            entry.origin == CommandCatalog.BUILT_IN && CommandPolicy.of(path) == null
        }.keySet() as List
    }

    // every command a command line registers that has no commands of its own, by its own name and not by another it has
    private static List<List<String>> commands(CommandLine parent, List<String> path) {
        List<List<String>> found = []
        Set<CommandLine> seen = Collections.newSetFromMap(new IdentityHashMap<CommandLine, Boolean>())
        parent.subcommands.values().each { CommandLine command ->
            if (!seen.add(command)) {
                return
            }
            List<String> words = path + [command.commandName]
            if (command.subcommands.isEmpty()) {
                found << words
            } else {
                found.addAll(commands(command, words))
            }
        }
        return found
    }

    private static Map<String, Object> changes(boolean destructive, boolean idempotent, boolean openWorld) {
        return [readOnlyHint: false, destructiveHint: destructive, idempotentHint: idempotent, openWorldHint: openWorld]
    }
}

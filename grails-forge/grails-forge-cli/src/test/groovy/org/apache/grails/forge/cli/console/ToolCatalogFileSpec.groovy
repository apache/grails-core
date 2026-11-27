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
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.apache.grails.gradle.model.cli.ToolCatalogBuilder
import org.apache.grails.gradle.model.cli.ToolDescriptor
import org.grails.forge.cli.CommandFixture
import org.grails.forge.cli.CommandSpec
import org.grails.forge.cli.ForgeCli
import spock.lang.Unroll

/**
 * The catalog of the tools, as the command line reads it: the member {@code tools}, each tool with its
 * target, {@code application} or {@code standalone}, whether it starts the application, which a tool of
 * the target {@code application} does unless the catalog says {@code false}, and what of it is deprecated.
 * A tool the catalog has otherwise than the task writes it is left out alone, with a line that says why.
 */
class ToolCatalogFileSpec extends CommandSpec implements CommandFixture {

    static final Map APPLICATION = [name: 'report', description: 'Reports', origin: 'application', target: 'application', registration: 'descriptor']

    PrintStream originalOut
    PrintStream originalErr
    ByteArrayOutputStream out = new ByteArrayOutputStream()
    ByteArrayOutputStream err = new ByteArrayOutputStream()

    void setup() {
        originalOut = System.out
        originalErr = System.err
        System.setOut(new PrintStream(out, true))
        System.setErr(new PrintStream(err, true))
        new File(dir, 'grails-app').mkdirs()
    }

    void cleanup() {
        System.setOut(originalOut)
        System.setErr(originalErr)
    }

    @Unroll
    void "a tool #what is read as one of the target #target"() {
        given:
        catalog([APPLICATION + changed])

        when:
        Map tool = tools().find { it.name == 'report' }

        then:
        tool.target == target
        tool.startsApplication == startsApplication
        tool.registration == registration
        document().diagnostics == []

        where:
        what                                    | changed                                                     || target        | startsApplication | registration
        'that says nothing of the application'  | [:]                                                         || 'application' | true              | 'descriptor'
        'that starts the application'           | [startsApplication: true]                                   || 'application' | true              | 'descriptor'
        'that does not start the application'   | [startsApplication: false]                                  || 'application' | false             | 'descriptor'
        'registered by the factories'           | [registration: 'factories']                                 || 'application' | true              | 'factories'
        'that runs as a process of its own'     | [target: 'standalone']                                      || 'standalone'  | false             | 'descriptor'
        'of its own process, said not to start' | [target: 'standalone', startsApplication: false]            || 'standalone'  | false             | 'descriptor'
    }

    @Unroll
    void "what a tool says is deprecated, #deprecated, is read and described"() {
        given:
        catalog([APPLICATION + [deprecated: deprecated]])

        when:
        Map tool = tools().find { it.name == 'report' }

        then:
        tool.deprecated == deprecated
        document().diagnostics == []

        where:
        deprecated << [['type'], ['folder'], ['type', 'folder'], ['folder', 'type']]
    }

    void "a tool that is deprecated in nothing is described without the member"() {
        given:
        catalog([APPLICATION, APPLICATION + [name: 'empty', deprecated: []]])

        expect:
        tools().every { !it.containsKey('deprecated') }
    }

    @Unroll
    void "a tool #what is left out, and a line says why"() {
        given:
        catalog([APPLICATION + [name: 'fine'], APPLICATION + changed])

        when:
        Map described = document()

        then: 'the other tool is there'
        ((List<Map>) described.commands).findAll { it.origin != 'built-in' }*.name == ['fine']
        described.complete == false
        described.diagnostics == ["Tool 2 of ${ToolCatalogFile.LOCATION} is not used: ${why}".toString()]

        where:
        what                                                  | changed                                                  | why
        'of the target gradle, which no longer exists'        | [target: 'gradle', startsApplication: false]             | 'its target is none there is'
        'of the target console'                               | [target: 'console']                                      | 'its target is console, which is not run from here'
        'registered by the bridge of Grails 7, which is gone' | [registration: 'legacy']                                 | 'its registration is none of descriptor, factories'
        'that says it is a script'                            | [registration: 'script']                                 | 'its registration is none of descriptor, factories'
        'that does not say with a boolean'                    | [startsApplication: 'false']                             | 'it does not say with true or false whether it starts the application'
        'of its own process that says it starts it'           | [target: 'standalone', startsApplication: true]          | 'it runs as a process of its own and says that it starts the application'
        'that is deprecated in what there is not'             | [deprecated: ['package']]                                | 'what it says is deprecated is not a list of type and folder, each at most once'
        'that is deprecated twice in one thing'               | [deprecated: ['type', 'type']]                           | 'what it says is deprecated is not a list of type and folder, each at most once'
        'that says what is deprecated in a word'              | [deprecated: 'type']                                     | 'what it says is deprecated is not a list of type and folder, each at most once'
    }

    void "a catalog with a list of commands and none of tools is not used"() {
        given:
        File file = new File(dir, ToolCatalogFile.LOCATION)
        file.parentFile.mkdirs()
        file.text = JsonOutput.toJson([format: 1, complete: true, diagnostics: [], commands: [APPLICATION]])

        when:
        Map described = document()

        then:
        described.complete == false
        ((List<String>) described.diagnostics)[0].startsWith("The tools of the application and its plugins are not listed: ${ToolCatalogFile.LOCATION} has no list of tools.")
        ((List<Map>) described.commands).every { it.origin == 'built-in' }
    }

    void "a script of the application is a tool of the target application that does not start it"() {
        given:
        catalog([APPLICATION])
        File script = new File(dir, 'src/main/scripts/clean-cache.groovy')
        script.parentFile.mkdirs()
        script.text = 'description "Cleans the cache"\n'

        when:
        Map tool = tools().find { it.name == 'clean-cache' }

        then:
        tool.target == 'application'
        tool.startsApplication == false
        tool.registration == 'script'
        tool.origin == 'application'
        !tool.containsKey('deprecated')
    }

    void "what the builder of the catalog writes for each target and each deprecation is read as it is"() {
        given: 'classes registered with descriptors as the compiler writes them, and the catalog the task writes of them'
        ToolCatalogBuilder builder = new ToolCatalogBuilder()
        builder.add(ToolCatalogBuilder.ORIGIN_APPLICATION, element('main', [
                descriptor('example.ReportTool', 'report', ToolDescriptor.TARGET_APPLICATION, true, []),
                descriptor('example.TidyTool', 'tidy', ToolDescriptor.TARGET_APPLICATION, false, ['folder']),
                descriptor('example.OldCommand', 'old', ToolDescriptor.TARGET_APPLICATION, true, ['type', 'folder'])]))
        builder.add('org.example:render-cli-standalone:1.0', element('render', [descriptor('example.RenderTool', 'render', ToolDescriptor.TARGET_STANDALONE, false, [])]))
        File file = new File(dir, ToolCatalogFile.LOCATION)
        file.parentFile.mkdirs()
        file.text = builder.toJson()

        when:
        Map<String, Map> tools = tools().collectEntries { [(it.name): it] }

        then:
        document().diagnostics == []
        tools.keySet() == ['old', 'render', 'report', 'tidy'] as Set
        tools.report.subMap(['target', 'startsApplication']) == [target: 'application', startsApplication: true]
        tools.tidy.subMap(['target', 'startsApplication', 'deprecated']) == [target: 'application', startsApplication: false, deprecated: ['folder']]
        tools.old.deprecated == ['type', 'folder']
        tools.render.subMap(['target', 'startsApplication', 'origin']) == [target: 'standalone', startsApplication: false, origin: 'org.example:render-cli-standalone:1.0']
    }

    void "what a tool declares, as the builder of the catalog writes it, is in help --json with every member, as the options of a command are"() {
        given: 'a tool that declares everything there is, one that reads what is typed and lacks what the compiler found, and one that declares nothing'
        ToolCatalogBuilder builder = new ToolCatalogBuilder()
        builder.add(ToolCatalogBuilder.ORIGIN_APPLICATION, element('main', [
                declaring('example.AuditReportTool', 'audit-report', new ToolDescriptor.Declarations(
                        [[names: ['--since', '-s'], arity: '1', type: 'string', label: 'DATE', description: 'The first day of the report'],
                         [names: ['--format'], arity: '1', type: 'string', label: 'FORMAT', description: 'The format of the report', choices: ['TEXT', 'JSON'], default: 'TEXT'],
                         [names: ['--include-deleted'], arity: '0', description: 'Lists deleted records as well'],
                         [names: ['--tag'], arity: '1', type: 'string', label: 'TAG', repeatable: true],
                         [names: ['--limit'], arity: '1', type: 'integer', label: 'LIMIT', required: true]],
                        [[label: 'OUTPUT', arity: '1', description: 'The file to write'], [label: 'MORE', arity: '0..*']],
                        [readOnlyHint: true], false, null)),
                declaring('example.ImportDataTool', 'import-data', new ToolDescriptor.Declarations(null, null, null, true, ['description', 'arguments', 'hints'])),
                declaring('example.PlainTool', 'plain', ToolDescriptor.Declarations.NONE)]))
        File file = new File(dir, ToolCatalogFile.LOCATION)
        file.parentFile.mkdirs()
        file.text = builder.toJson()

        when:
        Map<String, Map> tools = tools().collectEntries { [(it.name): it] }

        then: 'the members, in their order, each where it says something'
        document().diagnostics == []
        tools['audit-report'].keySet() as List == ['name', 'aliases', 'description', 'project', 'origin', 'target', 'startsApplication', 'registration',
                                                   'options', 'parameters', 'hints', 'commands']
        tools['import-data'].keySet() as List == ['name', 'aliases', 'description', 'project', 'origin', 'target', 'startsApplication', 'registration',
                                                  'readsInput', 'incomplete', 'commands']
        tools.plain.keySet() as List == ['name', 'aliases', 'description', 'project', 'origin', 'target', 'startsApplication', 'registration', 'commands']

        and: 'the options with every member a command of the command line has, in the order of the longest name of each'
        tools['audit-report'].options == [
                [names: ['--format'], arity: '1', type: 'string', label: 'FORMAT', required: false, description: 'The format of the report', repeatable: false, split: null,
                 choices: ['TEXT', 'JSON'], default: 'TEXT'],
                [names: ['--include-deleted'], arity: '0', type: 'boolean', label: null, required: false, description: 'Lists deleted records as well', repeatable: false,
                 split: null, choices: null, default: null],
                [names: ['--limit'], arity: '1', type: 'integer', label: 'LIMIT', required: true, description: '', repeatable: false, split: null, choices: null, default: null],
                [names: ['--since', '-s'], arity: '1', type: 'string', label: 'DATE', required: false, description: 'The first day of the report', repeatable: false,
                 split: null, choices: null, default: null],
                [names: ['--tag'], arity: '1', type: 'string', label: 'TAG', required: false, description: '', repeatable: true, split: null, choices: null, default: null]]
        tools['audit-report'].parameters == [
                [label: 'OUTPUT', arity: '1', type: 'string', description: 'The file to write', repeatable: false, split: null, choices: null],
                [label: 'MORE', arity: '0..*', type: 'string', description: '', repeatable: true, split: null, choices: null]]
        tools['audit-report'].hints == [readOnlyHint: true, destructiveHint: true, idempotentHint: false, openWorldHint: true]

        and:
        tools['import-data'].readsInput == true
        tools['import-data'].incomplete == ['description', 'arguments', 'hints']
    }

    @Unroll
    void "a tool whose declarations #what is left out, and a line says why"() {
        given:
        catalog([APPLICATION + [name: 'fine'], APPLICATION + changed])

        when:
        Map described = document()

        then:
        ((List<Map>) described.commands).findAll { it.origin != 'built-in' }*.name == ['fine']
        described.diagnostics == ["Tool 2 of ${ToolCatalogFile.LOCATION} is not used: ${why}".toString()]

        where:
        what                                     | changed                                                                                     | why
        'are not a list'                         | [options: [names: ['--x']]]                                                                 | 'its options are not a list of at most 32'
        'have an option of no long name'         | [options: [[names: ['-x'], arity: '0']]]                                                    | 'its option 1 has a name twice, no long name, or more than one short name'
        'name --help'                            | [options: [[names: ['--help'], arity: '0']]]                                                | 'its option 1 has names that are not 1 to 4 names of an option, long (two hyphens, a lower-case letter, then letters, digits and single hyphens, at most 32 characters) or short (a hyphen and one letter), none of them --help, --version, -D'
        'have a switch with a value'             | [options: [[names: ['--x'], arity: '0', type: 'string']]]                                   | 'its option 1 has a type its arity does not take: a switch is boolean, and an option that takes a value is string or integer'
        'have a description of two lines'        | [options: [[names: ['--x'], arity: '0', description: 'one\ntwo']]]                         | 'its option 1 has a description that is not one line of at most 512 characters that can be shown'
        'have a default that is no choice'       | [options: [[names: ['--x'], arity: '1', choices: ['A'], default: 'B']]]                     | 'its option 1 has a default that is not a value it takes'
        'give two options one name'              | [options: [[names: ['--x'], arity: '0'], [names: ['--y', '--x'], arity: '0']]]              | 'its options have a name two of them have'
        'have a parameter without a label'       | [parameters: [[arity: '1']]]                                                                | 'its parameter 1 has no label that is an upper-case letter followed by at most 31 upper-case letters, digits and hyphens'
        'have a required parameter after one left out' | [parameters: [[label: 'A', arity: '0..1'], [label: 'B', arity: '1']]]                 | 'it has a parameter that has to be given after one that can be left out'
        'have the rest before the last'          | [parameters: [[label: 'A', arity: '0..*'], [label: 'B', arity: '0..1']]]                    | 'it has a parameter that takes any number of values before its last'
        'have hints that are not booleans'       | [hints: [readOnlyHint: 'yes', destructiveHint: true, idempotentHint: false, openWorldHint: true]] | 'its hints are not an object with readOnlyHint, destructiveHint, idempotentHint, openWorldHint, each true or false'
        'say in a word that it reads input'      | [readsInput: 'true']                                                                        | 'it does not say with true or false whether it reads what is typed'
        'lack what there is not'                 | [incomplete: ['tests']]                                                                     | 'what it says it lacks is not a list of description, arguments, hints'
        'take no choices in any case'            | [options: [[names: ['--x'], arity: '1', caseInsensitive: true]]]                            | 'its option 1 does not say with true or false whether it takes its choices in any case, or takes in any case choices it has none of or that differ only in case'
    }

    @Unroll
    void "an option or parameter the catalog lists is used where the writer of a descriptor takes it, and only there: #declared"() {
        given:
        catalog([APPLICATION + [name: 'fine'], APPLICATION + [name: 'declaring'] + declared])
        String writer = ToolDescriptor.declarationsRefusalOf((List<Map>) (declared.options ?: []), (List<Map>) (declared.parameters ?: []))

        when:
        List<String> listed = tools()*.name

        then:
        listed.contains('declaring') == (writer == null)

        where:
        declared << [
                [options: [[names: ['--dataSource'], arity: '1', type: 'string', label: 'DATASOURCE']]],
                [options: [[names: ['--Data'], arity: '0']]],
                [options: [[names: ['--a', '-b', '-c'], arity: '0']]],
                [options: [[names: ['--a', '--b', '--c', '--d', '-e'], arity: '0']]],
                [options: [[names: ['--' + 'a' * 31], arity: '0']]],
                [options: [[names: ['--' + 'a' * 30], arity: '0']]],
                [options: [[names: ['--version'], arity: '0']]],
                [options: [[names: ['-D', '--define'], arity: '1']]],
                [options: [[names: ['--x'], arity: '1', label: 'lower']]],
                [options: [[names: ['--x'], arity: '1', label: 'A' * 32]]],
                [options: [[names: ['--x'], arity: '1', label: 'A' * 33]]],
                [options: [[names: ['--x'], arity: '1', type: 'number']]],
                [options: [[names: ['--x'], arity: '1', type: 'integer', default: '12']]],
                [options: [[names: ['--x'], arity: '1', type: 'integer', default: '1.5']]],
                [options: [[names: ['--x'], arity: '1', default: 'd' * 129]]],
                [options: [[names: ['--x'], arity: '1', required: true, default: 'd']]],
                [options: [[names: ['--x'], arity: '1', repeatable: true, default: 'd']]],
                [options: [[names: ['--x'], arity: '0', required: true]]],
                [options: [[names: ['--x'], arity: '1', choices: ['A', 'A']]]],
                [options: [[names: ['--x'], arity: '1', choices: ['not one']]]],
                [options: [[names: ['--x'], arity: '1', type: 'integer', choices: ['A']]]],
                [options: (1..33).collect { int each -> [names: ["--o${each}".toString()], arity: '0'] }],
                [options: (1..32).collect { int each -> [names: ["--o${each}".toString()], arity: '0'] }],
                [parameters: [[label: 'A', arity: '1..*'], [label: 'B', arity: '1']]],
                [parameters: [[label: 'A', arity: '1'], [label: 'A', arity: '0..1']]],
                [parameters: [[label: 'A', arity: '2']]],
                [parameters: [[label: 'A', arity: '0..*', repeatable: false]]],
                [parameters: [[label: 'A', arity: '1', type: 'boolean']]],
                [parameters: (1..17).collect { int each -> [label: "P${each}".toString(), arity: '0..1'] }],
                [parameters: [[label: 'A', arity: '1', choices: ['X', 'Y'], description: 'Chosen']]],
                [options: [[names: ['--x'], arity: '1', choices: ['gsp', 'json'], caseInsensitive: true]]],
                [options: [[names: ['--x'], arity: '1', choices: ['gsp', 'json'], caseInsensitive: false]]],
                [options: [[names: ['--x'], arity: '1', caseInsensitive: true]]],
                [options: [[names: ['--x'], arity: '1', choices: ['json', 'JSON'], caseInsensitive: true]]],
                [options: [[names: ['--x'], arity: '1', choices: ['json'], caseInsensitive: 'yes']]],
                [options: [[names: ['--x'], arity: '1', type: 'integer', default: '30']]],
                [parameters: [[label: 'A', arity: '0..*', type: 'integer']]],
                [parameters: [[label: 'A', arity: '1', choices: ['generate', 'export'], caseInsensitive: true]]],
                [parameters: [[label: 'A', arity: '1', type: 'integer', caseInsensitive: true]]]
        ]
    }

    void "the reader of the catalog has the limits of the writer of a descriptor"() {
        expect:
        ToolDeclarations.MAX_OPTIONS == ToolDescriptor.MAX_OPTIONS
        ToolDeclarations.MAX_PARAMETERS == ToolDescriptor.MAX_PARAMETERS
        ToolDeclarations.MAX_OPTION_NAMES == ToolDescriptor.MAX_OPTION_NAMES
        ToolDeclarations.MAX_OPTION_NAME == ToolDescriptor.MAX_OPTION_NAME_LENGTH
        ToolDeclarations.MAX_LABEL == ToolDescriptor.MAX_LABEL_LENGTH
        ToolDeclarations.MAX_DESCRIPTION == ToolDescriptor.MAX_DECLARATION_DESCRIPTION_LENGTH
        ToolDeclarations.MAX_CHOICES == ToolDescriptor.MAX_CHOICES
        ToolDeclarations.MAX_CHOICE == ToolDescriptor.MAX_CHOICE_LENGTH
        ToolDeclarations.MAX_DEFAULT == ToolDescriptor.MAX_DEFAULT_LENGTH
        ToolDeclarations.RESERVED == ToolDescriptor.RESERVED_OPTION_NAMES
        ToolDeclarations.HINT_NAMES == ToolDescriptor.HINTS
        ToolDeclarations.LACKING == ToolDescriptor.INCOMPLETE
        [ToolDeclarations.OPTIONS, ToolDeclarations.PARAMETERS, ToolDeclarations.HINTS, ToolDeclarations.READS_INPUT, ToolDeclarations.INCOMPLETE] ==
                [ToolDescriptor.MEMBER_OPTIONS, ToolDescriptor.MEMBER_PARAMETERS, ToolDescriptor.MEMBER_HINTS, ToolDescriptor.MEMBER_READS_INPUT, ToolDescriptor.MEMBER_INCOMPLETE]
        [ToolDeclarations.OPTIONS, ToolDeclarations.PARAMETERS, ToolDeclarations.HINTS, ToolDeclarations.READS_INPUT, ToolDeclarations.INCOMPLETE] == CommandCatalog.TOOL_MEMBERS
    }

    void "help of a tool that declares its options and parameters shows them, and says what it lacks and that it reads what is typed"() {
        given:
        catalog([APPLICATION + [name: 'audit-report', description: 'Writes the audit report',
                                options: [[names: ['--since', '-s'], arity: '1', type: 'string', label: 'DATE', description: 'The first day of the report'],
                                          [names: ['--format'], arity: '1', type: 'string', label: 'FORMAT', description: 'The format of the report', choices: ['TEXT', 'JSON'],
                                           default: 'TEXT'],
                                          [names: ['--limit'], arity: '1', type: 'integer', label: 'LIMIT', required: true, description: 'How many at most']],
                                parameters: [[label: 'OUTPUT', arity: '1', type: 'string', description: 'The file to write'],
                                             [label: 'MORE', arity: '0..*', type: 'string', description: '100% more']]],
                 APPLICATION + [name: 'import-data', readsInput: true, incomplete: ['description', 'hints']]])

        when:
        out.reset()
        int exit = ForgeCli.execute(beanContext, 'help', 'audit-report')
        List<String> lines = out.toString().readLines()
        String text = out.toString().replaceAll(/\s+/, ' ')

        then: 'the usage has the options and the parameters, as a command of the command line has them'
        exit == 0
        lines[0] == 'Usage: grails-forge-cli audit-report [-h] [--format=FORMAT] --limit=LIMIT [-s=DATE] OUTPUT [MORE...]'
        text.contains('--format=FORMAT The format of the report. Possible values: TEXT, JSON. Default: TEXT.')
        text.contains('--limit=LIMIT How many at most. Required.')
        text.contains('-s, --since=DATE The first day of the report.')
        text.contains('OUTPUT The file to write.')
        text.contains('[MORE...] 100% more.')
        text.contains('What is given after the name of the tool is held to the options and parameters below before the tool is started, and refused when it does not fit them.')
        !text.contains('--help after the name is given to the tool as well')

        when:
        out.reset()
        ForgeCli.execute(beanContext, 'help', 'import-data')
        text = out.toString().replaceAll(/\s+/, ' ')

        then: 'a tool that declares nothing takes what it is given, as before'
        out.toString().readLines()[0] == 'Usage: grails-forge-cli import-data [ARGS...]'
        text.contains('It reads what is typed: --non-interactive refuses it, and agents are not offered it.')
        text.contains('It lacks a description and its hints, which help, completion and agents read.')
        text.contains('Everything after the name of the tool is given to the tool as it is, the options too.')
    }

    void "a tool that declares an option the command line gives every command has it, and the command line is put together as before"() {
        given:
        catalog([APPLICATION + [name: 'quiet-report', options: [[names: ['--non-interactive'], arity: '0', type: 'boolean', description: 'Asks nothing'],
                                                                [names: ['--level'], arity: '1', type: 'integer', label: 'LEVEL']]]])

        when:
        Map tool = tools().find { it.name == 'quiet-report' }
        out.reset()
        int exit = ForgeCli.execute(beanContext, 'help', 'quiet-report')

        then: 'help --json has every option it declares, and its help those that are not the command line\'s'
        ((List<Map>) tool.options)*.names == [['--level'], ['--non-interactive']]
        exit == 0
        out.toString().readLines()[0] == 'Usage: grails-forge-cli quiet-report [-h] [--level=LEVEL]'
        !out.toString().contains('--plain-output')
    }

    // a descriptor with declarations, as the compiler writes it
    private static Map.Entry<String, String> declaring(String className, String name, ToolDescriptor.Declarations declarations) {
        return new AbstractMap.SimpleEntry<String, String>(className, ToolDescriptor.describe(className, name, "The ${name} tool".toString(), ToolDescriptor.TARGET_APPLICATION,
                true, [], declarations))
    }

    // the descriptor the compiler writes for a tool, by the class of the tool
    private static Map.Entry<String, String> descriptor(String className, String name, String target, boolean startsApplication, List<String> deprecated) {
        return new AbstractMap.SimpleEntry<String, String>(className, ToolDescriptor.describe(className, name, "The ${name} tool".toString(), target, startsApplication, deprecated))
    }

    // a directory of classes that registers tools under the key the compiler writes, with their descriptors
    private File element(String name, List<Map.Entry<String, String>> descriptors) {
        File element = new File(dir, "build/elements/${name}")
        new File(element, 'META-INF').mkdirs()
        new File(element, 'META-INF/grails-cli.factories').text = "${ToolDescriptor.FACTORIES_KEY_TOOL}=${descriptors*.key.join(',')}\n"
        for (Map.Entry<String, String> each : descriptors) {
            File file = new File(element, ToolDescriptor.location(each.key))
            file.parentFile.mkdirs()
            file.text = each.value
        }
        return element
    }

    private void catalog(List<Map> tools) {
        File file = new File(dir, ToolCatalogFile.LOCATION)
        file.parentFile.mkdirs()
        file.bytes = ProjectToolsSpec.doc(tools).getBytes(StandardCharsets.UTF_8)
    }

    private List<Map> tools() {
        return ((List<Map>) document().commands).findAll { it.origin != 'built-in' }
    }

    private Map document() {
        out.reset()
        assert ForgeCli.execute(beanContext, 'help', '--json') == 0
        return (Map) new JsonSlurper().parseText(out.toString().readLines().last())
    }
}

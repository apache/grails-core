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

import java.nio.file.Path

import groovy.json.JsonSlurper
import org.apache.grails.forge.cli.console.CommandCatalog
import org.apache.grails.forge.cli.console.CommandStreams
import org.apache.grails.forge.cli.console.ToolCatalogFile
import org.apache.grails.forge.cli.console.ToolCommand
import org.apache.grails.gradle.model.cli.ToolDescriptor
import org.grails.forge.cli.CommandSpec
import org.grails.forge.cli.ForgeCli
import org.grails.forge.cli.ForgeContext
import picocli.CommandLine
import spock.lang.Unroll

/**
 * What {@code help --json} says of the options and parameters of the commands besides their arity: the
 * {@code type} of each of their values, which is what a program that gives a command its values, as
 * {@code grails mcp} does, has to know; and the members a tool of the application or of a plugin has
 * for what it declares of its command line and of what it does.
 */
class HelpJsonTypeSpec extends CommandSpec {

    void "every option and every parameter says what its values are, and a switch is the one whose values are true and false"() {
        when:
        Map document = describe()
        List<Map> options = (document.options as List<Map>) + everyCommand(document.commands as List<Map>).collectMany { (it.options ?: []) as List<Map> }
        List<Map> parameters = everyCommand(document.commands as List<Map>).collectMany { (it.parameters ?: []) as List<Map> }

        then:
        !options.isEmpty()
        !parameters.isEmpty()
        (options + parameters).every { it.type in CommandCatalog.TYPES }
        options.every { (it.arity == '0') == (it.type == 'boolean') }
        parameters.every { it.type != 'boolean' && it.type != 'map' }

        and: 'it comes after the arity'
        options.every { (it.keySet() as List).indexOf('type') == (it.keySet() as List).indexOf('arity') + 1 }
        parameters.every { (it.keySet() as List).indexOf('type') == (it.keySet() as List).indexOf('arity') + 1 }
    }

    @Unroll
    void "the option #name of #command is of the type #type"() {
        when:
        Map found = option(command(describe(), command.split(' ')), name)

        then:
        found.type == type

        where:
        command         | name               || type
        'run-app'       | '--port'           || 'integer'
        'run-app'       | '--ready-timeout'  || 'integer'
        'run-app'       | '-D'               || 'map'
        'run-app'       | '--detach'         || 'boolean'
        'create-app'    | '--features'       || 'string'
        'create-app'    | '--servlet'        || 'string'
        'help'          | '--json'           || 'boolean'
        'feature add'   | '--dry-run'        || 'boolean'
    }

    void "a parameter is of the type of each of its values, as one that is given more than once"() {
        when:
        Map document = describe()

        then:
        command(document, 'create-app').parameters*.type == ['string']
        command(document, 'feature', 'add').parameters[0].repeatable
        command(document, 'feature', 'add').parameters[0].type == 'string'
    }

    @Unroll
    void "an option of the Java type #javaType is of the type #type"() {
        when:
        Map typed = command(new CommandCatalog(typedCommandLine()).describe(null, [], true), 'typed')

        then:
        option(typed, name).type == type

        where:
        name         | javaType               || type
        '--switch'   | 'boolean, no value'    || 'boolean'
        '--toggle'   | 'boolean, a value'     || 'boolean'
        '--count'    | 'int'                  || 'integer'
        '--size'     | 'Long'                 || 'integer'
        '--ratio'    | 'double'               || 'number'
        '--amount'   | 'BigDecimal'           || 'number'
        '-D'         | 'Map<String, String>'  || 'map'
        '--port'     | 'List<Integer>'        || 'integer'
        '--name'     | 'String[]'             || 'string'
        '--mode'     | 'an enum'              || 'string'
        '--path'     | 'Path'                 || 'string'
    }

    void "a parameter of whole numbers given more than once is of the type integer, and one of text of the type string"() {
        when:
        Map typed = command(new CommandCatalog(typedCommandLine()).describe(null, [], true), 'typed')

        then:
        typed.parameters*.label == ['NAME', 'NUMBERS']
        typed.parameters*.type == ['string', 'integer']
    }

    void "the types are those a descriptor of a tool gives its options and parameters"() {
        expect:
        CommandCatalog.TYPES == ToolDescriptor.TYPES
        CommandCatalog.TYPES == ['boolean', 'string', 'integer', 'number', 'map']
    }

    void "a tool has the members of what it declares where the build listed them for it and they say something, in their order before its commands"() {
        given: 'two tools the build lists: one that declares everything, and one that declares nothing'
        CommandLine root = new CommandLine(new Root())
        Map<String, Object> declared = [
                options   : [[names: ['--since'], arity: '1', type: 'string', label: 'DATE', required: false, description: 'The first day',
                              repeatable: false, split: null, choices: null, default: null]],
                parameters: [[label: 'OUTPUT', arity: '1', type: 'string', description: 'The file to write', repeatable: false, split: null, choices: null]],
                hints     : [readOnlyHint: true, destructiveHint: true, idempotentHint: false, openWorldHint: true],
                readsInput: true,
                incomplete: ['description']]
        Map<String, Object> nothing = [options: [], parameters: [], hints: [:], readsInput: false, incomplete: []]
        add(root, new Listed(ToolCatalogFile.Entry.script('audit-report'), declared))
        add(root, new Listed(ToolCatalogFile.Entry.script('clean-cache'), nothing))

        when:
        Map<String, Map> tools = (new CommandCatalog(root).describe(null, [], true).commands as List<Map>).collectEntries { [(it.name): it] }

        then:
        tools['audit-report'].keySet() as List == ['name', 'aliases', 'description', 'project', 'origin', 'target', 'startsApplication', 'registration',
                                                   'options', 'parameters', 'hints', 'readsInput', 'incomplete', 'commands']
        tools['audit-report'].subMap(CommandCatalog.TOOL_MEMBERS) == declared
        tools['clean-cache'].keySet() as List == ['name', 'aliases', 'description', 'project', 'origin', 'target', 'startsApplication', 'registration', 'commands']
        CommandCatalog.TOOL_MEMBERS == [ToolDescriptor.MEMBER_OPTIONS, ToolDescriptor.MEMBER_PARAMETERS, ToolDescriptor.MEMBER_HINTS,
                                        ToolDescriptor.MEMBER_READS_INPUT, ToolDescriptor.MEMBER_INCOMPLETE]
    }

    private Map describe() {
        CommandStreams streams = CommandStreams.capture()
        assert ForgeCli.execute(ForgeContext.of(beanContext), streams, '--plain-output', '--non-interactive', 'help', '--json') == 0
        return (Map) new JsonSlurper().parseText(streams.standardOutput())
    }

    private static CommandLine typedCommandLine() {
        CommandLine root = new CommandLine(new Root())
        root.addSubcommand(new Typed())
        return root
    }

    private static void add(CommandLine root, ToolCommand tool) {
        CommandLine.Model.CommandSpec spec = CommandLine.Model.CommandSpec.wrapWithoutInspection(tool)
        root.addSubcommand(tool.entry.name, new CommandLine(spec))
    }

    private static Map command(Map document, String... names) {
        List<Map> commands = document.commands as List<Map>
        Map found = null
        for (String name : names) {
            found = commands.find { it.name == name }
            commands = found.commands as List<Map>
        }
        return found
    }

    private static Map option(Map command, String name) {
        return (command.options as List<Map>).find { name in it.names }
    }

    private static List<Map> everyCommand(List<Map> commands) {
        return commands.collectMany { Map each -> [each] + everyCommand(each.commands as List<Map>) }
    }

    @CommandLine.Command(name = 'root')
    static class Root implements Runnable {

        @Override
        void run() {
        }
    }

    enum Mode {
        FAST, SLOW
    }

    @CommandLine.Command(name = 'typed', description = 'Takes values of every type')
    static class Typed implements Runnable {

        @CommandLine.Option(names = '--switch', description = 'A switch')
        boolean aSwitch

        @CommandLine.Option(names = '--toggle', arity = '1', description = 'A switch given its value')
        boolean toggle

        @CommandLine.Option(names = '--count', description = 'How many')
        int count

        @CommandLine.Option(names = '--size', description = 'How large')
        Long size

        @CommandLine.Option(names = '--ratio', description = 'A ratio')
        double ratio

        @CommandLine.Option(names = '--amount', description = 'An amount')
        BigDecimal amount

        @CommandLine.Option(names = '-D', description = 'Properties')
        Map<String, String> systemProperties

        @CommandLine.Option(names = '--port', description = 'Ports')
        List<Integer> ports

        @CommandLine.Option(names = '--name', description = 'Names')
        String[] names

        @CommandLine.Option(names = '--mode', description = 'A mode')
        Mode mode

        @CommandLine.Option(names = '--path', description = 'A path')
        Path path

        @CommandLine.Parameters(index = '0', paramLabel = 'NAME', description = 'A name')
        String name

        @CommandLine.Parameters(index = '1..*', paramLabel = 'NUMBERS', description = 'Numbers')
        List<Long> numbers

        @Override
        void run() {
        }
    }

    // a tool the build lists, as the build will list what it declares
    static class Listed extends ToolCommand {

        private final Map<String, Object> declared

        Listed(ToolCatalogFile.Entry entry, Map<String, Object> declared) {
            super(entry)
            this.declared = declared
        }

        @Override
        Map<String, Object> describe() {
            Map<String, Object> described = new LinkedHashMap<>(super.describe())
            Object commands = described.remove('commands')
            described.putAll(declared)
            described.put('commands', commands)
            return described
        }
    }
}

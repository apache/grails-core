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
package org.apache.grails.gradle.model.cli

import spock.lang.Specification
import spock.lang.Unroll

/**
 * What writes a descriptor and what reads one agree: nothing is written that is then refused.
 */
class ToolDescriptorSpec extends Specification {

    private static final String CLASS = 'example.GreetCommand'

    @Unroll
    def "what is written for #title is read back as it was"() {
        when:
        String json = ToolDescriptor.describe(CLASS, name, description)
        ToolDescriptor.Described read = ToolDescriptor.read(CLASS, json.getBytes('US-ASCII'))

        then:
        read.name == name
        read.description == description

        where:
        title                         | name           | description
        'a name and a description'    | 'greet'        | 'Greets someone'
        'no description'              | 'greet'        | null
        'an empty description'        | 'greet'        | ''
        'the longest name'            | 'a' * 64       | 'x' * 512
        'digits and hyphens'          | 's2-quick-2fa' | 'Says "hi" \\ to é 日本 😀'
        'markup of a command line'    | 'greet'        | '@|red not for a terminal|@ ${also.not}'
    }

    @Unroll
    def "whether a tool starts the application, and what is deprecated about it, are read back as written: #title"() {
        when:
        String json = ToolDescriptor.describe(CLASS, 'greet', 'Greets someone', target, startsApplication, deprecated)
        ToolDescriptor.Described read = ToolDescriptor.read(CLASS, json.getBytes('US-ASCII'))

        then:
        json == '{"format":1,"class":"example.GreetCommand","name":"greet","description":"Greets someone",' + members + '}\n'
        read.target == target
        read.startsApplication == starts
        read.deprecated == listed

        where:
        title                                            | target        | startsApplication | deprecated         || starts | listed             | members
        'a tool that runs in the application'            | 'application' | true              | []                 || true   | []                 | '"target":"application","startsApplication":true'
        'a tool the build runs without the application'  | 'application' | false             | null               || false  | []                 | '"target":"application","startsApplication":false'
        'a tool of a deprecated type'                    | 'application' | true              | ['type']           || true   | ['type']           | '"target":"application","startsApplication":true,"deprecated":["type"]'
        'a tool in a deprecated folder'                  | 'application' | false             | ['folder']         || false  | ['folder']         | '"target":"application","startsApplication":false,"deprecated":["folder"]'
        'both, in the order of the format, each once'    | 'application' | true              | ['folder', 'type', 'folder'] || true | ['type', 'folder'] | '"target":"application","startsApplication":true,"deprecated":["type","folder"]'
        'a standalone tool, which starts nothing'        | 'standalone'  | true              | ['type']           || false  | ['type']           | '"target":"standalone","deprecated":["type"]'
    }

    def "a descriptor without the members is of a tool that starts the application and has nothing deprecated, and what is read of it cannot be changed"() {
        when:
        ToolDescriptor.Described read = ToolDescriptor.read(CLASS, '{"format":1,"class":"example.GreetCommand","name":"greet"}'.getBytes('US-ASCII'))

        then:
        read.target == 'application'
        read.startsApplication
        read.deprecated == []

        when:
        read.deprecated << 'type'

        then:
        thrown(UnsupportedOperationException)
    }

    def "a standalone descriptor that says it starts the application is read as one that does not"() {
        expect:
        !ToolDescriptor.read(CLASS, '{"format":1,"class":"example.GreetCommand","name":"greet","target":"standalone","startsApplication":true}'.getBytes('US-ASCII')).startsApplication
    }

    def "no descriptor is written for something deprecated the format does not know"() {
        expect:
        ToolDescriptor.describe(CLASS, 'greet', 'Greets someone', 'application', true, ['type', 'name']) == null

        and: 'which the reader would refuse'
        refusal(ToolDescriptor.toJson(CLASS, 'greet', 'Greets someone', 'application', true, ['type', 'name'])) == 'has a deprecated that is not a list of type and folder'
    }

    def "the keys a tool is registered under are the one of the tool contract and the one Grails 8 wrote"() {
        expect:
        ToolDescriptor.FACTORIES_KEY_TOOL == 'org.apache.grails.core.cli.GrailsTool'
        ToolDescriptor.FACTORIES_KEY_COMMAND == 'org.apache.grails.core.cli.ApplicationCommand'
        ToolDescriptor.FACTORIES_KEYS == [ToolDescriptor.FACTORIES_KEY_TOOL, ToolDescriptor.FACTORIES_KEY_COMMAND]
        ToolDescriptor.DIRECTORY == 'META-INF/grails-tools'
        ToolDescriptor.location('example.GreetCommand') == 'META-INF/grails-tools/example.GreetCommand.json'
    }

    @Unroll
    def "the name the class #className gives is #name"() {
        expect:
        ToolDescriptor.conventionName(className) == name

        where:
        className                          || name
        'example.CreateReportTool'         || 'create-report'
        'example.CreateReportCommand'      || 'create-report'
        'example.ToolTool'                 || 'tool'
        'example.ReportToolCommand'        || 'report-tool'
        'example.ReportCommandTool'        || 'report-command'
        'CreateReportTool'                 || 'create-report'
    }

    @Unroll
    def "a description that is #title is left out, so that the reader takes the rest"() {
        when:
        String json = ToolDescriptor.describe(CLASS, 'greet', description)
        ToolDescriptor.Described read = ToolDescriptor.read(CLASS, json.getBytes('US-ASCII'))

        then:
        !json.contains('description')
        read.name == 'greet'
        read.description == null

        and: 'which is what the reader does with it, were it written'
        refusal(ToolDescriptor.toJson(CLASS, 'greet', description)) != 'was read'

        where:
        title                        | description
        'two lines'                  | 'line one\nline two'
        'two lines of Windows'       | 'line one\r\nline two'
        'with a tab'                 | 'one\ttwo'
        'with an escape sequence'    | 'red \u001b[31m'
        '600 characters'             | 'x' * 600
        'with a line separator'      | 'one two'
        'with a direction override'  | 'one ‮ two'
        'half a surrogate pair'      | 'one \ud83d'
    }

    @Unroll
    def "no descriptor is written for #title"() {
        expect:
        ToolDescriptor.describe(className, name, 'Greets someone') == null

        and: 'which the reader would refuse'
        className != CLASS || refusal(ToolDescriptor.toJson(className, name, 'Greets someone')).startsWith('has a name that cannot be')

        where:
        title                                   | className           | name
        'an empty name'                         | CLASS               | ''
        'the name the class Command gives'      | CLASS               | ToolDescriptor.conventionName('example.Command')
        'a name with upper case'                | CLASS               | 'Say Hello'
        'a name of 65 characters'               | CLASS               | 'a' * 65
        'a name that starts with a digit'       | CLASS               | '1st'
        'a name with two hyphens together'      | CLASS               | 'a--b'
        'a class name that is none'             | 'example.Greet Cmd' | 'greet'
        'a class name outside ASCII'            | 'example.Grüße'     | 'greet'
    }

    @Unroll
    def "a descriptor that #title is refused with a reason and nothing else is thrown"() {
        expect:
        refusal(content).startsWith(reason)

        where:
        title                             | content                                                              | reason
        'ends inside an escape'           | '{"format":1,"class":"example.GreetCommand","name":"greet\\'         | 'is not JSON in UTF-8'
        'ends inside a unicode escape'    | '{"format":1,"class":"example.GreetCommand","name":"\\u00'           | 'is not JSON in UTF-8'
        'has something after the object'  | '{"format":1,"class":"example.GreetCommand","name":"greet"}{}'       | 'is not JSON in UTF-8'
        'has a name twice'                | '{"format":1,"class":"example.GreetCommand","name":"a","name":"b"}'  | 'is not JSON in UTF-8'
        'has a short unicode escape'      | '{"format":1,"class":"example.GreetCommand","name":"\\u61"}'         | 'is not JSON in UTF-8'
        'is nested too deep'              | '{"format":1,"class":"example.GreetCommand","name":"a","x":[[[[1]]]]}' | 'is not JSON in UTF-8'
        'is nested without end'           | '[' * 16000                                                          | 'is not JSON in UTF-8'
        'is an array'                     | '[1]'                                                                | 'is not a JSON object'
        'is too large'                    | '{"format":1,"class":"example.GreetCommand","name":"a","x":"' + 'x' * 17000 + '"}' | 'has more than 16384 bytes'
        'is of format 2'                  | '{"format":2,"class":"example.GreetCommand","name":"greet"}'         | 'is not of format 1'
        'has the format as 1.0'           | '{"format":1.0,"class":"example.GreetCommand","name":"greet"}'       | 'is not of format 1'
        'has the format as text'          | '{"format":"1","class":"example.GreetCommand","name":"greet"}'       | 'is not of format 1'
        'is of another class'             | '{"format":1,"class":"example.Other","name":"greet"}'                | 'is the descriptor of another class'
        'has no name'                     | '{"format":1,"class":"example.GreetCommand"}'                        | 'has a name that cannot be'
        'has a number for a name'         | '{"format":1,"class":"example.GreetCommand","name":42}'              | 'has a name that cannot be'
        'has a list for a description'    | '{"format":1,"class":"example.GreetCommand","name":"a","description":["x"]}' | 'has a description that is not one line'
        'is for the console'              | '{"format":1,"class":"example.GreetCommand","name":"a","target":"console"}' | 'is for the execution target console'
        'is for nowhere'                  | '{"format":1,"class":"example.GreetCommand","name":"a","target":"no\\u001bwhere"}' | 'has an execution target that does not exist'
        'is for the target gradle'        | '{"format":1,"class":"example.GreetCommand","name":"a","target":"gradle"}' | 'has an execution target that does not exist'
        'starts the application as text'  | '{"format":1,"class":"example.GreetCommand","name":"a","startsApplication":"false"}' | 'has a startsApplication that is neither true nor false'
        'starts the application as 0'     | '{"format":1,"class":"example.GreetCommand","name":"a","startsApplication":0}' | 'has a startsApplication that is neither true nor false'
        'has one thing deprecated as text' | '{"format":1,"class":"example.GreetCommand","name":"a","deprecated":"type"}' | 'has a deprecated that is not a list of type and folder'
        'has something else deprecated'   | '{"format":1,"class":"example.GreetCommand","name":"a","deprecated":["type","name"]}' | 'has a deprecated that is not a list of type and folder'
        'has a number deprecated'         | '{"format":1,"class":"example.GreetCommand","name":"a","deprecated":[1]}' | 'has a deprecated that is not a list of type and folder'
    }

    def "members a later format adds are ignored"() {
        when:
        ToolDescriptor.Described read = ToolDescriptor.read(CLASS, ('{"format":1,"class":"example.GreetCommand","name":"greet",' +
                '"options":[{"names":["-f","--force"],"arity":"0"}],"later":{"a":[1,2.5e3,true,null]}}').getBytes('UTF-8'))

        then:
        read.name == 'greet'
        read.description == null
    }

    def "what a tool declares is written after the other members, each only where it says something, and read back as it was"() {
        given:
        ToolDescriptor.Declarations declarations = new ToolDescriptor.Declarations(
                [[names: ['--since', '-s'], arity: '1', type: 'string', label: 'DATE', required: false, description: 'The first day', repeatable: false],
                 [names: ['--format'], arity: '1', type: 'string', label: 'FORMAT', choices: ['TEXT', 'JSON'], default: 'TEXT'],
                 [names: ['--force'], arity: '0', type: 'boolean', description: '']],
                [[label: 'OUTPUT', arity: '1', type: 'string', description: 'The file'], [label: 'MORE', arity: '0..*', type: 'string']],
                [readOnlyHint: true], true, ['hints', 'description'])

        when:
        String json = ToolDescriptor.describe(CLASS, 'greet', 'Greets someone', 'application', true, [], declarations)
        ToolDescriptor.Described read = ToolDescriptor.read(CLASS, json.getBytes('US-ASCII'))

        then:
        json == '{"format":1,"class":"example.GreetCommand","name":"greet","description":"Greets someone","target":"application","startsApplication":true,' +
                '"options":[{"names":["--since","-s"],"arity":"1","type":"string","label":"DATE","description":"The first day"},' +
                '{"names":["--format"],"arity":"1","type":"string","label":"FORMAT","choices":["TEXT","JSON"],"default":"TEXT"},' +
                '{"names":["--force"],"arity":"0","type":"boolean"}],' +
                '"parameters":[{"label":"OUTPUT","arity":"1","type":"string","description":"The file"},{"label":"MORE","arity":"0..*","type":"string","repeatable":true}],' +
                '"hints":{"readOnlyHint":true,"destructiveHint":true,"idempotentHint":false,"openWorldHint":true},"readsInput":true,"incomplete":["description","hints"]}\n'
        read.declarations.options == declarations.options
        read.declarations.parameters == declarations.parameters
        read.declarations.hints == declarations.hints
        read.declarations.readsInput
        read.declarations.incomplete == ['description', 'hints']
        read.declarations.declaring
    }

    def "a tool that declares nothing and lacks nothing has the descriptor it had before declarations"() {
        expect:
        ToolDescriptor.describe(CLASS, 'greet', 'Greets someone', 'application', true, [], ToolDescriptor.Declarations.NONE) ==
                ToolDescriptor.describe(CLASS, 'greet', 'Greets someone', 'application', true, [])
        !ToolDescriptor.read(CLASS, ToolDescriptor.describe(CLASS, 'greet', 'Greets someone').getBytes('US-ASCII')).declarations.declaring
    }

    def "a descriptor written by hand, as a Java tool has, is read in the form a compiled one has"() {
        when:
        ToolDescriptor.Described read = ToolDescriptor.read(CLASS, ('{"format":1,"class":"example.GreetCommand","name":"greet",' +
                '"options":[{"description":"Replaces","arity":"0","names":["-f","--force"],"required":false}],' +
                '"parameters":[{"arity":"1..*","label":"FILES"}],"readsInput":false,"incomplete":[]}').getBytes('UTF-8'))

        then: 'the type its arity gives, in the order of the members, without what says nothing'
        read.declarations.options == [[names: ['-f', '--force'], arity: '0', type: 'boolean', description: 'Replaces']]
        read.declarations.parameters == [[label: 'FILES', arity: '1..*', type: 'string', repeatable: true]]
        read.declarations.hints == null
        !read.declarations.readsInput
        read.declarations.incomplete == []
    }

    @Unroll
    def "a descriptor whose declarations #title is refused with the rule, and nothing else is thrown"() {
        expect:
        refusal('{"format":1,"class":"example.GreetCommand","name":"greet",' + members + '}') == reason

        where:
        title                                   | members                                                                             || reason
        'are no list'                           | '"options":{"names":["--a"]}'                                                        || 'has options that are not a list'
        'have something that is no option'      | '"options":[1]'                                                                      || 'has declarations that have an option, the 1st, that is not a JSON object'
        'have an option without names'          | '"options":[{"arity":"0"}]'                                                          || 'has declarations that have an option, the 1st, that has no names'
        'have an option of arity 2'             | '"options":[{"names":["--a"],"arity":"0"},{"names":["--b"],"arity":"2"}]'            || 'has declarations that have an option, the 2nd, that has an arity that is neither 0, for a switch, nor 1, for an option that takes a value'
        'have a name with a control character'  | '"options":[{"names":["--a\\u001b"],"arity":"0"}]'                                   || 'has declarations that have an option, the 1st, that has a name that is neither a long name (two hyphens, a lower-case letter, then letters, digits and single hyphens, at most 32 characters, as --since) nor a short name (a hyphen and one letter, as -s)'
        'have a switch of the type string'      | '"options":[{"names":["--a"],"arity":"0","type":"string"}]'                          || 'has declarations that have an option, the 1st, that has a type its arity does not take: a switch is boolean, and an option that takes a value is string or integer'
        'have a parameter of arity 2'           | '"parameters":[{"label":"A","arity":"2"}]'                                           || 'has declarations that have a parameter, the 1st, that has an arity that is none of 1, 0..1, 1..*, 0..*'
        'give two options one name'             | '"options":[{"names":["--a"],"arity":"0"},{"names":["--a"],"arity":"1"}]'            || 'has declarations that say that the tool gives two options the name --a'
        'have the rest before the last'         | '"parameters":[{"label":"A","arity":"0..*"},{"label":"B","arity":"0..1"}]'           || 'has declarations that say that the tool has a parameter that takes any number of values, A, before its last'
        'have hints that are text'              | '"hints":"read-only"'                                                                || 'has hints that are not an object with readOnlyHint, destructiveHint, idempotentHint, openWorldHint, each true or false'
        'have three hints'                      | '"hints":{"readOnlyHint":true,"destructiveHint":true,"idempotentHint":true}'         || 'has hints that are not an object with readOnlyHint, destructiveHint, idempotentHint, openWorldHint, each true or false'
        'read input as text'                    | '"readsInput":"yes"'                                                                 || 'has a readsInput that is neither true nor false'
        'lack something else'                   | '"incomplete":["everything"]'                                                        || 'has an incomplete that is not a list of description, arguments, hints'
    }

    def "no descriptor is written whose declarations do not fit in one, nor for declarations that break a rule"() {
        given:
        List<Map> options = (0..<32).collect { int index -> [names: ["--o${index}".toString()], arity: '1', description: 'é' * 512] as Map }

        expect:
        ToolDescriptor.describe(CLASS, 'greet', 'Greets someone', 'application', true, [],
                new ToolDescriptor.Declarations(options, null, null, false, null)) == null
        ToolDescriptor.describe(CLASS, 'greet', 'Greets someone', 'application', true, [],
                new ToolDescriptor.Declarations(options.collect { it + [description: 'd' * 200] }, null, null, false, null)).length() <= ToolDescriptor.MAX_BYTES

        when:
        new ToolDescriptor.Declarations([[names: ['since'], arity: '1']], null, null, false, null)

        then:
        IllegalArgumentException refused = thrown()
        refused.message.startsWith('The declarations have an option, the 1st, that has a name that is neither a long name')
    }

    private static String refusal(String content) {
        try {
            ToolDescriptor.read(CLASS, content.getBytes('UTF-8'))
        } catch (ToolDescriptor.Refused refused) {
            assert refused.message ==~ /[\x20-\x7e]+/
            return refused.message
        }
        'was read'
    }
}

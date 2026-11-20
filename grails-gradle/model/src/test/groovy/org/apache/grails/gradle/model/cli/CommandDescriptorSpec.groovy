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
class CommandDescriptorSpec extends Specification {

    private static final String CLASS = 'example.GreetCommand'

    @Unroll
    def "what is written for #title is read back as it was"() {
        when:
        String json = CommandDescriptor.describe(CLASS, name, description)
        CommandDescriptor.Described read = CommandDescriptor.read(CLASS, json.getBytes('US-ASCII'))

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
    def "a description that is #title is left out, so that the reader takes the rest"() {
        when:
        String json = CommandDescriptor.describe(CLASS, 'greet', description)
        CommandDescriptor.Described read = CommandDescriptor.read(CLASS, json.getBytes('US-ASCII'))

        then:
        !json.contains('description')
        read.name == 'greet'
        read.description == null

        and: 'which is what the reader does with it, were it written'
        refusal(CommandDescriptor.toJson(CLASS, 'greet', description)) != 'was read'

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
        CommandDescriptor.describe(className, name, 'Greets someone') == null

        and: 'which the reader would refuse'
        className != CLASS || refusal(CommandDescriptor.toJson(className, name, 'Greets someone')).startsWith('has a name that cannot be')

        where:
        title                                   | className           | name
        'an empty name'                         | CLASS               | ''
        'the name the class Command gives'      | CLASS               | CommandDescriptor.conventionName('example.Command')
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
    }

    def "members a later format adds are ignored"() {
        when:
        CommandDescriptor.Described read = CommandDescriptor.read(CLASS, ('{"format":1,"class":"example.GreetCommand","name":"greet",' +
                '"options":[{"names":["-f","--force"],"arity":"0"}],"later":{"a":[1,2.5e3,true,null]}}').getBytes('UTF-8'))

        then:
        read.name == 'greet'
        read.description == null
    }

    private static String refusal(String content) {
        try {
            CommandDescriptor.read(CLASS, content.getBytes('UTF-8'))
        } catch (CommandDescriptor.Refused refused) {
            assert refused.message ==~ /[\x20-\x7e]+/
            return refused.message
        }
        'was read'
    }
}

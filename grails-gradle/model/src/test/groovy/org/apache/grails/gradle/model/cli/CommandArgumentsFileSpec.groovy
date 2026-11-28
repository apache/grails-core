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
import spock.lang.TempDir
import spock.lang.Unroll

/**
 * What the class that runs a command does with what it was started with, when a build gave the
 * arguments in a file.
 */
class CommandArgumentsFileSpec extends Specification {

    @TempDir
    File directory

    def "the arguments of the file go between what the build gave and the class of the application"() {
        given:
        File file = new File(directory, 'arguments of a command.json')
        file.setText('["a b", "", "--force", "it\'s \\"q\\"", "line one\\nline two", "\\ud83d\\ude00"]', 'UTF-8')

        expect:
        CommandArgumentsFile.expand((started + [CommandArgumentsFile.ARGUMENT + file.absolutePath]) as String[]) as List == expected

        where:
        started                                                          | expected
        ['example.Application']                                          | ['a b', '', '--force', 'it\'s "q"', 'line one\nline two', '😀', 'example.Application']
        ['hello-world', 'example.Application']                           | ['hello-world', 'a b', '', '--force', 'it\'s "q"', 'line one\nline two', '😀', 'example.Application']
    }

    @Unroll
    def "what was started without a file in last place is left as it is: #started"() {
        expect:
        CommandArgumentsFile.expand(started as String[]) as List == started

        where:
        started << [
                [],
                ['example.Application'],
                ['greet', 'a b', 'example.Application'],
                // an argument of a command is no file of arguments, wherever it is but last
                ['greet', '--grails-args-file=/nowhere', 'example.Application'],
        ]
    }

    @Unroll
    def "a file that #problem is refused, and nothing of it is repeated"() {
        given:
        File file = new File(directory, 'arguments.json')
        if (content != null) {
            file.bytes = content instanceof byte[] ? (byte[]) content : content.toString().getBytes('UTF-8')
        }

        when:
        CommandArgumentsFile.expand(['greet', 'example.Application', CommandArgumentsFile.ARGUMENT + file.absolutePath] as String[])

        then:
        CommandArgumentsFile.Refused refused = thrown()
        refused.message == message

        where:
        problem                       | content                                            | message
        'does not exist'              | null                                               | 'does not exist or is not a file.'
        'is not JSON'                 | 'greet SECRET'                                     | 'is not a JSON array of strings in UTF-8.'
        'ends inside an escape'       | '["SECRET\\'                                       | 'is not a JSON array of strings in UTF-8.'
        'has two arrays'              | '["a"]["SECRET"]'                                  | 'is not a JSON array of strings in UTF-8.'
        'has a short escape'          | '["\\u12"]'                                        | 'is not a JSON array of strings in UTF-8.'
        'is not UTF-8'                | [0x5b, 0x22, 0xff, 0x22, 0x5d] as byte[]           | 'is not a JSON array of strings in UTF-8.'
        'has half a surrogate pair'   | '["SECRET\\ud83d"]'                                | 'is not a JSON array of strings in UTF-8.'
        'has a number'                | '["greet", 1]'                                     | 'is not a JSON array of strings in UTF-8.'
        'is too large'                | '["' + 'x' * (1024 * 1024) + '"]'                  | 'has more than 1048576 bytes.'
        'has too many arguments'      | '[' + (['"a"'] * 4097).join(',') + ']'             | 'has more than 4096 arguments.'
        'has the character U+0000'    | '["SECRET\\u0000"]'                                | 'has an argument with the character U+0000 in it, which no command can be given.'
    }

    def "a file given without the class of the application in front of it is refused"() {
        when:
        CommandArgumentsFile.expand([CommandArgumentsFile.ARGUMENT + new File(directory, 'arguments.json').absolutePath] as String[])

        then:
        thrown(CommandArgumentsFile.Refused)
    }

    def "the largest file there can be is read in a time nobody notices"() {
        given: 'as many arguments as a file holds, and as many bytes'
        File file = new File(directory, 'arguments.json')
        file.setText('[' + (['"' + 'x' * 250 + '"'] * 4096).join(',') + ']', 'UTF-8')
        assert file.length() > 1000 * 1000

        expect:
        CommandArgumentsFile.read(file).size() == 4096
    }
}

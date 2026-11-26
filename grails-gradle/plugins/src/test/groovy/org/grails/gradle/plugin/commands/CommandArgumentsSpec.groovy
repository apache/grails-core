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
package org.grails.gradle.plugin.commands

import groovy.json.JsonOutput

import org.gradle.testkit.runner.BuildResult

/**
 * The arguments of a command arrive in the JVM that runs it as the strings they were given as, through
 * a build Gradle really runs: the class that runs a command is a stand-in that says what it was
 * started with.
 */
class CommandArgumentsSpec extends CommandProjectSpecification {

    // every one of these is changed, split or lost by joining the arguments with spaces and splitting them again
    private static final List<String> HOSTILE = [
            'a b',
            "it's",
            'say "hi"',
            'both \' and "',
            '$HOME ${user.home} `id` $(id)',
            '*',
            'C:\\dir\\',
            '\\',
            '--force',
            '-x',
            '-Dname=value',
            '--arg=inner',
            '-Pargs=inner',
            '--',
            '@file',
            '%41%20%',
            'a,b;c|d&e<f>g^h!i#j',
            ' leading and trailing ',
            'tab\there',
            'line one\nline two',
            'é ß 日本 😀',
            'example.Application',
    ].asImmutable()

    def "each argument given with --arg is one argument of the command, as it is"() {
        given:
        setupCommandProject()

        when:
        BuildResult result = executeTask('runCommand', (['greet'] + HOSTILE).collect { String argument -> "--arg=${argument}".toString() })

        then:
        arguments(result) == ['greet'] + HOSTILE + ['example.Application']
    }

    def "the arguments of a file given with --args-file are the arguments of the command, an empty one among them"() {
        given:
        setupCommandProject()
        List<String> given = ['greet'] + HOSTILE + ['', 'x' * 5000, 'after the empty one']
        projectFile(file, JsonOutput.toJson(given))

        when: 'the path is in the project directory unless it is absolute'
        BuildResult result = executeTask('runCommand', ["--args-file=${absolute ? new File(projectDir, file).absolutePath : file}".toString()])

        then:
        arguments(result) == given + ['example.Application']

        and: 'the JVM of the command was started with the path of the file and not with what it holds'
        started(result).size() == 2
        started(result)[0] == 'example.Application'
        started(result)[1].startsWith('--grails-args-file=')
        new File(started(result)[1].substring('--grails-args-file='.length())).canonicalFile == new File(projectDir, file).canonicalFile

        and: 'the file is the caller\'s and stays'
        new File(projectDir, file).isFile()

        where:
        file                           | absolute
        'build/tmp/args/arguments.json' | false
        'arguments of a command.json'   | true
    }

    def "what a file of arguments holds is in no command line and in no line of the log of the build"() {
        given: 'an argument nobody else is to see, and more arguments than a command line carries'
        setupCommandProject()
        List<String> given = ['greet', 'correct-horse-battery-staple'] + ['x' * 250] * 4000
        projectFile('arguments.json', JsonOutput.toJson(given))

        when: 'the build says everything it does'
        BuildResult result = executeTask('runCommand', ['--args-file=arguments.json', '--info'])

        then: 'the command has its arguments'
        arguments(result) == given + ['example.Application']

        and: 'its JVM was started with two'
        started(result).size() == 2

        and: 'the build wrote the command line of that JVM, without the argument'
        result.output.contains('--grails-args-file=')
        !result.output.contains('correct-horse-battery-staple')
    }

    def "a command with a task of its own takes its arguments in the same ways"() {
        given: 'a plugin whose companion has a command'
        setupCommandProject('''
            dependencies {
                grailsCli 'org.example:hello-plugin-cli:1.2.3'
            }
        ''')
        publish('org.example', 'hello-plugin-cli', '1.2.3', [(CLI_FACTORIES): registrations('com.example.HelloWorldCommand')])
        projectFile('arguments.json', JsonOutput.toJson(['from a file', '']))

        when:
        BuildResult options = executeTask('helloWorld', ['--arg=a b', '--arg=--loud'])
        BuildResult file = executeTask('helloWorld', ['--args-file=arguments.json'])
        BuildResult property = executeTask('helloWorld', ['-Pargs=a b'])

        then: 'the name of the command comes first'
        arguments(options) == ['hello-world', 'a b', '--loud', 'example.Application']
        arguments(file) == ['hello-world', 'from a file', '', 'example.Application']
        started(file)[0..1] == ['hello-world', 'example.Application']
        arguments(property) == ['hello-world', 'a', 'b', 'example.Application']
    }

    def "the project property args is split as it always was"() {
        given:
        setupCommandProject()

        when:
        BuildResult result = executeTask('runCommand', ["-Pargs=${property}".toString()])

        then:
        arguments(result) == expected + ['example.Application']

        where:
        property                                  | expected
        'greet Alice --loud'                      | ['greet', 'Alice', '--loud']
        'greet "Alice Smith" \'two words\' c'     | ['greet', 'Alice Smith', 'two words', 'c']
        'greet   spaced    out'                   | ['greet', 'spaced', 'out']
        'greet "" last'                           | ['greet', '', 'last']
        'greet --name=Alice -Dgrails.env=test *'  | ['greet', '--name=Alice', '-Dgrails.env=test', '*']
    }

    def "a run without arguments gives the command runner the application class alone"() {
        given:
        setupCommandProject()

        expect:
        arguments(executeTask('runCommand')) == ['example.Application']
    }

    def "a build that reads the arguments from a file reuses its configuration when the arguments change"() {
        given:
        setupCommandProject()
        projectFile('arguments.json', JsonOutput.toJson(['greet', 'first']))

        when:
        BuildResult first = executeTask('runCommand', ['--args-file=arguments.json'])
        projectFile('arguments.json', JsonOutput.toJson(['greet', 'second', 'time']))
        BuildResult second = executeTask('runCommand', ['--args-file=arguments.json'])

        then:
        first.output.contains('Configuration cache entry stored')
        arguments(first) == ['greet', 'first', 'example.Application']
        second.output.contains('Configuration cache entry reused')
        arguments(second) == ['greet', 'second', 'time', 'example.Application']
    }

    def "arguments given in two ways are refused: #options"() {
        given:
        setupCommandProject()
        projectFile('arguments.json', JsonOutput.toJson(['greet']))

        when:
        BuildResult result = runner.withArguments(['runCommand'] + options).buildAndFail()

        then:
        result.output.contains("Task 'runCommand' was given arguments in more than one way. " +
                'Use one of --arg, --args-file and the project property args (-Pargs).')
        !result.output.contains('ARGUMENTS=')

        where:
        options << [
                ['--arg=greet', '-Pargs=greet'],
                ['--arg=greet', '--args-file=arguments.json'],
                ['--args-file=arguments.json', '-Pargs=greet'],
        ]
    }

    def "a file of arguments that #problem is refused, and nothing of it is repeated"() {
        given:
        setupCommandProject()
        if (content != null) {
            projectFile('arguments.json', content)
        }

        when:
        BuildResult result = runner.withArguments('runCommand', '--args-file=arguments.json').buildAndFail()

        then:
        result.output.contains("The file given to task 'runCommand' with --args-file ${message}")
        !result.output.contains('STARTEDS=')
        !result.output.contains('SECRET')

        where:
        problem                          | content                                                    | message
        'does not exist'                 | null                                                       | 'does not exist or is not a file.'
        'is not JSON'                    | 'greet SECRET'                                             | 'is not a JSON array of strings in UTF-8.'
        'is not UTF-8'                   | [0x5b, 0x22, 0xff, 0x22, 0x5d] as byte[]                   | 'is not a JSON array of strings in UTF-8.'
        'is an object'                   | '{"SECRET": "greet"}'                                      | 'is not a JSON array of strings in UTF-8.'
        'has something that is no text'  | '["greet", "SECRET", 1]'                                   | 'is not a JSON array of strings in UTF-8.'
        'has an array in the array'      | '["greet", ["SECRET"]]'                                    | 'is not a JSON array of strings in UTF-8.'
        'is nested without end'          | '[' * 200000                                               | 'is not a JSON array of strings in UTF-8.'
        'is too large'                   | '["' + 'x' * (1024 * 1024) + '"]'                          | 'has more than 1048576 bytes.'
        'has too many arguments'         | JsonOutput.toJson((0..4096).collect { 'a' })               | 'has more than 4096 arguments.'
        'has the character U+0000'       | '["greet", "SECRET\\u0000"]'                               | 'has an argument with the character U+0000 in it, which no command can be given.'
        'has half a surrogate pair'      | '["greet", "SECRET\\ud83d"]'                               | 'is not a JSON array of strings in UTF-8.'
        'ends inside an escape'          | '["greet", "SECRET\\'                                      | 'is not a JSON array of strings in UTF-8.'
        'has a second array'             | '["greet"]["SECRET"]'                                      | 'is not a JSON array of strings in UTF-8.'
        'has something after the array'  | '["greet"]SECRET'                                          | 'is not a JSON array of strings in UTF-8.'
        'has a comma after the array'    | '["greet"],'                                               | 'is not a JSON array of strings in UTF-8.'
        'has a short escape'             | '["greet", "\\u12"]'                                       | 'is not a JSON array of strings in UTF-8.'
    }

    def "Gradle does not take an empty value for --arg, which is what the file is for"() {
        given:
        setupCommandProject()

        when:
        BuildResult result = runner.withArguments('runCommand', '--arg=greet', '--arg=').buildAndFail()

        then:
        result.output.contains("An empty argument was provided for command-line option '--arg'.")
        !result.output.contains('ARGUMENTS=')
    }

}

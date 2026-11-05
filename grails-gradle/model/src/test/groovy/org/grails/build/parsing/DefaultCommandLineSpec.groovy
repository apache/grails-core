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
package org.grails.build.parsing

import spock.lang.Specification

import grails.util.Environment

class DefaultCommandLineSpec extends Specification {

    private String originalEnv
    private String originalDefault

    void setup() {
        originalEnv = System.getProperty(Environment.KEY)
        originalDefault = System.getProperty(Environment.DEFAULT)
        System.clearProperty(Environment.KEY)
        System.clearProperty(Environment.DEFAULT)
    }

    void cleanup() {
        restore(Environment.KEY, originalEnv)
        restore(Environment.DEFAULT, originalDefault)
    }

    private static void restore(String key, String value) {
        if (value == null) {
            System.clearProperty(key)
        }
        else {
            System.setProperty(key, value)
        }
    }

    void 'a new command line has no command, arguments or options'() {
        given:
        DefaultCommandLine commandLine = new DefaultCommandLine()

        expect:
        commandLine.commandName == null
        commandLine.remainingArgs == []
        commandLine.remainingArgsArray.length == 0
        commandLine.remainingArgsString == ''
        commandLine.undeclaredOptions == [:]
        commandLine.systemProperties.isEmpty()
        commandLine.rawArguments == null
        !commandLine.environmentSet
        !commandLine.hasOption('anything')
        commandLine.optionValue('anything') == null
        commandLine.lastOption() == null
    }

    void 'the environment defaults to development and is flagged as defaulted'() {
        given:
        DefaultCommandLine commandLine = new DefaultCommandLine()

        expect:
        commandLine.environment == Environment.DEVELOPMENT.name
        System.getProperty(Environment.KEY) == Environment.DEVELOPMENT.name
        System.getProperty(Environment.DEFAULT) == 'true'
    }

    void 'the default environment honours an already set system property'() {
        given:
        System.setProperty(Environment.KEY, 'custom')

        expect:
        new DefaultCommandLine().environment == 'custom'
    }

    void 'an explicit environment wins and is published as a system property'() {
        given:
        DefaultCommandLine commandLine = new DefaultCommandLine()

        when:
        commandLine.setEnvironment('production')

        then:
        commandLine.environmentSet
        commandLine.environment == 'production'
        System.getProperty(Environment.KEY) == 'production'
        System.getProperty(Environment.DEFAULT) == 'false'
    }

    void 'the environment for a command comes from the command defaults'() {
        given:
        DefaultCommandLine commandLine = new DefaultCommandLine()
        commandLine.setCommand(command)

        expect:
        commandLine.environment == expected
        commandLine.lookupEnvironmentForCommand() == expected

        where:
        command    | expected
        'war'      | 'production'
        'test-app' | 'test'
        'run-app'  | 'development'
    }

    void 'a command without a default environment falls back to the system property'() {
        given:
        System.setProperty(Environment.KEY, 'custom')
        DefaultCommandLine commandLine = new DefaultCommandLine()
        commandLine.setCommandName('run-app')

        expect:
        commandLine.lookupEnvironmentForCommand() == 'custom'
        commandLine.environment == 'custom'
    }

    void 'setCommandName records the command, and refresh-dependencies also becomes an option'() {
        given:
        DefaultCommandLine commandLine = new DefaultCommandLine()

        when:
        commandLine.setCommandName('run-app')

        then:
        commandLine.commandName == 'run-app'
        !commandLine.hasOption(CommandLine.REFRESH_DEPENDENCIES_ARGUMENT)

        when:
        commandLine.setCommandName(CommandLine.REFRESH_DEPENDENCIES_ARGUMENT)

        then:
        commandLine.commandName == CommandLine.REFRESH_DEPENDENCIES_ARGUMENT
        commandLine.hasOption(CommandLine.REFRESH_DEPENDENCIES_ARGUMENT)
        commandLine.optionValue(CommandLine.REFRESH_DEPENDENCIES_ARGUMENT) == true
    }

    void 'declared options report their value, defaulting to true'() {
        given:
        DefaultCommandLine commandLine = new DefaultCommandLine()
        commandLine.addDeclaredOption('flag', new Option('flag', 'a flag'))
        commandLine.addDeclaredOption('level', new Option('level', 'a level'), '3')

        expect:
        commandLine.hasOption('flag')
        commandLine.optionValue('flag') == true
        commandLine.optionValue('level') == '3'
        commandLine.declaredOptions.level.option.name == 'level'
        commandLine.declaredOptions.level.value == '3'
    }

    void 'undeclared options report their value, defaulting to true'() {
        given:
        DefaultCommandLine commandLine = new DefaultCommandLine()
        commandLine.addUndeclaredOption('flag')
        commandLine.addUndeclaredOption('name', 'value')

        expect:
        commandLine.hasOption('flag')
        commandLine.optionValue('flag') == true
        commandLine.optionValue('name') == 'value'
        commandLine.undeclaredOptions == [flag: true, name: 'value']
    }

    void 'declared options take precedence over undeclared ones of the same name'() {
        given:
        DefaultCommandLine commandLine = new DefaultCommandLine()
        commandLine.addUndeclaredOption('mode', 'undeclared')
        commandLine.addDeclaredOption('mode', new Option('mode', 'the mode'), 'declared')

        expect:
        commandLine.optionValue('mode') == 'declared'
    }

    void 'lastOption returns the most recently added undeclared option'() {
        given:
        DefaultCommandLine commandLine = new DefaultCommandLine()
        commandLine.addUndeclaredOption('first')
        commandLine.addUndeclaredOption('second', 'x')

        expect:
        commandLine.lastOption().key == 'second'
        commandLine.lastOption().value == 'x'
    }

    void 'remaining arguments are exposed as a list, array and strings'() {
        given:
        DefaultCommandLine commandLine = new DefaultCommandLine()
        commandLine.addRemainingArg('one')
        commandLine.addRemainingArg('two')

        expect:
        commandLine.remainingArgs == ['one', 'two']
        commandLine.remainingArgsArray == ['one', 'two'] as String[]
        commandLine.remainingArgsString == 'one two'
        commandLine.remainingArgsLineSeparated == 'one\ntwo'
        commandLine.remainingArgsWithOptionsString == 'one two'
    }

    void 'the string with options renders flags and valued options'() {
        given:
        DefaultCommandLine commandLine = new DefaultCommandLine()
        commandLine.addRemainingArg('arg')
        commandLine.addUndeclaredOption('flag')
        commandLine.addUndeclaredOption('name', 'value')
        commandLine.addUndeclaredOption('off', Boolean.FALSE)

        expect:
        commandLine.remainingArgsWithOptionsString == 'arg -flag -name=value -off=false'
        commandLine.remainingArgsString == 'arg'
    }

    void 'system properties are stored, and grails.env also selects the environment'() {
        given:
        DefaultCommandLine commandLine = new DefaultCommandLine()

        when:
        commandLine.addSystemProperty('foo', 'bar')

        then:
        commandLine.systemProperties.getProperty('foo') == 'bar'
        !commandLine.environmentSet

        when:
        commandLine.addSystemProperty(Environment.KEY, 'test')

        then:
        commandLine.systemProperties.getProperty(Environment.KEY) == 'test'
        commandLine.environmentSet
        commandLine.environment == 'test'
    }

    void 'raw arguments are kept as given'() {
        given:
        DefaultCommandLine commandLine = new DefaultCommandLine()
        String[] args = ['a', '-b'] as String[]

        when:
        commandLine.setRawArguments(args)

        then:
        commandLine.rawArguments.is(args)
    }

    void 'parseNew carries the existing state into a freshly parsed command line'() {
        given:
        DefaultCommandLine commandLine = new DefaultCommandLine()
        commandLine.addSystemProperty('foo', 'bar')
        commandLine.addUndeclaredOption('existing', 'yes')
        commandLine.addDeclaredOption('declared', new Option('declared', 'd'))

        when:
        CommandLine parsed = commandLine.parseNew(['run-app', '-more', 'arg'] as String[])

        then:
        !parsed.is(commandLine)
        parsed.commandName == 'run-app'
        parsed.remainingArgs == []
        parsed.systemProperties.getProperty('foo') == 'bar'
        parsed.optionValue('existing') == 'yes'
        parsed.optionValue('declared') == true
        parsed.optionValue('more') == 'arg'
        !commandLine.hasOption('more')
    }

}

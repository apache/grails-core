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

import grails.util.Environment
import spock.lang.Shared
import spock.lang.Specification

/**
 * Tests for {@link CommandLineParser}
 */
class CommandLineParserSpec extends Specification {
    @Shared
    String originalGrailsEnv
    @Shared
    String originalGrailsEnvDefault

    def setup() {
        // set grails.env=development before each test
        System.setProperty(Environment.KEY, "development")
    }

    def setupSpec() {
        // save grails.env and grails.env.default keys before running this spec
        originalGrailsEnv = System.getProperty(Environment.KEY)
        originalGrailsEnvDefault = System.getProperty(Environment.DEFAULT)
    }

    def cleanupSpec() {
        // reset grails.env and grails.env.default keys after running this spec
        if (originalGrailsEnv != null) {
            System.setProperty(Environment.KEY, originalGrailsEnv)
        } else {
            System.clearProperty(Environment.KEY)
        }
        if (originalGrailsEnvDefault != null) {
            System.setProperty(Environment.DEFAULT, originalGrailsEnvDefault)
        } else {
            System.clearProperty(Environment.DEFAULT)
        }
    }

    void "Test parse string with command and args"() {
        when:
            def parser = new CommandLineParser()
            def cl = parser.parseString("run-app", "foo")

        then:
            cl.commandName == 'run-app'
            cl.environment == 'development'
            cl.systemProperties.size() == 0
            cl.remainingArgs == ['foo']
    }

    void "Test parse basic command"() {
        when:
            def parser = new CommandLineParser()
            def cl = parser.parse("run-app")

        then:
            cl.commandName == 'run-app'
            cl.environment == 'development'
            cl.systemProperties.size() == 0
            cl.remainingArgs.size() == 0
    }

    void "Test parse command with environment"() {
       when:
            def parser = new CommandLineParser()
            def cl = parser.parse("prod", "run-app")

        then:
            cl.commandName == 'run-app'
            cl.environment == 'production'
            cl.systemProperties.size() == 0
            cl.remainingArgs.size() == 0
    }

    void "Test parse command with environment and sys props"() {
       when:
            def parser = new CommandLineParser()
            def cl = parser.parse("prod", "run-app", "-DmyProp=value")

        then:
            cl.commandName == 'run-app'
            cl.environment == 'production'
            cl.systemProperties.size() == 1
            cl.systemProperties['myProp'] == 'value'
            cl.remainingArgs.size() == 0
    }

    void "Test parse command with environment and sys props and arguments"() {
       when:
            def parser = new CommandLineParser()
            def cl = parser.parse("prod", "run-app", "-DmyProp=value", "foo", "bar")

        then:
            cl.commandName == 'run-app'
            cl.environment == 'production'
            cl.systemProperties.size() == 1
            cl.systemProperties['myProp'] == 'value'
            cl.remainingArgs.size() == 2
            cl.remainingArgs == ['foo', 'bar']
            cl.remainingArgsString == "foo bar"
    }

    void "Test that options with spaces throw an exception"() {
        when:
               def parser = new CommandLineParser()
               def cl = parser.parse("prod", "run-app", "-DmyProp=value", "foo", "bar", "--dev mode")

        then:
            thrown ParseException
    }

    void "Test parse command with environment, sys props, arguments and undeclared options"() {
        when:
            def parser = new CommandLineParser()
            def cl = parser.parse("prod", "run-app", "-DmyProp=value", "foo", "bar", "--dev-mode")

        then:
            cl.commandName == 'run-app'
            cl.environment == 'production'
            cl.systemProperties.size() == 1
            cl.systemProperties['myProp'] == 'value'
            cl.remainingArgs.size() == 2
            cl.remainingArgs == ['foo', 'bar']
            cl.hasOption('dev-mode')
            cl.optionValue('dev-mode') == true
    }

    void "Test parse command with environment, sys props, arguments and undeclared options with values"() {
       when:
            def parser = new CommandLineParser()
            def cl = parser.parse("prod", "run-app", "-DmyProp=value", "foo", "bar", "--host=localhost")

        then:
            cl.commandName == 'run-app'
            cl.environment == 'production'
            cl.systemProperties.size() == 1
            cl.systemProperties['myProp'] == 'value'
            cl.remainingArgs.size() == 2
            cl.remainingArgs == ['foo', 'bar']
            cl.hasOption('host')
            cl.optionValue('host') == "localhost"
    }

    void "Test parse command with environment, sys props, arguments and undeclared options with values no equals"() {
        when:
        def parser = new CommandLineParser()
        def cl = parser.parse("prod", "run-app", "-DmyProp=value", "foo", "bar", "--host", "localhost")

        then:
        cl.commandName == 'run-app'
        cl.environment == 'production'
        cl.systemProperties.size() == 1
        cl.systemProperties['myProp'] == 'value'
        cl.remainingArgs.size() == 2
        cl.remainingArgs == ['foo', 'bar']
        cl.hasOption('host')
        cl.optionValue('host') == "localhost"
    }

    void "Test parse multiple flag values"() {
        when:
        def parser = new CommandLineParser()
        def cl = parser.parse("prod", "run-app", "--host", "localhost", "--port", "8081", "foo")

        then:
        cl.commandName == 'run-app'
        cl.environment == 'production'
        cl.remainingArgs.size() == 1
        cl.remainingArgs[0] == 'foo'
        cl.hasOption('host')
        cl.optionValue('host') == "localhost"
        cl.hasOption('port')
        cl.optionValue('port') == "8081"
        cl.lastOption().key == 'port'
        cl.lastOption().value == '8081'

    }

    void "Test parse command with environment, sys props with whitespaces, arguments and undeclared options with values"() {
        when:
            def parser = new CommandLineParser()
            def cl = parser.parse("prod", "run-app", "\"-DmyProp=value with whitespace\"", "foo", "bar", "--host=localhost")

       then:
            cl.commandName == 'run-app'
            cl.environment == 'production'
            cl.systemProperties.size() == 1
            cl.systemProperties['myProp'] == 'value with whitespace'
            cl.remainingArgs.size() == 2
            cl.remainingArgs == ['foo', 'bar']
            cl.hasOption('host')
            cl.optionValue('host') == "localhost"
    }

    void "Test parse command with sys props with whitespaces in different order"() {
        when:
            def parser = new CommandLineParser()
            def cl = parser.parse("\"-DmyProp=value with whitespace\"", "clean")

        then:
            cl.commandName == 'clean'
            cl.systemProperties.size() == 1
            cl.systemProperties['myProp'] == 'value with whitespace'
    }

    void "Test help message with declared options"() {
        when:
            def parser = new CommandLineParser()
            parser.addOption("interactive-mode", "Enabled interactive mode")
            parser.addOption("version", "Shows the vesrion")

        then:
            String ls = System.getProperty("line.separator")
            parser.optionsHelpMessage == "Available options:${ls} -interactive-mode        Enabled interactive mode${ls} -version                 Shows the vesrion${ls}"
    }

    // STRING tests

    void "Test parse string basic command"() {
        when:
            def parser = new CommandLineParser()
            def cl = parser.parseString("run-app")

        then:
            cl.commandName == 'run-app'
            cl.environment == 'development'
            cl.systemProperties.size() == 0
            cl.remainingArgs.size() == 0
    }

    void "Test parse string command with environment"() {
       when:
            def parser = new CommandLineParser()
            def cl = parser.parseString("prod run-app")

        then:
            cl.commandName == 'run-app'
            cl.environment == 'production'
            cl.systemProperties.size() == 0
            cl.remainingArgs.size() == 0
    }

    void "Test parse string command with environment and sys props"() {
       when:
            def parser = new CommandLineParser()
            def cl = parser.parseString("prod run-app -DmyProp=value")

        then:
            cl.commandName == 'run-app'
            cl.environment == 'production'
            cl.systemProperties.size() == 1
            cl.systemProperties['myProp'] == 'value'
            cl.remainingArgs.size() == 0
    }

    void "Test parse string command with environment and sys props and arguments"() {
       when:
            def parser = new CommandLineParser()
            def cl = parser.parseString("prod run-app -DmyProp=value foo bar")

        then:
            cl.commandName == 'run-app'
            cl.environment == 'production'
            cl.systemProperties.size() == 1
            cl.systemProperties['myProp'] == 'value'
            cl.remainingArgs.size() == 2
            cl.remainingArgs == ['foo', 'bar']
            cl.remainingArgsString == "foo bar"
    }

    void "Test parse string command with environment, sys props, arguments and undeclared options"() {
       when:
            def parser = new CommandLineParser()
            def cl = parser.parseString("prod run-app -DmyProp=value foo bar --dev-mode")

        then:
            cl.commandName == 'run-app'
            cl.environment == 'production'
            cl.systemProperties.size() == 1
            cl.systemProperties['myProp'] == 'value'
            cl.remainingArgs.size() == 2
            cl.remainingArgs == ['foo', 'bar']
            cl.hasOption('dev-mode')
            cl.optionValue('dev-mode') == true
    }

    void "Test parse string command with environment, sys props, arguments and undeclared options with values"() {
       when:
            def parser = new CommandLineParser()
            def cl = parser.parseString("prod run-app -DmyProp=value foo bar --host=localhost")

        then:
            cl.commandName == 'run-app'
            cl.environment == 'production'
            cl.systemProperties.size() == 1
            cl.systemProperties['myProp'] == 'value'
            cl.remainingArgs.size() == 2
            cl.remainingArgs == ['foo', 'bar']
            cl.hasOption('host')
            cl.optionValue('host') == "localhost"

    }
    
    void "Test parse string command with environment, sys props with whitespaces, arguments and undeclared options with values"() {
        when:
            def parser = new CommandLineParser()
            def cl = parser.parseString("prod run-app \"-DmyProp=value with whitespace\" foo bar --host=localhost")

       then:
            cl.commandName == 'run-app'
            cl.environment == 'production'
            cl.systemProperties.size() == 1
            cl.systemProperties['myProp'] == 'value with whitespace'
            cl.remainingArgs.size() == 2
            cl.remainingArgs == ['foo', 'bar']
            cl.hasOption('host')
            cl.optionValue('host') == "localhost"
    }

    void "Test parse string command with sys props with whitespaces in different order"() {
        when:
            def parser = new CommandLineParser()
            def cl = parser.parseString("\"-DmyProp=value with whitespace\" clean")

        then:
            cl.commandName == 'clean'
            cl.systemProperties.size() == 1
            cl.systemProperties['myProp'] == 'value with whitespace'
    }

    void "Test that parseString handles quoted arguments with double quotes"() {
       when:
            def parser = new CommandLineParser()
            def cl = parser.parseString('refresh-dependencies "file with spaces.xml" --include-sources')

        then:
            cl.commandName == 'refresh-dependencies'
            cl.systemProperties.size() == 0
            cl.remainingArgs.size() == 1
            cl.remainingArgs == ['file with spaces.xml']
    }

    void "Test that parseString handles quoted arguments with single quotes"() {
        when:
            def parser = new CommandLineParser()
            def cl = parser.parseString("refresh-dependencies 'file with spaces.xml' --include-sources")

        then:
            cl.commandName == 'refresh-dependencies'
            cl.systemProperties.size() == 0
            cl.remainingArgs.size() == 1
            cl.remainingArgs == ['file with spaces.xml']
            cl.hasOption('include-sources')
            cl.optionValue('include-sources') == true
    }

    void "Test that parseString handles quoted flags with single quotes"() {
        when:
        def parser = new CommandLineParser()
        def cl = parser.parseString("refresh-dependencies --include-sources 'file with spaces.xml'")

        then:
        cl.commandName == 'refresh-dependencies'
        cl.systemProperties.size() == 0
        cl.remainingArgs.size() == 0
        cl.hasOption('include-sources')
        cl.optionValue('include-sources') == 'file with spaces.xml'
    }


    void "Test that parseString with unbalanced double quotes throws ParseException"() {
        when:
            def parser = new CommandLineParser()
            def cl = parser.parseString("refresh-dependencies 'file with spaces.xml --include-sources")

        then:
            thrown ParseException
    }

    void "Test that parseString with unbalanced single quotes throws ParseException"() {
        when:
            def parser = new CommandLineParser()
            def cl = parser.parseString('refresh-dependencies --include-sources "file with spaces.xml')

        then:
            thrown ParseException
    }
}

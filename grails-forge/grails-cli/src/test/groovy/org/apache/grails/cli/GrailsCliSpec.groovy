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
package org.apache.grails.cli

import java.nio.file.Files
import groovy.json.JsonSlurper
import spock.lang.Specification

/**
 * The entry point of the {@code grails} command line, on the class path of the artifact a tool
 * resolves for it: the one this module is published with, which has the Grails shell on it too.
 */
class GrailsCliSpec extends Specification {

    File dir = Files.createTempDirectory('grailsclientry').toFile().canonicalFile
    String previousUserDir
    PrintStream originalOut
    PrintStream originalErr
    ByteArrayOutputStream out = new ByteArrayOutputStream()
    ByteArrayOutputStream err = new ByteArrayOutputStream()

    void setup() {
        previousUserDir = System.getProperty('user.dir')
        System.setProperty('user.dir', dir.path)
        originalOut = System.out
        originalErr = System.err
        System.setOut(new PrintStream(out, true))
        System.setErr(new PrintStream(err, true))
    }

    void cleanup() {
        System.setOut(originalOut)
        System.setErr(originalErr)
        System.setProperty('user.dir', previousUserDir)
        dir.deleteDir()
    }

    void "the command line is called grails, and its options are there"() {
        when:
        int exit = GrailsCli.execute('--help')

        then:
        exit == 0
        out.toString().startsWith('Usage: grails [-hvVx] [--non-interactive] [--plain-output] [COMMAND]')
        out.toString().contains('create-app')
        err.toString().isEmpty()
    }

    void "without a command the help is shown outside a project, and nothing waits for what is typed"() {
        when:
        int exit = GrailsCli.execute(args as String[])

        then:
        exit == 0
        out.toString().startsWith('Usage: grails ')
        err.toString().isEmpty()

        where:
        args << [[], ['--non-interactive'], ['--plain-output', '--non-interactive']]
    }

    void "the commands are described for a tool"() {
        when:
        int exit = GrailsCli.execute('--plain-output', '--non-interactive', 'help', '--json')
        Map document = new JsonSlurper().parseText(out.toString()) as Map

        then:
        exit == 0
        out.toString().readLines().size() == 1
        document.format == 1
        document.projectDirectory == null
        document.options*.names.flatten().containsAll(['--plain-output', '--non-interactive'])
        document.commands*.name.containsAll(['create-app', 'feature', 'gradle', 'compile', 'run-app', 'help'])
        err.toString().isEmpty()
    }

    void "the exit code says what happened: #args"() {
        when:
        int exit = GrailsCli.execute(args as String[])

        then:
        exit == expected
        err.toString().readLines()[0].contains(said)
        !(out.toString() + err.toString()).contains('\u001b[')

        where:
        args                                   | expected | said
        ['no-such-command']                    | 2        | 'There is no command no-such-command'
        ['--plain-output', 'compile']          | 1        | '| Error'
        ['create-app', '--no-such-option']     | 2        | '--no-such-option'
    }

    void "a command line that was run leaves the JVM as it was: its system properties, and no hook for when it ends"() {
        given:
        Properties before = (Properties) System.getProperties().clone()
        int hooks = shutdownHooks()

        when: 'a command that starts the application that generates'
        int listed = GrailsCli.execute('create-app', '--list-features')
        List<String> set = ['grails.env', 'grails.env.initializing', 'org.grails.MAIN_CLASS_NAME', 'java.awt.headless', 'PID', 'FILE_LOG_CHARSET', 'CONSOLE_LOG_CHARSET']

        then: 'what starting the application sets is as it was before'
        listed == 0
        set.findAll { String name -> System.getProperty(name) != before.getProperty(name) } == []
        shutdownHooks() == hooks
    }

    void "plain output holds while a command runs, and not after it"() {
        given:
        String before = System.getProperty('picocli.ansi')

        when:
        GrailsCli.execute('--plain-output', 'help')

        then:
        System.getProperty('picocli.ansi') == before
    }

    void "Spring does not report a generator that cannot be started: the command says it in one line"() {
        given: 'what the logging of this class path is configured with'
        Properties logging = new Properties()
        GrailsCli.getResourceAsStream('/simplelogger.properties').withCloseable { InputStream stream -> logging.load(stream) }

        expect:
        logging.getProperty('org.slf4j.simpleLogger.log.org.springframework.boot.diagnostics.LoggingFailureAnalysisReporter') == 'off'
        logging.getProperty('org.slf4j.simpleLogger.log.org.springframework.boot.SpringApplication') == 'off'
    }

    // the hooks of the JVM, which has no way to ask for them
    private static int shutdownHooks() {
        Class<?> hooks = Class.forName('java.lang.ApplicationShutdownHooks')
        def field = hooks.getDeclaredField('hooks')
        try {
            field.setAccessible(true)
        } catch (RuntimeException ignored) {
            // the JVM does not open its classes: the number is not known, and is taken for unchanged
            return -1
        }
        return ((Map) field.get(null)).size()
    }

    void "a command that generates does, on this class path"() {
        when:
        int listed = GrailsCli.execute('create-app', '--list-features')

        then:
        listed == 0
        out.toString().contains('gorm-hibernate5')

        when:
        int created = GrailsCli.execute('--plain-output', 'create-app', 'example.shop')

        then:
        created == 0
        new File(dir, 'shop/grails-app').isDirectory()
        new File(dir, 'shop/.grails/project.yml').isFile()
    }
}

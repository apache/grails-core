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
import java.util.jar.JarFile
import groovy.json.JsonSlurper
import org.apache.grails.forge.cli.GrailsCli
import spock.lang.Specification

/**
 * The entry point of the {@code grails} command line, on the class path of the artifact a tool
 * resolves for it: the one this module is published with.
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

    void "a mistake in the command line is said as the command line says it, through the entry point: #args"() {
        when:
        int exit = GrailsCli.execute(args as String[])
        List<String> lines = err.toString().readLines()

        then:
        exit == 2
        out.toString().isEmpty()
        lines.contains(said)
        lines.count { it.startsWith('Usage: grails') } == 1
        !new File(dir, 'x').exists()

        where:
        args                                              | said
        ['--plain-output', '-t', 'forge', 'create-app', 'x'] | 'grails has no option -t, and does not take forge after it'
        ['create-app', 'x', '--profile', 'rest-api']      | 'create-app has no option --profile, and does not take rest-api after it'
        ['create-app', 'x', '--profile', 'rest-api', '--other'] | 'create-app has no option --profile, and does not take rest-api or --other after it'
        ['prod', 'war']                                   | 'prod in front of a command named the environment production in the Grails shell. The environment now follows the command as ' +
                                                            '-Dgrails.env=production. Type: grails war -Dgrails.env=production'
        ['dev', 'run-app']                                | 'dev in front of a command named the environment development in the Grails shell. The environment now follows the command as ' +
                                                            '-Dgrails.env=development. Type: grails run-app -Dgrails.env=development'
        ['test', 'run-app']                               | 'test in front of a command named the environment test in the Grails shell. The environment now follows the command as ' +
                                                            '-Dgrails.env=test. Type: grails run-app -Dgrails.env=test'
        ['production', 'war']                             | 'production is the name of an environment. The environment follows the command as -Dgrails.env=production. ' +
                                                            'Type: grails war -Dgrails.env=production'
        ['development', 'run-app']                        | 'development is the name of an environment. The environment follows the command as -Dgrails.env=development. ' +
                                                            'Type: grails run-app -Dgrails.env=development'
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

    void "the option that chose between the Grails shell and Grails Forge is refused with what to do: #args"() {
        when:
        int exit = GrailsCli.execute(args as String[])

        then:
        exit == 2
        out.toString().isEmpty()
        err.toString().readLines() == [
                "grails has no option ${args[0]} since Grails 9: it chose between the Grails shell and Grails Forge, and there is one command line now. Give the command without it.".toString(),
                "Run 'grails help' for the commands"
        ]
        !new File(dir, 'shop').exists()

        where:
        args << [['-t', 'forge', 'create-app', 'example.shop'], ['--type', 'shell', 'create-app', 'example.shop'], ['-t'], ['--type']]
    }

    void "an option of a command that is spelled like it is that command's"() {
        when: 'create-app has an option --type of its own or not: the command line decides, not the launcher'
        int exit = GrailsCli.execute('create-app', '-t', 'forge')

        then:
        exit == 2
        !err.toString().contains('since Grails 9')
    }

    void "GRAILS_PREFERRED_SHELL is not read, and the artifact has no Grails shell and no class that delegates to one"() {
        expect: 'whatever the variable says, there is one command line to start'
        !classThere('org.grails.cli.GrailsCli')
        !classThere('org.springframework.boot.cli.command.CommandRunner')
        classThere('org.grails.forge.cli.ForgeCli')

        and: 'the logging of the launcher is bound by this artifact itself'
        classThere('org.slf4j.simple.SimpleLogger')
        !classThere('org.slf4j.nop.NOPServiceProvider')
        GrailsCli.classLoader.getResources('META-INF/services/org.slf4j.spi.SLF4JServiceProvider').toList().size() == 1
    }

    void "the jar of the artifact names the entry point as its start class, which is what java -jar and the Grails wrapper start"() {
        given:
        File jar = new File(System.getProperty('grails.cli.jar'))

        expect:
        new JarFile(jar).withCloseable { it.manifest.mainAttributes.getValue('Main-Class') } == 'org.apache.grails.forge.cli.GrailsCli'
        GrailsCli.name == 'org.apache.grails.forge.cli.GrailsCli'
    }

    private static boolean classThere(String name) {
        try {
            Class.forName(name, false, GrailsCli.classLoader)
            return true
        } catch (ClassNotFoundException ignored) {
            return false
        }
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

    void "the command line runs in a thread whose context class loader is not that of the command line, and leaves it"() {
        given: 'a tool that loaded the command line in a class loader of its own, as the Grails wrapper does'
        ClassLoader previous = Thread.currentThread().contextClassLoader
        ClassLoader foreign = new URLClassLoader(new URL[0], (ClassLoader) null)
        Thread.currentThread().contextClassLoader = foreign

        when: 'a command that starts the generator, which finds its configuration through the context class loader'
        int listed = GrailsCli.execute('create-app', '--list-features')

        then:
        listed == 0
        out.toString().contains('gorm-hibernate5')
        err.toString().isEmpty()
        Thread.currentThread().contextClassLoader.is(foreign)

        cleanup:
        Thread.currentThread().contextClassLoader = previous
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
        // the generator is closed without a servlet environment on this class path, and says nothing of it
        err.toString().isEmpty()
        new File(dir, 'shop/grails-app').isDirectory()
        new File(dir, 'shop/.grails/project.yml').isFile()
    }
}

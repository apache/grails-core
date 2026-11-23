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
package org.grails.forge.cli

import java.nio.file.Files
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Supplier
import groovy.json.JsonSlurper
import org.apache.grails.forge.cli.console.EarlierGrailsProject
import org.apache.grails.forge.cli.console.ConsoleProject
import org.apache.grails.forge.cli.console.PlainOutput
import org.springframework.context.ApplicationContext
import org.springframework.context.ConfigurableApplicationContext
import spock.lang.IgnoreIf
import spock.lang.Specification
import spock.lang.Unroll

/**
 * How the command line is started: what starts the application that generates and what does not,
 * what is left of the command line when that application cannot be started, the options
 * {@code --plain-output} and {@code --non-interactive}, the name the command line has, and what it
 * says in a project of an earlier Grails. The projects here are a directory with a {@code grails-app}
 * and a Gradle wrapper that says what it was run with: nothing is built.
 */
class LauncherSpec extends Specification {

    private static final String REFUSAL = 'This project is on Grails 8.1.2 (gradle.properties), and the command line of Grails 9 works with projects of Grails 9 and later. ' +
                'Use the command line of Grails 8 for this project, or upgrade the project to Grails 9 first, as the upgrade notes of the Grails guide describe; ' +
                'project migrate, which writes the record the commands of a project read, is the one command of a project that runs here'

    static final String ESCAPE = '\u001b['
    static final String BROKEN = 'the generator is broken on purpose'

    File dir = Files.createTempDirectory('grailsforgelauncher').toFile().canonicalFile
    String previousUserDir
    String previousAnsi
    PrintStream originalOut
    PrintStream originalErr
    ByteArrayOutputStream out = new ByteArrayOutputStream()
    ByteArrayOutputStream err = new ByteArrayOutputStream()
    AtomicInteger started = new AtomicInteger()

    /** An application that cannot be started, and counts how often that was tried. */
    ForgeContext broken = new ForgeContext({
        started.incrementAndGet()
        throw new IllegalStateException("${BROKEN}\nand says so in more than one line")
    } as Supplier<ApplicationContext>)

    private ForgeContext another() {
        return new ForgeContext({ throw new IllegalStateException(BROKEN) } as Supplier<ApplicationContext>)
    }

    void setup() {
        previousUserDir = System.getProperty('user.dir')
        previousAnsi = System.getProperty('picocli.ansi')
        System.setProperty('user.dir', dir.path)
        // whatever the environment of whoever runs this names as directories to use
        ConsoleProject.setSafeDirectories('')
        originalOut = System.out
        originalErr = System.err
        System.setOut(new PrintStream(out, true))
        System.setErr(new PrintStream(err, true))
    }

    void cleanup() {
        System.setOut(originalOut)
        System.setErr(originalErr)
        System.setProperty('user.dir', previousUserDir)
        if (previousAnsi == null) {
            System.clearProperty('picocli.ansi')
        } else {
            System.setProperty('picocli.ansi', previousAnsi)
        }
        ConsoleProject.setSafeDirectories(null)
        dir.deleteDir()
    }

    private void project(String wrapper = 'echo "the wrapper ran with $*"\n') {
        new File(dir, 'grails-app').mkdirs()
        File script = new File(dir, 'gradlew')
        script.text = '#!/bin/sh\n' + wrapper
        script.setExecutable(true, false)
    }

    private void record() {
        new File(dir, 'grails-forge-cli.yml').text = 'applicationType: web\ndefaultPackage: example\nfeatures: [gorm-hibernate5]\n'
    }

    void "what the start of the application sets in the JVM is put back when it is closed, and what was set since is left"() {
        given: 'an application whose start sets properties, as that of Spring does'
        System.clearProperty('launcher.spec.added')
        System.setProperty('launcher.spec.changed', 'before')
        System.setProperty('launcher.spec.changed.again', 'before')
        ForgeContext context = new ForgeContext({
            System.setProperty('launcher.spec.added', 'by the start')
            System.setProperty('launcher.spec.changed', 'by the start')
            System.setProperty('launcher.spec.changed.again', 'by the start')
            throw new IllegalStateException(BROKEN)
        } as Supplier<ApplicationContext>)
        ForgeContext started = new ForgeContext({
            System.setProperty('launcher.spec.added', 'by the start')
            System.setProperty('launcher.spec.changed', 'by the start')
            System.setProperty('launcher.spec.changed.again', 'by the start')
            return new org.springframework.context.support.GenericApplicationContext()
        } as Supplier<ApplicationContext>)

        when:
        started.get()
        System.setProperty('launcher.spec.changed.again', 'by the program')
        System.setProperty('launcher.spec.other', 'by the program')
        started.close()

        then:
        System.getProperty('launcher.spec.added') == null
        System.getProperty('launcher.spec.changed') == 'before'
        System.getProperty('launcher.spec.changed.again') == 'by the program'
        System.getProperty('launcher.spec.other') == 'by the program'

        cleanup:
        ['launcher.spec.added', 'launcher.spec.changed', 'launcher.spec.changed.again', 'launcher.spec.other'].each { System.clearProperty(it) }
        context.close()
    }

    void "plain output is taken back when the run ends, unless the program has set its own value in the meantime"() {
        when:
        System.clearProperty('picocli.ansi')
        PlainOutput.enter()
        boolean during = System.getProperty('picocli.ansi') == 'false'
        PlainOutput.leave()

        then:
        during
        System.getProperty('picocli.ansi') == null

        when:
        PlainOutput.enter()
        System.setProperty('picocli.ansi', 'true')
        PlainOutput.leave()

        then:
        System.getProperty('picocli.ansi') == 'true'
        !PlainOutput.isOn()
    }

    @IgnoreIf({ os.windows })
    void "after the name of the command run-command runs, --plain-output is that command's and not the command line's"() {
        given:
        project('echo "the wrapper ran with $*"\ncat .grails/run/command-args-0.json\n')

        when:
        int exit = ForgeCli.execute(another(), 'run-command', 'report', '--plain-output')

        then: 'it is given to the command, and Gradle is not asked for its plain console'
        exit == 0
        out.toString().contains('["report","--plain-output"]')
        !out.toString().contains('--console=plain')

        when: 'in front of the name it is the command line\'s'
        out.reset()
        exit = ForgeCli.execute(another(), 'run-command', '--plain-output', 'report')

        then:
        exit == 0
        out.toString().contains('the wrapper ran with --console=plain runCommand')
        out.toString().contains('["report"]')
    }

    @Unroll
    void "outside a project #args does not start the application that generates"() {
        when:
        int exit = ForgeCli.execute(broken, args as String[])

        then:
        exit == expected
        started.get() == 0
        !(out.toString() + err.toString()).contains(BROKEN)

        where:
        args                                  | expected
        ['--help']                            | 0
        ['-h']                                | 0
        ['help']                              | 0
        ['help', '--json']                    | 0
        ['help', 'gradle']                    | 0
        ['help', 'compile']                   | 0
        ['help', 'run-app']                   | 0
        ['help', 'feature']                   | 0
        ['help', 'feature', 'add']            | 0
        ['h']                                 | 0
        ['--version']                         | 0
        ['-V']                                | 0
        ['create-app', '--version']           | 0
        ['no-such-command']                   | 2
        ['help', 'no-such-command']           | 2
        ['--no-such-option']                  | 2
        ['create-app', '--no-such-option']    | 2
        ['create-app']                        | 2
        ['feature']                           | 2
        ['feature', 'add']                    | 1
        ['feature', 'add', 'grails-quartz']   | 1
        ['compile']                           | 1
        ['clean']                             | 1
        ['assemble']                          | 1
        ['test-app']                          | 1
        ['dependency-report']                 | 1
        ['run-app']                           | 1
        ['stop-app']                          | 1
        ['console']                           | 1
        ['shell']                             | 1
        ['run-script', 'x.groovy']            | 1
        ['run-command', 'x']                  | 1
        ['stats']                             | 1
        ['bug-report']                        | 1
        ['gradle', 'build']                   | 1
        ['gradle', '--help']                  | 1
        ['create-controller', 'Book']         | 1
    }

    @IgnoreIf({ os.windows })
    @Unroll
    void "in a project #args does not start the application that generates"() {
        given:
        project()
        record()

        when:
        int exit = ForgeCli.execute(broken, args as String[])

        then:
        exit == 0
        started.get() == 0
        !(out.toString() + err.toString()).contains(BROKEN)
        said.every { String each -> out.toString().contains(each) }

        where:
        args                               | said
        ['help']                           | ['create-controller', 'compile', 'gradle']
        ['help', '--json']                 | ['"create-controller"']
        ['help', 'create-controller']      | ['Usage: grails-forge-cli create-controller']
        ['create-controller', '--help']    | ['Usage: grails-forge-cli create-controller']
        ['compile']                        | ['the wrapper ran with classes']
        ['clean']                          | ['the wrapper ran with clean']
        ['assemble']                       | ['the wrapper ran with assemble']
        ['test-app']                       | ['the wrapper ran with']
        ['dependency-report']              | ['the wrapper ran with']
        ['gradle', 'build', '--offline']   | ['the wrapper ran with build --offline']
        ['g', 'build']                     | ['the wrapper ran with build']
        ['stats']                          | []
        ['create-controller', 'Book']      | ['grails-app/controllers/example/BookController.groovy']
        ['create-service', 'Book']         | ['grails-app/services/example/BookService.groovy']
        ['create-domain-class', 'Book']    | ['grails-app/domain/example/Book.groovy']
        ['create-unit-test', 'Book']       | ['src/test/groovy/example/BookSpec.groovy']
        ['install-templates']              | []
    }

    @IgnoreIf({ os.windows })
    void "the scripts of a project are listed and handed to its build without starting the application that generates"() {
        given:
        project()
        record()
        new File(dir, 'src/main/scripts').mkdirs()
        new File(dir, 'src/main/scripts/DataImport.groovy').text = 'println "never evaluated here"\n'

        when:
        int listed = ForgeCli.execute(broken, 'help')

        then:
        listed == 0
        out.toString().contains('data-import')
        out.toString().contains('(script of the application)')

        when: 'what follows the name is the script\'s, the options of the command line too'
        out.reset()
        int ran = ForgeCli.execute(broken, 'data-import', '--plain-output', 'orders.csv')

        then:
        ran == 0
        out.toString().contains('the wrapper ran with runScriptCommand --args-file=')
        started.get() == 0
        !(out.toString() + err.toString()).contains(BROKEN)
    }

    @IgnoreIf({ os.windows })
    void "when the application that generates cannot be started, the help, the list of commands and the commands of the build still work"() {
        given:
        project()

        expect:
        ForgeCli.execute(broken, 'help') == 0
        ForgeCli.execute(broken, '--help') == 0
        ForgeCli.execute(broken, 'help', 'gradle') == 0
        ForgeCli.execute(broken, 'compile') == 0
        ForgeCli.execute(broken, 'test-app') == 0
        ForgeCli.execute(broken, 'gradle', 'build') == 0
        started.get() == 0
        out.toString().contains('the wrapper ran with build')

        when:
        out.reset()
        int json = ForgeCli.execute(broken, 'help', '--json')
        Map document = new JsonSlurper().parseText(out.toString()) as Map

        then: 'the document describes every command, the ones that generate too'
        json == 0
        started.get() == 0
        document.commands*.name.containsAll(['create-app', 'feature', 'gradle', 'compile', 'help'])
        document.commands.find { it.name == 'create-app' }.options.find { '--features' in it.names }.description ==
                'The features to use. Possible values: those the option --list-features lists'
        err.toString().isEmpty()
    }

    @Unroll
    void "when the application that generates cannot be started, #args says so in one line and fails"() {
        when:
        int exit = ForgeCli.execute(broken, args as String[])
        int again = ForgeCli.execute(broken, args as String[])

        then:
        exit == 1
        again == 1
        List<String> lines = err.toString().readLines()
        lines.size() == 2
        lines[0] == "| Error The project generator of the command line could not be started (IllegalStateException: ${BROKEN}). " +
                'The commands that create an application or add a feature to one need it; the others do not'
        lines[1] == lines[0]

        and: 'it is tried once, and nothing was written'
        started.get() == 1
        dir.list().toList() == []

        where:
        args << [['create-app', 'example.shop'], ['create-app', '--list-features'], ['create-plugin', 'example.shop'], ['cA', 'example.shop']]
    }

    @Unroll
    void "when the application that generates cannot be started, #args still says how a command that creates is used, without the features"() {
        when:
        int exit = ForgeCli.execute(broken, args as String[])

        then:
        exit == 0
        out.toString().startsWith('Usage: grails-forge-cli create-app ')
        out.toString().contains('The features to use. Possible values:')
        out.toString().contains('--inplace')
        err.toString().isEmpty()
        started.get() == 1

        where:
        args << [['create-app', '--help'], ['help', 'create-app']]
    }

    void "after a mistake a command that creates is shown without the features being listed, so the generator is not started for it"() {
        when:
        int exit = ForgeCli.execute(broken, 'create-app', '--jdk')

        then:
        exit == 2
        started.get() == 0
        err.toString().readLines()[0].startsWith('Missing required parameter for option')
        err.toString().readLines()[1] == 'Usage: grails-forge-cli create-app [OPTIONS] [NAME]'
        err.toString().readLines()[2] == "Run 'grails-forge-cli help create-app' for the options"
        err.toString().readLines().size() == 3
    }

    void "a record of the oldest kind, which only the generator can read, is said not to be used when the generator cannot be started"() {
        given:
        project()
        new File(dir, 'grails-forge-cli.yml').text = 'profile: web\ndefaultPackage: example\n'

        when:
        int exit = ForgeCli.execute(broken, 'help')

        then:
        exit == 0
        started.get() == 1
        err.toString().readLines()[0].startsWith('| Warning grails-forge-cli.yml cannot be used: The project generator of the command line could not be started')
        out.toString().readLines()[0].startsWith('Usage: grails-forge-cli')
        !out.toString().contains('create-controller')
    }

    void "a command that generates starts the application when it runs, and only once for the commands that follow"() {
        given:
        ConfigurableApplicationContext running = null
        ForgeContext context = new ForgeContext({
            started.incrementAndGet()
            running = Application.builder().run()
            return running
        } as Supplier<ApplicationContext>)

        when:
        int help = ForgeCli.execute(context, 'help')

        then:
        help == 0
        started.get() == 0
        !context.started

        when:
        out.reset()
        int usage = ForgeCli.execute(context, 'create-app', '--help')

        then: 'the help of a command that creates lists the features, which the generator has'
        usage == 0
        started.get() == 1
        out.toString() =~ /(?s)Possible values: .*gorm-hibernate5/

        when:
        int created = ForgeCli.execute(context, 'create-app', 'example.shop')
        int listed = ForgeCli.execute(context, 'create-restapi', '--list-features')

        then:
        created == 0
        listed == 0
        started.get() == 1
        new File(dir, 'shop/grails-app').isDirectory()

        when: 'the document for a tool is the same as before the application was started'
        out.reset()
        ForgeCli.execute(context, 'help', '--json')
        Map document = new JsonSlurper().parseText(out.toString()) as Map

        then:
        document.commands.find { it.name == 'create-app' }.options.find { '--features' in it.names }.description ==
                'The features to use. Possible values: those the option --list-features lists'

        when:
        context.close()

        then:
        !running.active
        !context.started
    }

    void "launching closes the application it started, and starts none for the help"() {
        given:
        ConfigurableApplicationContext running = null
        ForgeContext context = new ForgeContext({
            started.incrementAndGet()
            running = Application.builder().run()
            return running
        } as Supplier<ApplicationContext>)

        expect:
        ForgeCli.launch(context, false, 'help') == 0
        started.get() == 0

        when:
        int exit = ForgeCli.launch(context, false, 'create-app', '--list-features')

        then:
        exit == 0
        started.get() == 1
        !running.active
    }

    void "the command line has the name it was started under"() {
        given:
        broken.commandName = 'grails'

        when:
        int help = ForgeCli.execute(broken, '--help')
        int unknown = ForgeCli.execute(broken, 'no-such-command')
        int command = ForgeCli.execute(broken, 'help', 'compile')

        then:
        help == 0
        unknown == 2
        command == 0
        out.toString().startsWith('Usage: grails [-hvVx] [--non-interactive] [--plain-output] [COMMAND]')
        out.toString().contains('Usage: grails compile ')
        err.toString().contains('Usage: grails [-hvVx] [--non-interactive] [--plain-output] [COMMAND]')
        !(out.toString() + err.toString()).contains('grails-forge-cli')
    }

    @Unroll
    void "without a command and with #args the help is shown and nothing waits for what is typed"() {
        given:
        if (inProject) {
            project()
        }
        broken.commandName = 'grails'

        when:
        int exit = ForgeCli.launch(broken, projectOnly, args as String[])

        then:
        exit == 0
        out.toString().startsWith('Usage: grails [-hvVx] [--non-interactive] [--plain-output] [COMMAND]')
        out.toString().contains('--non-interactive')
        err.toString().isEmpty()
        started.get() == 0

        where:
        args                                     | projectOnly | inProject
        ['--non-interactive']                    | false       | false
        ['--non-interactive']                    | true        | true
        ['--non-interactive', '--plain-output']  | true        | true
        ['--plain-output', '--non-interactive']  | false       | true
        []                                       | true        | false
        ['--plain-output']                       | true        | false
    }

    @IgnoreIf({ os.windows })
    @Unroll
    void "--non-interactive is accepted #place the command, and refuses the one command that reads what is typed"() {
        given:
        project()

        when:
        int compile = ForgeCli.execute(broken, *(before + ['compile'] + after))

        then:
        compile == 0
        out.toString().contains('the wrapper ran with classes')
        !out.toString().contains('non-interactive')

        when:
        out.reset()
        int shell = ForgeCli.execute(broken, *(before + ['shell'] + after))

        then: 'a mistake in the command line, and Gradle was not run'
        shell == 2
        err.toString().readLines()[0] == 'shell reads what is typed until it is left, which --non-interactive rules out: ' +
                'run it without the option, or use run-script for a script'
        err.toString().contains('Usage: grails-forge-cli shell')
        !out.toString().contains('the wrapper ran')

        where:
        place    | before                | after
        'before' | ['--non-interactive'] | []
        'after'  | []                    | ['--non-interactive']
    }

    @IgnoreIf({ os.windows })
    void "without --non-interactive shell is run"() {
        given:
        project()

        expect:
        ForgeCli.execute(broken, 'shell') == 0
        out.toString().contains('the wrapper ran with shell')
    }

    @Unroll
    void "with --plain-output #args is written without what a terminal reads, and without the option with it"() {
        given: 'an output that is taken for a terminal'
        System.setProperty('picocli.ansi', 'true')

        when:
        int plain = ForgeCli.execute(broken, *(args.collect { it == 'PLAIN' ? '--plain-output' : it }))
        String written = out.toString() + err.toString()

        then:
        plain == expected
        !written.isEmpty()
        !written.contains(ESCAPE)

        and: 'the output is as it was for the next command line'
        System.getProperty('picocli.ansi') == 'true'
        !PlainOutput.isOn()

        when:
        out.reset()
        err.reset()
        int coloured = ForgeCli.execute(broken, *(args - 'PLAIN'))

        then:
        coloured == expected
        (out.toString() + err.toString()).contains(ESCAPE)

        where:
        args                                  | expected
        ['PLAIN', '--help']                   | 0
        ['PLAIN', 'help']                     | 0
        ['help', 'PLAIN']                     | 0
        ['help', 'compile', 'PLAIN']          | 0
        ['PLAIN', 'compile']                  | 1
        ['compile', 'PLAIN']                  | 1
        ['PLAIN', 'no-such-command']          | 2
        ['no-such-command', 'PLAIN']          | 2
        ['PLAIN', 'create-app', '--no-such']  | 2
        ['create-app', 'PLAIN', '--no-such']  | 2
        ['PLAIN', 'create-app', 'example.x']  | 1
        ['PLAIN', 'gradle', 'build']          | 1
    }

    @IgnoreIf({ os.windows })
    @Unroll
    void "with --plain-output Gradle is asked for its plain console: #args"() {
        given:
        project()

        when:
        int exit = ForgeCli.execute(broken, args as String[])

        then:
        exit == 0
        out.toString().contains("the wrapper ran with ${ran}")

        where:
        args                                                   | ran
        ['--plain-output', 'compile']                          | '--console=plain classes'
        ['compile', '--plain-output']                          | '--console=plain classes'
        ['compile']                                            | 'classes'
        ['--plain-output', 'gradle', 'build']                  | '--console=plain build'
        ['--plain-output', 'gradle', 'build', '--console=rich'] | 'build --console=rich'
        ['--plain-output', 'gradle', '--console', 'rich', 'x'] | '--console rich x'
        ['gradle', 'build']                                    | 'build'
        // everything after gradle is Gradle's, which is then told what it does not know
        ['gradle', 'build', '--plain-output']                  | 'build --plain-output'
        // and after -- everything is for the build
        ['compile', '--', '--plain-output']                    | 'classes --plain-output'
    }

    void "the document for a tool lists what the command line takes in front of a command"() {
        when:
        int exit = ForgeCli.execute(broken, 'help', '--json')
        Map document = new JsonSlurper().parseText(out.toString()) as Map

        then:
        exit == 0
        document.format == 1
        document.options.find { it.names == ['--plain-output'] } == [names: ['--plain-output'], arity: '0', label: null, required: false,
                description: 'Write plain text for a program to read: no colour or other terminal markup, and no progress display of Gradle.',
                repeatable: false, split: null, choices: null, default: null]
        document.options.find { it.names == ['--non-interactive'] }.arity == '0'
        document.options.find { it.names == ['-h', '--help'] } != null

        and: 'a command takes them after its name too, except the one that gives everything to Gradle'
        document.commands.find { it.name == 'compile' }.options.any { it.names == ['--plain-output'] }
        document.commands.find { it.name == 'create-app' }.options.any { it.names == ['--non-interactive'] }
        document.commands.find { it.name == 'feature' }.commands.find { it.name == 'add' }.options.any { it.names == ['--plain-output'] }
        document.commands.find { it.name == 'gradle' }.options == []
    }

    @IgnoreIf({ os.windows })
    void "in a project of a Grails before the first of this command line the commands of a project are refused, and the help says why"() {
        given:
        project()
        new File(dir, 'gradle.properties').text = 'org.gradle.caching=true\ngrailsVersion=8.1.2\n'

        when: 'a command that creates a class, which is one of this version'
        int exit = ForgeCli.execute(broken, 'create-controller', 'Book')

        then: 'nothing is created, and the one line says why and what to do'
        exit == 1
        err.toString().readLines() == ['| Error ' + REFUSAL]
        out.toString().isEmpty()
        !new File(dir, 'grails-app/controllers').exists()

        when: 'the same context, as in the interactive shell: it is refused each time'
        err.reset()
        exit = ForgeCli.execute(broken, 'create-controller', 'Book')

        then:
        exit == 1
        err.toString().readLines() == ['| Error ' + REFUSAL]

        when: 'the help is shown, and says it first'
        err.reset()
        exit = ForgeCli.execute(another(), 'help')

        then:
        exit == 0
        err.toString().readLines()[0] == '| Warning ' + REFUSAL
        out.toString().readLines()[0].startsWith('Usage: ')

        when: 'the document for a tool is nothing but the document, and says it there'
        out.reset()
        err.reset()
        ForgeCli.execute(another(), 'help', '--json')
        Map document = new JsonSlurper().parseText(out.toString()) as Map

        then:
        document.diagnostics.any { String line -> line == REFUSAL }
        err.toString().isEmpty()
    }

    @IgnoreIf({ os.windows })
    @Unroll
    void "#line is not run in a project of a Grails before the first of this command line, with or without a record"() {
        given:
        project()
        new File(dir, 'gradle.properties').text = 'grailsVersion=8.1.2\n'
        if (state) {
            new File(dir, '.grails').mkdirs()
            new File(dir, '.grails/project.yml').text = 'not read here\n'
        }

        when:
        int exit = ForgeCli.execute(another(), line as String[])

        then: 'the wrapper of the project is not started'
        exit == 1
        err.toString().readLines() == ['| Error ' + REFUSAL]
        !out.toString().contains('the wrapper ran')

        where:
        line                             | state
        ['compile']                      | false
        ['gradle', 'build']              | false
        ['test-app', '--unit']           | false
        ['stats']                        | false
        ['run-command', 'dbm-update']    | false
        ['dbm-update']                   | false
        ['feature', 'add', 'cache']      | false
        ['project', 'show']              | false
        ['project', 'regenerate', '--diff'] | false
        ['compile']                      | true
        ['create-controller', 'Book']    | true
    }

    @IgnoreIf({ os.windows })
    @Unroll
    void "#line is run in a project of a Grails before the first of this command line all the same"() {
        given:
        project()
        new File(dir, 'gradle.properties').text = 'grailsVersion=8.1.2\n'

        when:
        int exit = ForgeCli.execute(another(), line as String[])

        then:
        exit == 0
        !(out.toString() + err.toString()).contains('This project is on Grails')

        where:
        line << [['--version'], ['-V']]
    }

    void "a project of the first version of this command line or a later one is told of a command line that is later still, and one before it is not"() {
        given:
        project()
        new File(dir, 'gradle.properties').text = "grailsVersion=${version}\n"

        expect:
        (EarlierGrailsProject.refusal(dir.toPath(), commandLine) != null) == refused
        (EarlierGrailsProject.guidance(dir.toPath(), commandLine) != null) == told
        (EarlierGrailsProject.earlierVersion(dir.toPath()) != null) == refused

        where:
        version   | commandLine      | refused | told
        '8.1.2'   | '9.0.0'          | true    | false
        '8.1.2'   | '10.0.0'         | true    | false
        '7.0.4'   | '9.0.0-SNAPSHOT' | true    | false
        '9.0.0'   | '9.0.3'          | false   | false
        '9.2.1'   | '10.0.0'         | false   | true
        '10.0.0'  | '10.1.0'         | false   | false
        '10.0.0'  | '9.0.0'          | false   | false
    }

    @IgnoreIf({ os.windows })
    @Unroll
    void "the version of Grails of a project is the one Gradle reads: #what"() {
        given: 'a build of several projects, of which the one the command is run in is one'
        new File(dir, 'settings.gradle').text = "include 'app'\n"
        File app = new File(dir, 'app')
        new File(app, 'grails-app').mkdirs()
        if (ofBuild != null) {
            new File(dir, 'gradle.properties').text = ofBuild
        }
        if (ofProject != null) {
            new File(app, 'gradle.properties').text = ofProject
        }
        System.setProperty('user.dir', app.path)

        when:
        ForgeCli.execute(another(), 'help')
        List<String> warnings = err.toString().readLines().findAll { it.contains('This project is on Grails') }

        then:
        warnings*.replaceFirst(/.*This project is on Grails (\S+) .*/, '$1') == (version == null ? [] : [version])

        where:
        what                                               | ofBuild                                      | ofProject                  | version
        'in the properties of the build above the project' | 'grailsVersion=8.1.2\n'                      | null                       | '8.1.2'
        'in those of the project before those of the build' | 'grailsVersion=9.0.0\n'                     | 'grailsVersion=7.0.4\n'    | '7.0.4'
        'the project of this version, whatever the build says' | 'grailsVersion=7.0.4\n'                  | 'grailsVersion=9.0.0\n'    | null
        'the last of two in one file'                      | 'grailsVersion=7.0.0\ngrailsVersion=9.0.0\n' | null                       | null
        'the last of two, which is the earlier one'        | 'grailsVersion=9.0.0\ngrailsVersion=7.0.0\n' | null                       | '7.0.0'
        'none anywhere'                                    | 'org.gradle.caching=true\n'                  | null                       | null
    }

    @IgnoreIf({ os.windows })
    @Unroll
    void "nothing is said of the version of Grails of a project #what"() {
        given:
        project()
        if (properties != null) {
            new File(dir, 'gradle.properties').text = properties
        }
        if (state) {
            new File(dir, '.grails').mkdirs()
            new File(dir, '.grails/project.yml').text = 'not read here\n'
        }

        when:
        int exit = ForgeCli.execute(broken, 'help')

        then:
        exit == 0
        !(out.toString() + err.toString()).contains('This project is on Grails')
        out.toString().readLines()[0].startsWith('Usage: ')

        where:
        what                                       | properties                          | state
        'of this version'                          | 'grailsVersion=9.0.0-SNAPSHOT\n'    | false
        'of a later version'                       | 'grailsVersion=10.0.0\n'            | false
        'that does not say which it is on'         | 'org.gradle.caching=true\n'         | false
        'without properties of its build'          | null                                | false
        'whose version is no version'              | 'grailsVersion=\u001b[31mold\n'     | false
        'of this version that has a record'        | 'grailsVersion=9.0.0\n'             | true
    }

    @IgnoreIf({ os.windows || !ForkedCli.available() })
    void "the program does not load the application for the help or the commands of the build, and writes plain text where the output is no terminal"() {
        given:
        project()
        File written = new File(dir, 'out.txt')
        File classes = new File(dir, 'classes.log')

        when:
        Process program = ForkedCli.builder(dir, ["-Xlog:class+load=info:file=${classes.path}".toString()], *args)
                .redirectOutput(written).redirectErrorStream(true).redirectInput(ProcessBuilder.Redirect.from(new File('/dev/null'))).start()
        boolean ended = program.waitFor(300, TimeUnit.SECONDS)
        String loaded = classes.text

        then:
        ended
        program.exitValue() == 0
        written.text.contains(said)
        !written.text.contains(ESCAPE)
        loaded.contains('org.grails.forge.cli.ForgeCli ')
        loaded.contains('org.springframework.boot.SpringApplication ') == application
        loaded.contains('grails.boot.GrailsApp ') == application

        cleanup:
        program?.destroyForcibly()

        where:
        args                                | said                                 | application
        ['--help']                          | 'Usage: grails-forge-cli'            | false
        ['help', '--json']                  | '"commands"'                         | false
        ['--version']                       | 'Grails Version:'                    | false
        ['--non-interactive']               | 'Usage: grails-forge-cli'            | false
        ['compile']                         | 'the wrapper ran with classes'       | false
        ['gradle', 'build']                 | 'the wrapper ran with build'         | false
        ['create-app', '--list-features']   | 'gorm-hibernate5'                    | true
    }
}

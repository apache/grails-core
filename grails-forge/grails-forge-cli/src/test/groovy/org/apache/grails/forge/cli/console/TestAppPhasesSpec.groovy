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
package org.apache.grails.forge.cli.console

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.grails.forge.application.ApplicationType
import org.grails.forge.cli.CommandFixture
import org.grails.forge.cli.CommandSpec
import org.grails.forge.cli.ForgeCli
import picocli.AutoComplete
import picocli.CommandLine
import spock.lang.Shared
import spock.lang.Unroll

/**
 * The test phases of a project on the command line of {@code test-app}: a phase is selected by a
 * switch of its name, where the build of the project has listed its phases and where it has not.
 * The projects have the wrapper of a generated application around the build of
 * {@link ConsoleCommandsSpec}, whose task {@code testApp} says what it was asked for.
 */
class TestAppPhasesSpec extends CommandSpec implements CommandFixture {

    // One Gradle daemon, of this specification alone, runs the builds of its features: they have nothing to
    // compile, and without it every one of them would wait for a JVM of Gradle to start. A feature that looks
    // at the process a build runs in asks for a build without a daemon.
    @Shared
    IsolatedGradle daemon = new IsolatedGradle()

    void cleanupSpec() {
        daemon.close()
    }

    static final List<Map> PHASES = [[name: 'unit', task: 'test'], [name: 'integration', task: 'integrationTest'], [name: 'end-to-end', task: 'endToEndTest'],
                                     [name: 'functional', task: 'functionalTest'], [name: 'smoke', task: 'smoke'], [name: 'clean', task: 'cleanTest'],
                                     [name: 'tests', task: 'tests']]

    PrintStream originalOut
    PrintStream originalErr
    ByteArrayOutputStream out = new ByteArrayOutputStream()

    void setup() {
        originalOut = System.out
        originalErr = System.err
        System.setOut(new PrintStream(out, true))
        System.setErr(new PrintStream(out, true))
        plainProject()
    }

    void cleanup() {
        System.setOut(originalOut)
        System.setErr(originalErr)
        ConsoleProject.setOwnedByUser(null)
    }

    @Unroll
    void "where the build has listed nothing, #line asks the task of the phases for #requested"() {
        when:
        int exit = ForgeCli.execute(beanContext, line as String[])

        then:
        exit == 0
        lineOf('requested=') == requested
        errors() == []
        out.toString().contains('| Tests PASSED')

        where:
        line                                                               | requested
        ['test-app', '--functional']                                       | '<testApp><--phase><functional>'
        ['test-app', '--unit', '--functional']                             | '<testApp><--phase><unit><--phase><functional>'
        ['test-app', '--end-to-end', '--integration', '-unit']             | '<testApp><--phase><unit><--phase><integration><--phase><end-to-end>'
        ['test-app', '--smoke', '--functional', '--smoke']                 | '<testApp><--phase><smoke><--phase><functional>'
        ['test-app', 'BookSpec', '--functional', '--clean', '--debug-jvm'] | '<testApp><--phase><functional><--tests><BookSpec><--rerun-tests><--debug-jvm>'
        ['test-app', '--functional', '--tests=*Book*', '-Dfoo=--smoke']    | '<testApp><--phase><functional><--tests><*Book*>'
        ['-x', 'test-app', '--verbose', '--api2']                          | '<testApp><--phase><api2>'
    }

    @Unroll
    void "where the build has listed nothing, #typed is refused as the mistyped option #meant and nothing is run"() {
        when:
        int exit = ForgeCli.execute(beanContext, 'test-app', typed)

        then:
        exit == 2
        out.toString().contains("test-app has no option ${typed}. Did you mean: ${meant}?")
        out.toString().contains("If the project has a test phase of that name, the task ${task}, the switch is taken once the build has listed its phases " +
                "(grails-forge-cli gradle grailsCommandCatalog), and the phase is run by its task with: grails-forge-cli gradle ${task}")
        out.toString().contains('Usage:')
        !out.toString().contains('requested=')

        where:
        typed            | meant           | task
        '--continous'    | '--continuous'  | 'continousTest'
        '--integraton'   | '--integration' | 'integratonTest'
        '--debug-jmv'    | '--debug-jvm'   | 'debugJmvTest'
        '--units'        | '--unit'        | 'unitsTest'
        '--cleen'        | '--clean'       | 'cleenTest'
        '--test'         | '--tests'       | 'testTest'
        '--verbos'       | '--verbose'     | 'verbosTest'
        '--helpp'        | '--help'        | 'helppTest'
    }

    @Unroll
    void "#line is not the switch of a phase: the switches of the options the command took with one dash are no pattern for a phase"() {
        when:
        int exit = ForgeCli.execute(beanContext, line as String[])

        then:
        exit == 2
        out.toString().contains('Usage:')
        !out.toString().contains('requested=')

        where:
        line << [['test-app', '-functional'], ['test-app', '--Functional'], ['test-app', '--functional=true'], ['test-app', '--functional-'], ['test-app', '--2e']]
    }

    @Unroll
    void "the phases the build listed are options of the command: #line asks for #requested"() {
        given:
        catalog(PHASES)

        when:
        int exit = ForgeCli.execute(beanContext, line as String[])

        then:
        exit == 0
        lineOf('requested=') == requested
        errors() == []

        where:
        line                                                    | requested
        ['test-app']                                            | '<testApp>'
        ['test-app', '--functional']                            | '<testApp><--phase><functional>'
        ['test-app', '--smoke', '--end-to-end', '--unit']       | '<testApp><--phase><unit><--phase><smoke><--phase><end-to-end>'
        ['test-app', '--functional', 'A', '--integration']      | '<testApp><--phase><integration><--phase><functional><--tests><A>'
        // an option of the command is the option, whatever phases the project has
        ['test-app', '--clean', '--tests', 'A']                 | '<testApp><--tests><A><--rerun-tests>'
    }

    void "the help of the command lists the phases the build listed, and says how a phase is run whose switch is an option of the command"() {
        given:
        catalog(PHASES)

        when:
        int exit = ForgeCli.execute(beanContext, 'help', 'test-app')
        String help = out.toString().readLines()*.trim().join(' ').replaceAll(/ +/, ' ')

        then:
        exit == 0
        help.contains('--functional Run the test phase functional: the task functionalTest.')
        help.contains('--end-to-end Run the test phase end-to-end: the task endToEndTest.')
        help.contains('--smoke Run the test phase smoke: the task smoke.')
        help.contains('-unit, --unit Run the unit tests: the task test.')
        help.contains('The test phase clean (the task cleanTest) has no switch, since --clean is an option of test-app. It is run with: grails-forge-cli gradle cleanTest')
        help.contains('The test phase tests (the task tests) has no switch, since --tests is an option of test-app. It is run with: grails-forge-cli gradle tests')
        !help.contains('once the build has listed them')
        !help.contains('may have changed')

        when: 'for a tool, and for the completion of the command line'
        out.reset()
        ForgeCli.execute(beanContext, 'help', '--json')
        Map testApp = ((Map) new JsonSlurper().parseText(out.toString())).commands.find { it.name == 'test-app' }
        List<CharSequence> completions = []
        AutoComplete.complete(ForgeCli.createCommandLine(beanContext, true, true).getCommandSpec(), ['test-app', ''] as String[], 1, 0, 0, completions)

        then:
        testApp.options.find { it.names == ['--functional'] }.description == 'Run the test phase functional: the task functionalTest.'
        testApp.options.count { it.names.contains('--clean') } == 1
        completions*.toString().containsAll(['--functional', '--smoke', '--end-to-end'])
    }

    @Unroll
    void "with the phases of the build listed, #typed is refused: nothing is run, and what there is and what may be meant is said"() {
        given:
        catalog(PHASES)

        when:
        int exit = ForgeCli.execute(beanContext, 'test-app', typed)
        List<String> lines = out.toString().readLines()

        then:
        exit == 2
        lines[0] == "test-app has no option ${typed}, and the project has no test phase of that name: its build lists " +
                "--unit, --integration, --end-to-end, --functional, --smoke.${meant ? ' Did you mean: ' + meant + '?' : ''}"
        lines[1] == 'A phase that was added since is listed with: grails-forge-cli gradle grailsCommandCatalog'
        lines.any { it.startsWith('Usage:') }
        !out.toString().contains('requested=')

        where:
        typed            | meant
        '--acceptance'   | null
        '--functinal'    | '--functional'
        '--smoek'        | '--smoke'
        '--continous'    | '--continuous'
        '--rerun'        | null
    }

    void "a list that may be out of date still gives its phases their switches, and a switch it does not have is taken for a phase that was added since"() {
        given:
        catalog(PHASES)
        new File(dir, 'build.gradle').setLastModified(System.currentTimeMillis() + 60_000)

        when:
        int exit = ForgeCli.execute(beanContext, 'test-app', '--functional', '--acceptance')

        then:
        exit == 0
        lineOf('requested=') == '<testApp><--phase><acceptance><--phase><functional>'

        when:
        out.reset()
        ForgeCli.execute(beanContext, 'test-app', '--help')
        String help = out.toString().readLines()*.trim().join(' ').replaceAll(/ +/, ' ')

        then:
        help.contains('--functional Run the test phase functional: the task functionalTest.')
        help.contains('The test phases may have changed: build.gradle is newer than .grails/cache/command-catalog.json. To list them again: grails-forge-cli gradle grailsCommandCatalog')
    }

    @Unroll
    void "a list that #says nothing of the phases is as none: a switch is taken for a phase, and the help says how the phases are listed"() {
        given:
        if (content != null) {
            File file = new File(dir, '.grails/cache/command-catalog.json')
            file.parentFile.mkdirs()
            file.text = content
        }

        when:
        int exit = ForgeCli.execute(beanContext, 'test-app', '--functional')

        then:
        exit == 0
        lineOf('requested=') == '<testApp><--phase><functional>'

        when:
        out.reset()
        ForgeCli.execute(beanContext, 'help', 'test-app')
        String help = out.toString().readLines()*.trim().join(' ').replaceAll(/ +/, ' ')

        then:
        !help.contains('--functional Run')
        help.contains('for another phase the name of its task without the ending Test, as --functional for functionalTest')
        help.contains('The test phases the project adds have a switch each, and are listed here once the build has listed them: grails-forge-cli gradle grailsCommandCatalog')

        where:
        says               | content
        'is not there and' | null
        'says'             | JsonOutput.toJson([format: 1, complete: true, diagnostics: [], commands: []])
        'is not read and'  | 'not a catalog'
    }

    void "what a list says of a phase that the task does not write is not used: the phase has no switch from it"() {
        given:
        catalog([[name: 'unit', task: 'test'], [name: 'Upper', task: 'upperTest'], [name: 'esc', task: 'a\u001b[31m'], [name: 'functional', task: 'functionalTest'],
                 [name: 'functional', task: 'other'], 'smoke', [name: 'spaced', task: 'a task'], [name: 'no-task']])

        when:
        CommandLine.Model.CommandSpec spec = ForgeCli.createCommandLine(beanContext, true, true).getSubcommands().get('test-app').getCommandSpec()

        then:
        spec.options().collect { it.longestName() }.sort() ==
                ['--clean', '--continuous', '--debug-jvm', '--functional', '--help', '--integration', '--non-interactive', '--plain-output', '--stacktrace', '--tests',
                 '--unit', '--verbose', '--version', '-D']
        spec.findOption('--functional').description() == ['Run the test phase functional: the task functionalTest.'] as String[]
    }

    void "when the build fails after a switch was taken for a phase, a note says that it was and how an option of Gradle is given"() {
        when:
        int exit = ForgeCli.execute(beanContext, 'test-app', '--acceptance', '--info', '--unit', '-Dfail=true')

        then:
        exit == 1
        lineOf('requested=') == '<testApp><--phase><unit><--phase><acceptance><--phase><info>'
        errors() == ['| Error Gradle did not succeed: it ended with exit code 1']
        notes() == ['| Note --acceptance, --info are no options of test-app and were taken for test phases of the project: the task acceptanceTest for --acceptance. ' +
                            'If Gradle said that the project has no such phase, it named the phases there are; an option of Gradle is given after --, ' +
                            'as in grails-forge-cli test-app -- --info']

        when: 'the phases are the ones the command has itself, or the build listed'
        out.reset()
        catalog(PHASES)
        exit = ForgeCli.execute(beanContext, 'test-app', '--unit', '--functional', '-Dfail=true')

        then:
        exit == 1
        notes() == []
    }

    void "in a project whose build has no task for the phases, Gradle says so, and a project that names an earlier Grails is not run at all"() {
        given: 'a build as one with the Grails Gradle plugin of an earlier version: it has the test tasks and no testApp'
        File build = new File(dir, 'build.gradle')
        build.text = build.text.replace("tasks.register('testApp', RecordedPhases)", '')

        when:
        int exit = ForgeCli.execute(beanContext, 'test-app', '--unit')

        then: 'nothing is run in place of it'
        exit == 1
        out.toString().contains("'testApp' not found")
        !out.toString().contains('ran test')
        errors() == ['| Error Gradle did not succeed: it ended with exit code 1']
        notes() == []

        when: 'the project says that it is on an earlier Grails'
        out.reset()
        new File(dir, 'gradle.properties') << 'grailsVersion=7.0.5\n'
        exit = ForgeCli.execute(beanContext, 'test-app', '--unit')

        then: 'Gradle is not started, and the one line says which version the command line is for'
        exit == 1
        !out.toString().contains("'testApp' not found")
        errors().size() == 1
        errors()[0].startsWith('| Error This project is on Grails 7.0.5 (gradle.properties), and the command line of Grails ')
    }

    private void catalog(List phases) {
        File file = new File(dir, '.grails/cache/command-catalog.json')
        file.parentFile.mkdirs()
        file.text = JsonOutput.toJson([format: 1, complete: true, diagnostics: [], commands: [], testPhases: phases])
    }

    private String lineOf(String start) {
        List<String> lines = out.toString().readLines().findAll { String line -> line.startsWith(start) }
        assert lines.size() == 1
        // A task of the project of these specifications is named as that of the root of its build, :classes for
        // classes, because the project has the settings of a build: MultiProjectBuildSpec holds that, and here
        // the task is read without the mark
        String line = lines[0].substring(start.length())
        return start == 'requested=' ? line.replaceAll(/<:([A-Za-z]+)>/, '<$1>') : line
    }

    private List<String> errors() {
        return out.toString().readLines().findAll { String line -> line.contains('| Error') }
    }

    private List<String> notes() {
        return out.toString().readLines().findAll { String line -> line.startsWith('| Note') }
    }

    // the wrapper of a generated application, and around it a build that needs nothing of Grails
    private void plainProject() {
        generateProject(ApplicationType.WEB)
        dir.eachFile { File file ->
            if (!(file.name in ['gradlew', 'gradlew.bat', 'gradle'])) {
                file.directory ? file.deleteDir() : file.delete()
            }
        }
        new File(dir, 'gradle').eachFile { File file ->
            if (file.name != 'wrapper') {
                file.directory ? file.deleteDir() : file.delete()
            }
        }
        // the daemon of the specification is given the system properties of each build anew
        new File(dir, 'settings.gradle').text = daemon.settingsOf(true)
        new File(dir, 'gradle.properties').text = daemon.propertiesOf(true)
        new File(dir, 'build.gradle').text = ConsoleCommandsSpec.BUILD
        new File(dir, 'grails-app').mkdirs()
    }
}

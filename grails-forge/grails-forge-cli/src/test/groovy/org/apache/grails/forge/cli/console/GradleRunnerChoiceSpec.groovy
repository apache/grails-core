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

import org.grails.forge.cli.CommandSpec
import org.grails.forge.cli.ForgeCli
import spock.lang.IgnoreIf
import spock.lang.Shared
import spock.lang.Timeout
import spock.lang.Unroll

/**
 * Which way a command runs Gradle by where it is run: a command line that runs one command starts
 * the Gradle wrapper of the project, the interactive console asks the Tooling API of Gradle for the
 * builds it runs itself, and the environment variable {@code GRAILS_GRADLE_RUNNER} says otherwise.
 *
 * <p>The project is a build of Gradle itself, which writes down what it was asked when it is run
 * through the Tooling API, with a wrapper script in it that writes down that it was run and ends with
 * 3, which no build of Gradle ends with: so each feature sees which of the two ran, and the message a
 * command has for each. The daemon is no daemon of whoever runs the specification, see
 * {@link IsolatedGradle}.</p>
 */
@IgnoreIf({ os.windows })
@Timeout(600)
class GradleRunnerChoiceSpec extends CommandSpec {

    static final String WRAPPER_THAT_ENDS_WITH_3 = '#!/bin/sh\nprintf "<%s>" "$@" >> wrapper-was-run.txt\necho >> wrapper-was-run.txt\nexit 3\n'
    static final String OF_THE_WRAPPER = '| Error Gradle did not succeed: it ended with exit code 3'

    @Shared
    IsolatedGradle gradle = new IsolatedGradle()

    PrintStream originalOut
    PrintStream originalErr
    ByteArrayOutputStream out = new ByteArrayOutputStream()

    void setup() {
        originalOut = System.out
        originalErr = System.err
        System.setOut(new PrintStream(out, true))
        System.setErr(new PrintStream(out, true))
        gradle.project(dir, new File(previousUsrDir, '..'))
        new File(dir, 'settings.gradle') << ToolingApiBuildSpec.RECORDER
        new File(dir, 'build.gradle').text = ToolingApiBuildSpec.BUILD
        new File(dir, 'gradlew').text = WRAPPER_THAT_ENDS_WITH_3
    }

    void cleanup() {
        ConsoleCommands.setInteractive(false)
        GradleBuild.closeConnections()
        GradleBuild.setRunner(null)
        System.setOut(originalOut)
        System.setErr(originalErr)
    }

    void cleanupSpec() {
        gradle.close()
    }

    @Unroll
    void "a command line that runs one command starts the Gradle wrapper when GRAILS_GRADLE_RUNNER is #said, and says how Gradle ended by its exit code"() {
        given:
        GradleBuild.setRunner(value)

        when:
        int exit = ForgeCli.execute(beanContext, 'test-app', '--unit')

        then:
        exit == 1
        errors() == [OF_THE_WRAPPER]
        wrapperWasAsked() == ["<:testApp><--phase><unit><-Dgrails.env=test>${pathCheck()}".toString()]
        !toolingApiWasAsked()
        !ToolingApiRunner.connectionKept

        where:
        value     | said
        ''        | 'not set'
        'auto'    | 'auto'
        ' auto '  | 'auto with spaces around it'
        'process' | 'process'
    }

    void "a command line that a caller runs in its own JVM is no console, and starts the Gradle wrapper"() {
        given:
        GradleBuild.setRunner('')

        when: 'as GrailsCli.execute runs it'
        int exit = ForgeCli.launch('grails', true, 'compile')

        then:
        exit == 1
        errors() == [OF_THE_WRAPPER]
        wrapperWasAsked() == ["<:classes>${pathCheck()}".toString()]
        !toolingApiWasAsked()
    }

    @Unroll
    void "in the interactive console the builds are asked of the Tooling API when GRAILS_GRADLE_RUNNER is #said, and the second of the connection the first made"() {
        given:
        GradleBuild.setRunner(value)
        ConsoleCommands.setInteractive(true)
        int before = ToolingApiRunner.connectionsMade

        when:
        int first = ForgeCli.execute(beanContext, 'test-app', '--unit')
        int second = ForgeCli.execute(beanContext, 'compile')

        then:
        first == 0 && second == 0
        errors() == []
        wrapperWasAsked() == []
        new File(dir, 'requested.txt').readLines().collect { String line -> line.split(' ')[0] } == ['requested=<:testApp><--phase><unit>', 'requested=<:classes>']
        new File(dir, 'ran.txt').readLines().collect { String line -> line.split(' ')[0] } == ['testApp', 'classes']

        and:
        ToolingApiRunner.connectionsMade == before + 1
        ToolingApiRunner.connectionKept

        when: 'a build that fails there'
        int failed = ForgeCli.execute(beanContext, 'compile', '--', 'fails')

        then: 'is said to have failed in the words there are without an exit code'
        failed == 1
        errors() == ['| Error Gradle did not succeed: the build failed']
        ToolingApiRunner.connectionsMade == before + 1

        where:
        value         | said
        ''            | 'not set'
        'auto'        | 'auto'
        'tooling-api' | 'tooling-api'
    }

    void "with GRAILS_GRADLE_RUNNER=tooling-api a command line that runs one command asks the Tooling API, and keeps no connection"() {
        given:
        GradleBuild.setRunner('tooling-api')
        int before = ToolingApiRunner.connectionsMade

        when:
        int exit = ForgeCli.execute(beanContext, 'test-app', '--unit')
        int failed = ForgeCli.execute(beanContext, 'compile', '--', 'fails')

        then:
        exit == 0
        failed == 1
        errors() == ['| Error Gradle did not succeed: the build failed']
        wrapperWasAsked() == []
        new File(dir, 'ran.txt').readLines().collect { String line -> line.split(' ')[0] } == ['testApp', 'classes']
        ToolingApiRunner.connectionsMade == before + 2
        !ToolingApiRunner.connectionKept
    }

    void "with GRAILS_GRADLE_RUNNER=process the interactive console starts the Gradle wrapper for its builds"() {
        given:
        GradleBuild.setRunner('process')
        ConsoleCommands.setInteractive(true)
        int before = ToolingApiRunner.connectionsMade

        when:
        int exit = ForgeCli.execute(beanContext, 'test-app', '--unit')

        then:
        exit == 1
        errors() == [OF_THE_WRAPPER]
        wrapperWasAsked() == ["<:testApp><--phase><unit><-Dgrails.env=test>${pathCheck()}".toString()]
        !toolingApiWasAsked()
        ToolingApiRunner.connectionsMade == before
        !ToolingApiRunner.connectionKept
    }

    @Unroll
    void "in the interactive console #command starts the Gradle wrapper as it does elsewhere, since #why"() {
        given:
        GradleBuild.setRunner(value)
        ConsoleCommands.setInteractive(true)

        when:
        int exit = ForgeCli.execute(beanContext, command as String[])

        then:
        exit == 1
        wrapperWasAsked().size() == 1
        wrapperWasAsked()[0].startsWith(asked)
        !toolingApiWasAsked()

        where:
        command                                                    | value         | asked                   | why
        ['gradle', 'classes', '--status']                          | ''            | '<classes><--status>'   | 'what follows gradle is Gradle\'s to read'
        ['gradle', 'classes']                                      | 'tooling-api' | '<classes>'             | 'what follows gradle is Gradle\'s to read'
        ['shell']                                                  | ''            | '<:shell><--quiet>'     | 'what is typed is the shell\'s until it is left'
        ['shell']                                                  | 'tooling-api' | '<:shell><--quiet>'     | 'what is typed is the shell\'s until it is left'
        ['run-app', '--port', freePort(), '--ready-timeout', '30'] | ''            | '<:bootRun><--quiet>'   | 'the application is left running there, and its build has to go on without the console'
        ['run-app', '--port', freePort(), '--ready-timeout', '30'] | 'tooling-api' | '<:bootRun><--quiet>'   | 'the application is left running there, and its build has to go on without the console'
        ['compile', '--', '--no-daemon']                           | ''            | '<:classes><--no-daemon>' | 'the argument is one the program gradle reads itself'
    }

    void "in the interactive console a run-app that stays with the application is a build of the console, and is asked of the Tooling API"() {
        given:
        GradleBuild.setRunner('')
        ConsoleCommands.setInteractive(true)

        when: 'the task ends at once here, as an application that has ended'
        int exit = ForgeCli.execute(beanContext, 'run-app', '--foreground', '--port', freePort())

        then:
        exit == 0
        errors() == []
        wrapperWasAsked() == []
        new File(dir, 'requested.txt').text.startsWith('requested=<:bootRun> system=[grails.env:development, ')
        new File(dir, 'requested.txt').text.contains(' log=QUIET ')
        !new File(dir, '.grails/run/app.json').exists()
    }

    @Unroll
    void "a value of GRAILS_GRADLE_RUNNER that is none of the three runs nothing, #where"() {
        given:
        GradleBuild.setRunner('wrapper')
        ConsoleCommands.setInteractive(interactive)

        when:
        int exit = ForgeCli.execute(beanContext, 'compile')

        then:
        exit == 1
        errors() == ['| Error The environment variable GRAILS_GRADLE_RUNNER says how Gradle is run, and is auto, process or tooling-api: wrapper is none of them. Gradle was not run']
        wrapperWasAsked() == []
        !toolingApiWasAsked()

        where:
        interactive | where
        false       | 'for a command line that runs one command'
        true        | 'in the interactive console'
    }

    // what Gradle is given after the arguments of a command that names the tasks of the project as those of the root, see ProjectPathCheck
    private String pathCheck() {
        return "<--init-script><${new File(dir.canonicalFile, '.grails/run/project-path.gradle').path}>"
    }

    private List<String> wrapperWasAsked() {
        File file = new File(dir, 'wrapper-was-run.txt')
        return file.isFile() ? file.readLines() : []
    }

    private boolean toolingApiWasAsked() {
        return new File(dir, 'requested.txt').exists()
    }

    private static String freePort() {
        return new ServerSocket(0, 1, InetAddress.getLoopbackAddress()).withCloseable { ServerSocket socket -> socket.localPort } as String
    }

    private List<String> errors() {
        return out.toString().readLines().findAll { String line -> line.contains('| Error') }
    }
}

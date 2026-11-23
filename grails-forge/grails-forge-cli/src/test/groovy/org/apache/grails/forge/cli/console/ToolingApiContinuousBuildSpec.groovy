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

import java.util.concurrent.atomic.AtomicInteger
import org.grails.forge.cli.CommandSpec
import org.grails.forge.cli.ForgeCli
import spock.lang.IgnoreIf
import spock.lang.Shared
import spock.lang.Timeout
import spock.lang.Unroll
import spock.util.concurrent.PollingConditions

/**
 * {@code test-app --continuous} through the Tooling API of Gradle: the first build runs, the build
 * then waits for a change, runs again when a file it reads has changed, and goes on until the
 * command is cancelled, after which the console runs the next command as before.
 *
 * <p>A build that is given an input stream through the Tooling API is taken by Gradle for one that a
 * user types into, and a continuous build of that kind ends when its input ends. So where nothing
 * may be read, the build is given no input at all, and not an input that has ended.</p>
 *
 * <p>The build is a build of Gradle itself, with a daemon of its own that the features share, see
 * {@link IsolatedGradle}.</p>
 */
@IgnoreIf({ os.windows })
@Timeout(600)
class ToolingApiContinuousBuildSpec extends CommandSpec {

    static final String BUILD = '''\
        def watched = layout.projectDirectory.file('src/watched.txt')
        def ran = layout.projectDirectory.file('ran.txt')
        tasks.register('testApp') {
            inputs.file(watched)
            outputs.upToDateWhen { false }
            doLast {
                String read = watched.asFile.text.trim()
                ran.asFile << read + '\\n'
                println "tested ${read}"
            }
        }
        tasks.register('classes') {
            doLast { println 'compiled' }
        }
        '''.stripIndent()

    @Shared
    IsolatedGradle gradle = new IsolatedGradle()

    PrintStream originalOut
    PrintStream originalErr
    InputStream originalIn
    ByteArrayOutputStream out = new ByteArrayOutputStream()
    File watched
    File ran

    void setup() {
        originalOut = System.out
        originalErr = System.err
        originalIn = System.in
        System.setOut(new PrintStream(out, true))
        System.setErr(new PrintStream(out, true))
        gradle.project(dir, new File(previousUsrDir, '..'))
        new File(dir, 'build.gradle').text = BUILD
        new File(dir, 'gradlew').text = ToolingApiBuildSpec.WRAPPER_THAT_SAYS_SO
        watched = new File(dir, 'src/watched.txt')
        watched.parentFile.mkdirs()
        watched.text = 'first\n'
        ran = new File(dir, 'ran.txt')
    }

    void cleanup() {
        ConsoleCommands.setInteractive(false)
        GradleBuild.closeConnections()
        GradleBuild.setRunner(null)
        GradleBuild.setNonInteractive(false)
        GradleBuild.outputOfProcess(null, null)
        System.setOut(originalOut)
        System.setErr(originalErr)
        System.setIn(originalIn)
    }

    void cleanupSpec() {
        gradle.close()
    }

    @Unroll
    void "#where a continuous build runs again on a change and goes on until the command is cancelled, and the next command runs"() {
        given:
        GradleBuild.setRunner(runner)
        ConsoleCommands.setInteractive(console)
        if (program) {
            // a program whose JVM ends with the command: the build would be given the input of the command line
            GradleBuild.outputOfProcess(originalOut, originalErr)
            System.setIn(new ByteArrayInputStream(new byte[0]))
        }
        AtomicInteger exit = new AtomicInteger(-1)
        String[] line = (options + ['test-app', '--continuous']) as String[]
        Thread command = new Thread({ exit.set(ForgeCli.execute(beanContext, line)) })

        when: 'the first build has run'
        command.start()
        new PollingConditions(timeout: 300, delay: 0.2).eventually {
            assert ran.isFile() && ran.readLines() == ['first']
            assert out.toString().contains('Waiting for changes to input files')
        }

        then: 'the build waits for a change, and is not taken for one that has ended'
        command.alive
        GradleBuild.isRunning()

        when: 'a file the build reads changes'
        watched.text = 'second\n'

        then: 'the build runs again, and waits again'
        new PollingConditions(timeout: 120, delay: 0.2).eventually {
            assert ran.readLines() == ['first', 'second']
        }
        command.alive

        when: 'the command is cancelled'
        command.interrupt()
        command.join(60_000)

        then:
        !command.alive
        exit.get() == 1
        errors() == ['| Error Gradle was stopped before it finished, because the command was cancelled']
        !GradleBuild.isRunning()

        when: 'the next command'
        out.reset()
        int next = ForgeCli.execute(beanContext, 'compile')

        then:
        next == 0
        errors() == []
        out.toString().contains('compiled')

        where:
        where                                                                   | runner                    | console | program | options
        'in the interactive console'                                           | GradleBuild.AUTO          | true    | false   | []
        'in the interactive console with --non-interactive'                    | GradleBuild.AUTO          | true    | false   | ['--non-interactive']
        'where a caller runs the command line in its own JVM'                  | GradleBuild.TOOLING_API   | false   | false   | []
        'in a program that runs one command with --non-interactive'            | GradleBuild.TOOLING_API   | false   | true    | ['--non-interactive']
    }

    private List<String> errors() {
        return out.toString().readLines().findAll { String line -> line.contains('| Error') }
    }
}

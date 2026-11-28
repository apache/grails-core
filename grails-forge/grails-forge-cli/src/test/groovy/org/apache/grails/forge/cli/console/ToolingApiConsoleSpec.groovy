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

import java.nio.file.Files
import java.util.concurrent.TimeUnit
import org.grails.forge.cli.CommandSpec
import org.grails.forge.cli.ForgeCli
import org.grails.forge.cli.ForkedCli
import org.grails.forge.cli.SpecWaits
import spock.lang.IgnoreIf
import spock.lang.Shared
import spock.lang.Timeout

/**
 * The connection to the Gradle of the project that the interactive console keeps between its builds:
 * one for the project the console is in, which a build that fails leaves as it was, which is replaced
 * for another project and for another Gradle, and which ends with the console. A command line that
 * runs one command keeps none.
 *
 * <p>The build is the one of {@link ToolingApiBuildSpec}, with a daemon of its own that the features
 * share, see {@link IsolatedGradle}.</p>
 */
@IgnoreIf({ os.windows })
@Timeout(600)
class ToolingApiConsoleSpec extends CommandSpec {

    @Shared
    IsolatedGradle gradle = new IsolatedGradle()

    PrintStream originalOut
    PrintStream originalErr
    ByteArrayOutputStream out = new ByteArrayOutputStream()
    Process program

    void setup() {
        originalOut = System.out
        originalErr = System.err
        System.setOut(new PrintStream(out, true))
        System.setErr(new PrintStream(out, true))
        project(dir)
        // what the console does by itself
        GradleBuild.setRunner(GradleBuild.AUTO)
    }

    void cleanup() {
        ConsoleCommands.setInteractive(false)
        GradleBuild.closeConnections()
        GradleBuild.setRunner(null)
        System.setOut(originalOut)
        System.setErr(originalErr)
        if (program != null) {
            program.destroyForcibly()
            program.waitFor(SpecWaits.BOUND.toMillis(), TimeUnit.MILLISECONDS)
        }
    }

    void cleanupSpec() {
        gradle.close()
    }

    private void project(File directory) {
        gradle.project(directory, new File(previousUsrDir, '..'))
        new File(directory, 'build.gradle').text = ToolingApiBuildSpec.BUILD
        new File(directory, 'gradlew').text = ToolingApiBuildSpec.WRAPPER_THAT_SAYS_SO
    }

    void "the builds of the interactive console are asked of one connection, which a build that fails leaves as it was"() {
        given:
        int before = ToolingApiRunner.connectionsMade
        ConsoleCommands.setInteractive(true)

        when:
        int first = ForgeCli.execute(beanContext, 'compile')
        int second = ForgeCli.execute(beanContext, 'compile')
        int failed = ForgeCli.execute(beanContext, 'compile', '--', 'fails')
        int third = ForgeCli.execute(beanContext, 'test-app', '--unit')

        then:
        first == 0 && second == 0 && third == 0
        failed == 1
        errors() == ['| Error Gradle did not succeed: the build failed']
        new File(dir, 'ran.txt').readLines().collect { String line -> line.split(' ')[0] } == ['classes', 'classes', 'classes', 'testApp']
        !new File(dir, 'wrapper-was-run.txt').exists()

        and: 'one connection was made for the four of them, and is kept for the next'
        ToolingApiRunner.connectionsMade == before + 1
        ToolingApiRunner.connectionKept

        when: 'the console is left'
        GradleBuild.closeConnections()

        then:
        !ToolingApiRunner.connectionKept

        when: 'and entered again'
        ConsoleCommands.setInteractive(true)
        int again = ForgeCli.execute(beanContext, 'compile')

        then:
        again == 0
        ToolingApiRunner.connectionsMade == before + 2
    }

    void "the connection of the console is replaced when a command is run for another project, and when the wrapper names another Gradle"() {
        given:
        File other = Files.createTempDirectory('grailsforgeother').toFile()
        project(other)
        int before = ToolingApiRunner.connectionsMade
        ConsoleCommands.setInteractive(true)

        when:
        ForgeCli.execute(beanContext, 'compile')
        System.setProperty('user.dir', other.absolutePath)
        int there = ForgeCli.execute(beanContext, 'compile')

        then: 'the build was the one of the other project'
        there == 0
        new File(other, 'ran.txt').readLines().size() == 1
        new File(dir, 'ran.txt').readLines().size() == 1
        ToolingApiRunner.connectionsMade == before + 2

        when: 'and again there'
        ForgeCli.execute(beanContext, 'compile')

        then:
        ToolingApiRunner.connectionsMade == before + 2

        when: 'what names the Gradle of the project is changed, as update-wrapper does'
        new File(other, 'gradle/wrapper/gradle-wrapper.properties') << '# changed\n'
        int after = ForgeCli.execute(beanContext, 'compile')

        then:
        after == 0
        errors() == []
        ToolingApiRunner.connectionsMade == before + 3
        new File(other, 'ran.txt').readLines().size() == 3

        cleanup:
        other.deleteDir()
    }

    void "a command line that runs one command through the Tooling API makes a connection for its build and keeps none"() {
        given:
        GradleBuild.setRunner(GradleBuild.TOOLING_API)
        int before = ToolingApiRunner.connectionsMade

        when:
        int first = ForgeCli.execute(beanContext, 'compile')
        int second = ForgeCli.execute(beanContext, 'compile')

        then:
        first == 0 && second == 0
        ToolingApiRunner.connectionsMade == before + 2
        !ToolingApiRunner.connectionKept
    }

    void "the console as a program runs its builds through the Tooling API, reads every line that is typed as a command, and ends when it is left"() {
        given:
        File typed = new File(gradle.registry, 'typed.txt')
        typed.text = 'compile\ncompile -- fails\ntest-app --unit\nexit\ncompile\n'
        File written = new File(gradle.registry, 'console.log')
        ProcessBuilder builder = ForkedCli.builder(dir, []).redirectErrorStream(true).redirectOutput(written).redirectInput(typed)
        builder.environment().remove(GradleBuild.RUNNER)
        builder.environment().remove('NO_COLOR')

        when:
        program = builder.start()
        boolean ended = program.waitFor(SpecWaits.BOUND.toMillis(), TimeUnit.MILLISECONDS)
        String said = written.text

        then: 'the console has ended by itself, with nothing of the Tooling API holding its JVM'
        ended
        program.exitValue() == 0

        and: 'every line was a command of the console: a build did not take what was typed for the console'
        new File(dir, 'ran.txt').readLines().collect { String line -> line.split(' ')[0] } == ['classes', 'classes', 'testApp']
        said.count('BUILD SUCCESSFUL') == 2
        said.count('BUILD FAILED') == 1
        said.contains('Gradle did not succeed: the build failed')
        !new File(dir, 'wrapper-was-run.txt').exists()
    }

    private List<String> errors() {
        return out.toString().readLines().findAll { String line -> line.contains('| Error') }
    }
}

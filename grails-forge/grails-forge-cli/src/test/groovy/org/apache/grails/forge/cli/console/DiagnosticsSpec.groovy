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

import java.util.concurrent.TimeUnit
import java.util.regex.Matcher
import org.apache.grails.forge.cli.Diagnostics
import org.grails.forge.cli.CommandSpec
import org.grails.forge.cli.ForgeCli
import org.grails.forge.cli.ForkedCli
import org.grails.forge.cli.SpecWaits
import spock.lang.IgnoreIf
import spock.lang.Shared
import spock.lang.Timeout
import spock.lang.Unroll

/**
 * What the command line says of how it runs a command where {@code GRAILS_CLI_DIAGNOSTICS=true}: which
 * way Gradle is run and why, a fallback and its reason, whether a connection to Gradle was made or
 * reused, whether the classes were loaded ahead, and how many templates the project generator
 * compiled. Each is a line on the error output that starts with {@code | Diagnostic}; without the
 * variable there is none.
 *
 * <p>The features set the switch for the JVM of the specification, which cannot change its
 * environment; one runs the program itself with the variable. The builds through the Tooling API are
 * of a build of Gradle with a daemon of its own, see {@link IsolatedGradle}; the others have a wrapper
 * script that writes down that it was run.</p>
 */
@IgnoreIf({ os.windows })
@Timeout(600)
class DiagnosticsSpec extends CommandSpec {

    static final String NOT_SET = 'requested auto, GRAILS_GRADLE_RUNNER not set'

    @Shared
    IsolatedGradle gradle = new IsolatedGradle()

    PrintStream originalOut
    PrintStream originalErr
    ByteArrayOutputStream out = new ByteArrayOutputStream()
    ByteArrayOutputStream err = new ByteArrayOutputStream()
    Process program

    void setup() {
        originalOut = System.out
        originalErr = System.err
        System.setOut(new PrintStream(out, true))
        System.setErr(new PrintStream(err, true))
        gradle.project(dir, new File(previousUsrDir, '..'))
        new File(dir, 'build.gradle').text = ToolingApiBuildSpec.BUILD
        new File(dir, 'gradlew').text = ToolingApiBuildSpec.WRAPPER_THAT_SAYS_SO
        Diagnostics.setOn(true)
    }

    void cleanup() {
        Diagnostics.setOn(null)
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

    @Unroll
    void "#command where GRAILS_GRADLE_RUNNER is #said says which way Gradle is run and why"() {
        given:
        GradleBuild.setRunner(value)

        when:
        ForgeCli.execute(beanContext, command as String[])

        then: 'on the error output, and nothing of it on the output; the project generator of the specification runs'
        diagnostics().size() == 2
        diagnostics()[0] == "| Diagnostic gradle runner: ${requested}; effective ${effective}".toString()
        diagnostics()[1].startsWith('| Diagnostic project generator: running; ')
        !out.toString().contains('| Diagnostic')

        where:
        command                         | value         | requested                    | effective
        ['compile']                     | ''            | NOT_SET                      | 'process: a command line that runs one command'
        ['compile']                     | 'auto'        | 'requested auto'             | 'process: a command line that runs one command'
        ['test-app']                    | 'process'     | 'requested process'          | 'process: GRAILS_GRADLE_RUNNER is process'
        ['gradle', 'build']             | 'tooling-api' | 'requested tooling-api'      | 'process: what follows gradle is given to Gradle as on a command line of its own'
        ['shell']                       | 'tooling-api' | 'requested tooling-api'      | 'process: a session at the terminal is given the terminal itself'
        ['compile']                     | 'gradle'      | 'requested gradle'           | 'none: gradle is none of the values of GRAILS_GRADLE_RUNNER'
        said = value ?: 'not set'
    }

    void "a build through the Tooling API says whether its connection was made for it, made and kept by the console, or reused"() {
        given:
        GradleBuild.setRunner(GradleBuild.TOOLING_API)

        when: 'a command line that runs one command'
        int exit = ForgeCli.execute(beanContext, 'compile')

        then:
        exit == 0
        diagnostics()[0] == '| Diagnostic gradle runner: requested tooling-api; effective tooling-api: GRAILS_GRADLE_RUNNER is tooling-api'
        diagnostics()[1] ==~ /\| Diagnostic gradle connection: made for this build, and closed after it \([0-9]+ made in this JVM\)/

        when: 'the console'
        GradleBuild.setRunner('')
        ConsoleCommands.setInteractive(true)
        err.reset()
        ForgeCli.execute(beanContext, 'compile')
        List<String> first = diagnostics()
        err.reset()
        ForgeCli.execute(beanContext, 'compile')
        List<String> second = diagnostics()

        then:
        first[0] == "| Diagnostic gradle runner: ${NOT_SET}; effective tooling-api: a build of the interactive console".toString()
        first[1] ==~ /\| Diagnostic gradle connection: made, and kept for the next build of the console \([0-9]+ made in this JVM\)/
        second[1] ==~ /\| Diagnostic gradle connection: reused: the one the console keeps for this project and its Gradle \([0-9]+ made in this JVM\)/

        when: 'what names the Gradle of the project has changed'
        new File(dir, 'gradle/wrapper/gradle-wrapper.properties') << '# changed\n'
        err.reset()
        ForgeCli.execute(beanContext, 'compile')

        then:
        diagnostics()[1] ==~ /\| Diagnostic gradle connection: made in the place of the one the console kept, which was for another project or another Gradle \([0-9]+ made in this JVM\)/
    }

    @Unroll
    void "a build that falls back from the Tooling API to the wrapper says why, with the name of the argument and not its value: #given"() {
        given:
        GradleBuild.setRunner(GradleBuild.TOOLING_API)

        when:
        ForgeCli.execute(beanContext, (['compile', '--'] + given) as String[])

        then:
        diagnostics()[0] == '| Diagnostic gradle runner: requested tooling-api; effective tooling-api: GRAILS_GRADLE_RUNNER is tooling-api'
        diagnostics()[1] == "| Diagnostic gradle runner: tooling-api falls back to process: the argument ${named} is one the program gradle reads itself, which the Tooling API does not take".toString()
        !err.toString().contains('secret')
        new File(dir, 'wrapper-was-run.txt').isFile()

        where:
        given                                     | named
        ['--no-daemon']                           | '--no-daemon'
        ['-Dorg.gradle.jvmargs=-Dsecret=1']       | '-Dorg.gradle.jvmargs'
        ['--gradle-user-home=/secret/home']       | '--gradle-user-home'
    }

    void "run-app --detach says that the application is a process of its own"() {
        given:
        GradleBuild.setRunner('')
        int port = new ServerSocket(0, 1, InetAddress.loopbackAddress).withCloseable { ServerSocket socket -> socket.localPort }

        when: 'the wrapper ends at once, so nothing answers'
        ForgeCli.execute(beanContext, 'run-app', '--detach', '--port', port as String, '--ready-timeout', SpecWaits.boundSeconds as String)

        then:
        diagnostics()[0] == "| Diagnostic gradle runner: ${NOT_SET}; effective process: an application that is left running is a process of its own, which outlives the command line".toString()
    }

    void "a command that ran the project generator says how many templates it took as the build compiled them and how many it compiled"() {
        given:
        File outside = File.createTempDir()
        System.setProperty('user.dir', outside.path)

        when:
        int exit = ForgeCli.execute(beanContext, 'create-app', 'example.shop', '--features', 'asciidoctor,github-workflow-java-ci')
        String line = diagnostics().last()
        Matcher counted = line =~ /^\| Diagnostic project generator: running; templates of the packs of Forge since it started: ([0-9]+) precompiled by the build of Forge, ([0-9]+) compiled at run time$/

        then:
        exit == 0
        counted.matches()
        (counted.group(1) as int) > 0
        (counted.group(2) as int) == 0

        cleanup:
        outside.deleteDir()
    }

    void "with the switch off nothing is said"() {
        given:
        Diagnostics.setOn(false)
        GradleBuild.setRunner(GradleBuild.TOOLING_API)

        when:
        ForgeCli.execute(beanContext, 'compile')
        ForgeCli.execute(beanContext, 'compile', '--', '--no-daemon')
        ForgeCli.launch('grails', false, '--version')

        then:
        diagnostics() == []
    }

    @IgnoreIf({ !ForkedCli.available() })
    void "the program says what it did with GRAILS_CLI_DIAGNOSTICS=true, and nothing without it"() {
        given:
        File written = new File(gradle.registry, 'diagnostics.log')

        when:
        ProcessBuilder builder = ForkedCli.builder(dir, [], '--version').redirectErrorStream(true).redirectOutput(written)
        builder.environment().put(Diagnostics.VARIABLE, 'true')
        program = builder.start()
        boolean ended = program.waitFor(SpecWaits.BOUND.toMillis(), TimeUnit.MILLISECONDS)
        List<String> said = written.readLines().findAll { String line -> line.startsWith('| Diagnostic') }

        then: 'the classes were loaded ahead, as the program does where the JVM has more than one processor'
        ended
        program.exitValue() == 0
        said.size() == 2
        said[0] ==~ /\| Diagnostic preload: started: the classes every command loads, (still loading|done)/ ||
                (Runtime.runtime.availableProcessors() < 2 && said[0] == '| Diagnostic preload: off: the JVM has one processor')
        said[1] == '| Diagnostic project generator: not started'

        when: 'without the variable'
        builder.environment().remove(Diagnostics.VARIABLE)
        program = builder.start()
        ended = program.waitFor(SpecWaits.BOUND.toMillis(), TimeUnit.MILLISECONDS)

        then:
        ended
        program.exitValue() == 0
        !written.text.contains('| Diagnostic')
    }

    void "a command line run in the JVM of a program says that the classes were not loaded ahead for it, or what the program did"() {
        when:
        ForgeCli.launch('grails', false, '--version')

        then:
        diagnostics()[0].startsWith('| Diagnostic preload: ')
        diagnostics().last() == '| Diagnostic project generator: not started'
    }

    private List<String> diagnostics() {
        return err.toString().readLines().findAll { String line -> line.startsWith('| Diagnostic') }
    }
}

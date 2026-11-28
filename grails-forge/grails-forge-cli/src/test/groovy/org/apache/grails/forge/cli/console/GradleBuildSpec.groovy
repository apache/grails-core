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
import java.util.function.Consumer
import spock.lang.Specification
import spock.lang.Unroll

/**
 * Which way of running Gradle a command is given for what it runs Gradle for, and what a command sees
 * of a build whichever way it is run: {@link GradleBuild} with a build in its place.
 */
class GradleBuildSpec extends Specification {

    List<String> failures = []
    Consumer<String> failure = { String message -> failures.add(message) } as Consumer<String>

    void cleanup() {
        GradleBuild.setRunner(null)
    }

    @Unroll
    void "for #purpose #where with GRAILS_GRADLE_RUNNER #said, Gradle is run by #runner.simpleName"() {
        given:
        GradleBuild.setRunner(value)
        ConsoleCommands.setInteractive(console)

        expect:
        GradleBuild.of(purpose, failure, false).getClass() == runner

        cleanup:
        ConsoleCommands.setInteractive(false)

        where:
        purpose                              | console | value         | runner
        GradleBuild.Purpose.BUILD            | false   | ''            | GradleRunner
        GradleBuild.Purpose.BUILD            | false   | 'auto'        | GradleRunner
        GradleBuild.Purpose.BUILD            | false   | 'process'     | GradleRunner
        GradleBuild.Purpose.BUILD            | false   | 'tooling-api' | ToolingApiRunner
        GradleBuild.Purpose.BUILD            | true    | ''            | ToolingApiRunner
        GradleBuild.Purpose.BUILD            | true    | 'auto'        | ToolingApiRunner
        GradleBuild.Purpose.BUILD            | true    | 'process'     | GradleRunner
        GradleBuild.Purpose.BUILD            | true    | 'tooling-api' | ToolingApiRunner
        GradleBuild.Purpose.PASSED_ON        | false   | ''            | GradleRunner
        GradleBuild.Purpose.PASSED_ON        | false   | 'tooling-api' | GradleRunner
        GradleBuild.Purpose.PASSED_ON        | true    | ''            | GradleRunner
        GradleBuild.Purpose.PASSED_ON        | true    | 'tooling-api' | GradleRunner
        GradleBuild.Purpose.PASSED_ON        | true    | 'process'     | GradleRunner
        GradleBuild.Purpose.TERMINAL_SESSION | false   | ''            | GradleRunner
        GradleBuild.Purpose.TERMINAL_SESSION | false   | 'tooling-api' | GradleRunner
        GradleBuild.Purpose.TERMINAL_SESSION | true    | ''            | GradleRunner
        GradleBuild.Purpose.TERMINAL_SESSION | true    | 'tooling-api' | GradleRunner
        GradleBuild.Purpose.TERMINAL_SESSION | true    | 'process'     | GradleRunner
        said = value ? "set to ${value}" : 'not set'
        where = console ? 'in the interactive console' : 'on a command line that runs one command'
    }

    void "a build that is left running is started by the wrapper as a process, whatever the environment says"() {
        given:
        GradleBuild.setRunner(value)

        expect:
        GradleBuild.leftRunning(failure, false).getClass() == GradleRunner

        where:
        value << ['', 'auto', 'tooling-api', 'process', 'something else']
    }

    @Unroll
    void "a value of GRAILS_GRADLE_RUNNER that is none of the three, #value, runs nothing for #purpose and says which values there are"() {
        given:
        GradleBuild.setRunner(value)
        File dir = Files.createTempDirectory('grailsbuild').toFile()
        new File(dir, 'grails-app').mkdirs()

        when:
        int exit = GradleBuild.of(purpose, failure, false).run(ConsoleProject.find(dir.toPath()), ['help'])

        then:
        exit == 1
        failures == ["The environment variable GRAILS_GRADLE_RUNNER says how Gradle is run, and is auto, process or tooling-api: ${shown} is none of them. Gradle was not run".toString()]

        cleanup:
        dir.deleteDir()

        where:
        value            | purpose                       | shown
        'Process'        | GradleBuild.Purpose.BUILD     | 'Process'
        'toolingapi'     | GradleBuild.Purpose.PASSED_ON | 'toolingapi'
        'Auto'           | GradleBuild.Purpose.BUILD     | 'Auto'
        'a\u001b[31mb\n' | GradleBuild.Purpose.BUILD     | 'a?[31mb'
    }

    @Unroll
    void "#argument is #what the program gradle reads itself"() {
        expect:
        ToolingApiRunner.isOfTheProgram(argument) == ofTheProgram

        where:
        argument                           | ofTheProgram
        '--daemon'                         | true
        '--no-daemon'                      | true
        '--status'                         | true
        '--stop'                           | true
        '--foreground'                     | true
        '-g'                               | true
        '--gradle-user-home'               | true
        '--gradle-user-home=/elsewhere'    | true
        '-p'                               | true
        '--project-dir=/elsewhere'         | true
        '-Dorg.gradle.jvmargs=-Xmx1g'      | true
        '-Dorg.gradle.java.home=/a/jdk'    | true
        '-Dgradle.user.home=/elsewhere'    | true
        ':bootRun'                         | false
        '--debug-jvm'                      | false
        '--offline'                        | false
        '--info'                           | false
        '--console=plain'                  | false
        '-Dgrails.env=test'                | false
        '-Pargs=--stop'                    | false
        '--args-file=build/--no-daemon'    | false
        what = ofTheProgram ? 'an argument' : 'no argument'
    }

    void "a command is given the build that was started, with the process a record of it names, and can ask for its end"() {
        given:
        boolean[] cancelled = [false] as boolean[]
        GradleBuild build = new GradleBuild() {

            @Override
            int run(ConsoleProject project, List<String> arguments, Consumer<GradleBuild.Running> started) {
                started.accept(new GradleBuild.Running(ProcessHandle.current(), true, { cancelled[0] = true } as Runnable))
                return cancelled[0] ? 1 : 0
            }
        }
        GradleBuild.Running seen = null

        when:
        int exit = build.run(null, ['help'], { GradleBuild.Running running ->
            seen = running
            running.cancel()
        } as Consumer<GradleBuild.Running>)

        then:
        exit == 1
        seen.process.pid() == ProcessHandle.current().pid()
        seen.commandLine
        cancelled[0]

        and: 'a command that asks for nothing of the build is run all the same'
        new GradleBuild() {

            @Override
            int run(ConsoleProject project, List<String> arguments, Consumer<GradleBuild.Running> started) {
                return started == null ? 0 : 1
            }
        }.run(null, ['help']) == 0
    }
}

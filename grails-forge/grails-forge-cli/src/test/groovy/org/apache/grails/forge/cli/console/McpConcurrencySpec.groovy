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

import java.nio.charset.StandardCharsets
import java.util.concurrent.Callable
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import org.grails.forge.application.ApplicationType
import org.grails.forge.cli.CommandFixture
import org.grails.forge.cli.CommandSpec
import org.grails.forge.cli.ForgeCli
import org.grails.forge.cli.ForgeContext
import spock.lang.IgnoreIf
import spock.lang.Shared
import spock.lang.Timeout

/**
 * Calls that {@code grails mcp} runs at once in its one process, one of each lane: a build, a command
 * that runs the project generator and two that do neither, again and again. What each writes, and what
 * Gradle writes for it from the threads that copy it or from those of the Tooling API, reaches that call
 * and no other, and nothing reaches the standard output of the process or, where no call is bound, its
 * standard error.
 *
 * <p>Fifty rounds run the build with the Gradle wrapper of the project as a process, a script whose
 * output takes about as long as the generator; fifty more run it through the Tooling API on the
 * connection that is kept for them, against a build of Gradle itself, whose daemon is the
 * specification's own ({@link IsolatedGradle}). Together they take about forty seconds.</p>
 */
@IgnoreIf({ os.windows })
@Timeout(900)
class McpConcurrencySpec extends CommandSpec implements CommandFixture {

    static final int ROUNDS = 50
    static final int TOOLING_API_ROUNDS = 50

    // the Gradle wrapper of the project: ten lines on each stream with the marker it is given, while the others run
    static final String WRAPPER = '''\
        #!/bin/sh
        marker=none
        for argument in "$@"; do
          case "$argument" in -Dmarker=*) marker="${argument#-Dmarker=}" ;; esac
        done
        line=0
        while [ $line -lt 10 ]; do
          echo "build out $line $marker"
          echo "build err $line $marker" >&2
          sleep 0.02
          line=$((line + 1))
        done
        '''.stripIndent()

    // the build, through the Tooling API: the same lines, written by the task
    static final String BUILD = '''\
        tasks.register('classes') {
            doLast {
                String marker = System.getProperty('marker')
                10.times { int line ->
                    println "build out ${line} ${marker}"
                    System.err.println("build err ${line} ${marker}")
                }
            }
        }
        '''.stripIndent()

    static final List<String> KINDS = ['buildmark', 'appmark', 'Ctlmark', 'Svcmark']

    @Shared
    IsolatedGradle gradle = new IsolatedGradle()

    PrintStream originalOut
    PrintStream originalErr
    InputStream originalIn
    ByteArrayOutputStream protocol = new ByteArrayOutputStream()
    ByteArrayOutputStream processErr = new ByteArrayOutputStream()
    CommandStreams.Guard guard
    ExecutorService calls = Executors.newFixedThreadPool(4)

    void setup() {
        dir = dir.canonicalFile
        System.setProperty('user.dir', dir.absolutePath)
        originalOut = System.out
        originalErr = System.err
        originalIn = System.in
        generateProject(ApplicationType.WEB)
    }

    void cleanup() {
        calls.shutdownNow()
        calls.awaitTermination(30, TimeUnit.SECONDS)
        guard?.close()
        PlainOutput.leave()
        GradleBuild.closeConnections()
        ConsoleCommands.setSession(ConsoleCommands.Session.NONE)
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

    // what grails mcp does before it serves, as GrailsCli.main and the command do it
    private void serve() {
        GradleBuild.outputOfProcess(System.out, System.err)
        guard = CommandStreams.guard(new ByteArrayInputStream(new byte[0]), protocol, new PrintStream(processErr, true))
        ConsoleCommands.setSession(ConsoleCommands.Session.MCP)
        PlainOutput.enter()
    }

    void "a build with the Gradle wrapper, a generator command and two direct calls at once, fifty times: no output crosses between them"() {
        given:
        File wrapper = new File(dir, 'gradlew')
        wrapper.text = WRAPPER
        wrapper.setExecutable(true, false)
        GradleBuild.setRunner(GradleBuild.PROCESS)
        serve()

        when:
        List<String> crossed = (1..ROUNDS).collectMany { int round -> runRound(round) }

        then:
        crossed == []

        and: 'nothing reached the standard output, or the standard error of the process'
        protocol.size() == 0
        KINDS.every { String kind -> !processErr.toString(StandardCharsets.UTF_8).contains(kind) }
    }

    void "a build through the Tooling API on the connection that is kept, a generator command and two direct calls at once, fifty times: no output crosses between them"() {
        given:
        ['gradlew', 'settings.gradle', 'gradle.properties', 'build.gradle'].each { String name -> new File(dir, name).delete() }
        new File(dir, 'gradle/wrapper').deleteDir()
        gradle.project(dir, new File(previousUsrDir, '..'))
        new File(dir, 'build.gradle').text = BUILD
        new File(dir, 'gradlew').text = ToolingApiBuildSpec.WRAPPER_THAT_SAYS_SO
        GradleBuild.setRunner(GradleBuild.AUTO)
        serve()
        int before = ToolingApiRunner.connectionsMade

        when:
        List<String> crossed = (1..TOOLING_API_ROUNDS).collectMany { int round -> runRound(round) }

        then:
        crossed == []
        !new File(dir, 'wrapper-was-run.txt').exists()

        and: 'the builds ran on one connection, which is kept'
        ToolingApiRunner.connectionsMade == before + 1
        ToolingApiRunner.connectionKept

        and: 'nothing reached the standard output, or the standard error of the process'
        protocol.size() == 0
        KINDS.every { String kind -> !processErr.toString(StandardCharsets.UTF_8).contains(kind) }
    }

    // one round: the four calls start together, and each is held to have written what is its own and nothing else
    private List<String> runRound(int round) {
        ForgeContext context = ForgeContext.of(beanContext)
        Map<String, List<String>> lines = [
                buildmark: ['compile', "-Dmarker=buildmark${round}x".toString()],
                appmark  : ['create-app', "appmark${round}x".toString()],
                Ctlmark  : ['create-controller', "Ctlmark${round}x".toString()],
                Svcmark  : ['create-service', "Svcmark${round}x".toString()]
        ]
        CyclicBarrier together = new CyclicBarrier(lines.size())
        Map<String, CommandStreams> streams = lines.collectEntries { String kind, List<String> line -> [(kind): CommandStreams.capture()] }
        Map<String, Future<Integer>> exits = lines.collectEntries { String kind, List<String> line ->
            [(kind): calls.submit({
                together.await(60, TimeUnit.SECONDS)
                return ForgeCli.execute(context, streams[kind], (['--plain-output', '--non-interactive'] + line) as String[])
            } as Callable<Integer>)]
        }
        List<String> crossed = []
        lines.keySet().each { String kind ->
            int exit = exits[kind].get(300, TimeUnit.SECONDS)
            String output = streams[kind].merged()
            Set<String> marks = (output =~ /(?:buildmark|appmark|Ctlmark|Svcmark)\d+x/).collect { it as String } as Set<String>
            String own = "${kind}${round}x".toString()
            if (exit != 0 || marks != [own] as Set) {
                crossed << "round ${round}, ${kind}: exit ${exit}, marks ${marks}: ${output}".toString()
            }
            if (kind == 'buildmark') {
                List<String> built = output.readLines().findAll { String line -> line.startsWith('build ') }
                if (built.size() != 20) {
                    crossed << "round ${round}, the build wrote ${built.size()} of its 20 lines: ${output}".toString()
                }
            }
        }
        return crossed
    }
}

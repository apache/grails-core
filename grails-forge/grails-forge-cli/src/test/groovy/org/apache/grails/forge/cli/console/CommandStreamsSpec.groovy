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
import java.nio.file.Files
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.function.Consumer
import javax.tools.ToolProvider
import groovy.json.JsonOutput
import org.apache.grails.forge.cli.Diagnostics
import org.grails.forge.cli.CommandSpec
import org.grails.forge.cli.ForgeCli
import org.grails.forge.cli.ForgeContext
import spock.lang.IgnoreIf
import spock.lang.Shared
import spock.lang.Timeout

/**
 * Where a command line that a program runs in its own JVM writes, as {@code grails mcp} runs each call:
 * to the streams bound to its thread, from every thread that writes for it, the threads that copy what
 * Gradle, a standalone tool or a program writes and those of the Tooling API among them; and where
 * nothing is bound, to the standard error of the process, whose standard output the guard keeps for the
 * protocol alone.
 */
@Timeout(600)
class CommandStreamsSpec extends CommandSpec {

    static final String MARKED_BUILD = '''\
        tasks.register('classes') {
            doLast {
                String marker = System.getProperty('marker')
                5.times { int line ->
                    println "build out ${line} ${marker}"
                    System.err.println("build err ${line} ${marker}")
                }
            }
        }
        '''.stripIndent()

    // what the Gradle wrapper of a project is here: it writes five lines on each stream, with the marker it is given
    static final String MARKED_WRAPPER = '''\
        #!/bin/sh
        marker=none
        for argument in "$@"; do
          case "$argument" in -Dmarker=*) marker="${argument#-Dmarker=}" ;; esac
        done
        line=0
        while [ $line -lt 5 ]; do
          echo "build out $line $marker"
          echo "build err $line $marker" >&2
          line=$((line + 1))
        done
        '''.stripIndent()

    @Shared
    IsolatedGradle gradle = new IsolatedGradle()

    PrintStream originalOut
    PrintStream originalErr
    InputStream originalIn
    ByteArrayOutputStream processOut = new ByteArrayOutputStream()
    ByteArrayOutputStream processErr = new ByteArrayOutputStream()

    void setup() {
        // the project's paths as the command line reads them, through no link
        dir = dir.canonicalFile
        System.setProperty('user.dir', dir.absolutePath)
        originalOut = System.out
        originalErr = System.err
        originalIn = System.in
        System.setOut(new PrintStream(processOut, true))
        System.setErr(new PrintStream(processErr, true))
    }

    void cleanup() {
        CommandStreams.guarded()?.close()
        GradleBuild.closeConnections()
        ConsoleCommands.setSession(ConsoleCommands.Session.NONE)
        GradleBuild.setRunner(null)
        GradleBuild.setNonInteractive(false)
        GradleBuild.outputOfProcess(null, null)
        Diagnostics.setOn(null)
        System.setOut(originalOut)
        System.setErr(originalErr)
        System.setIn(originalIn)
    }

    void cleanupSpec() {
        gradle.close()
    }

    void "a thread that was started while a call ran writes for it while it runs, and for no call once it has ended, also after it ran another"() {
        given:
        CommandStreams first = CommandStreams.capture()
        CommandStreams second = CommandStreams.capture()
        ExecutorService pool = null

        when: 'a pool gets its thread while the first call runs, as the threads of a kept connection or of a scheduler do'
        CommandStreams.bind(first, {
            pool = Executors.newSingleThreadExecutor()
            pool.submit({ CommandStreams.current().out.println('the pool, while the first call runs') } as Runnable).get()
        } as Callable<Object>)
        CommandStreams afterFirst = pool.submit({ CommandStreams.current() } as Callable<CommandStreams>).get()

        then: 'it wrote for the call that ran, and writes where nothing is bound since that ended'
        first.standardOutput().readLines() == ['the pool, while the first call runs']
        afterFirst.out.is(System.out)
        afterFirst.err.is(System.err)

        when: 'the thread runs another call, and then nothing'
        CommandStreams duringSecond = pool.submit({ CommandStreams.bind(second, { CommandStreams.current() } as Callable<CommandStreams>) } as Callable<CommandStreams>).get()
        CommandStreams afterSecond = pool.submit({ CommandStreams.current() } as Callable<CommandStreams>).get()

        then: 'what it inherited from the first call is not bound again when the second ends'
        duringSecond.is(second)
        afterSecond.out.is(System.out)
        afterSecond.err.is(System.err)
        first.merged() == 'the pool, while the first call runs' + System.lineSeparator()
        second.merged() == ''

        cleanup:
        pool?.shutdownNow()
    }

    void "the lines of captured streams are given to a listener as each is completed, and are counted"() {
        given:
        List<String> heard = Collections.synchronizedList(new ArrayList<String>())
        CommandStreams streams = CommandStreams.capture({ String line ->
            heard << line
            if (line == 'the listener fails here') {
                throw new IllegalStateException('the listener fails')
            }
        } as Consumer<String>)
        byte[] cut = 'café\n'.getBytes(StandardCharsets.UTF_8)

        expect:
        streams.lineCount() == 0
        streams.lastLine() == null

        when: 'a line in pieces, an error line between two pieces of the output, a carriage return, and a character cut in two'
        streams.out.print('one ')
        streams.out.print('line\r\ntwo')
        streams.err.println('an error')
        streams.out.println(' lines')
        streams.out.write(cut, 0, 4)
        streams.out.write(cut, 4, cut.length - 4)
        streams.err.println('the listener fails here')
        streams.out.print('not complete')

        then: 'the output and the errors are cut into lines apart, and what the listener throws changes nothing'
        heard == ['one line', 'an error', 'two lines', 'café', 'the listener fails here']
        streams.lineCount() == 5
        streams.lastLine() == 'the listener fails here'
        streams.merged().endsWith('not complete')
        streams.standardOutput().readLines() == ['one line', 'two lines', 'café', 'not complete']

        when:
        CommandStreams.current().lineCount()

        then: 'the streams of the process count nothing'
        thrown(IllegalStateException)
    }

    void "the guard keeps the standard output for its taker, and sends what else is written to the call that writes, or to the standard error of the process"() {
        given:
        ByteArrayOutputStream protocol = new ByteArrayOutputStream()
        ByteArrayOutputStream stderr = new ByteArrayOutputStream()
        InputStream input = new ByteArrayInputStream('{"jsonrpc":"2.0","method":"server/discover","id":1}\n'.getBytes(StandardCharsets.UTF_8))
        PrintStream before = System.out
        CommandStreams call = CommandStreams.capture()

        when:
        CommandStreams.Guard guard = CommandStreams.guard(input, protocol, new PrintStream(stderr, true))

        then: 'there is one guard, the standard error of the process is the one it was given, and System.in has ended'
        CommandStreams.guarded().is(guard)
        CommandStreams.guard().is(guard)
        CommandStreams.processErr().is(guard.error)
        System.in.read() == -1

        when: 'a call writes, a thread it starts writes, the diagnostics are said, and threads of no call write'
        CommandStreams.bind(call, {
            System.out.println('the output of the call')
            System.err.println('an error of the call')
            println 'println of the call'
            Thread started = new Thread({ System.out.println('a thread the call started') })
            started.start()
            started.join()
            Diagnostics.setOn(true)
            Diagnostics.say('topic', 'a diagnostic')
            Diagnostics.setOn(null)
            CommandStreams.bind(CommandStreams.current(), { System.out.println('the call bound again') } as Callable<Object>)
        } as Callable<Object>)
        Thread nobody = new Thread({
            System.out.println('the output of no call')
            System.err.println('an error of no call')
        })
        nobody.start()
        nobody.join()
        CommandStreams.bind(CommandStreams.current(), { System.out.println('the streams of the process, bound') } as Callable<Object>)
        guard.takeOutput().write('{"jsonrpc":"2.0","id":1,"result":{}}\n'.getBytes(StandardCharsets.UTF_8))

        then: 'the call has what it and its thread wrote, and nothing else'
        call.standardOutput().readLines() == ['the output of the call', 'println of the call', 'a thread the call started', 'the call bound again']
        call.standardError().readLines() == ['an error of the call']

        and: 'the standard error of the process has the diagnostics and what no call wrote'
        stderr.toString(StandardCharsets.UTF_8).readLines() == ['| Diagnostic topic: a diagnostic', 'the output of no call', 'an error of no call',
                                                                'the streams of the process, bound']

        and: 'the standard output has what its taker wrote, and nothing else'
        protocol.toString(StandardCharsets.UTF_8) == '{"jsonrpc":"2.0","id":1,"result":{}}\n'
        guard.takeInput().is(input)

        and: 'nothing reached the streams the process had before'
        processOut.size() == 0
        processErr.size() == 0

        when:
        guard.takeOutput()

        then: 'the standard output has one taker'
        thrown(IllegalStateException)

        when:
        guard.takeInput()

        then:
        thrown(IllegalStateException)

        when:
        CommandStreams.guard(input, protocol, System.err)

        then: 'and there is one guard'
        thrown(IllegalStateException)

        when:
        guard.close()

        then: 'closing it puts the streams back'
        System.out.is(before)
        CommandStreams.guarded() == null
        CommandStreams.processErr().is(System.err)
    }

    @IgnoreIf({ os.windows })
    void "what Gradle, a standalone tool and a program write reaches the call they run for, from the threads that copy it, and nothing reaches the process"() {
        given: 'a project whose wrapper writes lines with the marker it is given, and a standalone tool'
        project(MARKED_WRAPPER)
        standaloneTool()
        // a program whose output is that of the process: Gradle would be given it if the command did not write to streams of its own
        GradleBuild.outputOfProcess(System.out, System.err)
        GradleBuild.setRunner(GradleBuild.PROCESS)
        ForgeContext context = ForgeContext.of(beanContext)
        CommandStreams build = CommandStreams.capture()
        CommandStreams tool = CommandStreams.capture()
        CommandStreams program = CommandStreams.capture()

        when:
        int built = ForgeCli.execute(context, build, '--plain-output', '--non-interactive', 'compile', '-Dmarker=built')
        int ran = ForgeCli.execute(context, tool, '--plain-output', '--non-interactive', 'tool', 'an argument')
        int programExit = CommandStreams.bind(program, { ConsoleProgram.run('echo program out; echo program err >&2', dir, false) } as Callable<Integer>)

        then: 'Gradle wrote to the call that ran it: a call keeps one text, so its process has one stream, and its errors are where it wrote them'
        built == 0
        build.standardOutput().readLines() == (0..4).collectMany { int line -> ["build out ${line} built".toString(), "build err ${line} built".toString()] }
        build.standardError().readLines() == []
        build.merged().readLines().findAll { it.startsWith('build ') } == build.standardOutput().readLines()

        and: 'so did the tool'
        ran == 0
        tool.standardOutput().readLines().first() == 'arguments=1'
        tool.standardError().readLines() == ['| Running the tool tool of org.example:tool-cli-standalone:1.0, as a process of its own, without Gradle']

        and: 'and the program'
        programExit == 0
        program.standardOutput().readLines() == ['program out', 'program err']
        program.standardError().readLines() == []

        and: 'nothing reached the process'
        processOut.size() == 0
        processErr.size() == 0
    }

    @IgnoreIf({ os.windows })
    void "in grails mcp the builds run through the Tooling API on one kept connection, and what its threads write reaches the call of each build"() {
        given:
        gradle.project(dir, new File(previousUsrDir, '..'))
        new File(dir, 'build.gradle').text = MARKED_BUILD
        new File(dir, 'gradlew').text = ToolingApiBuildSpec.WRAPPER_THAT_SAYS_SO
        GradleBuild.setRunner(GradleBuild.AUTO)
        ConsoleCommands.setSession(ConsoleCommands.Session.MCP)
        ForgeContext context = ForgeContext.of(beanContext)
        int before = ToolingApiRunner.connectionsMade
        CommandStreams first = CommandStreams.capture()
        CommandStreams second = CommandStreams.capture()

        when: 'two builds, the second on the connection, and the threads of the Tooling API, that the first made'
        int firstExit = ForgeCli.execute(context, first, '--plain-output', '--non-interactive', 'compile', '-Dmarker=first')
        int secondExit = ForgeCli.execute(context, second, '--plain-output', '--non-interactive', 'compile', '-Dmarker=second')

        then:
        firstExit == 0
        secondExit == 0
        marked(first.merged()) == (0..4).collectMany { int line -> ["build out ${line} first".toString(), "build err ${line} first".toString()] } as Set
        marked(second.merged()) == (0..4).collectMany { int line -> ["build out ${line} second".toString(), "build err ${line} second".toString()] } as Set
        !new File(dir, 'wrapper-was-run.txt').exists()

        and: 'one connection was made, and is kept'
        ToolingApiRunner.connectionsMade == before + 1
        ToolingApiRunner.connectionKept

        and: 'nothing reached the process'
        !processOut.toString().contains('build ')
        !processErr.toString().contains('build ')
    }

    private static Set<String> marked(String output) {
        return output.readLines().findAll { String line -> line.startsWith('build ') } as Set<String>
    }

    // a Grails project whose wrapper is the script given
    private void project(String wrapper) {
        new File(dir, 'grails-app').mkdirs()
        new File(dir, 'settings.gradle').text = "rootProject.name = 'plain'\n"
        File script = new File(dir, 'gradlew')
        script.text = wrapper
        script.setExecutable(true, false)
    }

    // the tool of StandaloneToolsSpec, with the class that stands in for the one that runs it
    private void standaloneTool() {
        File catalog = new File(dir, ToolCatalogFile.LOCATION)
        catalog.parentFile.mkdirs()
        catalog.text = ProjectToolsSpec.doc([StandaloneToolsSpec.TOOL])
        File classes = new File(dir, 'build/classes/groovy/standalone')
        classes.mkdirs()
        new File(dir, 'src/standalone/groovy').mkdirs()
        File library = new File(dir, 'lib/library')
        library.mkdirs()
        File source = new File(dir, 'build/StandaloneToolRunner.java')
        source.text = StandaloneToolsSpec.RUNNER
        assert ToolProvider.getSystemJavaCompiler().run(null, null, null, '-d', library.path, source.path) == 0
        File file = new File(dir, StandaloneToolsFile.LOCATION)
        file.parentFile.mkdirs()
        Files.deleteIfExists(file.toPath())
        file.text = JsonOutput.toJson([format: 1, tools: ['tool'], classpath: [classes.path, library.path], sources: [new File(dir, 'src/standalone').path], java: 17]) + '\n'
    }
}

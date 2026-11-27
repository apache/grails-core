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
import org.apache.grails.forge.cli.mcp.McpLeftovers
import java.nio.file.Files
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.function.Supplier
import javax.tools.ToolProvider
import groovy.json.JsonOutput
import org.grails.forge.application.ApplicationType
import org.grails.forge.application.WebAvailableFeatures
import org.grails.forge.cli.CommandFixture
import org.grails.forge.cli.CommandSpec
import org.grails.forge.cli.ForgeCli
import org.grails.forge.cli.ForgeContext
import spock.lang.IgnoreIf
import spock.lang.Timeout
import spock.lang.Unroll

/**
 * What one process keeps between the command lines {@code grails mcp} runs in it, and what each call
 * leaves as it found it: the kind of session, the plain output, the current directory, the standard
 * streams, the streams bound to the thread and its interrupt, whether the builds may read what is
 * typed, and what the project generator lists for one call while another describes its commands. A
 * call is cancelled by interrupting its thread, as the console's Ctrl-C does.
 */
@IgnoreIf({ os.windows })
@Timeout(600)
class McpStaticStateSpec extends CommandSpec implements CommandFixture {

    // the Gradle wrapper of the project: it writes down what it was told, and does what the marker it is given says
    static final String WRAPPER = '''\
        #!/bin/sh
        here="$(dirname "$0")"
        marker=none
        for argument in "$@"; do
          case "$argument" in -Dmarker=*) marker="${argument#-Dmarker=}" ;; esac
        done
        echo "nonInteractive=$GRAILS_NON_INTERACTIVE" > "$here/build-$marker.txt"
        for argument in "$@"; do
          case "$argument" in *bootRun) echo $$ > "$here/app-started"; exec sleep 60 ;; esac
        done
        case "$marker" in
          fail) echo "the build fails"; exit 1 ;;
          waits) echo $$ > "$here/build-started"; echo "the build waits"; exec sleep 60 ;;
        esac
        echo "built $marker"
        '''.stripIndent()

    // the class a standalone tool is started with, which here waits until it is ended
    static final String WAITING_TOOL = '''\
        package org.apache.grails.core.cli;

        public class StandaloneToolRunner {

            public static void main(String[] args) throws Exception {
                java.nio.file.Files.writeString(java.nio.file.Paths.get(System.getProperty("base.dir"), "tool-started"),
                        String.valueOf(ProcessHandle.current().pid()));
                System.out.println("the tool waits");
                Thread.sleep(60000);
            }
        }
        '''.stripIndent()

    PrintStream originalOut
    PrintStream originalErr
    InputStream originalIn
    ByteArrayOutputStream protocol = new ByteArrayOutputStream()
    ByteArrayOutputStream processErr = new ByteArrayOutputStream()
    CommandStreams.Guard guard
    ForgeContext context

    void setup() {
        dir = dir.canonicalFile
        System.setProperty('user.dir', dir.absolutePath)
        originalOut = System.out
        originalErr = System.err
        originalIn = System.in
        generateProject(ApplicationType.WEB)
        File wrapper = new File(dir, 'gradlew')
        wrapper.text = WRAPPER
        wrapper.setExecutable(true, false)
        context = ForgeContext.of(beanContext)
        GradleBuild.setRunner(GradleBuild.PROCESS)
    }

    void cleanup() {
        guard?.close()
        PlainOutput.leave()
        ConsoleCommands.setSession(ConsoleCommands.Session.NONE)
        GradleBuild.setRunner(null)
        GradleBuild.setNonInteractive(false)
        GradleBuild.outputOfProcess(null, null)
        StandaloneToolProcess.setJavaHome(null)
        Thread.interrupted()
        System.setOut(originalOut)
        System.setErr(originalErr)
        System.setIn(originalIn)
        McpLeftovers.assertNoneLeft()
    }

    // what grails mcp does before it serves, as GrailsCli.main and the command do it
    private void serve() {
        GradleBuild.outputOfProcess(System.out, System.err)
        guard = CommandStreams.guard(new ByteArrayInputStream(new byte[0]), protocol, new PrintStream(processErr, true))
        ConsoleCommands.setSession(ConsoleCommands.Session.MCP)
        PlainOutput.enter()
    }

    void "each call leaves the session, the plain output, the current directory, the standard streams, what is bound and the interrupt as it found them"() {
        given:
        String ansiBefore = System.getProperty('picocli.ansi')
        serve()
        PrintStream routedOut = System.out
        PrintStream routedErr = System.err
        InputStream ended = System.in
        List<List<String>> calls = [
                ['help', '--json'],
                ['project', 'show', '--json'],
                ['create-controller', 'Book'],
                ['create-controller'],
                ['create-app', 'shop'],
                ['compile', '-Dmarker=ok'],
                ['compile', '-Dmarker=fail'],
                ['no-such-command']
        ]
        Map<String, Integer> exits = [:]
        List<String> changed = []

        when:
        for (List<String> call : calls) {
            CommandStreams streams = CommandStreams.capture()
            exits[call.join(' ')] = ForgeCli.execute(context, streams, (['--plain-output', '--non-interactive'] + call) as String[])
            Map<String, Object> now = [session        : ConsoleCommands.session, ansi: System.getProperty('picocli.ansi'), plain: PlainOutput.on,
                                       directory      : System.getProperty('user.dir'), out: System.out.is(routedOut), err: System.err.is(routedErr),
                                       input          : System.in.is(ended), bound: CommandStreams.current().out.is(System.out),
                                       interrupted    : Thread.currentThread().isInterrupted(), nonInteractive: GradleBuild.nonInteractive,
                                       guarded        : CommandStreams.guarded().is(guard)]
            Map<String, Object> expected = [session    : ConsoleCommands.Session.MCP, ansi: 'false', plain: true, directory: dir.absolutePath, out: true, err: true,
                                            input      : true, bound: true, interrupted: false, nonInteractive: true, guarded: true]
            if (now != expected) {
                changed << "${call.join(' ')}: ${now}".toString()
            }
        }

        then: 'the calls ran as they do at the terminal'
        exits == ['help --json': 0, 'project show --json': 0, 'create-controller Book': 0, 'create-controller': 2, 'create-app shop': 0, 'compile -Dmarker=ok': 0,
                  'compile -Dmarker=fail': 1, 'no-such-command': 2]

        and: 'none changed what the next call finds'
        changed == []

        and: 'nothing reached the standard output'
        protocol.size() == 0

        when: 'the server ends'
        PlainOutput.leave()
        ConsoleCommands.setSession(ConsoleCommands.Session.NONE)
        guard.close()
        guard = null

        then: 'the process is as it was'
        System.getProperty('picocli.ansi') == ansiBefore
        System.out.is(originalOut)
        System.err.is(originalErr)
        System.in.is(originalIn)
    }

    void "in grails mcp no build is given the input of the process, also when another command line has said that one may wait for it"() {
        given:
        serve()

        when: 'a command line that ended has reset what was asked, and this one does not ask'
        GradleBuild.setNonInteractive(false)
        int exit = ForgeCli.execute(context, CommandStreams.capture(), '--plain-output', 'compile', '-Dmarker=asked')

        then:
        exit == 0
        new File(dir, 'build-asked.txt').text == 'nonInteractive=true\n'
    }

    @Unroll
    void "a call that #what is cancelled by interrupting its thread: what it started is ended, it says so, and the interrupt is left for the caller"() {
        given:
        serve()
        if (what.contains('tool')) {
            standaloneTool()
        }
        CommandStreams streams = CommandStreams.capture()
        Map<String, Object> result = Collections.synchronizedMap([:])
        Thread call = new Thread({
            result.exit = run(streams, command)
            result.interrupted = Thread.currentThread().isInterrupted()
        })

        when:
        call.start()
        File started = new File(dir, startedFile)
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60)
        while (!started.isFile() || started.text.trim().isEmpty()) {
            assert System.nanoTime() < deadline: "${startedFile} was not written: ${streams.merged()}"
            Thread.sleep(50)
        }
        long pid = started.text.trim() as long
        call.interrupt()
        call.join(TimeUnit.SECONDS.toMillis(30))

        then: 'the call ended, said why, and left the interrupt set'
        !call.alive
        result.exit == exit
        streams.merged().contains(said)
        result.interrupted

        and: 'what it started has ended'
        ended(pid)

        where:
        what                                | command                                                                  | startedFile     | exit | said
        'runs Gradle'                       | ['compile', '-Dmarker=waits']                                            | 'build-started' | 1    | GradleBuild.STOPPED
        'runs a standalone tool'            | ['tool']                                                                 | 'tool-started'  | 1    | 'The command was stopped before it finished, because it was cancelled'
        'runs a program'                    | null                                                                     | 'program'       | 130  | '| Exit code 130'
        'waits for the application it left' | ['run-app', '--detach', "--port=${freePort()}".toString(), '--ready-timeout=120'] | 'app-started' | 1 | 'The application was stopped before it answered, because the command was cancelled'
    }

    void "a context that serves several calls at once lists the values of an option for one while another describes its commands without them"() {
        given:
        ForgeContext.Candidates features = new ForgeContext.Candidates(context, WebAvailableFeatures)
        CountDownLatch describing = new CountDownLatch(1)
        CountDownLatch listed = new CountDownLatch(1)
        Thread describer = new Thread({
            context.withoutListing({
                describing.countDown()
                listed.await(30, TimeUnit.SECONDS)
                return null
            } as Supplier<Object>)
        })

        when: 'one thread describes, and another lists meanwhile'
        describer.start()
        describing.await(30, TimeUnit.SECONDS)
        List<String> values = features.toList()
        listed.countDown()
        describer.join(30_000)

        then:
        values.size() > 1
        !values.contains(ForgeContext.Candidates.LISTED_ELSEWHERE)

        and: 'the one that describes does not list them'
        context.withoutListing({ features.toList() } as Supplier<List<String>>) == [ForgeContext.Candidates.LISTED_ELSEWHERE]
    }

    private int run(CommandStreams streams, List<String> command) {
        if (command == null) {
            return CommandStreams.bind(streams, { ConsoleProgram.run('echo $$ > program; exec sleep 60', dir, false) } as Callable<Integer>)
        }
        return ForgeCli.execute(context, streams, (['--plain-output', '--non-interactive'] + command) as String[])
    }

    // whether a process has ended, or does within a while
    private static boolean ended(long pid) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30)
        while (ProcessHandle.of(pid).map { ProcessHandle each -> each.alive }.orElse(false)) {
            if (System.nanoTime() > deadline) {
                ProcessHandle.of(pid).ifPresent { ProcessHandle each -> each.destroyForcibly() }
                return false
            }
            Thread.sleep(50)
        }
        return true
    }

    private static int freePort() {
        return new ServerSocket(0).withCloseable { ServerSocket socket -> socket.localPort }
    }

    // a standalone tool of a plugin that waits until it is ended
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
        source.setText(WAITING_TOOL, StandardCharsets.UTF_8.name())
        assert ToolProvider.getSystemJavaCompiler().run(null, null, null, '-d', library.path, source.path) == 0
        File file = new File(dir, StandaloneToolsFile.LOCATION)
        file.parentFile.mkdirs()
        Files.deleteIfExists(file.toPath())
        file.text = JsonOutput.toJson([format: 1, tools: ['tool'], classpath: [classes.path, library.path], sources: [new File(dir, 'src/standalone').path], java: 17]) + '\n'
    }
}

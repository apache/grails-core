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
import java.util.concurrent.atomic.AtomicInteger
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.grails.forge.cli.CommandSpec
import org.grails.forge.cli.ForgeCli
import org.grails.forge.cli.ForkedCli
import spock.lang.IgnoreIf
import spock.lang.Timeout
import spock.util.concurrent.PollingConditions

/**
 * A {@code run-app} that stays with its application and runs the build through the Tooling API of
 * Gradle from its own JVM, as it does where {@code GRAILS_GRADLE_RUNNER} is {@code tooling-api} and in
 * the interactive console with {@code --foreground}, and {@code stop-app} for it: there is no Gradle that {@code run-app}
 * started, so the record names the command line itself, which is asked to end its build and is never
 * ended. The command line is a JVM of its own here, as it is for a user, since what is held to is what
 * happens between two processes and when one of them is gone.
 *
 * <p>The build needs nothing of Grails: its {@code bootRun} starts a program of one file that answers
 * on the port. The daemon is no daemon of whoever runs the specification, see {@link IsolatedGradle}.
 * {@code WarmGradleDaemonSpec} has the same for the Gradle wrapper as a process.</p>
 */
@IgnoreIf({ os.windows })
@Timeout(600)
class ToolingApiRunAppSpec extends CommandSpec {

    // how long an application of the build has to answer: the build compiles it first, on a runner that can be slow
    static final int ANSWER_SECONDS = 300

    PrintStream originalOut
    PrintStream originalErr
    ByteArrayOutputStream out = new ByteArrayOutputStream()
    IsolatedGradle isolated = new IsolatedGradle()
    Process commandLine
    File said

    void setup() {
        originalOut = System.out
        originalErr = System.err
        System.setOut(new PrintStream(out, true))
        System.setErr(new PrintStream(out, true))
        isolated.project(dir, new File(previousUsrDir, '..'))
        new File(dir, 'build.gradle').text = WarmGradleDaemonSpec.BUILD
        new File(dir, 'Application.java').text = WarmGradleDaemonSpec.APPLICATION
        said = new File(isolated.registry, 'command-line.log')
        GradleBuild.setRunner(GradleBuild.TOOLING_API)
    }

    void cleanup() {
        GradleBuild.setRunner(null)
        System.setOut(originalOut)
        System.setErr(originalErr)
        List<Long> left = []
        try {
            if (commandLine != null && commandLine.alive) {
                commandLine.destroy()
                commandLine.waitFor(30, TimeUnit.SECONDS)
                commandLine.destroyForcibly()
            }
            ForgeCli.execute(beanContext, 'stop-app')
        } finally {
            File server = new File(dir, 'server.pid')
            if (server.isFile() && server.text.trim() ==~ /[0-9]+/ && alive(server.text.trim() as long)) {
                // an application that is still there a minute after the stop-app above is what a feature must not leave
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60)
                while (alive(server.text.trim() as long) && System.nanoTime() < deadline) {
                    Thread.sleep(100)
                }
                if (alive(server.text.trim() as long)) {
                    left.add(server.text.trim() as long)
                    ProcessHandle.of(server.text.trim() as long).ifPresent { ProcessHandle handle -> handle.destroyForcibly() }
                }
            }
            isolated.close()
        }
        assert left.isEmpty()
    }

    void "stop-app, from another process, has the run-app that stays with the application end its build, and ends no process itself"() {
        given:
        int port = freePort()

        when:
        runApp(port)
        long server = pid('server.pid')
        Map record = record()

        then: 'the record names the command line, and says that it does'
        record == [format: 1, pid: commandLine.pid(), started: commandLine.info().startInstant().get().toEpochMilli(), port: port, detached: false, commandLine: true]

        and: 'the application is a process of the daemon, and no Gradle wrapper was started for it'
        ProcessHandle.of(server).get().parent().get().pid() == daemon()
        !commandLine.toHandle().descendants().anyMatch { ProcessHandle each -> each.info().commandLine().orElse('').contains('GradleWrapperMain') }

        when:
        int stopped = ForgeCli.execute(beanContext, 'stop-app')

        then: 'stop-app says so when the port is free'
        stopped == 0
        errors() == []
        out.toString().readLines() == ['| Stopping application...', '| Application stopped.']
        !ApplicationPort.isOpen(port)

        and: 'the run-app has ended by itself, as one whose application was stopped as asked'
        commandLine.waitFor(30, TimeUnit.SECONDS)
        commandLine.exitValue() == 0
        said.text.readLines().findAll { String line -> line.startsWith('|') } == ['| Running application...', '| Application stopped']
        !new File(dir, '.grails/run/app.json').exists()
        !new File(dir, '.grails/run/app.stopping').exists()

        and: 'the application has ended, and the daemon is there for the next build'
        new PollingConditions(timeout: 60, delay: 0.05).eventually {
            assert !alive(server)
        }
        daemonIsIdle()
    }

    void "the application ends with the build when the run-app that stays with it is interrupted from the terminal"() {
        given:
        int port = freePort()

        when:
        runApp(port)
        long server = pid('server.pid')
        long daemon = daemon()
        new ProcessBuilder('kill', '-INT', commandLine.pid() as String).start().waitFor()

        then: 'the command line ends as one that was interrupted, after it has said that the build was stopped'
        commandLine.waitFor(60, TimeUnit.SECONDS)
        commandLine.exitValue() == 130
        said.text.readLines().findAll { String line -> line.startsWith('|') } == ['| Running application...'] ||
                said.text.contains('Gradle was stopped before it finished, because the command was cancelled')

        and: 'the application is ended by the daemon, which stays'
        new PollingConditions(timeout: 60, delay: 0.05).eventually {
            assert !alive(server)
        }
        !ApplicationPort.isOpen(port)
        alive(daemon)
        daemonIsIdle()

        when: 'the record it may have left names a process that has ended'
        ForgeCli.execute(beanContext, 'stop-app')

        then:
        !new File(dir, '.grails/run/app.json').exists()
    }

    void "the application ends with the build when the run-app that stays with it is killed, and what it recorded is seen to be over"() {
        given:
        int port = freePort()

        when:
        runApp(port)
        long server = pid('server.pid')
        long recorded = commandLine.pid()
        commandLine.destroyForcibly()

        then: 'nothing told the daemon but that the command line is gone'
        commandLine.waitFor(30, TimeUnit.SECONDS)
        new PollingConditions(timeout: 30, delay: 0.05).eventually {
            assert !alive(server)
        }
        !ApplicationPort.isOpen(port)
        daemonIsIdle()

        when:
        int stopped = ForgeCli.execute(beanContext, 'stop-app')

        then:
        stopped == 1
        errors() == ["| Error No application that run-app started is running for this project: process ${recorded}, which was started for it, has ended. Nothing was stopped, and the record was removed"]
        !new File(dir, '.grails/run/app.json').exists()
    }

    void "the build of a run-app that stays with the application is cancelled with the command, in the JVM of an interactive console"() {
        given:
        int port = freePort()
        AtomicInteger exit = new AtomicInteger(-1)
        Thread command = new Thread({ exit.set(ForgeCli.execute(beanContext, 'run-app', '--foreground', '--port', port as String)) })

        when:
        command.start()
        answers(port, { command.alive }, { "it ended with ${exit.get()} and wrote: ${out}" })
        long server = pid('server.pid')

        then: 'the record names this JVM, which is the command line here'
        record().pid == ProcessHandle.current().pid()
        record().commandLine == true
        GradleBuild.isRunning()

        when:
        command.interrupt()
        command.join(60_000)

        then:
        !command.alive
        exit.get() == 1
        errors() == ['| Error Gradle was stopped before it finished, because the command was cancelled']
        !new File(dir, '.grails/run/app.json').exists()
        new PollingConditions(timeout: 60, delay: 0.05).eventually {
            assert !alive(server)
        }
        daemonIsIdle()
    }

    void "stop-app works for a run-app that stays with the application in the same JVM, which it asks as any other"() {
        given:
        int port = freePort()
        AtomicInteger exit = new AtomicInteger(-1)
        Thread command = new Thread({ exit.set(ForgeCli.execute(beanContext, 'run-app', '--foreground', '--port', port as String)) })

        when:
        command.start()
        answers(port, { command.alive }, { "it ended with ${exit.get()} and wrote: ${out}" })
        long server = pid('server.pid')
        out.reset()
        int stopped = ForgeCli.execute(beanContext, 'stop-app')
        command.join(60_000)

        then:
        stopped == 0
        exit.get() == 0
        errors() == []
        out.toString().readLines().findAll { String line -> line.startsWith('|') } as Set == ['| Stopping application...', '| Application stopped', '| Application stopped.'] as Set

        and: 'the application, a process of the daemon that has closed its port, ends'
        new PollingConditions(timeout: 60, delay: 0.05).eventually {
            assert !alive(server)
        }
    }

    void "a record of a command line that is not the process it names is not asked anything, and one that does not answer is not ended"() {
        given:
        new File(dir, '.grails/run').mkdirs()
        File record = new File(dir, '.grails/run/app.json')
        ProcessHandle bystander = ProcessHandle.current().parent().get()
        long began = bystander.info().startInstant().get().toEpochMilli()

        when: 'the process did not begin when the record says'
        record.text = JsonOutput.toJson([format: 1, pid: bystander.pid(), started: began + 1, port: 0, detached: false, commandLine: true])
        int exit = ForgeCli.execute(beanContext, 'stop-app')

        then:
        exit == 1
        errors() == ["| Error No application that run-app started is running for this project: process ${bystander.pid()} is not the one that was started for it: " +
                             'it did not begin when the record says. Nothing was stopped, and the record was removed']
        !record.exists()
        !new File(dir, '.grails/run/app.stopping').exists()

        when: 'the record is not a file of the user'
        record.text = JsonOutput.toJson([format: 1, pid: bystander.pid(), started: began, port: 0, detached: false, commandLine: true])
        ConsoleProject.setOwnedByUser { java.nio.file.Path path -> path.fileName.toString() != 'app.json' }
        out.reset()
        exit = ForgeCli.execute(beanContext, 'stop-app')

        then:
        exit == 1
        errors().size() == 1
        errors()[0].contains('it is not owned by the user who runs the command')
        !new File(dir, '.grails/run/app.stopping').exists()
        bystander.alive

        when: 'what says so is no yes or no'
        ConsoleProject.setOwnedByUser(null)
        record.text = JsonOutput.toJson([format: 1, pid: bystander.pid(), started: began, port: 0, detached: false, commandLine: 'yes'])
        out.reset()
        exit = ForgeCli.execute(beanContext, 'stop-app')

        then:
        exit == 1
        errors().size() == 1
        errors()[0].contains('it does not name a process, an instant and a port')
        bystander.alive

        when: 'the process is the one the record names, and is no run-app that looks for what stop-app says'
        record.text = JsonOutput.toJson([format: 1, pid: bystander.pid(), started: began, port: 0, detached: false, commandLine: true])
        out.reset()
        exit = ForgeCli.execute(beanContext, 'stop-app')

        then: 'it is asked, is given its time, and is left alone'
        exit == 1
        errors() == ["| Error The run-app that stays with the application, process ${bystander.pid()}, was asked to end it and has not within 30 seconds. " +
                             'Nothing is ended by force: end that command where it runs, with Ctrl-C. The record was kept']
        bystander.alive
        record.exists()
        !new File(dir, '.grails/run/app.stopping').exists()

        cleanup:
        ConsoleProject.setOwnedByUser(null)
        record.delete()
    }

    void "run-app --detach starts the Gradle wrapper as a process and records that, as it has, where the others run through the Tooling API"() {
        given:
        int port = freePort()

        when:
        int exit = ForgeCli.execute(beanContext, 'run-app', '--detach', '--port', port as String, '--ready-timeout', '600')
        long server = pid('server.pid')
        Map record = record()

        then:
        exit == 0
        errors() == []
        record.keySet() == ['format', 'pid', 'started', 'port', 'detached'] as Set
        record.detached == true
        record.pid != ProcessHandle.current().pid()
        ProcessHandle.of(record.pid as long).get().info().commandLine().orElse('gradle').contains('gradle')

        when:
        out.reset()
        int stopped = ForgeCli.execute(beanContext, 'stop-app')

        then:
        stopped == 0
        out.toString().readLines() == ['| Stopping application...', '| Application stopped.']
        !alive(record.pid as long)
        new PollingConditions(timeout: 60, delay: 0.05).eventually {
            assert !alive(server)
        }
    }

    // run-app as a user runs it who asked for the Tooling API: a command line of its own
    private void runApp(int port) {
        ProcessBuilder builder = ForkedCli.builder(dir, [], 'run-app', '--port', port as String).redirectErrorStream(true).redirectOutput(said)
                .redirectInput(ProcessBuilder.Redirect.from(new File('/dev/null')))
        builder.environment().put(GradleBuild.RUNNER, GradleBuild.TOOLING_API)
        commandLine = builder.start()
        answers(port, { commandLine.alive }, { "it ended with ${commandLine.alive ? 'nothing yet' : commandLine.exitValue()} and wrote: ${said.text}" })
    }

    // waits for the application of a run-app to answer and for the record of the run-app, and fails at once, with what
    // the run-app wrote, when the run-app has ended first: an application that is then not there never answers
    private void answers(int port, Closure<Boolean> running, Closure<String> told) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(ANSWER_SECONDS)
        while (!(ApplicationPort.isReady(port) && new File(dir, '.grails/run/app.json').isFile())) {
            if (!running.call()) {
                throw new AssertionError("run-app ended before its application answered on port ${port}: ${told.call()}".toString())
            }
            if (System.nanoTime() > deadline) {
                throw new AssertionError("The application of run-app did not answer on port ${port} within ${ANSWER_SECONDS} seconds: ${told.call()}".toString())
            }
            Thread.sleep(100)
        }
    }

    private Map record() {
        return new JsonSlurper().parse(new File(dir, '.grails/run/app.json')) as Map
    }

    private long daemon() {
        return pid('build-daemon.pid')
    }

    // the daemon of the build is alive, and Gradle says of it that it is idle
    private boolean daemonIsIdle() {
        long daemon = daemon()
        new PollingConditions(timeout: 60, delay: 0.2).eventually {
            assert alive(daemon)
            List<String> daemons = isolated.gradle(dir, '--status').output.readLines().findAll { String line -> line.trim() ==~ /[0-9]+\s+(IDLE|BUSY|CANCELED|STOPPING)\s.*/ }
            assert daemons.size() == 1 && daemons[0].trim() ==~ /${daemon}\s+IDLE\s.*/
        }
        return true
    }

    private long pid(String name) {
        File file = new File(dir, name)
        new PollingConditions(timeout: 60, delay: 0.1).eventually {
            assert file.isFile() && file.text.endsWith('\n')
        }
        return file.text.trim() as long
    }

    // a port that nothing has on the loopback address, where the application binds, nor on every address. Java binds a
    // ServerSocket with SO_REUSEADDR, which does not refuse a port another process holds on one of them only, and the
    // application, given such a port, would not get it
    private static int freePort() {
        for (int attempt = 0; attempt < 100; attempt++) {
            int candidate = new ServerSocket(0, 1, InetAddress.getLoopbackAddress()).withCloseable { ServerSocket socket -> socket.localPort }
            if (bindsAlone(new InetSocketAddress(InetAddress.getLoopbackAddress(), candidate)) && bindsAlone(new InetSocketAddress(candidate))) {
                return candidate
            }
        }
        throw new IllegalStateException('No port is free on the loopback address and on every address')
    }

    private static boolean bindsAlone(InetSocketAddress address) {
        ServerSocket socket = new ServerSocket()
        try {
            socket.reuseAddress = false
            socket.bind(address)
            return true
        } catch (IOException ignored) {
            return false
        } finally {
            socket.close()
        }
    }

    private static boolean alive(long pid) {
        return ProcessHandle.of(pid).map { ProcessHandle handle -> handle.alive }.orElse(false)
    }

    private List<String> errors() {
        return out.toString().readLines().findAll { String line -> line.contains('| Error') }
    }
}

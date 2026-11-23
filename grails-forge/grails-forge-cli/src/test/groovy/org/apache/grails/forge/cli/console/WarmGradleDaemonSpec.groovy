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
import java.util.concurrent.atomic.AtomicInteger
import groovy.json.JsonSlurper
import org.grails.forge.cli.Application
import org.grails.forge.cli.CommandSpec
import org.grails.forge.cli.ForgeCli
import org.grails.forge.cli.ForkedCli
import spock.lang.IgnoreIf
import spock.lang.Timeout
import spock.lang.Unroll
import spock.util.concurrent.PollingConditions

/**
 * {@code run-app} and {@code stop-app} with Gradle as it is on a developer's computer: a daemon that
 * was running before {@code run-app}, which runs the build and starts the application, so that the
 * application is a process below the daemon and not below the Gradle that {@code run-app} started
 * and {@code stop-app} ends. What ends the application is then the daemon, which cancels a build
 * whose Gradle is gone; these features hold Gradle to that, by the process of the application and
 * not only by its port.
 *
 * <p>The build needs nothing of Grails: its {@code bootRun} starts a program of one file that
 * answers on the port. The wrapper is the one of this build, and the daemon is no daemon of whoever
 * runs the specification: see {@link IsolatedGradle}. The commands run the Gradle wrapper as a process
 * here, as the build has them do for the tests: what is held to is the Gradle that {@code run-app}
 * starts and records, which is there when the application is left running and when the environment
 * variable {@code GRAILS_GRADLE_RUNNER} asks for the wrapper. {@code ToolingApiRunAppSpec} has the same
 * for a {@code run-app} that stays with the application through the Tooling API.</p>
 */
@IgnoreIf({ os.windows })
@Timeout(600)
class WarmGradleDaemonSpec extends CommandSpec {

    static final String APPLICATION = '''\
        import com.sun.net.httpserver.HttpServer;
        import java.net.InetAddress;
        import java.net.InetSocketAddress;
        import java.nio.file.Files;
        import java.nio.file.Path;

        public class Application {

            public static void main(String[] args) throws Exception {
                HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), Integer.parseInt(args[0])), 0);
                server.createContext("/", exchange -> {
                    exchange.sendResponseHeaders(200, -1);
                    exchange.close();
                });
                server.start();
                Path slow = Path.of("shutdown-millis.txt");
                if (Files.exists(slow)) {
                    // an application that takes a while to shut down when it is asked to, and closes its port last
                    long millis = Long.parseLong(Files.readString(slow).trim());
                    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                        try {
                            Thread.sleep(millis);
                            Files.writeString(Path.of("shut-down.txt"), System.currentTimeMillis() + "\\n");
                        } catch (Exception ignored) {
                        }
                        server.stop(0);
                    }));
                }
                Files.writeString(Path.of("server.pid"), ProcessHandle.current().pid() + "\\n");
            }
        }
        '''.stripIndent()

    static final String BUILD = '''\
        def java = new File(System.getProperty('java.home'), 'bin/java').path
        def port = providers.systemProperty('grails.server.port').orElse('0')
        def directory = projectDir

        tasks.register('warm') {
            doLast { new File(directory, 'warm-daemon.pid').text = ProcessHandle.current().pid() + '\\n' }
        }
        tasks.register('bootRun', Exec) {
            commandLine java, 'Application.java', port.get()
            doFirst { new File(directory, 'build-daemon.pid').text = ProcessHandle.current().pid() + '\\n' }
        }
        '''.stripIndent()

    PrintStream originalOut
    PrintStream originalErr
    ByteArrayOutputStream out = new ByteArrayOutputStream()
    IsolatedGradle isolated = new IsolatedGradle()
    File registry = isolated.registry
    long daemon
    // a program that runs the command line in its own JVM, which a feature started
    Process program

    void setup() {
        originalOut = System.out
        originalErr = System.err
        System.setOut(new PrintStream(out, true))
        System.setErr(new PrintStream(out, true))
        isolated.project(dir, new File(previousUsrDir, '..'))
        new File(dir, 'build.gradle').text = BUILD
        new File(dir, 'Application.java').text = APPLICATION

        assert gradle('--daemon', 'warm', '-q').exit == 0
        daemon = new File(dir, 'warm-daemon.pid').text.trim() as long
    }

    void cleanup() {
        System.setOut(originalOut)
        System.setErr(originalErr)
        List<Long> left = []
        try {
            if (program != null) {
                program.descendants().forEach { ProcessHandle each -> each.destroyForcibly() }
                program.destroyForcibly()
                program.waitFor(60, TimeUnit.SECONDS)
            }
            ForgeCli.execute(beanContext, 'stop-app')
            gradle('--stop')
        } finally {
            // the application first: an application that is still there is what a feature must not leave
            ['server.pid', 'build-daemon.pid', 'warm-daemon.pid'].each { String name ->
                File file = new File(dir, name)
                if (file.isFile() && file.text.trim() ==~ /[0-9]+/ && alive(file.text.trim() as long)) {
                    if (name == 'server.pid') {
                        left.add(file.text.trim() as long)
                    }
                    ProcessHandle.of(file.text.trim() as long).ifPresent { ProcessHandle handle -> handle.destroyForcibly() }
                }
            }
            registry.deleteDir()
        }
        assert left.isEmpty()
    }

    void "stop-app ends an application that a Gradle daemon which was running before run-app started, and leaves the daemon"() {
        given:
        int port = freePort()

        when:
        int exit = ForgeCli.execute(beanContext, 'run-app', '--detach', '--port', port as String, '--ready-timeout', '600', '--', '--daemon')
        long server = pid('server.pid')
        Map record = new JsonSlurper().parse(new File(dir, '.grails/run/app.json')) as Map

        then: 'the application answers'
        exit == 0
        errors() == []
        ApplicationPort.isReady(port)

        and: 'it was started by the daemon that was there, and is no process below the Gradle that was recorded'
        pid('build-daemon.pid') == daemon
        ProcessHandle.of(server).get().parent().get().pid() == daemon
        record.pid != daemon
        !ProcessHandle.of(record.pid as long).get().descendants().anyMatch { ProcessHandle each -> each.pid() == server }

        when:
        out.reset()
        long asked = System.nanoTime()
        int stopped = ForgeCli.execute(beanContext, 'stop-app')
        long returned = System.nanoTime()

        then: 'stop-app says so when the port is free, which is within seconds'
        stopped == 0
        errors() == []
        out.toString().readLines() == ['| Stopping application...', '| Application stopped.']
        TimeUnit.NANOSECONDS.toSeconds(returned - asked) < 20
        !ApplicationPort.isOpen(port)
        !alive(record.pid as long)

        and: 'the process of the application has ended, or does within the moment it takes a process that has closed its port to end'
        new PollingConditions(timeout: 10, delay: 0.05).eventually {
            assert !alive(server)
        }

        and: 'the daemon is still there, has nothing to do, and is the only one the project has'
        daemonIsIdle()
    }

    @Unroll
    void "the application of a run-app that stays with it ends with the daemon's build when #what, and the daemon stays"() {
        given:
        int port = freePort()
        AtomicInteger exit = new AtomicInteger(-1)
        Thread command = new Thread({ exit.set(ForgeCli.execute(beanContext, 'run-app', '--port', port as String, '--', '--daemon')) })

        when:
        command.start()
        new PollingConditions(timeout: 300, delay: 0.1).eventually {
            assert ApplicationPort.isReady(port) && new File(dir, '.grails/run/app.json').isFile()
        }
        long server = pid('server.pid')
        Map record = new JsonSlurper().parse(new File(dir, '.grails/run/app.json')) as Map

        then:
        ProcessHandle.of(server).get().parent().get().pid() == daemon
        record.pid != daemon

        when: 'the Gradle that run-app started ends, and nothing tells the daemon but that'
        end(ProcessHandle.of(record.pid as long).get(), command)
        command.join(60_000)

        then:
        !command.alive
        exit.get() == 1
        errors() == [said]
        !new File(dir, '.grails/run/app.json').exists()

        and: 'the application is ended by the daemon'
        new PollingConditions(timeout: 20, delay: 0.05).eventually {
            assert !alive(server)
        }
        !ApplicationPort.isOpen(port)
        daemonIsIdle()

        where:
        what                                 | end                                                                    | said
        'the Gradle of run-app is ended'     | { ProcessHandle gradle, Thread run -> gradle.destroy() }               | '| Error Gradle did not succeed: it ended with exit code 143'
        'the Gradle of run-app is killed'    | { ProcessHandle gradle, Thread run -> gradle.destroyForcibly() }       | '| Error Gradle did not succeed: it ended with exit code 137'
        'the command is cancelled'           | { ProcessHandle gradle, Thread run -> run.interrupt() }                | '| Error Gradle was stopped before it finished, because the command was cancelled'
    }

    @IgnoreIf({ !ForkedCli.available() })
    void "when the console of a program is left, the application its run-app started through the daemon has shut down before the call returns"() {
        given: 'an application that takes three seconds to shut down, and closes its port last'
        int port = freePort()
        new File(dir, 'shutdown-millis.txt').text = '3000\n'
        new File(dir, 'typed.txt').text = "run-app --port ${port} -- --daemon\nexit\n"
        File returned = new File(dir, 'returned.txt')
        ProcessBuilder builder = ForkedCli.builder(dir, ["-D${EmbeddedConsoleSessionSpec.Host.PORT}=${port}".toString()], [] as String[])
                .redirectOutput(new File(dir, 'out.txt')).redirectErrorStream(true).redirectInput(new File(dir, 'typed.txt'))
        builder.command().set(builder.command().indexOf(Application.name), EmbeddedConsoleSessionSpec.Host.name)
        builder.environment().remove('GRAILS_SAFE_DIRECTORY')
        builder.environment().remove(GradleBuild.RUNNER)

        when: 'the console has returned to the program, which goes on'
        program = builder.start()
        new PollingConditions(timeout: 300, delay: 0.2).eventually {
            assert returned.isFile() && returned.text.endsWith('\n')
        }
        long server = pid('server.pid')
        List<String> atReturn = new File(dir, 'at-return.txt').text.trim().split(' ') as List<String>

        then: 'the console started the application through the daemon that was there, and ended with 0'
        new File(dir, 'out.txt').text.contains("| Application answers on port ${port}")
        returned.text == '0\n'
        pid('build-daemon.pid') == daemon
        program.alive

        and: 'when the call returned the port was free, and the application had finished its shutdown'
        atReturn[1] == 'false'
        new File(dir, 'shut-down.txt').isFile()
        (new File(dir, 'shut-down.txt').text.trim() as long) <= (atReturn[0] as long)
        new PollingConditions(timeout: 10, delay: 0.05).eventually {
            assert !alive(server)
        }

        and: 'the daemon is still there, and has nothing to do'
        daemonIsIdle()
    }

    // the daemon that was there before is alive, and Gradle says of it that it is idle
    private boolean daemonIsIdle() {
        new PollingConditions(timeout: 60, delay: 0.2).eventually {
            assert alive(daemon)
            List<String> daemons = gradle('--status').output.readLines().findAll { String line -> line.trim() ==~ /[0-9]+\s+(IDLE|BUSY|CANCELED|STOPPING)\s.*/ }
            assert daemons.size() == 1 && daemons[0].trim() ==~ /${daemon}\s+IDLE\s.*/
        }
        return true
    }

    private Map gradle(String... arguments) {
        return isolated.gradle(dir, arguments)
    }

    private long pid(String name) {
        File file = new File(dir, name)
        new PollingConditions(timeout: 60, delay: 0.1).eventually {
            assert file.isFile() && file.text.endsWith('\n')
        }
        return file.text.trim() as long
    }

    private static int freePort() {
        return new ServerSocket(0, 1, InetAddress.getLoopbackAddress()).withCloseable { ServerSocket socket -> socket.localPort }
    }

    private static boolean alive(long pid) {
        return ProcessHandle.of(pid).map { ProcessHandle handle -> handle.alive }.orElse(false)
    }

    private List<String> errors() {
        return out.toString().readLines().findAll { String line -> line.contains('| Error') }
    }
}

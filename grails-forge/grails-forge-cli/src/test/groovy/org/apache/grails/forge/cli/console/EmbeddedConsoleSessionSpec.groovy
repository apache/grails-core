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
import org.grails.forge.cli.Application
import org.grails.forge.cli.ForgeCli
import org.grails.forge.cli.ForkedCli
import spock.lang.IgnoreIf
import spock.lang.Specification
import spock.lang.Timeout
import spock.lang.Unroll
import spock.util.concurrent.PollingConditions

/**
 * The interactive console run by a program in its own JVM, as an IDE or a build runs it through
 * {@code GrailsCli.execute}, and not as the program {@code grails}: when the console is left, what
 * belongs to its session has ended before the call returns, although the JVM goes on. An application
 * that {@code run-app} started in the console without {@code --detach} belongs to the session; one
 * that {@code run-app --detach} started outlives it by design.
 *
 * <p>The console reads the input of the process, so the program runs in a JVM of its own, which
 * goes on after the console has returned and says what it saw then. The Gradle wrapper of the
 * project is a script around a program of one file that answers on a port, as in
 * {@link LifecycleCommandsSpec}.</p>
 */
@IgnoreIf({ os.windows || !ForkedCli.available() })
@Timeout(600)
class EmbeddedConsoleSessionSpec extends Specification {

    static final int SECONDS = 300

    File dir = Files.createTempDirectory('grailsforgeembedded').toFile().canonicalFile
    File written = new File(dir, 'out.txt')
    File returned = new File(dir, 'returned.txt')
    Process program
    List<Long> started = []

    void cleanup() {
        if (program != null) {
            program.descendants().forEach { ProcessHandle each -> each.destroyForcibly() }
            program.destroyForcibly()
            program.waitFor(60, TimeUnit.SECONDS)
        }
        File server = new File(dir, 'server.pid')
        if (server.isFile() && server.text.trim() ==~ /[0-9]+/) {
            started.add(server.text.trim() as long)
        }
        started.each { Long pid ->
            ProcessHandle.of(pid).ifPresent { ProcessHandle handle ->
                handle.descendants().forEach { ProcessHandle each -> each.destroyForcibly() }
                handle.destroyForcibly()
            }
        }
        dir.deleteDir()
    }

    @Unroll
    void "when the console of a program is left, #what"() {
        given:
        project()
        int port = freePort()
        File typed = new File(dir, 'typed.txt')
        typed.text = "run-app --port ${port}${options}\nexit\n"
        ProcessBuilder builder = ForkedCli.builder(dir, [], [] as String[]).redirectOutput(written).redirectErrorStream(true).redirectInput(typed)
        builder.command().set(builder.command().indexOf(Application.name), Host.name)
        builder.environment().remove('GRAILS_SAFE_DIRECTORY')
        builder.environment().remove(GradleBuild.RUNNER)

        when: 'the console has returned to the program, which goes on'
        program = builder.start()
        new PollingConditions(timeout: SECONDS, delay: 0.2).eventually {
            assert returned.isFile() && returned.text.endsWith('\n')
        }
        long server = new File(dir, 'server.pid').text.trim() as long
        started.add(server)

        then: 'the console started the application, and ended with 0'
        written.text.contains("| Application answers on port ${port}")
        returned.text == '0\n'
        program.alive

        and:
        alive(server) == outlives
        ApplicationPort.isOpen(port) == outlives

        where:
        what                                                                                | options      | outlives
        'the application run-app started in it has ended before the call returns'           | ''           | false
        'the application run-app --detach started in it goes on, as it does after grails'   | ' --detach'  | true
    }

    private void project() {
        new File(dir, 'grails-app').mkdirs()
        new File(dir, 'Server.java').text = LifecycleCommandsSpec.SERVER
        new File(dir, 'mode.txt').text = 'serve\n'
        File wrapper = new File(dir, 'gradlew')
        wrapper.text = "#!/bin/sh\n\"${System.getProperty('java.home')}/bin/java\" \"${dir.canonicalPath}/Server.java\" \"\$@\"\n"
        wrapper.setExecutable(true, false)
    }

    private static int freePort() {
        return new ServerSocket(0, 1, InetAddress.getLoopbackAddress()).withCloseable { ServerSocket socket -> socket.localPort }
    }

    private static boolean alive(long pid) {
        return ProcessHandle.of(pid).map { ProcessHandle handle -> handle.alive }.orElse(false)
    }

    /**
     * A program that runs the command line in its own JVM, without a command, as {@code GrailsCli.execute()}
     * does: the console in the project of its directory. It writes the exit code when the console has
     * returned, and goes on for a while, as an IDE does. Given the system property {@link #PORT}, it
     * first writes, to {@code at-return.txt}, the instant the console returned and whether something
     * took connections on that port then, as a program that starts the next application on it would find.
     */
    static class Host {

        static final String PORT = 'grails.spec.port'

        static void main(String[] args) {
            int exit = ForgeCli.launch('grails', true)
            String port = System.getProperty(PORT)
            if (port != null) {
                new File(System.getProperty('user.dir'), 'at-return.txt').text = "${System.currentTimeMillis()} ${ApplicationPort.isOpen(port as int)}\n"
            }
            new File(System.getProperty('user.dir'), 'returned.txt').text = "${exit}\n"
            Thread.sleep(TimeUnit.SECONDS.toMillis(SECONDS))
            System.exit(0)
        }
    }
}

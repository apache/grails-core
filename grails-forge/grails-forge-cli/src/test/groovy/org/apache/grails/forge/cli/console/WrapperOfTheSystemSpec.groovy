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
import java.util.function.Consumer
import groovy.json.JsonSlurper
import org.grails.forge.cli.CommandFixture
import org.grails.forge.cli.CommandSpec
import org.grails.forge.cli.ForgeCli
import org.grails.forge.cli.ForkedCli
import org.opentest4j.TestAbortedException
import spock.lang.Requires
import spock.lang.Unroll
import spock.util.concurrent.PollingConditions

/**
 * The commands that start the Gradle wrapper, with a wrapper of the system the specification runs on:
 * a shell script on Linux and macOS, and a batch file on Windows, which the command interpreter runs.
 * Either is two lines around a program of one file, which writes down the arguments it was given and,
 * for {@code bootRun}, starts a process of its own and answers on a port until it is ended. So what
 * {@code LifecycleCommandsSpec} runs with a shell script alone, and so on no Windows, is run here on
 * both for what differs between them: what reaches Gradle of an argument, an application that is
 * left running, and how it is ended.
 */
class WrapperOfTheSystemSpec extends CommandSpec implements CommandFixture {

    /** What the command interpreter of Windows reads as its own, and no batch file is given. */
    static final String REFUSED = '"%&|<>^!'

    /** Every other character of ASCII that is no letter and no digit, which a batch file is given as it is. */
    static final String GIVEN = '#$\'()*+,-./:;=?@[\\]_`{}~'

    static final String PROGRAM = '''\
        import java.io.InputStream;
        import java.io.IOException;
        import java.net.InetAddress;
        import java.net.ServerSocket;
        import java.net.Socket;
        import java.nio.charset.StandardCharsets;
        import java.nio.file.Files;
        import java.nio.file.Path;

        public class Fixture {

            public static void main(String[] args) throws Exception {
                Path home = Path.of("").toAbsolutePath();
                if (args.length == 1 && args[0].equals("below-the-fixture")) {
                    Thread.sleep(300000);
                    return;
                }
                StringBuilder invoked = new StringBuilder();
                String port = "0";
                boolean runs = false;
                for (String arg : args) {
                    invoked.append(arg).append('\\n');
                    if (arg.startsWith("-Dgrails.server.port=")) {
                        port = arg.substring(21);
                    }
                    runs = runs || arg.equals("bootRun") || arg.equals(":bootRun");
                }
                Files.write(home.resolve("invoked.txt"), invoked.toString().getBytes(StandardCharsets.UTF_8));
                System.out.println("the fixture ran in " + home);
                if (!runs) {
                    return;
                }
                String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
                Process below = new ProcessBuilder(java, home.resolve("Fixture.java").toString(), "below-the-fixture")
                        .redirectInput(ProcessBuilder.Redirect.DISCARD.file())
                        .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                        .redirectError(ProcessBuilder.Redirect.DISCARD)
                        .start();
                Files.writeString(home.resolve("below.pid"), below.pid() + "\\n");
                try (ServerSocket server = new ServerSocket(Integer.parseInt(port), 50, InetAddress.getLoopbackAddress())) {
                    Files.writeString(home.resolve("server.pid"), ProcessHandle.current().pid() + "\\n");
                    System.out.println("listening on " + port);
                    while (true) {
                        try (Socket socket = server.accept()) {
                            // every client of the specs sends its request at once, or closes
                            socket.setSoTimeout(30000);
                            InputStream request = socket.getInputStream();
                            int last = 0;
                            int next;
                            boolean asked = false;
                            while ((next = request.read()) != -1) {
                                asked = true;
                                if (next == '\\n' && last == '\\n') {
                                    break;
                                }
                                if (next != '\\r') {
                                    last = next;
                                }
                            }
                            if (asked) {
                                socket.getOutputStream().write("HTTP/1.0 200 OK\\r\\n\\r\\n".getBytes(StandardCharsets.ISO_8859_1));
                                socket.getOutputStream().flush();
                            }
                        } catch (IOException ignored) {
                        }
                    }
                }
            }
        }
        '''.stripIndent()

    static final boolean WINDOWS = File.separatorChar == ('\\' as char)

    PrintStream originalOut
    PrintStream originalErr
    ByteArrayOutputStream out = new ByteArrayOutputStream()
    List<Long> started = []

    void setup() {
        originalOut = System.out
        originalErr = System.err
        System.setOut(new PrintStream(out, true))
        System.setErr(new PrintStream(out, true))
        new File(dir, 'grails-app').mkdirs()
        new File(dir, 'settings.gradle').text = "rootProject.name = 'shop'\n"
        new File(dir, 'Fixture.java').text = PROGRAM
        String java = new File(System.getProperty('java.home'), 'bin/java').path
        File script = new File(dir, 'gradlew')
        script.text = "#!/bin/sh\n\"${java}\" \"${dir.canonicalPath}/Fixture.java\" \"\$@\"\n"
        script.setExecutable(true, false)
        // as the wrapper of Gradle gives Java what it was given: all of it, as it was typed
        new File(dir, 'gradlew.bat').text = "@echo off\r\n\"${java}.exe\" \"%~dp0Fixture.java\" %*\r\n"
    }

    void cleanup() {
        System.setOut(originalOut)
        System.setErr(originalErr)
        GradleRunner.setAsksToEnd(null)
        RunAppCommand.stopWhatTheShellStarted()
        ['server.pid', 'below.pid'].each { String name ->
            File file = new File(dir, name)
            if (file.isFile() && file.text.trim() ==~ /[0-9]+/) {
                started.add(file.text.trim() as long)
            }
        }
        List<Long> left = started.findAll { Long pid -> alive(pid) }
        left.each { Long pid ->
            ProcessHandle.of(pid).ifPresent { ProcessHandle handle ->
                handle.descendants().forEach { ProcessHandle each -> each.destroyForcibly() }
                handle.destroyForcibly()
            }
        }
        assert left.isEmpty(): "processes a feature started and did not end: ${left}"
    }

    void "every argument the wrapper of the system is given reaches what it starts as the argument it was"() {
        given: 'each character that is given, in an argument that the JVM leaves as it is and in one it puts between quotes'
        List<String> arguments = ['help', '-Dname=value', '-Pone=two words', '--tests', 'shop.BookSpec.a book (with a title), saved; again', 'C:\\dir\\', 'trailing\\']
        // where the wrapper is a script, the characters a batch file is not given are given too
        ((WINDOWS ? '' : REFUSED) + GIVEN).each { String character ->
            arguments.add("-Pbare${character}character".toString())
            arguments.add("-Pwith a space ${character} and the character".toString())
            arguments.add("${character}first".toString())
        }
        // an option of Gradle itself is none of the command, and a file of arguments is none of Java here
        arguments.removeAll { String argument -> argument in ['-first', '@first'] }

        when:
        int exit = ForgeCli.execute(beanContext, (['gradle'] + arguments) as String[])
        List<String> invoked = new File(dir, 'invoked.txt').isFile() ? new File(dir, 'invoked.txt').getText('UTF-8').readLines() : null

        then:
        assert exit == 0: said()
        assert invoked != null: said()
        assert invoked.takeRight(arguments.size()) == arguments: "${differences(arguments, invoked.takeRight(arguments.size()))}\n${said()}"
    }

    @Unroll
    void "the batch file of Windows is not given an argument with the character #name, and one with a space that ends with a backslash"() {
        given: 'what runs the wrapper of Windows, on whatever system this is'
        List<String> failures = []
        GradleRunner runner = new GradleRunner({ String message -> failures.add(message) } as Consumer<String>, true)
        ConsoleProject project = ConsoleProject.find(dir.canonicalFile.toPath())

        when:
        runner.run(project, ['help', argument], null)

        then: 'it is refused before anything is started'
        GradleRunner.RefusedArgumentException refused = thrown()
        refused.message.contains('has one of "%&|<>^! or a control character, or has a space and ends with a backslash. Run gradlew.bat with it yourself')
        !new File(dir, 'invoked.txt').exists()

        where:
        argument << REFUSED.collect { String character -> "-Pbare${character}character".toString() } +
                REFUSED.collect { String character -> "-Pwith a space ${character} and the character".toString() } +
                ['', 'two words\\', 'a\ttab\\', 'line\nfeed', 'carriage\rreturn', 'escape\u001b[0m', 'nothing\u0000at all']
        name = argument.isEmpty() ? 'none: it is empty' : argument.replaceAll(/\p{Cntrl}/, '?')
    }

    void "which characters a batch file is given, one by one: all of ASCII"() {
        expect: 'in an argument'
        (0..127).every { int code ->
            String character = (code as char) as String
            boolean refused = code < 32 || REFUSED.contains(character)
            assert GradleRunner.notForABatchFile("a${character}b".toString()) == refused: "the character ${code} in an argument"
            // a delete character is no control character for the command interpreter, and is given
            assert refused || code == 127 || code == 32 || GIVEN.contains(character) || Character.isLetterOrDigit(code): "the character ${code} is in neither list"
            true
        }

        and: 'in the path of the batch file where the JVM puts no quotes around it, which it does for white space alone'
        (33..126).every { int code ->
            String character = (code as char) as String
            assert GradleRunner.notAPathWithoutQuotes("C:\\work\\a${character}b\\gradlew.bat".toString()) == ',;=()'.contains(character): "the character ${code} in a path"
            assert !GradleRunner.notAPathWithoutQuotes("C:\\my work\\a${character}b\\gradlew.bat".toString()): "the character ${code} in a path with a space"
            true
        }
        !GradleRunner.notAPathWithoutQuotes('C:\\Program Files (x86)\\shop\\gradlew.bat')
        GradleRunner.notAPathWithoutQuotes('C:\\work(old)\\gradlew.bat')
    }

    @Requires({ os.windows })
    @Unroll
    void "on Windows the command refuses #argument, and the command interpreter is not started"() {
        when:
        int exit = ForgeCli.execute(beanContext, 'gradle', 'help', argument)

        then:
        assert exit == 2: said()
        // a mistake of the command line, and said as one: the line, how the command is used, and no error of a run
        assert out.toString().contains(' cannot be given to Gradle from here: the Gradle wrapper of Windows is a batch file'): said()
        assert out.toString().contains('Usage: ') && errors() == []: said()
        !new File(dir, 'invoked.txt').exists()

        where:
        argument << ['-Pa&b', '-Pa|b', '-Pa<b', '-Pa>b', '-Pa^b', '-Pa%PATH%b', '-Pa"b', '-Pa!b', 'two words\\']
    }

    void "a path that names a share by the name of its computer is told from one with a drive letter"() {
        expect:
        GradleRunner.isOfAShare('\\\\server\\share\\shop')
        GradleRunner.isOfAShare('\\\\localhost\\C$\\work\\shop\\gradlew.bat')
        GradleRunner.isOfAShare('//server/share/shop')
        !GradleRunner.isOfAShare('C:\\work\\shop')
        !GradleRunner.isOfAShare('Z:\\shop\\gradlew.bat')
        !GradleRunner.isOfAShare('\\work\\shop')
    }

    @Requires({ os.windows })
    void "on Windows a project on a share that is named by its computer is not built: the command interpreter would run Gradle elsewhere"() {
        given: 'the project through the share every drive has for the administrators of the computer'
        String path = dir.canonicalPath
        File shared = new File("\\\\localhost\\${path.substring(0, 1)}\$${path.substring(2)}")
        if (!(path ==~ /[A-Za-z]:\\.*/) || !new File(shared, 'gradlew.bat').isFile()) {
            throw new TestAbortedException("${shared} is not reached on this computer")
        }
        System.setProperty('user.dir', shared.path)

        when:
        int exit = ForgeCli.execute(beanContext, 'gradle', 'help')

        then:
        assert exit == 1: said()
        assert errors().size() == 1: said()
        assert errors()[0].contains('takes no path that begins with the name of a computer (a UNC path) for its current directory') &&
                errors()[0].contains('Gradle was not run; give the share a drive letter (net use) and run the command in the project on that drive'): said()
        !new File(dir, 'invoked.txt').exists()
    }

    void "run-app --detach leaves an application that outlives the command line, and stop-app ends it with every process below it"() {
        given:
        int port = freePort()
        File forked = new File(dir, 'forked.txt')

        when: 'a command line of its own starts the application, and ends'
        Process cli = ForkedCli.builder(dir, [], 'run-app', '--detach', '--port', port as String)
                .redirectErrorStream(true).redirectOutput(forked).redirectInput(ProcessBuilder.Redirect.from(new File(WINDOWS ? 'NUL' : '/dev/null'))).start()
        boolean ended = cli.waitFor(240, TimeUnit.SECONDS)
        if (!ended) {
            cli.destroyForcibly()
        }
        // Where the class path of a build is too long for a command line, as on Windows, Gradle gives the JVM of
        // the specs a jar that names it. Another JVM is started with the same; where that is not a class path
        // it finds the command line in, there is no command line of its own to start, and it is said here.
        if (ended && cli.exitValue() != 0 && forked.text.contains('Could not find or load main class')) {
            throw new TestAbortedException("the class path of this JVM is none another can be started with: ${System.getProperty('java.class.path').take(300)}")
        }

        then:
        assert ended && cli.exitValue() == 0: "the command line ${ended ? 'ended with ' + cli.exitValue() : 'did not end'}:\n${forked.text}\n${log()}"
        assert forked.text.contains("| Application answers on port ${port}: http://localhost:${port}. It runs until stop-app"): forked.text

        when:
        Map record = new JsonSlurper().parse(new File(dir, '.grails/run/app.json')) as Map
        started.add(record.pid as long)
        long server = pid('server.pid')
        long below = pid('below.pid')

        then: 'what it started is still there, and is what the record names'
        !cli.isAlive()
        assert alive(record.pid as long) && alive(server) && alive(below): "of the recorded ${record.pid}, the server ${server} and the process below it ${below}, alive are " +
                "${[record.pid as long, server, below].findAll { long each -> alive(each) }}\n${log()}"
        ApplicationPort.isOpen(port)
        record.detached == true && record.port == port
        ProcessHandle.of(record.pid as long).get().info().startInstant().get().toEpochMilli() == record.started
        log().contains("listening on ${port}")

        when:
        int stopped = ForgeCli.execute(beanContext, 'stop-app')

        then: 'the whole of it is gone, and what is said is what was done'
        assert stopped == 0: said()
        assert errors() == []: said()
        out.toString().readLines() == ['| Stopping application...', WINDOWS ? "| Application stopped. ${GradleRunner.ENDED_AT_ONCE}".toString() : '| Application stopped.']
        new PollingConditions(timeout: 60, delay: 0.1).eventually {
            assert !alive(record.pid as long) && !alive(server) && !alive(below)
        }
        !ApplicationPort.isOpen(port)
        !new File(dir, '.grails/run/app.json').exists()
    }

    void "where a process is ended at once and not asked to end, stop-app says that the application was not asked to shut down"() {
        given:
        int port = freePort()

        when:
        int exit = ForgeCli.execute(beanContext, 'run-app', '--detach', '--port', port as String)
        Map record = exit == 0 ? new JsonSlurper().parse(new File(dir, '.grails/run/app.json')) as Map : [:]
        if (record.pid != null) {
            started.add(record.pid as long)
        }

        then:
        assert exit == 0: said()

        when: 'as Windows is'
        out.reset()
        GradleRunner.setAsksToEnd(false)
        int stopped = ForgeCli.execute(beanContext, 'stop-app')

        then:
        assert stopped == 0: said()
        out.toString().readLines() == ['| Stopping application...',
                                       '| Application stopped. Its processes were ended at once: on Windows an application is not asked to shut down, ' +
                                               'so what it does when it is asked (its shutdown hooks, the graceful shutdown of its server) was not done.']
        new PollingConditions(timeout: 60, delay: 0.1).eventually {
            assert !alive(record.pid as long) && !alive(pid('server.pid')) && !alive(pid('below.pid'))
        }
    }

    void "what the system says of how a process is ended is what the JDK says of this process"() {
        expect:
        GradleRunner.asksToEnd() == ProcessHandle.current().supportsNormalTermination()
        GradleRunner.asksToEnd() == !WINDOWS
    }

    private static int freePort() {
        return new ServerSocket(0, 1, InetAddress.getLoopbackAddress()).withCloseable { ServerSocket socket -> socket.localPort }
    }

    private long pid(String name) {
        File file = new File(dir, name)
        new PollingConditions(timeout: 60, delay: 0.1).eventually {
            assert file.isFile() && file.text.endsWith('\n')
        }
        long pid = file.text.trim() as long
        started.add(pid)
        return pid
    }

    private static boolean alive(long pid) {
        return ProcessHandle.of(pid).map { ProcessHandle handle -> handle.alive }.orElse(false)
    }

    private List<String> errors() {
        return out.toString().readLines().findAll { String line -> line.contains('| Error') }
    }

    private String log() {
        File log = new File(dir, '.grails/run/app.log')
        return "--- .grails/run/app.log\n${log.isFile() ? log.text : 'is not there'}"
    }

    // what the command said and what the wrapper wrote down, for the message of an assertion
    private String said() {
        File invoked = new File(dir, 'invoked.txt')
        return "--- the command said\n${out}\n--- the wrapper was given\n${invoked.isFile() ? invoked.getText('UTF-8') : 'nothing: it did not run'}\n${log()}"
    }

    private static String differences(List<String> given, List<String> got) {
        List<String> lines = []
        for (int i = 0; i < Math.max(given.size(), got.size()); i++) {
            String one = i < given.size() ? given[i] : null
            String other = i < got.size() ? got[i] : null
            if (one != other) {
                lines.add("argument ${i}: given <${one}>, got <${other}>".toString())
            }
        }
        return lines.join('\n')
    }
}

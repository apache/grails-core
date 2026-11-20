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
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Predicate
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.grails.forge.application.ApplicationType
import org.grails.forge.cli.CommandFixture
import org.grails.forge.cli.CommandSpec
import org.grails.forge.cli.ForgeCli
import spock.lang.IgnoreIf
import spock.lang.Unroll
import spock.util.concurrent.PollingConditions

/**
 * The commands that run the application of the project and the sessions around it: {@code run-app},
 * {@code stop-app}, {@code console}, {@code shell}, {@code run-script} and {@code run-command}.
 *
 * <p>Most projects here have a Gradle wrapper that is a script of two lines around a program of one
 * file: it writes down the arguments it was given and, for {@code bootRun}, answers on a port of the
 * loopback address until it is ended, so that starting, waiting, recording and stopping are run as
 * they are, without Gradle and without the framework. Two features run the real wrapper around a
 * build that needs nothing of Grails.</p>
 */
@IgnoreIf({ os.windows })
class LifecycleCommandsSpec extends CommandSpec implements CommandFixture {

    static final String SERVER = '''\
        import java.io.IOException;
        import java.io.InputStream;
        import java.net.InetAddress;
        import java.net.ServerSocket;
        import java.net.Socket;
        import java.nio.charset.StandardCharsets;
        import java.nio.file.Files;
        import java.nio.file.Path;
        import java.nio.file.attribute.PosixFilePermissions;

        public class Server {

            public static void main(String[] args) throws Exception {
                Path home = Path.of("").toAbsolutePath();
                StringBuilder invoked = new StringBuilder();
                String port = Files.exists(home.resolve("port.txt")) ? Files.readString(home.resolve("port.txt")).trim() : "0";
                for (String arg : args) {
                    invoked.append('<').append(arg).append('>');
                    if (arg.startsWith("-Dgrails.server.port=")) {
                        port = arg.substring(21);
                    }
                    if (arg.startsWith("--args-file=")) {
                        Path file = home.resolve(arg.substring(12));
                        Files.writeString(home.resolve("args-file.txt"), Files.readString(file));
                        Files.writeString(home.resolve("args-file-permissions.txt"), PosixFilePermissions.toString(Files.getPosixFilePermissions(file)));
                    }
                }
                Files.writeString(home.resolve("invoked.txt"), invoked + "\\n");
                String mode = Files.exists(home.resolve("mode.txt")) ? Files.readString(home.resolve("mode.txt")).trim() : "serve";
                String task = args.length > 0 ? args[0] : "";
                System.out.println("the fixture ran " + task);
                if (mode.equals("fail")) {
                    System.out.println("boom on purpose");
                    System.exit(3);
                }
                if (!task.equals("bootRun") || mode.equals("record")) {
                    return;
                }
                if (mode.equals("child")) {
                    Process child = new ProcessBuilder("sleep", "300").start();
                    Files.writeString(home.resolve("child.pid"), child.pid() + "\\n");
                }
                if (mode.equals("silent")) {
                    Files.writeString(home.resolve("server.pid"), ProcessHandle.current().pid() + "\\n");
                    Thread.sleep(300000);
                    return;
                }
                try (ServerSocket server = new ServerSocket(Integer.parseInt(port), 50, InetAddress.getLoopbackAddress())) {
                    Files.writeString(home.resolve("server.pid"), ProcessHandle.current().pid() + "\\n");
                    System.out.println("listening on " + port);
                    while (true) {
                        try (Socket socket = server.accept()) {
                            socket.setSoTimeout(2000);
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
                                boolean starting = mode.equals("starting") && !Files.exists(home.resolve("ready"));
                                String answer = "HTTP/1.0 200 OK\\r\\n" + (starting ? "Grails-Startup-Phase: STARTING\\r\\n" : "") + "\\r\\n";
                                socket.getOutputStream().write(answer.getBytes(StandardCharsets.ISO_8859_1));
                                socket.getOutputStream().flush();
                            }
                        } catch (IOException ignored) {
                        }
                    }
                }
            }
        }
        '''.stripIndent()

    static final String BUILD = '''\
        import org.gradle.api.tasks.options.Option

        abstract class Arguments extends DefaultTask {

            @Internal @Option(option = 'args-file', description = 'The file of the arguments')
            String argsFile

            @TaskAction
            void run() {
                println 'args-file=' + new File(project.projectDir, argsFile).text
            }
        }

        tasks.register('runCommand', Arguments)
        tasks.register('bootRun') {
            def port = gradle.startParameter.systemPropertiesArgs['grails.server.port']
            def marker = new File(projectDir, 'server.pid')
            doLast {
                def server = new ServerSocket(port as int, 50, InetAddress.getLoopbackAddress())
                marker.text = ProcessHandle.current().pid() + '\\n'
                while (true) {
                    def socket = server.accept()
                    socket.outputStream.write('HTTP/1.0 200 OK\\r\\n\\r\\n'.bytes)
                    socket.close()
                }
            }
        }
        '''.stripIndent()

    PrintStream originalOut
    PrintStream originalErr
    ByteArrayOutputStream out = new ByteArrayOutputStream()
    List<Long> started = []

    void setup() {
        originalOut = System.out
        originalErr = System.err
        System.setOut(new PrintStream(out, true))
        System.setErr(new PrintStream(out, true))
    }

    void cleanup() {
        System.setOut(originalOut)
        System.setErr(originalErr)
        ConsoleProject.setOwnedByUser(null)
        ConsoleProject.setSafeDirectories(null)
        ConsoleCommands.setInteractive(false)
        RunAppCommand.stopWhatTheShellStarted()
        // whatever a feature started and did not see end is ended here, and the feature has then failed
        ['server.pid', 'child.pid'].each { String name -> noteStarted(new File(dir, name)) }
        List<Long> left = started.findAll { Long pid -> alive(pid) }
        left.each { Long pid ->
            ProcessHandle.of(pid).ifPresent { ProcessHandle handle ->
                handle.descendants().forEach { ProcessHandle each -> each.destroyForcibly() }
                handle.destroyForcibly()
            }
        }
        assert left.isEmpty()
    }

    @Unroll
    void "#line runs Gradle with #invoked"() {
        given:
        scriptedProject('record')
        new File(dir, 'scripts/two words').mkdirs()
        new File(dir, 'scripts/one.groovy').text = ''
        new File(dir, 'scripts/two words/it\'s "2".groovy').text = ''

        when:
        int exit = ForgeCli.execute(beanContext, line as String[])

        then:
        exit == 0
        new File(dir, 'invoked.txt').text == invoked + '\n'
        errors() == []

        where:
        line                                                                                       | invoked
        ['run-app']                                                                                | '<bootRun><--quiet><-Dgrails.env=development>'
        ['run']                                                                                    | '<bootRun><--quiet><-Dgrails.env=development>'
        ['run-app', '--port', '9090', '--host', '127.0.0.1', '--debug-jvm', '--verbose']           | '<bootRun><--debug-jvm><-Dgrails.env=development><-Dgrails.server.port=9090><-Dgrails.server.address=127.0.0.1>'
        ['run-app', '-port=9091', '-host=::1', '-debug-jvm', '-query-tail']                        | '<bootRun><--debug-jvm><--quiet><-Dgrails.env=development><-Dgrails.server.port=9091><-Dgrails.server.address=::1><-Pgrails.gorm.queryTail.enabled>'
        ['run-app', '-Dgrails.env=test', '-Da=b  "c"', '-Pone=two words', '-Pflag', '--query-tail'] | '<bootRun><--quiet><-Dgrails.env=test><-Da=b  "c"><-Pgrails.gorm.queryTail.enabled><-Pone=two words><-Pflag=>'
        ['run-app', '-x', '--', '--offline', '-Pz=$HOME *', '--port']                              | '<bootRun><--quiet><-Dgrails.env=development><--stacktrace><-Dgrails.full.stacktrace=true><--offline><-Pz=$HOME *><--port>'
        ['console']                                                                                | '<console><--quiet><-Dgrails.env=development>'
        ['console', '--debug-jvm', '-v', '-Dgrails.env=test', '--', '--offline']                   | '<console><--debug-jvm><-Dgrails.env=test><--offline>'
        ['shell']                                                                                  | '<shell><--quiet><-Dgrails.env=development>'
        ['sh', '-debug-jvm', '-Dother=1', '--stacktrace']                                          | '<shell><--debug-jvm><--quiet><-Dgrails.env=development><-Dother=1><--stacktrace><-Dgrails.full.stacktrace=true>'
        ['run-script', 'scripts/one.groovy']                                                       | '<runScript><-Pargs=scripts/one.groovy><-Dgrails.env=development>'
        ['run-script', 'scripts/one.groovy', 'scripts/two words/it\'s "2".groovy', '-Dgrails.env=test', '--', '-q'] | '<runScript><-Pargs=scripts/one.groovy "scripts/two words/it\'s "\'"\'"2"\'"\'".groovy"><-Dgrails.env=test><-q>'
        ['run-command', 'hello-world']                                                             | '<runCommand><--args-file=.grails/run/command-args-0.json><-Dgrails.env=development>'
        ['run-command', '-Dgrails.env=test', '-x', 'hello-world', '-x', '--', '-Dno=option']       | '<runCommand><--args-file=.grails/run/command-args-0.json><-Dgrails.env=test><--stacktrace><-Dgrails.full.stacktrace=true>'
    }

    void "the files run-script gives the task are split into the files that were given"() {
        expect: 'what the task does with the text: it splits at spaces outside quotes, and a quote of one kind ends at the next of that kind'
        split(given.collect { String file -> RunScriptCommand.quoted(file) }.join(' ')) == given

        where:
        given << [['a.groovy'], ['a b.groovy', 'c.groovy'], ['it\'s.groovy'], ['say "hi".groovy'], ['it\'s "both" \'and\' more.groovy', '"', '\'', '"\'"', 'tab\there'], ['  two  spaces  ']]
    }

    void "run-script runs nothing for a script that is not there, and needs one"() {
        given:
        scriptedProject('record')

        when:
        int exit = ForgeCli.execute(beanContext, 'run-script', 'scripts/missing.groovy')

        then:
        exit == 1
        errors().size() == 1
        errors()[0].contains('There is no script scripts/missing.groovy in ')
        errors()[0].endsWith('Gradle was not run')
        !new File(dir, 'invoked.txt').exists()

        when:
        out.reset()
        exit = ForgeCli.execute(beanContext, 'run-script')

        then:
        exit == 2
        !new File(dir, 'invoked.txt').exists()
    }

    void "run-command gives the command its arguments as they are, in a file only the user reads that is gone afterwards"() {
        given:
        scriptedProject('record')
        List<String> hostile = ['two  words', '"double" \'single\'', '--', '-Dnot=ours', '--help', '$HOME `id` *', 'line\nbreak', '', 'café 😀', '\\', '@|red x|@']

        when:
        int exit = ForgeCli.execute(beanContext, (['run-command', 'my-command'] + hostile) as String[])

        then:
        exit == 0
        errors() == []
        new JsonSlurper().parseText(new File(dir, 'args-file.txt').text) == ['my-command'] + hostile
        new File(dir, 'args-file-permissions.txt').text == 'rw-------'
        new File(dir, 'invoked.txt').text == '<runCommand><--args-file=.grails/run/command-args-0.json><-Dgrails.env=development>\n'

        and: 'the file is ASCII, and it is deleted'
        new File(dir, 'args-file.txt').text.every { String character -> character >= ' ' && character <= '~' }
        new File(dir, '.grails/run').list().toList() == ['.gitignore']
        new File(dir, '.grails/run/.gitignore').text == '*\n'
        PosixFilePermissions.toString(Files.getPosixFilePermissions(new File(dir, '.grails/run').toPath())) == 'rwx------'
    }

    void "run-command deletes the file of the arguments when the command fails too, and uses the next name while one is taken"() {
        given:
        scriptedProject('fail')
        new File(dir, '.grails/run').mkdirs()
        new File(dir, '.grails/run/command-args-0.json').text = '["of a command that runs"]'

        when:
        int exit = ForgeCli.execute(beanContext, 'run-command', 'my-command', 'one')

        then:
        exit == 1
        errors() == ['| Error Gradle did not succeed: it ended with exit code 3']
        new File(dir, 'invoked.txt').text.startsWith('<runCommand><--args-file=.grails/run/command-args-1.json>')
        new File(dir, 'args-file.txt').text == '["my-command","one"]'
        new File(dir, '.grails/run').list().toList().sort() == ['.gitignore', 'command-args-0.json']
        new File(dir, '.grails/run/command-args-0.json').text == '["of a command that runs"]'

        when: 'a file that a command left behind a day ago is replaced'
        new File(dir, '.grails/run/command-args-0.json').setLastModified(System.currentTimeMillis() - 2 * 24 * 60 * 60 * 1000L)
        new File(dir, 'mode.txt').text = 'record'
        out.reset()
        exit = ForgeCli.execute(beanContext, 'run-command', 'my-command', 'two')

        then:
        exit == 0
        new File(dir, 'invoked.txt').text.startsWith('<runCommand><--args-file=.grails/run/command-args-0.json>')
        new File(dir, '.grails/run').list().toList() == ['.gitignore']
    }

    @Unroll
    void "run-command refuses #what, and runs nothing"() {
        given:
        scriptedProject('record')

        when:
        int exit = ForgeCli.execute(beanContext, line as String[])

        then:
        exit == 2
        out.toString().contains(said)
        !out.toString().contains('secret')
        !new File(dir, 'invoked.txt').exists()
        !new File(dir, '.grails/run').exists() || new File(dir, '.grails/run').list().toList() == ['.gitignore']

        where:
        what                           | line                                               | said
        'no name'                      | ['run-command']                                    | 'Missing required parameter'
        'no name after its options'    | ['run-command', '-Dgrails.env=test']               | 'Missing required parameter'
        'an argument with U+0000'      | ['run-command', 'name', 'secret\u0000']            | 'The command cannot be given these arguments: argument 2 has the character U+0000'
        'half of a pair'               | ['run-command', 'name', 'ok', 'secret\ud83d']      | 'The command cannot be given these arguments: argument 3 has half of a character of two code units'
        'more arguments than the task' | ['run-command', 'name'] + ['secret'] * 4096        | 'The command cannot be given these arguments: these are 4097 arguments'
        'an option it does not have'   | ['run-command', '--bogus', 'name']                 | '--bogus'
    }

    void "run-app --https creates a certificate once, where the shell has it, and gives it to the application"() {
        given:
        scriptedProject('record')
        File keyStore = new File(dir, 'build/ssl/keystore')

        when:
        int exit = ForgeCli.execute(beanContext, 'run-app', '--https')

        then:
        exit == 0
        errors() == []
        keyStore.isFile()
        out.toString().contains('| Created a self-signed certificate for localhost in build/ssl/keystore')
        new File(dir, 'invoked.txt').text == "<bootRun><--quiet><-Dgrails.env=development><-Dgrails.server.port=8443><-Dgrails.server.ssl.key-store=${dir.canonicalPath}/build/ssl/keystore>" +
                '<-Dgrails.server.ssl.key-store-password=123456><-Dgrails.server.ssl.key-password=123456>\n'

        when: 'the one that is there is used, and a port that was given stays'
        byte[] created = keyStore.bytes
        out.reset()
        exit = ForgeCli.execute(beanContext, 'run-app', '-https', '--port', '9443')

        then:
        exit == 0
        keyStore.bytes == created
        !out.toString().contains('Created')
        new File(dir, 'invoked.txt').text.startsWith('<bootRun><--quiet><-Dgrails.env=development><-Dgrails.server.port=9443><-Dgrails.server.ssl.key-store=')

        when: 'a key store of the user is not replaced by one'
        keyStore.delete()
        exit = ForgeCli.execute(beanContext, 'run-app', '--https', '-Dgrails.server.ssl.key-store=/elsewhere/mine')

        then:
        exit == 0
        !keyStore.exists()
        new File(dir, 'invoked.txt').text == '<bootRun><--quiet><-Dgrails.env=development><-Dgrails.server.ssl.key-store=/elsewhere/mine><-Dgrails.server.port=8443>\n'
    }

    void "run-app --detach returns when the application answers, records what it started, and stop-app stops that and nothing else"() {
        given:
        scriptedProject('child')
        int port = freePort()

        when:
        int exit = ForgeCli.execute(beanContext, 'run-app', '--detach', '--port', port as String)
        long server = pid('server.pid')
        long child = pid('child.pid')
        Map record = record()

        then: 'it answers, and the command has returned'
        exit == 0
        errors() == []
        ApplicationPort.isOpen(port)
        out.toString().contains("| Application answers on port ${port}: http://localhost:${port}. It runs until stop-app")
        alive(server) && alive(child)

        and: 'the record is of the process that was started, and is the user\'s alone'
        record.format == 1 && record.port == port && record.detached == true
        record.command == ['bootRun', '--quiet', '-Dgrails.env=development', "-Dgrails.server.port=${port}".toString()]
        ProcessHandle.of(record.pid as long).get().info().startInstant().get().toEpochMilli() == record.started
        permissions('.grails/run') == 'rwx------'
        permissions('.grails/run/app.json') == 'rw-------'
        permissions('.grails/run/app.log') == 'rw-------'
        new File(dir, '.grails/run/.gitignore').text == '*\n'
        new File(dir, '.grails/run/app.log').text.contains("listening on ${port}")

        and: 'it is in a process group of its own, so the interrupt of the terminal of the CLI is not its'
        processGroup(record.pid as long) != processGroup(ProcessHandle.current().pid())

        when: 'a second one is asked for, either way'
        out.reset()
        new File(dir, 'invoked.txt').delete()
        int again = ForgeCli.execute(beanContext, 'run-app', '--detach', '--port', freePort() as String)
        int foreground = ForgeCli.execute(beanContext, 'run-app')

        then:
        again == 1 && foreground == 1
        errors() == ["| Error An application that run-app started is running for this project already: process ${record.pid}, for port ${port}. Run stop-app first. Nothing was started".toString()] * 2
        !new File(dir, 'invoked.txt').exists()
        alive(server)

        when:
        out.reset()
        int stopped = ForgeCli.execute(beanContext, 'stop-app')

        then: 'the application and what it started are gone, and so is the record'
        stopped == 0
        errors() == []
        out.toString().readLines() == ['| Stopping application...', '| Application stopped.']
        !alive(server) && !alive(child) && !alive(record.pid as long)
        !ApplicationPort.isOpen(port)
        !new File(dir, '.grails/run/app.json').exists()

        when: 'there is nothing to stop any more'
        out.reset()
        int twice = ForgeCli.execute(beanContext, 'stop')

        then:
        twice == 1
        errors() == ['| Error No application that run-app started is running for this project: there is no record of one, .grails/run/app.json. Nothing was stopped']
    }

    void "run-app stays with the application, and stop-app from elsewhere ends it as an end that was asked for"() {
        given:
        scriptedProject('serve')
        int port = freePort()
        AtomicInteger exit = new AtomicInteger(-1)
        Thread command = new Thread({ exit.set(ForgeCli.execute(beanContext, 'run-app', '--port', port as String)) })

        when:
        command.start()
        new PollingConditions(timeout: 60, delay: 0.2).eventually {
            assert ApplicationPort.isOpen(port) && new File(dir, '.grails/run/app.json').isFile()
        }
        long server = pid('server.pid')
        Map record = record()

        then:
        command.alive
        record.detached == false && record.port == port

        when:
        int stopped = ForgeCli.execute(beanContext, 'stop-app')
        command.join(60_000)

        then:
        stopped == 0
        !command.alive
        exit.get() == 0
        errors() == []
        out.toString().contains('| Application stopped')
        !alive(server)
        new File(dir, '.grails/run').list().toList() == ['.gitignore']
    }

    void "when run-app is cancelled the application is ended, and the record is gone"() {
        given:
        scriptedProject('serve')
        int port = freePort()
        AtomicInteger exit = new AtomicInteger(-1)
        Thread command = new Thread({ exit.set(ForgeCli.execute(beanContext, 'run-app', '--port', port as String)) })

        when:
        command.start()
        new PollingConditions(timeout: 60, delay: 0.2).eventually {
            assert ApplicationPort.isOpen(port)
        }
        long server = pid('server.pid')
        command.interrupt()
        command.join(60_000)

        then:
        !command.alive
        exit.get() == 1
        errors() == ['| Error Gradle was stopped before it finished, because the command was cancelled']
        !alive(server)
        !new File(dir, '.grails/run/app.json').exists()
    }

    void "run-app --detach says why when the build ends before the application answers, with the end of what it wrote"() {
        given:
        scriptedProject('fail')

        when:
        int exit = ForgeCli.execute(beanContext, 'run-app', '--detach', '--port', freePort() as String)

        then:
        exit == 1
        errors().size() == 1
        errors()[0].startsWith('| Error Gradle ended before the application answered on port ')
        out.toString().contains('The last lines of .grails/run/app.log:')
        out.toString().contains('  boom on purpose')
        !new File(dir, '.grails/run/app.json').exists()
    }

    void "run-app --detach stops what it started when the application does not answer in time"() {
        given:
        scriptedProject('silent')
        int port = freePort()

        when:
        int exit = ForgeCli.execute(beanContext, 'run-app', '--detach', '--port', port as String, '--ready-timeout', '5')
        long server = pid('server.pid')

        then:
        exit == 1
        errors() == ["| Error The application did not answer on port ${port} within 5 seconds, and was stopped. Give it longer with --ready-timeout".toString()]
        out.toString().contains('  the fixture ran bootRun')
        !alive(server)
        !new File(dir, '.grails/run/app.json').exists()
    }

    void "run-app --detach stops what it started when the command is cancelled while it waits"() {
        given:
        scriptedProject('silent')
        AtomicInteger exit = new AtomicInteger(-1)
        Thread command = new Thread({ exit.set(ForgeCli.execute(beanContext, 'run-app', '--detach', '--port', freePort() as String)) })

        when:
        command.start()
        new PollingConditions(timeout: 60, delay: 0.2).eventually {
            assert new File(dir, 'server.pid').isFile() && new File(dir, 'server.pid').text.endsWith('\n')
        }
        long server = pid('server.pid')
        command.interrupt()
        command.join(60_000)

        then:
        !command.alive
        exit.get() == 1
        errors() == ['| Error The application was stopped before it answered, because the command was cancelled']
        !alive(server)
        !new File(dir, '.grails/run/app.json').exists()
    }

    void "run-app --detach waits while the application says that it is starting"() {
        given:
        scriptedProject('starting')
        int port = freePort()
        AtomicInteger exit = new AtomicInteger(-1)
        Thread command = new Thread({ exit.set(ForgeCli.execute(beanContext, 'run-app', '--detach', '--port', port as String)) })

        when: 'the port answers, with the header of the page that is shown while the application starts'
        command.start()
        new PollingConditions(timeout: 60, delay: 0.2).eventually {
            assert ApplicationPort.isOpen(port)
        }
        Thread.sleep(2000)

        then:
        command.alive
        !ApplicationPort.isReady(port)

        when:
        new File(dir, 'ready').text = ''
        command.join(60_000)

        then:
        !command.alive
        exit.get() == 0

        cleanup:
        ForgeCli.execute(beanContext, 'stop-app')
    }

    void "run-app --detach starts nothing when something has the port, or when the application would not answer on the loopback address"() {
        given:
        scriptedProject('serve')
        ServerSocket taken = new ServerSocket(0, 50, InetAddress.getLoopbackAddress())

        when:
        int exit = ForgeCli.execute(beanContext, 'run-app', '--detach', '--port', taken.localPort as String)

        then:
        exit == 1
        errors().size() == 1
        errors()[0].startsWith("| Error Something answers on port ${taken.localPort} of this computer already")
        !new File(dir, 'invoked.txt').exists()
        !new File(dir, '.grails/run/app.json').exists()

        when:
        out.reset()
        exit = ForgeCli.execute(beanContext, 'run-app', '--detach', '--host', 'example.org')

        then:
        exit == 2
        out.toString().contains('where one that is bound to example.org does not answer: give --foreground to stay with the application instead')
        !new File(dir, 'invoked.txt').exists()

        when: 'the address is in the configuration of the application'
        new File(dir, 'grails-app/conf').mkdirs()
        new File(dir, 'grails-app/conf/application.yml').text = 'server:\n    address: 192.0.2.1\n'
        out.reset()
        exit = ForgeCli.execute(beanContext, 'run-app', '--detach')

        then:
        exit == 1
        errors().size() == 1
        errors()[0].startsWith('| Error The application is bound to 192.0.2.1 by its configuration or a system property')
        !new File(dir, 'invoked.txt').exists()

        cleanup:
        taken?.close()
    }

    @Unroll
    void "run-app waits on the port #where names"() {
        given:
        scriptedProject('serve')
        int port = freePort()
        new File(dir, 'port.txt').text = "${port}\n"
        new File(dir, 'grails-app/conf').mkdirs()
        new File(dir, 'grails-app/conf/application.yml').text = configuration.replace('PORT', port as String)

        when:
        int exit = ForgeCli.execute(beanContext, (['run-app', '--detach'] + options.collect { String option -> option.replace('PORT', port as String) }) as String[])

        then:
        exit == 0
        record().port == port

        cleanup:
        ForgeCli.execute(beanContext, 'stop-app')

        where:
        where                                    | configuration                                                                            | options
        'the configuration'                      | 'grails:\n    profile: web\nserver:\n    port: PORT\n'                                   | []
        'the configuration, in one key'          | 'server.port: PORT\n'                                                                    | []
        'the configuration of the environment'   | 'server:\n    port: 1\nenvironments:\n    test:\n        server:\n            port: PORT\n' | ['-Dgrails.env=test']
        'a later document of the configuration'  | 'server:\n    port: 1\n---\nserver:\n    port: PORT\n'                                   | []
        'the system property'                    | 'server:\n    port: 1\n'                                                                 | ['-Dgrails.server.port=PORT']
    }

    void "in the interactive shell run-app returns to the prompt, and the application ends with the shell"() {
        given:
        scriptedProject('serve')
        int port = freePort()
        ConsoleCommands.setInteractive(true)

        when:
        int exit = ForgeCli.execute(beanContext, 'run-app', '--port', port as String)
        long server = pid('server.pid')

        then:
        exit == 0
        record().detached == true
        out.toString().contains("| Application answers on port ${port}: http://localhost:${port}. It runs until stop-app, or until the shell ends")
        alive(server)

        when: 'what the shell does when it ends'
        RunAppCommand.stopWhatTheShellStarted()

        then:
        !alive(server)

        when: 'the record is of a process that has ended'
        out.reset()
        exit = ForgeCli.execute(beanContext, 'stop-app')

        then:
        exit == 1
        errors().size() == 1
        errors()[0].endsWith('which was started for it, has ended. Nothing was stopped, and the record was removed')
        !new File(dir, '.grails/run/app.json').exists()

        when: 'both ways at once'
        out.reset()
        exit = ForgeCli.execute(beanContext, 'run-app', '--detach', '--foreground')

        then:
        exit == 2
        out.toString().contains('--detach and --foreground are two answers to one question')
    }

    void "a record of a process that has ended is no application, and run-app starts one over it"() {
        given:
        scriptedProject('serve')
        Process ended = new ProcessBuilder('true').start()
        ended.waitFor()
        writeRecord(pid: ended.pid(), started: System.currentTimeMillis())
        int port = freePort()

        when:
        int exit = ForgeCli.execute(beanContext, 'run-app', '--detach', '--port', port as String)

        then:
        exit == 0
        record().pid != ended.pid()
        record().port == port

        cleanup:
        ForgeCli.execute(beanContext, 'stop-app')
    }

    @Unroll
    void "stop-app ends nothing for a record that #what"() {
        given: 'a process of the user that is not the application, and a record that was put into the project'
        scriptedProject('serve')
        Process other = new ProcessBuilder('sleep', '300').start()
        started.add(other.pid())
        ProcessHandle self = ProcessHandle.current()
        Map<String, Object> names = [
                OTHER      : other.pid(),
                OTHER_BEGAN: other.info().startInstant().get().toEpochMilli(),
                SELF       : self.pid(),
                SELF_BEGAN : self.info().startInstant().get().toEpochMilli(),
                PARENT     : self.parent().get().pid(),
                PARENT_BEGAN: self.parent().get().info().startInstant().map { it.toEpochMilli() }.orElse(1L),
                INIT_BEGAN : ProcessHandle.of(1L).flatMap { ProcessHandle init -> init.info().startInstant() }.map { it.toEpochMilli() }.orElse(1L)
        ]
        String text = content
        names.keySet().sort { String name -> -name.length() }.each { String name -> text = text.replace(name, names[name] as String) }
        new File(dir, '.grails/run').mkdirs()
        new File(dir, '.grails/run/app.json').text = text

        when:
        int exit = ForgeCli.execute(beanContext, 'stop-app')

        then:
        exit == 1
        errors().size() == 1
        errors()[0].contains(said)
        other.alive
        new File(dir, '.grails/run/app.json').exists() == kept
        !out.toString().contains('Stopping')

        cleanup:
        other?.destroyForcibly()
        other?.waitFor()

        where:
        what                                             | content                                                                                 | kept  | said
        'names the first process of the system'          | '{"format":1,"pid":1,"started":INIT_BEGAN,"port":0,"detached":true}'                    | false | 'it does not name a process, an instant and a port'
        'names the command itself'                       | '{"format":1,"pid":SELF,"started":SELF_BEGAN,"port":0,"detached":true}'                 | false | 'is this command or one that started it'
        'names the process that started the command'     | '{"format":1,"pid":PARENT,"started":PARENT_BEGAN,"port":0,"detached":true}'             | false | 'Nothing was stopped, and the record was removed'
        'names another process and not when it began'    | '{"format":1,"pid":OTHER,"started":1,"port":0,"detached":true}'                         | false | 'is not the one that was started for it: it did not begin when the record says'
        'names another process and when it began'        | '{"format":1,"pid":OTHER,"started":OTHER_BEGAN,"port":0,"detached":true}'               | true  | 'its command line is not that of the Gradle wrapper of this project running bootRun. End that process yourself if it is the application'
        'has a number that is none'                      | '{"format":1,"pid":"OTHER","started":OTHER_BEGAN,"port":0,"detached":true}'             | false | 'it does not name a process, an instant and a port'
        'has a number no process has'                    | '{"format":1,"pid":99999999999999999999999999,"started":1,"port":0,"detached":true}'    | false | 'it does not name a process, an instant and a port'
        'has a number larger than any process'           | '{"format":1,"pid":4294967296,"started":1,"port":0,"detached":true}'                    | false | 'it does not name a process, an instant and a port'
        'has a negative number'                          | '{"format":1,"pid":-OTHER,"started":OTHER_BEGAN,"port":0,"detached":true}'              | false | 'it does not name a process, an instant and a port'
        'has a number with a fraction'                   | '{"format":1,"pid":OTHER.0,"started":OTHER_BEGAN,"port":0,"detached":true}'             | false | 'it does not name a process, an instant and a port'
        'has a port that is none'                        | '{"format":1,"pid":OTHER,"started":OTHER_BEGAN,"port":65536,"detached":true}'           | false | 'it does not name a process, an instant and a port'
        'is of another format'                           | '{"format":2,"pid":OTHER,"started":OTHER_BEGAN,"port":0,"detached":true}'               | false | 'it is not of format 1, the one this command reads'
        'is a list'                                      | '[OTHER]'                                                                               | false | 'it is not a record of an application'
        'is not JSON'                                    | 'pid=OTHER\u0000\u001b[31m'                                                             | false | 'it is not a record of an application'
        'is empty'                                       | ''                                                                                      | false | 'it is not a record of an application'
        'is larger than a record'                        | '{"format":1,"pid":OTHER,"pad":"' + 'x' * 70000 + '"}'                                  | false | 'it is larger than 65536 bytes'
    }

    void "a process is not stopped for a record that names it when its command line only looks like the project's"() {
        given: 'a process of the user whose command line has the directory and the task, but that the record does not date'
        scriptedProject('serve')
        Process other = new ProcessBuilder('sh', '-c', 'sleep 300', "${dir.canonicalPath}/gradlew".toString(), 'bootRun').start()
        started.add(other.pid())
        writeRecord(pid: other.pid(), started: other.info().startInstant().get().toEpochMilli() + 1)

        when:
        int exit = ForgeCli.execute(beanContext, 'stop-app')

        then:
        exit == 1
        other.alive
        errors().size() == 1
        errors()[0].contains('it did not begin when the record says')

        cleanup:
        other?.destroyForcibly()
        other?.waitFor()
    }

    @Unroll
    void "nothing is read, written or stopped through #what that is a link"() {
        given: 'a place outside the project with a record of a process of the user'
        scriptedProject('serve')
        File outside = Files.createTempDirectory('grailsforgeoutside').toFile()
        Process other = new ProcessBuilder('sleep', '300').start()
        started.add(other.pid())
        String record = JsonOutput.toJson([format: 1, pid: other.pid(), started: other.info().startInstant().get().toEpochMilli(), port: 0, detached: true])
        new File(outside, 'run').mkdirs()
        new File(outside, 'run/app.json').text = record
        new File(outside, 'app.json').text = record
        new File(dir, '.grails').mkdirs()
        if (link == '.grails/run/app.json') {
            new File(dir, '.grails/run').mkdirs()
        }
        if (link == '.grails') {
            new File(dir, '.grails').delete()
        }
        Files.createSymbolicLink(new File(dir, link).toPath(), new File(outside, target).toPath())

        when:
        int stop = ForgeCli.execute(beanContext, 'stop-app')
        List<String> stopSaid = errors()
        out.reset()
        int run = ForgeCli.execute(beanContext, 'run-app', '--detach', '--port', freePort() as String, '--ready-timeout', '30')
        List<String> runSaid = errors()
        int command = ForgeCli.execute(beanContext, 'run-command', 'name')

        then:
        stop == 1
        stopSaid.size() == 1
        stopSaid[0].contains(said)
        other.alive

        and: 'what is outside is as it was'
        new File(outside, 'run/app.json').text == record
        new File(outside, 'app.json').text == record
        new File(outside, 'run').list().toList() == ['app.json']
        outside.list().toList().sort() == ['app.json', 'run']

        and: 'run-app and run-command, which would write there, do not either'
        run == runExit
        runExit == 0 || (runSaid.size() == 1 && runSaid[0].contains(said))
        command == runExit

        cleanup:
        ForgeCli.execute(beanContext, 'stop-app')
        other?.destroyForcibly()
        other?.waitFor()
        outside?.deleteDir()

        where:
        what                    | link                   | target         | runExit | said
        '.grails/run'           | '.grails/run'          | 'run'          | 1       | '.grails/run is a link, and nothing is read or written through it'
        '.grails'               | '.grails'              | '.'            | 1       | '.grails is a link, and nothing is read or written through it'
        'the record'            | '.grails/run/app.json' | 'app.json'     | 0       | '.grails/run/app.json cannot be used, it is not a file. Nothing was stopped, and the record was removed'
    }

    void "the output of an application that is left running is not written through a link"() {
        given:
        scriptedProject('serve')
        File outside = Files.createTempFile('grailsforgeoutside', '.log').toFile()
        outside.text = 'as it was\n'
        new File(dir, '.grails/run').mkdirs()
        Files.createSymbolicLink(new File(dir, '.grails/run/app.log').toPath(), outside.toPath())

        when:
        int exit = ForgeCli.execute(beanContext, 'run-app', '--detach', '--port', freePort() as String)

        then:
        exit == 0
        outside.text == 'as it was\n'
        !Files.isSymbolicLink(new File(dir, '.grails/run/app.log').toPath())
        new File(dir, '.grails/run/app.log').text.contains('listening on')

        cleanup:
        ForgeCli.execute(beanContext, 'stop-app')
        outside?.delete()
    }

    @IgnoreIf({ os.windows })
    void "in a project above that the environment names as one to use, the commands work on what another user owns there"() {
        given: 'a project in which nothing is this user\'s, as for root in a container over a directory of the host'
        scriptedProject('record')
        File below = new File(dir, 'below')
        below.mkdirs()
        System.setProperty('user.dir', below.absolutePath)
        ConsoleProject.setSafeDirectories('')
        ConsoleProject.setOwnedByUser({ Path path -> false } as Predicate<Path>)

        when: 'nothing names the directory'
        int exit = ForgeCli.execute(beanContext, 'run-command', 'my-command', 'one')

        then:
        exit == 1
        errors().size() == 1
        errors()[0].contains(', is not used: ')
        errors()[0].contains("name ${dir.canonicalPath} in the environment variable GRAILS_SAFE_DIRECTORY")
        !new File(dir, 'invoked.txt').exists()

        when: 'the environment names it'
        out.reset()
        ConsoleProject.setSafeDirectories(dir.canonicalPath)
        exit = ForgeCli.execute(beanContext, 'run-command', 'my-command', 'one')

        then: 'its wrapper is run, and the directory the commands keep their files in is used'
        exit == 0
        errors() == []
        new File(dir, 'invoked.txt').text.startsWith('<runCommand><--args-file=.grails/run/command-args-0.json>')

        when: 'a record that is there is read, and held to what a record is and not to whose it is'
        out.reset()
        new File(dir, '.grails/run/app.json').text = '[1]'
        exit = ForgeCli.execute(beanContext, 'stop-app')

        then:
        exit == 1
        errors().size() == 1
        errors()[0].contains('it is not a record of an application')
        !errors()[0].contains('not owned')
    }

    @Unroll
    void "#command does nothing outside a project, and nothing in a project above that is another user's"() {
        when:
        int exit = ForgeCli.execute(beanContext, line as String[])

        then:
        exit == 1
        errors().size() == 1
        errors()[0].startsWith('| Error There is no Grails project here: neither ')
        errors()[0].endsWith(notDone)

        when:
        scriptedProject('record')
        new File(dir, 'scripts').mkdirs()
        new File(dir, 'scripts/one.groovy').text = ''
        File below = new File(dir, 'below')
        below.mkdirs()
        System.setProperty('user.dir', below.absolutePath)
        ConsoleProject.setOwnedByUser({ Path path -> path != dir.canonicalFile.toPath() } as Predicate<Path>)
        out.reset()
        exit = ForgeCli.execute(beanContext, line as String[])

        then:
        exit == 1
        errors().size() == 1
        errors()[0].contains(', is not used: ')
        errors()[0].endsWith(notDone)
        !new File(dir, 'invoked.txt').exists()
        !new File(dir, '.grails').exists()

        where:
        line                                  | notDone
        ['run-app']                           | 'Gradle was not run'
        ['run-app', '--detach']               | 'Gradle was not run'
        ['stop-app']                          | 'Nothing was stopped'
        ['console']                           | 'Gradle was not run'
        ['shell']                             | 'Gradle was not run'
        ['run-script', 'scripts/one.groovy']  | 'Gradle was not run'
        ['run-command', 'name', 'argument']   | 'Gradle was not run'
        command = line[0..<(line.size() > 1 && line[1].startsWith('--') ? 2 : 1)].join(' ')
    }

    @Unroll
    void "#line is a mistake in the use of the command"() {
        given:
        scriptedProject('record')

        when:
        int exit = ForgeCli.execute(beanContext, line as String[])

        then:
        exit == 2
        out.toString().contains(said)
        !new File(dir, 'invoked.txt').exists()

        where:
        line                                     | said
        ['run-app', '--port', '0']               | '--port is a port, a number from 1 to 65535: 0 is none'
        ['run-app', '--port', '65536']           | '--port is a port, a number from 1 to 65535: 65536 is none'
        ['run-app', '--port', 'http']            | "Invalid value for option '--port'"
        ['run-app', '--ready-timeout', '0']      | '--ready-timeout is a number of seconds from 1 to 86400: 0 is none'
        ['run-app', '--host', '']                | '--host is an address the application binds to, and none was given'
        ['run-app', 'extra']                     | 'extra'
        ['run-app', '-D', 'x']                   | '-D is followed by the name of a system property and its value'
        ['stop-app', '--port', '8080']           | '--port'
        ['console', 'extra']                     | 'extra'
        ['shell', '--bogus']                     | '--bogus'
    }

    void "the commands are listed, described for a program, and found by their short forms"() {
        given:
        scriptedProject('record')

        when:
        int exit = ForgeCli.execute(beanContext, 'help')
        List<String> listed = out.toString().readLines()

        then:
        exit == 0
        listed.any { String line -> line =~ /^  run-app, run +Runs the Grails application: the Gradle task bootRun\./ }
        listed.any { String line -> line =~ /^  stop-app, stop +Stops the Grails application that run-app started for this project\./ }
        listed.any { String line -> line =~ /^  console +Runs the Swing console of the Grails application: the Gradle task console\./ }
        listed.any { String line -> line =~ /^  shell, sh +Runs the Groovy shell of the Grails application: the Gradle task shell\./ }
        listed.any { String line -> line =~ /^  run-script +Runs Groovy scripts in the Grails application: the Gradle task runScript\.$/ }
        listed.any { String line -> line =~ /^  run-command +Runs a command of the Grails application: the Gradle task runCommand\.$/ }

        when:
        out.reset()
        exit = ForgeCli.execute(beanContext, 'help', '--json')
        Map document = new JsonSlurper().parseText(out.toString()) as Map
        Map<String, Map> commands = (document.commands as List<Map>).collectEntries { Map each -> [(each.name): each] }

        then:
        exit == 0
        ['run-app', 'stop-app', 'console', 'shell', 'run-script', 'run-command'].every { String name -> commands[name].project == true && commands[name].origin == 'built-in' }
        commands['run-app'].aliases == ['run']
        commands['stop-app'].aliases == ['stop']
        commands['shell'].aliases == ['sh']
        (commands['run-app'].options as List<Map>).collectMany { Map option -> option.names as List<String> }.containsAll(
                ['--port', '--host', '--https', '--debug-jvm', '--query-tail', '-P', '-D', '--detach', '--foreground', '--ready-timeout'])
        (commands['run-command'].parameters as List<Map>)*.label == ['NAME', 'ARG']
        (commands['run-script'].parameters as List<Map>)*.label == ['FILE']

        when: 'the short forms of the shell'
        Map<String, Integer> exits = ['rA', 'sA', 'rS', 'rC', 'con', 'she'].collectEntries { String form ->
            out.reset()
            [(form): ForgeCli.execute(beanContext, form, '--help')]
        }
        out.reset()
        ForgeCli.execute(beanContext, 'rA', '--help')
        String runApp = out.toString()
        out.reset()
        ForgeCli.execute(beanContext, 'sA', '--help')
        String stopApp = out.toString()

        then:
        exits.values().every { int each -> each == 0 }
        runApp.contains(' run-app [-hvVx]')
        stopApp.contains(' stop-app [-hvVx]')

        when: 'the short forms that fit one of these commands and another one'
        Map<String, String> ambiguous = ['s', 'st', 'co', 'c'].collectEntries { String form ->
            out.reset()
            int code = ForgeCli.execute(beanContext, form, '--help')
            [(form): "${code} ${out.toString().readLines()[0]}".toString()]
        }

        then: 'before these commands s and st were stats, and co was compile'
        ambiguous == [
                s : '2 s is short for more than one command: stats, stop-app, shell. Type more of the name of the one that is meant',
                st: '2 st is short for more than one command: stats, stop-app. Type more of the name of the one that is meant',
                co: '2 co is short for more than one command: compile, console. Type more of the name of the one that is meant',
                c : '2 c is short for more than one command: compile, clean, console. Type more of the name of the one that is meant'
        ]
    }

    void "run-app and stop-app work with the Gradle wrapper of a project, and run-command gives Gradle the file of the arguments"() {
        given:
        plainProject()
        int port = freePort()

        when:
        int exit = ForgeCli.execute(beanContext, 'run-app', '--detach', '--port', port as String, '--ready-timeout', '600')
        long building = pid('server.pid')
        Map record = record()

        then:
        exit == 0
        errors() == []
        ApplicationPort.isOpen(port)
        alive(building)

        when: 'the record is believed: the process is the Gradle wrapper of this project running bootRun'
        out.reset()
        int stopped = ForgeCli.execute(beanContext, 'stop-app')

        then:
        stopped == 0
        errors() == []
        !alive(building) && !alive(record.pid as long)
        !ApplicationPort.isOpen(port)

        when:
        out.reset()
        exit = ForgeCli.execute(beanContext, 'run-command', 'hello-world', 'two  words', '--', '"quoted"')

        then:
        exit == 0
        out.toString().contains('args-file=["hello-world","two  words","--","\\"quoted\\""]')
    }

    private void scriptedProject(String mode) {
        new File(dir, 'grails-app').mkdirs()
        new File(dir, 'Server.java').text = SERVER
        new File(dir, 'mode.txt').text = mode + '\n'
        File wrapper = new File(dir, 'gradlew')
        wrapper.text = "#!/bin/sh\nexec \"${System.getProperty('java.home')}/bin/java\" \"${dir.canonicalPath}/Server.java\" \"\$@\"\n"
        wrapper.setExecutable(true, false)
    }

    // the wrapper of a generated application, and around it a build that needs nothing of Grails
    private void plainProject() {
        generateProject(ApplicationType.WEB)
        dir.eachFile { File file ->
            if (!(file.name in ['gradlew', 'gradlew.bat', 'gradle'])) {
                file.directory ? file.deleteDir() : file.delete()
            }
        }
        new File(dir, 'gradle').eachFile { File file ->
            if (file.name != 'wrapper') {
                file.directory ? file.deleteDir() : file.delete()
            }
        }
        new File(dir, 'settings.gradle').text = "rootProject.name = 'plain'\n"
        new File(dir, 'gradle.properties').text = 'org.gradle.daemon=false\n'
        new File(dir, 'build.gradle').text = BUILD
        new File(dir, 'grails-app').mkdirs()
    }

    private static int freePort() {
        return new ServerSocket(0, 1, InetAddress.getLoopbackAddress()).withCloseable { ServerSocket socket -> socket.localPort }
    }

    private long pid(String name) {
        File file = new File(dir, name)
        new PollingConditions(timeout: 60, delay: 0.1).eventually {
            assert file.isFile() && file.text.endsWith('\n')
        }
        noteStarted(file)
        return file.text.trim() as long
    }

    private void noteStarted(File file) {
        if (file.isFile() && file.text.trim() ==~ /[0-9]+/) {
            started.add(file.text.trim() as long)
        }
    }

    private static boolean alive(long pid) {
        return ProcessHandle.of(pid).map { ProcessHandle handle -> handle.alive }.orElse(false)
    }

    private Map record() {
        Map record = new JsonSlurper().parse(new File(dir, '.grails/run/app.json')) as Map
        started.add(record.pid as long)
        return record
    }

    private void writeRecord(Map values) {
        new File(dir, '.grails/run').mkdirs()
        new File(dir, '.grails/run/app.json').text = JsonOutput.toJson([format: 1, port: 0, detached: true] + values)
    }

    private String permissions(String path) {
        return PosixFilePermissions.toString(Files.getPosixFilePermissions(new File(dir, path).toPath()))
    }

    private static String processGroup(long pid) {
        Process ps = new ProcessBuilder('ps', '-o', 'pgid=', '-p', pid as String).redirectErrorStream(true).start()
        String group = ps.inputStream.text.trim()
        ps.waitFor()
        assert group ==~ /[0-9]+/
        return group
    }

    // the splitting of the task that runs scripts, CommandLineParser.translateCommandline of the Gradle plugin
    private static List<String> split(String text) {
        List<String> result = []
        StringBuilder current = new StringBuilder()
        String quote = null
        boolean quoted = false
        new StringTokenizer(text, '"\' ', true).each { Object each ->
            String token = each as String
            if (quote != null) {
                if (token == quote) {
                    quoted = true
                    quote = null
                } else {
                    current.append(token)
                }
                return
            }
            if (token == '\'' || token == '"') {
                quote = token
            } else if (token == ' ') {
                if (quoted || current.length() != 0) {
                    result.add(current.toString())
                    current.setLength(0)
                }
            } else {
                current.append(token)
            }
            quoted = false
        }
        if (quoted || current.length() != 0) {
            result.add(current.toString())
        }
        assert quote == null
        return result
    }

    private List<String> errors() {
        return out.toString().readLines().findAll { String line -> line.contains('| Error') }
    }
}

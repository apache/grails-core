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
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Predicate
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.grails.forge.application.ApplicationType
import org.grails.forge.cli.CommandFixture
import org.grails.forge.cli.CommandSpec
import org.grails.forge.cli.ForgeCli
import org.grails.forge.cli.ForkedCli
import org.opentest4j.TestAbortedException
import spock.lang.IgnoreIf
import spock.lang.Requires
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

    // what follows the refusal of a Groovy script as an argument of run-app, which grails run FILE.groovy ran in the Grails shell
    static final String SCRIPT_ADVICE = 'A Groovy script is run in a Grails project with run-script, with the application started around it. Type:'

    static final String SERVER = '''\
        import java.io.FileInputStream;
        import java.io.IOException;
        import java.io.InputStream;
        import java.net.InetAddress;
        import java.net.ServerSocket;
        import java.net.Socket;
        import java.nio.charset.StandardCharsets;
        import java.nio.file.Files;
        import java.nio.file.Path;
        import java.nio.file.attribute.PosixFilePermissions;
        import java.security.KeyStore;
        import javax.net.ssl.KeyManagerFactory;
        import javax.net.ssl.SSLContext;

        public class Server {

            // bound where the application is told, read as Spring Boot reads server.address; the loopback address otherwise
            static ServerSocket listen(String keyStore, int port, String address) throws Exception {
                InetAddress bound = address == null ? InetAddress.getLoopbackAddress() : InetAddress.getByName(address);
                if (keyStore == null) {
                    return new ServerSocket(port, 50, bound);
                }
                KeyStore keys = KeyStore.getInstance(KeyStore.getDefaultType());
                try (FileInputStream file = new FileInputStream(keyStore)) {
                    keys.load(file, "123456".toCharArray());
                }
                KeyManagerFactory factory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
                factory.init(keys, "123456".toCharArray());
                SSLContext context = SSLContext.getInstance("TLS");
                context.init(factory.getKeyManagers(), null, null);
                return context.getServerSocketFactory().createServerSocket(port, 50, bound);
            }

            public static void main(String[] args) throws Exception {
                String keyStore = null;
                String address = null;
                Path home = Path.of("").toAbsolutePath();
                StringBuilder invoked = new StringBuilder();
                String port = Files.exists(home.resolve("port.txt")) ? Files.readString(home.resolve("port.txt")).trim() : "0";
                for (String arg : args) {
                    invoked.append('<').append(arg).append('>');
                    if (arg.startsWith("-Dgrails.server.port=")) {
                        port = arg.substring(21);
                    }
                    if (arg.startsWith("-Dgrails.server.ssl.key-store=")) {
                        keyStore = arg.substring(30);
                    }
                    if (arg.startsWith("-Dgrails.server.address=")) {
                        address = arg.substring(24);
                    }
                    if (arg.startsWith("--args-file=")) {
                        Path file = home.resolve(arg.substring(12));
                        Files.writeString(home.resolve("args-file.txt"), Files.readString(file));
                        Files.writeString(home.resolve("args-file-permissions.txt"), PosixFilePermissions.toString(Files.getPosixFilePermissions(file)));
                    }
                }
                Files.writeString(home.resolve("invoked.txt"), invoked + "\\n");
                String told = System.getenv("GRAILS_NON_INTERACTIVE");
                Files.writeString(home.resolve("told.txt"), (told == null ? "<unset>" : told) + "\\n");
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
                try (ServerSocket server = listen(keyStore, Integer.parseInt(port), address)) {
                    Files.writeString(home.resolve("server.pid"), ProcessHandle.current().pid() + "\\n");
                    System.out.println("listening on " + port);
                    if (Files.exists(home.resolve("shutdown-millis.txt"))) {
                        // an application that takes a while to shut down when it is asked to, and closes its port last
                        long millis = Long.parseLong(Files.readString(home.resolve("shutdown-millis.txt")).trim());
                        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                            try {
                                Thread.sleep(millis);
                                Files.writeString(home.resolve("shut-down.txt"), System.currentTimeMillis() + "\\n");
                                server.close();
                            } catch (Exception ignored) {
                            }
                        }));
                    }
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
                            if (asked && !mode.equals("mute")) {
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
                    socket.soTimeout = 30000
                    try {
                        def request = new BufferedReader(new InputStreamReader(socket.inputStream, 'ISO-8859-1'))
                        def asked = request.readLine()
                        while (asked != null && request.readLine()) { }
                        if (asked != null) {
                            socket.outputStream.write('HTTP/1.0 200 OK\\r\\n\\r\\n'.bytes)
                            socket.outputStream.flush()
                        }
                    } catch (IOException ignored) {
                    }
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
        RunAppCommand.setEnvironmentVariables(null)
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
        ['run-app', '--port', '9090', '--host', '127.0.0.1', '--debug-jvm', '--verbose']           | '<bootRun><--debug-jvm><-Dgrails.env=development><-Dgrails.server.port=9090><-Dgrails.server.address=127.0.0.1>'
        ['run-app', '-port=9091', '-host=::1', '-debug-jvm', '-query-tail']                        | '<bootRun><--debug-jvm><--quiet><-Dgrails.env=development><-Dgrails.server.port=9091><-Dgrails.server.address=::1><-Pgrails.gorm.queryTail.enabled>'
        ['run-app', '-Dgrails.env=test', '-Da=b  "c"', '-Pone=two words', '-Pflag', '--query-tail'] | '<bootRun><--quiet><-Dgrails.env=test><-Da=b  "c"><-Pgrails.gorm.queryTail.enabled><-Pone=two words><-Pflag=>'
        ['run-app', '-x', '--', '--offline', '-Pz=$HOME *', '--port']                              | '<bootRun><--quiet><-Dgrails.env=development><--stacktrace><-Dgrails.full.stacktrace=true><--offline><-Pz=$HOME *><--port>'
        ['run']                                                                                    | '<bootRun><--quiet><-Dgrails.env=development>'
        ['run', '-port=9092', '-Dgrails.env=test', '--', '--offline']                              | '<bootRun><--quiet><-Dgrails.env=test><-Dgrails.server.port=9092><--offline>'
        ['console']                                                                                | '<console><--quiet><-Dgrails.env=development>'
        ['console', '--debug-jvm', '-v', '-Dgrails.env=test', '--', '--offline']                   | '<console><--debug-jvm><-Dgrails.env=test><--offline>'
        ['shell']                                                                                  | '<shell><--quiet><-Dgrails.env=development>'
        ['shell', '-debug-jvm', '-Dother=1', '--stacktrace']                                        | '<shell><--debug-jvm><--quiet><-Dgrails.env=development><-Dother=1><--stacktrace><-Dgrails.full.stacktrace=true>'
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

        and: 'the file is UTF-8, with what is not ASCII as it is, and it is deleted with the file that names its process'
        new File(dir, 'args-file.txt').getText('UTF-8').contains('caf\u00e9 \ud83d\ude00')
        new File(dir, 'args-file.txt').getText('UTF-8').contains('"line\\u000abreak"')
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
        permissions('build/ssl/keystore') == 'rw-------'
        permissions('build/ssl') == 'rwx------'
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

    @Unroll
    void "run-app --https #what"() {
        given: 'the passwords of a key store in the configuration of the application'
        scriptedProject('record')
        new File(dir, 'grails-app/conf').mkdirs()
        new File(dir, 'grails-app/conf/application.yml').text = 'server:\n    ssl:\n        key-store-password: secret\n        key-password: secret\n'
        String generated = "${dir.canonicalPath}/build/ssl/keystore"

        when:
        int exit = ForgeCli.execute(beanContext, (['run-app', '--https'] + options) as String[])

        then:
        exit == 0
        errors() == []
        new File(generated).isFile() == created
        out.toString().contains('| Created a self-signed certificate') == created
        new File(dir, 'invoked.txt').text == '<bootRun><--quiet><-Dgrails.env=development>' + invoked.replace('GENERATED', generated) + '\n'

        where:
        what                                                                                           | options                                                                    | created | invoked
        'creates a certificate and gives the application its passwords when nothing names a key store' | []                                                                         | true    | '<-Dgrails.server.port=8443><-Dgrails.server.ssl.key-store=GENERATED><-Dgrails.server.ssl.key-store-password=123456><-Dgrails.server.ssl.key-password=123456>'
        'gives a key store of the user no password when it is given as an option'                      | ['-Dgrails.server.ssl.key-store=/elsewhere/mine']                          | false   | '<-Dgrails.server.ssl.key-store=/elsewhere/mine><-Dgrails.server.port=8443>'
        'gives a key store of the user no password when it is given to Gradle after --'                | ['--', '-Dgrails.server.ssl.key-store=/elsewhere/mine']                    | false   | '<-Dgrails.server.port=8443><-Dgrails.server.ssl.key-store=/elsewhere/mine>'
        'gives a key store of the user no password when it is given to Gradle after -- as -D NAME=VALUE' | ['--', '-D', 'grails.server.ssl.key-store=/elsewhere/mine']              | false   | '<-Dgrails.server.port=8443><-D><grails.server.ssl.key-store=/elsewhere/mine>'
        'gives a key store of the user no password when it is given to Gradle after -- as --system-prop' | ['--', '--system-prop=grails.server.ssl.key-store=/elsewhere/mine']      | false   | '<-Dgrails.server.port=8443><--system-prop=grails.server.ssl.key-store=/elsewhere/mine>'
        'gives a key store of the user no password when it is given before and after --'              | ['-Dgrails.server.ssl.key-store=/elsewhere/mine', '--', '-Dgrails.server.ssl.key-store=/elsewhere/other'] | false | '<-Dgrails.server.ssl.key-store=/elsewhere/mine><-Dgrails.server.port=8443><-Dgrails.server.ssl.key-store=/elsewhere/other>'
    }

    void "run-app --detach returns when the application answers, records what it started, and stop-app stops that and nothing else"() {
        given:
        scriptedProject('child')
        int port = freePort()

        when:
        int exit = ForgeCli.execute(beanContext, 'run-app', '--detach', '--port', port as String, '-Dpassword=hunter2')
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
        record.keySet() == ['format', 'pid', 'started', 'port', 'detached'] as Set
        !new File(dir, '.grails/run/app.json').text.contains('hunter2')
        ProcessHandle.of(record.pid as long).get().info().startInstant().get().toEpochMilli() == record.started
        permissions('.grails/run') == 'rwx------'
        permissions('.grails/run/app.json') == 'rw-------'
        permissions('.grails/run/app.log') == 'rw-------'
        new File(dir, '.grails/run/.gitignore').text == '*\n'
        new File(dir, '.grails/run/app.log').text.contains("listening on ${port}")

        and: 'the interrupt of the terminal of the CLI is not its: it is in a process group of its own, or ignores it where the shell could not give it one'
        isSparedTheInterrupt(record.pid as long)

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
        int twice = ForgeCli.execute(beanContext, 'stop-app')

        then:
        twice == 1
        errors() == ['| Error No application that run-app started is running for this project: there is no record of one, .grails/run/app.json. Nothing was stopped']
    }

    @Unroll
    void "#asked run-app #how is told through the environment that nothing may wait for what a user types #told"() {
        given:
        scriptedProject(detached ? 'serve' : 'record')
        int port = freePort()

        when:
        int exit = ForgeCli.execute(beanContext, (options + ['run-app', '--port', port as String] + (detached ? ['--detach'] : [])) as String[])

        then: 'the build the command waits for and the one it leaves running are given the same environment'
        exit == 0
        errors() == []
        new File(dir, 'told.txt').text == variable + '\n'

        cleanup:
        if (detached) {
            assert ForgeCli.execute(beanContext, 'stop-app') == 0
            assert !ApplicationPort.isOpen(port)
        }

        where:
        options                 | detached | variable
        ['--non-interactive']   | false    | 'true'
        ['--non-interactive']   | true     | 'true'
        []                      | false    | '<unset>'
        []                      | true     | '<unset>'

        asked = options ? 'with --non-interactive' : 'without --non-interactive'
        how = detached ? 'that leaves the application running' : 'that stays with it'
        told = options ? 'as every build is' : 'by nothing'
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
        int exit = ForgeCli.execute(beanContext, 'run-app', '--detach', '--port', port as String, '--ready-timeout', '30')
        long server = pid('server.pid')

        then:
        exit == 1
        errors() == ["| Error The application did not answer on port ${port} of this computer within 30 seconds. The build was still running, and was stopped. ".toString() +
                'If the application answers on another port, give that with --port; if it needs longer, give it longer with --ready-timeout']
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
        errors()[0].startsWith('| Error The application is bound to 192.0.2.1 by its configuration, a system property or the environment')
        !new File(dir, 'invoked.txt').exists()

        cleanup:
        taken?.close()
    }

    // Linux answers on every address of 127.0.0.0/8 without anything being configured; macOS has only 127.0.0.1
    @Unroll
    @Requires({ os.linux })
    void "run-app --detach waits for an application bound to #host at that address, and stop-app sees its end there"() {
        given:
        scriptedProject('serve')
        int port = freePort()

        when:
        int exit = ForgeCli.execute(beanContext, 'run-app', '--detach', '--port', port as String, '--host', host, '--ready-timeout', '120')

        then: 'the application answers where it is bound, and nowhere else'
        exit == 0
        errors() == []
        out.toString().contains("| Application answers on port ${port}: http://127.0.0.2:${port}. It runs until stop-app")
        record().address == '127.0.0.2'
        !ApplicationPort.isOpen(port)

        when:
        long server = pid('server.pid')
        out.reset()
        int stopped = ForgeCli.execute(beanContext, 'stop-app')

        then: 'stop-app saw the application end at its address'
        stopped == 0
        out.toString().readLines() == ['| Stopping application...', '| Application stopped.']
        !alive(server)
        !new File(dir, '.grails/run/app.json').exists()

        where:
        host << ['127.0.0.2', '::ffff:127.0.0.2', '::ffff:7f00:2']
    }

    @Unroll
    @Requires({ LifecycleCommandsSpec.hasIpv6Loopback() })
    void "an application bound to #host is waited for there alone, and a program that has the port at #other is not taken for it"() {
        given: 'a program that has the port at the other loopback address'
        scriptedProject('serve')
        int port = freePort()
        ServerSocket taken = new ServerSocket(port, 50, InetAddress.getByName(other))
        taken.setSoTimeout(200)

        when:
        int exit = ForgeCli.execute(beanContext, 'run-app', '--detach', '--port', port as String, '--host', host, '--ready-timeout', '30')

        then: 'the port was free where the application is bound, and it answers there'
        exit == 0
        errors() == []
        out.toString().contains("| Application answers on port ${port}: http://${shown}:${port}. It runs until stop-app")
        record().address == recorded

        and: 'the other program was asked nothing'
        nothingAsked(taken)

        when:
        long server = pid('server.pid')
        out.reset()
        int stopped = ForgeCli.execute(beanContext, 'stop-app')

        then: 'stop-app waits for the address of the application, where the other program does not answer'
        stopped == 0
        out.toString().readLines() == ['| Stopping application...', '| Application stopped.']
        !alive(server)
        nothingAsked(taken)

        cleanup:
        taken?.close()

        where:
        host               | other       | shown       | recorded
        '::1'              | '127.0.0.1' | '[::1]'     | '::1'
        '[::1]'            | '127.0.0.1' | '[::1]'     | '::1'
        '0:0:0:0:0:0:0:1'  | '127.0.0.1' | '[::1]'     | '::1'
        '127.0.0.1'        | '::1'       | '127.0.0.1' | '127.0.0.1'
        '::ffff:127.0.0.1' | '::1'       | '127.0.0.1' | '127.0.0.1'
    }

    @Unroll
    void "an application bound to #host is waited for at 127.0.0.1 and ::1, and the record names no address"() {
        given:
        scriptedProject('serve')
        int port = freePort()

        when:
        int exit = ForgeCli.execute(beanContext, (['run-app', '--detach', '--port', port as String] + options) as String[])

        then:
        exit == 0
        errors() == []
        out.toString().contains("| Application answers on port ${port}: http://localhost:${port}. It runs until stop-app")
        !record().containsKey('address')

        cleanup:
        ForgeCli.execute(beanContext, 'stop-app')

        where:
        host         | options
        'no address' | []
        'localhost'  | ['--host', 'localhost']
    }

    @Unroll
    void "run-app waits on the port #where names"() {
        given:
        scriptedProject('serve')
        int port = freePort()
        new File(dir, 'port.txt').text = "${port}\n"
        new File(dir, 'grails-app/conf').mkdirs()
        new File(dir, 'grails-app/conf/application.yml').text = configuration.replace('PORT', port as String)

        when: 'an application that does not answer on the port the command waits on is stopped after the time it is given'
        int exit = ForgeCli.execute(beanContext, (['run-app', '--detach', '--ready-timeout', '30'] + options.collect { String option -> option.replace('PORT', port as String) }) as String[])

        then:
        exit == 0
        errors() == []
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
        // each document as the application reads it: what it says for the environment over what it says for all, then a later document over an earlier one
        'a later document, over the environment of an earlier one'          | forEnvironment('development', '1') + '---\n' + forAll('PORT')                       | []
        'the environment of a later document, over an earlier one'          | forAll('1') + '---\n' + forEnvironment('development', 'PORT')                       | []
        'a later document, over one that names a port for all and for the environment' | forAll('1') + forEnvironment('development', '2') + '---\n' + forAll('PORT') | []
        'the environment of a document, over the document, when no later one names a port' | forAll('1') + forEnvironment('development', 'PORT') + '---\ninfo:\n    app: shop\n' | []
        'the environment of the last of three documents'                    | forEnvironment('development', '1') + '---\n' + forAll('2') + '---\n' + forEnvironment('development', 'PORT') | []
        'a document, over the environment of an earlier one that is given after --' | forEnvironment('test', '1') + '---\n' + forAll('PORT')                    | ['--', '-Dgrails.env=test']
        'the environment given after --, of a later document'               | forAll('1') + '---\nenvironments:\n    test:\n        server:\n            port: PORT\n    development:\n        server:\n            port: 2\n' | ['--', '-Dgrails.env=test']
        'the environment, in one key'                                       | 'server.port: 1\nenvironments.development.server.port: PORT\n'                      | []
        'the key that is written later in a document'                       | 'server.port: 1\nserver:\n    port: PORT\n'                                          | []
        'the environment of a document, and not another one of a later document' | forEnvironment('development', 'PORT') + '---\n' + forEnvironment('production', '1') | []
        // a document for a Spring profile counts when the profile is active, as -Dgrails.spring.profiles.active makes it for the application
        'not a document of a profile that is not active'                    | forAll('PORT') + '---\n' + forProfile('other') + forAll('1')                         | []
        'a document of the profile that is active'                          | forAll('1') + '---\n' + forProfile('other') + forAll('PORT')                         | ['-Dgrails.spring.profiles.active=other']
        'a document of the profile that is active after --'                 | forAll('1') + '---\n' + forProfile('other') + forAll('PORT')                         | ['--', '-Dgrails.spring.profiles.active=other']
        'a document of one of the profiles that are active'                 | forAll('1') + '---\n' + forProfile('other') + forAll('PORT')                         | ['-Dgrails.spring.profiles.active=one, other']
        'the environment of a document of the profile that is active'       | forAll('1') + '---\n' + forProfile('other') + forEnvironment('development', 'PORT') + forAll('2') | ['-Dgrails.spring.profiles.active=other']
        'a document of a profile expression that is met'                    | forAll('1') + '---\n' + forProfile('"!other"') + forAll('PORT')                      | []
        'not a document of a profile expression that is not met'            | forAll('PORT') + '---\n' + forProfile('"!other"') + forAll('1')                      | ['-Dgrails.spring.profiles.active=other']
        'a document of the profile that is active, by the former key'       | forAll('1') + '---\nspring:\n    profiles: other\n' + forAll('PORT')                 | ['-Dgrails.spring.profiles.active=other']
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

    void "when the interactive shell ends, it waits until an application that is not below Gradle has shut down, as with a daemon that was there before"() {
        given: 'an application that takes three seconds to shut down, and closes its port last'
        daemonProject()
        new File(dir, 'shutdown-millis.txt').text = '3000\n'
        int port = freePort()
        ConsoleCommands.setInteractive(true)

        when:
        int exit = ForgeCli.execute(beanContext, 'run-app', '--port', port as String)
        long server = pid('server.pid')
        Map record = record()

        then: 'the application is no process below the one that was recorded'
        exit == 0
        ApplicationPort.isOpen(port)
        !ProcessHandle.of(record.pid as long).get().descendants().anyMatch { ProcessHandle each -> each.pid() == server }

        when: 'what the shell does when it ends'
        out.reset()
        RunAppCommand.stopWhatTheShellStarted()
        long returned = System.currentTimeMillis()

        then: 'it has returned when the application has shut down and its port is free, and says nothing'
        !ApplicationPort.isOpen(port)
        new File(dir, 'shut-down.txt').isFile()
        (new File(dir, 'shut-down.txt').text.trim() as long) <= returned
        !alive(record.pid as long)
        out.toString() == ''
        new PollingConditions(timeout: 30, delay: 0.1).eventually {
            assert !alive(server)
        }
    }

    void "when the interactive shell ends, it waits for an application that is not below Gradle for 30 seconds at most, and says so when it still answers"() {
        given: 'an application that does not end with Gradle'
        daemonProject()
        new File(dir, 'keep').text = ''
        int port = freePort()
        ConsoleCommands.setInteractive(true)

        when:
        int exit = ForgeCli.execute(beanContext, 'run-app', '--port', port as String)
        long server = pid('server.pid')
        Map record = record()
        out.reset()
        long asked = System.nanoTime()
        RunAppCommand.stopWhatTheShellStarted()
        long waited = TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - asked)

        then:
        exit == 0
        waited >= 30 && waited < 60
        !alive(record.pid as long)
        alive(server)
        ApplicationPort.isOpen(port)
        out.toString().readLines() == ["| Warning The shell ended the build of the application that run-app started in it for port ${port}, but something answers on that port still after 30 seconds: ".toString() +
                                           'the application may take longer to end, or it is another one']

        cleanup:
        ProcessHandle.of(server).ifPresent { ProcessHandle handle -> handle.destroyForcibly() }
        new PollingConditions(timeout: 30, delay: 0.1).eventually {
            assert !alive(server)
        }
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
        'has an address that is not of this computer'    | '{"format":1,"pid":OTHER,"started":OTHER_BEGAN,"port":0,"detached":true,"address":"192.0.2.1"}' | false | 'it names an address that is no loopback address of this computer'
        'has an address that is a name'                  | '{"format":1,"pid":OTHER,"started":OTHER_BEGAN,"port":0,"detached":true,"address":"localhost"}' | false | 'it names an address that is no loopback address of this computer'
        'has an address that is no text'                 | '{"format":1,"pid":OTHER,"started":OTHER_BEGAN,"port":0,"detached":true,"address":2130706434}'  | false | 'it names an address that is no loopback address of this computer'
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

    void "stop-app ends nothing for a record that names a process of another user, by its number and the instant it began"() {
        given: 'the oldest process of another user that the system shows, which is none that started this command'
        scriptedProject('serve')
        String user = ProcessHandle.current().info().user().orElse(null)
        Set<Long> above = [] as Set<Long>
        for (Optional<ProcessHandle> each = Optional.of(ProcessHandle.current()); each.present; each = each.get().parent()) {
            above.add(each.get().pid())
        }
        ProcessHandle other = ProcessHandle.allProcesses()
                .filter { ProcessHandle each -> each.pid() > 1 && !(each.pid() in above) }
                .filter { ProcessHandle each -> each.info().user().present && each.info().user().get() != user && each.info().startInstant().present }
                .min { ProcessHandle one, ProcessHandle two -> one.info().startInstant().get() <=> two.info().startInstant().get() }
                .orElse(null)
        if (user == null || other == null) {
            throw new TestAbortedException('this system shows no process of another user with the instant it began')
        }
        writeRecord(pid: other.pid(), started: other.info().startInstant().get().toEpochMilli())

        when:
        int exit = ForgeCli.execute(beanContext, 'stop-app')

        then: 'the record says what the system says of that process, and it is still no process to end'
        exit == 1
        errors() == ["| Error No application that run-app started is running for this project: process ${other.pid()} is not a process of the user who runs the command. " +
                'Nothing was stopped, and the record was removed']
        !out.toString().contains('Stopping')
        other.alive
        !new File(dir, '.grails/run/app.json').exists()
    }

    void "run-app --detach stops what it started when it cannot record it for stop-app"() {
        given: 'a wrapper that says which process it is and stays, and a directory where the record is to be'
        stayingProject()
        new File(dir, '.grails/run/app.json/kept').mkdirs()
        Files.setPosixFilePermissions(new File(dir, '.grails/run').toPath(), PosixFilePermissions.fromString('rwx------'))
        int port = freePort()

        when:
        int exit = ForgeCli.execute(beanContext, 'run-app', '--detach', '--port', port as String)
        noteStarted(new File(dir, 'server.pid'))

        then: 'the command fails, and what it had started is ended: nothing runs that stop-app would not find'
        exit == 1
        errors() == ['| Error The application was stopped again, because it could not be recorded for stop-app: .grails/run/app.json cannot be written: it is a directory']
        !out.toString().contains('Application answers')
        started.every { Long pid -> !alive(pid) }

        and: 'what was in the way is as it was, and nothing was left beside it'
        new File(dir, '.grails/run/app.json/kept').isDirectory()
        new File(dir, '.grails/run').list().toList().sort() == ['.gitignore', 'app.json', 'app.log']
    }

    void "a run-app that stays with the application says so when it cannot record it, and ending the command ends the application all the same"() {
        given:
        stayingProject()
        new File(dir, '.grails/run/app.json/kept').mkdirs()
        Files.setPosixFilePermissions(new File(dir, '.grails/run').toPath(), PosixFilePermissions.fromString('rwx------'))
        AtomicInteger exit = new AtomicInteger(-1)
        Thread command = new Thread({ exit.set(ForgeCli.execute(beanContext, 'run-app', '--port', freePort() as String)) })

        when:
        command.start()
        long server = pid('server.pid')
        new PollingConditions(timeout: 60, delay: 0.1).eventually {
            assert out.toString().contains('stop-app will not find it')
        }

        then: 'the application runs, and the command says how it is stopped'
        alive(server)
        command.alive
        out.toString().readLines().findAll { String line -> line.contains('Warning') } ==
                ['| Warning .grails/run/app.json cannot be written: it is a directory. The application runs, and stop-app will not find it: end this command to stop it']

        when: 'stop-app from elsewhere'
        int stopped = ForgeCli.execute(beanContext, 'stop-app')

        then: 'it ends nothing, as it was told'
        stopped == 1
        errors().size() == 1
        errors()[0].contains('.grails/run/app.json cannot be used, it is not a file. Nothing was stopped')
        alive(server)
        new File(dir, '.grails/run/app.json/kept').isDirectory()

        when:
        out.reset()
        command.interrupt()
        command.join(60_000)

        then:
        !command.alive
        exit.get() == 1
        errors() == ['| Error Gradle was stopped before it finished, because the command was cancelled']
        !alive(server)

        cleanup:
        command?.interrupt()
        command?.join(60_000)
    }

    @Unroll
    void "a run-app that stays with a wrapper that ends at once #what leaves no record of it"() {
        given:
        new File(dir, 'grails-app').mkdirs()
        File wrapper = new File(dir, 'gradlew')
        wrapper.text = content
        wrapper.setExecutable(true, false)

        when:
        int exit = ForgeCli.execute(beanContext, 'run-app', '--port', freePort() as String)

        then: 'the command ends as Gradle did'
        exit == expected
        errors() == said
        !out.toString().contains('stop-app will not find it')

        and: 'and nothing says that an application runs'
        new File(dir, '.grails/run').list().toList() == ['.gitignore']

        when:
        out.reset()
        int stopped = ForgeCli.execute(beanContext, 'stop-app')

        then:
        stopped == 1
        errors() == ['| Error No application that run-app started is running for this project: there is no record of one, .grails/run/app.json. Nothing was stopped']

        where:
        what                 | content                  | expected | said
        'with a failure'     | '#!/bin/sh\nexit 3\n'    | 1        | ['| Error Gradle did not succeed: it ended with exit code 3']
        'without a failure'  | '#!/bin/sh\nexit 0\n'    | 0        | []
    }

    void "stop-app ends nothing for a record of a process that has ended, and removes the record"() {
        given:
        scriptedProject('serve')
        Process ended = new ProcessBuilder('true').start()
        long began = ended.info().startInstant().map { it.toEpochMilli() }.orElse(System.currentTimeMillis())
        ended.waitFor()
        writeRecord(pid: ended.pid(), started: began)

        when:
        int exit = ForgeCli.execute(beanContext, 'stop-app')

        then: 'whether the number is free or another process has it by now, it is not the one that was recorded'
        exit == 1
        errors().size() == 1
        errors()[0].startsWith("| Error No application that run-app started is running for this project: process ${ended.pid()}")
        errors()[0].endsWith('Nothing was stopped, and the record was removed')
        !out.toString().contains('Stopping')
        !new File(dir, '.grails/run/app.json').exists()
    }

    @Unroll
    void "run-app --detach records nothing and says that Gradle ended when the wrapper #what"() {
        given:
        new File(dir, 'grails-app').mkdirs()
        File wrapper = new File(dir, 'gradlew')
        wrapper.text = content
        wrapper.setExecutable(executable, false)
        int port = freePort()

        when:
        int exit = ForgeCli.execute(beanContext, 'run-app', '--detach', '--port', port as String, '--ready-timeout', '60')

        then: 'which of the two lines it is depends on whether the process was still there when the command looked'
        exit == 1
        errors().size() == 1
        errors()[0] == "| Error The Gradle wrapper ${new File(dir.canonicalFile, 'gradlew').path} ended as soon as it was started" ||
                errors()[0] == "| Error Gradle ended before the application answered on port ${port}"
        !out.toString().contains('Application answers')

        and: 'what the wrapper wrote is shown, and there is no record of an application'
        wrote == null || out.toString().contains('The last lines of .grails/run/app.log:')
        wrote == null || out.toString().readLines().contains("  ${wrote}".toString())
        new File(dir, '.grails/run').list().toList().sort() == ['.gitignore', 'app.log']

        when:
        out.reset()
        int stopped = ForgeCli.execute(beanContext, 'stop-app')

        then:
        stopped == 1
        errors() == ['| Error No application that run-app started is running for this project: there is no record of one, .grails/run/app.json. Nothing was stopped']

        where:
        what                                  | content                              | executable | wrote
        'names a program that is not there'   | '#!/nonexistent/interpreter\n'       | true       | null
        'ends at once with a failure'         | '#!/bin/sh\necho gone\nexit 3\n'    | true       | 'gone'
        'ends at once without one'            | '#!/bin/sh\nexit 0\n'               | true       | null
        'is empty and cannot be executed'     | ''                                   | false      | null
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
        errors()[0] == "| Error ${line[0]} is a command of a Grails project; there is none in ${dir.canonicalPath} or above it"

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
        errors()[0].endsWith("GRAILS_SAFE_DIRECTORY. ${line[0]} is a command of a Grails project, and was not run")
        !new File(dir, 'invoked.txt').exists()
        !new File(dir, '.grails').exists()

        where:
        line << [['run-app'], ['run-app', '--detach'], ['stop-app'], ['console'], ['shell'], ['run-script', 'scripts/one.groovy'], ['run-command', 'name', 'argument'],
                 ['run'], ['run', '--detach'], ['stop']]
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
        ['run', 'extra']                         | 'run-app takes no argument extra'
        ['run', '--detach', '--foreground']      | '--detach and --foreground are two answers to one question: give one of them'
        ['stop', '--port', '8080']               | 'stop-app has no option --port'
        ['run', 'app.groovy']                    | "run-app takes no argument app.groovy. ${SCRIPT_ADVICE} grails-forge-cli run-script app.groovy"
        ['run-app', 'scripts/one.groovy']        | "run-app takes no argument scripts/one.groovy. ${SCRIPT_ADVICE} grails-forge-cli run-script scripts/one.groovy"
        ['run', 'app.groovy', 'two']             | "run-app takes no argument app.groovy, and does not take two after it. ${SCRIPT_ADVICE} grails-forge-cli run-script app.groovy"
        ['run', 'my script.groovy']              | "run-app takes no argument my script.groovy. ${SCRIPT_ADVICE} grails-forge-cli run-script FILE"
    }

    @Unroll
    void "#line, of a Groovy script, is answered with run-script outside a project too, where the Grails shell ran the script, and nothing is run"() {
        when:
        int exit = ForgeCli.execute(beanContext, line as String[])

        then:
        exit == 2
        out.toString().readLines()[0] == "run-app takes no argument app.groovy. ${SCRIPT_ADVICE} grails-forge-cli run-script app.groovy".toString()
        dir.list().length == 0

        where:
        line << [['run', 'app.groovy'], ['run-app', 'app.groovy']]
    }

    @Unroll
    void "#line is no Groovy script, and is refused as an argument run-app does not take, and nothing more"() {
        given:
        scriptedProject('record')

        when:
        int exit = ForgeCli.execute(beanContext, line as String[])

        then:
        exit == 2
        out.toString().readLines()[0] == "run-app takes no argument ${line[1]}".toString()
        !out.toString().contains('run-script')
        !new File(dir, 'invoked.txt').exists()

        where:
        line << [['run', 'app.groovy.txt'], ['run', 'groovy'], ['run-app', 'App.Groovy']]
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
        listed.any { String line -> line =~ /^  shell +Runs the Groovy shell of the Grails application: the Gradle task shell\./ }
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
        commands['shell'].aliases == []
        !commands.containsKey('run') && !commands.containsKey('stop')
        !(commands['run-app'].options as List<Map>).collectMany { Map option -> option.names as List<String> }.any { String name -> name in ['-port', '-host', '-https', '-debug-jvm', '-query-tail'] }
        (commands['run-app'].options as List<Map>).collectMany { Map option -> option.names as List<String> }.containsAll(
                ['--port', '--host', '--https', '--debug-jvm', '--query-tail', '-P', '-D', '--detach', '--foreground', '--ready-timeout'])
        (commands['run-command'].parameters as List<Map>)*.label == ['NAME', 'ARG']
        (commands['run-script'].parameters as List<Map>)*.label == ['FILE']

        when: 'the other names of the shell, its short forms, and those of lower-case letters'
        Map<String, Integer> exits = ['run', 'stop', 'rA', 'sA', 'rS', 'rC', 'con', 'she', 'ra', 'sa', 'rs', 'rc'].collectEntries { String form ->
            out.reset()
            [(form): ForgeCli.execute(beanContext, form, '--help')]
        }
        out.reset()
        ForgeCli.execute(beanContext, 'rA', '--help')
        String runApp = out.toString()
        out.reset()
        ForgeCli.execute(beanContext, 'sA', '--help')
        String stopApp = out.toString()
        out.reset()
        ForgeCli.execute(beanContext, 'ra', '--help')
        String initials = out.toString()

        then:
        exits.values().every { int each -> each == 0 }
        initials == runApp
        runApp.readLines().contains('Short form: ra')
        stopApp.readLines().contains('Short form: sa')
        runApp.readLines()[0] == 'Usage: grails-forge-cli run-app [-hvVx] [--non-interactive] [--plain-output]'
        runApp.contains('[--port=PORT] [--host=HOST] [--https] [--debug-jvm] [--query-tail] [--detach | --foreground]')
        runApp.contains('[--ready-timeout=SECONDS] [-DNAME=VALUE]... [-PNAME=VALUE]... [-- GRADLE-ARGUMENT...]')
        runApp.readLines().take(3).every { String line -> line.length() <= 100 }
        !runApp.readLines().take(3).join(' ').contains('-D=') && !runApp.contains('-debug-jvm,') && !runApp.contains('-port,') && !runApp.contains(' -https')
        stopApp.contains(' stop-app [-hvVx]')

        when: 'how the other commands are used'
        Map<String, String> usage = ['console', 'shell', 'run-script', 'run-command'].collectEntries { String name ->
            out.reset()
            ForgeCli.execute(beanContext, name, '--help')
            // the usage is two lines, each within the width of the help: the options of the command line, and those of the command
            assert out.toString().readLines().take(2).every { String line -> line.length() <= 100 }
            [(name): out.toString().readLines().take(2)*.trim().join(' ')]
        }

        then:
        usage['console'].endsWith(' console [-hvVx] [--non-interactive] [--plain-output] [--debug-jvm] [-DNAME=VALUE]... [-- GRADLE-ARGUMENT...]')
        usage['shell'].endsWith(' shell [-hvVx] [--non-interactive] [--plain-output] [--debug-jvm] [-DNAME=VALUE]... [-- GRADLE-ARGUMENT...]')
        usage['run-script'].endsWith(' run-script [-hvVx] [--non-interactive] [--plain-output] [-DNAME=VALUE]... FILE... [-- GRADLE-ARGUMENT...]')
        usage['run-command'].endsWith(' run-command [-hvVx] [--non-interactive] [--plain-output] [-DNAME=VALUE]... NAME [ARG...]')

        when: 'the short forms that fit one of these commands and another one'
        Map<String, String> ambiguous = ['s', 'co', 'c', 'r', 'st', 'ru', 'sto'].collectEntries { String form ->
            out.reset()
            int code = ForgeCli.execute(beanContext, form, '--help')
            [(form): "${code} ${out.toString().readLines()[0]}".toString()]
        }

        then: 'before these commands s was stats and co was compile; r and ru, which start run as much as run-script and run-command, are no command ' +
                'of the command line, so they start nothing, sto is none either, and st is stats still'
        ambiguous.s == '2 s is short for more than one command: stats (st), shell (sh). ' +
                'Type more of the name of the one that is meant, or the short form in brackets after it'
        ambiguous.co == '2 co is short for more than one command: compile (com), console (con). ' +
                'Type more of the name of the one that is meant, or the short form in brackets after it'
        ambiguous.c == '2 c is short for more than one command: compile (com), clean (cl), console (con). ' +
                'Type more of the name of the one that is meant, or the short form in brackets after it'
        ambiguous.r.startsWith('2 ')
        ambiguous.ru.startsWith('2 ')
        ambiguous.sto.startsWith('2 ')
        ambiguous.st.startsWith('0 ') && ambiguous.st.contains(' stats ')
    }

    void "run and stop, the other names the Grails shell had, start the application and stop it as run-app and stop-app do"() {
        given:
        scriptedProject('serve')
        int port = freePort()

        when:
        int exit = ForgeCli.execute(beanContext, 'run', '--detach', '--port', port as String)
        long server = pid('server.pid')
        Map record = record()

        then: 'what run-app --detach does, and says by the name of run-app'
        exit == 0
        errors() == []
        ApplicationPort.isOpen(port)
        out.toString().contains("| Application answers on port ${port}: http://localhost:${port}. It runs until stop-app")
        new File(dir, 'invoked.txt').text.startsWith('<bootRun>')
        new File(dir, 'invoked.txt').text.contains("<-Dgrails.env=development><-Dgrails.server.port=${port}>")
        record.port == port && record.detached == true
        alive(server)

        when:
        out.reset()
        int stopped = ForgeCli.execute(beanContext, 'stop')

        then: 'what stop-app does'
        stopped == 0
        errors() == []
        out.toString().readLines() == ['| Stopping application...', '| Application stopped.']
        !alive(server) && !alive(record.pid as long)
        !ApplicationPort.isOpen(port)
        !new File(dir, '.grails/run/app.json').exists()

        when: 'there is nothing to stop any more'
        out.reset()
        int twice = ForgeCli.execute(beanContext, 'stop')

        then:
        twice == 1
        errors() == ['| Error No application that run-app started is running for this project: there is no record of one, .grails/run/app.json. Nothing was stopped']
    }

    void "a run-app that stayed with its application leaves the record and the stop request of another one alone"() {
        given:
        scriptedProject('serve')
        int port = freePort()
        AtomicInteger exit = new AtomicInteger(-1)
        Thread command = new Thread({ exit.set(ForgeCli.execute(beanContext, 'run-app', '--port', port as String)) })
        Process other = new ProcessBuilder('sleep', '300').start()
        started.add(other.pid())
        long otherBegan = other.info().startInstant().get().toEpochMilli()

        when:
        command.start()
        new PollingConditions(timeout: 60, delay: 0.2).eventually {
            assert ApplicationPort.isOpen(port) && new File(dir, '.grails/run/app.json').isFile()
        }
        long server = pid('server.pid')
        Map mine = record()

        and: 'before it sees its Gradle end, stop-app has asked another application to stop, and another run-app has recorded its own'
        String asked = JsonOutput.toJson([pid: other.pid(), started: otherBegan])
        String theirs = JsonOutput.toJson([format: 1, pid: other.pid(), started: otherBegan, port: 0, detached: true])
        new File(dir, '.grails/run/app.stopping').text = asked
        new File(dir, '.grails/run/app.json').text = theirs
        GradleRunner.stop(ProcessHandle.of(mine.pid as long).get(), 10)
        command.join(60_000)

        then: 'its own end was not asked for, and what is the other one\'s is as it was'
        !command.alive
        exit.get() == 1
        errors() == ['| Error Gradle did not succeed: it ended with exit code 143']
        !alive(server)
        new File(dir, '.grails/run/app.json').text == theirs
        new File(dir, '.grails/run/app.stopping').text == asked

        cleanup:
        other?.destroyForcibly()
        other?.waitFor()
    }

    void "stop-app and a run-app right after it: the first run-app ends as asked, and the second is recorded"() {
        given:
        scriptedProject('serve')
        int port = freePort()
        int next = freePort()
        AtomicInteger first = new AtomicInteger(-1)
        Thread command = new Thread({ first.set(ForgeCli.execute(beanContext, 'run-app', '--port', port as String)) })

        when:
        command.start()
        new PollingConditions(timeout: 60, delay: 0.2).eventually {
            assert ApplicationPort.isOpen(port) && new File(dir, '.grails/run/app.json').isFile()
        }
        long server = pid('server.pid')
        new File(dir, 'server.pid').delete()
        int stopped = ForgeCli.execute(beanContext, 'stop-app')
        int second = ForgeCli.execute(beanContext, 'run-app', '--detach', '--port', next as String)
        command.join(60_000)
        Map record = record()

        then:
        stopped == 0
        second == 0
        !command.alive
        first.get() == 0
        errors() == []
        !alive(server)

        and: 'the record is the one of the second, which runs'
        record.port == next && record.detached == true
        alive(record.pid as long)
        ApplicationPort.isOpen(next)
        !new File(dir, '.grails/run/app.stopping').exists()

        cleanup:
        ForgeCli.execute(beanContext, 'stop-app')
    }

    @IgnoreIf({ os.windows || !ForkedCli.available() })
    void "when the CLI is ended while run-app --detach waits, what it started is ended, the record is removed, and it says so"() {
        given:
        scriptedProject('silent')
        File said = new File(dir, 'cli.out')

        when: 'the CLI as a program, ended by a signal, which interrupts no thread'
        Process program = ForkedCli.builder(dir, [], 'run-app', '--detach', '--port', freePort() as String).redirectErrorStream(true).redirectOutput(said).start()
        new PollingConditions(timeout: 300, delay: 0.2).eventually {
            assert new File(dir, 'server.pid').isFile() && new File(dir, 'server.pid').text.endsWith('\n') && new File(dir, '.grails/run/app.json').isFile()
        }
        long server = pid('server.pid')
        Map record = record()
        program.destroy()
        boolean ended = program.waitFor(120, TimeUnit.SECONDS)

        then:
        ended
        program.exitValue() == 143
        said.text.readLines().findAll { String line -> line.contains('| Error') } == ['| Error The application was stopped before it answered, because the command was ended']
        !alive(server) && !alive(record.pid as long)
        !new File(dir, '.grails/run/app.json').exists()

        cleanup:
        program?.destroyForcibly()
    }

    @Unroll
    void "run-app --detach starts nothing when #what, since the #unknown cannot be determined"() {
        given:
        scriptedProject('serve')
        new File(dir, 'grails-app/conf').mkdirs()
        if (configuration != null) {
            new File(dir, 'grails-app/conf/application.yml').text = configuration
        }
        RunAppCommand.setEnvironmentVariables({ String name -> variables[name] } as java.util.function.Function<String, String>)

        when:
        int exit = ForgeCli.execute(beanContext, (['run-app', '--detach', '--ready-timeout', '30'] + options) as String[])

        then:
        exit == 1
        errors().size() == 1
        errors()[0].startsWith("| Error The ${unknown} the application answers on could not be determined: ".toString())
        errors()[0].contains(said)
        errors()[0].endsWith("Nothing was started; give ${asks}, or stay with the application with --foreground".toString())
        !new File(dir, 'invoked.txt').exists()

        and: 'nothing of the file is on the terminal'
        !out.toString().contains('\u001b') && !out.toString().contains('RED') && out.toString().readLines().size() == 1

        when: 'the port is given'
        out.reset()
        int port = freePort()
        new File(dir, 'port.txt').text = "${port}\n"
        exit = ForgeCli.execute(beanContext, (['run-app', '--detach', '--port', port as String, '--host', '127.0.0.1'] + options.findAll { String option -> !option.startsWith('-Dgrails.server.port') }) as String[])

        then:
        exit == 0
        record().port == port

        cleanup:
        ForgeCli.execute(beanContext, 'stop-app')

        where:
        what                                             | configuration                                                    | options                        | variables            | unknown                | said
        'the configuration has a placeholder'            | 'server:\n  port: ${PORT:18194}\n'                               | []                             | [:]                  | 'port'                 | 'server.port of grails-app/conf/application.yml is not plainly a port'
        'the configuration has a number with a fraction' | 'server:\n  port: 18196.0\n'                                     | []                             | [:]                  | 'port'                 | 'server.port of grails-app/conf/application.yml is not plainly a port'
        'the configuration has a number of another base' | 'server:\n  port: 0o17\n'                                        | []                             | [:]                  | 'port'                 | 'server.port of grails-app/conf/application.yml is not plainly a port'
        'the configuration has a number that starts with a zero' | 'server:\n  port: "018195"\n'                            | []                             | [:]                  | 'port'                 | 'server.port of grails-app/conf/application.yml is not plainly a port'
        'the configuration has a port that is a list'    | 'server:\n  port: [1, 2]\n'                                      | []                             | [:]                  | 'port'                 | 'server.port of grails-app/conf/application.yml is not plainly a port'
        'the configuration has an address to work out'   | 'server:\n  address: "${HOST}"\n'                                | []                             | [:]                  | 'address'              | 'server.address of grails-app/conf/application.yml is not plainly an address'
        'the configuration has a key twice'              | '"k\\e[31mRED\\nFAKE | Error @|red x|@": 1\n"k\\e[31mRED\\nFAKE | Error @|red x|@": 2\n' | [] | [:] | 'port and the address' |
                'grails-app/conf/application.yml has a key twice in one map at line 2, column 1, which YAML does not allow'
        'the configuration is nested without end'        | 'x: ' + '[' * 5000 + ']' * 5000 + '\nserver:\n  port: 18199\n'   | []                             | [:]                  | 'port and the address' |
                'grails-app/conf/application.yml nests lists and maps deeper than 32 levels, more than the command reads'
        'the configuration is not YAML'                  | 'server: [\u0000'                                                | []                             | [:]                  | 'port and the address' |
                'grails-app/conf/application.yml is not valid YAML at line 1, column 10, where it has a character YAML does not allow'
        'the system property is no port'                 | null                                                             | ['-Dgrails.server.port=abc']   | [:]                  | 'port'                 | '-Dgrails.server.port is given, and its value is not a port'
        'the environment names no port'                  | 'server:\n  port: 18200\n'                                       | []                             | [SERVER_PORT: 'abc'] | 'port'                 | 'the environment variable SERVER_PORT is set, and its value is not a port'
        asks = [port: 'the port with --port', address: 'the address with --host', 'port and the address': 'the port with --port and the address with --host'][unknown]
    }

    void "run-app takes -Dserver.port for what it is, a property the application is not given"() {
        given:
        scriptedProject('record')

        when:
        int exit = ForgeCli.execute(beanContext, 'run-app', '--detach', '-Dserver.port=18082')

        then:
        exit == 2
        out.toString().contains('-Dserver.port is a system property of the build, which the Grails Gradle plugin does not give to the application')
        out.toString().contains('give --port PORT or -Dgrails.server.port=PORT')
        !new File(dir, 'invoked.txt').exists()

        when: 'the command stays with the application, and has no port to wait on'
        out.reset()
        new File(dir, 'grails-app/conf').mkdirs()
        new File(dir, 'grails-app/conf/application.yml').text = 'server:\n  port: ${PORT:18194}\n'
        exit = ForgeCli.execute(beanContext, 'run-app', '-Dserver.port=18082')

        then:
        exit == 0
        errors() == []
        new File(dir, 'invoked.txt').text == '<bootRun><--quiet><-Dgrails.env=development><-Dserver.port=18082>\n'
    }

    void "a port that takes connections and answers nothing is not an application that answers"() {
        given:
        scriptedProject('mute')
        int port = freePort()

        when:
        int exit = ForgeCli.execute(beanContext, 'run-app', '--detach', '--port', port as String, '--ready-timeout', '30')
        long server = pid('server.pid')

        then:
        exit == 1
        errors() == ["| Error The application did not answer on port ${port} of this computer within 30 seconds: the port takes connections, and no answer to an HTTP request came from it. ".toString() +
                'The build was still running, and was stopped. If the application answers on another port, give that with --port; if it needs longer, give it longer with --ready-timeout']
        !alive(server)
        !new File(dir, '.grails/run/app.json').exists()
    }

    void "run-app --detach --https waits for an answer over TLS, with the certificate it created"() {
        given:
        scriptedProject('serve')
        int port = freePort()

        when:
        int exit = ForgeCli.execute(beanContext, 'run-app', '--detach', '--https', '--port', port as String)

        then:
        exit == 0
        errors() == []
        out.toString().contains("| Application answers on port ${port}: https://localhost:${port}.")
        ApplicationPort.isReady(port)

        cleanup:
        ForgeCli.execute(beanContext, 'stop-app')
    }

    void "stop-app waits until an application that is not below Gradle has ended, as with a daemon that was there before"() {
        given:
        daemonProject()
        int port = freePort()

        when:
        int exit = ForgeCli.execute(beanContext, 'run-app', '--detach', '--port', port as String)
        long server = pid('server.pid')
        Map record = record()

        then: 'the application is no process below the one that was recorded'
        exit == 0
        alive(server)
        !ProcessHandle.of(record.pid as long).get().descendants().anyMatch { ProcessHandle each -> each.pid() == server }

        when:
        out.reset()
        int stopped = ForgeCli.execute(beanContext, 'stop-app')

        then: 'and has ended when stop-app says so'
        stopped == 0
        errors() == []
        !ApplicationPort.isOpen(port)
        new PollingConditions(timeout: 30, delay: 0.1).eventually {
            assert !alive(server)
        }
    }

    void "stop-app says so when the application has not ended with Gradle"() {
        given:
        daemonProject()
        new File(dir, 'keep').text = ''
        int port = freePort()

        when:
        int exit = ForgeCli.execute(beanContext, 'run-app', '--detach', '--port', port as String)
        long server = pid('server.pid')
        Map record = record()
        out.reset()
        int stopped = ForgeCli.execute(beanContext, 'stop-app')

        then:
        exit == 0
        stopped == 1
        errors() == ["| Error Gradle was stopped, but something answers on port ${port} still after 30 seconds: the application may take longer to end, or it is another one. Nothing else was stopped".toString()]
        !alive(record.pid as long)
        alive(server)

        cleanup:
        ProcessHandle.of(server).ifPresent { ProcessHandle handle -> handle.destroyForcibly() }
        new PollingConditions(timeout: 30, delay: 0.1).eventually {
            assert !alive(server)
        }
    }

    @Unroll
    void "a process the record names by number and instant is #taken for the Gradle of the project when its command line has #what"() {
        given:
        scriptedProject('serve')
        File sibling = new File(dir.canonicalPath + '-other')
        sibling.mkdirs()
        new File(sibling, 'gradlew').text = ''
        new File(dir, 'gradle/wrapper').mkdirs()
        new File(dir, 'gradle/wrapper/gradle-wrapper.jar').text = ''
        new File(dir, 'src').mkdirs()
        // a shell that waits for a line that does not come, and so stays the process with these words
        List<String> line = ['sh', '-c', 'read line', 'decoy'] + words.collect { String word -> word.replace('DIR', dir.canonicalPath) }
        Process other = new ProcessBuilder(line).start()
        started.add(other.pid())
        writeRecord(pid: other.pid(), started: other.info().startInstant().get().toEpochMilli())

        when:
        int exit = ForgeCli.execute(beanContext, 'stop-app')

        then:
        exit == (stopped ? 0 : 1)
        other.alive == !stopped
        new File(dir, '.grails/run/app.json').exists() == !stopped
        stopped || (errors().size() == 1 && errors()[0].contains('its command line is not that of the Gradle wrapper of this project running bootRun'))

        cleanup:
        other?.destroyForcibly()
        other?.waitFor()
        sibling?.deleteDir()

        where:
        what                                                          | words                                                                                         | stopped
        'the task and a path in the project'                          | ['bootRun', 'DIR/src']                                                                        | false
        'the wrapper of a directory whose name starts like this one'  | ['DIR-other/gradlew', 'bootRun']                                                              | false
        'the wrapper after the task'                                  | ['bootRun', 'DIR/gradlew']                                                                    | false
        'the wrapper and another task'                                | ['DIR/gradlew', 'xbootRun']                                                                   | false
        'the wrapper jar without the option of Java before it'        | ['DIR/gradle/wrapper/gradle-wrapper.jar', 'bootRun']                                          | false
        'the wrapper jar as a class path and another main class'      | ['-classpath', 'DIR/gradle/wrapper/gradle-wrapper.jar', 'Other', 'bootRun']                   | false
        'the wrapper and the task'                                    | ['DIR/gradlew', '--quiet', 'bootRun']                                                         | true
        'the wrapper jar as the jar of Java, and the task'            | ['-Xmx64m', '-jar', 'DIR/gradle/wrapper/gradle-wrapper.jar', 'bootRun']                       | true
        'the wrapper jar as a class path, its main class and the task' | ['-classpath', 'DIR/gradle/wrapper/gradle-wrapper.jar', 'org.gradle.wrapper.GradleWrapperMain', 'bootRun'] | true
        taken = stopped ? 'taken' : 'not taken'
    }

    void "a .grails/run that others can write to is not used"() {
        given:
        scriptedProject('record')
        Process other = new ProcessBuilder('sleep', '300').start()
        started.add(other.pid())
        writeRecord(pid: other.pid(), started: other.info().startInstant().get().toEpochMilli())
        Files.setPosixFilePermissions(new File(dir, '.grails/run').toPath(), PosixFilePermissions.fromString(mode))

        when:
        int stop = ForgeCli.execute(beanContext, 'stop-app')
        int run = ForgeCli.execute(beanContext, 'run-app')
        int command = ForgeCli.execute(beanContext, 'run-command', 'name')

        then:
        stop == 1 && run == 1 && command == 1
        errors().size() == 3
        errors().every { String line -> line.contains('.grails/run can be written to by others than its owner') }
        other.alive
        !new File(dir, 'invoked.txt').exists()
        new File(dir, '.grails/run').list().toList() == ['app.json']

        cleanup:
        other?.destroyForcibly()
        other?.waitFor()

        where:
        mode << ['rwxrwx---', 'rwx---rwx', 'rwx-w----']
    }

    void "run-command deletes the file of a command that is no longer there, and keeps the file of one that is"() {
        given:
        scriptedProject('record')
        new File(dir, '.grails/run').mkdirs()
        Process ended = new ProcessBuilder('true').start()
        ended.waitFor()
        ProcessHandle self = ProcessHandle.current()
        new File(dir, '.grails/run/command-args-0.json').text = '["of a CLI that runs","secret"]'
        new File(dir, '.grails/run/command-args-0.pid').text = "${self.pid()} ${self.info().startInstant().get().toEpochMilli()}\n"
        new File(dir, '.grails/run/command-args-1.json').text = '["of a CLI that was killed","secret"]'
        new File(dir, '.grails/run/command-args-1.pid').text = "${ended.pid()} 5\n"
        new File(dir, '.grails/run/command-args-2.json').text = '["of a number another process has now","secret"]'
        new File(dir, '.grails/run/command-args-2.pid').text = "${self.pid()} 5\n"
        new File(dir, '.grails/run/command-args-3.pid').text = 'not a process\n'
        new File(dir, '.grails/run/command-args-3.pid').setLastModified(System.currentTimeMillis() - 5 * 60 * 1000L)

        when:
        int exit = ForgeCli.execute(beanContext, 'run-command', 'my-command')

        then: 'the first name that is free again is used'
        exit == 0
        new File(dir, 'invoked.txt').text.startsWith('<runCommand><--args-file=.grails/run/command-args-1.json>')
        new File(dir, '.grails/run').list().toList().sort() == ['.gitignore', 'command-args-0.json', 'command-args-0.pid']
        new File(dir, '.grails/run/command-args-0.json').text == '["of a CLI that runs","secret"]'
    }

    void "the arguments of run-command are held to their size in UTF-8, which is what the task reads"() {
        given:
        scriptedProject('record')

        when: 'six hundred thousand bytes that would be more than a million as escapes'
        int exit = ForgeCli.execute(beanContext, 'run-command', 'name', '\u4e2d' * 200_000)

        then:
        exit == 0
        new File(dir, 'args-file.txt').length() == 600_000 + '["name",""]'.length()

        when:
        new File(dir, 'invoked.txt').delete()
        out.reset()
        exit = ForgeCli.execute(beanContext, 'run-command', 'name', '\u4e2d' * 349_524)

        then: 'three bytes each, and the brackets, quotes and name around them: more than 1048576'
        exit == 2
        out.toString().contains('The command cannot be given these arguments: the arguments are more than 1048576 bytes in UTF-8')
        !new File(dir, 'invoked.txt').exists()
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
        wrapper.text = "#!/bin/sh\n\"${System.getProperty('java.home')}/bin/java\" \"${dir.canonicalPath}/Server.java\" \"\$@\"\n"
        wrapper.setExecutable(true, false)
    }

    // what a document of application.yml says for all environments
    private static String forAll(String port) {
        return "server:\n    port: ${port}\n".toString()
    }

    // what a document of application.yml says for one environment
    private static String forEnvironment(String environment, String port) {
        return "environments:\n    ${environment}:\n        server:\n            port: ${port}\n".toString()
    }

    // what makes a document of application.yml one for the profiles a selector names
    private static String forProfile(String selector) {
        return "spring:\n    config:\n        activate:\n            on-profile: ${selector}\n".toString()
    }

    // A wrapper that becomes the one process it names in server.pid and stays, so that ending it leaves nothing behind
    private void stayingProject() {
        new File(dir, 'grails-app').mkdirs()
        File wrapper = new File(dir, 'gradlew')
        wrapper.text = '#!/bin/sh\necho $$ > server.pid\nexec sleep 300\n'
        wrapper.setExecutable(true, false)
    }

    // A wrapper whose application is not a process below it, as with a Gradle daemon that was there
    // before: the application ends when the wrapper is gone, unless the file keep is there.
    private void daemonProject() {
        scriptedProject('serve')
        File wrapper = new File(dir, 'gradlew')
        wrapper.text = """\
            #!/bin/sh
            me=\$\$
            ( ( "${System.getProperty('java.home')}/bin/java" "${dir.canonicalPath}/Server.java" "\$@" &
                app=\$!
                while kill -0 \$me 2>/dev/null; do sleep 0.2; done
                [ -f "${dir.canonicalPath}/keep" ] || kill \$app ) & )
            sleep 300 &
            wait
            """.stripIndent()
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

    static boolean hasIpv6Loopback() {
        try {
            new ServerSocket(0, 1, InetAddress.getByName('::1')).close()
            return true
        } catch (IOException ignored) {
            return false
        }
    }

    // whether nothing has connected to a server that accepts nothing itself: a connection waits for it to be accepted
    private static boolean nothingAsked(ServerSocket server) {
        try {
            server.accept().close()
            return false
        } catch (SocketTimeoutException ignored) {
            return true
        }
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

    // What the system has decides which: setsid or the job control of the shell put the build in a process
    // group of its own, which the terminal of the CLI sends nothing; a shell that has neither starts it with
    // the interrupt ignored. DetachedBuildSpec holds each of the three to what it is.
    private static boolean isSparedTheInterrupt(long pid) {
        if (processGroup(pid) != processGroup(ProcessHandle.current().pid())) {
            return true
        }
        new ProcessBuilder('kill', '-INT', pid as String).start().waitFor()
        Thread.sleep(500)
        return alive(pid)
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

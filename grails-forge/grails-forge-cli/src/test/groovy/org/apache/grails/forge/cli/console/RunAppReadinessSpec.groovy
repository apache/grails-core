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

import java.util.concurrent.atomic.AtomicInteger
import groovy.json.JsonSlurper
import org.grails.forge.cli.CommandSpec
import org.grails.forge.cli.ForgeCli
import spock.lang.IgnoreIf
import spock.lang.Timeout
import spock.lang.Unroll
import spock.util.concurrent.PollingConditions

/**
 * When {@code run-app} takes an application for started: what it asks of the port, and what it makes
 * of the answers of {@code grails-startup-progress}.
 *
 * <p>The application here is a program of one file behind a Gradle wrapper of two lines. It answers
 * as startup progress does, with the names of {@code run-app-protocol.properties} in the sources of
 * that module, which this specification holds the command line to and the specification of the
 * module holds the module to. Which stage of a start it plays is in a file the features rewrite, and
 * it writes down every request it gets, so a feature waits for what the command line asked, not for
 * time to pass.</p>
 */
@IgnoreIf({ os.windows })
@Timeout(240)
class RunAppReadinessSpec extends CommandSpec {

    static final String APPLICATION = '''\
        import java.io.ByteArrayInputStream;
        import java.io.FileInputStream;
        import java.io.IOException;
        import java.io.InputStream;
        import java.net.InetAddress;
        import java.net.ServerSocket;
        import java.net.Socket;
        import java.nio.charset.StandardCharsets;
        import java.nio.file.Files;
        import java.nio.file.Path;
        import java.nio.file.StandardOpenOption;
        import java.security.KeyStore;
        import java.util.Properties;
        import javax.net.ssl.KeyManagerFactory;
        import javax.net.ssl.SSLContext;

        public class Application {

            static Path home = Path.of("").toAbsolutePath();

            static String text(String name, String otherwise) throws IOException {
                return Files.exists(home.resolve(name)) ? Files.readString(home.resolve(name)).trim() : otherwise;
            }

            // the request line, and the rest of the head read to its end; null when nothing was asked
            static String head(InputStream request, int first) throws IOException {
                StringBuilder line = new StringBuilder();
                String asked = null;
                int last = 0;
                int next = first;
                while (next != -1) {
                    if (next == '\\n') {
                        asked = asked == null ? line.toString() : asked;
                        if (last == '\\n') {
                            break;
                        }
                    } else if (next != '\\r') {
                        line.append((char) next);
                    }
                    if (next != '\\r') {
                        last = next;
                    }
                    next = request.read();
                }
                return asked;
            }

            static void answer(Socket socket, String answer) throws IOException {
                socket.getOutputStream().write((answer + "\\r\\n").getBytes(StandardCharsets.ISO_8859_1));
                socket.getOutputStream().flush();
            }

            static void asked(String what) throws IOException {
                Files.writeString(home.resolve("requests.txt"), what + "\\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            }

            public static void main(String[] args) throws Exception {
                String port = "0";
                String keyStore = null;
                for (String arg : args) {
                    if (arg.startsWith("-Dgrails.server.port=")) {
                        port = arg.substring(21);
                    }
                    if (arg.startsWith("-Dgrails.server.ssl.key-store=")) {
                        keyStore = arg.substring(30);
                    }
                }
                if (args.length == 0 || !args[0].equals("bootRun")) {
                    return;
                }
                Properties protocol = new Properties();
                try (FileInputStream file = new FileInputStream(home.resolve("protocol.properties").toFile())) {
                    protocol.load(file);
                }
                SSLContext context = null;
                if (keyStore != null) {
                    KeyStore keys = KeyStore.getInstance(KeyStore.getDefaultType());
                    try (FileInputStream file = new FileInputStream(keyStore)) {
                        keys.load(file, "123456".toCharArray());
                    }
                    KeyManagerFactory factory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
                    factory.init(keys, "123456".toCharArray());
                    context = SSLContext.getInstance("TLS");
                    context.init(factory.getKeyManagers(), null, null);
                }
                try (ServerSocket server = new ServerSocket(Integer.parseInt(port), 50, InetAddress.getLoopbackAddress())) {
                    Files.writeString(home.resolve("server.pid"), ProcessHandle.current().pid() + "\\n");
                    System.out.println("the application says why: database is down");
                    while (true) {
                        try (Socket plain = server.accept()) {
                            plain.setSoTimeout(2000);
                            Socket socket = plain;
                            int first = plain.getInputStream().read();
                            if (context != null && first != 0x16) {
                                // as a web server that expects TLS answers a request that is none
                                head(plain.getInputStream(), first);
                                asked("PLAIN");
                                answer(plain, "HTTP/1.1 400 \\r\\nConnection: close\\r\\n");
                                continue;
                            }
                            if (context != null) {
                                socket = context.getSocketFactory().createSocket(plain, new ByteArrayInputStream(new byte[] {(byte) first}), true);
                                first = socket.getInputStream().read();
                            }
                            String asked = head(socket.getInputStream(), first);
                            if (asked == null) {
                                continue;
                            }
                            String[] request = asked.split(" ");
                            asked(request[0] + " " + request[1]);
                            // who answers, and the phase: "page" is the server of the progress page, which takes every
                            // request; "filter" is the web server with the application behind it; "none" is no startup progress
                            String[] stage = text("stage.txt", "none").split(" ");
                            String statusPath = text("status-path.txt", protocol.getProperty("statusPath.default"));
                            String headers = stage.length < 2 ? "" : protocol.getProperty("header.phase") + ": " + stage[1] + "\\r\\n";
                            if (stage.length == 2 && !Files.exists(home.resolve("no-status-path"))) {
                                headers += protocol.getProperty("header.statusPath") + ": " + text("advertised.txt", statusPath) + "\\r\\n";
                            }
                            if (stage[0].equals("page")) {
                                answer(socket, (request[1].equals(statusPath) ? "HTTP/1.1 200 OK\\r\\n" : "HTTP/1.1 503 Service Unavailable\\r\\nRetry-After: 2\\r\\n") + headers);
                            } else if (stage[0].equals("filter") && request[1].equals(statusPath)) {
                                answer(socket, "HTTP/1.1 200 OK\\r\\n" + headers);
                            } else {
                                answer(socket, "HTTP/1.1 404 Not Found\\r\\n");
                            }
                            socket.close();
                        } catch (IOException ignored) {
                        }
                    }
                }
            }
        }
        '''.stripIndent()

    PrintStream originalOut
    PrintStream originalErr
    ByteArrayOutputStream out = new ByteArrayOutputStream()
    Properties protocol = new Properties()
    PollingConditions conditions = new PollingConditions(timeout: 90, delay: 0.05)

    void setup() {
        originalOut = System.out
        originalErr = System.err
        System.setOut(new PrintStream(out, true))
        System.setErr(new PrintStream(out, true))
        File written = new File(previousUsrDir, '../../grails-startup-progress/src/test/resources/org/apache/grails/startup/run-app-protocol.properties')
        written.withInputStream { InputStream stream -> protocol.load(stream) }
        new File(dir, 'protocol.properties').bytes = written.bytes
        new File(dir, 'grails-app').mkdirs()
        new File(dir, 'Application.java').text = APPLICATION
        File wrapper = new File(dir, 'gradlew')
        wrapper.text = "#!/bin/sh\n\"${System.getProperty('java.home')}/bin/java\" \"${dir.canonicalPath}/Application.java\" \"\$@\"\n"
        wrapper.setExecutable(true, false)
    }

    void cleanup() {
        ForgeCli.execute(beanContext, 'stop-app')
        System.setOut(originalOut)
        System.setErr(originalErr)
        // whatever a feature started and did not see end is ended here, and the feature has then failed
        List<Long> left = []
        File pid = new File(dir, 'server.pid')
        if (pid.isFile() && pid.text.trim() ==~ /[0-9]+/ && alive(pid.text.trim() as long)) {
            left.add(pid.text.trim() as long)
            ProcessHandle.of(left[0]).ifPresent { ProcessHandle handle -> handle.destroyForcibly() }
        }
        assert left.isEmpty()
    }

    void "the command line asks as it is written down for it and for startup progress"() {
        expect:
        ApplicationPort.REQUEST_LINE == protocol.getProperty('request.line')
        ApplicationPort.PHASE_HEADER == protocol.getProperty('header.phase')
        ApplicationPort.STATUS_PATH_HEADER == protocol.getProperty('header.statusPath')
        ApplicationPort.DEFAULT_STATUS_PATH == protocol.getProperty('statusPath.default')
        ApplicationPort.READY_PHASE == protocol.getProperty('phase.ready')
        ApplicationPort.FAILED_PHASE == protocol.getProperty('phase.failed')

        and: 'a phase it has no name for is one of a start that goes on, and is taken as that'
        !(protocol.getProperty('phases.starting').split(',') as List).any { String phase -> phase in [ApplicationPort.READY_PHASE, ApplicationPort.FAILED_PHASE] }
    }

    void "run-app --detach follows the start through its phases on the path startup progress names, and returns when it says ready"() {
        given: 'an application under a context path that shows its progress page'
        String statusPath = '/app' + protocol.getProperty('statusPath.default')
        new File(dir, 'status-path.txt').text = statusPath
        stage('page CREATING_BEANS')
        int port = freePort()
        AtomicInteger exit = new AtomicInteger(-1)
        Thread command = new Thread({ exit.set(ForgeCli.execute(beanContext, 'run-app', '--detach', '--port', port as String)) })

        when: 'the page has the port'
        command.start()
        conditions.eventually {
            assert asked().count("HEAD ${statusPath}".toString()) >= 3
        }

        then: 'the command asked the root once, and the path it was told from then on, and waits'
        asked()[0] == 'HEAD /'
        asked().count('HEAD /') == 1
        command.alive

        when: 'the web server has the port, and answers for the application while BootStrap runs'
        stage('filter INITIALIZING')
        int before = asked().size()
        conditions.eventually {
            assert asked().size() >= before + 4
        }

        then: 'the command still waits: it asks the status path, where the start goes on, and not the root, which the application answers'
        command.alive
        asked().drop(before + 1).every { String request -> request == "HEAD ${statusPath}".toString() }
        !out.toString().contains('Application answers')

        when: 'the application is ready'
        stage('filter READY')
        command.join(60_000)

        then:
        !command.alive
        exit.get() == 0
        errors() == []
        out.toString().contains("| Application answers on port ${port}: http://localhost:${port}. It runs until stop-app")

        and: 'nothing was asked but those two paths, and nothing but their heads'
        asked().toSet() == ['HEAD /', "HEAD ${statusPath}".toString()] as Set
    }

    void "run-app --detach waits for an application that was past its progress page when it was first asked"() {
        given: 'a web server that answers for the application while BootStrap runs'
        stage('filter INITIALIZING')
        int port = freePort()
        AtomicInteger exit = new AtomicInteger(-1)
        Thread command = new Thread({ exit.set(ForgeCli.execute(beanContext, 'run-app', '--detach', '--port', port as String)) })

        when:
        command.start()
        conditions.eventually {
            assert asked().size() >= 5
        }

        then: 'the root is the application\'s, so the path startup progress has by default was asked, and is asked from then on'
        asked().take(2) == ['HEAD /', "HEAD ${protocol.getProperty('statusPath.default')}".toString()]
        asked().count('HEAD /') == 1
        command.alive

        when:
        stage('filter READY')
        command.join(60_000)

        then:
        exit.get() == 0
        errors() == []
    }

    void "run-app --detach says that the start failed when startup progress says so, and does not wait for the time to pass"() {
        given:
        stage('page CREATING_BEANS')
        int port = freePort()
        AtomicInteger exit = new AtomicInteger(-1)
        Thread command = new Thread({ exit.set(ForgeCli.execute(beanContext, 'run-app', '--detach', '--port', port as String, '--ready-timeout', '3600')) })

        when:
        command.start()
        conditions.eventually {
            assert asked().size() >= 3
        }
        long server = new File(dir, 'server.pid').text.trim() as long
        stage('page FAILED')
        command.join(60_000)

        then: 'it says so, with what the application wrote, and what it started is gone'
        !command.alive
        exit.get() == 1
        errors() == ["| Error The application failed to start: its startup progress says so on port ${port}. What was started was stopped".toString()]
        out.toString().contains('The last lines of .grails/run/app.log:')
        out.toString().contains('  the application says why: database is down')
        !alive(server)
        !new File(dir, '.grails/run/app.json').exists()
    }

    void "without startup progress an application has started when it answers a request, and is asked for the status once"() {
        given:
        int port = freePort()

        when:
        int exit = ForgeCli.execute(beanContext, 'run-app', '--detach', '--port', port as String)

        then:
        exit == 0
        errors() == []
        asked() == ['HEAD /', "HEAD ${protocol.getProperty('statusPath.default')}".toString()]
    }

    void "run-app --detach --https follows the start over TLS, past the refusal a web server gives a request that is not TLS"() {
        given:
        stage('filter INITIALIZING')
        int port = freePort()
        AtomicInteger exit = new AtomicInteger(-1)
        Thread command = new Thread({ exit.set(ForgeCli.execute(beanContext, 'run-app', '--detach', '--https', '--port', port as String)) })

        when:
        command.start()
        conditions.eventually {
            assert asked().count("HEAD ${protocol.getProperty('statusPath.default')}".toString()) >= 3
        }

        then: 'the refusal was no answer of the application, and the start is followed where it is said'
        asked().take(2) == ['PLAIN', 'HEAD /']
        command.alive

        and: 'once TLS answered, nothing is asked without it'
        asked().count('PLAIN') == 1

        when:
        stage('filter READY')
        command.join(60_000)

        then:
        exit.get() == 0
        errors() == []
        out.toString().contains("| Application answers on port ${port}: https://localhost:${port}.")
        ApplicationPort.isReady(port)
    }

    @Unroll
    void "a status path that is #what is not asked: the one startup progress has by default is"() {
        given:
        new File(dir, 'advertised.txt').text = advertised
        stage('page CREATING_BEANS')
        int port = freePort()
        AtomicInteger exit = new AtomicInteger(-1)
        Thread command = new Thread({ exit.set(ForgeCli.execute(beanContext, 'run-app', '--detach', '--port', port as String)) })

        when:
        command.start()
        conditions.eventually {
            assert asked().size() >= 4
        }
        stage('page READY')
        command.join(60_000)

        then:
        exit.get() == 0
        asked().toSet() == ['HEAD /', "HEAD ${protocol.getProperty('statusPath.default')}".toString()] as Set

        where:
        what                      | advertised
        'an address'              | 'http://127.0.0.1:1/status'
        'a path of another host'  | '//elsewhere.example/status'
        'a path with a query'     | '/status?next=/logout'
        'a path with a space'     | '/status HTTP/1.0'
        'longer than a path here' | '/' + 'a' * 300
    }

    void "startup progress that names no path is followed on the default one, and what answers there without a phase is the application"() {
        given: 'a progress page as an earlier version serves it, for an application that has its status elsewhere'
        new File(dir, 'no-status-path').text = ''
        new File(dir, 'status-path.txt').text = '/elsewhere'
        stage('page CREATING_BEANS')
        int port = freePort()
        AtomicInteger exit = new AtomicInteger(-1)
        Thread command = new Thread({ exit.set(ForgeCli.execute(beanContext, 'run-app', '--detach', '--port', port as String)) })

        when:
        command.start()
        conditions.eventually {
            assert asked().count("HEAD ${protocol.getProperty('statusPath.default')}".toString()) >= 3
        }

        then:
        command.alive

        when: 'the web server has the port'
        stage('filter INITIALIZING')
        command.join(60_000)

        then: 'as before there was a status to ask'
        exit.get() == 0
        errors() == []
    }

    void "run-app --detach says that startup progress answered when the application is not ready in time"() {
        given:
        stage('page CREATING_BEANS')
        int port = freePort()

        when:
        int exit = ForgeCli.execute(beanContext, 'run-app', '--detach', '--port', port as String, '--ready-timeout', '3')

        then:
        exit == 1
        errors() == ["| Error The application did not answer on port ${port} of this computer within 3 seconds: its startup progress answers there, and has not said that the application is ready. ".toString() +
                'The build was still running, and was stopped. If the application answers on another port, give that with --port; if it needs longer, give it longer with --ready-timeout']
        !new File(dir, '.grails/run/app.json').exists()
    }

    void "stop-app says that it did not see the end of an application that had not taken its port"() {
        given: 'a wrapper that becomes one process that takes no port, as an application does while it starts'
        File wrapper = new File(dir, 'gradlew')
        wrapper.text = '#!/bin/sh\necho $$ > server.pid\nsleep 300\n'
        int port = freePort()
        AtomicInteger exit = new AtomicInteger(-1)
        Thread command = new Thread({ exit.set(ForgeCli.execute(beanContext, 'run-app', '--port', port as String)) })

        when:
        command.start()
        conditions.eventually {
            assert new File(dir, '.grails/run/app.json').isFile() && new File(dir, 'server.pid').isFile()
            assert new JsonSlurper().parse(new File(dir, '.grails/run/app.json')).port == port
        }
        out.reset()
        int stopped = ForgeCli.execute(beanContext, 'stop-app')
        command.join(60_000)

        then: 'what it ended is said, and what it could not see is not claimed'
        stopped == 0
        out.toString().readLines().take(2) == ['| Stopping application...',
                "| Gradle was stopped. Nothing took connections on port ${port} yet, so the end of the application was not seen there: an application that was starting is ended with its build.".toString()]
        !command.alive
        exit.get() == 0
    }

    private void stage(String stage) {
        // in one step, so the application never reads half of it
        File next = new File(dir, 'stage.next')
        next.text = stage + '\n'
        next.renameTo(new File(dir, 'stage.txt'))
    }

    private List<String> asked() {
        File requests = new File(dir, 'requests.txt')
        return requests.isFile() ? requests.readLines() : []
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

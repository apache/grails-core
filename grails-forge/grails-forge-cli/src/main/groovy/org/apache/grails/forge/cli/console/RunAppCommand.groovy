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
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Consumer
import java.util.function.Function
import groovy.transform.CompileStatic
import org.grails.forge.application.OperatingSystem
import org.grails.forge.feature.FeatureSelectionException
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import picocli.CommandLine

/**
 * {@code run-app}: the Gradle task {@code bootRun} of the project, for the development environment
 * unless another one is given.
 *
 * <p>The command either stays with the application until it ends, or starts it, waits until it
 * answers on its port and returns, leaving it running for {@code stop-app}. Either way it writes a
 * record to {@code .grails/run} of the project, which is what {@code stop-app} goes by. As a rule it
 * starts the Gradle wrapper of the project as a process and records that process, which
 * {@code stop-app} ends. Where it stays with the application and runs the build through the Tooling
 * API of Gradle, from its own JVM, which it does in the interactive console with {@code --foreground}
 * and where the environment asks for the Tooling API, there is no such process: it records itself,
 * {@code stop-app} asks it to end the build, and it looks for that. Which of the two runs Gradle is
 * decided in {@link GradleBuild}.</p>
 *
 * @since 9.0
 */
@CommandLine.Command(name = 'run-app',
        description = [
                'Runs the Grails application: the Gradle task bootRun.',
                'Stays with the application until it ends or the command is cancelled.',
                'With @|bold --detach|@, and in the interactive shell, returns when the application answers on its port',
                'and leaves it running until stop-app.',
                'The environment is development unless -Dgrails.env names another one.',
                'What follows @|bold --|@ is given to Gradle as it is.'
        ],
        customSynopsis = [
                '${COMMAND-FULL-NAME} [-hvVx] [--non-interactive] [--plain-output]',
                '        [--port=PORT] [--host=HOST] [--https] [--debug-jvm] [--query-tail] [--detach | --foreground]',
                '        [--ready-timeout=SECONDS] [-DNAME=VALUE]... [-PNAME=VALUE]... [-- GRADLE-ARGUMENT...]'
        ],
        preprocessor = GradleTaskCommand.PassedOn)
@CompileStatic
class RunAppCommand extends GradleEnvironmentCommand {

    private static final int DEFAULT_PORT = 8080
    private static final int DEFAULT_HTTPS_PORT = 8443
    private static final int STOP_SECONDS = 10
    // How long the command sleeps between two looks. A user waits half of it on average each time something is
    // waited for, so both are short: a look for what stop-app says reads the attributes of one file, and a look
    // for an answer of the application is one request to this computer.
    private static final int STOP_POLL_MILLIS = 50
    private static final int READY_POLL_MILLIS = 100
    private static final int TAIL_LINES = 20
    private static final int KEYTOOL_SECONDS = 60
    private static final int MAX_CONFIGURATION = 1024 * 1024
    private static final int MAX_NESTING = 32
    private static final int MAX_ALIASES = 20
    private static final String CONFIGURATION = 'grails-app/conf/application.yml'
    private static final String KEY_STORE = 'build/ssl/keystore'
    private static final String KEY_STORE_PROPERTY = 'grails.server.ssl.key-store'
    // the password of the certificate of the shell, which is for development on this computer and no secret
    private static final String KEY_STORE_PASSWORD = '123456'
    private static final String PORT_PROPERTY = 'grails.server.port'
    private static final String ADDRESS_PROPERTY = 'grails.server.address'
    private static final String PORT_VARIABLE = 'SERVER_PORT'
    private static final String ADDRESS_VARIABLE = 'SERVER_ADDRESS'
    // a system property of the build that the Grails Gradle plugin does not hand to the application
    private static final String BUILD_PORT_PROPERTY = 'server.port'
    private static final String LOG = RunDirectory.LOCATION + '/' + RunningApplication.LOG
    private static final Map<String, String> ENVIRONMENTS = [dev: 'development', prod: 'production']
    // a lambda, and no closure made into a function when the class is loaded: that goes through what Groovy
    // makes a text of any object with, which costs every start of the command line some 70 ms
    private static final Function<String, String> ENVIRONMENT = (String name) -> System.getenv(name)

    private static final List<ProcessHandle> STARTED_IN_SHELL = new CopyOnWriteArrayList<ProcessHandle>()
    // what stops them when the JVM ends while the shell runs; guarded by STARTED_IN_SHELL
    private static Thread stopsWithJvm
    private static volatile Function<String, String> environmentVariables = ENVIRONMENT

    @CommandLine.Option(names = ['--port'], paramLabel = 'PORT', description = 'The port the application answers on. Without it, the port its configuration names, or 8080.')
    Integer port

    @CommandLine.Option(names = ['--host'], paramLabel = 'HOST', description = 'The address the application binds to.')
    String host

    @CommandLine.Option(names = ['--https'], description = 'Serve HTTPS, on port 8443 unless a port is given or configured, with a self-signed certificate that is created in build/ssl when there is none.')
    boolean https

    @CommandLine.Option(names = ['--debug-jvm'], description = 'Start the JVM of the application suspended, waiting for a debugger on port 5005.')
    boolean debugJvm

    @CommandLine.Option(names = ['--query-tail'], description = 'Capture every statement GORM runs, for the query-tail command.')
    boolean queryTail

    // the options as the shell spells them, with one dash: taken, and not shown
    @CommandLine.Option(names = ['-port'], hidden = true)
    Integer portOfShell

    @CommandLine.Option(names = ['-host'], hidden = true)
    String hostOfShell

    @CommandLine.Option(names = ['-https'], hidden = true)
    boolean httpsOfShell

    @CommandLine.Option(names = ['-debug-jvm'], hidden = true)
    boolean debugJvmOfShell

    @CommandLine.Option(names = ['-query-tail'], hidden = true)
    boolean queryTailOfShell

    @CommandLine.Option(names = ['-P'], paramLabel = 'NAME=VALUE', mapFallbackValue = '', description = 'A project property for the build; can be given more than once.')
    Map<String, String> projectProperties = new LinkedHashMap<>()

    @CommandLine.Option(names = ['--detach'], description = 'Return when the application answers on its port, and leave it running, also when the CLI has ended. Its output is in .grails/run/app.log.')
    boolean detach

    @CommandLine.Option(names = ['--foreground'], description = 'Stay with the application until it ends, also in the interactive shell.')
    boolean foreground

    @CommandLine.Option(names = ['--ready-timeout'], paramLabel = 'SECONDS', defaultValue = '300',
            description = 'How long the application is given to answer when the command does not stay with it; it is stopped when it has not by then. Default: ${DEFAULT-VALUE}.')
    int readyTimeout

    /**
     * Stops the applications that {@code run-app} started in the interactive shell without
     * {@code --detach}, which belong to the session of the shell. The shell does it when it is left,
     * before it returns, also where the JVM goes on after it, as that of a program that runs the
     * command line does; and the JVM does it when it ends while the shell runs. An application that
     * {@code run-app --detach} started is not among them and goes on.
     */
    static void stopWhatTheShellStarted() {
        Thread hook
        List<ProcessHandle> started
        synchronized (STARTED_IN_SHELL) {
            hook = stopsWithJvm
            stopsWithJvm = null
            started = new ArrayList<ProcessHandle>(STARTED_IN_SHELL)
            STARTED_IN_SHELL.clear()
        }
        if (hook != null && !hook.is(Thread.currentThread())) {
            // a JVM that goes on after the shell is left no hook of it
            try {
                Runtime.getRuntime().removeShutdownHook(hook)
            } catch (IllegalStateException ignored) {
                // the JVM is shutting down, and the hook runs
            }
        }
        started.findAll { ProcessHandle each -> each.isAlive() }.each { ProcessHandle each -> GradleRunner.stop(each, STOP_SECONDS) }
    }

    /**
     * Replaces the environment the command reads the port and the address of the application from,
     * for a specification, which cannot change the environment it runs in.
     *
     * @param variables gives the value of a variable, or {@code null}; {@code null} for the environment of the process
     */
    static void setEnvironmentVariables(Function<String, String> variables) {
        environmentVariables = variables == null ? ENVIRONMENT : variables
    }

    @Override
    protected String defaultEnvironment() {
        return 'development'
    }

    @Override
    protected List<String> verboseArguments() {
        // --verbose shows what the build says while the application starts, which is otherwise left out
        return []
    }

    @Override
    protected List<String> gradleArguments(ConsoleProject project) {
        List<String> arguments = [project.task('bootRun')]
        if (debugJvm) {
            arguments.add('--debug-jvm')
        }
        if (!verbose()) {
            arguments.add('--quiet')
        }
        arguments.addAll(systemPropertyArguments())
        if (port != null) {
            arguments.add("-D${PORT_PROPERTY}=${port}".toString())
        }
        if (host != null) {
            arguments.add("-D${ADDRESS_PROPERTY}=${host}".toString())
        }
        if (queryTail) {
            arguments.add('-Pgrails.gorm.queryTail.enabled')
        }
        projectProperties.each { String name, String value ->
            arguments.add("-P${name}=${value}".toString())
        }
        return arguments
    }

    @Override
    protected int run(ConsoleProject project) {
        boolean windows = getOperatingSystem() == OperatingSystem.WINDOWS
        boolean detached = detach || (ConsoleCommands.isInteractive() && !foreground)
        port = port != null ? port : portOfShell
        host = host != null ? host : hostOfShell
        https = https || httpsOfShell
        debugJvm = debugJvm || debugJvmOfShell
        queryTail = queryTail || queryTailOfShell
        check(detached)
        RunningApplication running = RunningApplication.find(project, windows)
        if (running.state == RunningApplication.State.RUNNING) {
            err("An application that run-app started is running for this project already: process ${running.pid}${running.port > 0 ? ', for port ' + running.port : ''}. " +
                    'Run stop-app first. Nothing was started')
            return 1
        }
        if (running.state == RunningApplication.State.REFUSED || running.state == RunningApplication.State.UNCONFIRMED) {
            err("The application was not started, because it could not be recorded for stop-app: ${GradleRunner.line(running.reason)}" +
                    (running.state == RunningApplication.State.UNCONFIRMED ? ". End that process yourself if it is the application, and delete ${RunDirectory.LOCATION}/${RunningApplication.RECORD}" : ''))
            return 1
        }
        Path directory
        try {
            directory = RunDirectory.of(project)
        } catch (IOException e) {
            err("The application was not started, because it could not be recorded for stop-app: ${GradleRunner.line(e.getMessage())}")
            return 1
        }
        Configured configured = configured(project)
        int answersOn = configured.port
        if (detached) {
            String unknown = configured.portUnknown ?: configured.addressUnknown
            if (unknown != null) {
                err("The port and the address the application answers on could not be determined: ${unknown}. " +
                        'Nothing was started; give the port with --port, or stay with the application with --foreground')
                return 1
            }
        }
        List<String> arguments = new ArrayList<String>(gradleArguments(project))
        if (https) {
            if (answersOn == 0 && configured.portUnknown == null) {
                answersOn = DEFAULT_HTTPS_PORT
                arguments.add("-D${PORT_PROPERTY}=${DEFAULT_HTTPS_PORT}".toString())
            }
            arguments.addAll(certificateArguments(project))
        }
        if (answersOn == 0 && configured.portUnknown == null) {
            answersOn = DEFAULT_PORT
        }
        if (showStacktrace()) {
            arguments.add('--stacktrace')
            arguments.add('-Dgrails.full.stacktrace=true')
        }
        arguments.addAll(passedOn)
        try {
            return detached ? startAndReturn(project, windows, directory, arguments, answersOn, configured.address) :
                    stayWith(project, windows, directory, arguments, answersOn)
        } catch (GradleRunner.RefusedArgumentException e) {
            throw new CommandLine.ParameterException(spec.commandLine(), e.getMessage())
        }
    }

    private void check(boolean detached) {
        if (detach && foreground) {
            throw new CommandLine.ParameterException(spec.commandLine(), '--detach and --foreground are two answers to one question: give one of them')
        }
        if (port != null && (port < 1 || port > 65535)) {
            throw new CommandLine.ParameterException(spec.commandLine(), "--port is a port, a number from 1 to 65535: ${port} is none")
        }
        if (readyTimeout < 1 || readyTimeout > 86400) {
            throw new CommandLine.ParameterException(spec.commandLine(), "--ready-timeout is a number of seconds from 1 to 86400: ${readyTimeout} is none")
        }
        if (host != null && host.isEmpty()) {
            throw new CommandLine.ParameterException(spec.commandLine(), '--host is an address the application binds to, and none was given')
        }
        if (detached && !ApplicationPort.isReachedOnLoopback(host)) {
            throw new CommandLine.ParameterException(spec.commandLine(),
                    "The command waits for the application on the loopback address of this computer, where one that is bound to ${FeatureSelectionException.shown(host)} does not answer: " +
                            'give --foreground to stay with the application instead')
        }
        if (detached && port == null && !systemProperties.containsKey(PORT_PROPERTY) && systemProperties.containsKey(BUILD_PORT_PROPERTY)) {
            throw new CommandLine.ParameterException(spec.commandLine(),
                    "-D${BUILD_PORT_PROPERTY} is a system property of the build, which the Grails Gradle plugin does not give to the application, so the command cannot tell " +
                            "which port the application answers on: give --port PORT or -D${PORT_PROPERTY}=PORT, which the application is given")
        }
    }

    // The command stays with Gradle, which stays with the application. The record lets stop-app, from
    // another terminal, end them; what Gradle then ends with is the end that was asked for. The record
    // and what stop-app said are this run's only while they name the process this run recorded: by the
    // time Gradle has ended, another run-app can have started and recorded another one. Where the build
    // is run from this JVM there is no process for stop-app to end: the record names this command, which
    // looks for what stop-app says while the build runs, and cancels the build itself.
    private int stayWith(ConsoleProject project, boolean windows, Path directory, List<String> arguments, int answersOn) throws GradleRunner.RefusedArgumentException {
        List<String> failures = []
        long[] recorded = [0L, 0L] as long[]
        AtomicBoolean asked = new AtomicBoolean()
        Thread[] looking = new Thread[1]
        GradleBuild runner = GradleBuild.of(GradleBuild.Purpose.BUILD, { String message -> failures.add(message) } as Consumer<String>, windows)
        out('| Running application...')
        int exit = runner.run(project, arguments, { GradleBuild.Running started ->
            try {
                // the instant as it was recorded: of a process that has ended by now the system says none
                recorded[1] = RunningApplication.record(directory, started.process, answersOn, false, started.commandLine)
                recorded[0] = started.process.pid()
                if (started.commandLine) {
                    looking[0] = lookForStop(directory, recorded[0], recorded[1], asked, started)
                }
            } catch (IOException e) {
                // a build that has ended by now is none to record, and says itself how it ended
                if (started.process.isAlive()) {
                    warning("${GradleRunner.line(e.getMessage())}. The application runs, and stop-app will not find it: end this command to stop it")
                }
            }
        } as Consumer<GradleBuild.Running>)
        if (looking[0] != null) {
            looking[0].interrupt()
        }
        if (recorded[0] != 0L && RunningApplication.wasAskedToStop(directory, recorded[0], recorded[1])) {
            asked.set(true)
        }
        if (recorded[0] != 0L) {
            RunningApplication.forget(directory, recorded[0], recorded[1])
        }
        if (asked.get()) {
            out(GradleRunner.asksToEnd() ? '| Application stopped' : "| Application stopped. ${GradleRunner.ENDED_AT_ONCE}")
            return 0
        }
        failures.each { String message -> err(message) }
        return exit
    }

    // Looks, while the build runs, for what stop-app says to this command, and cancels the build when it is said.
    private static Thread lookForStop(Path directory, long pid, long began, AtomicBoolean asked, GradleBuild.Running build) {
        Thread thread = new Thread({
            try {
                while (!RunningApplication.wasAskedToStop(directory, pid, began)) {
                    Thread.sleep(STOP_POLL_MILLIS)
                }
                asked.set(true)
                build.cancel()
            } catch (InterruptedException ignored) {
                // the build has ended
            }
        } as Runnable, 'grails-run-app-stop-asked')
        thread.setDaemon(true)
        thread.start()
        return thread
    }

    private int startAndReturn(ConsoleProject project, boolean windows, Path directory, List<String> arguments, int answersOn, String address) throws GradleRunner.RefusedArgumentException {
        if (!ApplicationPort.isReachedOnLoopback(address)) {
            err("The application is bound to ${FeatureSelectionException.shown(address)} by its configuration, a system property or the environment, and the command waits for it " +
                    'on the loopback address of this computer, where it would not answer. Nothing was started; give --foreground to stay with the application instead')
            return 1
        }
        if (ApplicationPort.isOpen(answersOn)) {
            err("Something answers on port ${answersOn} of this computer already, so the command could not tell when the application does. " +
                    'Nothing was started; stop what has the port, or give another one with --port')
            return 1
        }
        Path log
        try {
            log = RunDirectory.create(directory, RunningApplication.LOG)
        } catch (IOException e) {
            err("The application was not started: ${GradleRunner.line(e.getMessage())}")
            return 1
        }
        GradleRunner runner = GradleBuild.leftRunning({ String message -> err(message) } as Consumer<String>, windows)
        // From here to the answer the build is this command's to end: also when the CLI is ended by a
        // signal, which interrupts no thread. The hook is there before the build is, and is told of it.
        AtomicBoolean ending = new AtomicBoolean()
        ProcessHandle[] build = new ProcessHandle[1]
        long[] recorded = [0L, 0L] as long[]
        Thread whenEnded = new Thread({
            ending.set(true)
            ProcessHandle left = build[0]
            if (left != null) {
                err('The application was stopped before it answered, because the command was ended')
                GradleRunner.stop(left, STOP_SECONDS)
                RunningApplication.forget(directory, recorded[0], recorded[1])
            }
        } as Runnable, 'grails-run-app-wait')
        try {
            Runtime.getRuntime().addShutdownHook(whenEnded)
        } catch (IllegalStateException ignored) {
            err('The application was not started: the command is being ended')
            return 1
        }
        try {
            ProcessHandle started = runner.start(project, arguments, log)
            build[0] = started
            if (started == null) {
                said(log)
                return 1
            }
            if (ending.get()) {
                // the hook ran before it was told of the build
                err('The application was stopped before it answered, because the command was ended')
                GradleRunner.stop(started, STOP_SECONDS)
                return 1
            }
            try {
                // the instant as it was recorded: of a process that has ended by now the system says none
                recorded[1] = RunningApplication.record(directory, started, answersOn, true)
                recorded[0] = started.pid()
            } catch (IOException e) {
                if (!started.isAlive()) {
                    // nothing is there to record: the build has ended already, which is what there is to say
                    err("Gradle ended before the application answered on port ${answersOn}")
                    said(log)
                    return 1
                }
                GradleRunner.stop(started, STOP_SECONDS)
                err("The application was stopped again, because it could not be recorded for stop-app: ${GradleRunner.line(e.getMessage())}")
                return 1
            }
            out("| Running application... Its output is in ${LOG}")
            String ended = waitFor(started, answersOn, ending)
            if (ending.get()) {
                // the hook says it, ends the build and removes the record
                return 1
            }
            if (ended != null) {
                RunningApplication.forget(directory, recorded[0], recorded[1])
                err(ended)
                said(log)
                return 1
            }
            boolean withShell = ConsoleCommands.isInteractive() && !detach
            if (withShell) {
                stopWithShell(started)
            }
            out("| Application answers on port ${answersOn}: ${https ? 'https' : 'http'}://localhost:${answersOn}. " +
                    "It runs until stop-app${withShell ? ', or until the shell ends' : ''}")
            return 0
        } finally {
            // once it answers, or has been ended, the build is no longer the hook's
            build[0] = null
            try {
                Runtime.getRuntime().removeShutdownHook(whenEnded)
            } catch (IllegalStateException ignored) {
                // the JVM is shutting down, and the hook runs
            }
        }
    }

    // null when the application answers; otherwise why it does not, after what was started has ended
    private String waitFor(ProcessHandle started, int answersOn, AtomicBoolean ending) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(readyTimeout)
        ApplicationPort.Start start = ApplicationPort.start(answersOn)
        while (!ending.get()) {
            if (!started.isAlive()) {
                return "Gradle ended before the application answered on port ${answersOn}"
            }
            // an answer counts while what was started is still there: a port that another program took is not the application's
            ApplicationPort.Started answer = start.ask()
            if (answer == ApplicationPort.Started.READY) {
                if (started.isAlive()) {
                    return null
                }
                continue
            }
            if (answer == ApplicationPort.Started.FAILED && started.isAlive()) {
                // said by the application itself, whose process need not end for it: one that is restarted on a change stays
                GradleRunner.stop(started, STOP_SECONDS)
                return "The application failed to start: its startup progress says so on port ${answersOn}. What was started was stopped"
            }
            if (System.nanoTime() > deadline) {
                boolean accepts = ApplicationPort.isOpen(answersOn)
                GradleRunner.stop(started, STOP_SECONDS)
                String seen = !accepts ? '' : start.isReported() ?
                        ': its startup progress answers there still, and the web server of the application has not taken the port' :
                        ': the port takes connections, and no answer to an HTTP request came from it'
                return "The application did not answer on port ${answersOn} of this computer within ${readyTimeout} seconds" + seen +
                        '. The build was still running, and was stopped. ' +
                        'If the application answers on another port, give that with --port; if it needs longer, give it longer with --ready-timeout'
            }
            try {
                Thread.sleep(READY_POLL_MILLIS)
            } catch (InterruptedException ignored) {
                GradleRunner.stop(started, STOP_SECONDS)
                Thread.currentThread().interrupt()
                return 'The application was stopped before it answered, because the command was cancelled'
            }
        }
        return null
    }

    private void said(Path log) {
        List<String> lines = RunningApplication.tail(log, TAIL_LINES)
        if (!lines.isEmpty()) {
            PrintWriter writer = spec.commandLine().getErr()
            writer.println("The last lines of ${LOG}:")
            lines.each { String line -> writer.println('  ' + line) }
        }
    }

    private static void stopWithShell(ProcessHandle started) {
        synchronized (STARTED_IN_SHELL) {
            STARTED_IN_SHELL.add(started)
            if (stopsWithJvm == null) {
                Thread hook = new Thread({ stopWhatTheShellStarted() } as Runnable, 'grails-run-app-stop')
                Runtime.getRuntime().addShutdownHook(hook)
                stopsWithJvm = hook
            }
        }
    }

    // The certificate the shell creates for --https, in the place it has it, so one that is there is used.
    private List<String> certificateArguments(ConsoleProject project) {
        if (systemProperties.containsKey(KEY_STORE_PROPERTY)) {
            return []
        }
        Path keyStore = project.directory.resolve(KEY_STORE)
        String manual = "Set server.ssl.key-store in ${CONFIGURATION}, or give -D${KEY_STORE_PROPERTY}=FILE"
        if (project.isLinked(KEY_STORE)) {
            warning("${KEY_STORE} is reached through a link, so no certificate is read or created there. ${manual}")
            return []
        }
        if (!Files.isRegularFile(keyStore, LinkOption.NOFOLLOW_LINKS)) {
            String refusal = createCertificate(keyStore)
            if (refusal != null) {
                warning("No certificate could be created for HTTPS: ${refusal}. ${manual}")
                return []
            }
            out("| Created a self-signed certificate for localhost in ${KEY_STORE}")
        }
        ownerOnly(keyStore, 'rw-------')
        return ["-D${KEY_STORE_PROPERTY}=${keyStore}".toString(),
                "-Dgrails.server.ssl.key-store-password=${KEY_STORE_PASSWORD}".toString(),
                "-Dgrails.server.ssl.key-password=${KEY_STORE_PASSWORD}".toString()]
    }

    private String createCertificate(Path keyStore) {
        String home = System.getProperty('java.home') ?: System.getenv('JAVA_HOME')
        Path keytool = home == null ? null : Path.of(home, 'bin', getOperatingSystem() == OperatingSystem.WINDOWS ? 'keytool.exe' : 'keytool')
        if (keytool == null || !Files.isRegularFile(keytool)) {
            return 'there is no keytool in the Java installation the command runs with'
        }
        try {
            boolean created = !Files.exists(keyStore.getParent(), LinkOption.NOFOLLOW_LINKS)
            Files.createDirectories(keyStore.getParent())
            if (created) {
                // the private key is in it: before there is a file, the directory is the owner's alone
                ownerOnly(keyStore.getParent(), 'rwx------')
            }
            Process process = new ProcessBuilder(keytool.toString(), '-genkeypair', '-alias', 'localhost', '-dname', 'CN=localhost,OU=Test,O=Test,C=US',
                    '-keyalg', 'RSA', '-validity', '365', '-keystore', keyStore.toString(), '-storepass', KEY_STORE_PASSWORD, '-keypass', KEY_STORE_PASSWORD)
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start()
            if (!process.waitFor(KEYTOOL_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                return "keytool did not end within ${KEYTOOL_SECONDS} seconds"
            }
            return process.exitValue() == 0 && Files.isRegularFile(keyStore) ? null : "keytool ended with exit code ${process.exitValue()}"
        } catch (IOException e) {
            return GradleRunner.line(e.getMessage())
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt()
            return 'the command was cancelled'
        }
    }

    private static void ownerOnly(Path path, String permissions) {
        try {
            if (path.getFileSystem().supportedFileAttributeViews().contains('posix')) {
                Files.setPosixFilePermissions(path, PosixFilePermissions.fromString(permissions))
            }
        } catch (IOException | RuntimeException ignored) {
            // a file of another owner stays as its owner has it
        }
    }

    /** The port and the address the application will answer on, as far as the command can know them. */
    private static final class Configured {

        /** The port, or 0 when nothing names one. */
        int port

        /** Why the port is not known although something names one; {@code null} when it is known or nothing names one. */
        String portUnknown

        /** The address, or {@code null} when nothing names one. */
        String address

        /** Why the address is not known although something names one. */
        String addressUnknown
    }

    // What the application is told, in the order it listens to it: the system properties the build
    // hands it (--port and --host are given as these, and -Dgrails.server.port is handed on as
    // server.port), then the environment the build runs it in, then its application.yml, where what is
    // said for the environment it runs for comes before what is said for all. What names a port and is
    // not plainly one, as a placeholder that the application resolves when it starts, is not guessed at.
    private Configured configured(ConsoleProject project) {
        Configured found = new Configured()
        Map<String, Object> inFile = null
        String given = port != null ? port.toString() : systemProperties.get(PORT_PROPERTY)
        String variable = environmentVariables.apply(PORT_VARIABLE)
        if (given != null) {
            found.port = portOf(given) ?: 0
            found.portUnknown = found.port == 0 ? "-D${PORT_PROPERTY} is given, and its value is not a port, a number from 1 to 65535" : null
        } else if (variable != null && !variable.isEmpty()) {
            found.port = portOf(variable) ?: 0
            found.portUnknown = found.port == 0 ? "the environment variable ${PORT_VARIABLE} is set, and its value is not a port, a number from 1 to 65535" : null
        } else {
            inFile = ofFile(project)
            found.port = (inFile.port ?: 0) as int
            found.portUnknown = inFile.portUnknown as String ?: inFile.unknown as String
        }
        String address = host != null ? host : systemProperties.get(ADDRESS_PROPERTY) ?: environmentVariables.apply(ADDRESS_VARIABLE)
        if (address != null && !address.isEmpty()) {
            found.address = address
        } else {
            inFile = inFile != null ? inFile : ofFile(project)
            found.address = inFile.address as String
            found.addressUnknown = inFile.addressUnknown as String ?: inFile.unknown as String
        }
        return found
    }

    // port, address: what application.yml names plainly; portUnknown, addressUnknown: why one that is named
    // is not known; unknown: why nothing of the file is. Every reason is the command's own words: nothing
    // of the file, and nothing a parser says of it, is repeated.
    private Map<String, Object> ofFile(ConsoleProject project) {
        Map<String, Object> found = [:]
        Path file = project.directory.resolve(CONFIGURATION)
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
            return found
        }
        try {
            if (project.isLinked(CONFIGURATION) || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                found.unknown = "${CONFIGURATION} is not a file of the project, or is reached through a link, and is not read".toString()
                return found
            }
            if (Files.size(file) > MAX_CONFIGURATION) {
                found.unknown = "${CONFIGURATION} is larger than ${MAX_CONFIGURATION} bytes, and is not read".toString()
                return found
            }
            // the environment of the project, the one this command gives its build (-Dgrails.env, or
            // development), and not the one of Forge, which is production whatever the project's is
            String environment = ENVIRONMENTS.get(environment()) ?: environment()
            LoaderOptions options = new LoaderOptions()
            options.setAllowDuplicateKeys(false)
            options.setMaxAliasesForCollections(MAX_ALIASES)
            options.setNestingDepthLimit(MAX_NESTING)
            options.setCodePointLimit(MAX_CONFIGURATION)
            options.setProcessComments(false)
            Object[] ports = new Object[2]
            Object[] addresses = new Object[2]
            for (Object document : new Yaml(new SafeConstructor(options)).loadAll(Files.readString(file))) {
                List<Object> levels = [document, value(document, ['environments', environment])]
                for (int level = 0; level < 2; level++) {
                    // a key can be written in one piece or as a map in a map
                    Object settings = levels.get(level)
                    Object namedPort = value(settings, ['server.port']) != null ? value(settings, ['server.port']) : value(settings, ['server', 'port'])
                    Object namedAddress = value(settings, ['server.address']) != null ? value(settings, ['server.address']) : value(settings, ['server', 'address'])
                    ports[level] = namedPort != null ? namedPort : ports[level]
                    addresses[level] = namedAddress != null ? namedAddress : addresses[level]
                }
            }
            Object namedPort = ports[1] != null ? ports[1] : ports[0]
            if (namedPort != null) {
                Integer plain = namedPort instanceof Integer || namedPort instanceof String ? portOf(namedPort.toString()) : null
                found.port = plain
                found.portUnknown = plain != null ? null :
                        "server.port of ${CONFIGURATION} is not plainly a port, a number from 1 to 65535, but something the application works out when it starts".toString()
            }
            Object namedAddress = addresses[1] != null ? addresses[1] : addresses[0]
            if (namedAddress != null) {
                boolean plain = namedAddress instanceof String && namedAddress ==~ /[A-Za-z0-9.:\[\]-]{1,255}/
                found.address = plain ? namedAddress : null
                found.addressUnknown = plain ? null :
                        "server.address of ${CONFIGURATION} is not plainly an address, but something the application works out when it starts".toString()
            }
        } catch (IOException | RuntimeException | StackOverflowError ignored) {
            found.clear()
            found.unknown = "${CONFIGURATION} could not be read as the YAML the command reads: at most ${MAX_CONFIGURATION} bytes, no key twice in one map, ".toString() +
                    "nothing nested deeper than ${MAX_NESTING}".toString()
        }
        return found
    }

    private static Object value(Object from, List<String> path) {
        Object each = from
        for (String step : path) {
            each = each instanceof Map ? ((Map) each).get(step) : null
        }
        return each
    }

    private static Integer portOf(String text) {
        if (text == null || !(text ==~ /[1-9][0-9]{0,4}/)) {
            return null
        }
        int number = Integer.parseInt(text)
        return number <= 65535 ? Integer.valueOf(number) : null
    }
}

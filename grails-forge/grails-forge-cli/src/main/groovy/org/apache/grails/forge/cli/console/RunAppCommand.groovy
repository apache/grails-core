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

import java.nio.charset.CharacterCodingException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Consumer
import java.util.function.Function
import groovy.transform.CompileStatic
import org.grails.config.yaml.YamlPropertySourceLoader
import org.grails.forge.application.OperatingSystem
import org.grails.forge.feature.FeatureSelectionException
import org.springframework.core.env.PropertySource
import org.springframework.core.io.ByteArrayResource
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.ConstructorException
import org.yaml.snakeyaml.constructor.DuplicateKeyException
import org.yaml.snakeyaml.constructor.SafeConstructor
import org.yaml.snakeyaml.error.Mark
import org.yaml.snakeyaml.error.MarkedYAMLException
import org.yaml.snakeyaml.error.YAMLException
import org.yaml.snakeyaml.reader.ReaderException
import picocli.CommandLine

/**
 * {@code run-app}, also {@code run} as in the Grails shell: the Gradle task {@code bootRun} of the
 * project, for the development environment unless another one is given.
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
@CommandLine.Command(name = 'run-app', aliases = ['run'],
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
class RunAppCommand extends GradleEnvironmentCommand implements OptionRelation.Declared, UsageHelp.ArgumentAdvice {

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
    private static final String BUILD_ADDRESS_PROPERTY = 'server.address'
    private static final String ENVIRONMENT_PROPERTY = 'grails.env'
    // the system property of the build that the Grails Gradle plugin hands the application as spring.profiles.active
    private static final String PROFILES_PROPERTY = 'grails.spring.profiles.active'
    // the settings of application.yml
    private static final String PORT_SETTING = 'server.port'
    private static final String ADDRESS_SETTING = 'server.address'
    private static final String SYSTEM_PROPERTY = '-D'
    private static final String SYSTEM_PROPERTY_LONG = '--system-prop'
    // the option of the task bootRun, of the JavaExec of Gradle, that gives the application its arguments
    private static final String APPLICATION_ARGUMENTS = '--args'
    // what a file the Grails shell ran as a script ends with, as in grails run app.groovy
    private static final String SCRIPT_ENDING = '.groovy'
    private static final String LOG = RunDirectory.LOCATION + '/' + RunningApplication.LOG
    private static final Map<String, String> ENVIRONMENTS = [dev: 'development', prod: 'production']
    // a lambda, and no closure made into a function when the class is loaded: that goes through what Groovy
    // makes a text of any object with, which costs every start of the command line some 70 ms
    private static final Function<String, String> ENVIRONMENT = (String name) -> System.getenv(name)

    private static final List<InShell> STARTED_IN_SHELL = new CopyOnWriteArrayList<InShell>()
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

    @Override
    List<OptionRelation> getOptionRelations() {
        // see check
        return [OptionRelation.exclusive(false, '--detach', '--foreground')]
    }

    /**
     * A Groovy script, which {@code grails run FILE.groovy} ran in the Grails shell, is given to
     * {@code run-script}: the refusal of the file as an argument of this command says so, with the file
     * where it is typed as it is on the command line of a shell, and with {@code FILE} where it is not.
     */
    @Override
    String adviceFor(String argument) {
        if (argument == null || !argument.endsWith(SCRIPT_ENDING)) {
            return null
        }
        String file = argument ==~ /[A-Za-z0-9._\/-]{1,200}/ ? argument : 'FILE'
        return 'A Groovy script is run in a Grails project with run-script, with the application started around it. ' +
                "Type: ${UsageHelp.toType(spec, "run-script ${file}")}"
    }

    @CommandLine.Option(names = ['--ready-timeout'], paramLabel = 'SECONDS', defaultValue = '300',
            description = 'How long the application is given to answer when the command does not stay with it; it is stopped when it has not by then. Default: ${DEFAULT-VALUE}.')
    int readyTimeout

    /**
     * Stops the applications that {@code run-app} started in the interactive shell without
     * {@code --detach}, which belong to the session of the shell. The shell does it when it is left,
     * before it returns, also where the JVM goes on after it, as that of a program that runs the
     * command line does; and the JVM does it when it ends while the shell runs. An application that
     * {@code run-app --detach} started is not among them and goes on.
     *
     * <p>It ends them as {@code stop-app} does: the Gradle that was started, with every process below
     * it, and then it waits, for a while at most, until nothing answers on the port of each of them any
     * more. A Gradle daemon that runs the build is no process below that Gradle; it cancels the build
     * when the Gradle it worked for is gone, and the application ends then, which is seen at its port
     * alone. So what runs the shell finds the port free when the shell has returned, also where the
     * application takes a while to shut down.</p>
     */
    static void stopWhatTheShellStarted() {
        Thread hook
        List<InShell> started
        synchronized (STARTED_IN_SHELL) {
            hook = stopsWithJvm
            stopsWithJvm = null
            started = new ArrayList<InShell>(STARTED_IN_SHELL)
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
        List<InShell> stopped = started.findAll { InShell each -> each.build.isAlive() }
        stopped.each { InShell each -> GradleRunner.stop(each.build, STOP_SECONDS) }
        stopped.each { InShell each ->
            if (!StopAppCommand.closed(each.port, each.address)) {
                System.err.println(CommandLine.Help.Ansi.AUTO.string('@|bold,red | Warning|@ ' +
                        "The shell ended the build of the application that run-app started in it for port ${each.port}, " +
                        "but something answers on that port still after ${StopAppCommand.PORT_SECONDS} seconds: the application may take longer to end, or it is another one"))
            }
        }
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
        List<String> arguments = new ArrayList<String>(gradleArguments(project))
        // the system properties of the build as Gradle reads them, from the same arguments: what follows -- among them
        Map<String, String> told = systemPropertiesOf(arguments + passedOn)
        checkTold(detached, told)
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
        Configured configured = configured(project, told)
        int answersOn = configured.port
        if (detached && (configured.portUnknown != null || configured.addressUnknown != null)) {
            err(undetermined(configured))
            return 1
        }
        if (https) {
            if (answersOn == 0 && configured.portUnknown == null) {
                answersOn = DEFAULT_HTTPS_PORT
                arguments.add("-D${PORT_PROPERTY}=${DEFAULT_HTTPS_PORT}".toString())
            }
            arguments.addAll(certificateArguments(project, told))
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
                    stayWith(project, windows, directory, arguments, answersOn, configured.address)
        } catch (GradleRunner.RefusedArgumentException e) {
            throw new CommandLine.ParameterException(spec.commandLine(), e.getMessage())
        }
    }

    // Why nothing is started when the command does not stay with the application: what of the port and the
    // address is not known, why, and what to give for it. An option that was given is not asked for again:
    // what it names is not known all the same when a system property after -- names it otherwise, which the
    // reason says.
    private String undetermined(Configured configured) {
        List<String> unknown = []
        List<String> reasons = []
        List<String> asked = []
        if (configured.portUnknown != null) {
            unknown << 'port'
            reasons << configured.portUnknown
            if (port == null) {
                asked << 'the port with --port'
            }
        }
        if (configured.addressUnknown != null) {
            unknown << 'address'
            if (!reasons.contains(configured.addressUnknown)) {
                reasons << configured.addressUnknown
            }
            if (host == null) {
                asked << 'the address with --host'
            }
        }
        return "The ${unknown.join(' and the ')} the application answers on could not be determined: ${reasons.join('; ')}. Nothing was started; ".toString() +
                (asked.isEmpty() ? '' : "give ${asked.join(' and ')}, or ".toString()) + 'stay with the application with --foreground'
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
    }

    // What the build is told, after -- as well, that the command cannot wait on when it does not stay with the application.
    private void checkTold(boolean detached, Map<String, String> told) {
        if (!detached) {
            return
        }
        if (!told.containsKey(PORT_PROPERTY) && told.containsKey(BUILD_PORT_PROPERTY)) {
            throw new CommandLine.ParameterException(spec.commandLine(),
                    "-D${BUILD_PORT_PROPERTY} is a system property of the build, which the Grails Gradle plugin does not give to the application, so the command cannot tell " +
                            "which port the application answers on: give --port PORT or -D${PORT_PROPERTY}=PORT, which the application is given")
        }
        // the arguments of the application itself, which Spring Boot reads before any system property
        String arguments = applicationArguments(passedOn)
        if (arguments != null && (arguments.contains(BUILD_PORT_PROPERTY) || arguments.contains(BUILD_ADDRESS_PROPERTY))) {
            throw new CommandLine.ParameterException(spec.commandLine(),
                    "${APPLICATION_ARGUMENTS} gives the application arguments that name its port or its address, which it reads before what the command tells it, " +
                            'so the command cannot tell where the application answers: give --port PORT and --host HOST instead, or stay with the application with --foreground')
        }
    }

    /**
     * The system properties a build is given by its arguments, as Gradle reads them: {@code -Dname=value},
     * {@code -D name=value}, {@code --system-prop name=value} and {@code --system-prop=name=value}, and a
     * name without {@code =} for an empty value. Of a name that is given more than once the last counts,
     * as in Gradle.
     *
     * @param arguments the arguments of Gradle
     * @return the properties, by their names
     */
    static Map<String, String> systemPropertiesOf(List<String> arguments) {
        Map<String, String> properties = new LinkedHashMap<String, String>()
        for (int i = 0; i < arguments.size(); i++) {
            String argument = arguments.get(i)
            String given = null
            if (argument == SYSTEM_PROPERTY || argument == SYSTEM_PROPERTY_LONG) {
                given = i + 1 < arguments.size() ? arguments.get(++i) : null
            } else if (argument.startsWith(SYSTEM_PROPERTY_LONG + '=')) {
                given = argument.substring(SYSTEM_PROPERTY_LONG.length() + 1)
            } else if (argument.startsWith(SYSTEM_PROPERTY)) {
                given = argument.substring(SYSTEM_PROPERTY.length())
            }
            if (given != null && !given.isEmpty()) {
                int assigned = given.indexOf('=')
                properties.put(assigned < 0 ? given : given.substring(0, assigned), assigned < 0 ? '' : given.substring(assigned + 1))
            }
        }
        return properties
    }

    // the value of the option of the task bootRun that gives the application its arguments, the last one; null for none
    private static String applicationArguments(List<String> arguments) {
        String given = null
        for (int i = 0; i < arguments.size(); i++) {
            String argument = arguments.get(i)
            if (argument == APPLICATION_ARGUMENTS) {
                given = i + 1 < arguments.size() ? arguments.get(++i) : given
            } else if (argument.startsWith(APPLICATION_ARGUMENTS + '=')) {
                given = argument.substring(APPLICATION_ARGUMENTS.length() + 1)
            }
        }
        return given
    }

    // The command stays with Gradle, which stays with the application. The record lets stop-app, from
    // another terminal, end them; what Gradle then ends with is the end that was asked for. The record
    // and what stop-app said are this run's only while they name the process this run recorded: by the
    // time Gradle has ended, another run-app can have started and recorded another one. Where the build
    // is run from this JVM there is no process for stop-app to end: the record names this command, which
    // looks for what stop-app says while the build runs, and cancels the build itself.
    private int stayWith(ConsoleProject project, boolean windows, Path directory, List<String> arguments, int answersOn, String address) throws GradleRunner.RefusedArgumentException {
        List<String> failures = []
        // set where the build is told to have started, which can be a thread of the runner, and read here after it
        AtomicReference<long[]> recorded = new AtomicReference<long[]>([0L, 0L] as long[])
        AtomicBoolean asked = new AtomicBoolean()
        AtomicReference<Thread> looking = new AtomicReference<Thread>()
        GradleBuild runner = GradleBuild.of(GradleBuild.Purpose.BUILD, { String message -> failures.add(message) } as Consumer<String>, windows)
        out('| Running application...')
        int exit = runner.run(project, arguments, { GradleBuild.Running started ->
            try {
                // the instant as it was recorded: of a process that has ended by now the system says none
                long instant = RunningApplication.record(directory, started.process, answersOn, false, started.commandLine, ApplicationPort.boundLoopback(address))
                long pid = started.process.pid()
                recorded.set([pid, instant] as long[])
                if (started.commandLine) {
                    looking.set(lookForStop(directory, pid, instant, asked, started))
                }
            } catch (IOException e) {
                // a build that has ended by now is none to record, and says itself how it ended
                if (started.process.isAlive()) {
                    warning("${GradleRunner.line(e.getMessage())}. The application runs, and stop-app will not find it: end this command to stop it")
                }
            }
        } as Consumer<GradleBuild.Running>)
        looking.get()?.interrupt()
        long[] record = recorded.get()
        if (record[0] != 0L && RunningApplication.wasAskedToStop(directory, record[0], record[1])) {
            asked.set(true)
        }
        if (record[0] != 0L) {
            RunningApplication.forget(directory, record[0], record[1])
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
        if (ApplicationPort.isOpen(answersOn, address)) {
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
        // what the hook reads is set on the thread of the command: through atomics, so that the hook sees it as it was set
        AtomicBoolean ending = new AtomicBoolean()
        AtomicReference<ProcessHandle> build = new AtomicReference<ProcessHandle>()
        AtomicReference<long[]> recorded = new AtomicReference<long[]>([0L, 0L] as long[])
        Thread whenEnded = new Thread({
            ending.set(true)
            ProcessHandle left = build.get()
            if (left != null) {
                err('The application was stopped before it answered, because the command was ended')
                GradleRunner.stop(left, STOP_SECONDS)
                long[] record = recorded.get()
                RunningApplication.forget(directory, record[0], record[1])
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
            build.set(started)
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
                // the pid and the instant are set together: the hook never sees the one without the other
                long instant = RunningApplication.record(directory, started, answersOn, true, false, ApplicationPort.boundLoopback(address))
                recorded.set([started.pid(), instant] as long[])
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
            String ended = waitFor(started, answersOn, address, ending)
            if (ending.get()) {
                // the hook says it, ends the build and removes the record
                return 1
            }
            if (ended != null) {
                long[] record = recorded.get()
                RunningApplication.forget(directory, record[0], record[1])
                err(ended)
                said(log)
                return 1
            }
            boolean withShell = ConsoleCommands.isInteractive() && !detach
            if (withShell) {
                stopWithShell(new InShell(started, answersOn, address))
            }
            out("| Application answers on port ${answersOn}: ${https ? 'https' : 'http'}://${ApplicationPort.urlHost(address)}:${answersOn}. " +
                    "It runs until stop-app${withShell ? ', or until the shell ends' : ''}")
            return 0
        } finally {
            // once it answers, or has been ended, the build is no longer the hook's
            build.set(null)
            try {
                Runtime.getRuntime().removeShutdownHook(whenEnded)
            } catch (IllegalStateException ignored) {
                // the JVM is shutting down, and the hook runs
            }
        }
    }

    // null when the application answers; otherwise why it does not, after what was started has ended
    private String waitFor(ProcessHandle started, int answersOn, String address, AtomicBoolean ending) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(readyTimeout)
        ApplicationPort.Start start = ApplicationPort.start(answersOn, address)
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
                boolean accepts = ApplicationPort.isOpen(answersOn, address)
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

    private static void stopWithShell(InShell started) {
        synchronized (STARTED_IN_SHELL) {
            STARTED_IN_SHELL.add(started)
            if (stopsWithJvm == null) {
                Thread hook = new Thread({ stopWhatTheShellStarted() } as Runnable, 'grails-run-app-stop')
                Runtime.getRuntime().addShutdownHook(hook)
                stopsWithJvm = hook
            }
        }
    }

    // The certificate the shell creates for --https, in the place it has it, so one that is there is used. A key
    // store that the build is told of, also after --, is the user's, whose passwords are the application's to know:
    // with it nothing is created, and no password is given that would stand above those of its configuration.
    private List<String> certificateArguments(ConsoleProject project, Map<String, String> told) {
        if (told.containsKey(KEY_STORE_PROPERTY)) {
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

    /** An application that {@code run-app} started in the interactive shell, which ends with the shell. */
    private static final class InShell {

        /** The Gradle that was started, which is ended. */
        final ProcessHandle build

        /** The port the application answered on, which is free when the application has ended. */
        final int port

        /**
         * The address the application binds to as the command knows it (--host, -Dgrails.server.address,
         * SERVER_ADDRESS or server.address); {@code null} where nothing names one.
         */
        final String address

        InShell(ProcessHandle build, int port, String address) {
            this.build = build
            this.port = port
            this.address = address
        }
    }

    /**
     * {@code application.yml} as the application reads it: by the loader of its runtime, for the
     * environment and the active profiles it is started with, and not those of this JVM. The YAML is
     * parsed within the limits of the command, by a parser that makes no object of a tag.
     */
    private static final class ApplicationYaml extends YamlPropertySourceLoader {

        private final String environment
        private final String profiles

        ApplicationYaml(String environment, String profiles) {
            this.environment = environment
            this.profiles = profiles
        }

        @Override
        protected Yaml createYaml() {
            LoaderOptions options = new LoaderOptions()
            options.setAllowDuplicateKeys(false)
            options.setMaxAliasesForCollections(MAX_ALIASES)
            options.setNestingDepthLimit(MAX_NESTING)
            options.setCodePointLimit(MAX_CONFIGURATION)
            options.setProcessComments(false)
            return new Yaml(new SafeConstructor(options))
        }

        @Override
        protected String environmentName() {
            return environment
        }

        @Override
        protected String activeProfiles() {
            return profiles
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
    // server.port), then the environment the build runs it in, then its application.yml, read as the
    // application reads it, see ofFile. What names a port and is not plainly one, as a placeholder that
    // the application resolves when it starts, is not guessed at. The system properties are those the
    // build is told, by the options of the command and by what follows --, as Gradle reads them.
    private Configured configured(ConsoleProject project, Map<String, String> told) {
        Configured found = new Configured()
        Map<String, Object> inFile = null
        String given = told.get(PORT_PROPERTY)
        String variable = environmentVariables.apply(PORT_VARIABLE)
        if (given != null) {
            found.port = portOf(given) ?: 0
            found.portUnknown = found.port == 0 ? "-D${PORT_PROPERTY} is given, and its value is not a port, a number from 1 to 65535" : null
        } else if (variable != null && !variable.isEmpty()) {
            found.port = portOf(variable) ?: 0
            found.portUnknown = found.port == 0 ? "the environment variable ${PORT_VARIABLE} is set, and its value is not a port, a number from 1 to 65535" : null
        } else {
            inFile = ofFile(project, told)
            found.port = (inFile.port ?: 0) as int
            found.portUnknown = inFile.portUnknown as String ?: inFile.unknown as String
        }
        String address = told.get(ADDRESS_PROPERTY) ?: environmentVariables.apply(ADDRESS_VARIABLE)
        if (address != null && !address.isEmpty()) {
            found.address = address
        } else {
            inFile = inFile != null ? inFile : ofFile(project, told)
            found.address = inFile.address as String
            found.addressUnknown = inFile.addressUnknown as String ?: inFile.unknown as String
        }
        return found
    }

    // port, address: what application.yml names plainly; portUnknown, addressUnknown: why one that is named
    // is not known; unknown: why nothing of the file is. Every reason is the command's own words: nothing
    // of the file, and nothing a parser says of it, is repeated, but for the line and the column where it
    // stopped. A limit of the command is named where the file goes past it, and only there.
    private Map<String, Object> ofFile(ConsoleProject project, Map<String, String> told) {
        Map<String, Object> found = [:]
        Path file = project.directory.resolve(CONFIGURATION)
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
            return found
        }
        String text = ''
        try {
            if (project.isLinked(CONFIGURATION) || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                found.unknown = "${CONFIGURATION} is not a file of the project, or is reached through a link, and is not read".toString()
                return found
            }
            if (Files.size(file) > MAX_CONFIGURATION) {
                found.unknown = "${CONFIGURATION} is larger than ${MAX_CONFIGURATION} bytes, and is not read".toString()
                return found
            }
            // the environment of the project, the one this command gives its build (-Dgrails.env, also after
            // --, or development), and not the one of Forge, which is production whatever the project's is
            String named = told.get(ENVIRONMENT_PROPERTY) ?: environment()
            String environment = ENVIRONMENTS.get(named) ?: named
            text = Files.readString(file)
            // Read as the application reads it, by the same loader: each document with what it says for the environment
            // over what it says for all, and a later document over an earlier one; a document for a Spring profile only
            // where the profiles the application is given (-Dgrails.spring.profiles.active, which the Grails Gradle
            // plugin hands it as spring.profiles.active) select it. A value that is empty names nothing, as for the
            // application, which takes no port and no address from it.
            List<PropertySource<?>> read = new ApplicationYaml(environment, told.get(PROFILES_PROPERTY))
                    .load(CONFIGURATION, new ByteArrayResource(text.getBytes(StandardCharsets.UTF_8)))
            Map<?, ?> settings = read.isEmpty() ? [:] : (Map<?, ?>) read.get(0).getSource()
            Object namedPort = settings.get(PORT_SETTING)
            Object namedAddress = settings.get(ADDRESS_SETTING)
            if (namedPort != null && namedPort != '') {
                Integer plain = namedPort instanceof Integer || namedPort instanceof String ? portOf(namedPort.toString()) : null
                found.port = plain
                found.portUnknown = plain != null ? null :
                        "server.port of ${CONFIGURATION} is not plainly a port, a number from 1 to 65535, but something the application works out when it starts".toString()
            }
            if (namedAddress != null && namedAddress != '') {
                boolean plain = namedAddress instanceof String && namedAddress ==~ /[A-Za-z0-9.:\[\]-]{1,255}/
                found.address = plain ? namedAddress : null
                found.addressUnknown = plain ? null :
                        "server.address of ${CONFIGURATION} is not plainly an address, but something the application works out when it starts".toString()
            }
        } catch (CharacterCodingException ignored) {
            found.clear()
            found.unknown = "${CONFIGURATION} is not text in UTF-8, which is what the command reads".toString()
        } catch (IOException ignored) {
            found.clear()
            found.unknown = "${CONFIGURATION} could not be read".toString()
        } catch (YAMLException e) {
            found.clear()
            found.unknown = notRead(e, text)
        } catch (RuntimeException | StackOverflowError ignored) {
            found.clear()
            found.unknown = "${CONFIGURATION} could not be read as YAML".toString()
        }
        return found
    }

    // Why the YAML of the file is not read: where the parser stopped, or the limit of the command the file goes past
    private static String notRead(YAMLException e, String text) {
        if (e instanceof DuplicateKeyException) {
            return "${CONFIGURATION} has a key twice in one map${at(((MarkedYAMLException) e).getProblemMark())}, which YAML does not allow".toString()
        }
        if (e instanceof ConstructorException) {
            return "${CONFIGURATION} has a value${at(((MarkedYAMLException) e).getProblemMark())} that the command cannot read as YAML data, ".toString() +
                    'such as one with a tag it does not know'
        }
        if (e instanceof MarkedYAMLException) {
            MarkedYAMLException marked = (MarkedYAMLException) e
            return "${CONFIGURATION} is not valid YAML${at(marked.getProblemMark() ?: marked.getContextMark())}".toString()
        }
        if (e instanceof ReaderException) {
            return "${CONFIGURATION} is not valid YAML${at(text, ((ReaderException) e).getPosition())}, where it has a character YAML does not allow".toString()
        }
        // the limits of the parser, which it tells by its words alone
        String said = e.getMessage() ?: ''
        if (said.startsWith('Nesting Depth exceeded')) {
            return "${CONFIGURATION} nests lists and maps deeper than ${MAX_NESTING} levels, more than the command reads".toString()
        }
        if (said.startsWith('Number of aliases')) {
            return "${CONFIGURATION} refers to a list or a map by an alias more than ${MAX_ALIASES} times, more than the command reads".toString()
        }
        return "${CONFIGURATION} could not be read as YAML".toString()
    }

    private static String at(Mark mark) {
        return mark == null ? '' : " at line ${mark.getLine() + 1}, column ${mark.getColumn() + 1}".toString()
    }

    // The line and the column of a position in the text, which the parser counts in characters: a line ends
    // with a line feed, with a carriage return that no line feed follows, or with a line break of Unicode.
    private static String at(String text, int position) {
        int line = 1
        int column = 1
        int index = 0
        int i = 0
        while (i < text.length() && index < position) {
            int character = text.codePointAt(i)
            i += Character.charCount(character)
            index++
            boolean breaks = character == 0x0A || character == 0x85 || character == 0x2028 || character == 0x2029 ||
                    (character == 0x0D && (i >= text.length() || text.charAt(i) != (char) 0x0A))
            line += breaks ? 1 : 0
            column = breaks ? 1 : column + 1
        }
        return " at line ${line}, column ${column}".toString()
    }

    private static Integer portOf(String text) {
        if (text == null || !(text ==~ /[1-9][0-9]{0,4}/)) {
            return null
        }
        int number = Integer.parseInt(text)
        return number <= 65535 ? Integer.valueOf(number) : null
    }
}

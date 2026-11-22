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
import java.util.function.Consumer
import java.util.function.Supplier
import groovy.transform.CompileStatic

/**
 * How a command has Gradle run a build of the project and waits for it. A command says what it runs
 * Gradle for, a {@link Purpose}, and is given what runs it: which of the ways there are is used for
 * which purpose is decided in {@link #of} and nowhere else.
 *
 * <p>Whatever runs the build, it runs the Gradle of the project and no other, after the checks of
 * {@link ConsoleProject#gradleWrapper}; the arguments are a list from the command to Gradle, of which
 * nothing is joined or split; the exit code is 0 for a build that succeeded and 1 otherwise, after one
 * line that says why; and a build is cancelled when the thread that waits for it is interrupted, and
 * when the JVM of the command line ends.</p>
 *
 * @since 9.0
 */
@CompileStatic
abstract class GradleBuild {

    /** What a command of the application or a script reads to know that nothing may wait for what a user types. */
    public static final String NON_INTERACTIVE = 'GRAILS_NON_INTERACTIVE'

    /**
     * The environment variable that says how the commands run Gradle: {@link #PROCESS} for the Gradle
     * wrapper of the project as a process, every time; {@link #TOOLING_API}, or nothing, for what
     * {@link #of} decides.
     */
    public static final String RUNNER = 'GRAILS_GRADLE_RUNNER'

    /** The value of {@link #RUNNER} that has every command run the Gradle wrapper as a process. */
    public static final String PROCESS = 'process'

    /** The value of {@link #RUNNER} that says what is the case without it. */
    public static final String TOOLING_API = 'tooling-api'

    /** What Gradle is run for. */
    enum Purpose {

        /** A build, a test run or an application that the command line asks for itself and stays with. */
        BUILD,

        /** What the user typed for Gradle, which is Gradle's to read: {@code grails gradle ...}. */
        PASSED_ON,

        /** A build that is a session at the terminal, which reads what is typed until it is left: {@code shell}. */
        TERMINAL_SESSION
    }

    protected static final String STOPPED = 'Gradle was stopped before it finished, because the command was cancelled'

    private static final AtomicInteger RUNNING = new AtomicInteger()

    private static final Supplier<String> ENVIRONMENT = { System.getenv(RUNNER) } as Supplier<String>

    private static volatile Supplier<String> runner = ENVIRONMENT
    private static volatile boolean nonInteractive
    private static volatile PrintStream processOut
    private static volatile PrintStream processErr

    /**
     * The one place that decides what runs Gradle for a command that waits for it.
     *
     * <ul>
     * <li>A {@linkplain Purpose#BUILD build the command line asks for itself}, which is {@code compile},
     * {@code clean}, {@code assemble}, {@code war} and {@code package}, {@code test-app},
     * {@code dependency-report}, {@code package-plugin}, {@code publish-plugin}, {@code run-command}, a
     * command of the application by its name, {@code run-script} and a script by its name,
     * {@code console} and a {@code run-app} that stays with the application, is run through the
     * Tooling API of Gradle, see {@link ToolingApiRunner}.</li>
     * <li>{@linkplain Purpose#PASSED_ON What the user typed for Gradle}, {@code grails gradle ...}, is
     * run by the Gradle wrapper of the project as a process, see {@link GradleRunner}: it is to be to
     * Gradle what it is on a command line of its own, and the Tooling API does not take what the
     * program {@code gradle} reads itself, as {@code --stop}, {@code --status} or {@code --no-daemon}.</li>
     * <li>{@linkplain Purpose#TERMINAL_SESSION A session at the terminal}, {@code shell}, is run by the
     * wrapper as a process too. A process is given the terminal itself, and ends with all that read it.
     * The Tooling API is given a stream, which it reads from as soon as a build starts, whether the
     * build reads or not, in a thread that goes on waiting for what is typed when the build has ended:
     * in the interactive console that thread would take the next line from the prompt.</li>
     * <li>Where the environment variable {@link #RUNNER} is {@code process}, the wrapper is run as a
     * process for every purpose: the way out where a build does not run through the Tooling API.</li>
     * </ul>
     *
     * <p>A build that is left running is no purpose here: see {@link #leftRunning}.</p>
     *
     * @param purpose what the command runs Gradle for
     * @param failure given the one line that says why the command failed, when it did
     * @param windows whether the project is built on Windows
     * @return what runs the build
     */
    static GradleBuild of(Purpose purpose, Consumer<String> failure, boolean windows) {
        GradleRunner wrapper = new GradleRunner(failure, windows)
        String asked = runner.get()
        if (asked != null && !asked.trim().isEmpty() && !(asked.trim() in [PROCESS, TOOLING_API])) {
            return new GradleBuild() {

                @Override
                int run(ConsoleProject project, List<String> arguments, Consumer<GradleBuild.Running> started) {
                    failure.accept("The environment variable ${RUNNER} says how Gradle is run, and is ${PROCESS} or ${TOOLING_API}: ".toString() +
                            "${GradleRunner.line(asked.trim())} is neither. Gradle was not run".toString())
                    return 1
                }
            }
        }
        if (purpose != Purpose.BUILD || asked?.trim() == PROCESS) {
            return wrapper
        }
        return new ToolingApiRunner(failure, windows, wrapper)
    }

    /**
     * Replaces what the environment says of how Gradle is run, for a specification, which cannot
     * change the environment it runs in.
     *
     * @param value what {@link #RUNNER} is taken to be, empty for nothing; {@code null} for what the
     * environment says
     */
    static void setRunner(String value) {
        runner = value == null ? ENVIRONMENT : ({ value } as Supplier<String>)
    }

    /**
     * What starts a build that goes on when the command has ended, and when the command line has:
     * {@code run-app --detach}. Such a build is a process of its own, which the Gradle wrapper of the
     * project is; nothing the JVM of the command line holds can outlive it.
     *
     * @param failure given the one line that says why the build was not started
     * @param windows whether the project is built on Windows
     * @return what starts the build
     */
    static GradleRunner leftRunning(Consumer<String> failure, boolean windows) {
        return new GradleRunner(failure, windows)
    }

    /**
     * Says whether nothing may wait for what a user types, as {@code --non-interactive} asks: Gradle
     * is then not given the input of the CLI but an input that has ended, and what it runs is told
     * through the environment variable {@link #NON_INTERACTIVE}.
     *
     * @param asked whether the command line asked for it
     */
    static void setNonInteractive(boolean asked) {
        nonInteractive = asked
    }

    /**
     * @return whether nothing may wait for what a user types
     */
    static boolean isNonInteractive() {
        return nonInteractive
    }

    /**
     * Tells which streams are the output the process was started with. While they are still the output
     * of the CLI, Gradle writes to the terminal there is, as one; called once, when the CLI is started as a program.
     *
     * @param out the standard output of the process
     * @param err the standard error of the process
     */
    static void outputOfProcess(PrintStream out, PrintStream err) {
        processOut = out
        processErr = err
    }

    /**
     * @return whether the command line was started as a program, whose JVM ends with it, and not in
     * the JVM of a caller, which goes on
     */
    protected static boolean isProgram() {
        return processOut != null
    }

    /**
     * @return whether the output of the command line is, now, the output the process was started with
     */
    protected static boolean isOutputOfProcess() {
        return System.out.is(processOut) && System.err.is(processErr)
    }

    /**
     * @return whether a command of this JVM waits for Gradle now, and so can be cancelled by
     * interrupting its thread. A build that was started to be left running is not waited for and is none:
     * it is not this command's to cancel, and goes on when the command is interrupted or the CLI ends
     */
    static boolean isRunning() {
        return RUNNING.get() > 0
    }

    /**
     * Closes what was kept open between the builds of the interactive console, which is the
     * connection to the Gradle of the project. The console does it when it is left.
     */
    static void closeConnections() {
        ToolingApiRunner.closeConnections()
    }

    /** Called by what runs a build when a command begins to wait for it. */
    protected static void waiting() {
        RUNNING.incrementAndGet()
    }

    /** Called by what runs a build when the command no longer waits for it. */
    protected static void waited() {
        RUNNING.decrementAndGet()
    }

    /**
     * Runs Gradle in the directory of the project and waits for it.
     *
     * @param project the project
     * @param arguments the arguments of Gradle, each given to it as the one argument it is
     * @return 0 when the build succeeded, and 1 otherwise, after the line that says why
     * @throws GradleRunner.RefusedArgumentException for an argument that cannot be given to Gradle unchanged; nothing was run
     */
    int run(ConsoleProject project, List<String> arguments) throws GradleRunner.RefusedArgumentException {
        return run(project, arguments, null)
    }

    /**
     * Runs Gradle in the directory of the project and waits for it.
     *
     * @param project the project
     * @param arguments the arguments of Gradle, each given to it as the one argument it is
     * @param started given the build that was started, as soon as it is; can be {@code null}
     * @return 0 when the build succeeded, and 1 otherwise, after the line that says why
     * @throws GradleRunner.RefusedArgumentException for an argument that cannot be given to Gradle unchanged; nothing was run
     */
    abstract int run(ConsoleProject project, List<String> arguments, Consumer<Running> started) throws GradleRunner.RefusedArgumentException

    /** A build that was started and is waited for. */
    static final class Running {

        /**
         * The process that ends with the build and that the build ends with: the Gradle that was
         * started for it, or the command line itself where the build is run from its own JVM.
         */
        final ProcessHandle process

        /** Whether {@link #process} is the command line itself. */
        final boolean commandLine

        private final Runnable cancel

        Running(ProcessHandle process, boolean commandLine, Runnable cancel) {
            this.process = process
            this.commandLine = commandLine
            this.cancel = cancel
        }

        /**
         * Asks for the end of the build. The command that waits for it is then told, as it is of every
         * other end the build has.
         */
        void cancel() {
            cancel.run()
        }
    }
}

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

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Consumer
import java.util.function.Supplier
import java.util.regex.Matcher
import java.util.regex.Pattern
import groovy.transform.CompileStatic
import org.apache.grails.gradle.model.cli.JvmOptions

/**
 * Starts a command that runs as a process of its own, without Gradle, and waits for it: a plain JVM
 * with the class path the build of the project wrote down for the command
 * ({@link StandaloneToolsFile}), whose main class, shipped with Grails, finds the command by its
 * name and runs it without the application.
 *
 * <p>The JVM is the one the Gradle wrapper of the project would be started with: the one the
 * environment variable {@code JAVA_HOME} names, and without it the one that runs the command line.
 * The class path is given to it in a file only the user can read, since it is longer than a command
 * line of Windows can be, and so are the arguments of the command, as they are for a command the
 * build runs. The process is given the input and the output of the command line, or of the command that
 * runs it where that writes to streams of its own ({@link CommandStreams#current()}), is ended with
 * every process it started when the command is cancelled or the command line ends, and its exit code
 * is that of the command: 0 when the command succeeded.</p>
 *
 * @since 9.0
 */
@CompileStatic
final class StandaloneToolProcess {

    /** The class the JVM is started with, of {@code grails-core-cli}. */
    public static final String MAIN_CLASS = 'org.apache.grails.core.cli.StandaloneToolRunner'

    /** What the main class reads the arguments of the command from; the rest of the argument is the path of the file. */
    public static final String ARGUMENTS = '--grails-args-file='

    /** The system property that says which of the diagnostics of SLF4J itself it writes. */
    static final String SLF4J_VERBOSITY = 'slf4j.internal.verbosity'

    private static final String JAVA_HOME = 'JAVA_HOME'
    private static final String ARGUMENTS_OPTION = '--args-file='
    private static final int STOP_SECONDS = 10
    private static final String STOPPED = 'The command was stopped before it finished, because it was cancelled'
    private static final int OUTPUT_SECONDS = 10
    private static final Pattern VERSION = Pattern.compile(/(?m)^JAVA_VERSION="?(\d+)(?:\.(\d+))?/)
    private static final int MAX_RELEASE = 64 * 1024
    private static final Supplier<String> ENVIRONMENT = { System.getenv(JAVA_HOME) } as Supplier<String>

    private static volatile Supplier<String> javaHome = ENVIRONMENT

    private StandaloneToolProcess() {
    }

    /**
     * Replaces what the environment says of {@code JAVA_HOME}, for a specification, which cannot
     * change the environment it runs in.
     *
     * @param value what {@code JAVA_HOME} is taken to be, empty for nothing; {@code null} for what the environment says
     */
    static void setJavaHome(String value) {
        javaHome = value == null ? ENVIRONMENT : ({ value } as Supplier<String>)
    }

    /** The JVM a command is started with, or why there is none. */
    static final class Java {

        /** The program, or {@code null} when there is none that is run. */
        final String program

        /** Why no JVM is started, as one line; {@code null} when one is. */
        final String refusal

        /** The version of the JVM as its {@code release} file says it, 0 when it is not known. */
        final int version

        private Java(String program, String refusal, int version = 0) {
            this.program = program
            this.refusal = refusal
            this.version = version
        }

        /**
         * The options of {@link JvmOptions} for the version of the JVM, which the Grails Gradle plugin gives
         * the processes of a build too: from Java 22 on, native access, without which a restricted method
         * JLine calls has Java warn on the standard error. A Java whose version is not known is given
         * nothing it might not take.
         *
         * @return the options of the JVM that come before the class path
         */
        List<String> getOptions() {
            return JvmOptions.of(version)
        }
    }

    /**
     * Chooses the JVM as the Gradle wrapper of the project chooses its own: {@code JAVA_HOME} when it
     * is set, and otherwise the JVM that runs the command line, which is the {@code java} that was found
     * without it. A JVM that is older than the classes of the project were compiled for is not started.
     *
     * @param windows whether the command runs on Windows
     * @param needed the version of Java the classes of the project are compiled for, 0 when it is not known
     * @return the JVM, or why none is started
     */
    static Java java(boolean windows, int needed) {
        String program = windows ? 'java.exe' : 'java'
        String named = javaHome.get()
        Path home
        String from
        try {
            if (named != null && !named.trim().isEmpty()) {
                home = Path.of(named.trim())
                from = "${JAVA_HOME} (${GradleRunner.line(named.trim())})"
                if (!Files.isRegularFile(home.resolve('bin').resolve(program))) {
                    return new Java(null, "The environment variable ${from} names no Java: there is no bin/${program} in it")
                }
            } else {
                String own = System.getProperty('java.home')
                home = own == null ? null : Path.of(own)
                from = 'the Java that runs this command line'
                if (home == null || !Files.isRegularFile(home.resolve('bin').resolve(program))) {
                    // as the Gradle wrapper does without JAVA_HOME: the java of the PATH
                    return new Java(program, null)
                }
            }
        } catch (RuntimeException ignored) {
            return new Java(null, "The environment variable ${JAVA_HOME} names no directory")
        }
        int version = versionOf(home)
        if (needed > 0 && version > 0 && version < needed) {
            return new Java(null, "The classes of the project are compiled for Java ${needed}, and ${from} is Java ${version}: " +
                    "set ${JAVA_HOME} to a Java ${needed} or newer")
        }
        return new Java(home.resolve('bin').resolve(program).toString(), null, version)
    }

    /**
     * @param home the directory of a Java installation
     * @return its version as its {@code release} file says it, 8 for 1.8, or 0 when it does not say
     */
    static int versionOf(Path home) {
        try {
            Path release = home.resolve('release')
            if (!Files.isRegularFile(release) || Files.size(release) > MAX_RELEASE) {
                return 0
            }
            Matcher matcher = VERSION.matcher(new String(Files.readAllBytes(release), StandardCharsets.UTF_8))
            if (!matcher.find()) {
                return 0
            }
            int first = Integer.parseInt(matcher.group(1))
            return first == 1 && matcher.group(2) != null ? Integer.parseInt(matcher.group(2)) : first
        } catch (IOException | RuntimeException ignored) {
            return 0
        }
    }

    /**
     * Starts the command and waits for it.
     *
     * @param project the project
     * @param java the JVM, as {@link #java} chose it
     * @param classpath the class path of the command, each entry as the file or directory it is
     * @param arguments the file of the name and the arguments of the command
     * @param stacktrace whether the command is asked for the whole stack trace of what fails
     * @param windows whether the command runs on Windows
     * @param failure given the one line that says why the command failed, when it did
     * @param started given the process, as soon as there is one; can be {@code null}
     * @return 0 when the command succeeded, and 1 otherwise, after the line that says why
     */
    static int run(ConsoleProject project, Java java, List<Path> classpath, CommandArgumentsFile arguments, boolean stacktrace, boolean windows,
                   Consumer<String> failure, Consumer<ProcessHandle> started = null) {
        Path options
        try {
            options = RunDirectory.create(RunDirectory.of(project), "standalone-classpath-${ProcessHandle.current().pid()}.args".toString())
            Files.write(options, optionsFile(classpath).getBytes(StandardCharsets.UTF_8))
        } catch (IOException e) {
            failure.accept("The class path of the command could not be written for Java: ${GradleRunner.line(e.getMessage())}. The command was not run".toString())
            return 1
        }
        try {
            // The class path of a standalone tool has none of the application, and so no logging backend for SLF4J, which the
            // tool API logs through: SLF4J would say so in three lines of its own each time a tool runs. Its errors it still says.
            List<String> command = [java.program]
            command.addAll(java.options)
            command.addAll(["-Dgrails.env=${ToolCommand.ENVIRONMENT}".toString(), "-Dbase.dir=${project.getDirectory()}".toString(),
                            "-D${SLF4J_VERBOSITY}=ERROR".toString()])
            if (stacktrace) {
                command.add('-Dgrails.full.stacktrace=true')
            }
            if (PlainOutput.isOn()) {
                command.add('-Dgrails.console.enable.terminal=false')
            }
            if (GradleBuild.isNonInteractive()) {
                command.add('-Dgrails.console.enable.interactive=false')
            }
            command.add("@${options}".toString())
            command.add(MAIN_CLASS)
            command.add(ARGUMENTS + project.getDirectory().resolve(arguments.getOption().substring(ARGUMENTS_OPTION.length())))
            return waitFor(command, project.getDirectory(), windows, failure, started)
        } finally {
            try {
                Files.deleteIfExists(options)
            } catch (IOException ignored) {
                // it is in a directory nothing of is committed, and names files, not what is in them
            }
        }
    }

    /**
     * @param classpath the entries of a class path
     * @return what a file says that the {@code java} program reads its options from ({@code @file}):
     * the class path, between quotes, in which a backslash and a quote are written after a backslash
     */
    static String optionsFile(List<Path> classpath) {
        String joined = classpath.collect { Path entry -> entry.toString() }.join(File.pathSeparator)
        return '-cp\n"' + joined.replace('\\', '\\\\').replace('"', '\\"') + '"\n'
    }

    private static int waitFor(List<String> command, Path directory, boolean windows, Consumer<String> failure, Consumer<ProcessHandle> started) {
        // read once, on the thread of the command, and handed to the threads that copy what the command writes
        CommandStreams streams = CommandStreams.current()
        PrintStream out = streams.out
        PrintStream err = streams.err
        boolean inherited = GradleBuild.isOutputOfProcess()
        ProcessBuilder builder = new ProcessBuilder(command).directory(directory.toFile())
        out.flush()
        err.flush()
        if (inherited) {
            builder.inheritIO()
        } else {
            builder.redirectInput(ProcessBuilder.Redirect.INHERIT)
        }
        if (GradleBuild.isNonInteractive()) {
            // the command reads the end of its input at once, and is told why
            builder.redirectInput(ProcessBuilder.Redirect.from(new File(windows ? 'NUL' : '/dev/null')))
            builder.environment().put(GradleBuild.NON_INTERACTIVE, 'true')
        }
        AtomicBoolean stopped = new AtomicBoolean()
        AtomicBoolean said = new AtomicBoolean()
        // said once and by whoever comes first: this JVM can end before the thread of the command gets to say it
        Runnable sayStopped = {
            if (said.compareAndSet(false, true)) {
                failure.accept(STOPPED)
            }
        } as Runnable
        // When this JVM is ended while the command runs, the command is not left running without it; the line comes
        // before the wait for what does not end by itself, which is then ended by force. As for Gradle (GradleRunner),
        // the hook is there before the command is, and the command is started while the hook holds what it reads: a
        // JVM that is ended while the command is being started stops it once it has started, and one that is ended
        // before starts none. With the hook added after the start, a JVM ended in between ran no hook, and left the
        // command running.
        Object starting = new Object()
        AtomicBoolean ending = new AtomicBoolean()
        AtomicReference<Process> launched = new AtomicReference<Process>()
        Thread whenEnded = new Thread({
            Process running
            synchronized (starting) {
                ending.set(true)
                running = launched.get()
            }
            if (running != null) {
                stopped.set(true)
                sayStopped.run()
                GradleRunner.stop(running.toHandle(), STOP_SECONDS)
            }
        } as Runnable, 'grails-standalone-command-stop')
        try {
            Runtime.getRuntime().addShutdownHook(whenEnded)
        } catch (IllegalStateException ignored) {
            // this JVM is being ended already: the command is not started
            return 1
        }
        try {
            Process process
            synchronized (starting) {
                if (ending.get()) {
                    // the hook has run: this JVM is being ended, and the command is not started
                    return 1
                }
                try {
                    process = builder.start()
                } catch (IOException | IllegalArgumentException e) {
                    failure.accept("Java (${GradleRunner.line(command[0])}) could not be started for the command: ${GradleRunner.line(e.getMessage())}".toString())
                    return 1
                }
                launched.set(process)
            }
            if (started != null) {
                started.accept(process.toHandle())
            }
            return waitFor(process, inherited, out, err, stopped, sayStopped, failure)
        } finally {
            try {
                Runtime.getRuntime().removeShutdownHook(whenEnded)
            } catch (IllegalStateException ignored) {
                // the JVM is shutting down, and the hook runs
            }
        }
    }

    // waits for the command that was started, and says how it ended
    private static int waitFor(Process process, boolean inherited, PrintStream out, PrintStream err, AtomicBoolean stopped, Runnable sayStopped,
                               Consumer<String> failure) {
        // as while a command waits for Gradle: the interrupt of the console cancels the command, and not the console
        GradleBuild.waiting()
        Thread output = inherited ? null : copy(process.getInputStream(), out)
        Thread errors = inherited ? null : copy(process.getErrorStream(), err)
        try {
            int exit = process.waitFor()
            if (!inherited) {
                output.join(TimeUnit.SECONDS.toMillis(OUTPUT_SECONDS))
                errors.join(TimeUnit.SECONDS.toMillis(OUTPUT_SECONDS))
            }
            if (stopped.get() || (exit != 0 && Thread.currentThread().isInterrupted())) {
                sayStopped.run()
                return 1
            }
            if (exit != 0) {
                failure.accept("The command did not succeed: it ended with exit code ${exit}".toString())
                return 1
            }
            return 0
        } catch (InterruptedException ignored) {
            sayStopped.run()
            stopped.set(true)
            GradleRunner.stop(process.toHandle(), STOP_SECONDS)
            Thread.currentThread().interrupt()
            return 1
        } finally {
            GradleBuild.waited()
        }
    }

    // what the command writes is written as it comes, and as the bytes it is
    private static Thread copy(InputStream from, OutputStream to) {
        Thread thread = new Thread({
            try {
                byte[] buffer = new byte[8192]
                int count
                while ((count = from.read(buffer)) != -1) {
                    to.write(buffer, 0, count)
                    to.flush()
                }
            } catch (IOException ignored) {
                // the process ended, or was ended
            }
        } as Runnable, 'grails-standalone-command-output')
        thread.setDaemon(true)
        thread.start()
        return thread
    }
}

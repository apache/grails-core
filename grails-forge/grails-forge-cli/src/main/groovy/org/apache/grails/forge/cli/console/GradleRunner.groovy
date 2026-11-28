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
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Consumer
import groovy.transform.CompileStatic
import org.grails.forge.feature.FeatureSelectionException

/**
 * Runs the Gradle wrapper of a project as a process: the one place that builds its command line, shows
 * what it writes, turns the exit code of Gradle into the one of the command, and ends Gradle when the
 * command is cancelled. The arguments are a list from the command to Gradle; nothing is joined or split.
 *
 * <p>It is what runs Gradle where Gradle is to be what it is on a command line of its own, see
 * {@link GradleBuild#of}, and what starts a build that is left running, see {@link #start}.</p>
 *
 * <p>Gradle writes to the output of the CLI itself where that is the output the process was started
 * with, so that it sees the terminal there is, with its colours and its progress. Where the output
 * was replaced, as a specification does to read it, and where the command writes to streams of its own,
 * as each call of {@code grails mcp} does ({@link CommandStreams#current()}), the bytes Gradle writes are
 * copied there.</p>
 *
 * @since 9.0
 */
@CompileStatic
final class GradleRunner extends GradleBuild {

    // What cmd.exe reads as its own in the command line of a batch file. The JVM hands a batch file to
    // the system as it hands a program: the system starts cmd.exe /c with the whole command line, which
    // cmd.exe reads once, and the batch file gives its arguments to Java as %*, which cmd.exe reads again.
    // By default the JVM puts an argument between quotes only where it has white space, and takes nothing
    // away or out (jdk.lang.Process.allowAmbiguousCommands, which is true unless it is set to false), so
    // what must not reach cmd.exe is refused here:
    //   "      begins and ends what cmd.exe takes literally, and cannot stand in what it is to take so
    //   %      names a variable of the environment, which is put in its place, also between quotes
    //   & |    end a command and begin the next one
    //   < >    redirect what the command reads and writes
    //   ^      is taken out, and makes the character after it one that is not read
    //   !      names a variable where delayed expansion is on, which the registry can make it for every
    //          cmd.exe, and is taken out then; between quotes too
    // Not refused, since %* is the arguments as they were typed and Java splits them at white space alone:
    //   , ; =  divide the arguments a batch file numbers (%1, %2), which the wrapper of Gradle does not use,
    //          so that -Dname=value is one argument for Gradle
    //   ( )    open and close a block, and the wrapper of Gradle starts Java in none
    //   space  the JVM puts the argument between quotes, and cmd.exe and Java both keep it one argument
    private static final String NOT_FOR_A_BATCH_FILE = '"%&|<>^!'
    // The JVM puts the path of the batch file between quotes only where it has white space. Without them
    // cmd.exe ends the name of the command at any of these, and would start another program: C:\a for
    // C:\a,b\gradlew.bat. A block is looked for where a command begins, so a parenthesis is refused as well.
    private static final String NOT_IN_A_PATH_WITHOUT_QUOTES = ',;=()'
    private static final int STOP_SECONDS = 10
    /** What is said after an application was stopped where {@link #asksToEnd} is false. */
    static final String ENDED_AT_ONCE = 'Its processes were ended at once: on Windows an application is not asked to shut down, ' +
            'so what it does when it is asked (its shutdown hooks, the graceful shutdown of its server) was not done.'
    private static final int OUTPUT_SECONDS = 10
    private static final int FORCED_SECONDS = 5
    // $1 is the file the output is added to, the rest the command. A signal that is ignored stays ignored
    // in what is started. The command is to be in no process group of the terminal the CLI runs in, so
    // that the interrupt of that terminal is not sent to it, and there are three ways to that, in this order:
    // - setsid, where the system has the program, which Linux does and macOS does not: the command is in a
    //   session of its own, and so in a process group of its own, whatever the shell is. A shell without
    //   job control starts what it starts in the background as a process that leads no group, which is
    //   what setsid needs to become the command without starting another process, so that $! is the command.
    // - the job control of the shell: with it the command is in a process group of its own. bash, the sh of
    //   macOS, grants it without a terminal. dash, the sh of Debian and Ubuntu, grants it only with a
    //   terminal it can open, and says so; that is why it is not the first choice.
    // - neither: a shell without job control starts what it starts in the background with the interrupt
    //   and the quit of the terminal ignored, which the command inherits. It is then in the process group
    //   of the CLI and is sent the interrupt of its terminal, and ignores it.
    private static final String IN_THE_BACKGROUND = 'log=$1; shift; trap "" HUP; ' +
            'if command -v setsid >/dev/null 2>&1; then setsid "$@" </dev/null >>"$log" 2>&1 & ' +
            'else set -m 2>/dev/null; "$@" </dev/null >>"$log" 2>&1 & fi; echo $!'
    private static final String SHELL = '/bin/sh'
    private static final int SIGNAL_MILLIS = 1000
    private static final int SIGNAL_STEP_MILLIS = 50
    private static final int WINDOWS_CONTROL_C = -1073741510

    private static volatile String shell = SHELL
    private static volatile String shellPath
    private static volatile Boolean asked
    private static volatile Integer givenSeconds

    private final Consumer<String> failure
    private final boolean windows

    /**
     * @param failure given the one line that says why the command failed, when it did
     * @param windows whether the wrapper for Windows is run
     */
    GradleRunner(Consumer<String> failure, boolean windows) {
        this.failure = failure
        this.windows = windows
    }

    @Override
    int run(ConsoleProject project, List<String> arguments, Consumer<GradleBuild.Running> started) throws RefusedArgumentException {
        Path wrapper = wrapperFor(project, arguments)
        List<String> command = wrapper == null ? null : command(project, wrapper, arguments)
        return command == null ? 1 : run(command, wrapper, project.directory, started)
    }

    /**
     * Replaces the shell that starts a build which is left running, and the {@code PATH} it looks for
     * programs in, for a specification: which shell {@code /bin/sh} is, and whether there is a
     * {@code setsid}, is the system's to say.
     *
     * @param program the shell, {@code null} for {@code /bin/sh}
     * @param path the {@code PATH} of the shell and of what it starts, {@code null} for the one of the CLI
     */
    static void setShell(String program, String path) {
        shell = program == null ? SHELL : program
        shellPath = path
    }

    /**
     * @return whether a process that this command line ends is asked to end, and can do what it does
     * before it ends: so it is where the system has signals. On Windows there is no such request that
     * a program without a window receives from another process: a process is ended at once, as it is
     * by force, and neither it nor the application below it runs its shutdown hooks
     */
    static boolean asksToEnd() {
        Boolean replaced = asked
        return replaced != null ? replaced.booleanValue() : ProcessHandle.current().supportsNormalTermination()
    }

    /**
     * Replaces what the system says of how a process is ended, for a specification.
     *
     * @param asks what {@link #asksToEnd} is taken to be; {@code null} for what the system says
     */
    static void setAsksToEnd(Boolean asks) {
        asked = asks
    }

    /**
     * Replaces how long a process that is stopped ({@link #stop(ProcessHandle, int)}) is given to end by itself
     * before it is ended by force, whatever the caller gives it, for a specification: one of a process that does
     * not end when it is asked, which is to be ended by force at a moment the specification chooses and not at
     * one that races what it looks at.
     *
     * @param seconds the time a process is given; {@code null} for the one each caller gives
     */
    static void setStopSeconds(Integer seconds) {
        givenSeconds = seconds
    }

    /**
     * @param path a path, as the system writes it
     * @return whether it names a directory or a file of another computer by the name of that computer
     * and of the share (a UNC path, as {@code \\server\share\app}), and not through a drive letter
     */
    static boolean isOfAShare(String path) {
        return path.startsWith('\\\\') || path.startsWith('//')
    }

    /**
     * Starts Gradle in the directory of the project and does not wait for it: the build goes on when
     * the command has ended, and when the CLI has. It reads nothing, and what it writes is added to a
     * file. Its environment is the one a build the command waits for has: that of the CLI, and
     * {@link GradleBuild#NON_INTERACTIVE} with {@code --non-interactive}. Where the system has process
     * groups, the interrupt of the terminal the CLI runs in is not an interrupt of the build, and the
     * build is not ended with the terminal: it is in a session or a process group of its own where the
     * system has {@code setsid} or the shell grants job control, and ignores the interrupt where neither
     * is so.
     *
     * <p>On Windows the interrupt and the end of a console go to the processes that are attached to
     * it, and the build is attached to another one: none of the three streams of the process that is
     * started is that of the CLI, and the JVM starts such a process without a console window
     * ({@code CREATE_NO_WINDOW} in {@code ProcessImpl_md.c} of the JDK, for every process whose
     * streams are all redirected), which gives it a console of its own that has no window. So the
     * build is not sent Ctrl-C of the console the CLI runs in and does not end when that console is
     * closed, and nothing is shown. No other program is needed for it, and none is started.</p>
     *
     * @param project the project
     * @param arguments the arguments of Gradle, each given to it as the one argument it is
     * @param log the file the output of the build is added to
     * @return the process that was started, or {@code null} after the line that says why there is none
     * @throws RefusedArgumentException on Windows, for an argument a batch file does not receive unchanged; nothing was run
     */
    ProcessHandle start(ConsoleProject project, List<String> arguments, Path log) throws RefusedArgumentException {
        Path wrapper = wrapperFor(project, arguments)
        List<String> command = wrapper == null ? null : command(project, wrapper, arguments)
        if (command == null) {
            return null
        }
        ProcessBuilder builder
        if (windows) {
            // every stream is redirected, and has to be: with one of them inherited the JVM would start
            // the build in the console of the CLI, whose interrupt and whose end would then be its own
            builder = new ProcessBuilder(command)
                    .redirectInput(ProcessBuilder.Redirect.from(new File('NUL')))
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.appendTo(log.toFile()))
        } else {
            builder = new ProcessBuilder([shell, '-c', IN_THE_BACKGROUND, 'grails', log.toString()] + command)
                    .redirectInput(ProcessBuilder.Redirect.from(new File('/dev/null')))
                    .redirectError(ProcessBuilder.Redirect.appendTo(log.toFile()))
            if (shellPath != null) {
                builder.environment().put('PATH', shellPath)
            }
        }
        if (isNonInteractive()) {
            // it reads the end of its input as every build does, and is told why as every build is
            builder.environment().put(NON_INTERACTIVE, 'true')
        }
        builder.directory(project.directory.toFile())
        String said = ''
        try {
            Process process = builder.start()
            if (windows) {
                return process.toHandle()
            }
            // the shell says which process the build is and ends; the build is not its to wait for
            said = new String(process.getInputStream().readNBytes(64), StandardCharsets.US_ASCII).trim()
            process.waitFor()
            Optional<ProcessHandle> build = buildOf(said)
            if (build.isPresent()) {
                return build.get()
            }
            failed("The Gradle wrapper ${line(wrapper)} ended as soon as it was started")
            return null
        } catch (IOException | IllegalArgumentException e) {
            failed("The Gradle wrapper ${line(wrapper)} could not be started: ${line(e.getMessage())}")
            return null
        } catch (InterruptedException ignored) {
            // the command was cancelled once the shell had started the build: the build is not left running without it
            buildOf(said).ifPresent { ProcessHandle build -> stop(build, STOP_SECONDS) }
            Thread.currentThread().interrupt()
            failed(STOPPED)
            return null
        }
    }

    // the build the shell says it started, by its number
    private static Optional<ProcessHandle> buildOf(String said) {
        return said ==~ /[0-9]{1,10}/ ? ProcessHandle.of(Long.parseLong(said)) : Optional.<ProcessHandle>empty()
    }

    /**
     * Ends a process with every process it started in turn: first the processes below it, then the
     * process, and after a while by force whatever of them is left. Where a process cannot be asked to
     * end, see {@link #asksToEnd}, all of them are ended at once and there is nothing to wait for: the
     * processes below are found before any is ended, since a process whose parent has ended is below
     * nothing any more.
     *
     * @param process the process
     * @param seconds how long the processes are given to end by themselves
     * @return whether all of them have ended
     */
    static boolean stop(ProcessHandle process, int seconds) {
        Integer replaced = givenSeconds
        int given = replaced != null ? replaced.intValue() : seconds
        List<ProcessHandle> started = new ArrayList<ProcessHandle>(process.descendants().toList())
        Collections.reverse(started)
        started.add(process)
        started.each { ProcessHandle each -> each.destroy() }
        // where a process is not asked, it has been ended: the wait is for the system to say so
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(asksToEnd() ? given : Math.min(given, FORCED_SECONDS))
        try {
            for (ProcessHandle each : started) {
                each.onExit().get(Math.max(0L, deadline - System.nanoTime()), TimeUnit.NANOSECONDS)
            }
        } catch (TimeoutException | InterruptedException | ExecutionException ignored) {
            // what is left is ended below
        }
        started.addAll(process.descendants().toList())
        List<ProcessHandle> left = started.findAll { ProcessHandle each -> each.isAlive() }
        left.each { ProcessHandle each -> each.destroyForcibly() }
        try {
            for (ProcessHandle each : left) {
                each.onExit().get(FORCED_SECONDS, TimeUnit.SECONDS)
            }
        } catch (TimeoutException | InterruptedException | ExecutionException ignored) {
            // said by what is returned
        }
        return !started.any { ProcessHandle each -> each.isAlive() }
    }

    // null when Gradle cannot be run for the project, which was then said
    private Path wrapperFor(ConsoleProject project, List<String> arguments) throws RefusedArgumentException {
        ConsoleProject.Wrapper found = project.gradleWrapper(windows)
        if (found.file == null) {
            failed("${found.refusal}. Gradle was not run, since the command runs the Gradle of the project and no other")
            return null
        }
        Path wrapper = found.file
        if (windows) {
            String refused = arguments.find { String argument -> notForABatchFile(argument) }
            if (refused != null) {
                throw new RefusedArgumentException("The argument ${FeatureSelectionException.shown(refused)} cannot be given to Gradle from here: " +
                        "the Gradle wrapper of Windows is a batch file, which would not get as it is an argument that is empty, has one of ${NOT_FOR_A_BATCH_FILE} or a control character, " +
                        'or has a space and ends with a backslash. Run gradlew.bat with it yourself')
            }
            Path share = [wrapper, project.directory].find { Path path -> isOfAShare(path.toString()) }
            if (share != null) {
                failed("Gradle cannot be run from here for ${line(share)}: the Gradle wrapper of Windows is a batch file, and the command interpreter that runs it " +
                        'takes no path that begins with the name of a computer (a UNC path) for its current directory, and would run Gradle in the directory of Windows instead. ' +
                        'Gradle was not run; give the share a drive letter (net use) and run the command in the project on that drive')
                return null
            }
            Path named = [wrapper, project.directory].find { Path path -> hasWhatABatchFileReads(path.toString()) }
            if (named != null) {
                failed("Gradle cannot be run from here for ${line(named)}: the Gradle wrapper of Windows is a batch file, and a path with one of ${NOT_FOR_A_BATCH_FILE} " +
                        'or a control character is not read as it is where the batch file is started. Gradle was not run; run gradlew.bat yourself, or move the project')
                return null
            }
            if (notAPathWithoutQuotes(wrapper.toString())) {
                failed("Gradle cannot be run from here for ${line(wrapper)}: the Gradle wrapper of Windows is a batch file, and where its path has no space it is given to the " +
                        "command interpreter without quotes, which ends the name of a command at any of ${NOT_IN_A_PATH_WITHOUT_QUOTES}. " +
                        'Gradle was not run; run gradlew.bat yourself, or move the project')
                return null
            }
        }
        return wrapper
    }

    /**
     * @param text a path or a message of the system
     * @return the text for a message of a command: one line, and no markup of the command line
     */
    static String line(Object text) {
        return String.valueOf(text).replaceAll(/[\p{Cc}\p{Cf}\p{Zl}\p{Zp}]/, '?').replace('@|', '@ |')
    }

    /**
     * @param argument an argument for the Gradle wrapper of Windows
     * @return whether the batch file would not get the argument as it is
     */
    static boolean notForABatchFile(String argument) {
        if (argument.isEmpty() || hasWhatABatchFileReads(argument)) {
            return true
        }
        // such an argument is put between quotes, and the backslash would then stand before the closing one
        return argument.endsWith('\\') && (argument.contains(' ') || argument.contains('\t'))
    }

    /**
     * @param path the path of the Gradle wrapper of Windows
     * @return whether the JVM gives it to the command interpreter without quotes, as it does a path
     * that has no white space, and the command interpreter would not read all of it as the name of
     * the command
     */
    static boolean notAPathWithoutQuotes(String path) {
        boolean quoted = false
        boolean divided = false
        for (int i = 0; i < path.length(); i++) {
            char character = path.charAt(i)
            // as the JVM decides it
            quoted = quoted || Character.isSpaceChar(character) || Character.isWhitespace(character)
            divided = divided || NOT_IN_A_PATH_WITHOUT_QUOTES.indexOf((int) character) > -1
        }
        return divided && !quoted
    }

    private void failed(String message) {
        failure.accept(message)
    }

    private static boolean hasWhatABatchFileReads(String text) {
        for (int i = 0; i < text.length(); i++) {
            char character = text.charAt(i)
            if (character < (char) ' ' || NOT_FOR_A_BATCH_FILE.indexOf((int) character) > -1) {
                return true
            }
        }
        return false
    }

    // null when the check of the path of the project cannot be given to Gradle, which was then said
    private List<String> command(ConsoleProject project, Path wrapper, List<String> arguments) {
        List<String> checked
        try {
            checked = project.pathCheck()
        } catch (IOException e) {
            failed(ProjectPathCheck.notRun(project, e))
            return null
        }
        List<String> command = []
        // a wrapper that lost its executable bit, as in a project unpacked from an archive, is still the script it was
        if (!windows && !Files.isExecutable(wrapper)) {
            command.add('sh')
        }
        command.add(wrapper.toString())
        command.addAll(PlainOutput.forGradle(arguments))
        // options of Gradle, which are options only before a --: in front of what the command gives it, which
        // can have a -- of its own after which every argument is a task or an option of one, and stays as it was
        command.addAll(checked)
        command.addAll(arguments)
        return command
    }

    private int run(List<String> command, Path wrapper, Path directory, Consumer<GradleBuild.Running> started) {
        // read once, on the thread of the command, and handed to the threads that copy what Gradle writes
        CommandStreams streams = CommandStreams.current()
        PrintStream out = streams.out
        PrintStream err = streams.err
        boolean inherited = isOutputOfProcess()
        ProcessBuilder builder = new ProcessBuilder(command).directory(directory.toFile())
        if (inherited) {
            out.flush()
            err.flush()
            builder.inheritIO()
        } else {
            builder.redirectInput(ProcessBuilder.Redirect.INHERIT)
        }
        // what Gradle writes is kept in the order it wrote it, where the command keeps one text: the stream of
        // its errors is then a null stream, whose copy ends at once
        oneStreamWhereOneText(builder, streams, inherited)
        if (isNonInteractive()) {
            // Gradle and what it starts read the end of their input at once, and are told why
            builder.redirectInput(ProcessBuilder.Redirect.from(new File(windows ? 'NUL' : '/dev/null')))
            builder.environment().put(NON_INTERACTIVE, 'true')
        }
        AtomicBoolean stopped = new AtomicBoolean()
        AtomicBoolean said = new AtomicBoolean()
        // said once and by whoever comes first: this JVM can end before the thread of the command gets to say it
        Runnable sayStopped = {
            if (said.compareAndSet(false, true)) {
                failed(STOPPED)
            }
        } as Runnable
        // When this JVM is ended while Gradle runs, Gradle is not left running without it; the line comes before
        // the wait for what does not end by itself, which is then ended by force. The hook is there before Gradle
        // is, and Gradle is started while the hook holds what it reads: a JVM that is ended while the wrapper is
        // being started stops the wrapper once it has started, and one that is ended before starts none. With the
        // hook added after the start, a JVM ended in between ran no hook, said nothing and left Gradle running.
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
                stop(running, stopped)
            }
        } as Runnable, 'grails-gradle-stop')
        try {
            Runtime.getRuntime().addShutdownHook(whenEnded)
        } catch (IllegalStateException ignored) {
            // this JVM is being ended already: Gradle is not started
            return 1
        }
        try {
            Process process
            synchronized (starting) {
                if (ending.get()) {
                    // the hook has run: this JVM is being ended, and Gradle is not started
                    return 1
                }
                try {
                    process = builder.start()
                } catch (IOException | IllegalArgumentException e) {
                    // the second is what the JVM of Windows says to a command line it will not hand to a batch file
                    failed("The Gradle wrapper ${line(wrapper)} could not be started: ${line(e.getMessage())}")
                    return 1
                }
                launched.set(process)
            }
            if (started != null) {
                started.accept(new GradleBuild.Running(process.toHandle(), false, { stop(process, stopped) } as Runnable))
            }
            return waitFor(process, inherited, out, err, stopped, sayStopped)
        } finally {
            try {
                Runtime.getRuntime().removeShutdownHook(whenEnded)
            } catch (IllegalStateException ignored) {
                // the JVM is shutting down, and the hook runs
            }
        }
    }

    // waits for the Gradle that was started, and says how it ended
    private int waitFor(Process process, boolean inherited, PrintStream out, PrintStream err, AtomicBoolean stopped, Runnable sayStopped) {
        waiting()
        Thread output = inherited ? null : copy(process.getInputStream(), out)
        Thread errors = inherited ? null : copy(process.getErrorStream(), err)
        try {
            int exit = process.waitFor()
            if (!inherited) {
                // a process Gradle left behind can hold the pipe open: what it writes later is not waited for
                output.join(TimeUnit.SECONDS.toMillis(OUTPUT_SECONDS))
                errors.join(TimeUnit.SECONDS.toMillis(OUTPUT_SECONDS))
            }
            // A signal of the terminal reaches Gradle and this JVM alike, and Gradle can have ended by it
            // before this JVM knows that it is being ended or that the command was cancelled
            for (int waited = 0; waited < SIGNAL_MILLIS && endedBySignal(exit) && !stopped.get() && !shuttingDown(); waited += SIGNAL_STEP_MILLIS) {
                Thread.sleep(SIGNAL_STEP_MILLIS)
            }
            // the exit code of a process that was ended says nothing but that it was
            if (stopped.get() || (exit != 0 && Thread.currentThread().isInterrupted()) || (endedBySignal(exit) && shuttingDown())) {
                sayStopped.run()
                return 1
            }
            if (exit != 0) {
                failed("Gradle did not succeed: it ended with exit code ${exit}")
                return 1
            }
            return 0
        } catch (InterruptedException ignored) {
            sayStopped.run()
            stop(process, stopped)
            Thread.currentThread().interrupt()
            return 1
        } finally {
            waited()
        }
    }

    // 128 and the number of the signal is what a shell and the JVM give for a process a signal ended; the
    // other is what Windows gives for one that was ended with Ctrl-C
    private static boolean endedBySignal(int exit) {
        return (exit > 128 && exit < 160) || exit == WINDOWS_CONTROL_C
    }

    // a shutdown hook can be neither added nor removed once the JVM is being ended
    private static boolean shuttingDown() {
        Thread probe = new Thread({ } as Runnable)
        try {
            Runtime.getRuntime().addShutdownHook(probe)
            Runtime.getRuntime().removeShutdownHook(probe)
            return false
        } catch (IllegalStateException ignored) {
            return true
        }
    }

    // Ends what was started, with every process it started in turn: the wrapper is a script and need not
    // become the Gradle client, and on Windows it never does. A Gradle daemon that was running before is
    // none of them: it serves other builds too, and cancels this one when its client is gone.
    private static void stop(Process process, AtomicBoolean stopped) {
        stopped.set(true)
        stop(process.toHandle(), STOP_SECONDS)
    }

    // what Gradle writes is written as it comes, not when a line or the build is complete, and as the bytes it is
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
        } as Runnable, 'grails-gradle-output')
        thread.setDaemon(true)
        thread.start()
        return thread
    }

    /**
     * An argument that cannot be given to Gradle as it is. The message is one line for the user, with
     * the argument made safe to show.
     */
    static class RefusedArgumentException extends Exception {

        RefusedArgumentException(String message) {
            super(message)
        }
    }
}

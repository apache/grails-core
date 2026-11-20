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
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Consumer
import groovy.transform.CompileStatic
import org.grails.forge.feature.FeatureSelectionException

/**
 * Runs the Gradle wrapper of a project, synchronously, for the commands of the CLI that are a Gradle
 * build: the one place that finds the wrapper, builds the command line, shows what Gradle writes,
 * turns the exit code of Gradle into the one of the command, and ends Gradle when the command is
 * cancelled. The arguments are a list from the command to Gradle; nothing is joined or split.
 *
 * <p>Gradle writes to the output of the CLI itself where that is the output the process was started
 * with, so that it sees the terminal there is, with its colours and its progress. Where the output
 * was replaced, as a specification does to read it, the bytes Gradle writes are copied to it.</p>
 *
 * @since 9.0
 */
@CompileStatic
final class GradleRunner {

    // what cmd.exe reads as its own in the command line of a batch file
    private static final String NOT_FOR_A_BATCH_FILE = '"%&|<>^'
    private static final int STOP_SECONDS = 10
    private static final int OUTPUT_SECONDS = 10
    private static final String STOPPED = 'Gradle was stopped before it finished, because the command was cancelled'

    private static volatile PrintStream processOut
    private static volatile PrintStream processErr

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

    /**
     * Tells which streams are the output the process was started with. While they are still the output
     * of the CLI, Gradle is given them as they are; called once, when the CLI is started as a program.
     *
     * @param out the standard output of the process
     * @param err the standard error of the process
     */
    static void outputOfProcess(PrintStream out, PrintStream err) {
        processOut = out
        processErr = err
    }

    /**
     * Runs Gradle in the directory of the project and waits for it.
     *
     * @param project the project
     * @param arguments the arguments of Gradle, each given to it as the one argument it is
     * @return 0 when Gradle ended with 0, and 1 otherwise, after the line that says why
     * @throws RefusedArgumentException on Windows, for an argument a batch file does not receive unchanged; nothing was run
     */
    int run(ConsoleProject project, List<String> arguments) throws RefusedArgumentException {
        ConsoleProject.Wrapper found = project.gradleWrapper(windows)
        if (found.file == null) {
            failed("${found.refusal}. Gradle was not run, since the command runs the Gradle of the project and no other")
            return 1
        }
        Path wrapper = found.file
        if (windows) {
            String refused = arguments.find { String argument -> notForABatchFile(argument) }
            if (refused != null) {
                throw new RefusedArgumentException("The argument ${FeatureSelectionException.shown(refused)} cannot be given to Gradle from here: " +
                        "the Gradle wrapper of Windows is a batch file, which would not get as it is an argument that is empty, has one of ${NOT_FOR_A_BATCH_FILE} or a control character, " +
                        'or has a space and ends with a backslash. Run gradlew.bat with it yourself')
            }
            Path named = [wrapper, project.directory].find { Path path -> hasWhatABatchFileReads(path.toString()) }
            if (named != null) {
                failed("Gradle cannot be run from here for ${line(named)}: the Gradle wrapper of Windows is a batch file, and a path with one of ${NOT_FOR_A_BATCH_FILE} " +
                        'or a control character is not read as it is where the batch file is started. Gradle was not run; run gradlew.bat yourself, or move the project')
                return 1
            }
        }
        return run(command(wrapper, arguments), wrapper, project.directory)
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

    private List<String> command(Path wrapper, List<String> arguments) {
        List<String> command = []
        // a wrapper that lost its executable bit, as in a project unpacked from an archive, is still the script it was
        if (!windows && !Files.isExecutable(wrapper)) {
            command.add('sh')
        }
        command.add(wrapper.toString())
        command.addAll(arguments)
        return command
    }

    private int run(List<String> command, Path wrapper, Path directory) {
        PrintStream out = System.out
        PrintStream err = System.err
        boolean inherited = out.is(processOut) && err.is(processErr)
        ProcessBuilder builder = new ProcessBuilder(command).directory(directory.toFile())
        if (inherited) {
            out.flush()
            err.flush()
            builder.inheritIO()
        } else {
            builder.redirectInput(ProcessBuilder.Redirect.INHERIT)
        }
        Process process
        try {
            process = builder.start()
        } catch (IOException | IllegalArgumentException e) {
            // the second is what the JVM of Windows says to a command line it will not hand to a batch file
            failed("The Gradle wrapper ${line(wrapper)} could not be started: ${line(e.getMessage())}")
            return 1
        }
        AtomicBoolean stopped = new AtomicBoolean()
        // when this JVM is ended while Gradle runs, Gradle is not left running without it
        Thread whenEnded = new Thread({ stop(process, stopped) } as Runnable, 'grails-gradle-stop')
        Runtime.getRuntime().addShutdownHook(whenEnded)
        Thread output = inherited ? null : copy(process.getInputStream(), out)
        Thread errors = inherited ? null : copy(process.getErrorStream(), err)
        try {
            int exit = process.waitFor()
            if (!inherited) {
                // a process Gradle left behind can hold the pipe open: what it writes later is not waited for
                output.join(TimeUnit.SECONDS.toMillis(OUTPUT_SECONDS))
                errors.join(TimeUnit.SECONDS.toMillis(OUTPUT_SECONDS))
            }
            // the exit code of a process that was ended says nothing but that it was
            if (stopped.get() || (exit != 0 && Thread.currentThread().isInterrupted())) {
                failed(STOPPED)
                return 1
            }
            if (exit != 0) {
                failed("Gradle did not succeed: it ended with exit code ${exit}")
                return 1
            }
            return 0
        } catch (InterruptedException ignored) {
            stop(process, stopped)
            failed(STOPPED)
            Thread.currentThread().interrupt()
            return 1
        } finally {
            try {
                Runtime.getRuntime().removeShutdownHook(whenEnded)
            } catch (IllegalStateException ignored) {
                // the JVM is shutting down, and the hook runs
            }
        }
    }

    // Ends what was started, with every process it started in turn: the wrapper is a script and need not
    // become the Gradle client, and on Windows it never does. First the processes below it, then the
    // wrapper, and after a while by force whatever is left. A Gradle daemon that was running before is
    // none of them: it serves other builds too, and cancels this one when its client is gone.
    private static void stop(Process process, AtomicBoolean stopped) {
        stopped.set(true)
        List<ProcessHandle> started = new ArrayList<ProcessHandle>(process.descendants().toList())
        Collections.reverse(started)
        started.add(process.toHandle())
        started.each { ProcessHandle each -> each.destroy() }
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(STOP_SECONDS)
        try {
            for (ProcessHandle each : started) {
                each.onExit().get(Math.max(0L, deadline - System.nanoTime()), TimeUnit.NANOSECONDS)
            }
        } catch (TimeoutException | InterruptedException | ExecutionException ignored) {
            // what is left is ended below
        }
        started.addAll(process.descendants().toList())
        started.findAll { ProcessHandle each -> each.isAlive() }.each { ProcessHandle each -> each.destroyForcibly() }
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

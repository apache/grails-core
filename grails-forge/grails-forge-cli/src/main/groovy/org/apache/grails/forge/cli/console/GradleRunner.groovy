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

import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import java.util.function.Consumer
import groovy.transform.CompileStatic
import org.grails.forge.feature.FeatureSelectionException

/**
 * Runs the Gradle wrapper of a project, synchronously, for the commands of the CLI that are a Gradle
 * build: the one place that finds the wrapper, builds the command line, shows what Gradle writes,
 * turns the exit code of Gradle into the one of the command, and ends Gradle when the command is
 * cancelled. The arguments are a list from the command to Gradle; nothing is joined or split.
 *
 * @since 9.0
 */
@CompileStatic
final class GradleRunner {

    // what cmd.exe reads as its own in the command line of a batch file
    private static final String NOT_FOR_A_BATCH_FILE = '"%&|<>^'
    private static final int STOP_SECONDS = 10
    private static final int OUTPUT_SECONDS = 5

    private final PrintWriter out
    private final PrintWriter err
    private final Consumer<String> failure
    private final boolean windows

    /**
     * @param out where the standard output of Gradle is copied to
     * @param err where the standard error of Gradle is copied to
     * @param failure given the one line that says why the command failed, when it did
     * @param windows whether the wrapper for Windows is run
     */
    GradleRunner(PrintWriter out, PrintWriter err, Consumer<String> failure, boolean windows) {
        this.out = out
        this.err = err
        this.failure = failure
        this.windows = windows
    }

    /**
     * @param from the directory no project was found from
     * @param notDone what did not happen for it, as a sentence without the full stop
     * @return the one line that says there is no project
     */
    static String noProject(Path from, String notDone) {
        return "There is no Grails project here: neither ${line(from)} nor a directory above it in the same repository has a grails-app directory " +
                "or the record of a generated project. ${notDone}"
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
        Path wrapper = project.findGradleWrapper(windows)
        if (wrapper == null) {
            failed("The project in ${line(project.directory)} has no Gradle wrapper: there is no ${ConsoleProject.wrapperName(windows)} in it or in a directory above it " +
                    'in the same repository. Gradle was not run, since the command runs the Gradle of the project and no other')
            return 1
        }
        if (windows) {
            String refused = arguments.find { String argument -> notForABatchFile(argument) }
            if (refused != null) {
                throw new RefusedArgumentException("The argument ${FeatureSelectionException.shown(refused)} cannot be given to Gradle from here: " +
                        "the Gradle wrapper of Windows is a batch file, which would not get an argument with one of ${NOT_FOR_A_BATCH_FILE} or a control character as it is. " +
                        'Run gradlew.bat with it yourself')
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

    private void failed(String message) {
        failure.accept(message)
    }

    private static boolean notForABatchFile(String argument) {
        for (int i = 0; i < argument.length(); i++) {
            char character = argument.charAt(i)
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
        Process process
        try {
            process = new ProcessBuilder(command)
                    .directory(directory.toFile())
                    .redirectInput(ProcessBuilder.Redirect.INHERIT)
                    .start()
        } catch (IOException e) {
            failed("The Gradle wrapper ${line(wrapper)} could not be started: ${line(e.getMessage())}")
            return 1
        }
        boolean onWindows = windows
        // when this JVM is ended while Gradle runs, Gradle is not left running without it
        Thread whenEnded = new Thread({ stop(process, onWindows) } as Runnable, 'grails-gradle-stop')
        Runtime.getRuntime().addShutdownHook(whenEnded)
        Thread output = copy(process.getInputStream(), out)
        Thread errors = copy(process.getErrorStream(), err)
        try {
            int exit = process.waitFor()
            // a Gradle daemon that was started keeps neither stream open; the wait is bounded all the same
            output.join(TimeUnit.SECONDS.toMillis(OUTPUT_SECONDS))
            errors.join(TimeUnit.SECONDS.toMillis(OUTPUT_SECONDS))
            if (exit != 0) {
                failed("Gradle did not succeed: it ended with exit code ${exit}")
                return 1
            }
            return 0
        } catch (InterruptedException ignored) {
            stop(process, onWindows)
            failed('Gradle was stopped before it finished, because the command was cancelled')
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

    // Ends the Gradle client, which makes a Gradle daemon cancel the build it runs for the client. The
    // daemon is not ended from here: it serves other builds too. On Windows the process is the command
    // interpreter of the batch file, and the client is its child.
    private static void stop(Process process, boolean windows) {
        List<ProcessHandle> clients = windows ? process.children().toList() : Collections.<ProcessHandle>emptyList()
        process.destroy()
        clients.each { ProcessHandle client -> client.destroy() }
        boolean ended
        try {
            ended = process.waitFor(STOP_SECONDS, TimeUnit.SECONDS)
        } catch (InterruptedException ignored) {
            ended = false
        }
        if (!ended) {
            process.destroyForcibly()
            clients.each { ProcessHandle client -> client.destroyForcibly() }
        }
    }

    // what Gradle writes is written as it comes, not when a line or the build is complete
    private static Thread copy(InputStream from, PrintWriter to) {
        Thread thread = new Thread({
            try {
                Reader reader = new InputStreamReader(from, outputCharset())
                char[] buffer = new char[4096]
                int count
                while ((count = reader.read(buffer)) != -1) {
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

    private static Charset outputCharset() {
        String encoding = System.getProperty('native.encoding')
        try {
            return encoding == null ? Charset.defaultCharset() : Charset.forName(encoding)
        } catch (IllegalArgumentException ignored) {
            return Charset.defaultCharset()
        }
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

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

import java.util.concurrent.TimeUnit
import groovy.transform.CompileStatic
import picocli.CommandLine

/**
 * A line of the interactive console that starts with an exclamation mark, as it did in the Grails
 * shell: what follows the mark is a command line of the operating system. It is given as it was
 * typed to the shell of the user, which runs it in the current directory of the console with the
 * rights of the user, and the console waits for it. The program has the terminal of the console: its
 * output, its error output and, where the console reads a terminal, its input. Where the console
 * reads lines that were piped into it, the input of the program is empty, so that the lines after
 * it are lines of the console.
 *
 * <p>This is a line of the console only: on a command line of its own an argument that starts with
 * an exclamation mark is no command, and nothing is run for it.</p>
 *
 * @since 9.0
 */
@CompileStatic
final class ConsoleProgram {

    /** What a line of the console starts with to run a command line of the operating system. */
    static final String MARK = '!'

    /** The shell where the environment names none, or one that is no absolute path. */
    static final String POSIX_SHELL = '/bin/sh'

    // How long the output of a program is still copied after it ended, when the console does not hand its own
    // output over: a program it started in the background can hold the pipe open
    private static final int OUTPUT_SECONDS = 5

    private ConsoleProgram() {
    }

    /**
     * @param line a line of the console as it was typed
     * @return what follows the exclamation mark the line starts with (white space may come before
     * the mark), which is blank for the mark alone; {@code null} for a line that does not start with one
     */
    static String of(String line) {
        if (line == null) {
            return null
        }
        String stripped = line.stripLeading()
        return stripped.startsWith(MARK) ? stripped.substring(MARK.length()) : null
    }

    /**
     * The command that runs a command line in the shell of the user: on Windows the command
     * interpreter the environment variable {@code ComSpec} names, else {@code cmd.exe}, with
     * {@code /d /s /c} and the command line between quotes, which it takes as it is; elsewhere the
     * shell the environment variable {@code SHELL} names, when it is an absolute path, else
     * {@code /bin/sh}, with {@code -c} and the command line.
     *
     * @param command the command line
     * @param windows whether it runs on Windows
     * @param environment the environment the shell is looked up in
     * @return the program and its arguments
     */
    static List<String> shellCommand(String command, boolean windows, Map<String, String> environment) {
        if (windows) {
            String interpreter = environment.get('ComSpec') ?: environment.get('COMSPEC')
            // /s: what is between the first and the last quote is the command line, as it is. A backslash before
            // the last quote would be read by the JVM as one that is part of the argument.
            String quoted = '"' + command + (command.endsWith('\\') ? ' ' : '') + '"'
            return [interpreter?.trim() ? interpreter : 'cmd.exe', '/d', '/s', '/c', quoted]
        }
        String shell = environment.get('SHELL')
        return [shell?.trim() && new File(shell).isAbsolute() ? shell : POSIX_SHELL, '-c', command]
    }

    /**
     * Runs a command line in the shell of the user and waits for it. A non-zero exit code is said in
     * one line on the error output, and a shell that cannot be started in one line that says why.
     *
     * @param command the command line, which is not blank
     * @param directory the directory it runs in
     * @param readsTerminal whether the program is given the input of the console, which is a terminal
     * a user types into; without it the input of the program is empty
     * @return the exit code of the shell, or -1 when it could not be started
     */
    static int run(String command, File directory, boolean readsTerminal) {
        boolean windows = isWindows()
        // read once, on the thread of the line, and handed to the threads that copy what the program writes
        CommandStreams streams = CommandStreams.current()
        PrintStream out = streams.out
        PrintStream err = streams.err
        // the program writes to the output of the console itself where that is the output of the process; a caller
        // that runs the console in its own JVM is given what the program writes
        boolean inherited = GradleBuild.isOutputOfProcess()
        ProcessBuilder builder = new ProcessBuilder(shellCommand(command, windows, System.getenv())).directory(directory)
        out.flush()
        err.flush()
        if (inherited) {
            builder.redirectOutput(ProcessBuilder.Redirect.INHERIT).redirectError(ProcessBuilder.Redirect.INHERIT)
        }
        // what the program writes is kept in the order it wrote it, where the console keeps one text
        GradleBuild.oneStreamWhereOneText(builder, streams, inherited)
        builder.redirectInput(readsTerminal ? ProcessBuilder.Redirect.INHERIT : ProcessBuilder.Redirect.from(new File(windows ? 'NUL' : '/dev/null')))
        Process process
        try {
            process = builder.start()
        } catch (IOException | IllegalArgumentException e) {
            err.println(CommandLine.Help.Ansi.AUTO.string('@|bold,red | Error|@ ') + "The shell ${builder.command()[0]} could not be started: ${e.message}")
            return -1
        }
        Thread output = inherited ? null : copy(process.getInputStream(), out)
        Thread errors = inherited ? null : copy(process.getErrorStream(), err)
        int exit
        boolean interrupted = false
        try {
            exit = process.waitFor()
        } catch (InterruptedException ignored) {
            // the interrupt of the terminal is the program's and does not get here: whoever interrupted the
            // console asked for the program to end with it
            process.descendants().forEach { ProcessHandle each -> each.destroy() }
            process.destroy()
            interrupted = true
            exit = 130
        }
        if (!inherited) {
            // what the program wrote before it ended is copied whole, also after an interrupt: the thread is
            // interrupted again only once it is, or a wait for the copy would end at once
            interrupted = join(output) || interrupted
            interrupted = join(errors) || interrupted
        }
        out.flush()
        if (exit != 0) {
            err.println("| Exit code ${exit}")
        }
        err.flush()
        if (interrupted) {
            Thread.currentThread().interrupt()
        }
        return exit
    }

    // waits for a copy of what the program writes; true when the wait was interrupted, which it then is no longer
    private static boolean join(Thread copy) {
        try {
            copy.join(TimeUnit.SECONDS.toMillis(OUTPUT_SECONDS))
            return false
        } catch (InterruptedException ignored) {
            return true
        }
    }

    private static boolean isWindows() {
        return System.getProperty('os.name', '').toLowerCase(Locale.ENGLISH).contains('windows')
    }

    private static Thread copy(InputStream from, OutputStream to) {
        Thread thread = new Thread({
            try {
                byte[] buffer = new byte[8192]
                int count
                // what the program writes is shown when it writes it, as it is in a terminal
                while ((count = from.read(buffer)) != -1) {
                    to.write(buffer, 0, count)
                    to.flush()
                }
            } catch (IOException ignored) {
                // the process ended, or was ended
            }
        } as Runnable, 'grails-console-program-output')
        thread.setDaemon(true)
        thread.start()
        return thread
    }
}

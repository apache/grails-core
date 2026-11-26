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
import java.nio.file.LinkOption
import java.nio.file.Path
import java.time.Instant
import java.util.concurrent.TimeUnit
import groovy.transform.CompileStatic

/**
 * The arguments of a tool of the application, as the file the Gradle task {@code runTool} reads with
 * {@code --args-file}: a JSON array of strings in UTF-8, the name of the command first. Nothing of
 * the arguments is then on a command line, where a batch file of Windows would not get all of them,
 * where its length is limited and where other users of the computer read it.
 *
 * <p>The file is in {@code .grails/run} of the project, read and written by its owner only, and is
 * deleted when it is closed, and when the CLI is ended while Gradle runs. Its name is the first of a
 * few that is free, so it is the same from one run to the next unless two commands run at once: the
 * path is an option of the task, and Gradle keeps what it configured for a command line it has seen.
 * Beside it is a file that names the process that wrote it. A file whose process is no longer
 * there, as after a CLI that was killed, is deleted by the next command that writes one.</p>
 *
 * @since 9.0
 */
@CompileStatic
final class CommandArgumentsFile implements Closeable {

    /** What the task reads at most, in bytes of the file. */
    public static final int MAX_BYTES = 1024 * 1024

    /** How many arguments the task reads at most. */
    public static final int MAX_ARGUMENTS = 4096

    private static final int NAMES = 16
    private static final int MAX_OWNER = 64
    // how long a file can be without the name of its process: it is written right after the file is created
    private static final long UNNAMED_MILLIS = TimeUnit.MINUTES.toMillis(1)
    private static final char[] HEX = '0123456789abcdef'.toCharArray()

    private final Path file
    private final Path owner
    private final String name
    private final Thread whenEnded

    private CommandArgumentsFile(Path file, Path owner, String name) {
        this.file = file
        this.owner = owner
        this.name = name
        this.whenEnded = new Thread({ delete() } as Runnable, 'grails-command-arguments')
    }

    /**
     * Says why arguments cannot be given to the task, before anything is written.
     *
     * @param arguments the name of the command and its arguments
     * @return one line, without any of the arguments in it, or {@code null} when they can be given
     */
    static String refusal(List<String> arguments) {
        if (arguments.size() > MAX_ARGUMENTS) {
            return "these are ${arguments.size()} arguments, and the task that runs a command takes ${MAX_ARGUMENTS} at most"
        }
        for (int i = 0; i < arguments.size(); i++) {
            String argument = arguments.get(i)
            if (argument.indexOf(0) > -1) {
                return "argument ${i + 1} has the character U+0000, which no command line carries"
            }
            if (halfOfAPair(argument)) {
                return "argument ${i + 1} has half of a character of two code units, which cannot be written as text"
            }
        }
        if (json(arguments).length > MAX_BYTES) {
            return "the arguments are more than ${MAX_BYTES} bytes in UTF-8, and the task that runs a command reads no more"
        }
        return null
    }

    /**
     * Writes the arguments to a file of the project.
     *
     * @param project the project
     * @param arguments the name of the command and its arguments, which {@link #refusal} has nothing against
     * @return the file, to be closed when Gradle has ended
     * @throws IOException with one line, when the file cannot be written
     */
    static CommandArgumentsFile write(ConsoleProject project, List<String> arguments) throws IOException {
        Path run = RunDirectory.of(project)
        sweep(run)
        for (int i = 0; i < NAMES; i++) {
            String name = name(i)
            Path created = RunDirectory.createNew(run, name)
            if (created == null) {
                continue
            }
            CommandArgumentsFile written = new CommandArgumentsFile(created, run.resolve(ownerName(i)), name)
            try {
                Runtime.getRuntime().addShutdownHook(written.whenEnded)
            } catch (IllegalStateException ignored) {
                written.delete()
                throw new IOException("${RunDirectory.LOCATION}/${name} was not written: the command is being ended")
            }
            try {
                ProcessHandle self = ProcessHandle.current()
                // where the system does not say when the process began, an instant that is none but is not nothing
                long began = self.info().startInstant().map { Instant instant -> instant.toEpochMilli() }.orElse(1L)
                Files.write(RunDirectory.create(run, ownerName(i)), "${self.pid()} ${began}\n".toString().getBytes(StandardCharsets.US_ASCII))
                Files.write(created, json(arguments))
            } catch (IOException e) {
                written.close()
                throw new IOException("${RunDirectory.LOCATION}/${name} cannot be written: ${GradleRunner.line(e.getMessage())}")
            }
            return written
        }
        throw new IOException("${RunDirectory.LOCATION} has ${NAMES} files of arguments already, one for each command that runs: " +
                'delete the files command-args-* there when no command runs')
    }

    /**
     * @return the option of the task that names the file, by its path in the project
     */
    String getOption() {
        return "--args-file=${RunDirectory.LOCATION}/${name}".toString()
    }

    @Override
    void close() {
        delete()
        try {
            Runtime.getRuntime().removeShutdownHook(whenEnded)
        } catch (IllegalStateException ignored) {
            // the JVM is shutting down, and the hook runs
        }
    }

    private void delete() {
        try {
            Files.deleteIfExists(file)
            Files.deleteIfExists(owner)
        } catch (IOException ignored) {
            // the next command that writes one deletes it, since its process is gone then
        }
    }

    // Deletes the files of commands that are no longer there: of a CLI that was killed, which deletes nothing.
    private static void sweep(Path run) {
        for (int i = 0; i < NAMES; i++) {
            Path file = run.resolve(name(i))
            Path owner = run.resolve(ownerName(i))
            try {
                boolean there = Files.exists(file, LinkOption.NOFOLLOW_LINKS)
                if (!there && !Files.exists(owner, LinkOption.NOFOLLOW_LINKS)) {
                    continue
                }
                if (Files.isDirectory(file, LinkOption.NOFOLLOW_LINKS) || Files.isDirectory(owner, LinkOption.NOFOLLOW_LINKS) || writerIsThere(file, owner)) {
                    continue
                }
                Files.deleteIfExists(file)
                Files.deleteIfExists(owner)
            } catch (IOException ignored) {
                // the name stays taken
            }
        }
    }

    // whether the process that wrote a file of arguments still runs: the one of that number that began at that instant
    private static boolean writerIsThere(Path file, Path owner) throws IOException {
        String named = null
        if (Files.isRegularFile(owner, LinkOption.NOFOLLOW_LINKS) && Files.size(owner) <= MAX_OWNER) {
            named = new String(Files.readAllBytes(owner), StandardCharsets.US_ASCII).trim()
        }
        if (named == null || !(named ==~ /[0-9]{1,10} [0-9]{1,18}/)) {
            // being written just now, or of no process that can be asked for
            Path newest = Files.exists(file, LinkOption.NOFOLLOW_LINKS) ? file : owner
            return Files.getLastModifiedTime(newest, LinkOption.NOFOLLOW_LINKS).toMillis() > System.currentTimeMillis() - UNNAMED_MILLIS
        }
        String[] parts = named.split(' ')
        long pid = Long.parseLong(parts[0])
        long began = Long.parseLong(parts[1])
        Optional<ProcessHandle> process = pid > 0L && pid <= Integer.MAX_VALUE ? ProcessHandle.of(pid) : Optional.<ProcessHandle>empty()
        if (!process.isPresent() || !process.get().isAlive()) {
            return false
        }
        // A file is its writer's while the process of that number that began at that instant runs, as the
        // user who runs the command: a file that names no instant, or a process of someone else, as
        // the files of a clone do, holds no name.
        if (began == 0L || !ProcessUser.isOfThisUser(process.get())) {
            return false
        }
        Optional<Instant> instant = process.get().info().startInstant()
        // where the system does not say when a process began, one of that number is taken for the writer
        return !instant.isPresent() || instant.get().toEpochMilli() == began
    }

    private static String name(int index) {
        return "command-args-${index}.json".toString()
    }

    private static String ownerName(int index) {
        return "command-args-${index}.pid".toString()
    }

    // What the task reads strictly: a quote, a backslash and a control character as escapes, and every
    // other character as it is, so that the size of the file is the size of the arguments in UTF-8.
    private static byte[] json(List<String> arguments) {
        StringBuilder json = new StringBuilder('[')
        for (int i = 0; i < arguments.size(); i++) {
            if (i > 0) {
                json.append(',')
            }
            json.append('"')
            String argument = arguments.get(i)
            for (int at = 0; at < argument.length(); at++) {
                char each = argument.charAt(at)
                if (each == ('"' as char) || each == ('\\' as char)) {
                    json.append('\\').append(each)
                } else if (each < (' ' as char)) {
                    int code = (int) each
                    json.append('\\u00').append(HEX[(code >> 4) & 0xf]).append(HEX[code & 0xf])
                } else {
                    json.append(each)
                }
            }
            json.append('"')
        }
        return json.append(']').toString().getBytes(StandardCharsets.UTF_8)
    }

    private static boolean halfOfAPair(String text) {
        for (int i = 0; i < text.length(); i++) {
            char each = text.charAt(i)
            if (Character.isHighSurrogate(each)) {
                if (i + 1 == text.length() || !Character.isLowSurrogate(text.charAt(i + 1))) {
                    return true
                }
                i++
            } else if (Character.isLowSurrogate(each)) {
                return true
            }
        }
        return false
    }
}

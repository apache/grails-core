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
import java.util.concurrent.TimeUnit
import groovy.json.JsonOutput
import groovy.transform.CompileStatic

/**
 * The arguments of an application command, as the file the Gradle task {@code runCommand} reads with
 * {@code --args-file}: a JSON array of strings, the name of the command first. Nothing of the
 * arguments is then on a command line, where a batch file of Windows would not get all of them, where
 * its length is limited and where other users of the computer read it.
 *
 * <p>The file is in {@code .grails/run} of the project, read and written by its owner only, and is
 * deleted when it is closed. Its name is the first of a few that is free, so it is the same from one
 * run to the next unless two commands run at once: the path is an option of the task, and Gradle
 * keeps what it configured for a command line it has seen.</p>
 *
 * @since 9.0
 */
@CompileStatic
final class CommandArgumentsFile implements Closeable {

    /** What the task reads at most, in bytes of UTF-8. */
    public static final int MAX_BYTES = 1024 * 1024

    /** How many arguments the task reads at most. */
    public static final int MAX_ARGUMENTS = 4096

    private static final int NAMES = 16
    private static final long LEFT_BEHIND_MILLIS = TimeUnit.DAYS.toMillis(1)

    private final Path file
    private final String name

    private CommandArgumentsFile(Path file, String name) {
        this.file = file
        this.name = name
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
            return "the arguments are more than ${MAX_BYTES} bytes, and the task that runs a command reads no more"
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
        for (int i = 0; i < NAMES; i++) {
            String name = "command-args-${i}.json".toString()
            Path created = RunDirectory.createNew(run, name)
            if (created == null && leftBehind(run.resolve(name))) {
                created = RunDirectory.create(run, name)
            }
            if (created != null) {
                try {
                    Files.write(created, json(arguments))
                } catch (IOException e) {
                    Files.deleteIfExists(created)
                    throw new IOException("${RunDirectory.LOCATION}/${name} cannot be written: ${GradleRunner.line(e.getMessage())}")
                }
                return new CommandArgumentsFile(created, name)
            }
        }
        throw new IOException("${RunDirectory.LOCATION} has ${NAMES} files of arguments already, one for each command that runs: " +
                'delete the files command-args-*.json there when no command runs')
    }

    /**
     * @return the option of the task that names the file, by its path in the project
     */
    String getOption() {
        return "--args-file=${RunDirectory.LOCATION}/${name}".toString()
    }

    @Override
    void close() {
        try {
            Files.deleteIfExists(file)
        } catch (IOException ignored) {
            // it is replaced by a later command when it is a day old
        }
    }

    private static byte[] json(List<String> arguments) {
        // every character that is not ASCII is written as an escape, so the bytes are the same in every encoding
        return JsonOutput.toJson(arguments).getBytes(StandardCharsets.US_ASCII)
    }

    // a file of a command that was ended before it could delete it
    private static boolean leftBehind(Path file) {
        try {
            return Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) &&
                    Files.getLastModifiedTime(file, LinkOption.NOFOLLOW_LINKS).toMillis() < System.currentTimeMillis() - LEFT_BEHIND_MILLIS
        } catch (IOException ignored) {
            return false
        }
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

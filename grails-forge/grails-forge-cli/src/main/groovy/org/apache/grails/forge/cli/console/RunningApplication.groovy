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
import java.nio.file.StandardCopyOption
import java.time.Instant
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import groovy.transform.CompileStatic

/**
 * The application {@code run-app} started for a project, as the record of it in {@code .grails/run}
 * says and as the processes of the computer confirm.
 *
 * <p>The record is a file in the directory of the project, and a project can come from anywhere, so
 * what it says is not believed: a process is the one {@code run-app} started only when it has the
 * recorded number <em>and</em> began at the recorded instant, is a process of the user who runs the
 * command, is not the command itself or a process above it, and, where the system shows the command
 * line of a process, was started from the directory of the Gradle wrapper of this project to run
 * {@code bootRun}. A number alone is never enough: the system gives the number of a process that has
 * ended to another one.</p>
 *
 * @since 9.0
 */
@CompileStatic
final class RunningApplication {

    /** The record, in {@code .grails/run}. */
    public static final String RECORD = 'app.json'

    /** What the build and the application write when {@code run-app} does not stay with them, in {@code .grails/run}. */
    public static final String LOG = 'app.log'

    /** Says that {@code stop-app} ends the application, to the {@code run-app} that waits for it; in {@code .grails/run}. */
    public static final String STOPPING = 'app.stopping'

    private static final int FORMAT = 1
    private static final int MAX_RECORD = 64 * 1024
    private static final int MAX_TAIL = 8 * 1024
    private static final long MAX_PID = 0xFFFFFFFFL
    private static final String TASK = 'bootRun'

    /** What is known of the application of a project. */
    enum State {
        /** There is no record. */
        NONE,
        /** {@code .grails/run} is not a directory of the user's own: nothing in it is read or written. */
        REFUSED,
        /** There is a record that says nothing that can be used. */
        UNREADABLE,
        /** The record is of a process that has ended, or that is not the one that was recorded. */
        ENDED,
        /** The recorded process is there, and it cannot be told that it is the Gradle of this project. */
        UNCONFIRMED,
        /** The application runs. */
        RUNNING
    }

    final State state

    /** Why the state is what it is, as part of a sentence; {@code null} for {@link State#NONE} and {@link State#RUNNING}. */
    final String reason

    /** The process that was started, for {@link State#RUNNING}. */
    final ProcessHandle process

    /** The recorded process number, where the record has one. */
    final long pid

    /** The port the application was started for, or 0. */
    final int port

    /** Whether {@code run-app} did not stay with the application. */
    final boolean detached

    private final Path record

    private RunningApplication(State state, String reason, Path record, ProcessHandle process, long pid, int port, boolean detached) {
        this.state = state
        this.reason = reason
        this.record = record
        this.process = process
        this.pid = pid
        this.port = port
        this.detached = detached
    }

    /**
     * @param project the project
     * @param windows whether the Gradle wrapper for Windows is the one that is run
     * @return what is known of the application {@code run-app} started for the project
     */
    static RunningApplication find(ConsoleProject project, boolean windows) {
        Path run
        try {
            run = RunDirectory.existing(project)
        } catch (IOException e) {
            return new RunningApplication(State.REFUSED, e.getMessage(), null, null, 0L, 0, false)
        }
        Path record = run?.resolve(RECORD)
        if (record == null || !Files.exists(record, LinkOption.NOFOLLOW_LINKS)) {
            return new RunningApplication(State.NONE, null, null, null, 0L, 0, false)
        }
        Map<String, Object> said
        try {
            said = read(record, project)
        } catch (IOException e) {
            return new RunningApplication(State.UNREADABLE, e.getMessage(), record, null, 0L, 0, false)
        }
        long pid = (said.pid as Number).longValue()
        long started = (said.started as Number).longValue()
        int port = (said.port as Number).intValue()
        boolean detached = said.detached as boolean
        Optional<ProcessHandle> found = ProcessHandle.of(pid)
        if (!found.isPresent() || !found.get().isAlive()) {
            return new RunningApplication(State.ENDED, "process ${pid}, which was started for it, has ended", record, null, pid, port, detached)
        }
        ProcessHandle process = found.get()
        ProcessHandle.Info info = process.info()
        Optional<Instant> began = info.startInstant()
        if (!began.isPresent() || began.get().toEpochMilli() != started) {
            return new RunningApplication(State.ENDED, "process ${pid} is not the one that was started for it: it did not begin when the record says", record, null, pid, port, detached)
        }
        if (isThisCommand(process)) {
            return new RunningApplication(State.ENDED, "process ${pid} is this command or one that started it", record, null, pid, port, detached)
        }
        if (!ProcessUser.isOfThisUser(process)) {
            return new RunningApplication(State.ENDED, "process ${pid} is not a process of the user who runs the command", record, null, pid, port, detached)
        }
        if (!isGradleOf(project, windows, info)) {
            return new RunningApplication(State.UNCONFIRMED, "process ${pid} began when the record says, but its command line is not that of the Gradle wrapper of this project running ${TASK}",
                    record, null, pid, port, detached)
        }
        return new RunningApplication(State.RUNNING, null, record, process, pid, port, detached)
    }

    /**
     * Writes the record of a process that was started.
     *
     * @param run the directory {@code .grails/run} of the project
     * @param process the process
     * @param port the port the application was started for, or 0
     * @param detached whether {@code run-app} does not stay with the application
     * @param command the arguments Gradle was given
     * @throws IOException with one line, when the record cannot be written
     */
    static void record(Path run, ProcessHandle process, int port, boolean detached, List<String> command) throws IOException {
        Optional<Instant> began = process.info().startInstant()
        if (!began.isPresent()) {
            throw new IOException("${RunDirectory.LOCATION}/${RECORD} cannot be written: the system does not say when process ${process.pid()} began, and without that it cannot be told from another one later")
        }
        Map<String, Object> said = new LinkedHashMap<String, Object>()
        said.put('format', FORMAT)
        said.put('pid', process.pid())
        said.put('started', began.get().toEpochMilli())
        said.put('port', port)
        said.put('detached', detached)
        said.put('command', command)
        // written beside the record and moved, so that whoever reads the record reads all of it
        Path written = RunDirectory.create(run, RECORD + '.new')
        try {
            Files.write(written, (JsonOutput.toJson(said) + '\n').getBytes(StandardCharsets.US_ASCII))
            Path record = run.resolve(RECORD)
            if (Files.isDirectory(record, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException('it is a directory')
            }
            Files.move(written, record, StandardCopyOption.REPLACE_EXISTING)
        } catch (IOException e) {
            Files.deleteIfExists(written)
            throw new IOException("${RunDirectory.LOCATION}/${RECORD} cannot be written: ${GradleRunner.line(e.getMessage())}")
        }
    }

    /**
     * Removes the record, when there is one that is a file or a link.
     *
     * @return whether there is no record now
     */
    boolean forget() {
        return record == null || remove(record)
    }

    /**
     * @param run the directory {@code .grails/run} of the project
     * @param name the name of a file in it
     * @return whether a file or a link of that name was there and was removed
     */
    static boolean remove(Path run, String name) {
        Path file = run.resolve(name)
        return Files.exists(file, LinkOption.NOFOLLOW_LINKS) && remove(file)
    }

    /**
     * @param log a file of text
     * @param lines how many lines
     * @return the last lines of the file, each as one line of a message, from its last 8 KiB
     */
    static List<String> tail(Path log, int lines) {
        try {
            if (!Files.isRegularFile(log, LinkOption.NOFOLLOW_LINKS)) {
                return []
            }
            byte[] end = new RandomAccessFile(log.toFile(), 'r').withCloseable { RandomAccessFile file ->
                long length = file.length()
                file.seek(Math.max(0L, length - MAX_TAIL))
                byte[] bytes = new byte[(int) Math.min((long) MAX_TAIL, length)]
                file.readFully(bytes)
                return bytes
            }
            List<String> all = new String(end, StandardCharsets.UTF_8).readLines().findAll { String line -> !line.trim().isEmpty() }
            return all.takeRight(lines).collect { String line -> GradleRunner.line(line) }
        } catch (IOException ignored) {
            return []
        }
    }

    private static boolean remove(Path file) {
        try {
            if (Files.isDirectory(file, LinkOption.NOFOLLOW_LINKS)) {
                return false
            }
            Files.deleteIfExists(file)
            return true
        } catch (IOException ignored) {
            return false
        }
    }

    private static Map<String, Object> read(Path record, ConsoleProject project) throws IOException {
        if (!Files.isRegularFile(record, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException('it is not a file')
        }
        if (!ConsoleProject.isOwnedByUser(record, project)) {
            throw new IOException('it is not owned by the user who runs the command')
        }
        if (Files.size(record) > MAX_RECORD) {
            throw new IOException("it is larger than ${MAX_RECORD} bytes")
        }
        Object parsed
        try {
            parsed = new JsonSlurper().parse(Files.readAllBytes(record), StandardCharsets.UTF_8.name())
        } catch (IOException e) {
            throw new IOException("it cannot be read: ${GradleRunner.line(e.getMessage())}")
        } catch (RuntimeException ignored) {
            throw new IOException('it is not a record of an application')
        }
        if (!(parsed instanceof Map)) {
            throw new IOException('it is not a record of an application')
        }
        Map<String, Object> said = (Map<String, Object>) parsed
        if (said.format != FORMAT) {
            throw new IOException("it is not of format ${FORMAT}, the one this command reads")
        }
        // a number that is too large for a process is read as a type that is neither
        boolean whole = [said.pid, said.started, said.port].every { Object value -> value instanceof Integer || value instanceof Long }
        if (!whole || !(said.detached instanceof Boolean)) {
            throw new IOException('it does not name a process, an instant and a port')
        }
        long pid = (said.pid as Number).longValue()
        long port = (said.port as Number).longValue()
        if (pid < 2L || pid > MAX_PID || (said.started as Number).longValue() < 1L || port < 0L || port > 65535L) {
            throw new IOException('it does not name a process, an instant and a port')
        }
        return said
    }

    private static boolean isThisCommand(ProcessHandle process) {
        Optional<ProcessHandle> each = Optional.of(ProcessHandle.current())
        for (int levels = 0; each.isPresent() && levels < 64; levels++) {
            if (each.get().pid() == process.pid()) {
                return true
            }
            each = each.get().parent()
        }
        return false
    }

    // Where the system does not show the arguments of a process there is nothing to compare, and the
    // number, the instant and the user decide.
    private static boolean isGradleOf(ConsoleProject project, boolean windows, ProcessHandle.Info info) {
        Optional<String[]> arguments = info.arguments()
        if (!arguments.isPresent()) {
            return true
        }
        Path wrapper = project.gradleWrapper(windows).file
        if (wrapper == null || !(TASK in arguments.get().toList())) {
            return false
        }
        Set<String> homes = [wrapper.getParent().toString()] as Set<String>
        try {
            homes.add(wrapper.getParent().toRealPath().toString())
        } catch (IOException ignored) {
            // the path as it was found is the one there is
        }
        List<String> line = arguments.get().toList()
        if (info.command().isPresent()) {
            line.add(info.command().get())
        }
        return line.any { String argument -> homes.any { String home -> argument.contains(home + File.separator) } }
    }
}

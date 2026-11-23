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
import java.nio.file.InvalidPathException
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Instant
import groovy.transform.CompileStatic

/**
 * The application {@code run-app} started for a project, as the record of it in {@code .grails/run}
 * says and as the processes of the computer confirm.
 *
 * <p>The record is a file in the directory of the project, and a project can come from anywhere, so
 * what it says is not believed: a process is the one {@code run-app} started only when it has the
 * recorded number <em>and</em> began at the recorded instant, is a process of the user who runs the
 * command, is not the command itself or a process above it, and, where the system shows the command
 * line of a process, is the Gradle wrapper of this project, or the Java program that wrapper becomes,
 * running {@code bootRun}. A number alone is never enough: the system gives the number of a process
 * that has ended to another one.</p>
 *
 * <p>A {@code run-app} that stays with its application runs the build from its own JVM, through the
 * Tooling API of Gradle, and has started no process: it records itself, and says so in the record.
 * Such a process is held to the number, the instant and the user, and to nothing of its command line,
 * which is that of a command line and of no Gradle. It is never ended on the word of a record: it is
 * asked, through a file beside the record that it looks for, and ends its build itself.</p>
 *
 * <p>The record holds the number, the instant and the port, and for an application that is bound to
 * one loopback address that address, where the port is asked; nothing of the command line the
 * process was started with: that can have a password in it, and nothing reads it.</p>
 *
 * @since 9.0
 */
@CompileStatic
final class RunningApplication {

    /** The record, in {@code .grails/run}. */
    public static final String RECORD = 'app.json'

    /** What the build and the application write when {@code run-app} does not stay with them, in {@code .grails/run}. */
    public static final String LOG = 'app.log'

    /**
     * @return whether the record that was found is there still and names the process it named: a
     * {@code run-app} that stays with its application removes it when the build has ended
     */
    boolean isRecorded() {
        return record != null && names(record, pid, started)
    }

    /**
     * Takes back what {@link #askToStop} said, when it is still there: a {@code run-app} that did not
     * look for it in time is not to find it later.
     *
     * @param run the directory {@code .grails/run} of the project
     */
    void withdraw(Path run) {
        Path asked = run.resolve(STOPPING)
        if (names(asked, pid, started)) {
            remove(asked)
        }
    }

    /**
     * Says that {@code stop-app} ends the application, to the {@code run-app} that stays with it; in
     * {@code .grails/run}. It names the process it is for, and is for no other.
     */
    public static final String STOPPING = 'app.stopping'

    private static final int FORMAT = 1
    private static final int MAX_RECORD = 64 * 1024
    private static final int MAX_TAIL = 8 * 1024
    private static final long MAX_PID = Integer.MAX_VALUE
    private static final String TASK = 'bootRun'
    private static final String WRAPPER_JAR = 'gradle/wrapper/gradle-wrapper.jar'
    private static final String WRAPPER_MAIN = 'org.gradle.wrapper.GradleWrapperMain'
    private static final List<String> CLASS_PATH_OPTIONS = ['-classpath', '-cp', '--class-path']

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

    /** The recorded instant the process began, in milliseconds, where the record has one. */
    final long started

    /** The port the application was started for, or 0. */
    final int port

    /**
     * The loopback address the application was bound to, where the port is asked, as
     * {@link ApplicationPort#boundLoopback} gives it; {@code null} where it is asked at
     * {@code 127.0.0.1} and {@code ::1}.
     */
    final String address

    /** Whether {@code run-app} did not stay with the application. */
    final boolean detached

    /**
     * Whether the recorded process is the command line that runs {@code run-app} itself, which is asked
     * to end its build, and not a Gradle that it started, which is ended.
     */
    final boolean commandLine

    private final Path record

    private RunningApplication(State state, String reason, Path record, ProcessHandle process, long pid, long started, int port, boolean detached,
                               boolean commandLine = false, String address = null) {
        this.state = state
        this.reason = reason
        this.record = record
        this.process = process
        this.pid = pid
        this.started = started
        this.port = port
        this.detached = detached
        this.commandLine = commandLine
        this.address = address
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
            return new RunningApplication(State.REFUSED, e.getMessage(), null, null, 0L, 0L, 0, false)
        }
        Path record = run?.resolve(RECORD)
        if (record == null || !Files.exists(record, LinkOption.NOFOLLOW_LINKS)) {
            return new RunningApplication(State.NONE, null, null, null, 0L, 0L, 0, false)
        }
        Map<String, Object> said
        try {
            said = read(record, project)
        } catch (IOException e) {
            return new RunningApplication(State.UNREADABLE, e.getMessage(), record, null, 0L, 0L, 0, false)
        }
        long pid = said.pid as long
        long started = said.started as long
        int port = said.port as int
        boolean detached = said.detached as boolean
        boolean commandLine = said.commandLine as boolean
        String address = said.address as String
        Closure<RunningApplication> not = { State state, String why ->
            new RunningApplication(state, why, record, null, pid, started, port, detached, commandLine, address)
        }
        Optional<ProcessHandle> found = ProcessHandle.of(pid)
        if (!found.isPresent() || !found.get().isAlive()) {
            return not(State.ENDED, "process ${pid}, which was started for it, has ended".toString())
        }
        ProcessHandle process = found.get()
        if (!began(process, started)) {
            return not(State.ENDED, "process ${pid} is not the one that was started for it: it did not begin when the record says".toString())
        }
        // a command line that recorded itself is asked and not ended, so it can be this one or one above it
        if (!commandLine && isThisCommand(process)) {
            return not(State.ENDED, "process ${pid} is this command or one that started it".toString())
        }
        if (!ProcessUser.isOfThisUser(process)) {
            return not(State.ENDED, "process ${pid} is not a process of the user who runs the command".toString())
        }
        if (!commandLine && !isGradleOf(project, windows, process.info())) {
            return not(State.UNCONFIRMED, "process ${pid} began when the record says, but its command line is not that of the Gradle wrapper of this project running ${TASK}".toString())
        }
        return new RunningApplication(State.RUNNING, null, record, process, pid, started, port, detached, commandLine, address)
    }

    /**
     * Writes the record of a process that was started.
     *
     * @param run the directory {@code .grails/run} of the project
     * @param process the process
     * @param port the port the application was started for, or 0
     * @param detached whether {@code run-app} does not stay with the application
     * @param commandLine whether the process is the command line that runs {@code run-app} itself
     * @param address the loopback address the application is bound to, as {@link ApplicationPort#boundLoopback}
     * gives it, or {@code null}
     * @return the instant the process began as the record has it, in milliseconds: what the record is
     * found by again when it is to be removed, which the system no longer says of a process that has ended
     * @throws IOException with one line, when the record cannot be written
     */
    static long record(Path run, ProcessHandle process, int port, boolean detached, boolean commandLine = false, String address = null) throws IOException {
        Optional<Instant> began = process.info().startInstant()
        if (!began.isPresent()) {
            throw new IOException("${RunDirectory.LOCATION}/${RECORD} cannot be written: the system does not say when process ${process.pid()} began, and without that it cannot be told from another one later")
        }
        // said only where it is so: a record of a Gradle that was started, for no address of its own, is what it has been
        String boundTo = address == null ? '' : ",\"address\":\"${address}\""
        String said = "{\"format\":${FORMAT},\"pid\":${process.pid()},\"started\":${began.get().toEpochMilli()},\"port\":${port},\"detached\":${detached}" +
                "${commandLine ? ',\"commandLine\":true' : ''}${boundTo}}\n"
        // written beside the record and moved, so that whoever reads the record reads all of it
        Path written = RunDirectory.create(run, RECORD + '.new')
        try {
            Files.write(written, said.getBytes(StandardCharsets.US_ASCII))
            Path record = run.resolve(RECORD)
            if (Files.isDirectory(record, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException('it is a directory')
            }
            Files.move(written, record, StandardCopyOption.REPLACE_EXISTING)
            return began.get().toEpochMilli()
        } catch (IOException e) {
            Files.deleteIfExists(written)
            throw new IOException("${RunDirectory.LOCATION}/${RECORD} cannot be written: ${GradleRunner.line(e.getMessage())}")
        }
    }

    /**
     * Removes the record that was found, when it is a file or a link. A record that named a process
     * is removed only while it still names that one: another {@code run-app} can have written its
     * own since it was read.
     *
     * @return whether the record that was found is not there any more
     */
    boolean forget() {
        if (record == null) {
            return true
        }
        if (state != State.UNREADABLE && !names(record, pid, started)) {
            return !Files.exists(record, LinkOption.NOFOLLOW_LINKS)
        }
        return remove(record)
    }

    /**
     * Removes the record when it is the one of a process, and no other: the record there is can be
     * the one of an application that was started since.
     *
     * @param run the directory {@code .grails/run} of the project
     * @param pid the number of the process that was recorded
     * @param started the instant it began, in milliseconds
     */
    static void forget(Path run, long pid, long started) {
        if (names(run.resolve(RECORD), pid, started)) {
            remove(run.resolve(RECORD))
        }
    }

    /**
     * Says to the {@code run-app} that stays with this application that its end is asked for.
     *
     * @param run the directory {@code .grails/run} of the project
     * @throws IOException when it cannot be said
     */
    void askToStop(Path run) throws IOException {
        Files.write(RunDirectory.create(run, STOPPING), "{\"pid\":${pid},\"started\":${started}}\n".toString().getBytes(StandardCharsets.US_ASCII))
    }

    /**
     * @param run the directory {@code .grails/run} of the project
     * @param pid the number of a process that {@code run-app} stayed with
     * @param started the instant it began, in milliseconds
     * @return whether {@code stop-app} asked for the end of that process, which is then taken as said;
     * what was said to another process is left for it
     */
    static boolean wasAskedToStop(Path run, long pid, long started) {
        Path asked = run.resolve(STOPPING)
        return names(asked, pid, started) && remove(asked)
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

    // whether a file of the directory names a process by its number and the instant it began
    private static boolean names(Path file, long pid, long started) {
        try {
            if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.size(file) > MAX_RECORD) {
                return false
            }
            Object parsed = BoundedJson.parse(new String(Files.readAllBytes(file), StandardCharsets.UTF_8), 2, 16, 64)
            if (!(parsed instanceof Map)) {
                return false
            }
            Long named = whole(((Map) parsed).get('pid'))
            Long began = whole(((Map) parsed).get('started'))
            return named != null && began != null && named.longValue() == pid && began.longValue() == started
        } catch (IOException | BoundedJson.Refused ignored) {
            return false
        }
    }

    private static boolean began(ProcessHandle process, long started) {
        Optional<Instant> began = process.info().startInstant()
        return began.isPresent() && began.get().toEpochMilli() == started
    }

    // a number that is written with digits alone is read as one of these, and no other number is
    private static Long whole(Object value) {
        return value instanceof Long ? (Long) value : null
    }

    // what a record says: its numbers, whether run-app left the application and whether it recorded itself, and the address, or null
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
            parsed = BoundedJson.parse(new String(Files.readAllBytes(record), StandardCharsets.UTF_8), 2, 16, 64)
        } catch (IOException e) {
            throw new IOException("it cannot be read: ${GradleRunner.line(e.getMessage())}")
        } catch (BoundedJson.Refused ignored) {
            throw new IOException('it is not a record of an application')
        }
        if (!(parsed instanceof Map)) {
            throw new IOException('it is not a record of an application')
        }
        Map said = (Map) parsed
        Long format = whole(said.get('format'))
        if (format == null || format.longValue() != FORMAT) {
            throw new IOException("it is not of format ${FORMAT}, the one this command reads")
        }
        Long pid = whole(said.get('pid'))
        Long started = whole(said.get('started'))
        Long port = whole(said.get('port'))
        Object commandLine = said.get('commandLine')
        if (pid == null || started == null || port == null || !(said.get('detached') instanceof Boolean) || !(commandLine == null || commandLine instanceof Boolean) ||
                pid < 2L || pid > MAX_PID || started < 1L || port < 0L || port > 65535L) {
            throw new IOException('it does not name a process, an instant and a port')
        }
        // the port is asked there, and so the address is one of the loopback, as run-app records it, and nothing else
        Object address = said.get('address')
        String bound = address instanceof String ? ApplicationPort.boundLoopback((String) address) : null
        if (address != null && bound == null) {
            throw new IOException('it names an address that is no loopback address of this computer')
        }
        return [pid: pid, started: started, port: port, detached: said.get('detached'), commandLine: Boolean.TRUE.equals(commandLine), address: bound] as Map<String, Object>
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

    // Whether a command line is that of the Gradle wrapper of the project running bootRun: the wrapper
    // script itself as an argument, with the task after it, as while a shell runs the script; or the
    // program the script becomes, Java with the wrapper jar of the project as its class path or its jar
    // and the task after that. Paths are compared as the files they are, never as text that has another.
    // Where the system does not show the arguments of a process there is nothing to compare, and the
    // number, the instant and the user decide.
    private static boolean isGradleOf(ConsoleProject project, boolean windows, ProcessHandle.Info info) {
        Optional<String[]> shown = info.arguments()
        if (!shown.isPresent()) {
            return true
        }
        Path wrapper = project.gradleWrapper(windows).file
        if (wrapper == null) {
            return false
        }
        List<String> arguments = shown.get().toList()
        // as run-app names it: by the path of the project where the project is a part of a larger build
        String task = project.task(TASK)
        Path jar = wrapper.getParent().resolve(WRAPPER_JAR)
        for (int i = 0; i < arguments.size(); i++) {
            List<String> after = arguments.subList(i + 1, arguments.size())
            if (!(task in after)) {
                return false
            }
            if (same(arguments.get(i), wrapper)) {
                return true
            }
            if (i > 0 && same(arguments.get(i), jar) &&
                    (arguments.get(i - 1) == '-jar' || (arguments.get(i - 1) in CLASS_PATH_OPTIONS && WRAPPER_MAIN in after && after.indexOf(WRAPPER_MAIN) < after.lastIndexOf(task)))) {
                return true
            }
        }
        return false
    }

    private static boolean same(String argument, Path file) {
        try {
            Path named = Path.of(argument)
            return named.isAbsolute() && Files.exists(named) && Files.isSameFile(named, file)
        } catch (InvalidPathException | IOException ignored) {
            return false
        }
    }
}

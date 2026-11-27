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
import java.nio.charset.StandardCharsets
import java.util.concurrent.Callable
import groovy.transform.CompileStatic

/**
 * Where one command line that is run writes: its output and its errors. A command line the program
 * {@code grails} runs writes where the process does, to {@code System.out} and {@code System.err};
 * one that a program runs in its own process, as {@code grails mcp} runs each call, writes to streams
 * of its own, which are bound to the thread that runs it for as long as it runs.
 *
 * <p>Everything that writes for a command and is not the command itself, as what runs Gradle or a
 * tool for it, asks {@link #current()} where to write, once, on the thread that runs the command, and
 * hands that on to the threads that copy what Gradle or the tool writes.</p>
 *
 * <p>{@link #capture()} makes streams that keep what is written, in the order it is written, and
 * apart for the output and the errors.</p>
 *
 * @since 9.0
 */
@CompileStatic
final class CommandStreams {

    private static final Charset CAPTURED = StandardCharsets.UTF_8

    // Inherited by the threads that are started while a command runs, so that what such a thread writes for the
    // command goes where the command writes. A thread that was started before, as one of a pool, has to be handed
    // the streams by what runs the command.
    private static final InheritableThreadLocal<CommandStreams> BOUND = new InheritableThreadLocal<>()

    /** What the command writes as its output. */
    final PrintStream out

    /**
     * What the command writes as its errors and warnings. What it writes here is part of everything
     * it wrote as well, in the order it was written.
     */
    final PrintStream err

    private final Captured captured

    private CommandStreams(PrintStream out, PrintStream err, Captured captured) {
        this.out = out
        this.err = err
        this.captured = captured
    }

    /**
     * Where a command that runs on this thread writes.
     *
     * @return the streams bound with {@link #bind} to this thread, or to the thread that started it
     * while they were bound there; where none are, {@code System.out} and {@code System.err} as they
     * are now
     */
    static CommandStreams current() {
        CommandStreams bound = BOUND.get()
        return bound != null ? bound : new CommandStreams(System.out, System.err, null)
    }

    /**
     * The standard error of the process, which is never the output of a command that runs: where what
     * is said to the user who started the process goes, and not to whoever asked for the command, as the
     * diagnostics of the command line. Where nothing has replaced {@code System.err} to send what is
     * written there to the command that runs, it is {@code System.err}.
     *
     * @return the standard error of the process
     */
    static PrintStream processErr() {
        return System.err
    }

    /**
     * Runs something with streams bound to this thread: while it runs, {@link #current()} is these
     * streams on this thread and on every thread that is started from it. What was bound before is
     * bound again when it ends, however it ends.
     *
     * @param streams where what runs writes
     * @param call what runs
     * @return what it gives
     * @throws Exception whatever it throws
     */
    static <T> T bind(CommandStreams streams, Callable<T> call) throws Exception {
        Objects.requireNonNull(streams, 'streams')
        Objects.requireNonNull(call, 'call')
        CommandStreams before = BOUND.get()
        BOUND.set(streams)
        try {
            return call.call()
        } finally {
            if (before == null) {
                BOUND.remove()
            } else {
                BOUND.set(before)
            }
        }
    }

    /**
     * Streams that keep what is written to them, for a command whose output is read when it has ended,
     * or while it runs. What is written is kept as UTF-8, three times: everything in the order it was
     * written ({@link #merged()}), the output ({@link #standardOutput()}) and the errors
     * ({@link #standardError()}). Several threads can write to them at once.
     *
     * @return new streams that have kept nothing yet
     */
    static CommandStreams capture() {
        Captured captured = new Captured()
        return new CommandStreams(new PrintStream(new Into(captured, captured.output), true, CAPTURED),
                new PrintStream(new Into(captured, captured.error), true, CAPTURED), captured)
    }

    /**
     * @return everything that was written to the streams so far, the output and the errors in the
     * order they were written
     * @throws IllegalStateException for streams that were not made by {@link #capture()}
     */
    String merged() {
        return text(captured().merged)
    }

    /**
     * @return what was written to {@link #out} so far
     * @throws IllegalStateException for streams that were not made by {@link #capture()}
     */
    String standardOutput() {
        return text(captured().output)
    }

    /**
     * @return what was written to {@link #err} so far
     * @throws IllegalStateException for streams that were not made by {@link #capture()}
     */
    String standardError() {
        return text(captured().error)
    }

    private Captured captured() {
        if (captured == null) {
            throw new IllegalStateException('These streams keep nothing: only those of CommandStreams.capture() do')
        }
        return captured
    }

    private String text(ByteArrayOutputStream buffer) {
        synchronized (captured) {
            return buffer.toString(CAPTURED)
        }
    }

    // what captured streams keep; it is also the lock that keeps what is written to both streams in one order
    @CompileStatic
    private static final class Captured {

        final ByteArrayOutputStream merged = new ByteArrayOutputStream()
        final ByteArrayOutputStream output = new ByteArrayOutputStream()
        final ByteArrayOutputStream error = new ByteArrayOutputStream()
    }

    // writes into one of the buffers and into the one of everything
    @CompileStatic
    private static final class Into extends OutputStream {

        private final Captured captured
        private final ByteArrayOutputStream own

        Into(Captured captured, ByteArrayOutputStream own) {
            this.captured = captured
            this.own = own
        }

        @Override
        void write(int b) {
            synchronized (captured) {
                own.write(b)
                captured.merged.write(b)
            }
        }

        @Override
        void write(byte[] bytes, int offset, int length) {
            synchronized (captured) {
                own.write(bytes, offset, length)
                captured.merged.write(bytes, offset, length)
            }
        }
    }
}

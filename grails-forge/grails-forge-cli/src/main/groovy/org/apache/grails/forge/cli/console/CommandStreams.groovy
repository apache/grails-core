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
import java.util.function.Consumer
import groovy.transform.CompileStatic
import groovy.transform.PackageScope

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
 * <p>{@link #guard()} keeps the standard output of the process for one writer: a program whose
 * standard output is read by another program, as {@code grails mcp} is, has everything else that
 * writes to {@code System.out} or {@code System.err} write for the command line that is run on its
 * thread, or to the standard error of the process where none is.</p>
 *
 * @since 9.0
 */
@CompileStatic
final class CommandStreams {

    private static final Charset CAPTURED = StandardCharsets.UTF_8
    private static final int NEW_LINE = 10
    private static final int RETURN = 13

    // Inherited by the threads that are started while a command runs, so that what such a thread writes for the
    // command goes where the command writes. A thread that was started before, as one of a pool, has to be handed
    // the streams by what runs the command. A thread that was started while a command ran and outlives it, as one of a
    // pool or of a connection that is kept, inherited a binding that ends with the command: it writes where nothing
    // is bound from then on, and does not take the streams of a command that has ended for its own.
    private static final InheritableThreadLocal<Binding> BOUND = new InheritableThreadLocal<>()

    private static volatile Guard installed

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
     * while they were bound there, for as long as what they were bound for runs; where none are,
     * {@code System.out} and {@code System.err} as they are now
     */
    static CommandStreams current() {
        CommandStreams bound = bound()
        return bound != null ? bound : new CommandStreams(System.out, System.err, null)
    }

    /**
     * The standard error of the process, which is never the output of a command that runs: where what
     * is said to the user who started the process goes, and not to whoever asked for the command, as the
     * diagnostics of the command line. Where nothing has replaced {@code System.err} to send what is
     * written there to the command that runs, it is {@code System.err}; where {@link #guard()} has, it is
     * the standard error the process had before.
     *
     * @return the standard error of the process
     */
    static PrintStream processErr() {
        Guard guard = installed
        return guard != null ? guard.error : System.err
    }

    /**
     * Runs something with streams bound to this thread: while it runs, {@link #current()} is these
     * streams on this thread and on every thread that is started from it. What was bound before is
     * bound again when it ends, however it ends, unless it was bound for something that has ended since,
     * as for a thread of a pool that was started while another command ran.
     *
     * @param streams where what runs writes
     * @param call what runs
     * @return what it gives
     * @throws Exception whatever it throws
     */
    static <T> T bind(CommandStreams streams, Callable<T> call) throws Exception {
        Objects.requireNonNull(streams, 'streams')
        Objects.requireNonNull(call, 'call')
        Binding before = BOUND.get()
        Binding binding = new Binding(streams)
        BOUND.set(binding)
        try {
            return call.call()
        } finally {
            // the threads that were started while it ran see that it has ended, and write where nothing is bound
            binding.open = false
            if (before == null || !before.open) {
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
        return capture(null)
    }

    /**
     * Streams that keep what is written to them as {@link #capture()} does, and give each line to a
     * listener as soon as it is complete, for a program that shows how a command it runs is getting on.
     * A line is what is written to {@link #out} or to {@link #err} up to a line feed, without the line
     * feed and a carriage return before it, read as UTF-8; the output and the errors are cut into lines
     * apart, so a line of one is never broken by what is written to the other. {@link #lineCount()} and
     * {@link #lastLine()} say the same to a program that asks now and then instead.
     *
     * <p>The listener is called on the thread that completed the line, after the line was kept and
     * outside the lock of the streams: lines that several threads complete at once may reach it in
     * another order than {@link #merged()} has them. It is to return quickly, and must not write to
     * these streams. What it throws is ignored, so that what the command writes is never refused for it.</p>
     *
     * @param eachLine given each line as it is completed; {@code null} for none
     * @return new streams that have kept nothing yet
     */
    static CommandStreams capture(Consumer<String> eachLine) {
        Captured captured = new Captured(eachLine)
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

    /**
     * @return how many lines were completed so far, of the output and the errors together; a line is
     * what {@link #capture(Consumer)} says it is
     * @throws IllegalStateException for streams that were not made by {@link #capture()}
     */
    int lineCount() {
        Captured kept = captured()
        synchronized (kept) {
            return kept.lines
        }
    }

    /**
     * @return the line that was completed last, of the output or the errors, or {@code null} when none
     * was yet
     * @throws IllegalStateException for streams that were not made by {@link #capture()}
     */
    String lastLine() {
        Captured kept = captured()
        synchronized (kept) {
            return kept.last
        }
    }

    /**
     * Keeps the standard output of this process for one writer, a program that speaks a protocol on
     * it, as {@code grails mcp} does: {@link Guard#takeOutput()} gives it, once, and nothing else writes
     * there from now on. {@code System.out} and {@code System.err} write for the command that runs on
     * the thread that writes, to its {@link #out} and its {@link #err}, and to the standard error of the
     * process where none runs; {@code System.in} has ended, and the standard input is kept for the same
     * program ({@link Guard#takeInput()}). A command line that is run in the process writes where it is
     * bound to write, as everywhere else, and starts Gradle and the tools with their output piped into
     * that and their input ended, never with the terminal of the process.
     *
     * <p>{@code grails} keeps the standard output for {@code grails mcp} first, before anything else
     * runs; the guard stays for as long as the process does. Asked for again, this gives the guard that
     * is there.</p>
     *
     * @return the guard of this process
     * @since 9.0
     */
    static Guard guard() {
        synchronized (CommandStreams) {
            Guard guard = installed
            return guard != null ? guard : install(System.in, new FileOutputStream(FileDescriptor.out), System.err)
        }
    }

    /**
     * Keeps streams as {@link #guard()} keeps the standard streams of the process: for a program that
     * speaks the protocol of {@code grails mcp} with a process of its own in this JVM, as a specification
     * does, which gives the streams the process would have.
     *
     * @param input what is read in the place of the standard input of the process
     * @param output what is written in the place of its standard output
     * @param error what is written in the place of its standard error
     * @return the guard, which puts back what {@code System.in}, {@code System.out} and {@code System.err}
     * were when it is closed
     * @throws IllegalStateException when a guard is there already
     * @since 9.0
     */
    static Guard guard(InputStream input, OutputStream output, PrintStream error) {
        Objects.requireNonNull(input, 'input')
        Objects.requireNonNull(output, 'output')
        Objects.requireNonNull(error, 'error')
        synchronized (CommandStreams) {
            if (installed != null) {
                throw new IllegalStateException('The standard streams of this process are guarded already')
            }
            return install(input, output, error)
        }
    }

    /**
     * @return the guard that keeps the standard output of this process, or {@code null} where none does
     * @since 9.0
     */
    static Guard guarded() {
        return installed
    }

    private static Guard install(InputStream input, OutputStream output, PrintStream error) {
        Guard guard = new Guard(input, output, error)
        installed = guard
        System.setIn(guard.emptyInput)
        System.setOut(guard.routedOut)
        System.setErr(guard.routedErr)
        return guard
    }

    private static CommandStreams bound() {
        Binding binding = BOUND.get()
        return binding != null && binding.open ? binding.streams : null
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

    /**
     * What keeps the standard streams of a process for a program that speaks a protocol on them: see
     * {@link CommandStreams#guard()}.
     *
     * @since 9.0
     */
    @CompileStatic
    static final class Guard implements Closeable {

        /** The standard error of the process, as it was before the guard: see {@link CommandStreams#processErr()}. */
        final PrintStream error

        private final InputStream input
        private final OutputStream output
        private final InputStream emptyInput = new ByteArrayInputStream(new byte[0])
        private final PrintStream routedOut
        private final PrintStream routedErr
        private final InputStream previousIn = System.in
        private final PrintStream previousOut = System.out
        private final PrintStream previousErr = System.err
        private boolean inputTaken
        private boolean outputTaken

        private Guard(InputStream input, OutputStream output, PrintStream error) {
            this.input = input
            this.output = output
            this.error = error
            this.routedOut = new PrintStream(new Router(this, true), true, CAPTURED)
            this.routedErr = new PrintStream(new Router(this, false), true, CAPTURED)
        }

        /**
         * @return the standard output of the process, for the one program that writes there
         * @throws IllegalStateException when it was taken before
         */
        synchronized OutputStream takeOutput() {
            if (outputTaken) {
                throw new IllegalStateException('The standard output of this process was taken already: it has one writer')
            }
            outputTaken = true
            return output
        }

        /**
         * @return the standard input of the process, for the one program that reads it
         * @throws IllegalStateException when it was taken before
         */
        synchronized InputStream takeInput() {
            if (inputTaken) {
                throw new IllegalStateException('The standard input of this process was taken already: it has one reader')
            }
            inputTaken = true
            return input
        }

        /**
         * Puts back what {@code System.in}, {@code System.out} and {@code System.err} were before the
         * guard, each where it is still what the guard made it, for a program that runs the protocol in a
         * JVM that goes on after it. The streams the guard kept are not closed: they are their taker's.
         */
        @Override
        void close() {
            synchronized (CommandStreams) {
                if (!installed.is(this)) {
                    return
                }
                installed = null
                if (System.in.is(emptyInput)) {
                    System.setIn(previousIn)
                }
                if (System.out.is(routedOut)) {
                    System.setOut(previousOut)
                }
                if (System.err.is(routedErr)) {
                    System.setErr(previousErr)
                }
            }
        }

        // where a write to System.out or System.err goes now, on the thread that writes
        @PackageScope
        PrintStream target(boolean output) {
            CommandStreams bound = bound()
            PrintStream to = bound == null ? null : (output ? bound.out : bound.err)
            // streams that are the process's own, as current() gives them where nothing is bound, write where nothing is bound
            return to == null || to.is(routedOut) || to.is(routedErr) ? error : to
        }
    }

    // System.out or System.err of a guarded process
    @CompileStatic
    private static final class Router extends OutputStream {

        private final Guard guard
        private final boolean output

        Router(Guard guard, boolean output) {
            this.guard = guard
            this.output = output
        }

        @Override
        void write(int b) {
            guard.target(output).write(b)
        }

        @Override
        void write(byte[] bytes, int offset, int length) {
            guard.target(output).write(bytes, offset, length)
        }

        @Override
        void flush() {
            guard.target(output).flush()
        }
    }

    // the streams a command is bound to, for as long as what they are bound for runs
    @CompileStatic
    private static final class Binding {

        final CommandStreams streams
        volatile boolean open = true

        Binding(CommandStreams streams) {
            this.streams = streams
        }
    }

    // what captured streams keep; it is also the lock that keeps what is written to both streams in one order
    @CompileStatic
    private static final class Captured {

        final ByteArrayOutputStream merged = new ByteArrayOutputStream()
        final ByteArrayOutputStream output = new ByteArrayOutputStream()
        final ByteArrayOutputStream error = new ByteArrayOutputStream()
        final Consumer<String> listener
        int lines
        String last

        Captured(Consumer<String> listener) {
            this.listener = listener
        }
    }

    // writes into one of the buffers and into the one of everything, and cuts what it is given into lines
    @CompileStatic
    private static final class Into extends OutputStream {

        private final Captured captured
        private final ByteArrayOutputStream own
        // the line that is not complete yet
        private final ByteArrayOutputStream pending = new ByteArrayOutputStream()

        Into(Captured captured, ByteArrayOutputStream own) {
            this.captured = captured
            this.own = own
        }

        @Override
        void write(int b) {
            byte[] one = [(byte) b] as byte[]
            write(one, 0, 1)
        }

        @Override
        void write(byte[] bytes, int offset, int length) {
            List<String> completed = null
            synchronized (captured) {
                own.write(bytes, offset, length)
                captured.merged.write(bytes, offset, length)
                int start = offset
                int end = offset + length
                for (int i = offset; i < end; i++) {
                    if (bytes[i] == (byte) NEW_LINE) {
                        pending.write(bytes, start, i - start)
                        String line = line()
                        captured.lines++
                        captured.last = line
                        if (captured.listener != null) {
                            completed = completed ?: new ArrayList<String>()
                            completed.add(line)
                        }
                        start = i + 1
                    }
                }
                pending.write(bytes, start, end - start)
            }
            if (completed != null) {
                for (String line : completed) {
                    tell(line)
                }
            }
        }

        // the pending line, without a carriage return at its end, which is then pending no longer
        private String line() {
            byte[] read = pending.toByteArray()
            pending.reset()
            int length = read.length > 0 && read[read.length - 1] == (byte) RETURN ? read.length - 1 : read.length
            return new String(read, 0, length, CAPTURED)
        }

        private void tell(String line) {
            try {
                captured.listener.accept(line)
            } catch (RuntimeException ignored) {
                // what the command writes is kept all the same
            }
        }
    }
}

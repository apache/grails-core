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
package org.apache.grails.forge.cli.mcp.protocol

import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Consumer
import java.util.function.IntConsumer
import groovy.transform.CompileStatic

/**
 * How {@code grails mcp} ends: the SDK ends the protocol, and the server ends what it holds for the session.
 *
 * <p>The server ends at the end of its input, which the SDK signals, on SIGINT or SIGTERM, which a hook of
 * the JVM is told of, and when the server asks to end with {@link #requestExit}. However it ends, and however
 * often it is asked to, it ends once and in this order ({@link #shutdown}):</p>
 * <ol>
 * <li>it takes no new calls: a call that arrives is answered with {@code -32603};</li>
 * <li>it interrupts every running call, and waits for them to end, {@linkplain Reason#getGrace() as long as
 * the reason allows};</li>
 * <li>it closes the SDK, which ends each {@code subscriptions/listen} and then the transport;</li>
 * <li>it closes what the session holds, with what was given to {@link #onShutdown}, in the order it was
 * given;</li>
 * <li>it exits with the exit code, unless the JVM ends already, as it does on a signal.</li>
 * </ol>
 *
 * @since 9.0
 */
@CompileStatic
final class McpLifecycle {

    /**
     * Why the server ends, and how long it waits for the running calls to end once it has interrupted them.
     *
     * @since 9.0
     */
    @CompileStatic
    static enum Reason {

        /** The client closed the server's input, which is how a client ends a server. */
        END_OF_INPUT(Duration.ofSeconds(5)),

        /**
         * The process was sent SIGINT or SIGTERM. A client that sends them kills the process soon after, as
         * Claude Code does 500 ms after SIGINT.
         */
        SIGNAL(Duration.ofMillis(300)),

        /** The server asked to end, with {@link McpLifecycle#requestExit}. */
        EXIT(Duration.ofSeconds(5))

        /** How long the server waits for the running calls to end once it has interrupted them. */
        final Duration grace

        Reason(Duration grace) {
            this.grace = grace
        }
    }

    // the graces a specification gave in place of those of the reasons
    private static final Map<Reason, Duration> GIVEN_GRACES = new ConcurrentHashMap<>()

    /** Exits the JVM with the exit code: what ends {@code grails mcp}, unless something else is given. */
    static final IntConsumer SYSTEM_EXIT = { int exitCode -> System.exit(exitCode) } as IntConsumer

    /** Has the JVM run what it is given when it is sent SIGINT or SIGTERM: a shutdown hook. */
    static final Consumer<Runnable> SHUTDOWN_HOOK = { Runnable onSignal ->
        Runtime.getRuntime().addShutdownHook(new Thread(onSignal, 'grails-mcp-shutdown'))
    } as Consumer<Runnable>

    private final ModernServer server
    private final PrintStream diagnostics
    private final IntConsumer exit
    private final Consumer<Runnable> signals
    private final List<Closer> closers = new CopyOnWriteArrayList<>()
    private final AtomicBoolean served = new AtomicBoolean()
    private final AtomicBoolean ending = new AtomicBoolean()
    private final CountDownLatch ended = new CountDownLatch(1)
    private final CompletableFuture<Integer> exitRequested = new CompletableFuture<>()
    private volatile Reason reason

    /**
     * A lifecycle that exits the JVM, and is told of SIGINT and SIGTERM by a shutdown hook.
     *
     * @param server the server
     * @param diagnostics where what could not be closed is said: the standard error of the process
     */
    McpLifecycle(ModernServer server, PrintStream diagnostics) {
        this(server, diagnostics, SYSTEM_EXIT, SHUTDOWN_HOOK)
    }

    /**
     * @param server the server
     * @param diagnostics where what could not be closed is said: the standard error of the process
     * @param exit what ends the process with an exit code, once the server has ended
     * @param signals what has a {@link Runnable} run when the process is sent SIGINT or SIGTERM, from a
     * thread that is not the one that {@linkplain #serve serves}; the JVM ends when it has run
     */
    McpLifecycle(ModernServer server, PrintStream diagnostics, IntConsumer exit, Consumer<Runnable> signals) {
        this.server = Objects.requireNonNull(server, 'server')
        this.diagnostics = Objects.requireNonNull(diagnostics, 'diagnostics')
        this.exit = Objects.requireNonNull(exit, 'exit')
        this.signals = Objects.requireNonNull(signals, 'signals')
    }

    /**
     * Replaces how long the server waits for the running calls to end once it has interrupted them, when it ends
     * for a reason, for a specification: one that holds what is waited for without waiting it out in full, or
     * that tells the waits of two reasons apart by more than the time a slow computer takes.
     *
     * @param reason the reason
     * @param grace the time; {@code null} for the {@linkplain Reason#grace grace of the reason}
     */
    static void setGrace(Reason reason, Duration grace) {
        if (grace == null) {
            GIVEN_GRACES.remove(reason)
        } else {
            GIVEN_GRACES.put(reason, grace)
        }
    }

    /**
     * @param reason the reason
     * @return how long the server waits for the running calls to end when it ends for the reason
     */
    static Duration graceOf(Reason reason) {
        return GIVEN_GRACES.getOrDefault(reason, reason.grace)
    }

    /**
     * Has something the session holds closed when the server ends, after the SDK is closed: the connection
     * to Gradle, the project generator, the applications the session started. They are closed in the order
     * they are given, each once; one that fails is said on the diagnostics and does not keep the others open.
     *
     * @param name what is closed, as the diagnostics name it
     * @param closer what closes it
     * @return this lifecycle
     */
    McpLifecycle onShutdown(String name, Runnable closer) {
        closers << new Closer(Objects.requireNonNull(name, 'name'), Objects.requireNonNull(closer, 'closer'))
        return this
    }

    /**
     * Serves until the server ends, on the thread that calls it, which is the main thread of the process:
     * starts the server, has the signals end it, waits for the end of its input or for {@link #requestExit},
     * then {@linkplain #shutdown ends} it and exits.
     *
     * @return the exit code, where the exit given returns; on a signal, the JVM ends without it
     * @throws IllegalStateException when it is called a second time
     */
    int serve() {
        if (!served.compareAndSet(false, true)) {
            throw new IllegalStateException('A server is served once')
        }
        signals.accept({ -> shutdown(Reason.SIGNAL) } as Runnable)
        CompletableFuture<Void> input = server.start()
        try {
            CompletableFuture.anyOf(input, exitRequested).get()
        } catch (ExecutionException ignored) {
            // the input could not be read any further, which ends the server as its end does
        } catch (InterruptedException ignored) {
            // the main thread is not interrupted but to end the server, which follows
        }
        shutdown(exitRequested.isDone() ? Reason.EXIT : Reason.END_OF_INPUT)
        int exitCode = exitRequested.getNow(0)
        if (reason == Reason.SIGNAL) {
            // the JVM ends with the signal; exiting while it runs its hooks would never return
            return exitCode
        }
        exit.accept(exitCode)
        return exitCode
    }

    /**
     * Has the server end with an exit code, as it does at the end of its input: the thread that
     * {@linkplain #serve serves} ends it. It can be called on any thread, a thread that runs a call included;
     * it takes no new calls from then on, and returns at once. Only the first request counts.
     *
     * @param exitCode the exit code
     */
    void requestExit(int exitCode) {
        server.calls.stopTaking()
        exitRequested.complete(exitCode)
    }

    /**
     * Ends the server, in the order this class describes, on the thread that calls it, which is the one that
     * {@linkplain #serve serves} or the one that is told of a signal, and never one of the SDK. It ends it
     * once: another call waits until it has ended, and then returns.
     *
     * @param reason why it ends
     */
    void shutdown(Reason reason) {
        if (!ending.compareAndSet(false, true)) {
            awaitEnded()
            return
        }
        this.reason = Objects.requireNonNull(reason, 'reason')
        try {
            CallThreads calls = server.calls
            calls.stopTaking()
            calls.interruptAll()
            awaitCalls(calls, graceOf(reason))
            server.close()
            closers.each { Closer closer -> close(closer) }
        } finally {
            ended.countDown()
        }
    }

    /**
     * @return why the server ends, once it ends; {@code null} until then
     */
    Reason getReason() {
        return reason
    }

    private static void awaitCalls(CallThreads calls, Duration wait) {
        try {
            calls.awaitIdle(wait)
        } catch (InterruptedException ignored) {
            // the server ends all the same
        }
    }

    private void close(Closer closer) {
        try {
            closer.closer.run()
        } catch (Exception e) {
            diagnostics.println("| Error grails mcp could not close ${closer.name}: ${e.message ?: e.class.name}")
        }
    }

    private void awaitEnded() {
        boolean interrupted = false
        while (true) {
            try {
                ended.await()
                break
            } catch (InterruptedException ignored) {
                interrupted = true
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt()
        }
    }

    @CompileStatic
    private static final class Closer {

        final String name
        final Runnable closer

        Closer(String name, Runnable closer) {
            this.name = name
            this.closer = closer
        }
    }
}

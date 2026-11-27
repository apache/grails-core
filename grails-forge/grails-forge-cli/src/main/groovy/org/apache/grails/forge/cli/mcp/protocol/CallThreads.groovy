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
import java.util.concurrent.Callable
import java.util.concurrent.TimeUnit
import groovy.transform.CompileStatic
import groovy.transform.PackageScope
import org.apache.grails.forge.cli.mcp.McpCallContext
import org.apache.grails.forge.cli.mcp.McpException

/**
 * The calls of tools that are running in {@code grails mcp}, each on the thread of the protocol's library
 * that runs it: which they are, since when they run, and since when they were interrupted, when they were.
 *
 * <p>A call is interrupted when the client cancels it, which the library does, and when the server ends,
 * which {@link #interruptAll()} does. A call that has been interrupted is cancelled: its
 * {@link McpCallContext#isCancelled()} is {@code true}. A call that does not stop when it is interrupted
 * stays among the {@link #running()} calls until it ends or is {@linkplain #abandon abandoned}, which is
 * what the server does with a call that has not stopped long after its interrupt.</p>
 *
 * <p>When a call ends, its thread goes back to the library with its interrupt flag cleared, so that an
 * interrupt meant for a call never reaches what the library runs next on that thread.</p>
 *
 * <p>Once the server ends, it takes no new calls: a call that arrives then is refused with
 * {@link McpException#INTERNAL_ERROR}.</p>
 *
 * @since 9.0
 */
@CompileStatic
final class CallThreads {

    /** What a call that arrives while the server ends is told. */
    public static final String ENDING = 'The server is ending'

    private final Object lock = new Object()

    // guarded by lock; by the context of the call, which is one per call
    private final Map<McpCallContext, RunningCall> calls = new IdentityHashMap<>()

    // guarded by lock
    private boolean taking = true

    /**
     * Runs a call on this thread, as one of the running calls, until it ends. Its thread's interrupt flag
     * is cleared when it ends, however it ends.
     *
     * @param id the JSON-RPC id of the request of the call
     * @param tool the name of the tool called
     * @param context the call, as the handler of the server is given it
     * @param onInterrupt what is done when the server interrupts the call, before its thread is interrupted
     * @param body the call
     * @return what the call gives
     * @throws McpException with {@link McpException#INTERNAL_ERROR} when the server takes no more calls
     * @throws Exception whatever the call throws
     */
    @PackageScope
    <T> T run(Object id, String tool, McpCallContext context, Runnable onInterrupt, Callable<T> body) throws Exception {
        RunningCall call = new RunningCall(id, tool, context, Thread.currentThread(), onInterrupt)
        synchronized (lock) {
            if (!taking) {
                throw new McpException(McpException.INTERNAL_ERROR, ENDING)
            }
            calls.put(context, call)
        }
        if (context.isCancelled()) {
            // cancelled while it was on its way to this thread: the library interrupts the thread as well
            call.markInterrupted()
        }
        try {
            return body.call()
        } finally {
            synchronized (lock) {
                if (calls.get(context).is(call)) {
                    calls.remove(context)
                }
                lock.notifyAll()
            }
            // after the call has left the running calls, which are interrupted while the lock is held:
            // no interrupt for it can arrive after this
            Thread.interrupted()
        }
    }

    /**
     * Notes that the library is interrupting a call, as it does when the client cancels the call.
     *
     * @param context the call
     */
    @PackageScope
    void interrupted(McpCallContext context) {
        synchronized (lock) {
            calls.get(context)?.markInterrupted()
        }
    }

    /**
     * Takes no new calls from now on: a call that arrives is refused with {@link McpException#INTERNAL_ERROR}.
     * The calls that are running go on.
     */
    void stopTaking() {
        synchronized (lock) {
            taking = false
        }
    }

    /**
     * @return whether new calls are taken
     */
    boolean isTaking() {
        synchronized (lock) {
            return taking
        }
    }

    /**
     * Interrupts every running call: each is cancelled, and its thread is interrupted.
     *
     * @return how many calls were interrupted
     */
    int interruptAll() {
        synchronized (lock) {
            // while the lock is held, so that no thread is interrupted after its call has ended
            calls.values().each { RunningCall call -> call.interrupt() }
            return calls.size()
        }
    }

    /**
     * Waits until no call is running, or until the time is up.
     *
     * @param timeout how long to wait at most
     * @return whether no call is running
     * @throws InterruptedException when the thread that waits is interrupted
     */
    boolean awaitIdle(Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos()
        synchronized (lock) {
            while (!calls.isEmpty()) {
                long left = deadline - System.nanoTime()
                if (left <= 0) {
                    return false
                }
                TimeUnit.NANOSECONDS.timedWait(lock, left)
            }
            return true
        }
    }

    /**
     * @return the calls that are running now, the longest running first
     */
    List<RunningCall> running() {
        List<RunningCall> running
        synchronized (lock) {
            running = new ArrayList<RunningCall>(calls.values())
        }
        return running.sort(false) { RunningCall call -> call.startedNanos() }
    }

    /**
     * @param atLeast how long ago at least they were interrupted
     * @return the calls that are still running although they were interrupted at least that long ago, the
     * longest running first
     */
    List<RunningCall> interruptedFor(Duration atLeast) {
        return running().findAll { RunningCall call ->
            Duration interrupted = call.interruptedFor
            interrupted != null && interrupted >= atLeast
        }
    }

    /**
     * Stops counting a call among the running calls, though its thread goes on: it is no longer waited for,
     * and is no longer interrupted. What it gives when it ends is dropped by the library, as it is for every
     * interrupted call.
     *
     * @param call a call that was running
     * @return whether it was running until now
     */
    boolean abandon(RunningCall call) {
        synchronized (lock) {
            if (!calls.get(call.context).is(call)) {
                return false
            }
            calls.remove(call.context)
            lock.notifyAll()
            return true
        }
    }

    /**
     * A call that is running: which it is, on which thread, since when, and since when it was interrupted.
     *
     * @since 9.0
     */
    @CompileStatic
    static final class RunningCall {

        /** The JSON-RPC id of the request of the call. */
        final Object id

        /** The name of the tool called. */
        final String tool

        /** The call, as the handler of the server was given it. */
        final McpCallContext context

        /** The thread that runs the call. */
        final Thread thread

        private final long started = System.nanoTime()
        private final Runnable onInterrupt
        private volatile Long interrupted

        private RunningCall(Object id, String tool, McpCallContext context, Thread thread, Runnable onInterrupt) {
            this.id = id
            this.tool = tool
            this.context = context
            this.thread = thread
            this.onInterrupt = onInterrupt
        }

        /**
         * @return how long the call has been running
         */
        Duration getRunningFor() {
            return Duration.ofNanos(System.nanoTime() - started)
        }

        /**
         * @return whether the call has been interrupted
         */
        boolean isInterrupted() {
            return interrupted != null
        }

        /**
         * @return how long ago the call was first interrupted, or {@code null} when it was not
         */
        Duration getInterruptedFor() {
            Long at = interrupted
            return at == null ? null : Duration.ofNanos(System.nanoTime() - at)
        }

        @Override
        String toString() {
            return "${tool} (${id})".toString()
        }

        @PackageScope
        long startedNanos() {
            return started
        }

        @PackageScope
        void markInterrupted() {
            if (interrupted == null) {
                interrupted = System.nanoTime()
            }
        }

        @PackageScope
        void interrupt() {
            markInterrupted()
            onInterrupt.run()
            thread.interrupt()
        }
    }
}

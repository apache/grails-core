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
package org.apache.grails.forge.cli.mcp.run

import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.ReentrantLock
import java.util.function.Consumer
import groovy.transform.CompileStatic
import org.apache.grails.forge.cli.mcp.Lane

/**
 * The lanes the calls of {@code grails mcp} run in, beside each other: {@link Lane#BUILD} and
 * {@link Lane#GENERATOR} take one call at a time, in the order the calls came, and {@link Lane#DIRECT}
 * up to {@value #DIRECT_WIDTH} at once. A call that finds its lane taken waits for it in a queue, is told
 * now and then which tool holds it while it keeps its place there, and stops waiting when its thread is
 * interrupted, as it is when the client cancels the call: it then leaves the queue, and has taken nothing.
 * The order is the one in which the calls reach their lane, each on the thread of the protocol's library
 * that runs it.
 *
 * <p>What a call takes is a {@link Ticket}, which is given back once: by the command when it has ended,
 * or by the server when it gives up on a command that does not end. Giving it back again does nothing,
 * so a lane is never given back twice.</p>
 *
 * @since 9.0
 */
@CompileStatic
final class CallLanes {

    /** How many calls of {@link Lane#DIRECT} run at once, at most. */
    public static final int DIRECT_WIDTH = 4

    private final Duration waitingEvery
    private final Map<Lane, Places> lanes = new EnumMap<>(Lane)
    // guarded by itself: what holds each lane now
    private final List<Ticket> holders = []

    /**
     * @param waitingEvery how often a call that waits for its lane is told that it does
     */
    CallLanes(Duration waitingEvery) {
        this.waitingEvery = Objects.requireNonNull(waitingEvery, 'waitingEvery')
        lanes.put(Lane.BUILD, new Places(1))
        lanes.put(Lane.GENERATOR, new Places(1))
        lanes.put(Lane.DIRECT, new Places(DIRECT_WIDTH))
    }

    /**
     * Takes a place in a lane, and waits for it as long as the lane is taken.
     *
     * @param lane the lane
     * @param tool the name of the tool the place is taken for, as a call that waits is told it
     * @param owner what the place is taken for: the call, as the server is given it; one place at most for each
     * @param waiting told {@code Waiting for <tool>, which is running} when the lane is taken, and again every
     * so often while the call waits; can be {@code null}
     * @return the place, to be {@linkplain #release given back}
     * @throws InterruptedException when the thread is interrupted while it waits: the call takes no place
     */
    Ticket take(Lane lane, String tool, Object owner, Consumer<String> waiting) throws InterruptedException {
        Places places = lanes.get(Objects.requireNonNull(lane, 'lane'))
        places.take(waitingEvery.toNanos(), { -> tell(waiting, lane) } as Runnable)
        Ticket ticket = new Ticket(this, lane, tool, owner)
        synchronized (holders) {
            holders.add(ticket)
        }
        return ticket
    }

    /**
     * Gives a place back, once: the next call of its lane takes it.
     *
     * @param ticket the place
     * @return whether it was given back now, and not before
     */
    boolean release(Ticket ticket) {
        if (ticket == null || !ticket.givenBack.compareAndSet(false, true)) {
            return false
        }
        synchronized (holders) {
            holders.remove(ticket)
        }
        lanes.get(ticket.lane).giveBack()
        return true
    }

    /**
     * @return the places that are held now, the longest held first
     */
    List<Ticket> held() {
        synchronized (holders) {
            return new ArrayList<Ticket>(holders)
        }
    }

    /**
     * @param owner what a place was taken for
     * @return the place it holds now, or {@code null}
     */
    Ticket heldBy(Object owner) {
        synchronized (holders) {
            return holders.find { Ticket ticket -> ticket.owner.is(owner) }
        }
    }

    private void tell(Consumer<String> waiting, Lane lane) {
        if (waiting == null) {
            return
        }
        Ticket first
        synchronized (holders) {
            first = holders.find { Ticket ticket -> ticket.lane == lane }
        }
        String tool = first == null ? 'another call' : first.tool
        try {
            waiting.accept("Waiting for ${tool}, which is running".toString())
        } catch (RuntimeException ignored) {
            // the call waits all the same
        }
    }

    /**
     * The places of one lane, which the calls take in the order they come: a call that waits keeps its place in
     * the queue while it is told, every so often, that it waits. A fair semaphore that is waited for with a time
     * would not keep it: a call whose time is up leaves the queue of the semaphore, and comes back behind the calls
     * that came after it, so that two calls that wait longer than that would take the lane in either order.
     */
    @CompileStatic
    private static final class Places {

        private final ReentrantLock lock = new ReentrantLock()
        private final Condition changed = lock.newCondition()
        // guarded by lock: the calls that wait, the first first
        private final ArrayDeque<Object> queue = new ArrayDeque<>()
        // guarded by lock: how many places are free
        private int free

        Places(int width) {
            free = width
        }

        /**
         * Takes a place, and waits for it in the queue as long as none is free or calls that came earlier wait.
         *
         * @param everyNanos how long the call waits between two times it is told that it waits
         * @param tell what tells it, at once when it has to wait and then every so often
         * @throws InterruptedException when the thread is interrupted: the call leaves the queue and takes nothing
         */
        void take(long everyNanos, Runnable tell) throws InterruptedException {
            Object call = new Object()
            lock.lockInterruptibly()
            try {
                if (queue.isEmpty() && free > 0) {
                    free--
                    return
                }
                queue.addLast(call)
            } finally {
                lock.unlock()
            }
            boolean taken = false
            try {
                while (!taken) {
                    // the call is in the queue when it is told that it waits
                    tell.run()
                    taken = waitForTurn(call, everyNanos)
                }
            } finally {
                if (!taken) {
                    leaveQueue(call)
                }
            }
        }

        /**
         * Gives a place back: the first call of the queue takes it.
         */
        void giveBack() {
            lock.lock()
            try {
                free++
                changed.signalAll()
            } finally {
                lock.unlock()
            }
        }

        // whether the call is the first of the queue while a place is free, within the time, and has taken the place
        private boolean waitForTurn(Object call, long nanos) throws InterruptedException {
            lock.lockInterruptibly()
            try {
                long left = nanos
                while (!(queue.peekFirst().is(call) && free > 0)) {
                    if (left <= 0L) {
                        return false
                    }
                    left = changed.awaitNanos(left)
                }
                queue.removeFirst()
                free--
                if (free > 0 && !queue.isEmpty()) {
                    // the next call of the queue can take a place that is still free
                    changed.signalAll()
                }
                return true
            } finally {
                lock.unlock()
            }
        }

        // a call that was interrupted while it waited has taken nothing, and the next one may take a place that is free
        private void leaveQueue(Object call) {
            lock.lock()
            try {
                queue.remove(call)
                changed.signalAll()
            } finally {
                lock.unlock()
            }
        }
    }

    /**
     * A place in a lane, held by one call: which lane, for which tool, and since when the call that holds
     * it was cancelled while what it runs goes on, where it was.
     *
     * @since 9.0
     */
    @CompileStatic
    static final class Ticket {

        /** The lane. */
        final Lane lane

        /** The name of the tool the place was taken for. */
        final String tool

        /** What the place was taken for. */
        final Object owner

        private final CallLanes lanes
        private final AtomicBoolean givenBack = new AtomicBoolean()
        private final AtomicBoolean abandoned = new AtomicBoolean()
        private volatile Long cancelled

        private Ticket(CallLanes lanes, Lane lane, String tool, Object owner) {
            this.lanes = lanes
            this.lane = lane
            this.tool = tool
            this.owner = owner
        }

        /**
         * Notes that the call that holds the place was cancelled, and no longer waits for what it runs,
         * which goes on and gives the place back when it ends.
         */
        void cancelled() {
            if (cancelled == null) {
                cancelled = System.nanoTime()
            }
        }

        /**
         * @return how long ago the call that holds the place was cancelled while what it runs went on, or
         * {@code null} when it was not
         */
        Duration getCancelledFor() {
            Long at = cancelled
            return at == null ? null : Duration.ofNanos(System.nanoTime() - at)
        }

        /**
         * Gives the place back for a command that does not end, once.
         *
         * @return whether the place was given up on now, and not before
         */
        boolean abandon() {
            return abandoned.compareAndSet(false, true) && lanes.release(this)
        }

        /**
         * @return whether the place was given back
         */
        boolean isReleased() {
            return givenBack.get()
        }

        @Override
        String toString() {
            return "${tool} (${lane.name().toLowerCase(Locale.ROOT)})".toString()
        }
    }
}

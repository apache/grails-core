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
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Consumer
import groovy.transform.CompileStatic
import org.apache.grails.forge.cli.mcp.Lane

/**
 * The lanes the calls of {@code grails mcp} run in, beside each other: {@link Lane#BUILD} and
 * {@link Lane#GENERATOR} take one call at a time, in the order the calls came, and {@link Lane#DIRECT}
 * up to {@value #DIRECT_WIDTH} at once. A call that finds its lane taken waits for it, is told now and
 * then which tool holds it, and stops waiting when its thread is interrupted, as it is when the client
 * cancels the call: it then leaves the queue, and has taken nothing.
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
    private final Map<Lane, Semaphore> lanes = new EnumMap<>(Lane)
    // guarded by itself: what holds each lane now
    private final List<Ticket> holders = []

    /**
     * @param waitingEvery how often a call that waits for its lane is told that it does
     */
    CallLanes(Duration waitingEvery) {
        this.waitingEvery = Objects.requireNonNull(waitingEvery, 'waitingEvery')
        // fair, so that the calls of a lane take it in the order they came
        lanes.put(Lane.BUILD, new Semaphore(1, true))
        lanes.put(Lane.GENERATOR, new Semaphore(1, true))
        lanes.put(Lane.DIRECT, new Semaphore(DIRECT_WIDTH, true))
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
        Semaphore semaphore = lanes.get(Objects.requireNonNull(lane, 'lane'))
        // a timed try keeps the order of a fair lane, which a try without a time does not
        if (!semaphore.tryAcquire(0L, TimeUnit.NANOSECONDS)) {
            while (true) {
                tell(waiting, lane)
                if (semaphore.tryAcquire(waitingEvery.toNanos(), TimeUnit.NANOSECONDS)) {
                    break
                }
            }
        }
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
        lanes.get(ticket.lane).release()
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

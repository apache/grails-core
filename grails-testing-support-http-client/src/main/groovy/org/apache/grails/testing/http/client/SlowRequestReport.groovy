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
package org.apache.grails.testing.http.client

import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

import groovy.transform.CompileStatic

/**
 * Says what the server side of a slow request is doing, while it is still slow.
 *
 * <p>A functional test that never gets its response reports only that the client gave up, which
 * does not distinguish a request that was never served from one that was served too slowly. An
 * integration test runs the application in the test's own JVM, so the threads that would answer
 * are here: a dump taken while the request is outstanding says which of the two it is.
 *
 * <p>Nothing here may change what a test does. Every capture is guarded, the timer thread is a
 * daemon so it cannot hold the JVM open - a build has hung on exactly that before - and a request
 * that answers in time cancels its timer and costs nothing but the scheduling.
 *
 * @since 8.0
 */
@CompileStatic
class SlowRequestReport {

    /**
     * How long a request may run before the JVM is asked what it is doing. Left well under the
     * client timeout, so the dump lands while the request is still outstanding rather than after
     * it has been abandoned.
     */
    static final long DUMP_AFTER_SECONDS =
            Long.getLong('grails.http.client.diagnostics.seconds', 90L)

    /** Set to false to turn the whole thing off without removing it. */
    static final boolean ENABLED =
            Boolean.parseBoolean(System.getProperty('grails.http.client.diagnostics', 'true'))

    /** Carried by every request, so a client line and a server line can be paired. */
    static final String CORRELATION_HEADER = 'X-Grails-Test-Correlation-Id'

    private static final AtomicLong COUNTER = new AtomicLong()

    private static final ScheduledExecutorService TIMER = Executors.newSingleThreadScheduledExecutor { Runnable task ->
        Thread thread = new Thread(task, 'http-client-slow-request-report')
        thread.daemon = true      // never the reason a JVM cannot exit
        thread
    }

    /**
     * @return an identifier for one request, short enough to read in a log and unique enough to
     *         pair a client line with a server one
     */
    static String nextCorrelationId() {
        "t${COUNTER.incrementAndGet()}-${Long.toHexString(System.nanoTime())}".toString()
    }

    /**
     * Arms a report for a request that is about to be sent. The caller cancels it when the
     * response arrives.
     *
     * @param correlationId identifier carried by the request
     * @param description what the request is, for the log
     * @return the scheduled report, to be cancelled, or null if reporting is off
     */
    static ScheduledFuture<?> arm(String correlationId, String description) {
        if (!ENABLED) {
            return null
        }
        try {
            return TIMER.schedule({ report(correlationId, description) } as Runnable,
                    DUMP_AFTER_SECONDS, TimeUnit.SECONDS)
        }
        catch (Throwable ignored) {
            return null      // diagnostics never decide whether a test runs
        }
    }

    /**
     * Cancels a report armed for a request that has now answered.
     */
    static void disarm(ScheduledFuture<?> report) {
        try {
            report?.cancel(false)
        }
        catch (Throwable ignored) {
            // nothing to do about it, and nothing worth failing a test over
        }
    }

    private static void report(String correlationId, String description) {
        try {
            StringBuilder out = new StringBuilder()
            out << "\n=== a request has been waiting ${DUMP_AFTER_SECONDS}s: ${description}\n"
            out << "=== correlation id: ${correlationId}\n"
            out << '=== the application under test runs in this JVM; its threads follow\n'
            Thread.allStackTraces.each { Thread thread, StackTraceElement[] stack ->
                out << "\n\"${thread.name}\" ${thread.state}${thread.daemon ? ' daemon' : ''}\n"
                stack.each { StackTraceElement frame -> out << "    at ${frame}\n" }
            }
            System.err.print(out.toString())
            System.err.flush()
        }
        catch (Throwable ignored) {
            // a report that cannot be produced is not worth an exception on a timer thread
        }
    }

}

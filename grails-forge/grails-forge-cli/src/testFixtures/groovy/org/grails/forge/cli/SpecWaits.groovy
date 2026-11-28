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
package org.grails.forge.cli

import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.function.Supplier
import groovy.transform.CompileStatic

/**
 * How long the specs of the command line wait for something that is to happen: one bound for every wait, which is
 * no part of what a spec expects and only ends a wait when something is wrong.
 *
 * <p>A spec waits for the event itself: a line the program wrote, a step a stub said it is at ({@link Handshake}), a
 * latch, a thread or a process that ended, a port that answers. How long that takes depends on the computer, and on
 * a runner of CI that is shared and slow it takes many times what it takes on a fast one, so no wait has a time of
 * its own that it expects the event within. The bound is {@link #BASE} times a factor, which is 1 unless the
 * environment variable {@value #FACTOR_VARIABLE} or the system property {@value #FACTOR_PROPERTY} names another
 * number: a runner that is slower still raises every bound in one place.</p>
 *
 * <p>Where a wait has nothing to block on, as for a file another process writes, it looks every
 * {@value #LOOK_MILLIS} milliseconds, which costs nothing of the time of a spec that passes.</p>
 */
@CompileStatic
final class SpecWaits {

    /** The environment variable that multiplies every bound. */
    static final String FACTOR_VARIABLE = 'GRAILS_SPEC_WAIT_FACTOR'

    /** The system property that multiplies every bound, read before the environment variable. */
    static final String FACTOR_PROPERTY = 'grails.spec.waitFactor'

    /** The bound with a factor of 1. */
    static final Duration BASE = Duration.ofMinutes(5)

    /** How long any wait of a spec waits at most before it fails. */
    static final Duration BOUND = scaled(BASE)

    static final long LOOK_MILLIS = 20

    private SpecWaits() {
    }

    /**
     * @param base a duration with a factor of 1
     * @return the duration times the factor the environment names, and the duration itself when it names none
     */
    static Duration scaled(Duration base) {
        String named = System.getProperty(FACTOR_PROPERTY) ?: System.getenv(FACTOR_VARIABLE)
        if (named == null || named.trim().isEmpty()) {
            return base
        }
        double factor
        try {
            factor = Double.parseDouble(named.trim())
        } catch (NumberFormatException ignored) {
            throw new IllegalArgumentException("${FACTOR_PROPERTY} or ${FACTOR_VARIABLE} is to be a number, and is ${named}".toString())
        }
        if (factor < 1) {
            throw new IllegalArgumentException("${FACTOR_PROPERTY} or ${FACTOR_VARIABLE} is to be at least 1, and is ${named}: it raises the bound and does not lower it".toString())
        }
        return Duration.ofMillis(Math.round(base.toMillis() * factor))
    }

    /** @return the bound in whole seconds, for an API that takes seconds */
    static long getBoundSeconds() {
        return BOUND.toSeconds()
    }

    /**
     * Waits until a condition holds.
     *
     * @param what what is waited for, for the message of a wait that ends at the bound
     * @param condition what is to hold; it is asked again until it does
     */
    static void until(String what, Supplier<Boolean> condition) {
        value(what, { -> condition.get() ? Boolean.TRUE : null } as Supplier<Boolean>)
    }

    /**
     * Waits until there is a value.
     *
     * @param what what is waited for, for the message of a wait that ends at the bound
     * @param value gives the value, or {@code null} while there is none; it is asked again until there is one
     * @return the value
     */
    static <T> T value(String what, Supplier<T> value) {
        long deadline = System.nanoTime() + BOUND.toNanos()
        while (true) {
            T found = value.get()
            if (found != null) {
                return found
            }
            if (System.nanoTime() - deadline > 0) {
                throw failed(what)
            }
            try {
                Thread.sleep(LOOK_MILLIS)
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt()
                throw new AssertionError("The wait for ${what} was interrupted".toString(), e)
            }
        }
    }

    /**
     * Waits until what a spec holds holds: the closure is run again until it ends without an {@link AssertionError},
     * as the conditions of a spec do once they are true.
     *
     * <p>It returns nothing, as {@code PollingConditions.eventually} does, so that a spec calls it in a {@code then:}
     * block without it being taken for a condition of its own.</p>
     *
     * @param assertions what is held, with {@code assert}
     * @throws AssertionError at the bound, with the last failure of the closure as its cause
     */
    static void eventually(Closure<?> assertions) {
        long deadline = System.nanoTime() + BOUND.toNanos()
        while (true) {
            try {
                assertions.call()
                return
            } catch (AssertionError e) {
                if (System.nanoTime() - deadline > 0) {
                    AssertionError failed = failed('what the spec waits for')
                    failed.initCause(e)
                    throw failed
                }
            }
            try {
                Thread.sleep(LOOK_MILLIS)
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt()
                throw new AssertionError('The wait was interrupted', e)
            }
        }
    }

    /**
     * Waits until a text has a line that contains another text.
     *
     * @param output gives the text as it is now, which grows while it is waited for
     * @param text what a line is to contain
     * @return the first such line
     */
    static String line(Supplier<? extends CharSequence> output, String text) {
        return value("a line with [${text}]".toString(), { ->
            output.get().toString().readLines().find { String line -> line.contains(text) }
        } as Supplier<String>)
    }

    /** Waits until a latch is open. */
    static void await(CountDownLatch latch, String what) {
        if (!latch.await(BOUND.toNanos(), TimeUnit.NANOSECONDS)) {
            throw failed(what)
        }
    }

    /**
     * Waits until a computation has ended.
     *
     * @return its value; its failure is thrown as the cause of an {@link ExecutionException}
     */
    static <T> T get(Future<T> future, String what) {
        try {
            return future.get(BOUND.toNanos(), TimeUnit.NANOSECONDS)
        } catch (TimeoutException ignored) {
            throw failed(what)
        }
    }

    /** Waits until a thread has ended. */
    static void join(Thread thread, String what) {
        thread.join(BOUND.toMillis())
        if (thread.alive) {
            throw failed(what)
        }
    }

    /**
     * Waits until a process has ended.
     *
     * @return its exit code
     */
    static int exited(Process process, String what) {
        if (!process.waitFor(BOUND.toNanos(), TimeUnit.NANOSECONDS)) {
            throw failed(what)
        }
        return process.exitValue()
    }

    /** Waits until a process, which may have ended already, has ended. */
    static void ended(ProcessHandle process, String what) {
        try {
            process.onExit().get(BOUND.toNanos(), TimeUnit.NANOSECONDS)
        } catch (TimeoutException ignored) {
            throw failed(what)
        }
    }

    /** Waits until the process of a number, which may have ended already, has ended. */
    static void ended(long pid, String what) {
        Optional<ProcessHandle> process = ProcessHandle.of(pid)
        if (process.isPresent()) {
            ended(process.get(), what)
        }
    }

    /** @return whether the process of a number is running */
    static boolean alive(long pid) {
        return ProcessHandle.of(pid).map { ProcessHandle handle -> handle.alive }.orElse(false)
    }

    private static AssertionError failed(String what) {
        return new AssertionError("${what} did not happen within ${BOUND.toSeconds()} s, the bound of every wait of the specs; " +
                "a slower computer raises it with ${FACTOR_VARIABLE} or -D${FACTOR_PROPERTY}".toString())
    }
}

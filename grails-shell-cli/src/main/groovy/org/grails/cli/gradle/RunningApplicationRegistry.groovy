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
package org.grails.cli.gradle

import java.util.concurrent.ConcurrentHashMap

import groovy.transform.CompileStatic

import org.gradle.tooling.CancellationTokenSource

import grails.build.logging.GrailsConsole

/**
 * Tracks the {@link CancellationTokenSource} of Grails applications started by the
 * CLI via the {@code run-app} command (an asynchronous Gradle {@code bootRun} build).
 *
 * <p>This allows the {@code stop-app} command to cancel the underlying Gradle build
 * without exiting the interactive CLI, providing a CLI only shutdown mechanism that
 * does not rely on the Spring Boot Actuator shutdown endpoint or JMX.</p>
 *
 * <p>A {@code run-app} build registers its token before the build runs and removes it
 * when the build finishes (whether it completes normally, fails, or is cancelled).
 * {@link #stopAll()} only requests cancellation; it never removes tokens directly so
 * that the build remains responsible for its own lifecycle.</p>
 *
 * @author Apache Grails Team
 * @since 7.0.0
 */
@CompileStatic
class RunningApplicationRegistry {

    private static final Set<CancellationTokenSource> RUNNING = ConcurrentHashMap.newKeySet()

    // Monitor used to wake up awaitStop() the moment the last running build deregisters,
    // instead of polling. Only deregister() can empty RUNNING, so only it needs to notify.
    private static final Object MONITOR = new Object()

    private RunningApplicationRegistry() {
    }

    /**
     * Registers the cancellation token source of a running application.
     *
     * @param tokenSource the token source backing the running build
     */
    static void register(CancellationTokenSource tokenSource) {
        if (tokenSource != null) {
            RUNNING.add(tokenSource)
        }
    }

    /**
     * Removes a previously registered cancellation token source. This should be called
     * by the build once it has finished, regardless of how it terminated.
     *
     * @param tokenSource the token source to remove
     */
    static void deregister(CancellationTokenSource tokenSource) {
        if (tokenSource != null) {
            RUNNING.remove(tokenSource)
            synchronized (MONITOR) {
                MONITOR.notifyAll()
            }
        }
    }

    /**
     * @return {@code true} if at least one application started via {@code run-app} is running
     */
    static boolean isApplicationRunning() {
        !RUNNING.isEmpty()
    }

    /**
     * Requests cancellation of every running application. The registered token sources are
     * not removed here; each running build removes its own token when it terminates.
     *
     * @return {@code true} if at least one running application was found and cancellation requested
     */
    static boolean stopAll() {
        if (RUNNING.isEmpty()) {
            return false
        }
        // Snapshot to avoid surprises if a build deregisters concurrently while we iterate
        List<CancellationTokenSource> tokenSources = new ArrayList<>(RUNNING)
        for (CancellationTokenSource tokenSource : tokenSources) {
            try {
                tokenSource.cancel()
            }
            catch (Throwable e) {
                GrailsConsole.getInstance().verbose("Failed to request cancellation of a running application: ${e.message}")
            }
        }
        return true
    }

    /**
     * Waits up to the given timeout for all running applications to terminate, i.e. for the
     * cancelled builds to finish tearing down and deregister their token sources.
     *
     * @param timeoutMillis the maximum time to wait in milliseconds
     * @return {@code true} if all applications stopped within the timeout, {@code false} otherwise
     */
    static boolean awaitStop(long timeoutMillis) {
        long deadline = System.currentTimeMillis() + timeoutMillis
        synchronized (MONITOR) {
            while (!RUNNING.isEmpty()) {
                long remaining = deadline - System.currentTimeMillis()
                if (remaining <= 0) {
                    return false
                }
                try {
                    MONITOR.wait(remaining)
                }
                catch (InterruptedException e) {
                    Thread.currentThread().interrupt()
                    return RUNNING.isEmpty()
                }
            }
            return true
        }
    }
}

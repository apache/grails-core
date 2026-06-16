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

import java.time.Instant
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

import groovy.transform.CompileStatic

/**
 * Locates and stops the Grails application started by the CLI {@code run-app} command.
 *
 * <p>{@code run-app} launches an application as a forked Gradle {@code bootRun} JVM. The
 * {@code GrailsGradlePlugin} configures that {@code bootRun} task to tell the application to write
 * its own process id to a project local PID file (via Spring Boot's
 * {@code ApplicationPidFileWriter}). Because the contract is a file on disk - and not in-memory
 * state - {@code stop-app} can terminate the application even when it was forked into a separate
 * process or when {@code grails stop-app} is run from a different CLI invocation than the one that
 * started it.</p>
 *
 * <p>Stopping is performed with {@link ProcessHandle#destroy()}, which requests a graceful
 * shutdown (a {@code SIGTERM} on Unix-like systems, allowing the JVM shutdown hooks and
 * Spring's orderly shutdown to run). On platforms where the operating system has no
 * graceful equivalent (notably Windows) termination is best effort.</p>
 *
 * @author Apache Grails Team
 * @since 7.0.0
 */
@CompileStatic
class RunningApplicationProcess {

    /**
     * The name of the PID file written, relative to the project build directory.
     */
    static final String PID_FILE_NAME = 'run-app.pid'

    /**
     * The name of the marker file, relative to the project build directory, that {@code stop-app}
     * writes to signal a deliberate shutdown. It lets a foreground {@code run-app} that is blocked
     * on the {@code bootRun} build report a clean stop rather than a startup failure when its
     * process is terminated.
     */
    static final String STOP_MARKER_NAME = 'run-app.stopping'

    /**
     * The forked application writes its PID file after it starts, so a live process described by
     * the PID file always started before the file was written. If the live process holding that
     * PID started noticeably later than the file, the id has almost certainly been recycled by
     * the operating system for an unrelated process, so it must not be terminated.
     */
    private static final long PID_REUSE_TOLERANCE_MILLIS = 5000L

    /**
     * The result of a {@link #stop(File, long)} request.
     */
    enum StopResult {
        /** No application started by {@code run-app} was running. */
        NOT_RUNNING,
        /** The application was found and has stopped. */
        STOPPED,
        /** The application was found but had not stopped before the timeout elapsed. */
        STILL_RUNNING
    }

    private RunningApplicationProcess() {
    }

    /**
     * @param buildDir the project build directory
     * @return the PID file used to track the application started by {@code run-app}
     */
    static File pidFile(File buildDir) {
        new File(buildDir, PID_FILE_NAME)
    }

    /**
     * @param buildDir the project build directory
     * @return the marker file used to signal that a {@code stop-app} shutdown is in progress
     */
    static File stopMarker(File buildDir) {
        new File(buildDir, STOP_MARKER_NAME)
    }

    /**
     * Records that a deliberate {@code stop-app} shutdown is in progress, so a foreground
     * {@code run-app} blocked on the {@code bootRun} build can distinguish an intentional stop
     * from a startup failure when its process is terminated.
     *
     * @param buildDir the project build directory
     */
    static void requestStop(File buildDir) {
        File marker = stopMarker(buildDir)
        try {
            marker.parentFile?.mkdirs()
            marker.setText(Long.toString(System.currentTimeMillis()), 'UTF-8')
        }
        catch (Exception ignored) {
            // Best effort: a missing marker only affects the message shown by a foreground run-app.
        }
    }

    /**
     * @param buildDir the project build directory
     * @return {@code true} if a {@code stop-app} shutdown has been requested for the project
     */
    static boolean isStopRequested(File buildDir) {
        stopMarker(buildDir).isFile()
    }

    /**
     * Removes any pending {@code stop-app} shutdown marker for the project.
     *
     * @param buildDir the project build directory
     */
    static void clearStopRequest(File buildDir) {
        deleteQuietly(stopMarker(buildDir))
    }

    /**
     * Reads the process id recorded in the given PID file.
     *
     * @param pidFile the PID file
     * @return the recorded process id, or {@code null} if the file is missing, empty, malformed or
     *         not a positive process id
     */
    static Long readPid(File pidFile) {
        if (pidFile == null || !pidFile.isFile()) {
            return null
        }
        try {
            String text = pidFile.getText('UTF-8')?.trim()
            if (!text) {
                return null
            }
            long pid = Long.parseLong(text)
            return pid > 0L ? Long.valueOf(pid) : null
        }
        catch (NumberFormatException | IOException ignored) {
            return null
        }
    }

    /**
     * Resolves the live application process described by the PID file, applying a guard against
     * recycled process ids.
     *
     * @param pidFile the PID file
     * @return the live {@link ProcessHandle}, or an empty optional when the file is missing,
     *         malformed, refers to a process that is no longer alive, or refers to a process id
     *         that has been reused by an unrelated process
     */
    static Optional<ProcessHandle> liveProcess(File pidFile) {
        Long pid = readPid(pidFile)
        if (pid == null) {
            return Optional.empty()
        }
        Optional<ProcessHandle> handle
        try {
            handle = ProcessHandle.of(pid)
        }
        catch (IllegalArgumentException ignored) {
            return Optional.empty()
        }
        if (!handle.isPresent() || !handle.get().isAlive()) {
            return Optional.empty()
        }
        ProcessHandle process = handle.get()
        if (isProbablyReusedPid(process, pidFile)) {
            return Optional.empty()
        }
        return Optional.of(process)
    }

    /**
     * @param pidFile the PID file
     * @return {@code true} if an application started by {@code run-app} is currently running
     */
    static boolean isRunning(File pidFile) {
        liveProcess(pidFile).isPresent()
    }

    /**
     * Stops the application recorded in the PID file and removes the file once the process is gone.
     *
     * @param pidFile the PID file
     * @param timeoutMillis the maximum time to wait for the process to terminate
     * @return the outcome of the stop request
     */
    static StopResult stop(File pidFile, long timeoutMillis) {
        Optional<ProcessHandle> handle = liveProcess(pidFile)
        if (!handle.isPresent()) {
            deleteQuietly(pidFile)
            return StopResult.NOT_RUNNING
        }

        ProcessHandle process = handle.get()
        process.destroy()
        boolean exited = awaitExit(process, timeoutMillis)
        if (!exited) {
            process.destroyForcibly()
            exited = awaitExit(process, Math.min(timeoutMillis, 5000L))
        }

        if (exited) {
            deleteQuietly(pidFile)
            return StopResult.STOPPED
        }
        return StopResult.STILL_RUNNING
    }

    private static boolean awaitExit(ProcessHandle process, long timeoutMillis) {
        try {
            process.onExit().get(timeoutMillis, TimeUnit.MILLISECONDS)
            return true
        }
        catch (TimeoutException ignored) {
            return !process.isAlive()
        }
        catch (InterruptedException ignored) {
            Thread.currentThread().interrupt()
            return !process.isAlive()
        }
        catch (ExecutionException ignored) {
            // onExit() failed to observe the process; fall back to a liveness check
            return !process.isAlive()
        }
    }

    private static boolean isProbablyReusedPid(ProcessHandle process, File pidFile) {
        long pidFileModified = pidFile.lastModified()
        if (pidFileModified <= 0L) {
            return false
        }
        Optional<Instant> startInstant = process.info().startInstant()
        if (!startInstant.isPresent()) {
            return false
        }
        return startInstant.get().toEpochMilli() > (pidFileModified + PID_REUSE_TOLERANCE_MILLIS)
    }

    private static void deleteQuietly(File file) {
        if (file == null) {
            return
        }
        try {
            file.delete()
        }
        catch (Exception ignored) {
            // best effort cleanup
        }
    }
}

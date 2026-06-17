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
import java.util.concurrent.TimeUnit

import spock.lang.Specification
import spock.lang.TempDir

/**
 * Tests for {@link RunningApplicationProcess}, the PID file based mechanism that lets
 * {@code stop-app} terminate the application started by {@code run-app}.
 */
class RunningApplicationProcessSpec extends Specification {

    @TempDir
    File buildDir

    List<Process> spawned = []

    void cleanup() {
        spawned.each { Process process ->
            try {
                process.descendants().forEach { it.destroyForcibly() }
                process.destroyForcibly()
            }
            catch (ignored) {
            }
        }
        spawned.clear()
    }

    private Process spawnLongLivedProcess() {
        boolean windows = System.getProperty('os.name').toLowerCase(Locale.ENGLISH).contains('win')
        // Single process commands (no shell wrapper) so that the spawned PID is the process itself.
        List<String> command = windows ? ['ping', '-n', '60', '127.0.0.1'] : ['sleep', '60']
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start()
        spawned << process
        process
    }

    private long deadPid() {
        Process process = spawnLongLivedProcess()
        long pid = process.pid()
        process.destroyForcibly()
        process.waitFor(10, TimeUnit.SECONDS)
        pid
    }

    void "pidFile resolves run-app.pid under the build directory"() {
        expect:
        RunningApplicationProcess.pidFile(buildDir) == new File(buildDir, RunningApplicationProcess.PID_FILE_NAME)
    }

    void "readPid returns null when the PID file is missing"() {
        expect:
        RunningApplicationProcess.readPid(new File(buildDir, 'missing.pid')) == null
    }

    void "readPid returns null for a malformed PID file"() {
        given:
        File pidFile = RunningApplicationProcess.pidFile(buildDir)
        pidFile.text = 'not-a-pid'

        expect:
        RunningApplicationProcess.readPid(pidFile) == null
    }

    void "readPid parses a valid PID"() {
        given:
        File pidFile = RunningApplicationProcess.pidFile(buildDir)
        pidFile.text = '12345\n'

        expect:
        RunningApplicationProcess.readPid(pidFile) == 12345L
    }

    void "readPid returns null for a non-positive PID"() {
        given:
        File pidFile = RunningApplicationProcess.pidFile(buildDir)
        pidFile.text = value

        expect:
        RunningApplicationProcess.readPid(pidFile) == null

        where:
        value << ['0', '-1']
    }

    void "isRunning is false when no PID file exists"() {
        expect:
        !RunningApplicationProcess.isRunning(RunningApplicationProcess.pidFile(buildDir))
    }

    void "isRunning is false for a stale PID that is no longer alive"() {
        given:
        File pidFile = RunningApplicationProcess.pidFile(buildDir)
        pidFile.text = deadPid().toString()

        expect:
        !RunningApplicationProcess.isRunning(pidFile)
    }

    void "isRunning is true while the process is alive"() {
        given:
        Process process = spawnLongLivedProcess()
        File pidFile = RunningApplicationProcess.pidFile(buildDir)
        pidFile.text = process.pid().toString()

        expect:
        RunningApplicationProcess.isRunning(pidFile)
    }

    void "isRunning treats a PID file written long before the live process as a recycled PID"() {
        given: "a live process recorded in the PID file"
        Process process = spawnLongLivedProcess()
        File pidFile = RunningApplicationProcess.pidFile(buildDir)
        pidFile.text = process.pid().toString()

        and: "the live process exposes a start time, which the recycled-PID guard relies on"
        Optional<Instant> startInstant = ProcessHandle.of(process.pid())
                .flatMap { ProcessHandle handle -> handle.info().startInstant() }
        assert startInstant.present

        and: "the PID file is backdated so the live process started well after the file was written"
        assert pidFile.setLastModified(startInstant.get().toEpochMilli() - 3_600_000L)

        expect: "the guard interprets the id as recycled and reports the app as not running"
        !RunningApplicationProcess.isRunning(pidFile)
    }

    void "stop does not terminate a process whose PID looks recycled"() {
        given:
        Process process = spawnLongLivedProcess()
        File pidFile = RunningApplicationProcess.pidFile(buildDir)
        pidFile.text = process.pid().toString()

        and: "the live process exposes a start time, which the recycled-PID guard relies on"
        Optional<Instant> startInstant = ProcessHandle.of(process.pid())
                .flatMap { ProcessHandle handle -> handle.info().startInstant() }
        assert startInstant.present
        assert pidFile.setLastModified(startInstant.get().toEpochMilli() - 3_600_000L)

        expect:
        RunningApplicationProcess.stop(pidFile, 5000) == RunningApplicationProcess.StopResult.NOT_RUNNING
        process.isAlive()
    }

    void "stop returns NOT_RUNNING and removes a stale PID file"() {
        given:
        File pidFile = RunningApplicationProcess.pidFile(buildDir)
        pidFile.text = deadPid().toString()

        when:
        def result = RunningApplicationProcess.stop(pidFile, 5000)

        then:
        result == RunningApplicationProcess.StopResult.NOT_RUNNING
        !pidFile.exists()
    }

    void "stop returns NOT_RUNNING when there is no PID file"() {
        expect:
        RunningApplicationProcess.stop(RunningApplicationProcess.pidFile(buildDir), 5000) ==
                RunningApplicationProcess.StopResult.NOT_RUNNING
    }

    void "stop request marker can be written, detected and cleared"() {
        expect: "no stop is requested initially"
        !RunningApplicationProcess.isStopRequested(buildDir)

        when: "a stop is requested"
        RunningApplicationProcess.requestStop(buildDir)

        then: "it is detected"
        RunningApplicationProcess.isStopRequested(buildDir)

        when: "the request is cleared"
        RunningApplicationProcess.clearStopRequest(buildDir)

        then: "no stop is requested"
        !RunningApplicationProcess.isStopRequested(buildDir)
    }

    void "stop terminates a running process and removes the PID file"() {
        given:
        Process process = spawnLongLivedProcess()
        File pidFile = RunningApplicationProcess.pidFile(buildDir)
        pidFile.text = process.pid().toString()

        expect:
        RunningApplicationProcess.isRunning(pidFile)

        when:
        def result = RunningApplicationProcess.stop(pidFile, 15000)

        then:
        result == RunningApplicationProcess.StopResult.STOPPED
        !pidFile.exists()
        !process.isAlive()
    }
}

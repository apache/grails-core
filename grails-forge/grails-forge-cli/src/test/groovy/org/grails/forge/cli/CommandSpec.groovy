/*
 * Copyright 2017-2024 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.forge.cli

import spock.lang.Specification
import spock.util.concurrent.PollingConditions
import spock.util.environment.OperatingSystem

import java.nio.file.Files

class CommandSpec extends Specification {

    File dir = Files.createTempDirectory('grailsforgetmp').toFile()
    StringBuilder output
    Process process
    String previousUsrDir

    void setupSpec() {
        Thread shutdownHook = new Thread(this::killProcess)
        Runtime.runtime.addShutdownHook(shutdownHook)
    }

    void setup() {
        previousUsrDir = System.getProperty('user.dir')
        System.setProperty('user.dir', dir.absolutePath)
        output = new StringBuilder()
    }

    void cleanup() {
        System.setProperty('user.dir', previousUsrDir)
        dir.deleteDir()
        killProcess()
    }

    Process executeGradleCommand(String command) {
        StringBuilder gradleCommand = new StringBuilder()
        if (OperatingSystem.current.isWindows()) {
            gradleCommand.append('cmd.exe /c gradlew')
        } else {
            gradleCommand.append('./gradlew')
        }
        gradleCommand.append(' --no-daemon -S ').append(command)
        executeCommand(gradleCommand)
    }

    // The specs that call executeGradleCommand('build') build a whole generated application -
    // asset compilation, bootWar, test and integrationTest - so this budget covers a full Grails
    // build on a shared CI runner, not a single task. At 240s it was marginal rather than
    // generous: on the Java 25 lane that build measured 217s when it passed, and a runner roughly
    // 10% slower than average was enough to push it over and fail the lane on timing alone.
    protected static final int POLL_TIMEOUT_SECONDS = 600

    private static final int POLL_INITIAL_DELAY_MILLIS = 3000
    private static final int POLL_DELAY_MILLIS = 1000

    // Once the process has exited the output can no longer grow, so allow the consumer thread
    // started by consumeProcessOutputStream a moment to drain before deciding the value is absent.
    private static final int OUTPUT_DRAIN_MILLIS = 2000

    PollingConditions getDefaultPollingConditions() {
        new PollingConditions(timeout: POLL_TIMEOUT_SECONDS, initialDelay: 3, delay: 1, factor: 1)
    }

    /**
     * Waits for the generated build to emit {@code value}, giving up as soon as the build process
     * exits without having produced it.
     *
     * <p>Deliberately not {@link PollingConditions}: that retries on any {@code Throwable}, so a
     * build which has already finished cannot short-circuit the wait. Waiting out the full budget
     * after the process is gone is how a generated build that simply failed came to look like a
     * hang, reported only as a bare unsatisfied condition with no cause. Keying the early exit on
     * process liveness rather than on a {@code BUILD FAILED} marker matters, because several specs
     * legitimately wait for text from a build that is expected to fail.</p>
     */
    void testOutputContains(String value) {
        long deadline = System.currentTimeMillis() + (POLL_TIMEOUT_SECONDS * 1000L)
        sleep(POLL_INITIAL_DELAY_MILLIS)
        while (true) {
            if (output.toString().contains(value)) {
                return
            }
            if (process != null && !process.alive) {
                sleep(OUTPUT_DRAIN_MILLIS)
                String finalOutput = output.toString()
                if (finalOutput.contains(value)) {
                    return
                }
                throw new AssertionError("The generated build exited with code ${process.exitValue()} " +
                        "without producing the expected output [${value}].\nBuild output:\n${finalOutput}" as Object)
            }
            if (System.currentTimeMillis() >= deadline) {
                throw new AssertionError("Timed out after ${POLL_TIMEOUT_SECONDS}s waiting for the generated " +
                        "build to produce [${value}]; the build was still running.\nBuild output:\n" +
                        "${output}" as Object)
            }
            sleep(POLL_DELAY_MILLIS)
        }
    }

    private Process executeCommand(StringBuilder builder) {
        String[] args = builder.toString().split(' ')
        ProcessBuilder pb = new ProcessBuilder(args)
        pb.environment().put('JAVA_HOME', System.getenv('JAVA_HOME') ?: System.getProperty('java.home'))
        pb.environment().put('GRAILS_REPO_URL', System.getenv('GRAILS_REPO_URL') ?: null)
        process = pb.directory(dir).start()
        process.consumeProcessOutputStream(output)
        process
    }

    void killProcess() {
        if (process) {
            process.destroy()
            try {
                process.waitForOrKill(1000)
            } catch(ignored) {
                process.destroyForcibly()
            }
        }
    }
}

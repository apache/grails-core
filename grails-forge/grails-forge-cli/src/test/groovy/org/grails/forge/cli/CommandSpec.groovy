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

import org.springframework.context.ConfigurableApplicationContext
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.util.concurrent.PollingConditions
import spock.util.environment.OperatingSystem

import java.nio.file.Files
import java.util.concurrent.TimeUnit

class CommandSpec extends Specification {

    /** The Forge CLI's Grails application, started once per specification: what the specs run commands on. */
    @Shared
    @AutoCleanup
    ConfigurableApplicationContext beanContext = Application.builder().run()

    File dir = Files.createTempDirectory('grailsforgetmp').toFile()
    StringBuilder output
    Process process
    // what reads the output of the process into output: it has read all of it when it has ended
    Thread outputReader
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
        // the build first: a build that still runs writes into the directory while it is deleted
        killProcess()
        dir.deleteDir()
    }

    Process executeGradleCommand(String command) {
        List<String> gradleCommand = OperatingSystem.current.isWindows()
                ? ['cmd.exe', '/c', 'gradlew']
                : ['./gradlew']
        gradleCommand.addAll(['--no-daemon', '-S'])
        gradleCommand.addAll(GeneratedBuild.arguments())
        gradleCommand.addAll(command.split(' '))
        executeCommand(gradleCommand)
    }

    // The specs that call executeGradleCommand('build') build a whole generated application -
    // asset compilation, bootWar, test and integrationTest - so this budget covers a full Grails
    // build on a shared CI runner, not a single task. At 240s it was marginal rather than
    // generous: on the Java 25 lane that build measured 217s when it passed, and a runner roughly
    // 10% slower than average was enough to push it over and fail the lane on timing alone.
    protected static final int POLL_TIMEOUT_SECONDS = 600

    // How long a wait sleeps before its first look at the output and between two looks. Both were whole seconds
    // (three and one), which every wait paid although a look is a search of a string: a wait for a line that is
    // there already took three seconds, and one for the last line of a build half a second longer than the build.
    private static final int POLL_INITIAL_DELAY_MILLIS = 100
    private static final int POLL_DELAY_MILLIS = 100

    // Once the process has exited the output can no longer grow: the JDK keeps what is left in the pipe and closes
    // it, and the reader is waited for until it has read that, which it has at once. A fixed moment instead races
    // the reader: on a busy machine a value that was written could be said not to be. The minute is for a reader
    // that was in a read as the process ended, which goes on while a process the build left behind has the output
    // open; what such a process writes later is read only then, so no spec waits for it.
    private static final int OUTPUT_READ_SECONDS = 60
    // How long a Gradle client is given to end after the last line of its build. Only what a failure says
    // depends on it: nothing a spec waits for is written after that line.
    private static final int EXIT_MILLIS = 2000
    // The line Gradle ends every build with. Nothing a spec waits for is written after it.
    private static final List<String> BUILD_END_MARKERS = ['BUILD SUCCESSFUL', 'BUILD FAILED']

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
     * hang, reported only as a bare unsatisfied condition with no cause. A {@code BUILD FAILED}
     * line alone is no reason to give up, because several specs legitimately wait for text from a
     * build that is expected to fail; that text comes before the line, though, so the wait also ends
     * when Gradle has written the last line of the build and the text is not there, whether or not
     * the process has exited by then. A Gradle client can outlive its build, and each such wait
     * used to cost the whole budget.</p>
     *
     * <p>The output holds both streams of the build, so the failure report Gradle writes to stderr
     * is part of the message when the build exits without the expected text.</p>
     */
    void testOutputContains(String value) {
        long deadline = System.currentTimeMillis() + (POLL_TIMEOUT_SECONDS * 1000L)
        sleep(POLL_INITIAL_DELAY_MILLIS)
        while (true) {
            if (output.toString().contains(value)) {
                return
            }
            if (process != null && !process.alive) {
                outputReader?.join(TimeUnit.SECONDS.toMillis(OUTPUT_READ_SECONDS))
                String finalOutput = output.toString()
                if (finalOutput.contains(value)) {
                    return
                }
                throw new AssertionError("The generated build exited with code ${process.exitValue()} " +
                        "without producing the expected output [${value}].\nBuild output:\n${finalOutput}" as Object)
            }
            if (BUILD_END_MARKERS.any { output.toString().contains(it) }) {
                // The last line of a build is written a moment before its process ends. The process is
                // given that moment, so that a build that has ended is reported with its exit code, as
                // it is when the process is seen to have ended first.
                boolean exited = process == null || process.waitFor(EXIT_MILLIS, TimeUnit.MILLISECONDS)
                if (process == null || !exited) {
                    sleep(EXIT_MILLIS)
                }
                String finalOutput = output.toString()
                if (finalOutput.contains(value)) {
                    return
                }
                throw new AssertionError((exited && process != null ? "The generated build exited with code ${process.exitValue()} " +
                        "without producing the expected output" : 'The generated build ended without producing the expected output') +
                        " [${value}].\nBuild output:\n${finalOutput}" as Object)
            }
            if (System.currentTimeMillis() >= deadline) {
                throw new AssertionError("Timed out after ${POLL_TIMEOUT_SECONDS}s waiting for the generated " +
                        "build to produce [${value}]; the build was still running.\nBuild output:\n" +
                        "${output}" as Object)
            }
            sleep(POLL_DELAY_MILLIS)
        }
    }

    protected Process executeCommand(List<String> args) {
        // one build at a time: the one before is over for the spec, whatever its process still does
        killProcess()
        // a new one, not an emptied one: what still reads the build before may write a last line
        output = new StringBuilder()
        ProcessBuilder pb = new ProcessBuilder(args)
        // Gradle writes its failure report to stderr. Merge it into the stream the consumer thread
        // drains, so that the report reaches the assertion message and a failed build cannot sit
        // on a full pipe nobody reads for the whole poll budget once the report, with -S, outgrows
        // the pipe buffer.
        pb.redirectErrorStream(true)
        pb.environment().put('JAVA_HOME', System.getenv('JAVA_HOME') ?: System.getProperty('java.home'))
        pb.environment().put('GRAILS_REPO_URL', System.getenv('GRAILS_REPO_URL') ?: null)
        process = pb.directory(dir).start()
        // the error stream is in this one, so its reader reads all the build says
        outputReader = process.consumeProcessOutputStream(output)
        process
    }

    void killProcess() {
        if (process) {
            // The Gradle client starts the build in a process of its own when the project asks for
            // other JVM settings than the client has, and that one starts the test workers. Ending
            // the client alone leaves them running, holding the locks of the next build.
            List<ProcessHandle> descendants = process.descendants().toList()
            process.destroy()
            try {
                process.waitForOrKill(1000)
            } catch (ignored) {
                process.destroyForcibly()
            }
            descendants.each { it.destroyForcibly() }
        }
    }
}

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
        gradleCommand.append(' --no-daemon ').append(command)
        executeCommand(gradleCommand)
    }

    PollingConditions getDefaultPollingConditions() {
        new PollingConditions(timeout: 240, initialDelay: 3, delay: 1, factor: 1)
    }

    void testOutputContains(String value) {
        defaultPollingConditions.eventually {
            assert output.toString().contains(value)
        }
    }

    private Process executeCommand(StringBuilder builder) {
        String[] args = builder.toString().split(' ')
        ProcessBuilder pb = new ProcessBuilder(args)
        pb.environment().put('JAVA_HOME', System.getenv('JAVA_HOME') ?: System.getProperty('java.home'))
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

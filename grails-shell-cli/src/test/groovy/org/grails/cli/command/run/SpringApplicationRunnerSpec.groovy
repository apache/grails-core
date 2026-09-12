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
package org.grails.cli.command.run

import java.lang.management.ManagementFactory
import java.util.concurrent.TimeUnit

import spock.lang.Specification
import spock.lang.TempDir

class SpringApplicationRunnerSpec extends Specification {

    @TempDir
    File tempDir

    void "ROOT OFF prints #failureMode failures to stderr"() {
        expect:
        runFixture(failureMode, false) == 'verified'

        where:
        failureMode << ['launch', 'reload', 'shutdown']
    }

    void "enabled logging captures #failureMode failures without stderr duplication"() {
        expect:
        runFixture(failureMode, true) == 'verified'

        where:
        failureMode << ['launch', 'reload', 'shutdown']
    }

    private String runFixture(String failureMode, boolean loggingEnabled) {
        File argumentsFile = new File(tempDir, "${failureMode}-${loggingEnabled}.args")
        argumentsFile.text = (jacocoAgentArguments() + [
            '-cp',
            testRuntimeClasspath(),
            SpringApplicationRunnerSpec.name,
            failureMode,
            loggingEnabled as String
        ]).join('\n')
        Process process = new ProcessBuilder(
            new File(System.getProperty('java.home'), 'bin/java').absolutePath,
            "@${argumentsFile.absolutePath}"
        ).redirectErrorStream(true).start()
        process.outputStream.close()
        try {
            awaitProcess(process, 'Spring application runner fixture')
            String output = process.inputStream.withCloseable { it.getText('UTF-8').trim() }
            assert process.exitValue() == 0 : output
            output.readLines().last()
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly()
                process.waitFor(5, TimeUnit.SECONDS)
            }
            process.inputStream.close()
            process.errorStream.close()
        }
    }

    private static void awaitProcess(Process process, String fixtureName) {
        if (process.waitFor(30, TimeUnit.SECONDS)) {
            return
        }
        process.destroyForcibly()
        if (!process.waitFor(5, TimeUnit.SECONDS)) {
            throw new AssertionError("${fixtureName} did not terminate after timing out")
        }
        throw new AssertionError("${fixtureName} timed out after 30 seconds")
    }

    private static String testRuntimeClasspath() {
        [
            System.getProperty('java.class.path'),
            new File('grails-shell-cli/build/resources/test').absolutePath
        ].join(File.pathSeparator)
    }

    private static List<String> jacocoAgentArguments() {
        ManagementFactory.runtimeMXBean.inputArguments.findAll {
            it.startsWith('-javaagent:') && it.contains('jacoco')
        }
    }

    static void main(String[] args) {
        String originalUserHome = System.getProperty('user.home')
        File userHome = File.createTempDir('spring-application-runner', '')
        String failureMode = args[0]
        boolean loggingEnabled = Boolean.parseBoolean(args[1])
        int reloadExitCode = 0
        try {
            System.setProperty('user.home', userHome.absolutePath)
            System.setProperty('slf4j.provider', 'ch.qos.logback.classic.spi.LogbackServiceProvider')
            LifecycleVerifier verifier = new LifecycleVerifier(failureMode, loggingEnabled)
            try {
                verifier.verify()
                println 'verified'
            }
            catch (Throwable throwable) {
                throwable.printStackTrace()
                if (failureMode == 'reload') {
                    reloadExitCode = 1
                }
                else {
                    throw throwable
                }
            }
        }
        finally {
            System.setProperty('user.home', originalUserHome)
            userHome.deleteDir()
        }
        if (failureMode == 'reload') {
            System.exit(reloadExitCode)
        }
    }
}

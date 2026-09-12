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
package org.grails.cli

import java.lang.management.ManagementFactory
import java.util.concurrent.TimeUnit

import spock.lang.Specification
import spock.lang.TempDir

class GrailsCliSpec extends Specification {

    @TempDir
    File tempDir

    void "shared settings parse failures are logged and reported to stderr"() {
        given:
        File grailsHome = new File(tempDir, '.grails')
        assert grailsHome.mkdirs()
        File settingsFile = new File(grailsHome, 'settings.groovy')
        settingsFile.text = 'invalid = ['

        expect:
        loadGrailsCli(tempDir) == 'verified'
    }

    private String loadGrailsCli(File homeDirectory) {
        File argumentsFile = new File(tempDir, 'grails-cli.args')
        argumentsFile.text = (jacocoAgentArguments() + [
            '-cp',
            testRuntimeClasspath(),
            GrailsCliSpec.name,
            homeDirectory.absolutePath
        ]).join('\n')
        Process process = new ProcessBuilder(
            new File(System.getProperty('java.home'), 'bin/java').absolutePath,
            "@${argumentsFile.absolutePath}"
        ).redirectErrorStream(true).start()
        process.outputStream.close()
        try {
            awaitProcess(process, 'Grails CLI fixture')
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
        System.setProperty('slf4j.provider', 'ch.qos.logback.classic.spi.LogbackServiceProvider')
        GrailsCliLoggingVerifier verifier = new GrailsCliLoggingVerifier(args[0])
        verifier.verify()
        println 'verified'
    }
}

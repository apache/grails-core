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
package grails.build.logging

import java.lang.management.ManagementFactory
import java.util.concurrent.TimeUnit

import org.slf4j.LoggerFactory
import spock.lang.Specification
import spock.lang.TempDir

class GrailsConsoleLoggingSpec extends Specification {

    @TempDir
    File tempDir

    void "invalid configured console failure is visible with #loggingDescription"() {
        given:
        String output = runFixture(apiOnly)

        expect:
        apiOnly ? output.contains('ClassNotFoundException: invalid.console.ClassName') :
            output.count('Unable to create configured Grails console invalid.console.ClassName') == 1
        output.readLines().last() == 'verified'

        where:
        apiOnly | loggingDescription
        true    | 'an API-only SLF4J classpath'
        false   | 'an ERROR-capable SLF4J provider'
    }

    private String runFixture(boolean apiOnly) {
        File argumentsFile = new File(tempDir, "grails-console-${apiOnly}.args")
        File outputFile = new File(tempDir, "grails-console-${apiOnly}.output")
        argumentsFile.text = (jacocoAgentArguments() + [
            '-cp',
            testRuntimeClasspath(apiOnly),
            GrailsConsoleLoggingSpec.name,
            apiOnly as String
        ]).join('\n')
        Process process = new ProcessBuilder(
            new File(System.getProperty('java.home'), 'bin/java').absolutePath,
            "@${argumentsFile.absolutePath}"
        ).redirectErrorStream(true).redirectOutput(outputFile).start()
        process.outputStream.close()
        try {
            awaitProcess(process, 'GrailsConsole logging fixture')
            String output = outputFile.getText('UTF-8').trim()
            assert process.exitValue() == 0 : output
            output
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly()
                process.waitFor(5, TimeUnit.SECONDS)
            }
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

    private static String testRuntimeClasspath(boolean apiOnly) {
        String[] entries = System.getProperty('java.class.path').split(File.pathSeparator)
        if (!apiOnly) {
            return entries.join(File.pathSeparator)
        }
        entries.findAll { !it.contains('slf4j-simple') && !it.contains('logback-classic') }.join(File.pathSeparator)
    }

    private static List<String> jacocoAgentArguments() {
        ManagementFactory.runtimeMXBean.inputArguments.findAll {
            it.startsWith('-javaagent:') && it.contains('jacoco')
        }
    }

    static void main(String[] args) {
        boolean apiOnly = Boolean.parseBoolean(args[0])
        System.setProperty('grails.console.class', 'invalid.console.ClassName')
        System.setProperty(GrailsConsole.ENABLE_INTERACTIVE, 'false')
        System.setProperty(GrailsConsole.ENABLE_TERMINAL, 'false')
        GrailsConsole console
        Throwable failure
        try {
            assert LoggerFactory.getLogger(GrailsConsole).errorEnabled == !apiOnly
            console = GrailsConsole.createInstance()
            assert console.class == GrailsConsole
        } catch (Throwable throwable) {
            failure = throwable
        } finally {
            console?.restoreOriginalSystemOutAndErr()
        }
        if (failure != null) {
            failure.printStackTrace()
            System.exit(1)
        }
        println 'verified'
    }
}

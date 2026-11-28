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

import org.grails.forge.application.ApplicationType
import spock.lang.Requires
import spock.util.environment.OperatingSystem

/**
 * Gradle writes a build's failure report to stderr, so a failed generated build is reported
 * properly only when the harness reads that stream as well: the report has to reach the assertion
 * message, and the build has to be able to exit although its report is larger than a pipe buffer.
 */
class CommandSpecBuildFailureSpec extends CommandSpec implements CommandFixture {

    // Larger than the 64 KiB pipe buffer of Linux and macOS, so a Gradle client left writing its
    // failure report to a pipe nobody reads blocks on it instead of exiting.
    private static final int FAILURE_MESSAGE_LENGTH = 70000

    void 'a generated build that fails is reported with its failure report instead of waiting out the poll budget'() {
        given: 'a generated project whose build fails while configuring, with a report larger than a pipe buffer'
        generateProject(ApplicationType.WEB)
        new File(dir, 'buildSrc').deleteDir()
        new File(dir, 'settings.gradle').text = "rootProject.name = 'foo'\n"
        new File(dir, 'build.gradle').text =
                "throw new GradleException('configuration failure ' + ('x' * ${FAILURE_MESSAGE_LENGTH}))\n"

        when:
        executeGradleCommand('build')
        testOutputContains('BUILD SUCCESSFUL')

        then: 'the report is in the message'
        AssertionError e = thrown()
        e.message.contains('configuration failure')
        e.message.contains('BUILD FAILED')

        and: 'the build ends as a failed one does, and is not left writing to a pipe nobody reads'
        SpecWaits.exited(process, 'the end of the generated build') == 1
    }

    @Requires({ !OperatingSystem.current.windows })
    void 'a build that has ended without the expected output is reported although its process is still there'() {
        when: 'a process that writes the last line of a build and stays until the feature lets it go'
        executeCommand(['sh', '-c', handshake.shell + 'echo "TestServiceSpec > test something PASSED"; echo "BUILD SUCCESSFUL in 1s"; hold end'])
        testOutputContains('TestServiceSpec > test something FAILED')

        then: 'the wait ended while the process was still there'
        AssertionError e = thrown()
        e.message.contains('ended without producing the expected output')
        e.message.contains('test something PASSED')
        process.alive
    }

    @Requires({ !OperatingSystem.current.windows })
    void 'the output a spec waits for is that of the build it started last'() {
        given:
        executeCommand(['sh', '-c', 'echo "BUILD FAILED in 1s"'])
        testOutputContains('BUILD FAILED')

        when: 'a second build, which has written nothing yet'
        executeCommand(['sh', '-c', handshake.shell + 'say started; hold started; echo "BUILD SUCCESSFUL in 1s"'])
        handshake.awaitStarted()

        then: 'what the spec reads is not what the build before wrote'
        !output.toString().contains('BUILD FAILED')

        when:
        handshake.release('started')
        testOutputContains('BUILD SUCCESSFUL')

        then:
        !output.toString().contains('BUILD FAILED')
    }

    // What a process the build left behind writes after the build has ended is not part of this: when the process
    // ends, the JDK keeps what is in the pipe and closes it, so a later line reaches the reader only if the reader
    // happened to be in a read at that moment.
    @Requires({ !OperatingSystem.current.windows })
    void 'what a build that has ended wrote is read to its end before the output a spec waits for is said not to be there'() {
        when: 'a process that writes a thousand lines and then the line, and ends before the reader can have read them all'
        executeCommand(['sh', '-c', 'i=0; while [ $i -lt 1000 ]; do echo "line $i of the build"; i=$((i + 1)); done; ' +
                'echo "TestServiceSpec > test something PASSED"'])
        // the process has ended when the spec looks, whatever the reader has read by then
        SpecWaits.exited(process, 'the end of the build')
        testOutputContains('TestServiceSpec > test something PASSED')

        then:
        noExceptionThrown()
    }

    @Requires({ !OperatingSystem.current.windows })
    void 'ending a build ends the processes it started'() {
        given: 'a process with a child that would outlive it, as it stays until the feature lets it go'
        executeCommand(['sh', '-c', handshake.shell + 'hold child & echo started; wait'])
        testOutputContains('started')
        List<ProcessHandle> children = process.descendants().toList()

        when:
        killProcess()

        then:
        !children.empty
        children.each { ProcessHandle child -> SpecWaits.ended(child, "the end of the process ${child.pid()} the build started") }
    }
}

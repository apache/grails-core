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

import io.micronaut.context.ApplicationContext
import org.grails.forge.application.ApplicationType
import spock.lang.AutoCleanup
import spock.lang.Shared

/**
 * Gradle writes a build's failure report to stderr, so a failed generated build is reported
 * properly only when the harness reads that stream as well: the report has to reach the assertion
 * message, and the build has to be able to exit although its report is larger than a pipe buffer.
 */
class CommandSpecBuildFailureSpec extends CommandSpec implements CommandFixture {

    // Larger than the 64 KiB pipe buffer of Linux and macOS, so a Gradle client left writing its
    // failure report to a pipe nobody reads blocks on it instead of exiting.
    private static final int FAILURE_MESSAGE_LENGTH = 70000

    @Shared
    @AutoCleanup
    ApplicationContext beanContext = ApplicationContext.run()

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

        then:
        AssertionError e = thrown()
        e.message.contains('exited with code 1')
        e.message.contains('configuration failure')
        e.message.contains('BUILD FAILED')
    }
}

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
package org.grails.gradle.plugin.core

import org.gradle.api.GradleException
import spock.lang.Specification
import spock.lang.Unroll

class BootRunExitCodeVerifierSpec extends Specification {

    @Unroll
    void "exit code #code from a deliberate stop is tolerated as a successful build"() {
        when: "an exit code produced by a graceful stop is verified"
        new BootRunExitCodeVerifier().verify(code)

        then: "the build is not failed"
        noExceptionThrown()

        where: "the application exited cleanly (0), or on SIGTERM (143) or SIGINT (130)"
        code << [0, 143, 130]
    }

    @Unroll
    void "exit code #code from an abnormal exit fails the build"() {
        when: "any other non-zero exit code is verified"
        new BootRunExitCodeVerifier().verify(code)

        then: "the build is failed and the message names the exit code"
        GradleException e = thrown()
        e.message.contains(code.toString())

        where: "a real startup error (1), a force-kill (137) or any other non-zero code"
        code << [1, 2, 137, 255]
    }
}

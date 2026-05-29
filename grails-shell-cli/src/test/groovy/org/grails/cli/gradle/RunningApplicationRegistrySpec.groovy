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

import org.gradle.tooling.CancellationTokenSource
import spock.lang.Specification

/**
 * Tests for {@link RunningApplicationRegistry}, the CLI only mechanism that allows
 * stop-app to cancel a running run-app build.
 */
class RunningApplicationRegistrySpec extends Specification {

    List<CancellationTokenSource> created = []

    private CancellationTokenSource newToken() {
        CancellationTokenSource token = Mock(CancellationTokenSource)
        created << token
        token
    }

    void cleanup() {
        // Defensively clear the static registry so feature methods do not pollute each other
        created.each { RunningApplicationRegistry.deregister(it) }
        created.clear()
    }

    void "isApplicationRunning reflects registered tokens"() {
        expect: "no application is running initially"
        !RunningApplicationRegistry.isApplicationRunning()

        when: "a token is registered"
        CancellationTokenSource token = newToken()
        RunningApplicationRegistry.register(token)

        then: "an application is reported as running"
        RunningApplicationRegistry.isApplicationRunning()

        when: "the token is deregistered"
        RunningApplicationRegistry.deregister(token)

        then: "no application is running"
        !RunningApplicationRegistry.isApplicationRunning()
    }

    void "register and deregister ignore null tokens"() {
        when:
        RunningApplicationRegistry.register(null)

        then:
        !RunningApplicationRegistry.isApplicationRunning()

        when:
        RunningApplicationRegistry.deregister(null)

        then:
        noExceptionThrown()
    }

    void "stopAll returns false when no applications are running"() {
        expect:
        !RunningApplicationRegistry.stopAll()
    }

    void "stopAll requests cancellation of all registered tokens without removing them"() {
        given: "two registered tokens"
        CancellationTokenSource token1 = newToken()
        CancellationTokenSource token2 = newToken()
        RunningApplicationRegistry.register(token1)
        RunningApplicationRegistry.register(token2)

        when: "all applications are stopped"
        boolean result = RunningApplicationRegistry.stopAll()

        then: "cancellation is requested on each token"
        result
        1 * token1.cancel()
        1 * token2.cancel()

        and: "tokens remain registered until the build deregisters them"
        RunningApplicationRegistry.isApplicationRunning()
    }

    void "stopAll keeps cancelling remaining tokens when one throws"() {
        given:
        CancellationTokenSource failing = newToken()
        CancellationTokenSource healthy = newToken()
        RunningApplicationRegistry.register(failing)
        RunningApplicationRegistry.register(healthy)

        when:
        boolean result = RunningApplicationRegistry.stopAll()

        then: "a failure cancelling one token does not stop the others"
        result
        1 * failing.cancel() >> { throw new RuntimeException("boom") }
        1 * healthy.cancel()
    }

    void "awaitStop returns true once all tokens are deregistered"() {
        given:
        CancellationTokenSource token = newToken()
        RunningApplicationRegistry.register(token)

        when: "the token is deregistered shortly after the wait begins"
        Thread.start {
            sleep 200
            RunningApplicationRegistry.deregister(token)
        }
        boolean stopped = RunningApplicationRegistry.awaitStop(5000)

        then:
        stopped
        !RunningApplicationRegistry.isApplicationRunning()
    }

    void "awaitStop returns false when tokens are not deregistered within the timeout"() {
        given:
        CancellationTokenSource token = newToken()
        RunningApplicationRegistry.register(token)

        expect:
        !RunningApplicationRegistry.awaitStop(300)
        RunningApplicationRegistry.isApplicationRunning()
    }
}

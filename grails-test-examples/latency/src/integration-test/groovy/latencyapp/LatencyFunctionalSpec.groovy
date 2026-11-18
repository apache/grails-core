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
package latencyapp

import java.time.Duration

import spock.lang.Specification

import grails.testing.mixin.integration.Integration
import org.apache.grails.testing.http.client.HttpClientSupport

/**
 * Confirms the behavior of the {@code grails-testing-support-latency} module against a running
 * application. The test environment enables latency for {@code /slow/*} with a minimum delay of
 * 3 seconds (see {@code application.yml}), so matched requests must take at least that long while
 * unmatched requests stay well under it.
 */
@Integration
class LatencyFunctionalSpec extends Specification implements HttpClientSupport {

    private static final long MIN_DELAY_NANOS = Duration.ofSeconds(3).toNanos()

    void 'requests matching the latency url patterns are delayed by at least the configured minimum'() {
        when:
        long start = System.nanoTime()
        def response = http('/slow/ping')
        long elapsedNanos = System.nanoTime() - start

        then:
        response.assertStatus(200)
        response.assertContains('pong')
        elapsedNanos >= MIN_DELAY_NANOS
    }

    void 'requests outside the latency url patterns are not delayed'() {
        when:
        long start = System.nanoTime()
        def response = http('/fast/ping')
        long elapsedNanos = System.nanoTime() - start

        then:
        response.assertStatus(200)
        response.assertContains('pong')
        elapsedNanos < MIN_DELAY_NANOS
    }
}

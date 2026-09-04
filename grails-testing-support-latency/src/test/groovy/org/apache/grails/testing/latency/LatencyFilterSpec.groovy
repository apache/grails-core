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
package org.apache.grails.testing.latency

import java.time.Duration

import spock.lang.Specification

import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

class LatencyFilterSpec extends Specification {

    void 'delays the request by at least the configured minimum before continuing the chain'() {
        given:
        def filter = new LatencyFilter(new LatencyProperties(
                minDelay: Duration.ofMillis(100),
                maxDelay: Duration.ofMillis(100)
        ))
        def chain = new MockFilterChain()

        when:
        long start = System.nanoTime()
        filter.doFilter(new MockHttpServletRequest('GET', '/report/show'), new MockHttpServletResponse(), chain)
        long elapsedNanos = System.nanoTime() - start

        then:
        elapsedNanos >= Duration.ofMillis(100).toNanos()
        chain.request != null
    }

    void 'does not delay when the probability is zero'() {
        given:
        def filter = new LatencyFilter(new LatencyProperties(
                minDelay: Duration.ofSeconds(5),
                maxDelay: Duration.ofSeconds(5),
                probability: 0.0d
        ))
        def chain = new MockFilterChain()

        when:
        long start = System.nanoTime()
        filter.doFilter(new MockHttpServletRequest('GET', '/'), new MockHttpServletResponse(), chain)
        long elapsedNanos = System.nanoTime() - start

        then: 'the request completes long before the configured delay'
        elapsedNanos < Duration.ofSeconds(5).toNanos()
        chain.request != null
    }

    void 'abandons the request when interrupted while delaying'() {
        given:
        def filter = new LatencyFilter(new LatencyProperties(
                minDelay: Duration.ofSeconds(5),
                maxDelay: Duration.ofSeconds(5)
        ))
        def chain = new MockFilterChain()

        when: 'the thread is interrupted so the delay is cut short'
        Thread.currentThread().interrupt()
        filter.doFilter(new MockHttpServletRequest('GET', '/'), new MockHttpServletResponse(), chain)

        then: 'the interrupt flag is re-asserted and the chain is never invoked'
        Thread.interrupted()
        chain.request == null
    }

    void 'random delays never undercut the configured minimum'() {
        given:
        def filter = new LatencyFilter(new LatencyProperties(
                minDelay: Duration.ofMillis(20),
                maxDelay: Duration.ofMillis(60),
                seed: 42L
        ))

        expect:
        timings(filter, 10).every { it >= Duration.ofMillis(20).toNanos() }
    }

    void 'rejects invalid configuration: #description'() {
        when:
        new LatencyFilter(properties)

        then:
        thrown(IllegalArgumentException)

        where:
        description                  | properties
        'max-delay below min-delay'  | new LatencyProperties(minDelay: Duration.ofSeconds(2), maxDelay: Duration.ofSeconds(1))
        'negative min-delay'         | new LatencyProperties(minDelay: Duration.ofMillis(-1))
        'probability above 1.0'      | new LatencyProperties(probability: 1.5d)
        'negative probability'       | new LatencyProperties(probability: -0.5d)
        'NaN probability'            | new LatencyProperties(probability: Double.NaN)
        'infinite probability'       | new LatencyProperties(probability: Double.POSITIVE_INFINITY)
    }

    private static List<Long> timings(LatencyFilter filter, int requests) {
        (1..requests).collect {
            long start = System.nanoTime()
            filter.doFilter(new MockHttpServletRequest('GET', '/'), new MockHttpServletResponse(), new MockFilterChain())
            System.nanoTime() - start
        }
    }
}

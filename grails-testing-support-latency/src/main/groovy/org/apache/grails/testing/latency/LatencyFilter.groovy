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

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletException
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

import org.springframework.web.filter.OncePerRequestFilter

/**
 * Servlet filter that delays each matched request by a random amount of time before
 * passing it down the filter chain.
 * <p>
 * Slowing responses down widens the window in which timing bugs occur, turning
 * intermittent functional test flakiness (assertions that race a navigation, missing
 * waits, ordering assumptions) into deterministic failures. It is intended purely as a
 * testing aid and should never be enabled in production.
 *
 * @see LatencyAutoConfiguration
 * @see LatencyProperties
 */
@Slf4j
@CompileStatic
class LatencyFilter extends OncePerRequestFilter {

    private final long minDelayMillis
    private final long maxDelayMillis
    private final double probability
    private final Random random

    LatencyFilter(LatencyProperties properties) {
        minDelayMillis = properties.minDelay.toMillis()
        maxDelayMillis = properties.maxDelay.toMillis()
        probability = properties.probability
        if (minDelayMillis < 0) {
            throw new IllegalArgumentException("grails.testing.latency.min-delay must not be negative, was ${properties.minDelay}")
        }
        if (maxDelayMillis < minDelayMillis) {
            throw new IllegalArgumentException("grails.testing.latency.max-delay (${properties.maxDelay}) " +
                    "must not be less than min-delay (${properties.minDelay})")
        }
        if (!Double.isFinite(probability) || probability < 0.0d || probability > 1.0d) {
            throw new IllegalArgumentException("grails.testing.latency.probability must be between 0.0 and 1.0, was $probability")
        }
        random = properties.seed == null ? new Random() : new Random(properties.seed)
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        long delayMillis = nextDelayMillis()
        if (delayMillis > 0) {
            log.debug('Delaying {} {} by {}ms', request.method, request.requestURI, delayMillis)
            try {
                Thread.sleep(delayMillis)
            }
            catch (InterruptedException ignored) {
                // The container is shutting down the worker thread; abandon the request
                // instead of running it on an interrupted thread.
                Thread.currentThread().interrupt()
                return
            }
        }
        filterChain.doFilter(request, response)
    }

    private long nextDelayMillis() {
        if (probability < 1.0d && random.nextDouble() >= probability) {
            return 0
        }
        if (minDelayMillis == maxDelayMillis) {
            return minDelayMillis
        }
        random.nextLong(minDelayMillis, maxDelayMillis + 1)
    }
}

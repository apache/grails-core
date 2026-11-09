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

import groovy.transform.CompileStatic

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Configuration for artificial request latency, bound from the {@code grails.testing.latency} prefix.
 * <p>
 * Example configuration ({@code application.yml} of the application under test, or system properties
 * passed to the test JVM):
 * <pre>
 * grails:
 *     testing:
 *         latency:
 *             enabled: true
 *             min-delay: 250ms
 *             max-delay: 2s
 *             probability: 0.5
 * </pre>
 * Durations without a unit suffix are interpreted as milliseconds.
 *
 * @see LatencyAutoConfiguration
 */
@CompileStatic
@ConfigurationProperties(prefix = 'grails.testing.latency')
class LatencyProperties {

    /**
     * Whether to inject artificial latency. Disabled by default so the library is inert
     * unless explicitly switched on for a test run.
     */
    boolean enabled = false

    /**
     * Lower bound (inclusive) of the random delay applied to a request.
     */
    Duration minDelay = Duration.ZERO

    /**
     * Upper bound (inclusive) of the random delay applied to a request.
     */
    Duration maxDelay = Duration.ofSeconds(2)

    /**
     * Fraction of requests to delay, between {@code 0.0} (none) and {@code 1.0} (all, the default).
     */
    double probability = 1.0d

    /**
     * Servlet url patterns the latency filter is mapped to. Defaults to every request.
     */
    List<String> urlPatterns = ['/*']

    /**
     * Optional fixed seed for the random number generator, for reproducible delay sequences.
     * When unset, delays vary from run to run.
     * <p>
     * The generator is shared by all requests, so with concurrent requests which request
     * receives which delay is order-dependent; a fixed seed only yields a fully reproducible
     * assignment of delays to requests when requests are serialized.
     */
    Long seed
}

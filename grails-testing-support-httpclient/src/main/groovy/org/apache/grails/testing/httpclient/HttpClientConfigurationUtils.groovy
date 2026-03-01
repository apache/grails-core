/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.grails.testing.httpclient

import java.time.Duration

import groovy.transform.CompileStatic
import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType

import io.micronaut.http.client.DefaultHttpClientConfiguration
import io.micronaut.http.client.HttpClientConfiguration

@CompileStatic
class HttpClientConfigurationUtils {

    /**
     * Builds a HttpClientConfiguration using known Micronaut HTTP client
     * system properties (micronaut.http.client.*).
     * Only applies properties that are set.
     *
     * Supported:
     * - micronaut.http.client.read-timeout
     * - micronaut.http.client.connect-timeout
     * - micronaut.http.client.read-idle-timeout
     * - micronaut.http.client.follow-redirects
     * - micronaut.http.client.max-content-length
     */
    static HttpClientConfiguration fromSystemProperties() {
        def cfg = new DefaultHttpClientConfiguration()

        // durations
        setIfPresentDuration('micronaut.http.client.read-timeout') { cfg.readTimeout = it }
        setIfPresentDuration('micronaut.http.client.connect-timeout') { cfg.connectTimeout = it }
        setIfPresentDuration('micronaut.http.client.read-idle-timeout') { cfg.readIdleTimeout = it }

        // booleans
        setIfPresentBoolean('micronaut.http.client.follow-redirects') { cfg.followRedirects = it }

        // numbers
        setIfPresentInteger('micronaut.http.client.max-content-length') { cfg.maxContentLength = it }

        return cfg
    }

    private static void setIfPresentDuration(
            String key,
            @ClosureParams(value = SimpleType, options = 'java.time.Duration') Closure<?> setter
    ) {
        def raw = System.getProperty(key)
        if (!raw) return
        setter.call(parseDuration(raw, key))
    }

    private static void setIfPresentBoolean(
            String key,
            @ClosureParams(value = SimpleType, options = 'boolean') Closure<?> setter
    ) {
        def raw = System.getProperty(key)
        if (!raw) return
        setter.call(Boolean.parseBoolean(raw.trim()))
    }

    private static void setIfPresentInteger(
            String key,
            @ClosureParams(value = SimpleType, options = 'int') Closure<?> setter
    ) {
        def raw = System.getProperty(key)
        if (!raw) return
        try {
            setter.call(Integer.parseInt(raw.trim()))
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("System property '$key' must be an integer, was '$raw'", e)
        }
    }

    /**
     * Accepts:
     * - Micronaut style: 20s, 200ms, 1m, 2h, 1d
     * - ISO-8601: PT20S, PT200MS, etc.
     * - plain number (treated as milliseconds): 20000
     */
    private static Duration parseDuration(String raw, String key) {
        def v = raw.trim()
        if (!v) throw new IllegalArgumentException("System property '$key' was blank")

        // ISO-8601
        if (v.startsWith('P')) {
            try {
                return Duration.parse(v)
            } catch (Exception e) {
                throw new IllegalArgumentException("System property '$key' must be an ISO-8601 duration, was '$raw'", e)
            }
        }

        // plain number -> millis
        if (v ==~ /^\d+$/) {
            return Duration.ofMillis(Long.parseLong(v))
        }

        // suffix format
        def m = (v =~ /^(\d+)\s*(ms|s|m|h|d)$/)
        if (!m.matches()) {
            throw new IllegalArgumentException(
                    "System property '$key' must be like '20s', '200ms', '1m', '2h', '1d' or ISO-8601 'PT20S'. Was '$raw'"
            )
        }

        long n = Long.parseLong(m.group(1))
        String unit = m.group(2)

        switch (unit) {
            case 'ms': return Duration.ofMillis(n)
            case 's' : return Duration.ofSeconds(n)
            case 'm' : return Duration.ofMinutes(n)
            case 'h' : return Duration.ofHours(n)
            case 'd' : return Duration.ofDays(n)
            default:
                throw new IllegalStateException("Unhandled duration unit '$unit' for '$key'")
        }
    }
}

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
package org.springframework.security.web.util.matcher

import groovy.transform.CompileStatic

import jakarta.servlet.http.HttpServletRequest

import org.springframework.util.AntPathMatcher
import org.springframework.web.util.UrlPathHelper

@CompileStatic
class AntPathRequestMatcher implements RequestMatcher {

    private static final UrlPathHelper PATH_HELPER = UrlPathHelper.defaultInstance
    private static final UrlPathHelper RAW_PATH_HELPER = rawPathHelper()

    private final String pattern
    private final String httpMethod
    private final boolean caseSensitive
    private final AntPathMatcher pathMatcher = new AntPathMatcher()

    AntPathRequestMatcher(String pattern) {
        this(pattern, null, false)
    }

    AntPathRequestMatcher(String pattern, String httpMethod, boolean caseSensitive) {
        this.pattern = pattern
        this.httpMethod = httpMethod
        this.caseSensitive = caseSensitive
    }

    @Override
    boolean matches(HttpServletRequest request) {
        if (httpMethod && !httpMethod.equalsIgnoreCase(request.method)) {
            return false
        }
        String path = getPathWithinApplication(request)
        String candidate = caseSensitive ? path : path.toLowerCase(Locale.ENGLISH)
        String matcherPattern = caseSensitive ? pattern : pattern.toLowerCase(Locale.ENGLISH)
        pathMatcher.match(matcherPattern, candidate)
    }

    /**
     * The request path within the application, resolved with the same {@link UrlPathHelper} call that Grails URL
     * mapping dispatch uses, so an encoded or matrix-parameter variant of a path selects the same filter chain as
     * the path it is dispatched to. Path parameters are removed per segment before percent-decoding (Jakarta Servlet
     * 6.0 section 3.5.2), so an encoded semicolon stays a literal character (RFC 3986 section 2.2), and the path is
     * decoded exactly once (RFC 3986 section 2.4). Like dispatch, and unlike RFC 3986 section 6.2.2.1, the context
     * path is compared case-insensitively, and during a {@code RequestDispatcher} include the included URI is
     * matched. A URI with an illegal percent escape, which a Servlet 6.0 container rejects with 400 before the filter
     * chain runs, is matched undecoded rather than failing chain selection.
     */
    private static String getPathWithinApplication(HttpServletRequest request) {
        try {
            PATH_HELPER.getPathWithinApplication(request)
        } catch (IllegalArgumentException ignored) {
            RAW_PATH_HELPER.getPathWithinApplication(request)
        }
    }

    private static UrlPathHelper rawPathHelper() {
        UrlPathHelper helper = new UrlPathHelper()
        helper.urlDecode = false
        helper
    }

    @Override
    String toString() {
        httpMethod ? "Ant [pattern='$pattern', $httpMethod]" : "Ant [pattern='$pattern']"
    }

    @Override
    boolean equals(Object other) {
        if (this.is(other)) {
            return true
        }
        if (!(other instanceof AntPathRequestMatcher)) {
            return false
        }
        def matcher = other as AntPathRequestMatcher
        pattern == matcher.pattern && httpMethod == matcher.httpMethod && caseSensitive == matcher.caseSensitive
    }

    @Override
    int hashCode() {
        Objects.hash(pattern, httpMethod, caseSensitive)
    }
}

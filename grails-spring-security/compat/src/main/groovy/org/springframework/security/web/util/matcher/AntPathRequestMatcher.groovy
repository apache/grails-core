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

@CompileStatic
class AntPathRequestMatcher implements RequestMatcher {

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
        def path = request.requestURI ?: '/'
        def contextPath = request.contextPath
        if (contextPath && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length())
        }
        def candidate = caseSensitive ? path : path.toLowerCase(Locale.ENGLISH)
        def matcherPattern = caseSensitive ? pattern : pattern.toLowerCase(Locale.ENGLISH)
        pathMatcher.match(matcherPattern, candidate)
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

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
package grails.plugin.springsecurity.web

import groovy.transform.CompileStatic

import jakarta.servlet.http.HttpServletRequest

import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher
import org.springframework.security.web.util.matcher.RequestMatcher

/**
 * Decides which requests the request cache saves as the page to redirect to after a login: all but those the
 * {@code requestCache.ignoredPatterns} setting names, which browsers and their tools send on their own. Spring Security
 * leaves the same paths out of the matcher its configuration DSL builds, which the plugin does not use, and that list
 * is private to the DSL.
 *
 * @since 9.0
 */
@CompileStatic
class SavedRequestMatcher implements RequestMatcher {

    private List<RequestMatcher> ignored = []

    /**
     * @param patterns the path patterns, relative to the context path, of the requests not to save
     */
    void setIgnoredPatterns(Collection<String> patterns) {
        ignored = patterns.collect { String pattern -> PathPatternRequestMatcher.pathPattern(pattern) as RequestMatcher }
    }

    @Override
    boolean matches(HttpServletRequest request) {
        !ignored.any { RequestMatcher matcher -> matcher.matches(request) }
    }
}

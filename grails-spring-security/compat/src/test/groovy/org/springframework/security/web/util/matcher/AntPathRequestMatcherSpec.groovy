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

import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.web.util.WebUtils

import spock.lang.Specification
import spock.lang.Unroll

class AntPathRequestMatcherSpec extends Specification {

    @Unroll
    void 'matches the canonical request path under context path /app: #requestUri'() {
        given:
        def request = new MockHttpServletRequest('GET', requestUri)
        request.contextPath = '/app'

        expect:
        new AntPathRequestMatcher('/admin/**').matches(request) == matches

        where:
        requestUri                    | matches | reason
        '/app/admin/deleteUser'       | true    | 'plain path'
        '/app/admin;x=1/deleteUser'   | true    | 'matrix parameters are removed'
        '/app/%61dmin/deleteUser'     | true    | 'percent escapes are decoded'
        '/app/admin%3Bx=1/deleteUser' | false   | 'an encoded semicolon is a literal character, so this is a different path'
        '/app/public'                 | false   | 'unrelated path'
        '/APP/admin/deleteUser'       | true    | 'the context path is compared case-insensitively, as dispatch does'
        '/app/admin//deleteUser'      | true    | 'empty segments are collapsed, as dispatch does'
    }

    void 'matches the request path when deployed at the root'() {
        expect:
        new AntPathRequestMatcher('/admin/**').matches(new MockHttpServletRequest('GET', '/admin;x=1/deleteUser'))
        !new AntPathRequestMatcher('/admin/**').matches(new MockHttpServletRequest('GET', '/public'))
    }

    @Unroll
    void 'a malformed percent escape is matched undecoded instead of failing chain selection: #pattern'() {
        given:
        def request = new MockHttpServletRequest('GET', '/app/foo%')
        request.contextPath = '/app'

        expect:
        new AntPathRequestMatcher(pattern).matches(request) == matches

        where:
        pattern     | matches
        '/admin/**' | false
        '/foo*'     | true
    }

    @Unroll
    void 'the included path is matched during an include, as dispatch does: #pattern'() {
        given:
        def request = new MockHttpServletRequest('GET', '/app/public')
        request.contextPath = '/app'
        request.setAttribute(WebUtils.INCLUDE_REQUEST_URI_ATTRIBUTE, '/app/admin/x')

        expect:
        new AntPathRequestMatcher(pattern).matches(request) == matches

        where:
        pattern      | matches
        '/admin/**'  | true
        '/public/**' | false
    }

    void 'pattern matching is case-insensitive by default and case-sensitive on request'() {
        given:
        def request = new MockHttpServletRequest('GET', '/app/ADMIN/deleteUser')
        request.contextPath = '/app'

        expect:
        new AntPathRequestMatcher('/admin/**').matches(request)
        !new AntPathRequestMatcher('/admin/**', null, true).matches(request)
    }

    void 'the HTTP method must match when one is given'() {
        given:
        def request = new MockHttpServletRequest('POST', '/admin/deleteUser')

        expect:
        new AntPathRequestMatcher('/admin/**', 'POST', false).matches(request)
        new AntPathRequestMatcher('/admin/**', 'post', false).matches(request)
        !new AntPathRequestMatcher('/admin/**', 'GET', false).matches(request)
    }

    void 'an empty request URI is matched as the root path'() {
        expect:
        new AntPathRequestMatcher('/').matches(new MockHttpServletRequest('GET', ''))
        !new AntPathRequestMatcher('/admin/**').matches(new MockHttpServletRequest('GET', ''))
    }

    void 'equality and description are based on the pattern, method and case sensitivity'() {
        expect:
        new AntPathRequestMatcher('/admin/**') == new AntPathRequestMatcher('/admin/**', null, false)
        new AntPathRequestMatcher('/admin/**').hashCode() == new AntPathRequestMatcher('/admin/**', null, false).hashCode()
        new AntPathRequestMatcher('/admin/**') != new AntPathRequestMatcher('/admin/**', 'GET', false)
        new AntPathRequestMatcher('/admin/**') != new AntPathRequestMatcher('/admin/**', null, true)
        new AntPathRequestMatcher('/admin/**') != new AntPathRequestMatcher('/other/**')
        new AntPathRequestMatcher('/admin/**').toString() == "Ant [pattern='/admin/**']"
        new AntPathRequestMatcher('/admin/**', 'GET', false).toString() == "Ant [pattern='/admin/**', GET]"
    }
}

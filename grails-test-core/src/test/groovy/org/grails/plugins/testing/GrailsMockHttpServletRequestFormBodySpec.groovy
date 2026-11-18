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
package org.grails.plugins.testing

import jakarta.servlet.FilterChain

import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.web.filter.FormContentFilter
import spock.lang.Specification

class GrailsMockHttpServletRequestFormBodySpec extends Specification {

    private static final String FORM = 'application/x-www-form-urlencoded'

    void 'a form-encoded #method body is #outcome, as Spring FormContentFilter treats it'() {
        given:
        def request = new GrailsMockHttpServletRequest()
        request.method = method
        request.contentType = FORM
        request.content = 'title=Grails'.bytes

        expect:
        request.getParameter('title') == title

        and: 'the filter this request stands in for agrees'
        filteredTitle(method) == title

        where:
        method   | title
        'PUT'    | 'Grails'
        'PATCH'  | 'Grails'
        'DELETE' | 'Grails'
        'QUERY'  | 'Grails'
        'POST'   | null
        'GET'    | null

        outcome = title ? 'parsed' : 'left unparsed'
    }

    void 'a QUERY body of another content type is left unparsed'() {
        given:
        def request = new GrailsMockHttpServletRequest()
        request.method = 'QUERY'
        request.contentType = 'application/json'
        request.content = '{"title":"Grails"}'.bytes

        expect:
        request.getParameter('title') == null
        new String(request.contentAsByteArray) == '{"title":"Grails"}'
    }

    private static String filteredTitle(String method) {
        def request = new MockHttpServletRequest(method, '/')
        request.contentType = FORM
        request.content = 'title=Grails'.bytes
        String title = null
        new FormContentFilter().doFilter(request, new MockHttpServletResponse(), { req, res ->
            title = req.getParameter('title')
        } as FilterChain)
        title
    }
}

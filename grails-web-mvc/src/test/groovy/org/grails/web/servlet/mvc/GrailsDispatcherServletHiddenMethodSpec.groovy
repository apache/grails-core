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
package org.grails.web.servlet.mvc

import org.grails.web.util.HiddenHttpMethod

import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.mock.web.MockServletContext
import org.springframework.web.context.request.RequestContextHolder

import spock.lang.Specification

/**
 * When the hidden HTTP method filter is disabled the dispatcher resolves the override itself, and publishes
 * the result through {@link GrailsWebRequest} so that everything reading the request through the Grails API
 * agrees with the method the URL mappings matched on.
 *
 * This is what makes 'allowedMethods' work: the generated check reads the controller's 'request', which
 * resolves to {@code GrailsWebRequest.getCurrentRequest()}. Without the wrapper it would still report POST
 * and a form submit routed to 'delete' would be rejected with a 405.
 */
class GrailsDispatcherServletHiddenMethodSpec extends Specification {

    private GrailsWebRequest webRequest

    void setup() {
        webRequest = null
    }

    void cleanup() {
        RequestContextHolder.resetRequestAttributes()
    }

    void 'a form POST naming DELETE is dispatched as a DELETE'() {
        given:
        def request = formPost('DELETE')

        when:
        def dispatched = dispatch(request, true)

        then: 'the request the dispatcher hands downstream reports the overridden method'
        dispatched.method == 'DELETE'

        and: 'the override is published, so allowedMethods resolves the same method the mappings matched on'
        HiddenHttpMethod.effectiveMethod(request) == 'DELETE'
    }

    void 'PUT and PATCH are resolved too'() {
        expect:
        dispatch(formPost(requested), true).method == requested

        where:
        requested << ['PUT', 'PATCH']
    }

    void 'the request is untouched while the filter is doing the rewriting'() {
        given: 'the default, where the servlet filter has already rewritten the method'
        def request = formPost('DELETE')

        when:
        def dispatched = dispatch(request, false)

        then: 'the dispatcher returns the very same request, leaving multipart handling as it was'
        dispatched.is(request)
        dispatched.method == 'POST'
    }

    void 'a POST without the parameter is untouched'() {
        given:
        def request = new MockHttpServletRequest('POST', '/books/1')

        expect:
        dispatch(request, true).is(request)
    }

    void 'only the methods a browser form cannot send are accepted'() {
        given: 'unlike the servlet filter, which applies any method name it is given'
        def request = formPost(requested)

        expect: 'the request is left as a POST rather than being turned into a read or an unknown method'
        dispatch(request, true).is(request)

        where:
        requested << ['GET', 'HEAD', 'TRACE', 'BOGUS']
    }

    void 'a non-POST request is never overridden'() {
        given:
        def request = new MockHttpServletRequest('GET', '/books/1')
        request.addParameter('_method', 'DELETE')

        expect:
        dispatch(request, true).is(request)
    }

    private static MockHttpServletRequest formPost(String requested) {
        def request = new MockHttpServletRequest('POST', '/books/1')
        request.contentType = 'application/x-www-form-urlencoded'
        request.addParameter('_method', requested)
        request
    }

    private def dispatch(MockHttpServletRequest request, boolean resolveHiddenHttpMethod) {
        def servlet = new GrailsDispatcherServlet()
        servlet.resolveHiddenHttpMethod = resolveHiddenHttpMethod

        def response = new MockHttpServletResponse()
        webRequest = new GrailsWebRequest(request, response, new MockServletContext())
        RequestContextHolder.setRequestAttributes(webRequest)

        servlet.checkMultipart(request)
    }
}

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
package org.grails.web.filters

import jakarta.servlet.FilterChain

import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import spock.lang.Specification
import spock.lang.Unroll

class HiddenHttpMethodFilterSpec extends Specification {

    HiddenHttpMethodFilter filter = new HiddenHttpMethodFilter()

    @Unroll
    void 'a POST is overridden to #expected when #source'() {
        given:
        MockHttpServletRequest request = new MockHttpServletRequest()
        MockHttpServletResponse response = new MockHttpServletResponse()
        request.method = 'POST'
        if (param) {
            request.addParameter('_method', param)
        }
        if (header) {
            request.addHeader(HiddenHttpMethodFilter.HEADER_X_HTTP_METHOD_OVERRIDE, header)
        }
        String seenMethod

        when:
        filter.doFilter(request, response, { req, res -> seenMethod = req.method } as FilterChain)

        then:
        seenMethod == expected

        where:
        source                     | param    | header   || expected
        'no override is supplied'  | null     | null     || 'POST'
        'a _method parameter wins' | 'DELETE' | null     || 'DELETE'
        'the override header wins' | null     | 'delete' || 'DELETE'
    }

    void 'a non-POST request is never overridden even with an override parameter'() {
        given:
        MockHttpServletRequest request = new MockHttpServletRequest()
        MockHttpServletResponse response = new MockHttpServletResponse()
        request.method = 'GET'
        request.addParameter('_method', 'DELETE')
        String seenMethod

        when:
        filter.doFilter(request, response, { req, res -> seenMethod = req.method } as FilterChain)

        then:
        seenMethod == 'GET'
    }

    void 'a custom method parameter name can be configured'() {
        given:
        filter.methodParam = 'httpMethod'
        MockHttpServletRequest request = new MockHttpServletRequest()
        MockHttpServletResponse response = new MockHttpServletResponse()
        request.method = 'POST'
        request.addParameter('httpMethod', 'PUT')
        String seenMethod

        when:
        filter.doFilter(request, response, { req, res -> seenMethod = req.method } as FilterChain)

        then:
        seenMethod == 'PUT'
    }

    void 'an empty method parameter name is rejected'() {
        when:
        filter.setMethodParam('')

        then:
        thrown(IllegalArgumentException)
    }

    void 'the default method parameter name is exposed'() {
        expect:
        HiddenHttpMethodFilter.DEFAULT_METHOD_PARAM == '_method'
    }

}

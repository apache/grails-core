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

import org.grails.web.filters.HiddenHttpMethodFilter
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.mock.web.MockMultipartHttpServletRequest

import jakarta.servlet.FilterChain

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertThrows

/**
 * @author Graeme Rocher
 * @since 1.1
 */
class HiddenHttpMethodFilterTests {

    @Test
    void testDefaultCase() {
        def filter = new HiddenHttpMethodFilter()
        def req = new MockHttpServletRequest()
        def res = new MockHttpServletResponse()
        req.setMethod("POST")
        String method
        filter.doFilter(req, res, { req2, res2 -> method = req2.method } as FilterChain)

        assertEquals "POST", method
    }

    @Test
    void testWithParameter() {
        def filter = new HiddenHttpMethodFilter()
        def req = new MockHttpServletRequest()
        def res = new MockHttpServletResponse()
        req.addParameter("_method", "DELETE")
        req.setMethod("POST")
        String method
        filter.doFilter(req, res, { req2, res2 -> method = req2.method } as FilterChain)

        assertEquals "DELETE", method
    }

    @Test
    void testMultipartRequestWithUnreadableParametersIsPassedOn() {
        // An upload breaching the container's limits fails part parsing, so reading _method throws.
        // The filter must not abort the request here - DispatcherServlet.checkMultipart raises the
        // failure during dispatch, where the application's error handling can see it.
        def filter = new HiddenHttpMethodFilter()
        def req = unreadableParameterRequest('multipart/form-data; boundary=test')
        def res = new MockHttpServletResponse()
        String method
        filter.doFilter(req, res, { req2, res2 -> method = req2.method } as FilterChain)

        assertEquals "POST", method
    }

    @Test
    void testMultipartRequestStillHonoursTheMethodParameter() {
        def filter = new HiddenHttpMethodFilter()
        def req = new MockMultipartHttpServletRequest()
        req.contentType = 'multipart/form-data; boundary=test'
        req.addParameter("_method", "PUT")
        req.setMethod("POST")
        def res = new MockHttpServletResponse()
        String method
        filter.doFilter(req, res, { req2, res2 -> method = req2.method } as FilterChain)

        assertEquals "PUT", method
    }

    @Test
    void testUnreadableParametersStillThrowForANonMultipartRequest() {
        def filter = new HiddenHttpMethodFilter()
        def req = unreadableParameterRequest('application/x-www-form-urlencoded')
        def res = new MockHttpServletResponse()

        def e = assertThrows(IllegalStateException) {
            filter.doFilter(req, res, { req2, res2 -> } as FilterChain)
        }

        assertEquals 'parameters are unreadable', e.message
    }

    private static MockHttpServletRequest unreadableParameterRequest(String contentType) {
        def request = new MockHttpServletRequest() {
            @Override
            String getParameter(String name) {
                throw new IllegalStateException('parameters are unreadable')
            }
        }
        request.contentType = contentType
        request.setMethod("POST")
        request
    }

    @Test
    void testWithHeader() {
        def filter = new HiddenHttpMethodFilter()
        def req = new MockHttpServletRequest()
        req.addHeader(HiddenHttpMethodFilter.HEADER_X_HTTP_METHOD_OVERRIDE, "DELETE")
        def res = new MockHttpServletResponse()
        // req.addParameter("_method", "DELETE")
        req.setMethod("POST")
        String method
        filter.doFilter(req, res, { req2, res2 -> method = req2.method } as FilterChain)

        assertEquals "DELETE", method
    }
}

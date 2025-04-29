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

import jakarta.servlet.FilterChain

import static org.junit.jupiter.api.Assertions.assertEquals

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

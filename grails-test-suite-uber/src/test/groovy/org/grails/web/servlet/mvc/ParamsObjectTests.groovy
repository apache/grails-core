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

import grails.web.servlet.mvc.GrailsParameterMap
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest

import static org.junit.jupiter.api.Assertions.assertEquals

/**
 * Tests the behaviour of the GrailsParameterMap params object.
 *
 * @author Graeme Rocher
 * @since 1.1
 */
class ParamsObjectTests {

    /**
     * The grails params object exhibits multi dimensional behaviour. This test tests that.
     */
    @Test
    void testMultiDHashBehaviour() {
        def request = new MockHttpServletRequest()

        request.addParameter("test", "1")
        request.addParameter("firstName", "Fred")
        request.addParameter("lastName", "Flintstone")
        request.addParameter("book.title", "The Stand")
        request.addParameter("book.author.name", "Stephen King")
        request.addParameter("book.id", "10")
        request.addParameter("publisher.name", "Apress")
        request.addParameter("publisher.authors[0].name", "Fred")
        request.addParameter("publisher.authors[1].name", "Joe")
        request.addParameter("test..foo..bar", "Stuff")

        def params = new GrailsParameterMap(request)

        assertEquals "1", params.test
        assertEquals "Fred", params.firstName
        assertEquals "Flintstone", params.lastName
        assertEquals "The Stand", params.'book.title'
        assertEquals "Stephen King", params.'book.author.name'
        assertEquals "The Stand", params['book'].title
        assertEquals "Stephen King", params['book']['author.name']
        assertEquals "Stephen King", params['book']['author'].name
        assertEquals "Apress", params['publisher'].name
        assertEquals "Fred", params['publisher'].'authors[0].name'
    }
}

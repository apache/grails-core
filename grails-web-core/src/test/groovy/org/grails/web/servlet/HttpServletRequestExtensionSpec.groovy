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
package org.grails.web.servlet

import groovy.transform.CompileStatic

import jakarta.servlet.http.HttpServletRequest

import org.springframework.mock.web.MockHttpServletRequest

import spock.lang.Specification

class HttpServletRequestExtensionSpec extends Specification {

    void "Test typed converters read and convert request attributes"() {
        given:
            HttpServletRequest request = new MockHttpServletRequest()
            request.setAttribute('page', '3')
            request.setAttribute('total', 99L)
            request.setAttribute('name', 'Bob')

        expect:
            request.int('page') == 3
            request.long('total') == 99L
            request.string('name') == 'Bob'
    }

    void "Test typed converters are null-safe and honor defaults"() {
        given:
            HttpServletRequest request = new MockHttpServletRequest()

        expect:
            request.int('missing') == null
            request.int('missing', 1) == 1
            request.boolean('missing', true) == true
            request.string('missing', 'fallback') == 'fallback'
            request.list('missing') == []
    }

    void "Test converters resolve under static compilation"() {
        given:
            HttpServletRequest request = new MockHttpServletRequest()
            request.setAttribute('page', '5')

        expect:
            StaticCaller.readPage(request) == 5
            StaticCaller.readName(request) == 'anonymous'
    }

    @CompileStatic
    static class StaticCaller {
        static Integer readPage(HttpServletRequest request) {
            request.int('page', 1)
        }

        static String readName(HttpServletRequest request) {
            request.string('user', 'anonymous')
        }
    }
}

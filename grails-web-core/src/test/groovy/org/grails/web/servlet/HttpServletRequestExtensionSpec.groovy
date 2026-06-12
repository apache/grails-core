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


    void "Test the Class-typed getAttribute returns the attribute only when it is an instance of the requested type"() {
        given:
            HttpServletRequest request = new MockHttpServletRequest()
            request.setAttribute('principal', new StringBuilder('alice'))
            request.setAttribute('count', 42)

        expect: 'a matching type returns the typed attribute'
            request.getAttribute('principal', StringBuilder).toString() == 'alice'
            request.getAttribute('count', Integer) == 42

        and: 'a supertype of the stored attribute matches too'
            request.getAttribute('principal', CharSequence).toString() == 'alice'

        and: 'absent and wrong-typed attributes read as null - no coercion is attempted'
            request.getAttribute('missing', StringBuilder) == null
            request.getAttribute('count', String) == null

        and: 'primitive class literals are normalized to their wrappers'
            request.getAttribute('count', int) == 42

        when: 'a null type is passed'
            request.getAttribute('count', (Class) null)

        then: 'the contract violation is reported clearly rather than as a bare NPE'
            thrown(IllegalArgumentException)

        and: 'the Class-typed overload resolves to the requested type under static compilation'
            StaticCaller.readPrincipal(request).toString() == 'alice'
    }

    void "Test the Class-typed getAttribute honors the default for absent and wrong-typed attributes"() {
        given:
            HttpServletRequest request = new MockHttpServletRequest()
            request.setAttribute('count', 42)

        expect:
            request.getAttribute('missing', String, 'fallback') == 'fallback'
            request.getAttribute('count', String, 'fallback') == 'fallback'
            request.getAttribute('count', Integer, 7) == 42
    }

    @CompileStatic
    static class StaticCaller {
        static StringBuilder readPrincipal(HttpServletRequest request) {
            StringBuilder principal = request.getAttribute('principal', StringBuilder)
            principal
        }

        static Integer readPage(HttpServletRequest request) {
            request.int('page', 1)
        }

        static String readName(HttpServletRequest request) {
            request.string('user', 'anonymous')
        }
    }
}

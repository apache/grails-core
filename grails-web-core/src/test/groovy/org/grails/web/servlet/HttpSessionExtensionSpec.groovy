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

import jakarta.servlet.http.HttpSession

import org.springframework.mock.web.MockHttpSession

import spock.lang.Specification

class HttpSessionExtensionSpec extends Specification {

    void "Test typed converters read and convert session attributes"() {
        given:
            HttpSession session = new MockHttpSession()
            session.setAttribute('age', '42')
            session.setAttribute('rate', 3.5d)
            session.setAttribute('active', 'true')
            session.setAttribute('tz', 'America/Los_Angeles')

        expect:
            session.int('age') == 42
            session.long('age') == 42L
            session.double('rate') == 3.5d
            session.boolean('active') == true
            session.string('tz') == 'America/Los_Angeles'
    }

    void "Test typed converters are null-safe and honor defaults"() {
        given:
            HttpSession session = new MockHttpSession()

        expect:
            session.int('missing') == null
            session.int('missing', 7) == 7
            session.boolean('missing') == null
            session.boolean('missing', true) == true
            session.string('missing') == null
            session.string('missing', 'fallback') == 'fallback'
            session.list('missing') == []
    }

    void "Test the date converter parses session attributes"() {
        given:
            HttpSession session = new MockHttpSession()
            session.setAttribute('createdOn', '2026-06-04 09:30:00.0')

        expect:
            session.date('createdOn') != null

        and: "an unparseable value yields null"
            session.date('missing') == null
    }

    void "Test the list converter always returns a list"() {
        given:
            HttpSession session = new MockHttpSession()
            session.setAttribute('single', 'one')
            session.setAttribute('many', ['a', 'b'] as String[])

        expect:
            session.list('single') == ['one']
            session.list('many') == ['a', 'b']
    }

    void "Test converters resolve under static compilation"() {
        given:
            HttpSession session = new MockHttpSession()
            session.setAttribute('age', '21')

        expect: "the statically compiled helper resolves session.int / session.string"
            StaticCaller.readAge(session) == 21
            StaticCaller.readTimeZone(session) == 'America/Los_Angeles'
    }


    void "Test the Class-typed getAttribute returns the attribute only when it is an instance of the requested type"() {
        given:
            HttpSession session = new MockHttpSession()
            session.setAttribute('principal', new StringBuilder('alice'))
            session.setAttribute('count', 42)

        expect: 'a matching type returns the typed attribute'
            session.getAttribute('principal', StringBuilder).toString() == 'alice'
            session.getAttribute('count', Integer) == 42

        and: 'a supertype of the stored attribute matches too'
            session.getAttribute('principal', CharSequence).toString() == 'alice'

        and: 'absent and wrong-typed attributes read as null - no coercion is attempted'
            session.getAttribute('missing', StringBuilder) == null
            session.getAttribute('count', String) == null

        and: 'the Class-typed overload resolves to the requested type under static compilation'
            StaticCaller.readPrincipal(session).toString() == 'alice'
    }

    void "Test the Class-typed getAttribute honors the default for absent and wrong-typed attributes"() {
        given:
            HttpSession session = new MockHttpSession()
            session.setAttribute('count', 42)

        expect:
            session.getAttribute('missing', String, 'fallback') == 'fallback'
            session.getAttribute('count', String, 'fallback') == 'fallback'
            session.getAttribute('count', Integer, 7) == 42
    }

    @CompileStatic
    static class StaticCaller {
        static StringBuilder readPrincipal(HttpSession session) {
            StringBuilder principal = session.getAttribute('principal', StringBuilder)
            principal
        }

        static Integer readAge(HttpSession session) {
            session.int('age')
        }

        static String readTimeZone(HttpSession session) {
            session.string('userTimeZoneId', 'America/Los_Angeles')
        }
    }
}

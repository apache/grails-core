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
package grails.web.servlet.mvc

import org.springframework.mock.web.MockHttpServletRequest

import spock.lang.Specification

class GrailsHttpSessionSpec extends Specification {

    void 'invalidate does not create a servlet session when none exists'() {
        given:
        def request = new MockHttpServletRequest()
        def session = new GrailsHttpSession(request)

        expect:
        request.getSession(false) == null

        when:
        session.invalidate()

        then:
        request.getSession(false) == null
    }

    void 'invalidate invalidates an existing servlet session without prior wrapper access'() {
        given:
        def request = new MockHttpServletRequest()
        def existingSession = request.getSession(true)
        existingSession.setAttribute('name', 'value')
        def session = new GrailsHttpSession(request)

        when:
        session.invalidate()

        then:
        request.getSession(false) == null

        when:
        existingSession.getAttribute('name')

        then:
        thrown(IllegalStateException)

        when:
        session.getAttribute('name')

        then:
        thrown(IllegalStateException)
    }
}

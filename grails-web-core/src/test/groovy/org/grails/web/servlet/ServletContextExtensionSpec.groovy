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

import jakarta.servlet.ServletContext

import org.springframework.mock.web.MockServletContext

import spock.lang.Specification

class ServletContextExtensionSpec extends Specification {

    void "Test typed converters read and convert servletContext attributes"() {
        given:
            ServletContext context = new MockServletContext()
            context.setAttribute('maxUploads', '10')
            context.setAttribute('appName', 'demo')

        expect:
            context.int('maxUploads') == 10
            context.string('appName') == 'demo'
    }

    void "Test typed converters are null-safe and honor defaults"() {
        given:
            ServletContext context = new MockServletContext()

        expect:
            context.int('missing') == null
            context.int('missing', 4) == 4
            context.string('missing', 'fallback') == 'fallback'
    }

    void "Test converters resolve under static compilation"() {
        given:
            ServletContext context = new MockServletContext()
            context.setAttribute('maxUploads', '8')

        expect:
            StaticCaller.readMaxUploads(context) == 8
            StaticCaller.readAppName(context) == 'grails'
    }


    void "Test the Class-typed getAttribute returns the attribute only when it is an instance of the requested type"() {
        given:
            ServletContext context = new MockServletContext()
            context.setAttribute('principal', new StringBuilder('alice'))
            context.setAttribute('count', 42)

        expect: 'a matching type returns the typed attribute'
            context.getAttribute('principal', StringBuilder).toString() == 'alice'
            context.getAttribute('count', Integer) == 42

        and: 'a supertype of the stored attribute matches too'
            context.getAttribute('principal', CharSequence).toString() == 'alice'

        and: 'absent and wrong-typed attributes read as null - no coercion is attempted'
            context.getAttribute('missing', StringBuilder) == null
            context.getAttribute('count', String) == null

        and: 'primitive class literals are normalized to their wrappers'
            context.getAttribute('count', int) == 42

        when: 'a null type is passed'
            context.getAttribute('count', (Class) null)

        then: 'the contract violation is reported clearly rather than as a bare NPE'
            thrown(IllegalArgumentException)

        and: 'the Class-typed overload resolves to the requested type under static compilation'
            StaticCaller.readPrincipal(context).toString() == 'alice'
    }

    void "Test the Class-typed getAttribute honors the default for absent and wrong-typed attributes"() {
        given:
            ServletContext context = new MockServletContext()
            context.setAttribute('count', 42)

        expect:
            context.getAttribute('missing', String, 'fallback') == 'fallback'
            context.getAttribute('count', String, 'fallback') == 'fallback'
            context.getAttribute('count', Integer, 7) == 42
    }

    @CompileStatic
    static class StaticCaller {
        static StringBuilder readPrincipal(ServletContext context) {
            StringBuilder principal = context.getAttribute('principal', StringBuilder)
            principal
        }

        static Integer readMaxUploads(ServletContext context) {
            context.int('maxUploads', 1)
        }

        static String readAppName(ServletContext context) {
            context.string('appName', 'grails')
        }
    }
}

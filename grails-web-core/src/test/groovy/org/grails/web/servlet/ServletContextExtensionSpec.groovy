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

    @CompileStatic
    static class StaticCaller {
        static Integer readMaxUploads(ServletContext context) {
            context.int('maxUploads', 1)
        }

        static String readAppName(ServletContext context) {
            context.string('appName', 'grails')
        }
    }
}

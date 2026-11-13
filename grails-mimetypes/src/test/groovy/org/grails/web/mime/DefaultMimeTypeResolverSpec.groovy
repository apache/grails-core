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
package org.grails.web.mime

import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.mock.web.MockServletContext
import org.springframework.web.context.request.RequestContextHolder
import spock.lang.Specification

import org.grails.web.servlet.mvc.GrailsWebRequest

class DefaultMimeTypeResolverSpec extends Specification {

    private final DefaultMimeTypeResolver resolver = new DefaultMimeTypeResolver()

    void cleanup() {
        RequestContextHolder.resetRequestAttributes()
    }

    void 'without a current request nothing is resolved'() {
        expect:
        resolver.resolveResponseMimeType() == null
        resolver.resolveRequestMimeType() == null
        resolver.resolveResponseMimeType(null) == null
        resolver.resolveRequestMimeType(null) == null
    }

    void 'the current request is the one resolved'() {
        given:
        def request = new MockHttpServletRequest(new MockServletContext())
        request.addHeader('Accept', 'application/json')
        request.contentType = 'application/xml'
        def webRequest = new GrailsWebRequest(request, new MockHttpServletResponse(), request.servletContext)
        RequestContextHolder.setRequestAttributes(webRequest)

        expect: 'the same as naming it'
        resolver.resolveResponseMimeType().name == resolver.resolveResponseMimeType(webRequest).name
        resolver.resolveRequestMimeType().name == resolver.resolveRequestMimeType(webRequest).name

        and:
        resolver.resolveResponseMimeType(webRequest) != null
        resolver.resolveRequestMimeType(webRequest).extension == 'xml'
    }

    void 'the methods that take no request are compiled to call GrailsWebRequest directly, which needs no reflection hint'() {
        given: 'a default argument, which this replaces, is compiled dynamically even in a statically compiled class'
        def bytes = DefaultMimeTypeResolver.getResourceAsStream('DefaultMimeTypeResolver.class').bytes

        expect: 'a direct call names the method and its descriptor in the constant pool, where a dynamic one has only the name'
        new String(bytes, 'ISO-8859-1').contains('()Lorg/grails/web/servlet/mvc/GrailsWebRequest;')
    }
}

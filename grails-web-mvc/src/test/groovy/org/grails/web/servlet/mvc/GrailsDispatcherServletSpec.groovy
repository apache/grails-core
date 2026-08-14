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

import jakarta.servlet.http.HttpServletRequestWrapper
import jakarta.servlet.http.HttpServletRequest

import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.mock.web.MockMultipartHttpServletRequest
import org.springframework.mock.web.MockServletContext
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import org.springframework.web.multipart.MultipartResolver

import spock.lang.Specification

import org.springframework.web.servlet.DispatcherServlet

class GrailsDispatcherServletSpec extends Specification {

    def cleanup() {
        RequestContextHolder.resetRequestAttributes()
    }

    void 'buildRequestAttributes propagates a multipart request nested in a wrapper'() {
        given:
        def servletContext = new MockServletContext()
        def request = new MockHttpServletRequest(servletContext)
        def response = new MockHttpServletResponse()
        def multipartRequest = new MockMultipartHttpServletRequest(servletContext)
        def wrappedRequest = new HttpServletRequestWrapper(multipartRequest)
        def webRequest = new GrailsWebRequest(request, response, servletContext)
        def servlet = new TestGrailsDispatcherServlet()

        when:
        def result = servlet.buildRequestAttributesForTest(wrappedRequest, response, webRequest)

        then:
        result.is(webRequest)
        webRequest.currentRequest.is(multipartRequest)
    }

    void 'checkMultipart returns and stores a newly resolved multipart request'() {
        given:
        def request = new MockHttpServletRequest(contentType: 'multipart/form-data; boundary=test')
        def response = new MockHttpServletResponse()
        def servletContext = new MockServletContext()
        def webRequest = new GrailsWebRequest(request, response, servletContext)
        def multipartRequest = new MockMultipartHttpServletRequest(servletContext)
        def resolver = Mock(MultipartResolver)
        def servlet = new TestGrailsDispatcherServlet(multipartResolverForTest: resolver)
        RequestContextHolder.setRequestAttributes(webRequest)

        when:
        def result = servlet.checkMultipartForTest(request)

        then:
        1 * resolver.isMultipart(request) >> true
        1 * resolver.resolveMultipart(request) >> multipartRequest
        result.is(multipartRequest)
        webRequest.currentRequest.is(multipartRequest)

    }

    private static class TestGrailsDispatcherServlet extends GrailsDispatcherServlet {

        ServletRequestAttributes buildRequestAttributesForTest(
                HttpServletRequest request, MockHttpServletResponse response, GrailsWebRequest previousAttributes) {
            buildRequestAttributes(request, response, previousAttributes)
        }

        HttpServletRequest checkMultipartForTest(HttpServletRequest request) {
            checkMultipart(request)
        }

        void setMultipartResolverForTest(MultipartResolver resolver) {
            def field = DispatcherServlet.getDeclaredField('multipartResolver')
            field.accessible = true
            field.set(this, resolver)
        }
    }
}

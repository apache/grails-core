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

import jakarta.servlet.http.HttpServletRequest

import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockMultipartFile
import org.springframework.mock.web.MockMultipartHttpServletRequest
import org.springframework.mock.web.MockServletConfig
import org.springframework.mock.web.MockServletContext
import org.springframework.web.context.WebApplicationContext
import org.springframework.web.context.support.StaticWebApplicationContext
import org.springframework.web.multipart.MultipartResolver

import org.grails.web.util.WebUtils

import spock.lang.Specification

class GrailsDispatcherServletSpec extends Specification {

    MockServletContext servletContext = new MockServletContext()
    MockMultipartHttpServletRequest resolvedRequest = new MockMultipartHttpServletRequest().tap {
        it.addFile(new MockMultipartFile('file', 'test.txt', 'text/plain', 'content'.bytes))
    }
    MultipartResolver multipartResolver = Mock(MultipartResolver)

    void 'checkMultipart hands the resolved request to the dispatch and publishes it for application code'() {
        given:
        def servlet = dispatcherServlet()
        def request = multipartRequest()

        when:
        def processedRequest = servlet.callCheckMultipart(request)

        then:
        1 * multipartResolver.isMultipart(request) >> true
        1 * multipartResolver.resolveMultipart(request) >> resolvedRequest

        and: 'the dispatch runs against the resolved request, as Spring MVC expects'
        processedRequest.is(resolvedRequest)

        and: 'and it is reachable from the request Grails bound before the DispatcherServlet ran'
        WebUtils.resolveMultipartRequest(request).is(resolvedRequest)
    }

    void 'checkMultipart leaves an ordinary request untouched'() {
        given:
        def servlet = dispatcherServlet()
        def request = new MockHttpServletRequest()

        when:
        def processedRequest = servlet.callCheckMultipart(request)

        then:
        1 * multipartResolver.isMultipart(request) >> false
        0 * multipartResolver.resolveMultipart(_)

        and:
        processedRequest.is(request)
        WebUtils.resolveMultipartRequest(request) == null
    }

    void 'checkMultipart does not resolve during an error dispatch'() {
        given:
        def servlet = dispatcherServlet()
        def request = multipartRequest()
        request.setAttribute(WebUtils.ERROR_STATUS_CODE_ATTRIBUTE, 500)

        when:
        def processedRequest = servlet.callCheckMultipart(request)

        then: 'the parts belong to the original dispatch and must not be resolved a second time'
        0 * multipartResolver.resolveMultipart(_)
        processedRequest.is(request)
    }

    void 'checkMultipart does not resolve during a forward or include'() {
        given:
        def servlet = dispatcherServlet()
        def request = multipartRequest()
        request.setAttribute(attribute, '/target')

        when:
        def processedRequest = servlet.callCheckMultipart(request)

        then:
        0 * multipartResolver.resolveMultipart(_)
        processedRequest.is(request)

        where:
        attribute << [WebUtils.FORWARD_REQUEST_URI_ATTRIBUTE, WebUtils.INCLUDE_REQUEST_URI_ATTRIBUTE]
    }

    private MockHttpServletRequest multipartRequest() {
        new MockHttpServletRequest(contentType: 'multipart/form-data; boundary=test', method: 'POST')
    }

    private TestGrailsDispatcherServlet dispatcherServlet() {
        def context = new StaticWebApplicationContext()
        context.servletContext = servletContext
        context.beanFactory.registerSingleton(
                org.springframework.web.servlet.DispatcherServlet.MULTIPART_RESOLVER_BEAN_NAME, multipartResolver)
        context.refresh()
        def servlet = new TestGrailsDispatcherServlet(context)
        servlet.init(new MockServletConfig(servletContext, 'grails'))
        servlet
    }

    private static class TestGrailsDispatcherServlet extends GrailsDispatcherServlet {

        TestGrailsDispatcherServlet(WebApplicationContext webApplicationContext) {
            super(webApplicationContext)
        }

        HttpServletRequest callCheckMultipart(HttpServletRequest request) {
            checkMultipart(request)
        }
    }
}

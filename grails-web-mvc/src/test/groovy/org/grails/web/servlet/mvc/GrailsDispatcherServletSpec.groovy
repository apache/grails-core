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

import jakarta.servlet.DispatcherType
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest

import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.mock.web.MockMultipartFile
import org.springframework.mock.web.MockMultipartHttpServletRequest
import org.springframework.mock.web.MockServletConfig
import org.springframework.mock.web.MockServletContext
import org.springframework.web.context.WebApplicationContext
import org.springframework.web.context.request.RequestAttributes
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import org.springframework.web.context.support.StaticWebApplicationContext
import org.springframework.web.filter.RequestContextFilter
import org.springframework.web.multipart.MultipartResolver
import org.springframework.web.servlet.handler.SimpleUrlHandlerMapping
import org.springframework.web.servlet.mvc.Controller

import org.grails.web.util.GrailsApplicationAttributes
import org.grails.web.util.WebUtils

import spock.lang.Issue
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

    @Issue('https://github.com/apache/grails-core/issues/15819')
    void 'service publishes an active request and restores the previous binding for #binding with #dispatchType'() {
        given:
        def context = new StaticWebApplicationContext()
        context.servletContext = servletContext
        def request = new MockHttpServletRequest(servletContext, 'GET', '/test')
        request.dispatcherType = dispatchType
        def response = new MockHttpServletResponse()
        def existing = new GrailsWebRequest(request, response, servletContext)
        existing.params['retained'] = 'value'
        if (binding != 'missing' && binding != 'threadOnly') {
            request.setAttribute(GrailsApplicationAttributes.WEB_REQUEST, existing)
        }
        if (binding == 'completed' || binding == 'completedAttribute') {
            existing.requestCompleted()
        }
        def previous = binding in ['thread', 'threadOnly', 'completed']
                ? existing
                : (binding in ['plain', 'completedAttribute'] ? new ServletRequestAttributes(request, response) : null)
        RequestContextHolder.setRequestAttributes(previous)
        GrailsWebRequest seen
        int completed = 0
        def controller = { req, res ->
            seen = GrailsWebRequest.lookup()
            assert seen.is(GrailsWebRequest.lookup(req))
            assert seen.active
            seen.registerDestructionCallback('completion', { completed++ } as Runnable, RequestAttributes.SCOPE_REQUEST)
            null
        } as Controller
        def mapping = new SimpleUrlHandlerMapping(['/test': controller], 0)
        context.beanFactory.registerSingleton('handlerMapping', mapping)
        context.refresh()
        mapping.setApplicationContext(context)
        def servlet = new GrailsDispatcherServlet(context)
        servlet.init(new MockServletConfig(servletContext))

        when:
        servlet.service(request, response)

        then:
        seen != null
        seen.is(existing) == reuse
        !reuse || seen.params.retained == 'value'
        !seen.active
        completed == 1
        RequestContextHolder.requestAttributes.is(previous)

        cleanup:
        RequestContextHolder.resetRequestAttributes()
        servlet?.destroy()
        context.close()

        where:
        binding              | dispatchType           | reuse
        'missing'            | DispatcherType.REQUEST | false
        'attribute'          | DispatcherType.REQUEST | true
        'thread'             | DispatcherType.REQUEST | true
        'threadOnly'         | DispatcherType.REQUEST | true
        'plain'              | DispatcherType.REQUEST | true
        'completed'          | DispatcherType.REQUEST | false
        'completed'          | DispatcherType.ASYNC   | false
        'completed'          | DispatcherType.ERROR   | false
        'completedAttribute' | DispatcherType.ASYNC   | false
        'completedAttribute' | DispatcherType.ERROR   | false
    }

    @Issue('https://github.com/apache/grails-core/issues/15819')
    void 'nested #dispatchType through a late filter restores the enclosing Grails request'() {
        given:
        def context = new StaticWebApplicationContext()
        context.servletContext = servletContext
        def request = new MockHttpServletRequest(servletContext, 'GET', '/test')
        request.dispatcherType = dispatchType
        request.setAttribute(dispatchAttribute, '/test')
        def response = new MockHttpServletResponse()
        def outer = new GrailsWebRequest(request, response, servletContext)
        outer.renderView = false
        WebUtils.storeGrailsWebRequest(outer)
        GrailsWebRequest inner
        def controller = { req, res ->
            inner = GrailsWebRequest.lookup()
            assert inner.is(GrailsWebRequest.lookup(req))
            assert inner.active
            assert !inner.is(outer)
            null
        } as Controller
        def mapping = new SimpleUrlHandlerMapping(['/test': controller], 0)
        context.beanFactory.registerSingleton('handlerMapping', mapping)
        context.refresh()
        mapping.setApplicationContext(context)
        def servlet = new GrailsDispatcherServlet(context)
        servlet.init(new MockServletConfig(servletContext))
        def filter = new GrailsWebRequestFilter()
        filter.setServletContext(servletContext)
        filter.setApplicationContext(context)

        when:
        filter.doFilter(request, response, { req, res ->
            new RequestContextFilter().doFilter(req, res, { nestedRequest, nestedResponse ->
                servlet.service(nestedRequest, nestedResponse)
            } as FilterChain)
        } as FilterChain)

        then:
        inner != null
        !inner.active
        GrailsWebRequest.lookup().is(outer)
        GrailsWebRequest.lookup(request).is(outer)
        outer.active
        !outer.renderViewRequested

        cleanup:
        RequestContextHolder.resetRequestAttributes()
        servlet?.destroy()
        context.close()

        where:
        dispatchType           | dispatchAttribute
        DispatcherType.INCLUDE | WebUtils.INCLUDE_REQUEST_URI_ATTRIBUTE
        DispatcherType.FORWARD | WebUtils.FORWARD_REQUEST_URI_ATTRIBUTE
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

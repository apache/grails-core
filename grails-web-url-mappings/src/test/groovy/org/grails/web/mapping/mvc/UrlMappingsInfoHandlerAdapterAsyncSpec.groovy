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
package org.grails.web.mapping.mvc

import jakarta.servlet.DispatcherType

import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.mock.web.MockServletContext
import org.springframework.web.context.request.async.DeferredResult
import org.springframework.web.context.request.async.StandardServletAsyncWebRequest
import org.springframework.web.context.request.async.WebAsyncManager
import org.springframework.web.context.request.async.WebAsyncUtils
import org.springframework.web.servlet.ModelAndView

import grails.core.GrailsControllerClass
import grails.web.mapping.UrlMappingInfo
import org.grails.web.servlet.mvc.GrailsWebRequest
import org.grails.web.util.WebUtils
import spock.lang.Specification

class UrlMappingsInfoHandlerAdapterAsyncSpec extends Specification {

    private MockHttpServletRequest request
    private MockHttpServletResponse response
    private WebAsyncManager asyncManager
    private UrlMappingsInfoHandlerAdapter adapter

    void setup() {
        request = new MockHttpServletRequest(new MockServletContext())
        request.asyncSupported = true
        response = new MockHttpServletResponse()
        new GrailsWebRequest(request, response, request.servletContext)
        asyncManager = WebAsyncUtils.getAsyncManager(request)
        asyncManager.asyncWebRequest = new StandardServletAsyncWebRequest(request, response)
        adapter = new UrlMappingsInfoHandlerAdapter()
    }

    void 'returns the Spring concurrent ModelAndView on async dispatch'() {
        given:
        ModelAndView expected = new ModelAndView('/book/index', [books: ['one']])
        completeAsyncRequest(expected)

        expect:
        adapter.handle(request, response, Mock(UrlMappingInfo)).is(expected)
    }

    void 'rethrows the Spring concurrent exception on async dispatch'() {
        given:
        completeAsyncRequest(new IllegalArgumentException('bad'))

        when:
        adapter.handle(request, response, Mock(UrlMappingInfo))

        then:
        IllegalArgumentException failure = thrown()
        failure.message == 'bad'
    }

    void 'runs the action a forward made during the async dispatch names, rather than answering with the async result'() {
        given: 'a dispatch that returned the result of async processing, whose view forwards to another action'
        completeAsyncRequest(new ModelAndView('/book/index'))
        request.dispatcherType = DispatcherType.FORWARD
        WebUtils.storeGrailsWebRequest(new GrailsWebRequest(request, response, request.servletContext))
        def controllerClass = Mock(GrailsControllerClass) {
            getFullName() >> 'BookController'
            isSingleton() >> false
            newInstance() >> new Object()
            getDefaultAction() >> 'show'
        }
        def info = Mock(GrailsControllerUrlMappingInfo) {
            getControllerClass() >> controllerClass
            getActionName() >> 'show'
        }

        when:
        adapter.handle(request, response, info)

        then: 'the action runs, and the async result is left for the dispatch it belongs to'
        1 * controllerClass.invoke(_, 'show') >> [book: 'one']
        asyncManager.hasConcurrentResult()
    }

    private void completeAsyncRequest(Object result) {
        DeferredResult<Object> deferredResult = new DeferredResult<>()
        asyncManager.startDeferredResultProcessing(deferredResult)
        deferredResult.setResult(result)
        request.dispatcherType = DispatcherType.ASYNC
        request.setAttribute(WebUtils.ASYNC_REQUEST_URI_ATTRIBUTE, '/book/index')
    }
}

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
package org.grails.plugins.web.async.mvc

import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.mock.web.MockServletContext
import org.springframework.web.context.request.async.WebAsyncManager
import org.springframework.web.context.request.async.WebAsyncUtils
import org.springframework.web.context.request.async.StandardServletAsyncWebRequest
import org.springframework.web.servlet.ModelAndView
import org.springframework.web.context.support.StaticWebApplicationContext
import org.springframework.core.env.MapPropertySource
import org.springframework.mock.env.MockEnvironment

import org.grails.async.factory.future.CompletableFuturePromise
import org.grails.web.servlet.mvc.GrailsWebRequest
import org.grails.web.util.GrailsApplicationAttributes
import spock.lang.Specification

class AsyncActionResultTransformerSpec extends Specification {

    private MockHttpServletRequest request
    private MockHttpServletResponse response
    private GrailsWebRequest webRequest
    private WebAsyncManager asyncManager

    void setup() {
        request = new MockHttpServletRequest(new MockServletContext())
        request.asyncSupported = true
        response = new MockHttpServletResponse()
        webRequest = new GrailsWebRequest(request, response, request.servletContext)
        asyncManager = WebAsyncUtils.getAsyncManager(request)
    }

    void 'delegates successful promise rendering to Spring deferred result processing'() {
        given:
        CompletableFuturePromise<Map<String, Object>> promise = new CompletableFuturePromise<>()

        when:
        Object transformed = new AsyncActionResultTransformer().transformActionResult(webRequest, '/book/index', promise)
        promise.complete([books: ['one', 'two']])

        then:
        transformed == null
        request.asyncStarted
        asyncManager.hasConcurrentResult()
        ModelAndView result = (ModelAndView) asyncManager.concurrentResult
        result.viewName == '/book/index'
        result.model.books == ['one', 'two']
    }

    void 'delegates promise failures to Spring exception processing'() {
        given:
        CompletableFuturePromise<Object> promise = new CompletableFuturePromise<>()

        when:
        new AsyncActionResultTransformer().transformActionResult(webRequest, '/book/index', promise)
        promise.completeExceptionally(failure)

        then:
        asyncManager.hasConcurrentResult()
        asyncManager.concurrentResult.message == 'bad'
        asyncManager.concurrentResult.is(failure)

        where:
        failure << [new IllegalStateException('bad', new IOException('inner')), new IOException('bad', new IOException('inner'))]
    }

    void 'joins async processing started eagerly by a web promise'() {
        given:
        StandardServletAsyncWebRequest asyncWebRequest = new StandardServletAsyncWebRequest(request, response)
        asyncManager.asyncWebRequest = asyncWebRequest
        asyncWebRequest.startAsync()
        CompletableFuturePromise<Map<String, Object>> promise = new CompletableFuturePromise<>()

        when:
        new AsyncActionResultTransformer().transformActionResult(webRequest, '/book/index', promise)
        promise.complete([books: ['one']])

        then:
        asyncManager.concurrentResult instanceof ModelAndView
        ((ModelAndView) asyncManager.concurrentResult).model.books == ['one']
    }

    void 'does not restart a completed async request'() {
        given:
        def transformer = new AsyncActionResultTransformer()
        transformer.transformActionResult(webRequest, '/book/index', new CompletableFuturePromise<>())
        asyncManager.asyncWebRequest.onComplete(new jakarta.servlet.AsyncEvent(request.asyncContext))

        when:
        transformer.transformActionResult(webRequest, '/book/index', new CompletableFuturePromise<>())

        then:
        def failure = thrown(IllegalStateException)
        failure.message.contains('completed')
        WebAsyncUtils.getAsyncManager(request).asyncWebRequest == null
    }

    void 'returned promises use the configured MVC timeout or the container default'() {
        given:
        def context = new StaticWebApplicationContext()
        if (configured != null) {
            context.environment.propertySources.addFirst(new MapPropertySource('test', ['spring.mvc.async.request-timeout': configured]))
        }
        request.servletContext.setAttribute(GrailsApplicationAttributes.APPLICATION_CONTEXT, context)
        webRequest = new GrailsWebRequest(request, response, request.servletContext)

        when:
        new AsyncActionResultTransformer().transformActionResult(webRequest, '/book/index', new CompletableFuturePromise<>())

        then:
        request.asyncContext.timeout == expected

        cleanup:
        context.close()

        where:
        configured | expected
        '2s'       | 2000L
        '1500'     | 1500L
        null       | 10000L
    }

    void 'timeout is parsed once per application context'() {
        given:
        def context = new StaticWebApplicationContext()
        def environment = Mock(MockEnvironment)
        context.environment = environment
        request.servletContext.setAttribute(GrailsApplicationAttributes.APPLICATION_CONTEXT, context)

        when:
        2.times {
            def nextRequest = new MockHttpServletRequest(request.servletContext)
            nextRequest.asyncSupported = true
            def next = new GrailsWebRequest(nextRequest, new MockHttpServletResponse(), request.servletContext)
            new AsyncActionResultTransformer().transformActionResult(next, '/book/index', new CompletableFuturePromise<>())
            assert nextRequest.asyncContext.timeout == 2000L
        }

        then:
        1 * environment.getProperty('spring.mvc.async.request-timeout') >> '2s'

        cleanup:
        context.close()
    }
}

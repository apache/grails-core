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

import org.grails.async.factory.future.CompletableFuturePromise
import org.grails.web.servlet.mvc.GrailsWebRequest
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
        promise.completeExceptionally(new IllegalStateException('bad'))

        then:
        asyncManager.hasConcurrentResult()
        asyncManager.concurrentResult instanceof IllegalStateException
        asyncManager.concurrentResult.message == 'bad'
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
}

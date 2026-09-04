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
package org.grails.plugins.web.async

import jakarta.servlet.AsyncContext
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import jakarta.servlet.http.HttpServletRequest

import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.mock.web.MockServletContext
import org.springframework.web.context.request.RequestContextHolder

import spock.lang.Specification

import org.grails.web.servlet.mvc.GrailsWebRequest

/**
 * What happens when a promise is handed a request the container will not start a new asynchronous
 * cycle on, which is where a callback attached to a promise that completed while it was being
 * attached arrives.
 */
class AsyncWebRequestPromiseDecoratorLookupStrategySpec extends Specification {

    AsyncWebRequestPromiseDecoratorLookupStrategy strategy = new AsyncWebRequestPromiseDecoratorLookupStrategy()

    void cleanup() {
        RequestContextHolder.resetRequestAttributes()
    }

    void 'a request the container will not start again fails visibly'() {
        given: 'a request that supports asynchronous processing and refuses to start another cycle'
        bind(requestRefusingToStart())

        when:
        strategy.findDecorators()

        then: 'the invalid lifecycle is reported instead of silently running undecorated'
        def exception = thrown(IllegalStateException)
        exception.message.contains('Async state [DISPATCHING]')
    }

    void 'a request that cannot process asynchronously at all still says so'() {
        given: 'what a caller gets for asking of a request that will never do this'
        def request = requestRefusingToStart().tap {
            asyncSupported = false
        }
        bind(request)

        when:
        strategy.findDecorators()

        then: 'a mistake rather than a race, and reported as one'
        thrown(IllegalStateException)
    }

    void 'a thread with no request bound to it is not decorated'() {
        expect:
        strategy.findDecorators().isEmpty()
    }

    private static MockHttpServletRequest requestRefusingToStart() {
        new MockHttpServletRequest() {

            @Override
            AsyncContext startAsync() {
                throw new IllegalStateException(
                        'Calling [asyncStart()] is not valid for a request with Async state [DISPATCHING]')
            }

            @Override
            AsyncContext startAsync(ServletRequest servletRequest, ServletResponse servletResponse) {
                startAsync()
            }
        }.tap {
            asyncSupported = true
        }
    }

    private static void bind(HttpServletRequest request) {
        RequestContextHolder.setRequestAttributes(
                new GrailsWebRequest(request, new MockHttpServletResponse(), new MockServletContext()))
    }

}

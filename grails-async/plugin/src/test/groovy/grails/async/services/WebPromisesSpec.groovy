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
package grails.async.services

import jakarta.servlet.AsyncContext
import jakarta.servlet.AsyncEvent

import spock.lang.Specification

import org.springframework.mock.web.MockAsyncContext
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.mock.web.MockServletContext
import org.springframework.web.context.request.RequestContextHolder

import grails.async.Promises
import grails.async.web.AsyncGrailsWebRequest
import grails.async.web.WebPromises
import grails.util.GrailsWebMockUtil
import org.grails.web.servlet.mvc.GrailsWebRequest

class WebPromisesSpec extends Specification {

    void cleanup() {
        RequestContextHolder.resetRequestAttributes()
    }

    void 'Test web promises handling'() {

        setup:
            GrailsWebMockUtil.bindMockWebRequest()

        when: 'A promise is created'
            def webPromise = WebPromises.task {
                RequestContextHolder.currentRequestAttributes()
            }
            webPromise.get() != null

        then: 'Async was requested'
            def e = thrown(IllegalStateException)
            e.message == 'The current request does not support Async processing'

        when: 'A normal promise is used'
            def promise = Promises.task { 'good' }

        then: 'No request is bound'
            promise.get() == 'good'

    }

    void 'a callback attached during async dispatch reuses the active request'() {
        given:
        def servletContext = new MockServletContext()
        def response = new MockHttpServletResponse()
        int starts = 0
        def request = new MockHttpServletRequest(servletContext) {
            @Override
            boolean isAsyncStarted() {
                false
            }

            @Override
            AsyncContext startAsync() {
                starts++
                throw new IllegalStateException('The request is already dispatching')
            }
        }.tap {
            asyncSupported = true
        }
        def asyncContext = new MockAsyncContext(request, response)
        def asyncWebRequest = new AsyncGrailsWebRequest(request, response, servletContext)
        asyncWebRequest.asyncContext = asyncContext
        RequestContextHolder.setRequestAttributes(new GrailsWebRequest(request, response, servletContext))

        when:
        def promise = WebPromises.task {
            RequestContextHolder.currentRequestAttributes()
        }

        then:
        promise.get() instanceof GrailsWebRequest
        starts == 0
    }

    void 'a callback attached after async completion still fails visibly'() {
        given:
        def servletContext = new MockServletContext()
        def request = new MockHttpServletRequest(servletContext)
        def response = new MockHttpServletResponse()
        def asyncContext = new MockAsyncContext(request, response)
        def asyncWebRequest = new AsyncGrailsWebRequest(request, response, servletContext).tap {
            it.asyncContext = asyncContext
        }
        asyncWebRequest.onComplete(new AsyncEvent(asyncContext))
        RequestContextHolder.setRequestAttributes(new GrailsWebRequest(request, response, servletContext))

        when:
        WebPromises.task { 'too late' }

        then:
        def exception = thrown(IllegalStateException)
        exception.message == 'Cannot start a task once asynchronous request processing has completed'
    }
}

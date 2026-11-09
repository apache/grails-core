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

import jakarta.servlet.AsyncEvent

import grails.async.web.AsyncGrailsWebRequest

import org.springframework.mock.web.MockAsyncContext
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.mock.web.MockServletContext
import org.springframework.web.context.request.RequestContextHolder

import spock.lang.Specification

import org.grails.web.servlet.mvc.GrailsWebRequest

/**
 * When a decorated task is allowed to run, which depends on whether the decorator started the
 * asynchronous cycle itself or joined one already in flight.
 */
class AsyncWebRequestPromiseDecoratorSpec extends Specification {

    MockServletContext servletContext = new MockServletContext()
    MockHttpServletResponse response = new MockHttpServletResponse()

    void cleanup() {
        RequestContextHolder.resetRequestAttributes()
    }

    void 'a task runs while the cycle its decorator started is running'() {
        given:
        def request = new MockHttpServletRequest(servletContext).tap {
            asyncSupported = true
        }
        def decorator = new AsyncWebRequestPromiseDecorator(
                new GrailsWebRequest(request, response, servletContext)
        )

        expect:
        decorator.decorate { it -> 'ran' }.call('in') == 'ran'
    }

    void 'a task refuses to run once the cycle its decorator started is no longer running'() {
        given:
        def request = new MockHttpServletRequest(servletContext).tap {
            asyncSupported = true
        }
        def decorator = new AsyncWebRequestPromiseDecorator(
                new GrailsWebRequest(request, response, servletContext)
        )

        when: 'the cycle is dispatched, so the request reports it as no longer started'
        request.asyncStarted = false
        decorator.decorate { it -> 'ran' }.call('in')

        then: 'completion has not been observed yet, but a cycle this decorator started must still be running'
        def exception = thrown(IllegalStateException)
        exception.message == 'Asynchronous request already terminated. Likely timeout reached'
    }

    void 'a task joining a cycle in flight runs while the container delivers its result'() {
        given: 'an asynchronous request whose cycle is dispatching: live, but no longer started'
        def request = new MockHttpServletRequest(servletContext).tap {
            asyncStarted = true
        }
        def asyncWebRequest = new AsyncGrailsWebRequest(request, response, servletContext).tap {
            asyncContext = new MockAsyncContext(request, response)
        }
        def decorator = new AsyncWebRequestPromiseDecorator(
                new GrailsWebRequest(request, response, servletContext)
        )

        expect:
        decorator.decorate { it -> 'ran' }.call('in') == 'ran'
    }

    void 'a task joining a cycle refuses to run once that cycle completes'() {
        given:
        def request = new MockHttpServletRequest(servletContext).tap {
            asyncSupported = true
        }
        def asyncContext = new MockAsyncContext(request, response)
        def asyncWebRequest = new AsyncGrailsWebRequest(request, response, servletContext).tap {
            it.asyncContext = asyncContext
        }
        def decorator = new AsyncWebRequestPromiseDecorator(
                new GrailsWebRequest(request, response, servletContext)
        )

        when: 'the cycle completes after the task was attached but before it runs'
        asyncWebRequest.onComplete(new AsyncEvent(asyncContext))
        decorator.decorate { it -> 'ran' }.call('in')

        then:
        def exception = thrown(IllegalStateException)
        exception.message == 'Asynchronous request already terminated. Likely timeout reached'
    }

}

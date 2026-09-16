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

import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.web.context.request.RequestContextHolder

import spock.lang.Specification

import org.grails.web.servlet.mvc.GrailsWebRequest
import org.grails.web.util.GrailsApplicationAttributes

class GrailsAsyncContextSpec extends Specification {

    void cleanup() {
        RequestContextHolder.resetRequestAttributes()
    }

    void 'worker cleanup does not access a request recycled during completion'() {
        given:
        def request = new RecyclableRequest()
        def response = new MockHttpServletResponse()
        AsyncContext delegate = Stub() {
            getRequest() >> request
            getResponse() >> response
            start(_ as Runnable) >> { Runnable worker -> worker.run() }
        }
        AsyncContext context = new GrailsAsyncContext(delegate,
                new GrailsWebRequest(request, response, request.servletContext))

        when:
        context.start {
            assert GrailsWebRequest.lookup() != null
            request.recycled = true
        }

        then:
        noExceptionThrown()
        RequestContextHolder.requestAttributes == null
    }

    void 'worker cleanup preserves the web request installed by an async dispatch'() {
        given:
        def request = new MockHttpServletRequest()
        def response = new MockHttpServletResponse()
        def dispatchedRequest = new GrailsWebRequest(request, response, request.servletContext)
        AsyncContext delegate = Stub() {
            getRequest() >> request
            getResponse() >> response
            start(_ as Runnable) >> { Runnable worker -> worker.run() }
        }
        AsyncContext context = new GrailsAsyncContext(delegate,
                new GrailsWebRequest(request, response, request.servletContext))

        when:
        context.start {
            request.setAttribute(GrailsApplicationAttributes.WEB_REQUEST, dispatchedRequest)
        }

        then:
        request.getAttribute(GrailsApplicationAttributes.WEB_REQUEST).is(dispatchedRequest)
        RequestContextHolder.requestAttributes == null
    }

    private static class RecyclableRequest extends MockHttpServletRequest {
        boolean recycled

        @Override
        void removeAttribute(String name) {
            if (recycled) {
                throw new IllegalStateException('The request object has been recycled')
            }
            super.removeAttribute(name)
        }
    }
}

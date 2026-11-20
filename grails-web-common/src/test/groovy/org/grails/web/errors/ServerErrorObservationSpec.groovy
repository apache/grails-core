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
package org.grails.web.errors

import io.micrometer.observation.Observation
import io.micrometer.observation.ObservationHandler
import io.micrometer.observation.ObservationRegistry
import jakarta.servlet.AsyncEvent
import jakarta.servlet.FilterChain
import jakarta.servlet.RequestDispatcher
import spock.lang.Specification

import org.springframework.http.server.observation.ServerRequestObservationContext
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.web.filter.ServerHttpObservationFilter

class ServerErrorObservationSpec extends Specification {

    private static final IllegalStateException FIRST = new IllegalStateException('first')
    private static final IllegalStateException LATER = new IllegalStateException('later')

    private final MockHttpServletRequest request = new MockHttpServletRequest('GET', '/failing')
    private final MockHttpServletResponse response = new MockHttpServletResponse()
    private final List<Throwable> notified = []
    private Throwable stoppedWith

    void 'a server error #answered is reported once, when the request completes'() {
        when:
        observe {
            if (left) {
                request.setAttribute(RequestDispatcher.ERROR_EXCEPTION, FIRST)
            }
            ServerErrorObservation.recordServerError(request, FIRST)
        }

        then:
        stoppedWith.is(FIRST)
        notified == [FIRST]

        where:
        answered                                                    | left
        'that an error view answered, which leaves nothing behind'  | false
        'that an error controller answered, which leaves it behind' | true
    }

    void 'a server error recorded after the first does not replace it'() {
        when:
        observe {
            ServerErrorObservation.recordServerError(request, FIRST)
            ServerErrorObservation.recordServerError(request, LATER)
        }

        then:
        stoppedWith.is(FIRST)
        notified == [FIRST]
    }

    void 'without an observation, a server error is only noted on the request'() {
        when:
        ServerErrorObservation.recordServerError(request, FIRST)

        then:
        noExceptionThrown()
        request.getAttribute(RequestDispatcher.ERROR_EXCEPTION) == null
    }

    void 'the first server error of a request that started asynchronous processing is reported once it completes, over #later'() {
        given:
        request.asyncSupported = true

        when: 'the dispatch that started the processing fails, and something else fails before it completes'
        observe {
            request.startAsync()
            ServerErrorObservation.recordServerError(request, FIRST)
        }
        overwrite.call(request, response)
        request.asyncContext.complete()

        then:
        stoppedWith.is(FIRST)
        notified == [FIRST]

        where:
        later                                     | overwrite
        'a failure Spring MVC records as handled' | { req, res -> ServerHttpObservationFilter.recordHandledException(req, LATER) }
        'a failure left on the request'           | { req, res -> req.setAttribute(RequestDispatcher.ERROR_EXCEPTION, LATER) }
    }

    void 'the server error left on a request that started asynchronous processing is reported once, when the container reports it'() {
        given:
        request.asyncSupported = true

        when: 'the error left on the request is reported by the container, as Tomcat does, before the processing completes'
        observe {
            request.startAsync()
            request.setAttribute(RequestDispatcher.ERROR_EXCEPTION, FIRST)
            ServerErrorObservation.recordServerError(request, FIRST)
        }
        request.asyncContext.listeners.each { it.onError(new AsyncEvent(request.asyncContext, request, response, FIRST)) }
        request.asyncContext.complete()

        then: 'the observation heard of it from the container, and does not read it off the request again'
        stoppedWith.is(FIRST)
        notified == [FIRST]
        request.getAttribute(RequestDispatcher.ERROR_EXCEPTION) == null
    }

    private void observe(Closure dispatch) {
        def registry = ObservationRegistry.create()
        registry.observationConfig().observationHandler(new ObservationHandler<Observation.Context>() {
            @Override
            boolean supportsContext(Observation.Context context) {
                context instanceof ServerRequestObservationContext
            }

            @Override
            void onError(Observation.Context context) {
                notified << context.error
            }

            @Override
            void onStop(Observation.Context context) {
                stoppedWith = context.error
            }
        })
        new ServerHttpObservationFilter(registry).doFilter(request, response, { req, res -> dispatch.call() } as FilterChain)
    }
}

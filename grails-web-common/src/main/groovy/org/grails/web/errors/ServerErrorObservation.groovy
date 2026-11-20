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

import java.util.function.Supplier

import groovy.transform.CompileStatic

import jakarta.servlet.AsyncEvent
import jakarta.servlet.AsyncListener
import jakarta.servlet.RequestDispatcher
import jakarta.servlet.http.HttpServletRequest

import org.springframework.web.filter.ServerHttpObservationFilter

/**
 * Tells the observation of a request about the server error an error handler answered, which hides it from the
 * observation: its metrics would otherwise say there was no error, and its trace would not be marked as failed.
 * {@link ServerHttpObservationFilter} reports the failure to the observation's handlers, one of which marks the span,
 * once the exchange completes, whether the request was dispatched asynchronously, forwarded or answered by an error
 * controller.
 *
 * @since 9.0
 */
@CompileStatic
final class ServerErrorObservation {

    /** The failure recorded on the observation of the request, which a later one does not replace. */
    private static final String RECORDED_ATTRIBUTE = ServerErrorObservation.name + '.RECORDED'

    private ServerErrorObservation() {
    }

    /**
     * Records the server error an error handler answered, unless one was recorded earlier in the exchange.
     *
     * @param request the request that failed
     * @param failure what failed, as the observation reports it
     * @see #recordServerError(HttpServletRequest, Throwable, Supplier)
     */
    static void recordServerError(HttpServletRequest request, Throwable failure) {
        recordServerError(request, failure, { null } as Supplier<Object>)
    }

    /**
     * Answers a server error with an error handler, and records it once the handler has answered, unless one was
     * recorded earlier in the exchange: the request failed with that one, and what fails after it follows from it.
     * That covers what fails inside the error handler, which its own resolution answers within the handler, and what
     * fails once it has answered, such as asynchronous processing the action started before it failed timing out, or an
     * error handler finding the response already used. A failure recorded while the handler answers, as Spring MVC
     * records one it resolves to a server error itself, gives way to this one. A handler that fails without an answer
     * leaves its own failure to reach the observation.
     * <p>
     * The failure an error controller answered is left on the request, where the filter that observes the request reads
     * it once the exchange completes. A container also finds it there once a dispatch that started asynchronous
     * processing has returned, and Tomcat then reports it to the listeners of that processing, the observation's among
     * them, so it is not recorded a second time, and what was recorded after it is taken back once the processing
     * completes.
     *
     * @param request the request that failed
     * @param failure what failed, as the observation reports it
     * @param errorHandler answers the failure
     * @return what the error handler answered
     */
    static <T> T recordServerError(HttpServletRequest request, Throwable failure, Supplier<T> errorHandler) {
        if (request.getAttribute(RECORDED_ATTRIBUTE) != null) {
            return errorHandler.get()
        }
        request.setAttribute(RECORDED_ATTRIBUTE, failure)
        T answer
        try {
            answer = errorHandler.get()
        }
        catch (Throwable unanswered) {
            request.removeAttribute(RECORDED_ATTRIBUTE)
            throw unanswered
        }
        if (!request.asyncStarted || !request.getAttribute(RequestDispatcher.ERROR_EXCEPTION).is(failure)) {
            ServerHttpObservationFilter.recordHandledException(request, failure)
        }
        if (request.asyncStarted) {
            request.asyncContext.addListener(new FirstFailureListener(request, failure))
        }
        return answer
    }

    /**
     * Leaves the observation of a request that started asynchronous processing with the failure recorded first once the
     * processing completes, which is when the observation reads what failed. A listener of the observation reported it
     * already when the container did, so it is taken off the request, together with whatever was recorded after it;
     * otherwise it is recorded again, over what failed after it. This listener is added before the observation's, which the filter adds once the dispatch has returned,
     * so it is told first.
     */
    private static final class FirstFailureListener implements AsyncListener {

        private final HttpServletRequest request
        private final Throwable failure
        private boolean reportedByContainer

        private FirstFailureListener(HttpServletRequest request, Throwable failure) {
            this.request = request
            this.failure = failure
        }

        @Override
        void onStartAsync(AsyncEvent event) {
            event.asyncContext.addListener(this)
        }

        @Override
        void onError(AsyncEvent event) {
            if (event.throwable.is(failure)) {
                reportedByContainer = true
            }
        }

        @Override
        void onTimeout(AsyncEvent event) {
        }

        @Override
        void onComplete(AsyncEvent event) {
            if (reportedByContainer) {
                request.removeAttribute(RequestDispatcher.ERROR_EXCEPTION)
                // Setting a request attribute to null removes it, so this takes back what was recorded after it
                ServerHttpObservationFilter.recordHandledException(request, null)
            }
            else {
                ServerHttpObservationFilter.recordHandledException(request, failure)
            }
        }
    }
}

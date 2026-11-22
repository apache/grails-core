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
package org.apache.grails.undertow.core.servlet;

import jakarta.servlet.DispatcherType;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.servlet.handlers.ServletRequestContext;
import io.undertow.servlet.spec.AsyncContextImpl;
import io.undertow.servlet.spec.HttpServletRequestImpl;

/**
 * Completes the asynchronous processing a request or asynchronous dispatch started once the error that dispatch sent
 * has been answered, as Jetty does. Undertow sends the error, through the error page that maps to it, once the
 * dispatch has returned, and ends the exchange with it. The asynchronous processing is then never completed and never
 * times out, so its listeners are never told it completed: {@code ServerHttpObservationFilter} never stops its
 * observation of the request, and a {@code DeferredResult} is never resolved. Tomcat leaves such processing to time
 * out, and so does Undertow when no error page maps to the error.
 * <p>
 * A dispatch that completes the processing itself after sending the error would otherwise have its listeners told
 * before the error page runs, since Undertow completes it once the dispatch has returned and only then sends the
 * error. The error is sent here first in that case, so the listeners are told once it has been answered.
 */
final class AsyncErrorCompletionHandler implements HttpHandler {

    private final HttpHandler next;

    AsyncErrorCompletionHandler(HttpHandler next) {
        this.next = next;
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        ServletRequestContext context = exchange.getAttachment(ServletRequestContext.ATTACHMENT_KEY);
        DispatcherType dispatcherType = context == null ? null : context.getDispatcherType();
        // Undertow sends the error a dispatch sent only once a request or an asynchronous dispatch has returned
        if (dispatcherType != DispatcherType.REQUEST && dispatcherType != DispatcherType.ASYNC) {
            this.next.handleRequest(exchange);
            return;
        }
        this.next.handleRequest(exchange);
        HttpServletRequestImpl request = context.getOriginalRequest();
        AsyncContextImpl asyncContext = request.getAsyncContextInternal();
        // An asynchronous dispatch that did not start the processing again finds the processing it was dispatched by
        if (context.getErrorCode() <= 0 || asyncContext == null || !request.isAsyncStarted()) {
            return;
        }
        if (asyncContext.isCompletedBeforeInitialRequestDone()) {
            // Undertow tells the listeners as soon as this returns, and sends the error after them. Send it now, and
            // clear it so that Undertow does not send it again
            context.getOriginalResponse().doErrorDispatch(context.getErrorCode(), context.getErrorMessage());
            context.setError(-1, null);
        }
        else {
            exchange.addExchangeCompleteListener((completed, nextListener) -> {
                try {
                    asyncContext.complete();
                    if (asyncContext.isCompletedBeforeInitialRequestDone()) {
                        // On the thread of the request dispatch, completing only marks the processing as completed,
                        // and Undertow tells the listeners before the error is sent, which has already happened
                        asyncContext.handleCompletedBeforeInitialRequestDone();
                    }
                }
                finally {
                    nextListener.proceed();
                }
            });
        }
    }

}

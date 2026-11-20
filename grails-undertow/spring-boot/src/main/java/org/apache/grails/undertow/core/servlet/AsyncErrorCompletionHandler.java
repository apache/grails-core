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

/**
 * Completes the asynchronous processing a request dispatch started once the error that dispatch sent has been
 * answered, as Tomcat and Jetty do. Undertow sends the error, through the error page that maps to it, once the dispatch
 * has returned, and ends the exchange with it. The asynchronous processing is then never completed and never times
 * out, so its listeners are never told it completed: {@code ServerHttpObservationFilter} never stops its observation
 * of the request, and a {@code DeferredResult} is never resolved.
 */
final class AsyncErrorCompletionHandler implements HttpHandler {

    private final HttpHandler next;

    AsyncErrorCompletionHandler(HttpHandler next) {
        this.next = next;
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        ServletRequestContext context = exchange.getAttachment(ServletRequestContext.ATTACHMENT_KEY);
        if (context == null || context.getDispatcherType() != DispatcherType.REQUEST) {
            this.next.handleRequest(exchange);
            return;
        }
        this.next.handleRequest(exchange);
        AsyncContextImpl asyncContext = context.getOriginalRequest().getAsyncContextInternal();
        if (context.getErrorCode() > 0 && asyncContext != null && !asyncContext.isCompletedBeforeInitialRequestDone()) {
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

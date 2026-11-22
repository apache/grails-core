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
import jakarta.servlet.ServletContext;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.servlet.api.DeploymentInfo;
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
 * When the dispatch, or a handler around it, completes the processing after an error was sent, Undertow tells its
 * listeners once the dispatch has returned and only then sends the error, so they would be told before the error page
 * runs. The error is therefore sent here once every handler that can send it, forward or complete the processing has
 * returned, and the processing completed after it, so its listeners are told once the error has been answered.
 * <p>
 * {@link #addTo(DeploymentInfo, ServletContext)}, a servlet extension the factory adds after its customizers, puts the
 * handler around every handler they and other servlet extensions add. Undertow runs outer handlers on a request
 * dispatch only, so a request dispatch is handled by the outermost outer handler, and an asynchronous dispatch by the
 * outermost inner one. Only Undertow's own handlers remain around them, and none of those sends an error, forwards or
 * completes the processing once the handlers it wraps have returned.
 */
final class AsyncErrorCompletionHandler implements HttpHandler {

    private final HttpHandler next;

    private final DispatcherType dispatcherType;

    private AsyncErrorCompletionHandler(HttpHandler next, DispatcherType dispatcherType) {
        this.next = next;
        this.dispatcherType = dispatcherType;
    }

    /**
     * Adds the handlers around the handlers already added to a deployment.
     *
     * @param deployment the deployment, once its customizers and the servlet extensions before this one have run
     * @param servletContext the servlet context of the deployment
     */
    static void addTo(DeploymentInfo deployment, ServletContext servletContext) {
        deployment.addOuterHandlerChainWrapper((next) -> new AsyncErrorCompletionHandler(next, DispatcherType.REQUEST));
        deployment.addInnerHandlerChainWrapper((next) -> new AsyncErrorCompletionHandler(next, DispatcherType.ASYNC));
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        ServletRequestContext context = exchange.getAttachment(ServletRequestContext.ATTACHMENT_KEY);
        // Undertow sends the error a dispatch sent only once a request or an asynchronous dispatch has returned
        if (context == null || context.getDispatcherType() != this.dispatcherType) {
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
        // Sent now, and cleared so that Undertow does not send it again
        context.getOriginalResponse().doErrorDispatch(context.getErrorCode(), context.getErrorMessage());
        context.setError(-1, null);
        if (!asyncContext.isCompletedBeforeInitialRequestDone()) {
            // On the thread of the dispatch, completing only marks the processing as completed, and Undertow tells
            // the listeners once the dispatch has returned. A handler outside this one completing it later is ignored
            asyncContext.complete();
        }
    }

}

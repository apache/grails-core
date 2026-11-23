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
import io.undertow.util.AttachmentKey;

/**
 * Completes the asynchronous processing a request or asynchronous dispatch started once an error page has answered the
 * error that dispatch sent, as Jetty does. Undertow sends the error, through the error page that maps to it, once the
 * dispatch has returned, and ends the exchange with it. Its error dispatch completes the processing, but too late for
 * Undertow to tell the listeners, and the processing then never times out either: {@code ServerHttpObservationFilter}
 * never stops its observation of the request, and a {@code DeferredResult} is never resolved. Processing an error no
 * error page maps to is left to the asynchronous timeout, by Undertow and by this handler, as Tomcat does.
 * <p>
 * When the dispatch, or a handler around it, completes the processing after the error was sent, Undertow tells its
 * listeners once the dispatch has returned and only then sends the error, so they would be told before the error page
 * runs. In both cases the error is therefore sent here, once the handlers the factory's customizers and servlet
 * extensions add have returned, and set aside so that Undertow does not send it again. Undertow tells the listeners
 * once the dispatch has returned, after the error has been answered.
 * <p>
 * {@link #addTo(DeploymentInfo, ServletContext)}, a servlet extension the factory adds after its customizers, adds the
 * handler around the handlers they and the servlet extensions before it add. Undertow runs outer handlers on a request
 * dispatch only, so a request dispatch is handled by an outer handler, and an asynchronous dispatch by an inner one.
 * <p>
 * A dispatch that dispatches the processing after sending the error leaves it to Undertow to send, once, and the
 * completion to the dispatch it asked for. Undertow's error dispatch completes the processing once the error page has
 * run, though, so with an error page its listeners are told before that dispatch runs.
 * <p>
 * A change to Undertow that sends a dispatch's error before it tells the listeners, and so also completes the
 * processing an error page answered wherever the handler that completes it or sends the error sits, is proposed in
 * <a href="https://github.com/undertow-io/undertow/pull/2018">undertow-io/undertow#2018</a>. Once Grails uses an
 * Undertow release with it, this handler is needed only to keep the error of a dispatch that dispatched the processing
 * from being sent again by the dispatch it asked for.
 */
final class AsyncErrorCompletionHandler implements HttpHandler {

    /** Marks an exchange whose error Undertow sends once, which a later dispatch of the request does not send again. */
    private static final AttachmentKey<Boolean> ERROR_SENT = AttachmentKey.create(Boolean.class);

    private final HttpHandler next;

    private final DispatcherType dispatcherType;

    private AsyncErrorCompletionHandler(HttpHandler next, DispatcherType dispatcherType) {
        this.next = next;
        this.dispatcherType = dispatcherType;
    }

    /**
     * Adds the handlers to a deployment, around the handlers the factory's customizers and the servlet extensions
     * before this one have added.
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
        int errorCode = context.getErrorCode();
        if (errorCode <= 0) {
            return;
        }
        if (exchange.getAttachment(ERROR_SENT) != null) {
            // An earlier dispatch's error was sent, and Undertow would send it again once this one has returned
            context.setError(-1, null);
            return;
        }
        HttpServletRequestImpl request = context.getOriginalRequest();
        AsyncContextImpl asyncContext = request.getAsyncContextInternal();
        // An asynchronous dispatch that did not start the processing again finds the processing it was dispatched by
        if (asyncContext == null || !request.isAsyncStarted()) {
            return;
        }
        if (asyncContext.isDispatched()) {
            // Undertow sends it once this dispatch has returned, and the dispatch the request asked for completes it
            exchange.putAttachment(ERROR_SENT, Boolean.TRUE);
            return;
        }
        // The error dispatch completes the processing once the error page has run. Processing neither the dispatch nor
        // an error page completes is left to Undertow, which sends the error and leaves it to the asynchronous timeout
        boolean answeredByAnErrorPage = context.getDeployment().getErrorPages().getErrorLocation(errorCode) != null;
        if (!asyncContext.isCompletedBeforeInitialRequestDone() && !answeredByAnErrorPage) {
            return;
        }
        String errorMessage = context.getErrorMessage();
        context.setError(-1, null);
        context.getOriginalResponse().doErrorDispatch(errorCode, errorMessage);
    }

}

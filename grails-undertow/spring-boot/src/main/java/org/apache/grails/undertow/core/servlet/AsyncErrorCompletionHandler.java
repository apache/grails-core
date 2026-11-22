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

import java.util.Collections;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.ServletContainerInitializer;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;

import io.undertow.server.HandlerWrapper;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.ServletContainerInitializerInfo;
import io.undertow.servlet.api.ThreadSetupHandler;
import io.undertow.servlet.handlers.ServletRequestContext;
import io.undertow.servlet.spec.AsyncContextImpl;
import io.undertow.servlet.spec.HttpServletRequestImpl;
import io.undertow.servlet.util.ImmediateInstanceFactory;
import io.undertow.util.AttachmentKey;

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
 * returned, and the processing completed after it, so its listeners are told once the error has been answered. An
 * asynchronous dispatch that started the processing again has it completed, when no error page did, once Undertow has
 * told the listeners of the processing before it that it started again: completing it completes that processing as
 * well. Undertow's error dispatch completes it as soon as the error page has run, which tells those listeners first.
 * <p>
 * {@link #addTo(DeploymentInfo, ServletContext)}, a servlet extension the factory adds after its customizers, puts the
 * handler around every handler the deployment has once it has started: those its customizers, its servlet extensions,
 * its servlet container initializers, such as Spring Boot's, and its context listeners add. Undertow runs outer
 * handlers on a request dispatch only, so a request dispatch is handled by the outermost outer handler, and an
 * asynchronous dispatch by the outermost inner one. Only Undertow's own handlers remain around them, and none of those
 * sends an error, forwards or completes the processing once the handlers it wraps have returned. An error recorded
 * outside them, by a request listener before the request reaches them for example, is sent by Undertow, and the
 * processing it leaves open is completed once the dispatch has returned. So is the processing an error page answered
 * once the dispatch had returned.
 * <p>
 * A dispatch that dispatches the processing after sending the error leaves it to Undertow to send, once, and the
 * completion to the dispatch it asked for. Undertow's error dispatch completes the processing once the error page has
 * run, though, so with an error page its listeners are told before that dispatch runs.
 * <p>
 * A change to Undertow that sends a dispatch's error before it tells the listeners, and so also completes the
 * processing an error page answered, is proposed in
 * <a href="https://github.com/undertow-io/undertow/pull/2018">undertow-io/undertow#2018</a>. Once Grails uses an
 * Undertow release with it, this handler is needed only for what that change leaves open, such as processing that no
 * error page answered.
 */
final class AsyncErrorCompletionHandler implements HttpHandler {

    /** The error sent once, which a later dispatch of the request does not send again. */
    private static final AttachmentKey<SentError> SENT_ERROR = AttachmentKey.create(SentError.class);

    private final HttpHandler next;

    private final DispatcherType dispatcherType;

    private AsyncErrorCompletionHandler(HttpHandler next, DispatcherType dispatcherType) {
        this.next = next;
        this.dispatcherType = dispatcherType;
    }

    /**
     * Adds the handlers to a deployment once every other handler has been added to it. A servlet container initializer
     * this adds as the last servlet extension runs after every other one, and a listener that initializer adds is told
     * the context was initialized after every other listener, which Undertow does just before it builds the handler
     * chains. The outer handler is added as Undertow makes its last wrap before it wraps the outer handlers, so an
     * outer handler another handler adds as it is wrapped comes before it. Undertow only lets a listener add a context
     * listener through its internal API, and a listener added that way can always come after this one.
     *
     * @param deployment the deployment, once its customizers and the servlet extensions before this one have run
     * @param servletContext the servlet context of the deployment
     */
    static void addTo(DeploymentInfo deployment, ServletContext servletContext) {
        deployment.addThreadSetupAction(new DispatchBoundary());
        ServletContextListener addsHandlers = new ServletContextListener() {

            @Override
            public void contextInitialized(ServletContextEvent event) {
                deployment.addInnerHandlerChainWrapper((next) -> {
                    addOuterHandlerOnceTheLastWrapBeforeIt(deployment);
                    return new AsyncErrorCompletionHandler(next, DispatcherType.ASYNC);
                });
            }

        };
        ServletContainerInitializer initializer = (classes, context) -> context.addListener(addsHandlers);
        deployment.addServletContainerInitializer(new ServletContainerInitializerInfo(
            ServletContainerInitializer.class, new ImmediateInstanceFactory<>(initializer), Collections.emptySet()));
    }

    /**
     * Adds the outer handler as Undertow makes the last wrap before it wraps the outer handlers, which is the only time
     * left when a handler can add one: Undertow iterates each list as it wraps it. Undertow wraps the inner handlers,
     * this one's last, then, unless the deployment's security is disabled, the security handlers and the initial
     * security handler, and only then the outer handlers.
     */
    private static void addOuterHandlerOnceTheLastWrapBeforeIt(DeploymentInfo deployment) {
        if (deployment.isSecurityDisabled()) {
            addOuterHandler(deployment);
            return;
        }
        deployment.addSecurityWrapper((secured) -> {
            HandlerWrapper initial = deployment.getInitialSecurityWrapper();
            if (initial == null) {
                addOuterHandler(deployment);
            }
            else {
                deployment.setInitialSecurityWrapper((handler) -> {
                    HttpHandler wrapped = initial.wrap(handler);
                    addOuterHandler(deployment);
                    return wrapped;
                });
            }
            return secured;
        });
    }

    private static void addOuterHandler(DeploymentInfo deployment) {
        deployment.addOuterHandlerChainWrapper((next) -> new AsyncErrorCompletionHandler(next, DispatcherType.REQUEST));
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
        HttpServletRequestImpl request = context.getOriginalRequest();
        AsyncContextImpl asyncContext = request.getAsyncContextInternal();
        // An asynchronous dispatch that did not start the processing again finds the processing it was dispatched by
        boolean started = asyncContext != null && request.isAsyncStarted();
        SentError earlier = exchange.getAttachment(SENT_ERROR);
        if (earlier != null) {
            // An earlier dispatch's error was sent. It stays recorded, so that the response ignores it being sent
            // again, and is set aside until the dispatch has returned, so that Undertow does not send it again
            earlier.setAside(context);
            if (started && !asyncContext.isDispatched() && !asyncContext.isCompletedBeforeInitialRequestDone()) {
                asyncContext.addAsyncTask(asyncContext::complete);
            }
            return;
        }
        if (!started) {
            return;
        }
        SentError sent = new SentError(errorCode, context.getErrorMessage());
        exchange.putAttachment(SENT_ERROR, sent);
        if (asyncContext.isDispatched()) {
            // Undertow sends it once this dispatch has returned, and the dispatch the request asked for completes it
            return;
        }
        sent.setAside(context);
        context.getOriginalResponse().doErrorDispatch(errorCode, sent.message());
        if (asyncContext.isCompletedBeforeInitialRequestDone()) {
            // The dispatch, or Undertow's error dispatch once the error page has run, completed it, and Undertow tells
            // the listeners once the dispatch has returned
            return;
        }
        if (this.dispatcherType == DispatcherType.ASYNC) {
            // Completing it completes the processing before it as well, and Undertow tells that one's listeners that
            // the processing started again only once this dispatch has returned. Its tasks run after that
            asyncContext.addAsyncTask(asyncContext::complete);
        }
        else {
            // On the thread of the dispatch, completing only marks the processing as completed, and Undertow tells
            // the listeners once the dispatch has returned. A handler outside this one completing it later is ignored
            asyncContext.complete();
        }
    }

    /**
     * Restores the error set aside once a request or asynchronous dispatch has returned, and completes the processing
     * an error sent outside the handlers left open: one a request listener sent, for example, or one sent before the
     * request reached them.
     */
    private static final class DispatchBoundary implements ThreadSetupHandler {

        @Override
        public <T, C> Action<T, C> create(Action<T, C> action) {
            return (exchange, context) -> {
                try {
                    return action.call(exchange, context);
                }
                finally {
                    // Undertow runs a request or asynchronous dispatch with its request context
                    if (exchange != null && context instanceof ServletRequestContext dispatched) {
                        afterDispatch(exchange, dispatched);
                    }
                }
            };
        }

        private static void afterDispatch(HttpServerExchange exchange, ServletRequestContext context) {
            SentError sent = exchange.getAttachment(SENT_ERROR);
            boolean sentByUndertow = false;
            if (sent != null) {
                sent.restore(context);
            }
            else if (context.getErrorCode() > 0 && exchange.isResponseComplete()) {
                // Undertow sent an error recorded outside the handlers, which a later dispatch does not send again
                exchange.putAttachment(SENT_ERROR, new SentError(context.getErrorCode(), context.getErrorMessage()));
                sentByUndertow = true;
            }
            HttpServletRequestImpl request = context.getOriginalRequest();
            AsyncContextImpl asyncContext = request.getAsyncContextInternal();
            if (asyncContext == null || !request.isAsyncStarted() || asyncContext.isDispatched()) {
                return;
            }
            if (asyncContext.isCompletedBeforeInitialRequestDone()) {
                // Undertow's error dispatch completed it after Undertow looked for that
                asyncContext.handleCompletedBeforeInitialRequestDone();
            }
            else if (sentByUndertow) {
                asyncContext.addAsyncTask(asyncContext::complete);
            }
        }

    }

    /**
     * The error sent once, set aside while Undertow would send it again.
     *
     * @param code the status code of the error
     * @param message the message of the error
     */
    private record SentError(int code, String message) {

        void setAside(ServletRequestContext context) {
            context.setError(-1, null);
        }

        void restore(ServletRequestContext context) {
            context.setError(this.code, this.message);
        }

    }

}

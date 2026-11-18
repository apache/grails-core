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
package org.grails.plugins.web.async.mvc

import java.time.Duration
import java.util.function.Supplier

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.reactivestreams.Publisher

import org.springframework.core.ReactiveAdapter
import org.springframework.core.ReactiveAdapterRegistry
import org.springframework.web.context.request.async.DeferredResult
import org.springframework.web.context.request.async.WebAsyncManager
import org.springframework.web.context.request.async.WebAsyncUtils

import grails.async.web.StreamItemRenderer
import org.grails.plugins.web.async.AsyncRequestSupport
import org.grails.web.servlet.mvc.ActionResultTransformer
import org.grails.web.servlet.mvc.GrailsWebRequest
import org.grails.web.util.GrailsApplicationAttributes

/**
 * Streams a {@link Publisher} returned from a controller action to the client as server-sent events.
 *
 * <p>The request is handled asynchronously through Spring MVC, as a returned {@code Promise} is, so the
 * container thread is released while the stream produces. The response is committed as
 * {@code text/event-stream} before the first item, and each item is written as soon as it is emitted,
 * formatted by a {@link ServerSentEventFormatter}. A publisher that emits at most one item, such as a
 * Reactor {@code Mono}, is not streamed: as Spring MVC does, Grails resolves it and treats its value as
 * the action's result, the way a returned {@code Promise}'s value is; which publishers are single-valued
 * is what Spring's {@link ReactiveAdapterRegistry} says. A stream has no timeout, as Spring MVC's
 * server-sent event streams have none, unless {@code grails.async.streaming.timeout} sets one; a timeout
 * ends the stream with a {@code complete} event, since the response is already committed.</p>
 *
 * @since 8.1
 */
@CompileStatic
@Slf4j
class PublisherActionResultTransformer implements ActionResultTransformer {

    public static final String CONTENT_TYPE = 'text/event-stream'

    /**
     * The asynchronous request timeout that means none, which a stream gets unless one is configured.
     */
    public static final long NO_TIMEOUT = -1L

    private final ServerSentEventFormatter formatter
    private final Long timeoutMillis

    /**
     * @param renderers the renderers that turn items into event text, in the order they are consulted
     * @param timeout how long a stream may stay open, or {@code null} for no timeout
     */
    PublisherActionResultTransformer(List<StreamItemRenderer> renderers, Duration timeout) {
        this.formatter = new ServerSentEventFormatter(renderers)
        this.timeoutMillis = timeout?.toMillis()
    }

    @Override
    Object transformActionResult(GrailsWebRequest webRequest, String viewName, Object actionResult) {
        if (!(actionResult instanceof Publisher)) {
            return actionResult
        }
        Publisher<Object> publisher = (Publisher<Object>) actionResult
        HttpServletRequest request = webRequest.currentRequest
        WebAsyncManager asyncManager = WebAsyncUtils.getAsyncManager(request)
        if (AsyncRequestSupport.isComplete(webRequest) || asyncManager.asyncWebRequest?.isAsyncComplete()) {
            throw new IllegalStateException('Cannot process a Publisher once asynchronous request processing has completed')
        }
        boolean singleValue = isSingleValue(publisher)
        if (!singleValue && webRequest.currentResponse.committed) {
            throw new IllegalStateException('Cannot stream a Publisher once the response has been committed')
        }
        if (!asyncManager.isConcurrentHandlingStarted()) {
            asyncManager.asyncWebRequest = AsyncRequestSupport.create(webRequest)
        }
        if (singleValue) {
            resolve(publisher, request, viewName, asyncManager)
            return null
        }
        stream(publisher, request, viewName, webRequest, asyncManager)
        return null
    }

    /**
     * @return how long a stream may stay open, or {@code null} for no timeout
     */
    Duration getTimeout() {
        timeoutMillis == null ? null : Duration.ofMillis(timeoutMillis)
    }

    /**
     * @return whether the publisher emits at most one item, as Spring's adapter registry knows it, so that
     * its value is the action's result rather than a stream
     */
    static boolean isSingleValue(Publisher<?> publisher) {
        ReactiveAdapter adapter = ReactiveAdapterRegistry.sharedInstance.getAdapter(publisher.getClass())
        adapter != null && !adapter.multiValue
    }

    /**
     * Resolves a single-valued publisher the way a returned {@code Promise} is resolved.
     */
    private static void resolve(Publisher<Object> publisher, HttpServletRequest request, String viewName,
                                WebAsyncManager asyncManager) {
        DeferredResult<Object> deferredResult = new DeferredResult<Object>()
        asyncManager.startDeferredResultProcessing(deferredResult)
        request.setAttribute(GrailsApplicationAttributes.ASYNC_STARTED, true)
        SingleValueSubscriber subscriber = new SingleValueSubscriber(request, viewName, deferredResult)
        try {
            publisher.subscribe(subscriber)
        }
        catch (Throwable failure) {
            subscriber.onError(failure)
        }
    }

    private void stream(Publisher<Object> publisher, HttpServletRequest request, String viewName,
                        GrailsWebRequest webRequest, WebAsyncManager asyncManager) {
        // The asynchronous request's own response refuses a write once the request has completed, which
        // turns a late emission into the failed write the subscriber already handles.
        HttpServletResponse response = asyncManager.asyncWebRequest.getNativeResponse(HttpServletResponse)

        // A timed-out stream completes with no result instead of an AsyncRequestTimeoutException: the
        // response is committed, so there is no status left to report a timeout with.
        Supplier<Object> timeoutResult = { -> null }
        DeferredResult<Object> deferredResult = new DeferredResult<Object>(timeoutMillis ?: NO_TIMEOUT, timeoutResult)
        ServerSentEventSubscriber subscriber = new ServerSentEventSubscriber(response, formatter, deferredResult)
        deferredResult.onTimeout { -> subscriber.timeout() }
        deferredResult.onError { Throwable failure -> subscriber.cancel() }
        deferredResult.onCompletion { -> subscriber.cancel() }

        response.contentType = CONTENT_TYPE + ';charset=UTF-8'
        response.setHeader('Cache-Control', 'no-cache')
        asyncManager.startDeferredResultProcessing(deferredResult)
        request.setAttribute(GrailsApplicationAttributes.ASYNC_STARTED, true)
        webRequest.renderView = false
        try {
            // Commit the headers now, so a client is connected before the first item arrives, however long that takes.
            response.flushBuffer()
        }
        catch (IOException failure) {
            log.debug('Client went away before a streamed response began', failure)
            subscriber.cancel()
            deferredResult.setResult(null)
            return
        }
        try {
            publisher.subscribe(subscriber)
        }
        catch (Throwable failure) {
            subscriber.fail(failure)
        }
    }

}

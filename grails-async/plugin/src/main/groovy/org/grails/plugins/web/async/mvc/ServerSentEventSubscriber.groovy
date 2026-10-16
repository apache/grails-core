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

import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import jakarta.servlet.ServletOutputStream
import jakarta.servlet.http.HttpServletResponse
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription

import org.springframework.web.context.request.async.DeferredResult

/**
 * Writes the items of a stream to the response as server-sent events, one at a time, and completes
 * the deferred result that holds the request open when the stream ends.
 *
 * <p>Items are requested one by one, so a publisher that honours back-pressure produces no faster
 * than the response is written. Every stream ends with a {@code complete} event, so an
 * {@code EventSource} can close instead of reconnecting: its data is empty when the publisher completed,
 * {@code timeout} when the container timed the request out and {@code error} when the publisher failed
 * or an item could not be rendered, since the response was committed when streaming began and cannot
 * carry an error status any more. The failure itself is logged on the server and nothing of it reaches
 * the client. A client that disconnects is detected by the failed write and the stream is cancelled.</p>
 *
 * <p>The publisher emits on its own thread while the container reports a timeout on another, so every
 * write to the response, and the check that the stream is still open before it, happens under one
 * lock; two events can never interleave on the wire.</p>
 *
 * @since 8.1
 */
@CompileStatic
@Slf4j
class ServerSentEventSubscriber implements Subscriber<Object> {

    private final HttpServletResponse response
    private final ServerSentEventFormatter formatter
    private final DeferredResult<Object> deferredResult
    private final AtomicBoolean done = new AtomicBoolean()
    private final Object lock = new Object()
    private volatile Subscription subscription

    /**
     * @param response the response to write to, the asynchronous request's own so that a write after the
     * request has completed fails instead of reaching a recycled response
     */
    ServerSentEventSubscriber(HttpServletResponse response, ServerSentEventFormatter formatter,
                              DeferredResult<Object> deferredResult) {
        this.response = response
        this.formatter = formatter
        this.deferredResult = deferredResult
    }

    @Override
    void onSubscribe(Subscription subscription) {
        if (this.subscription != null) {
            subscription.cancel()
            return
        }
        this.subscription = subscription
        if (done.get()) {
            subscription.cancel()
            return
        }
        subscription.request(1)
    }

    @Override
    void onNext(Object item) {
        boolean written = false
        synchronized (lock) {
            if (done.get()) {
                return
            }
            String event
            try {
                event = formatter.format(item)
            }
            catch (Throwable failure) {
                end(failure)
                return
            }
            try {
                write(event)
                written = true
            }
            catch (IOException failure) {
                log.debug('Client went away while a response was being streamed', failure)
                subscription.cancel()
                finish()
            }
        }
        if (written) {
            subscription.request(1)
        }
    }

    @Override
    void onError(Throwable failure) {
        fail(failure)
    }

    @Override
    void onComplete() {
        synchronized (lock) {
            if (done.get()) {
                return
            }
            try {
                write(formatter.formatCompletion(''))
            }
            catch (IOException failure) {
                log.debug('Client went away before the end of a streamed response could be reported', failure)
            }
            finish()
        }
    }

    /**
     * Ends the stream after a failure of the publisher, of a renderer or of the subscription itself: the
     * client gets a {@code complete} event whose data is {@code error}, and the request completes.
     */
    void fail(Throwable failure) {
        synchronized (lock) {
            end(failure)
        }
    }

    /**
     * Stops the stream when the container times the request out. The client is told the stream is
     * complete, with {@code timeout} as the reason; the deferred result completes itself.
     */
    void timeout() {
        synchronized (lock) {
            if (!done.compareAndSet(false, true)) {
                return
            }
            subscription?.cancel()
            try {
                write(formatter.formatCompletion(ServerSentEventFormatter.TIMEOUT_REASON))
            }
            catch (IOException ignored) {
                log.debug('Client went away before the timeout of a streamed response could be reported', ignored)
            }
        }
    }

    /**
     * Stops the stream without writing anything more, once the request has completed or failed.
     */
    void cancel() {
        done.set(true)
        subscription?.cancel()
    }

    /**
     * @return whether the stream has ended, by completing, failing or being cancelled
     */
    boolean isDone() {
        done.get()
    }

    private void end(Throwable failure) {
        if (!done.compareAndSet(false, true)) {
            return
        }
        log.error('Streamed response failed', failure)
        subscription?.cancel()
        try {
            write(formatter.formatCompletion(ServerSentEventFormatter.ERROR_REASON))
        }
        catch (IOException ignored) {
            log.debug('Client went away before the failure of a streamed response could be reported', ignored)
        }
        deferredResult.setResult(null)
    }

    private void finish() {
        if (done.compareAndSet(false, true)) {
            deferredResult.setResult(null)
        }
    }

    private void write(String event) throws IOException {
        ServletOutputStream out = response.outputStream
        out.write(event.getBytes(StandardCharsets.UTF_8))
        out.flush()
    }

}

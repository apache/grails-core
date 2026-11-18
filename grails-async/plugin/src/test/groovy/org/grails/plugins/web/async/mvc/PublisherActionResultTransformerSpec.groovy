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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

import jakarta.servlet.AsyncEvent
import jakarta.servlet.ServletOutputStream
import org.reactivestreams.Publisher
import org.reactivestreams.Subscriber
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.publisher.Sinks

import org.springframework.mock.web.DelegatingServletOutputStream
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.mock.web.MockServletContext
import org.springframework.web.context.request.async.WebAsyncManager
import org.springframework.web.context.request.async.WebAsyncUtils
import org.springframework.web.servlet.ModelAndView

import grails.async.web.StreamItemRenderer
import org.grails.web.servlet.mvc.GrailsWebRequest
import org.grails.web.util.GrailsApplicationAttributes
import spock.lang.Specification

class PublisherActionResultTransformerSpec extends Specification {

    private static final String COMPLETE = 'event: complete\ndata: \n\n'
    private static final String FAILED = 'event: complete\ndata: error\n\n'

    private MockHttpServletRequest request
    private MockHttpServletResponse response
    private GrailsWebRequest webRequest
    private WebAsyncManager asyncManager
    private PublisherActionResultTransformer transformer = new PublisherActionResultTransformer([new DefaultStreamItemRenderer()], null)

    void setup() {
        request = new MockHttpServletRequest(new MockServletContext())
        request.asyncSupported = true
        response = new MockHttpServletResponse()
        webRequest = new GrailsWebRequest(request, response, request.servletContext)
        asyncManager = WebAsyncUtils.getAsyncManager(request)
    }

    void 'leaves a result that is not a Publisher alone'() {
        expect:
        transformer.transformActionResult(webRequest, '/book/index', result).is(result)
        !request.asyncStarted
        webRequest.renderView

        where:
        result << [[books: []], 'text', null]
    }

    void 'streams every item of a Publisher as a server-sent event and completes the request'() {
        when:
        Object transformed = transformer.transformActionResult(webRequest, '/book/index', Flux.just('Hello', ' world', 'two\nlines'))

        then:
        transformed == null
        request.asyncStarted
        request.getAttribute(GrailsApplicationAttributes.ASYNC_STARTED) == true
        !webRequest.renderView
        response.contentType == 'text/event-stream;charset=UTF-8'
        response.getHeader('Cache-Control') == 'no-cache'
        response.contentAsString == 'data: Hello\n\ndata:  world\n\ndata: two\ndata: lines\n\n' + COMPLETE
        asyncManager.hasConcurrentResult()
        asyncManager.concurrentResult == null
    }

    void 'commits the response before the first item so the client is connected while it waits'() {
        when:
        transformer.transformActionResult(webRequest, '/book/index', Flux.never())

        then:
        response.committed
        response.contentAsString == ''
        request.asyncStarted
        !asyncManager.hasConcurrentResult()
    }

    void 'requests items one at a time'() {
        given:
        List<Long> requested = []

        when:
        transformer.transformActionResult(webRequest, '/book/index', Flux.range(1, 3).doOnRequest { long n -> requested << n })

        then:
        requested == [1L, 1L, 1L, 1L]
        response.contentAsString == 'data: 1\n\ndata: 2\n\ndata: 3\n\n' + COMPLETE
    }

    void 'a single-valued publisher, such as a Mono, is not streamed: a Map it emits is the model of the view'() {
        when:
        Object transformed = transformer.transformActionResult(webRequest, '/book/show', Mono.just([title: 'Grails']))

        then:
        transformed == null
        request.asyncStarted
        request.getAttribute(GrailsApplicationAttributes.ASYNC_STARTED) == true
        !response.committed
        response.contentType == null
        response.contentAsString == ''
        asyncManager.hasConcurrentResult()
        asyncManager.concurrentResult instanceof ModelAndView
        ((ModelAndView) asyncManager.concurrentResult).viewName == '/book/show'
        ((ModelAndView) asyncManager.concurrentResult).model == [title: 'Grails']
    }

    void 'a single-valued publisher that emits something else, or nothing, leaves the response to what the action rendered'() {
        given:
        ModelAndView rendered = new ModelAndView('/book/rendered')
        request.setAttribute(GrailsApplicationAttributes.MODEL_AND_VIEW, rendered)

        when:
        transformer.transformActionResult(webRequest, '/book/show', publisher)

        then:
        asyncManager.hasConcurrentResult()
        asyncManager.concurrentResult.is(rendered)
        !response.committed

        where:
        publisher << [Mono.just('text'), Mono.empty()]
    }

    void 'a single-valued publisher that fails fails the request'() {
        when:
        transformer.transformActionResult(webRequest, '/book/show', Mono.error(new IllegalStateException('boom')))

        then:
        asyncManager.hasConcurrentResult()
        asyncManager.concurrentResult instanceof IllegalStateException
        !response.committed
    }

    void 'a single-valued publisher is resolved once the response has been committed, as a Promise is'() {
        given:
        response.flushBuffer()

        when:
        transformer.transformActionResult(webRequest, '/book/show', Mono.just([title: 'Grails']))

        then:
        notThrown(IllegalStateException)
        asyncManager.concurrentResult instanceof ModelAndView
    }

    void 'which publishers are single-valued is what the reactive adapter registry says'() {
        expect:
        PublisherActionResultTransformer.isSingleValue(Mono.just(1))
        !PublisherActionResultTransformer.isSingleValue(Flux.just(1))
        !PublisherActionResultTransformer.isSingleValue(Sinks.many().unicast().onBackpressureBuffer().asFlux())
        !PublisherActionResultTransformer.isSingleValue({ Subscriber subscriber -> } as Publisher)
    }

    void 'a failed stream ends with a complete event whose reason is error, and nothing of the failure reaches the client'() {
        when:
        transformer.transformActionResult(webRequest, '/book/index',
                Flux.concat(Flux.just('partial'), Flux.error(new IllegalStateException('secret details'))))

        then:
        response.contentAsString == 'data: partial\n\n' + FAILED
        !response.contentAsString.contains('IllegalStateException')
        !response.contentAsString.contains('secret')
        asyncManager.hasConcurrentResult()
        asyncManager.concurrentResult == null
    }

    void 'an item that cannot be rendered ends the stream with a complete event whose reason is error'() {
        given:
        StreamItemRenderer broken = { Object item -> throw new IllegalArgumentException('cannot render') }
        PublisherActionResultTransformer withBroken = new PublisherActionResultTransformer([broken], null)
        boolean cancelled = false

        when:
        withBroken.transformActionResult(webRequest, '/book/index', Flux.just('a', 'b').doOnCancel { cancelled = true })

        then:
        response.contentAsString == FAILED
        cancelled
        asyncManager.hasConcurrentResult()
        asyncManager.concurrentResult == null
    }

    void 'a publisher that fails to subscribe ends the stream with a complete event whose reason is error'() {
        given:
        Flux<String> refusing = Flux.from { subscriber -> throw new IllegalStateException('no subscriptions') }

        when:
        transformer.transformActionResult(webRequest, '/book/index', refusing)

        then:
        response.contentAsString == FAILED
        asyncManager.hasConcurrentResult()
    }

    void 'a timeout cancels the stream and completes the request with a complete event'() {
        given:
        boolean cancelled = false

        when:
        transformer.transformActionResult(webRequest, '/book/index', Flux.never().doOnCancel { cancelled = true })
        asyncManager.asyncWebRequest.onTimeout(new AsyncEvent(request.asyncContext))

        then:
        cancelled
        asyncManager.hasConcurrentResult()
        asyncManager.concurrentResult == null
        response.contentAsString == 'event: complete\ndata: timeout\n\n'
    }

    void 'a timeout reported while an item is being written does not interleave with it'() {
        given: 'a response whose first byte blocks until the test releases it'
        CountDownLatch writing = new CountDownLatch(1)
        CountDownLatch release = new CountDownLatch(1)
        ByteArrayOutputStream wire = new ByteArrayOutputStream()
        OutputStream blocking = new OutputStream() {
            private volatile boolean first = true

            @Override
            void write(int b) throws IOException {
                if (first) {
                    first = false
                    writing.countDown()
                    release.await(10, TimeUnit.SECONDS)
                }
                wire.write(b)
            }
        }
        response = new MockHttpServletResponse() {
            @Override
            ServletOutputStream getOutputStream() {
                new DelegatingServletOutputStream(blocking)
            }
        }
        webRequest = new GrailsWebRequest(request, response, request.servletContext)
        Sinks.Many<String> sink = Sinks.many().unicast().onBackpressureBuffer()
        transformer.transformActionResult(webRequest, '/book/index', sink.asFlux())

        when: 'an item is written on the publisher thread while the container reports a timeout'
        Thread emitter = new Thread({ -> sink.tryEmitNext('slow item') })
        emitter.start()
        writing.await(10, TimeUnit.SECONDS)
        Thread timer = new Thread({ -> asyncManager.asyncWebRequest.onTimeout(new AsyncEvent(request.asyncContext)) })
        timer.start()
        timer.join(500)
        boolean timeoutWaited = timer.alive
        release.countDown()
        emitter.join(10_000)
        timer.join(10_000)

        then: 'the timeout waited for the item, so the events follow one another on the wire'
        timeoutWaited
        wire.toString('UTF-8') == 'data: slow item\n\nevent: complete\ndata: timeout\n\n'
    }

    void 'items emitted after the request has completed are dropped'() {
        given:
        Sinks.Many<String> sink = Sinks.many().unicast().onBackpressureBuffer()

        when:
        transformer.transformActionResult(webRequest, '/book/index', sink.asFlux())
        sink.tryEmitNext('before')
        asyncManager.asyncWebRequest.onComplete(new AsyncEvent(request.asyncContext))
        Sinks.EmitResult late = sink.tryEmitNext('after')

        then:
        late == Sinks.EmitResult.FAIL_CANCELLED
        response.contentAsString == 'data: before\n\n'
    }

    void 'a client that goes away cancels the stream'() {
        given:
        boolean cancelled = false
        int writes = 0
        response = new MockHttpServletResponse() {
            @Override
            ServletOutputStream getOutputStream() {
                new DelegatingServletOutputStream(new OutputStream() {
                    @Override
                    void write(int b) throws IOException {
                        throw new IOException('Broken pipe')
                    }
                })
            }
        }
        webRequest = new GrailsWebRequest(request, response, request.servletContext)
        Flux<String> stream = Flux.just('a', 'b').doOnNext { writes++ }.doOnCancel { cancelled = true }

        when:
        transformer.transformActionResult(webRequest, '/book/index', stream)

        then:
        writes == 1
        cancelled
        asyncManager.hasConcurrentResult()
    }

    void 'a client that goes away before the first byte completes the request quietly'() {
        given:
        response = new MockHttpServletResponse() {
            @Override
            void flushBuffer() {
                throw new IOException('Broken pipe')
            }
        }
        webRequest = new GrailsWebRequest(request, response, request.servletContext)
        boolean subscribed = false

        when:
        Object transformed = transformer.transformActionResult(webRequest, '/book/index', Flux.just('a').doOnSubscribe { subscribed = true })

        then:
        transformed == null
        !subscribed
        asyncManager.hasConcurrentResult()
        asyncManager.concurrentResult == null
    }

    void 'uses the configured streaming timeout for the asynchronous request'() {
        given:
        PublisherActionResultTransformer timed = new PublisherActionResultTransformer([new DefaultStreamItemRenderer()], Duration.ofSeconds(42))

        when:
        timed.transformActionResult(webRequest, '/book/index', Flux.never())

        then:
        timed.timeout == Duration.ofSeconds(42)
        request.asyncContext.timeout == 42000
    }

    void 'without a streaming timeout a stream has no timeout, as a Spring MVC server-sent event stream has none'() {
        when:
        transformer.transformActionResult(webRequest, '/book/index', Flux.never())

        then:
        transformer.timeout == null
        request.asyncContext.timeout == PublisherActionResultTransformer.NO_TIMEOUT
    }

    void 'refuses to stream once the response has been committed'() {
        given:
        response.flushBuffer()

        when:
        transformer.transformActionResult(webRequest, '/book/index', Flux.just('late'))

        then:
        IllegalStateException failure = thrown()
        failure.message.contains('committed')
    }

    void 'refuses to stream once asynchronous processing has completed'() {
        given:
        transformer.transformActionResult(webRequest, '/book/index', Flux.never())
        asyncManager.asyncWebRequest.onComplete(new AsyncEvent(request.asyncContext))

        when:
        transformer.transformActionResult(webRequest, '/book/index', Flux.just('again'))

        then:
        IllegalStateException failure = thrown()
        failure.message.contains('completed')
    }

}

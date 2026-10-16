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
package aiapp

import java.time.Duration

import java.util.concurrent.Callable

import grails.compiler.GrailsCompileStatic
import grails.converters.JSON
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

import org.springframework.http.codec.ServerSentEvent

/**
 * Streams of every kind of item the server-sent event support renders, without a model in the way.
 */
@GrailsCompileStatic
class StreamController {

    def ticks() {
        Flux.just('one', 'two', 'three\nlines')
    }

    /**
     * Emits from a timer thread after the container thread has returned, which is what a model does.
     */
    def slow() {
        Flux.interval(Duration.ofMillis(100)).take(3).map { Long tick -> "tick ${tick}".toString() }
    }

    def json() {
        Flux.just([count: 1], [count: 2])
    }

    def events() {
        Flux.just(ServerSentEvent.builder('ready').id('1').event('status').build())
    }

    def failing() {
        Flux.concat(Flux.just('partial'), Flux.error(new IllegalStateException('boom')))
    }

    /**
     * A single-valued publisher is resolved, not streamed: what it renders is the response, as with a Promise.
     */
    def single() {
        Mono.fromCallable({ ->
            render([answer: 42] as JSON)
            null
        } as Callable)
    }

}

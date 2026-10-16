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

import spock.lang.Specification

import grails.testing.mixin.integration.Integration
import org.apache.grails.testing.http.client.HttpClientSupport

/**
 * A Publisher returned from a controller action reaches the client as server-sent events through the
 * running server, with every kind of item the support renders.
 */
@Integration
class StreamingFunctionalSpec extends Specification implements HttpClientSupport {

    private static final String COMPLETE = 'event: complete\ndata: \n\n'

    void 'text items arrive as one event each, a data line per line'() {
        when:
        def response = http('/stream/ticks')

        then:
        response.assertStatus(200)
        response.contentType.startsWith('text/event-stream')
        response.headerValue('Cache-Control') == 'no-cache'
        response.body() == 'data: one\n\ndata: two\n\ndata: three\ndata: lines\n\n' + COMPLETE
    }

    void 'items emitted after the container thread has returned still reach the client'() {
        when:
        def response = http('/stream/slow')

        then:
        response.assertStatus(200)
        response.body() == 'data: tick 0\n\ndata: tick 1\n\ndata: tick 2\n\n' + COMPLETE
    }

    void 'items that are not text arrive as JSON'() {
        expect:
        http('/stream/json').assertStatus(200).body() == 'data: {"count":1}\n\ndata: {"count":2}\n\n' + COMPLETE
    }

    void 'a ServerSentEvent arrives with its id and event name'() {
        expect:
        http('/stream/events').assertStatus(200).body() == 'id: 1\nevent: status\ndata: ready\n\n' + COMPLETE
    }

    void 'a failed stream ends with a complete event whose reason is error'() {
        when:
        def response = http('/stream/failing')

        then:
        response.assertStatus(200)
        response.body() == 'data: partial\n\nevent: complete\ndata: error\n\n'
    }

    void 'a single-valued publisher is resolved rather than streamed, so what it rendered is the response'() {
        when:
        def response = http('/stream/single')

        then:
        response.assertStatus(200)
        response.contentType.startsWith('application/json')
        response.body() == '{"answer":42}'
    }

}

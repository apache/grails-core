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

import tools.jackson.databind.json.JsonMapper

import org.springframework.http.codec.ServerSentEvent

import grails.async.web.StreamItemRenderer
import spock.lang.Specification

class ServerSentEventFormatterSpec extends Specification {

    ServerSentEventFormatter formatter = new ServerSentEventFormatter([
            new DefaultStreamItemRenderer(),
            new JacksonStreamItemRenderer(JsonMapper.builder().build())
    ])

    void 'formats text as the data of one event, a line per line of text'() {
        expect:
        formatter.format(text) == event

        where:
        text               | event
        'hello'            | 'data: hello\n\n'
        ' world'           | 'data:  world\n\n'
        'a\nb'             | 'data: a\ndata: b\n\n'
        'a\r\nb\rc'        | 'data: a\ndata: b\ndata: c\n\n'
        'a\n'              | 'data: a\ndata: \n\n'
        '\n\n'             | 'data: \ndata: \ndata: \n\n'
        ''                 | 'data: \n\n'
        null               | 'data: \n\n'
    }

    void 'a client assembles exactly the text that was emitted'() {
        expect: 'joining the data lines with line breaks and dropping the last one gives the text back'
        assemble(formatter.format(text)) == text

        where:
        text << ['hello', 'a\nb', 'a\n', '\n\n', 'two\n\nparagraphs\n', '']
    }

    void 'renders scalars as their string form and other items as JSON'() {
        expect:
        formatter.format(item) == event

        where:
        item                 | event
        42                   | 'data: 42\n\n'
        true                 | 'data: true\n\n'
        [count: 1, ok: true] | 'data: {"count":1,"ok":true}\n\n'
        ['a', 'b']           | 'data: ["a","b"]\n\n'
    }

    void 'falls back to String.valueOf when no renderer handles an item'() {
        given:
        ServerSentEventFormatter noJson = new ServerSentEventFormatter([new DefaultStreamItemRenderer()])

        expect:
        noJson.format([count: 1]) == 'data: {count=1}\n\n'
    }

    void 'the first renderer that handles an item wins'() {
        given:
        StreamItemRenderer shouting = { Object item -> item instanceof CharSequence ? item.toString().toUpperCase() : null }
        ServerSentEventFormatter custom = new ServerSentEventFormatter([shouting, new DefaultStreamItemRenderer()])

        expect:
        custom.format('hello') == 'data: HELLO\n\n'
        custom.format(7) == 'data: 7\n\n'
    }

    void 'writes every field of a ServerSentEvent'() {
        given:
        ServerSentEvent<Map> event = ServerSentEvent.builder([tick: 1])
                .id('7')
                .event('tick')
                .retry(Duration.ofSeconds(3))
                .comment('keep\nalive')
                .build()

        expect:
        formatter.format(event) == ':keep\n:alive\nid: 7\nevent: tick\nretry: 3000\ndata: {"tick":1}\n\n'
    }

    void 'a ServerSentEvent without data is a heartbeat'() {
        expect:
        formatter.format(ServerSentEvent.builder().comment('ping').build()) == ':ping\n\n'
    }

    void 'a complete event carries the reason the stream ended'() {
        expect:
        formatter.formatCompletion('') == 'event: complete\ndata: \n\n'
        formatter.formatCompletion(null) == 'event: complete\ndata: \n\n'
        formatter.formatCompletion(ServerSentEventFormatter.TIMEOUT_REASON) == 'event: complete\ndata: timeout\n\n'
        formatter.formatCompletion(ServerSentEventFormatter.ERROR_REASON) == 'event: complete\ndata: error\n\n'
    }

    void 'no event is named error, which an EventSource dispatches for its own connection failures'() {
        expect:
        ServerSentEventFormatter.COMPLETE_EVENT != 'error'
        !formatter.formatCompletion(ServerSentEventFormatter.ERROR_REASON).contains('event: error')
    }

    private static String assemble(String wire) {
        String buffer = ''
        for (String line : wire.split('\n', -1)) {
            if (line.startsWith('data: ')) {
                buffer += line.substring('data: '.length()) + '\n'
            }
        }
        buffer.endsWith('\n') ? buffer.substring(0, buffer.length() - 1) : buffer
    }

}

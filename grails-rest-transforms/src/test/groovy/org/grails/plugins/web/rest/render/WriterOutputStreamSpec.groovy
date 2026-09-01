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
package org.grails.plugins.web.rest.render

import java.nio.charset.Charset

import spock.lang.Specification

import static java.nio.charset.StandardCharsets.ISO_8859_1
import static java.nio.charset.StandardCharsets.UTF_8

class WriterOutputStreamSpec extends Specification {

    void 'bytes are decoded into the writer as they are written'() {
        given:
        def writer = new StringWriter()

        when:
        WriterOutputStream.writeThrough(writer, UTF_8) { it.write('{"title":"Grails"}'.getBytes(UTF_8)) }

        then:
        writer.toString() == '{"title":"Grails"}'
    }

    void 'a multi-byte character split across writes is not corrupted'() {
        given: "the two bytes of an e-acute arrive in separate writes"
        def writer = new StringWriter()
        byte[] bytes = 'café'.getBytes(UTF_8)

        when:
        WriterOutputStream.writeThrough(writer, UTF_8) { stream ->
            bytes.each { stream.write(it as int) }
        }

        then:
        writer.toString() == 'café'
    }

    void 'a body larger than the internal buffer streams through intact'() {
        given: "a body several times the 8k buffer, with multi-byte characters throughout"
        def writer = new StringWriter()
        String body = 'áé' * 20000

        when:
        WriterOutputStream.writeThrough(writer, UTF_8) { it.write(body.getBytes(UTF_8)) }

        then:
        writer.toString() == body
        writer.toString().length() == 40000
    }

    void 'the configured charset is honoured'() {
        given:
        def writer = new StringWriter()

        when:
        WriterOutputStream.writeThrough(writer, ISO_8859_1) { it.write('café'.getBytes(ISO_8859_1)) }

        then:
        writer.toString() == 'café'
    }

    void 'the writer is left open for the container to close'() {
        given:
        def closed = false
        def writer = new StringWriter() {
            @Override
            void close() {
                closed = true
            }
        }

        when:
        WriterOutputStream.writeThrough(writer, UTF_8) { it.write('body'.getBytes(UTF_8)) }

        then:
        !closed
        writer.toString() == 'body'
    }

    void 'an I/O failure surfaces rather than truncating the response'() {
        given:
        def writer = new StringWriter()

        when:
        WriterOutputStream.writeThrough(writer, UTF_8) { throw new IOException('disconnected') }

        then:
        def e = thrown(UncheckedIOException)
        e.cause.message == 'disconnected'
    }
}

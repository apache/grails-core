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
package org.grails.web.converters.jackson

import spock.lang.Specification
import tools.jackson.databind.json.JsonMapper

import org.grails.web.json.JSONException
import org.grails.web.json.JSONWriter
import org.grails.web.json.PathCapturingJSONWriterWrapper

/**
 * {@link JacksonJSONWriter} accepts and rejects the same calls as {@link JSONWriter}.
 */
class JacksonJSONWriterSpec extends Specification {

    StringWriter out = new StringWriter()

    JsonMapperSupport jsonMapper = new JsonMapperSupport(JsonMapper.builder().build())

    JacksonJSONWriter writer = new JacksonJSONWriter(jsonMapper.createGenerator(out, false), jsonMapper)

    void "writes objects, arrays and values"() {
        when:
        writer.object()
                .key('a').value(1L)
                .key('b').array().value(true).value(2.5d).value('x').valueNull().endArray()
                .key('c').object().key('d').value(new JsonMapperValue(new Date(0L), jsonMapper)).endObject()
                .endObject()
        writer.generator.flush()

        then:
        out.toString() == '{"a":1,"b":[true,2.5,"x",null],"c":{"d":"1970-01-01T00:00:00.000Z"}}'
    }

    void "rejects #description, as JSONWriter does"() {
        when:
        calls.call(writer)

        then:
        def e = thrown(JSONException)
        e.message.startsWith(message)

        where:
        description               | calls                                             || message
        'a key outside an object' | { JSONWriter w -> w.array().key('a') }            || 'Misplaced key'
        'a value without a key'   | { JSONWriter w -> w.object().value('a') }         || 'Value out of sequence'
        'a null key'              | { JSONWriter w -> w.object().key(null) }          || 'Null key.'
        'an unbalanced end'       | { JSONWriter w -> w.object().endArray() }         || 'Misplaced endArray.'
    }

    void "a PathCapturingJSONWriterWrapper can wrap it"() {
        given:
        def wrapper = new PathCapturingJSONWriterWrapper(writer)

        when:
        wrapper.object().key('list').array().object().key('a')
        def path = wrapper.currentStrackReference
        wrapper.value(1L).endObject().endArray().endObject()
        writer.generator.flush()

        then:
        path == '.list[0].a'
        out.toString() == '{"list":[{"a":1}]}'
    }

    void "a JsonMapperValue written to a plain JSONWriter is the JSON the mapper writes"() {
        given:
        def text = new StringWriter()

        when:
        new JSONWriter(text).array().value(new JsonMapperValue(new Date(0L), jsonMapper)).endArray()

        then:
        text.toString() == '["1970-01-01T00:00:00.000Z"]'
    }

    void "a generator failure is reported as a JSONException"() {
        given:
        def failing = new JacksonJSONWriter(jsonMapper.createGenerator(new Writer() {
            void write(char[] cbuf, int off, int len) { throw new IOException('closed') }
            void flush() { throw new IOException('closed') }
            void close() {}
        }, false), jsonMapper)

        when:
        failing.object().key('a').value('x' * 10000).endObject()
        failing.generator.flush()

        then:
        thrown(JSONException)
    }
}

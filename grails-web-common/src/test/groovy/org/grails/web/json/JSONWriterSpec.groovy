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
package org.grails.web.json

import java.util.function.Predicate

import groovy.transform.CompileStatic
import spock.lang.Issue
import spock.lang.Specification
import tools.jackson.core.JsonGenerator
import tools.jackson.databind.SerializationContext
import tools.jackson.databind.cfg.DateTimeFeature
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.module.SimpleModule
import tools.jackson.databind.ser.std.StdSerializer

class JSONWriterSpec extends Specification {

    StringWriter out = new StringWriter()

    JsonMapperSupport jsonMapper = new JsonMapperSupport(JsonMapper.builder().build())

    JSONWriter writer = new JSONWriter(out, jsonMapper, false)

    JsonMapperSupport boxes = new JsonMapperSupport(JsonMapper.builder()
            .addModule(new SimpleModule().addSerializer(Box, new BoxSerializer())).build())

    @Issue('GRAILS-10823')
    void 'Test rendering a forward slash'() {
        when:
        writer.object().key('namespace').value('alpha/beta').endObject()

        then:
        '{"namespace":"alpha/beta"}' == out.toString()
    }

    void 'should handle nulls'() {
        when:
        writer.array()
        writeNumber(writer, null)
        writeObject(writer, null)
        writer.endArray()

        then:
        '[{"key":null},{"key":null}]' == out.toString()
    }

    @CompileStatic
    private writeNumber(JSONWriter jsonWriter, Number n) {
        jsonWriter.object().key('key').value(n).endObject()
    }

    @CompileStatic
    private writeObject(JSONWriter jsonWriter, Object o) {
        jsonWriter.object().key('key').value(o).endObject()
    }

    void "writes objects, arrays and values"() {
        when:
        writer.object()
                .key('a').value(1L)
                .key('b').array().value(true).value(2.5d).value('x').valueNull().endArray()
                .key('c').object().key('d').value(new JsonMapperValue(new Date(0L), jsonMapper)).endObject()
                .endObject()

        then: 'the complete text is flushed'
        out.toString() == '{"a":1,"b":[true,2.5,"x",null],"c":{"d":"1970-01-01T00:00:00.000Z"}}'
    }

    void "rejects #description"() {
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

        then:
        path == '.list[0].a'
        out.toString() == '{"list":[{"a":1}]}'
    }

    void "a single value is a JSON text, written without a flush"() {
        when:
        new JSONWriter(out).value('x')

        then:
        out.toString() == '"x"'
    }

    void "flush() writes an incomplete text so far"() {
        when:
        writer.object().key('a')

        then:
        out.toString() == ''

        when:
        writer.flush()

        then:
        out.toString() == '{"a"'
    }

    void "the Writer is neither flushed nor closed"() {
        given:
        def calls = []
        def target = new StringWriter() {
            void flush() { calls << 'flush'; super.flush() }
            void close() { calls << 'close'; super.close() }
        }

        when:
        def jsonWriter = new JSONWriter(target, jsonMapper, false)
        jsonWriter.object().key('a').value(1L).endObject()
        jsonWriter.flush()

        then:
        target.toString() == '{"a":1}'
        calls == []
    }

    void "a JsonMapperValue is written by its own mapper"() {
        given:
        def timestamps = new JsonMapperSupport(JsonMapper.builder().enable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS).build())

        when:
        writer.array()
                .value(new JsonMapperValue(new Date(0L), jsonMapper))
                .value(new JsonMapperValue(new Date(0L), timestamps))
                .endArray()

        then:
        out.toString() == '["1970-01-01T00:00:00.000Z",0]'
    }

    void "a PathCapturingJSONWriterWrapper passes flush() on"() {
        given:
        def wrapper = new PathCapturingJSONWriterWrapper(writer)

        when:
        wrapper.object().key('a')
        wrapper.flush()

        then:
        out.toString() == '{"a"'
    }

    void "a JsonMapperValue is the JSON text the mapper writes"() {
        expect:
        new JsonMapperValue(new Date(0L), jsonMapper).toString() == '"1970-01-01T00:00:00.000Z"'
    }

    void "a JSONElement is written as the JSON text it writes, and other objects as their quoted text"() {
        when:
        writer.array().value(new JSONObject([a: 1])).value(new URI('http://x/y')).endArray()

        then:
        out.toString() == '[{"a":1},"http://x/y"]'
    }

    void "a generator failure is reported as a JSONException"() {
        given:
        def failing = new JSONWriter(new Writer() {
            void write(char[] cbuf, int off, int len) { throw new IOException('closed') }
            void flush() { throw new IOException('closed') }
            void close() {}
        }, jsonMapper, false)

        when:
        failing.object().key('a').value('x' * 10000).endObject()

        then:
        thrown(JSONException)
    }

    void "a value nested in a JsonMapperValue is written by its nested value writer, and the writer then carries on"() {
        given: 'a nested value writer for maps'
        def writer = new JSONWriter(out, boxes, false)
        def nested = { Object value ->
            if (value instanceof Map) {
                writer.writeNested { writer.object().key('size').value(value.size()).endObject() }
                return true
            }
            false
        } as Predicate<Object>

        when:
        writer.object()
                .key('map').value(new JsonMapperValue(new Box(content: [a: 1, b: 2]), boxes, nested))
                .key('date').value(new JsonMapperValue(new Box(content: new Date(0L)), boxes, nested))
                .key('after').value(1)
                .endObject()

        then: 'the map is written by the nested value writer, and the date by the mapper'
        out.toString() == '{"map":{"content":{"size":2}},"date":{"content":"1970-01-01T00:00:00.000Z"},"after":1}'
    }

    void "a nested value writer must write one complete value"() {
        given:
        def writer = new JSONWriter(out, boxes, false)
        def nested = { Object value ->
            writer.writeNested { writer.object().key('a') }
            true
        } as Predicate<Object>

        when:
        writer.object().key('box').value(new JsonMapperValue(new Box(content: [a: 1]), boxes, nested))

        then:
        thrown(JSONException)
    }

    void "a JsonMapperValue of another mapper is written without its nested value writer"() {
        given:
        def nested = { Object value -> throw new AssertionError("offered $value") } as Predicate<Object>

        when:
        writer.array().value(new JsonMapperValue(new Box(content: [a: 1]), boxes, nested)).endArray()

        then:
        out.toString() == '[{"content":{"a":1}}]'
    }

    void "a PathCapturingJSONWriterWrapper tracks the path of a nested value through the mapper's output"() {
        given:
        def wrapper = new PathCapturingJSONWriterWrapper(new JSONWriter(out, boxes, false))
        String path = null
        def nested = { Object value ->
            wrapper.writeNested {
                wrapper.object().key('x')
                path = wrapper.currentStrackReference
                wrapper.value(1).endObject()
            }
            true
        } as Predicate<Object>

        when:
        wrapper.object().key('list').array()
                .value(1)
                .value(new JsonMapperValue(new Box(content: [a: 1]), boxes, nested))
                .endArray()
                .endObject()

        then:
        path == '.list[1].content.x'
        out.toString() == '{"list":[1,{"content":{"x":1}}]}'
    }
}

class Box {
    Object content
}

class BoxSerializer extends StdSerializer<Box> {

    BoxSerializer() {
        super(Box)
    }

    @Override
    void serialize(Box box, JsonGenerator generator, SerializationContext context) {
        generator.writeStartObject()
        generator.writeName('content')
        generator.writePOJO(box.content)
        generator.writeEndObject()
    }
}

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

import java.io.StringWriter

import groovy.transform.CompileStatic
import spock.lang.Specification

class PathCapturingJSONWriterWrapperApiSpec extends Specification {

    void "capture nested object properties and array indices as values are written"() {
        given:
        def output = new StringWriter()
        def writer = new PathCapturingJSONWriterWrapper(new JSONWriter(output))

        expect:
        writer.getCurrentStackReference() == ''
        writer.getStackReference(0) == ''
        writer.object().key('items').is(writer)
        writer.getCurrentStackReference() == '.items'
        writer.array().is(writer)
        writer.getCurrentStackReference() == '.items[-1]'
        writer.object().key('name').is(writer)
        writer.getCurrentStackReference() == '.items[0].name'
        writer.getCurrentStrackReference() == writer.getCurrentStackReference()
        writer.getStackReference(1) == '.items'
        writer.getStackReference(2) == '.items[0]'
        writeObjectValue(writer, 'Grails').is(writer)
        writer.getCurrentStackReference() == '.items[0]'
        writer.endObject().is(writer)
        writer.object().key('active').is(writer)
        writer.getCurrentStackReference() == '.items[1].active'
        writeBoolean(writer, true).is(writer)
        writer.getCurrentStackReference() == '.items[1]'
        writer.endObject().endArray().is(writer)
        writer.getCurrentStackReference() == ''
        writer.key('nested').object().key('count').is(writer)
        writer.getCurrentStackReference() == '.nested.count'
        writeLong(writer, 2L).is(writer)
        writer.getCurrentStackReference() == '.nested'
        writer.endObject().endObject().is(writer)
        writer.getCurrentStackReference() == ''
        output.toString() == '{"items":[{"name":"Grails"},{"active":true}],"nested":{"count":2}}'
    }

    void "each scalar overload advances array indices and removes object properties"() {
        given:
        def arrayOutput = new StringWriter()
        def arrayWriter = new PathCapturingJSONWriterWrapper(new JSONWriter(arrayOutput))
        def objectOutput = new StringWriter()
        def objectWriter = new PathCapturingJSONWriterWrapper(new JSONWriter(objectOutput))

        expect:
        arrayWriter.array().is(arrayWriter)
        writeBoolean(arrayWriter, false).is(arrayWriter)
        arrayWriter.getCurrentStackReference() == '[0]'
        writeDouble(arrayWriter, 2.5d).is(arrayWriter)
        arrayWriter.getCurrentStackReference() == '[1]'
        writeLong(arrayWriter, 7L).is(arrayWriter)
        arrayWriter.getCurrentStackReference() == '[2]'
        writeObjectValue(arrayWriter, null).is(arrayWriter)
        arrayWriter.getCurrentStackReference() == '[3]'
        arrayWriter.endArray().is(arrayWriter)
        arrayWriter.getCurrentStackReference() == ''
        arrayOutput.toString() == '[false,2.5,7,null]'
        objectWriter.object().key('boolean').is(objectWriter)
        writeBoolean(objectWriter, true).is(objectWriter)
        objectWriter.getCurrentStackReference() == ''
        objectWriter.key('double').is(objectWriter)
        writeDouble(objectWriter, 2.5d).is(objectWriter)
        objectWriter.getCurrentStackReference() == ''
        objectWriter.key('long').is(objectWriter)
        writeLong(objectWriter, 7L).is(objectWriter)
        objectWriter.getCurrentStackReference() == ''
        objectWriter.key('null').is(objectWriter)
        writeObjectValue(objectWriter, null).is(objectWriter)
        objectWriter.endObject().is(objectWriter)
        objectOutput.toString() == '{"boolean":true,"double":2.5,"long":7,"null":null}'
    }

    void "the nesting depth is the wrapped writer's"() {
        given:
        def writer = new PathCapturingJSONWriterWrapper(new JSONWriter(new StringWriter()))

        when:
        writer.object().key('list').array()

        then:
        writer.nestingDepth() == 2
    }

    void "public raw append delegates to the wrapped writer and comma writes nothing"() {
        given: 'the generator writes the separators itself, so comma() has nothing to add'
        def output = new StringWriter()
        def writer = new PathCapturingJSONWriterWrapper(new JSONWriter(output))
        def commaOutput = new StringWriter()
        def commaWriter = new PathCapturingJSONWriterWrapper(new JSONWriter(commaOutput))

        when:
        writer.array()
        def result = writer.append('true')
        writer.endArray()
        commaWriter.comma()

        then:
        result.is(writer)
        output.toString() == '[true]'
        commaOutput.toString() == ''
        writer.getCurrentStackReference() == ''
    }

    void "delegated raw append rejects null"() {
        when:
        appendRaw(new PathCapturingJSONWriterWrapper(new JSONWriter(new StringWriter())), null)

        then:
        def error = thrown(JSONException)
        error.message.contains('Null pointer')
    }

    void "stack references reject depths beyond the current path"() {
        when:
        new PathCapturingJSONWriterWrapper(new JSONWriter(new StringWriter())).getStackReference(1)

        then:
        thrown(IndexOutOfBoundsException)
    }

    @CompileStatic
    private static JSONWriter appendRaw(PathCapturingJSONWriterWrapper writer, String value) {
        writer.append(value)
    }

    @CompileStatic
    private static JSONWriter writeBoolean(PathCapturingJSONWriterWrapper writer, boolean value) {
        writer.value(value)
    }

    @CompileStatic
    private static JSONWriter writeDouble(PathCapturingJSONWriterWrapper writer, double value) {
        writer.value(value)
    }

    @CompileStatic
    private static JSONWriter writeLong(PathCapturingJSONWriterWrapper writer, long value) {
        writer.value(value)
    }

    @CompileStatic
    private static JSONWriter writeObjectValue(PathCapturingJSONWriterWrapper writer, Object value) {
        writer.value(value)
    }
}

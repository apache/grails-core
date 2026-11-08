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

import java.io.IOException
import java.io.StringWriter
import java.io.Writer
import java.util.Date

import groovy.lang.Writable
import groovy.transform.CompileStatic
import spock.lang.Specification

class JSONWriterApiSpec extends Specification {

    void "write nested objects arrays and every value overload"() {
        given:
        def output = new StringWriter()
        def writer = new JSONWriter(output)

        when:
        def result = writer.object().key('items').array().object().key('name').value((Object) 'Grails')
            .endObject().array().value(true).value(false).value(2.5d).value(4000000000L)
            .value((Number) 1.25G).value((Number) null).value((Object) null).valueNull()
            .value((Object) new JSONObject([nested: true])).value((Object) new JSONArray([1, 2]))
            .endArray().endArray().key('empty').object().endObject().endObject()

        then:
        result.is(writer)
        output.toString() == '{"items":[{"name":"Grails"},[true,false,2.5,4000000000,1.25,null,null,null,{"nested":true},[1,2]]],"empty":{}}'
    }

    void "object values quote strings string builders and ordinary objects"() {
        given:
        def output = new StringWriter()
        def writer = new JSONWriter(output)

        when:
        writer.array().value((Object) 'quote"\n\\').value((Object) new StringBuilder('builder'))
            .value((Object) new StringBuffer('buffer')).value((Object) new Date(123L))
            .value((Object) true).value((Object) 3).endArray()

        then:
        new JSONArray(output.toString()) == new JSONArray(['quote"\n\\', 'builder', 'buffer', new Date(123L), true, 3])
    }

    void "write a Writable string representation as escaped JSON"() {
        given:
        def output = new StringWriter()
        Writable value = new Writable() {
            @Override
            Writer writeTo(Writer out) throws IOException {
                out.write('line\nquote"')
                out
            }

            @Override
            String toString() {
                'line\nquote"'
            }
        }

        when:
        new JSONWriter(output).array().value((Object) value).endArray()

        then:
        new JSONArray(output.toString()).getString(0) == 'line\nquote"'
    }

    void "invalid writer sequences report #message in case #iterationIndex"() {
        given:
        def writer = new JSONWriter(new StringWriter())
        setup(writer)

        when:
        operation(writer)

        then:
        def error = thrown(JSONException)
        error.message.contains(message)

        where:
        setup                                      | operation                         | message
        ({ JSONWriter w -> })                         | ({ w -> w.value(true) })          | 'Value out of sequence'
        ({ JSONWriter w -> w.object() })              | ({ w -> w.value((Object) 'bad') }) | 'Value out of sequence'
        ({ JSONWriter w -> })                         | ({ w -> w.key('key') })           | 'Misplaced key'
        ({ JSONWriter w -> w.array() })               | ({ w -> w.key('key') })           | 'Misplaced key'
        ({ JSONWriter w -> w.object() })              | ({ w -> w.key(null) })            | 'Null key'
        ({ JSONWriter w -> w.object() })              | ({ w -> w.array() })              | 'Misplaced array'
        ({ JSONWriter w -> w.object() })              | ({ w -> w.object() })             | 'Misplaced object'
        ({ JSONWriter w -> })                         | ({ w -> w.endArray() })           | 'Misplaced endArray'
        ({ JSONWriter w -> })                         | ({ w -> w.endObject() })          | 'Misplaced endObject'
        ({ JSONWriter w -> w.object().key('key') })   | ({ w -> w.endObject() })          | 'Misplaced endObject'
        ({ JSONWriter w -> w.array().endArray() })    | ({ w -> w.array() })              | 'Misplaced array'
        ({ JSONWriter w -> w.object().endObject() })  | ({ w -> w.object() })             | 'Misplaced object'
        ({ JSONWriter w -> w.array().endArray() })    | ({ w -> w.value(1L) })            | 'Value out of sequence'
    }

    void "wrap IO failures when opening writing separating keys and closing containers"() {
        given:
        def output = Stub(Writer) {
            write(_) >> { List arguments ->
                def value = arguments[0]
                if (value == failingValue) {
                    throw new IOException('broken output')
                }
            }
        }
        def writer = new JSONWriter(output)

        when:
        operation(writer)

        then:
        def error = thrown(JSONException)
        error.cause instanceof IOException
        error.message == 'broken output'

        where:
        failingValue | operation
        '['          | ({ w -> w.array() })
        '{'          | ({ w -> w.object() })
        'true'       | ({ w -> w.array().value(true) })
        (int) ','    | ({ w -> w.array().value(true).value(false) })
        (int) ','    | ({ w -> w.object().key('one').value(true).key('two') })
        (int) ':'    | ({ w -> w.object().key('one') })
        (int) ']'    | ({ w -> w.array().endArray() })
        (int) '}'    | ({ w -> w.object().endObject() })
    }

    void "primitive overloads are available without Groovy dispatch"() {
        given:
        def output = new StringWriter()

        when:
        writePrimitives(new JSONWriter(output))

        then:
        output.toString() == '[true,2.5,7,1.25,null,null]'
    }

    @CompileStatic
    private static void writePrimitives(JSONWriter writer) {
        writer.array()
        writer.value(true)
        writer.value(2.5d)
        writer.value(7L)
        writer.value((Number) 1.25G)
        writer.value((Number) null)
        writer.value((Object) null)
        writer.endArray()
    }
}

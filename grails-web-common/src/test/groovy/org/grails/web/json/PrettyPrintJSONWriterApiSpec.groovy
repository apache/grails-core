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

import spock.lang.Specification

class PrettyPrintJSONWriterApiSpec extends Specification {

    void "indent nested objects and arrays with the default indentation"() {
        given:
        def output = new StringWriter()
        def writer = new PrettyPrintJSONWriter(output)
        def newline = PrettyPrintJSONWriter.NEWLINE

        when:
        def result = writer.object().key('items').array().object().key('name').value((Object) 'Grails')
            .endObject().value(true).valueNull().endArray().key('count').value(2L).endObject()

        then:
        result.is(writer)
        PrettyPrintJSONWriter.DEFAULT_INDENT_STR == '  '
        output.toString() == ['{', '  "items": ', '  [', '    {', '      "name": "Grails"', '    },', '    true,', '    null', '  ],', '  "count": 2', '}'].join(newline)
        new JSONObject(output.toString()).getJSONArray('items').length() == 3
    }

    void "custom indentation is used at each nesting level including empty containers"() {
        given:
        def output = new StringWriter()
        def newline = PrettyPrintJSONWriter.NEWLINE

        when:
        new PrettyPrintJSONWriter(output, '    ').object().key('empty').object().endObject()
            .key('array').array().endArray().endObject()

        then:
        output.toString() == ['{', '    "empty": {', '    },', '    "array": ', '    [', '    ]', '}'].join(newline)
    }

    void "pretty writer rejects invalid key and value sequences"() {
        given:
        def writer = new PrettyPrintJSONWriter(new StringWriter())

        when:
        operation(writer)

        then:
        def error = thrown(JSONException)
        error.message.contains(message)

        where:
        operation                           | message
        ({ w -> w.key(null) })                 | 'Null key'
        ({ w -> w.key('key') })                | 'Misplaced key'
        ({ w -> w.object().valueNull() })      | 'Value out of sequence'
        ({ w -> w.array().endObject() })       | 'Misplaced endObject'
        ({ w -> w.object().endArray() })       | 'Misplaced endArray'
    }

    void "pretty writer writes nothing before rejecting a misplaced end"() {
        given:
        def output = new StringWriter()
        def writer = new PrettyPrintJSONWriter(output)
        writer.array()
        def written = output.toString()

        when:
        writer.endObject()

        then:
        def error = thrown(JSONException)
        error.message == 'Misplaced endObject.'
        output.toString() == written
    }
}

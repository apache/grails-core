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
package grails.plugin.json.builder

import groovy.json.JsonException
import spock.lang.Specification

class JsonOutputSpec extends Specification {

    void 'the structural constants are exposed'() {
        expect:
        JsonOutput.OPEN_BRACE == ('{' as char)
        JsonOutput.OPEN_BRACKET == ('[' as char)
        JsonOutput.CLOSE_BRACKET == (']' as char)
        JsonOutput.CLOSE_BRACE == ('}' as char)
        JsonOutput.COLON == (':' as char)
        JsonOutput.COMMA == (',' as char)
        JsonOutput.NULL_VALUE == 'null'
        groovy.json.JsonOutput.isAssignableFrom(JsonOutput)
        JsonOutput.toJson([a: 1]) == '{"a":1}'
    }

    void 'a json writable behaves as a char sequence backed by its writer'() {
        given:
        JsonOutput.JsonWritable writable = new JsonOutput.JsonWritable() {
            @Override
            Writer writeTo(Writer out) throws IOException {
                out.write(inline ? 'inline' : 'block')
                out.write(first ? '!' : '.')
                out
            }
        }

        expect:
        writable.toString() == 'block!'
        writable.length() == 6
        writable.charAt(0) == ('b' as char)
        writable.subSequence(0, 5) == 'block'
        writable instanceof CharSequence
        writable instanceof Writable

        when:
        writable.inline = true
        writable.first = false

        then:
        writable.toString() == 'inline.'
    }

    void 'a failing writable is reported as a json exception'() {
        given:
        JsonOutput.JsonWritable writable = new JsonOutput.JsonWritable() {
            @Override
            Writer writeTo(Writer out) throws IOException {
                throw new IOException('disk full')
            }
        }

        when:
        writable.toString()

        then:
        JsonException e = thrown()
        e.message == 'Error writing JSON writable: disk full'
        e.cause instanceof IOException
    }

}

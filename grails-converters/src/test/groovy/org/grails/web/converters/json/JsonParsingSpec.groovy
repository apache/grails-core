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
package org.grails.web.converters.json

import spock.lang.Specification

import grails.converters.JSON
import org.grails.web.converters.exceptions.ConverterException
import org.grails.web.json.JSONArray
import org.grails.web.json.JSONObject

/**
 * {@code JSON.parse}, and so {@code request.JSON}, parse with Grails' own {@code JSONTokener}, which writing JSON through
 * Jackson does not change: the lenient syntax it accepts, the number types it returns and its {@code Map} and
 * {@code List} containers.
 */
class JsonParsingSpec extends Specification {

    void "JSON.parse accepts #syntax"() {
        expect:
        JSON.parse(text) == expected

        where:
        syntax                             | text                   || expected
        'single quotes'                    | "{'x':'a'}"            || [x: 'a']
        '// comments'                      | '{"x":1 // note\n}'    || [x: 1]
        '/* */ comments'                   | '{"x":1 /* note */}'   || [x: 1]
        '# comments'                       | '{"x":1 # note\n}'     || [x: 1]
        'an unquoted string value'         | '{x:hello}'            || [x: 'hello']
        'uppercase literals'               | '{"x":TRUE}'           || [x: true]
        '=> and ; separators'              | '{x=>1;y:2}'           || [x: 1, y: 2]
        'a trailing comma in an object'    | '{"x":1,}'             || [x: 1]
        'a trailing comma in an array'     | '[1,]'                 || [1]
        'a missing array element, as null' | '[1,,2]'               || [1, null, 2]
        'an octal number'                  | '{"x":010}'            || [x: 8]
        'a numeric key, as a string'       | '{12:3}'               || ['12': 3]
        'a \\x escape'                     | '{"x":"\\x41"}'        || [x: 'A']
        'a JavaScript date'                | '{"x":new Date(0)}'    || [x: new Date(0)]
        'duplicate keys, the last winning' | '{"x":1,"x":2}'        || [x: 2]
        'content after the value'          | '{"x":1} trailing'     || [x: 1]
    }

    void "JSON.parse rejects a line break inside a string"() {
        when:
        JSON.parse('{"x":"a\nb"}')

        then:
        thrown(ConverterException)
    }

    void "JSON.parse reads #number as a #type.simpleName"() {
        when:
        def value = JSON.parse("{\"n\":${number}}").n

        then:
        value.getClass() == type
        value.toString() == text

        where:
        number                    || type       | text
        '42'                      || Integer    | '42'
        '9223372036854775807'     || Long       | '9223372036854775807'
        '9223372036854775808'     || BigInteger | '9223372036854775808'
        '1.5'                     || Double     | '1.5'
        '0.123456789012345678901' || BigDecimal | '0.123456789012345678901'
        '1.00'                    || BigDecimal | '1.00'
        '1e3'                     || BigDecimal | '1E+3'
    }

    void "JSON.parse returns Grails' Map and List containers, with Java nulls"() {
        when:
        def json = JSON.parse('{"list":[1,{"nested":null}],"value":null}')

        then:
        json instanceof JSONObject
        json instanceof Map
        json.list instanceof JSONArray
        json.list instanceof List
        json.list[1] instanceof JSONObject
        json.list[1].nested == null
        json.value == null
    }
}

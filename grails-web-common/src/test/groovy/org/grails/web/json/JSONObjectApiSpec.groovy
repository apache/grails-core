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

import groovy.transform.CompileStatic
import spock.lang.Specification

class JSONObjectApiSpec extends Specification {

    void "parse nested values and coerce typed properties"() {
        given:
        def json = new JSONObject('{"array":[1,true,null],"object":{"name":"Grails"},"number":12,"decimal":"2.5","yes":"TRUE","no":"false","bool":false,"nil":null}')

        expect:
        json.getJSONArray('array').getInt(0) == 1
        json.getJSONArray('array').getBoolean(1)
        json.getJSONArray('array').isNull(2)
        json.getJSONObject('object').getString('name') == 'Grails'
        json.getInt('number') == 12
        json.getLong('number') == 12L
        json.getDouble('number') == 12d
        json.getInt('decimal') == 2
        json.getLong('decimal') == 2L
        json.getDouble('decimal') == 2.5d
        json.getString('number') == '12'
        json.getBoolean('yes')
        !json.getBoolean('no')
        !json.getBoolean('bool')
        json.has('nil')
        json.get('nil') == null
        json.isNull('nil')
        json.isNull('missing')
        json.opt(null) == null
        json.optJSONArray('array').is(json.getJSONArray('array'))
        json.optJSONObject('object').is(json.getJSONObject('object'))
        json.optJSONArray('number') == null
        json.optJSONObject('missing') == null
        json.optBoolean('yes')
        !json.optBoolean('bool', true)
        json.optDouble('number') == 12d
        json.optDouble('decimal', 99d) == 2.5d
        json.optInt('decimal') == 2
        json.optLong('number') == 12L
        json.optLong('decimal', 99L) == 2L
        json.optString('number') == '12'
        json.length() == 8
    }

    void "optional getters use defaults for missing null and incompatible values"() {
        given:
        def json = new JSONObject([bad: [:], nil: null])

        expect:
        !json.optBoolean(key)
        json.optBoolean(key, true)
        Double.isNaN(json.optDouble(key))
        json.optDouble(key, 3.5d) == 3.5d
        json.optInt(key) == 0
        json.optInt(key, 7) == 7
        json.optLong(key) == 0L
        json.optLong(key, 9L) == 9L
        json.optJSONArray(key) == null
        json.optJSONObject(key) == null

        where:
        key << ['missing', 'nil', 'bad']
    }

    void "optional strings distinguish a value from absent data"() {
        expect:
        new JSONObject().optString('missing') == ''
        new JSONObject().optString('missing', 'fallback') == 'fallback'
        new JSONObject([nil: null]).optString('nil', 'fallback') == 'fallback'
        new JSONObject([text: 'value']).optString('text', 'fallback') == 'value'
    }

    void "required getters reject #method with #key"() {
        given:
        def json = new JSONObject([bad: 'not a number'])

        when:
        json."$method"(key)

        then:
        def error = thrown(JSONException)
        error.message.contains(message)

        where:
        method          | key       | message
        'get'           | 'missing' | 'not found'
        'getBoolean'    | 'bad'     | 'not a Boolean'
        'getDouble'     | 'bad'     | 'not a number'
        'getInt'        | 'bad'     | 'not a number'
        'getLong'       | 'bad'     | 'not a number'
        'getJSONArray'  | 'bad'     | 'not a JSONArray'
        'getJSONObject' | 'bad'     | 'not a JSONObject'
        'getString'     | 'missing' | 'not found'
    }

    void "put overloads accumulate values and copy selected names"() {
        given:
        def json = new JSONObject()

        when:
        putTypedValues(json)
        json.accumulate('items', 'first').accumulate('items', 'second').accumulate('items', 'third')
        json.putOpt(null, 'ignored').putOpt('ignored', null).putOpt('optional', 'present')
        def copy = new JSONObject(json, ['text', 'long', 'missing'] as String[])
        def values = json.toJSONArray(new JSONArray(['long', 'text', 'missing']))
        json.put('text', (Object) null)

        then:
        json.getBoolean('boolean')
        !json.getBoolean('false')
        json.getDouble('double') == 2.5d
        json.getInt('int') == 3
        json.getLong('long') == 4000000000L
        json.getJSONArray('items').toString() == '["first","second","third"]'
        json.getString('optional') == 'present'
        !json.has('ignored')
        !json.has('text')
        copy == new JSONObject([text: 'value', long: 4000000000L])
        values.toString() == '[4000000000,"value",null]'
        json.toJSONArray(null) == null
        json.toJSONArray(new JSONArray()) == null
        json.remove('optional') == 'present'
        json.remove('missing') == null
    }

    void "put and accumulation reject invalid numbers"() {
        when:
        new JSONObject()."$method"('number', value)

        then:
        thrown(JSONException)

        where:
        method       | value
        'put'        | Double.NaN
        'put'        | Double.POSITIVE_INFINITY
        'put'        | Float.NaN
        'put'        | Float.NEGATIVE_INFINITY
        'putOpt'     | Double.NaN
        'accumulate' | Double.NaN
    }

    void "put rejects a null key"() {
        when:
        new JSONObject().put((String) null, (Object) 'value')

        then:
        def error = thrown(JSONException)
        error.message == 'Null key.'
    }

    void "map operations expose entries and preserve equality"() {
        given:
        def json = new JSONObject([one: 1, two: 2])
        def equal = new JSONObject([one: 1, two: 2])

        expect:
        json.size() == 2
        !json.isEmpty()
        json.containsKey('one')
        json.containsValue(2)
        json.keys().toList().toSet() == ['one', 'two'] as Set
        json.names().toArray().toList().toSet() == ['one', 'two'] as Set
        json.keySet() == ['one', 'two'] as Set
        json.values().toSet() == [1, 2] as Set
        json.entrySet().collectEntries { [it.key, it.value] } == [one: 1, two: 2]
        objectsEqual(json, json)
        objectsEqual(json, equal)
        json.hashCode() == equal.hashCode()
        !objectsEqual(json, new JSONObject([one: 3]))
        !objectsEqual(json, [one: 1, two: 2])
        !objectsEqual(json, null)

        when:
        def previous = putMapValue(json, 'one', 3)
        json.putAll([three: 4])

        then:
        previous == 1
        getMapValue(json, 'one') == 3
        getMapValue(json, 'absent') == null
        removeMapValue(json, 'two') == 2

        when:
        json.clear()

        then:
        json.isEmpty()
        json.names() == null
        json.toString() == '{}'
        json.toString(2) == '{}'
    }

    void "quote escapes JSON control characters and HTML closing tags"() {
        expect:
        JSONObject.quote(null) == '""'
        JSONObject.quote('') == '""'
        JSONObject.quote('"\\/</\b\t\n\f\r' + ((char) 1)) == '"\\"\\\\/<\\/\\b\\t\\n\\f\\r\\u0001"'
        JSONObject.quote('Grails é') == '"Grails é"'
        JSONObject.numberToString(12.000G) == '12'
        JSONObject.numberToString(12.340G) == '12.34'
        JSONObject.numberToString(1.2e20d) == '1.2E20'
        JSONObject.dateToString(new Date(123L)) == 'new Date(123)'
        JSONObject.collectionToString([true, null, 'text', [1, 2]]) == '[true,null,"text",[1,2]]'
        JSONObject.collectionToString([]) == '[]'
    }

    void "number formatting rejects null and nonfinite values"() {
        when:
        JSONObject.numberToString(value)

        then:
        thrown(JSONException)

        where:
        value << [null, Double.NaN, Double.NEGATIVE_INFINITY, Float.NaN, Float.POSITIVE_INFINITY]
    }

    void "serialize nested objects through compact pretty and writable APIs"() {
        given:
        def json = new JSONObject([nested: new JSONObject([name: 'Grails']), array: new JSONArray([true, null, 2.5d]), text: 'line\nquote"'])
        def output = new StringWriter()
        def writableOutput = new StringWriter()

        expect:
        json.write(output).is(output)
        json.writeTo(writableOutput).is(writableOutput)
        new JSONObject(output.toString()) == json
        new JSONObject(writableOutput.toString()) == json
        new JSONObject(json.toString()) == json
        new JSONObject(json.toString(2)) == json
        json.toString(2).contains('\n  "')
        new JSONObject([single: true]).toString(2) == '{"single": true}'
        new JSONObject([date: new Date(123L)]).toString() == '{"date":new Date(123)}'
        new JSONObject([date: new Date(123L)]).toString(2) == '{"date": new Date(123)}'
        new JSONObject([nil: null]).toString(2) == '{"nil": null}'
    }

    void "invalid map values make compact serialization fail and pretty serialization throw"() {
        given:
        def json = new JSONObject([invalid: Double.NaN])

        expect:
        json.toString() == null

        when:
        json.toString(2)

        then:
        thrown(JSONException)
    }

    void "writing wraps output failures"() {
        given:
        def output = Stub(Writer) {
            write(_) >> { throw new IOException('broken output') }
        }

        when:
        new JSONObject([value: true]).write(output)

        then:
        def error = thrown(JSONException)
        error.cause instanceof IOException
        error.message == 'broken output'
    }

    void "tokener constructor accepts legacy separators and empty objects"() {
        expect:
        new JSONObject(new JSONTokener('{a=1;b=>2,c:3,}')) == new JSONObject([a: 1, b: 2, c: 3])
        new JSONObject(new JSONTokener('{}')).isEmpty()
    }

    void "malformed object text reports #message"() {
        when:
        new JSONObject(text)

        then:
        def error = thrown(JSONException)
        error.message.contains(message)

        where:
        text       | message
        '[]'       | "must begin with '{'"
        '{'        | "must end with '}'"
        '{a!1}'    | "Expected a ':'"
        '{a:1:2}'  | "Expected a ',' or '}'"
        '{a:}'     | 'Missing value'
    }

    @CompileStatic
    private static boolean objectsEqual(JSONObject json, Object other) {
        json.equals(other)
    }

    @CompileStatic
    private static void putTypedValues(JSONObject json) {
        json.put('boolean', true)
        json.put('false', false)
        json.put('double', 2.5d)
        json.put('int', 3)
        json.put('long', 4000000000L)
        json.put('text', (Object) 'value')
    }

    @CompileStatic
    private static Object getMapValue(Map json, Object key) {
        json.get(key)
    }

    @CompileStatic
    private static Object putMapValue(Map json, Object key, Object value) {
        json.put(key, value)
    }

    @CompileStatic
    private static Object removeMapValue(Map json, Object key) {
        json.remove(key)
    }
}

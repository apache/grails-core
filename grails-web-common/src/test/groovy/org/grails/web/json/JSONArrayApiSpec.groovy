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

import groovy.transform.CompileStatic
import spock.lang.Specification

class JSONArrayApiSpec extends Specification {

    void "parse nested arrays and coerce typed values"() {
        given:
        def array = new JSONArray('[true,false,"TRUE","false",12,"2.5",[1],{"name":"Grails"},null]')

        expect:
        array.length() == 9
        array.getBoolean(0)
        !array.getBoolean(1)
        array.getBoolean(2)
        !array.getBoolean(3)
        array.getInt(4) == 12
        array.getLong(4) == 12L
        array.getDouble(4) == 12d
        array.getInt(5) == 2
        array.getLong(5) == 2L
        array.getDouble(5) == 2.5d
        array.getString(4) == '12'
        array.getJSONArray(6).getInt(0) == 1
        array.getJSONObject(7).getString('name') == 'Grails'
        array.optJSONArray(6).is(array.getJSONArray(6))
        array.optJSONObject(7).is(array.getJSONObject(7))
        array.optJSONArray(0) == null
        array.optJSONObject(0) == null
        array.isNull(8)
        array.isNull(-1)
        array.opt(-1) == null
        array.opt(99) == null
        array.optBoolean(0)
        !array.optBoolean(1, true)
        array.optDouble(5) == 2.5d
        array.optDouble(4, 99d) == 12d
        array.optInt(4) == 12
        array.optInt(5, 99) == 2
        array.optLong(4) == 12L
        array.optLong(5, 99L) == 2L
        array.optString(4) == '12'
        array.optString(5, 'fallback') == '2.5'
    }

    void "optional array getters use defaults for invalid and missing values"() {
        given:
        def array = new JSONArray(['invalid', null])

        expect:
        !array.optBoolean(index)
        array.optBoolean(index, true)
        Double.isNaN(array.optDouble(index))
        array.optDouble(index, 3.5d) == 3.5d
        array.optInt(index) == 0
        array.optInt(index, 7) == 7
        array.optLong(index) == 0L
        array.optLong(index, 9L) == 9L
        array.optJSONArray(index) == null
        array.optJSONObject(index) == null

        where:
        index << [-1, 0, 1, 2]
    }

    void "optional strings return defaults for null and absent values"() {
        expect:
        new JSONArray([null]).optString(0) == ''
        new JSONArray().optString(1, 'fallback') == 'fallback'
    }

    void "required typed array getters reject #method"() {
        when:
        new JSONArray(['invalid'])."$method"(0)

        then:
        def error = thrown(JSONException)
        error.message.contains(message)

        where:
        method          | message
        'getBoolean'    | 'not a Boolean'
        'getDouble'     | 'not a number'
        'getInt'        | 'not a number'
        'getLong'       | 'not a number'
        'getJSONArray'  | 'not a JSONArray'
        'getJSONObject' | 'not a JSONObject'
    }

    void "get rejects absent and null elements with a JSON cause"() {
        when:
        new JSONArray([null]).get(index)

        then:
        def error = thrown(RuntimeException)
        error.cause instanceof JSONException
        error.cause.message == "JSONArray[$index] not found."

        where:
        index << [-1, 0, 1]
    }

    void "put overloads append replace and pad values"() {
        given:
        def array = new JSONArray()

        when:
        putTypedValues(array)

        then:
        array.toString() == '[false,1.5,2,3,4000000000,"text",null,null,"last"]'
        array.isNull(6)
        array.isNull(7)
        array.join('|') == 'false|1.5|2|3|4000000000|"text"|null|null|"last"'
    }

    void "put rejects negative indices and nonfinite numbers"() {
        when:
        def array = new JSONArray()
        if (indexed) {
            array.put(index, value)
        } else {
            array.put((double) value)
        }

        then:
        thrown(JSONException)

        where:
        indexed | index | value
        true    | -1    | 'text'
        true    | 0     | Double.NaN
        true    | 0     | Float.POSITIVE_INFINITY
        false   | 0     | Double.NaN
        false   | 0     | Double.NEGATIVE_INFINITY
    }

    void "list operations mutate and expose the underlying sequence"() {
        given:
        def array = new JSONArray(['a', 'b', 'a'])

        expect:
        array.size() == 3
        !array.isEmpty()
        array.contains('a')
        array.indexOf('a') == 0
        array.lastIndexOf('a') == 2
        array.containsAll(['a', 'b'])
        array.iterator().toList() == ['a', 'b', 'a']
        array.listIterator().next() == 'a'
        array.listIterator(1).next() == 'b'
        array.subList(1, 3) == ['b', 'a']
        array.toArray().toList() == ['a', 'b', 'a']
        array.toArray(new String[0]).toList() == ['a', 'b', 'a']
        cloneArray(array) == ['a', 'b', 'a']
        arraysEqual(array, array)
        arraysEqual(array, new JSONArray(['a', 'b', 'a']))
        array.hashCode() == new JSONArray(['a', 'b', 'a']).hashCode()
        !arraysEqual(array, new JSONArray(['b']))
        !arraysEqual(array, ['a', 'b', 'a'])
        !arraysEqual(array, null)

        when:
        array.ensureCapacity(20)
        array.trimToSize()
        def previous = array.set(1, 'c')
        array.add(1, 'inserted')

        then:
        previous == 'b'
        array.add('d')
        array.addAll(['e', 'f'])
        array.addAll(0, ['start'])
        array.remove(0) == 'start'
        array.remove((Object) 'inserted')
        array.removeAll(['e', 'f'])
        array.retainAll(['a', 'c'])
        array.toString() == '["a","c","a"]'

        when:
        array.clear()

        then:
        array.isEmpty()
        array.join(',') == ''
        array.toString() == '[]'
        array.toString(2) == '[]'
    }

    void "convert names and values into an object"() {
        given:
        def array = new JSONArray([1, 'text'])

        expect:
        array.toJSONObject(new JSONArray(['number', 'name', 'missing'])) == new JSONObject([number: 1, name: 'text'])
        array.toJSONObject(null) == null
        array.toJSONObject(new JSONArray()) == null
        new JSONArray().toJSONObject(new JSONArray(['name'])) == null

        when:
        array.toJSONObject(new JSONArray([null]))

        then:
        thrown(RuntimeException)
    }

    void "serialize nested structures through compact pretty and writable APIs"() {
        given:
        def array = new JSONArray([new JSONObject([name: 'Grails']), new JSONArray([true, null]), 2.5d, 'line\n'])
        def output = new StringWriter()
        def writableOutput = new StringWriter()

        expect:
        array.write(output).is(output)
        array.writeTo(writableOutput).is(writableOutput)
        new JSONArray(output.toString()) == array
        new JSONArray(writableOutput.toString()) == array
        new JSONArray(array.toString()) == array
        new JSONArray(array.toString(2)) == array
        array.toString(2).contains('\n  {')
        new JSONArray([true]).toString(2) == '[true]'
        new JSONArray([null]).toString(2) == '[null]'
    }

    void "invalid collection numbers fail serialization"() {
        given:
        def array = new JSONArray([Double.NaN])

        expect:
        array.toString() == null

        when:
        array.toString(2)

        then:
        thrown(JSONException)
    }

    void "writing wraps output failures"() {
        given:
        def output = Stub(Writer) {
            write(_) >> { throw new IOException('broken output') }
        }

        when:
        new JSONArray([true]).write(output)

        then:
        def error = thrown(JSONException)
        error.cause instanceof IOException
        error.message == 'broken output'
    }

    void "tokener constructor handles empty arrays elisions and legacy separators"() {
        expect:
        new JSONArray(new JSONTokener('[]')).isEmpty()
        new JSONArray(new JSONTokener('[,1;2,]')).toString() == '[null,1,2]'
    }

    void "malformed array text reports #message"() {
        when:
        new JSONArray(text)

        then:
        def error = thrown(JSONException)
        error.message.contains(message)

        where:
        text    | message
        '{}'    | "must start with '['"
        '[1:2]' | "Expected a ',' or ']'"
        '[}'    | 'Missing value'
    }

    @CompileStatic
    private static boolean arraysEqual(JSONArray array, Object other) {
        array.equals(other)
    }

    @CompileStatic
    private static void putTypedValues(JSONArray array) {
        array.put(true)
        array.put(false)
        array.put(2.5d)
        array.put(3)
        array.put(4000000000L)
        array.put((Object) 'text')
        array.put(0, false)
        array.put(1, 1.5d)
        array.put(2, 2)
        array.put(3, 3L)
        array.put(8, (Object) 'last')
    }

    @CompileStatic
    private static Object cloneArray(JSONArray array) {
        array.clone()
    }
}

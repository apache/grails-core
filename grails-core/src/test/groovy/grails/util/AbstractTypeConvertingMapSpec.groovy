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
package grails.util

import spock.lang.Specification

class AbstractTypeConvertingMapSpec extends Specification {

    private static TypeConvertingMap mapOf(Map values) {
        new TypeConvertingMap(values)
    }

    void 'typed getters convert stored values to the requested type'() {
        given:
        def map = mapOf(byteValue: '5', charValue: 'A', intValue: '42', longValue: '99',
                shortValue: '3', doubleValue: '1.5', floatValue: '2.5', boolValue: 'true')

        expect:
        map.getByte('byteValue') == (byte) 5
        map.getChar('charValue') == 'A' as char
        map.getInt('intValue') == 42
        map.getLong('longValue') == 99L
        map.getShort('shortValue') == (short) 3
        map.getDouble('doubleValue') == 1.5d
        map.getFloat('floatValue') == 2.5f
        map.getBoolean('boolValue')
    }

    void 'typed getters return null for an absent key when no default is supplied'() {
        given:
        def map = mapOf([:])

        expect:
        map.getByte('missing') == null
        map.getInt('missing') == null
        map.getLong('missing') == null
        map.getDouble('missing') == null
    }

    void 'getBoolean with a default returns the default only when the key is entirely absent'() {
        given:
        def map = mapOf(flag: 'false')

        expect:
        map.getBoolean('flag', true) == false
        map.getBoolean('missing', true) == true
    }

    void 'getList wraps a single value and flattens an existing collection'() {
        given:
        def map = mapOf(single: 'a', many: ['a', 'b'])

        expect:
        map.getList('single') == ['a']
        map.list('single') == ['a']
        map.getList('many') == ['a', 'b']
    }

    void 'getDate parses using the default format, an explicit format, and a list of candidate formats'() {
        given:
        def map = mapOf(defaultFormat: '2024-01-15 10:30:00.0', custom: '15/01/2024')

        expect:
        map.getDate('defaultFormat') != null
        map.date('custom', 'dd/MM/yyyy') != null
        map.date('custom', ['yyyy-MM-dd', 'dd/MM/yyyy']) != null
        map.getDate('missing') == null
    }

    void 'asBoolean reflects emptiness'() {
        expect:
        !mapOf([:]).asBoolean()
        mapOf(a: 1).asBoolean()
    }
}

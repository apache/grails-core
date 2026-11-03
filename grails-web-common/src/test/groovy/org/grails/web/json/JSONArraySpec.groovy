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

import spock.lang.Specification

class JSONArraySpec extends Specification {

    void 'add and get round-trip a value'() {
        given:
        def array = new JSONArray()

        when:
        array.add('one')
        array.add('two')

        then:
        array.get(0) == 'one'
        array.get(1) == 'two'
        array.size() == 2
    }

    void 'equals is true for the same instance'() {
        given:
        def array = new JSONArray([1, 2, 3])

        expect:
        array == array
    }

    void 'equals is true for two arrays with the same elements'() {
        given:
        def a = new JSONArray([1, 2, 3])
        def b = new JSONArray([1, 2, 3])

        expect:
        a == b
        a.hashCode() == b.hashCode()
    }

    void 'equals is false for arrays with different elements'() {
        given:
        def a = new JSONArray([1, 2, 3])
        def b = new JSONArray([1, 2])

        expect:
        a != b
    }

    void 'equals is false when compared to null or a different type'() {
        given:
        def array = new JSONArray([1, 2])

        expect:
        array != null
        array != 'a string'
    }

    void 'constructing from a Collection copies its elements'() {
        given:
        def source = ['x', 'y']

        when:
        def array = new JSONArray(source)

        then:
        array.size() == 2
        array.get(0) == 'x'
        array.get(1) == 'y'
    }

}

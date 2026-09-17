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

class JSONObjectSpec extends Specification {

    void 'put and get round-trip a value'() {
        given:
        def json = new JSONObject()

        when:
        json.put('name', 'Bob')

        then:
        json.get('name') == 'Bob'
        json.has('name')
        json.length() == 1
    }

    void 'equals is true for the same instance'() {
        given:
        def json = new JSONObject().put('a', 1)

        expect:
        json == json
    }

    void 'equals is true for two objects with the same entries'() {
        given:
        def a = new JSONObject().put('a', 1).put('b', 2)
        def b = new JSONObject().put('a', 1).put('b', 2)

        expect:
        a == b
        a.hashCode() == b.hashCode()
    }

    void 'equals is false for objects with different entries'() {
        given:
        def a = new JSONObject().put('a', 1)
        def b = new JSONObject().put('a', 2)

        expect:
        a != b
    }

    void 'equals is false when compared to null or a different type'() {
        given:
        def json = new JSONObject().put('a', 1)

        expect:
        json != null
        json != 'a string'
    }

    void 'quote wraps a string in double quotes'() {
        expect:
        JSONObject.quote(null) == '""'
        JSONObject.quote('') == '""'
        JSONObject.quote('hi') == '"hi"'
    }

    void 'constructing from a Map copies its entries'() {
        given:
        def source = [a: 1, b: 2]

        when:
        def json = new JSONObject(source)

        then:
        json.get('a') == 1
        json.get('b') == 2
    }

}

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

package org.grails.gorm.graphql.types

import spock.lang.Specification

class KeyClassQuerySpec extends Specification implements KeyClassQuery<String> {

    void "test searchMap finds an exact match"() {
        given:
        Map<Class, String> map = [(Integer): 'integer', (Number): 'number']

        expect:
        searchMap(map, Integer) == 'integer'
    }

    void "test searchMap finds the most specific super class match in reverse order"() {
        given:
        Map<Class, String> map = [(Number): 'number', (Serializable): 'serializable']

        expect:
        searchMap(map, Integer) == 'serializable'
    }

    void "test searchMap can search in forward order"() {
        given:
        Map<Class, String> map = [(Number): 'number', (Serializable): 'serializable']

        expect:
        searchMap(map, Integer, false) == 'number'
    }

    void "test searchMap returns null when no match is found"() {
        given:
        Map<Class, String> map = [(String): 'string']

        expect:
        searchMap(map, Integer) == null
    }

    void "test searchMapAll returns all matching super class values"() {
        given:
        Map<Class, String> map = [(Number): 'number', (Serializable): 'serializable', (String): 'string']

        expect:
        searchMapAll(map, Integer) as Set == ['number', 'serializable'] as Set
    }

    void "test searchMapAll flattens collection values"() {
        given:
        Map<Class, Object> map = [(Number): ['a', 'b']]

        expect:
        searchMapAll((Map) map, Integer) == ['a', 'b']
    }

    void "test searchMapAll returns an empty list when no match is found"() {
        given:
        Map<Class, String> map = [(String): 'string']

        expect:
        searchMapAll(map, Integer) == []
    }
}

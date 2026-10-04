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

class CollectionUtilsSpec extends Specification {

    void 'newMap builds a map from alternating keys and values'() {
        expect:
        CollectionUtils.newMap('a', 1, 'b', 2) == [a: 1, b: 2]
    }

    void 'newMap returns an empty map for null input'() {
        expect:
        CollectionUtils.newMap((Object[]) null) == [:]
    }

    void 'newMap rejects an odd number of arguments'() {
        when:
        CollectionUtils.newMap('a', 1, 'b')

        then:
        thrown(IllegalArgumentException)
    }

    void 'newSet builds a set from varargs'() {
        expect:
        CollectionUtils.newSet('a', 'b', 'a') == ['a', 'b'] as Set
    }

    void 'newSet returns an empty set for null input'() {
        expect:
        CollectionUtils.newSet((Object[]) null).isEmpty()
    }

    void 'newList builds a list from varargs'() {
        expect:
        CollectionUtils.newList('a', 'b') == ['a', 'b']
    }

    void 'newList returns an empty list for null input'() {
        expect:
        CollectionUtils.newList((Object[]) null).isEmpty()
    }

    void 'getOrCreateChildMap returns the existing child map'() {
        given:
        Map child = [x: 1]
        Map parent = [foo: child]

        expect:
        CollectionUtils.getOrCreateChildMap(parent, 'foo').is(child)
    }

    void 'getOrCreateChildMap returns a new empty map when the key is absent'() {
        given:
        Map parent = [:]

        expect:
        CollectionUtils.getOrCreateChildMap(parent, 'missing') == [:]
    }

    void 'getOrCreateChildMap returns a new empty map when the value is not a map'() {
        given:
        Map parent = [foo: 'not a map']

        expect:
        CollectionUtils.getOrCreateChildMap(parent, 'foo') == [:]
    }

}

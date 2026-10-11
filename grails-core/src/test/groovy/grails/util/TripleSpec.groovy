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

class TripleSpec extends Specification {

    void 'getters expose the constructor values'() {
        given:
        Triple<String, Integer, Boolean> triple = new Triple<>('a', 1, true)

        expect:
        triple.getaValue() == 'a'
        triple.getbValue() == 1
        triple.getcValue() == true
    }

    void 'two triples with equal values are equal and share a hash code'() {
        expect:
        new Triple<>('a', 1, true) == new Triple<>('a', 1, true)
        new Triple<>('a', 1, true).hashCode() == new Triple<>('a', 1, true).hashCode()
    }

    void 'triples differing in any value are not equal'() {
        expect:
        new Triple<>('a', 1, true) != new Triple<>('b', 1, true)
        new Triple<>('a', 1, true) != new Triple<>('a', 2, true)
        new Triple<>('a', 1, true) != new Triple<>('a', 1, false)
    }

    void 'null components are tolerated by equals'() {
        expect:
        new Triple<>(null, 1, true) == new Triple<>(null, 1, true)
        new Triple<>(null, 1, true) != new Triple<>('a', 1, true)
    }

    void 'is not equal to null or another type'() {
        given:
        Triple<String, Integer, Boolean> triple = new Triple<>('a', 1, true)

        expect:
        triple != null
        triple != 'a'
    }

    void 'toString describes all three values'() {
        expect:
        new Triple<>('a', 1, true).toString() == 'Triple [aValue=a, bValue=1, cValue=true]'
    }
}

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

class PairSpec extends Specification {

    void 'getters expose the constructor values'() {
        given:
        Pair<String, Integer> pair = new Pair<>('a', 1)

        expect:
        pair.getaValue() == 'a'
        pair.getbValue() == 1
    }

    void 'two pairs with equal values are equal and share a hash code'() {
        expect:
        new Pair<>('a', 1) == new Pair<>('a', 1)
        new Pair<>('a', 1).hashCode() == new Pair<>('a', 1).hashCode()
    }

    void 'pairs differing in either value are not equal'() {
        expect:
        new Pair<>('a', 1) != new Pair<>('b', 1)
        new Pair<>('a', 1) != new Pair<>('a', 2)
    }

    void 'null components are tolerated by equals'() {
        expect:
        new Pair<>(null, 1) == new Pair<>(null, 1)
        new Pair<>(null, 1) != new Pair<>('a', 1)
        new Pair<>('a', 1) != new Pair<>(null, 1)
    }

    void 'is not equal to null or another type'() {
        given:
        Pair<String, Integer> pair = new Pair<>('a', 1)

        expect:
        pair != null
        pair != 'a'
    }

    void 'toString describes both values'() {
        expect:
        new Pair<>('a', 1).toString() == 'TupleKey [aValue=a, bValue=1]'
    }
}

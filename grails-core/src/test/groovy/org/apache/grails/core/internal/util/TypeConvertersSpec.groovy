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
package org.apache.grails.core.internal.util

import spock.lang.Specification

class TypeConvertersSpec extends Specification {

    void 'toList of null is an empty list'() {
        expect:
        TypeConverters.toList(null) == []
    }

    void 'toList of an object array lists its elements'() {
        expect:
        TypeConverters.toList(['a', 'b'] as String[]) == ['a', 'b']
        TypeConverters.toList([1, 2] as Integer[]) == [1, 2]
    }

    void 'toList of a primitive array lists its boxed elements'() {
        // The Java original threw ClassCastException here. Groovy coerces the primitive array
        // when it is cast to Object[], and being lenient is the better behaviour for a helper
        // that normalises request parameter values, so the difference is kept on purpose.
        expect:
        TypeConverters.toList([1, 2] as int[]) == [1, 2]
        TypeConverters.toList([true, false] as boolean[]) == [true, false]
    }

    void 'toList of a collection copies it'() {
        given:
        Set<String> source = ['a', 'b'] as LinkedHashSet

        when:
        List<Object> list = TypeConverters.toList(source)
        source.add('c')

        then:
        list == ['a', 'b']
    }

    void 'toList of a single value wraps it'() {
        expect:
        TypeConverters.toList('only') == ['only']
        TypeConverters.toList(42) == [42]
    }

}

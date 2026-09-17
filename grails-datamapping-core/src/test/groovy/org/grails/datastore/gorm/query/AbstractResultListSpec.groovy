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
package org.grails.datastore.gorm.query

import spock.lang.Specification

class AbstractResultListSpec extends Specification {

    void "test isEmpty is true for an empty cursor before and after initialization"() {
        given:
        def list = new StringResultList(0, [].iterator())

        expect:
        list.isEmpty()

        when:
        list.initializeFully()

        then:
        list.isEmpty()
    }

    void "test isEmpty is false once the cursor has produced an element"() {
        given:
        def list = new StringResultList(0, ['a'].iterator())

        expect:
        !list.isEmpty()
    }

    void "test get returns elements pulled from the cursor for ascending indices"() {
        given:
        def list = new StringResultList(0, ['a', 'b', 'c'].iterator())

        expect:
        list.get(0) == 'a'
        list.get(1) == 'b'
        list.get(2) == 'c'
    }

    void "test size initializes fully when the size hint was not provided"() {
        given:
        def list = new StringResultList(0, ['a', 'b', 'c'].iterator())

        expect:
        list.size() == 3
        list.initialized
    }

    void "test size uses the provided hint without exhausting the cursor"() {
        given:
        def cursor = ['a', 'b'].iterator()
        def list = new StringResultList(0, 2, cursor)

        expect:
        list.size() == 2
        !list.initialized
    }

    void "test iterator yields all elements in order and marks the list initialized"() {
        given:
        def list = new StringResultList(0, ['a', 'b', 'c'].iterator())

        when:
        def result = list.iterator().toList()

        then:
        result == ['a', 'b', 'c']
        list.initialized
    }

    void "test iterator can be consumed a second time once fully initialized"() {
        given:
        def list = new StringResultList(0, ['a', 'b'].iterator())
        list.iterator().toList()

        expect:
        list.iterator().toList() == ['a', 'b']
    }

    void "test convertObject transforms elements produced by the cursor"() {
        given:
        def list = new UpperCasingResultList(0, ['a', 'b'].iterator())

        expect:
        list.get(0) == 'A'
        list.get(1) == 'B'
    }

    void "test set replaces an already-initialized element and returns the previous value"() {
        given:
        def list = new StringResultList(0, ['a', 'b'].iterator())

        when:
        def previous = list.set(0, 'z')

        then:
        previous == 'a'
        list.get(0) == 'z'
    }

    void "test add initializes fully and inserts the element at the given index"() {
        given:
        def list = new StringResultList(0, ['a', 'b'].iterator())

        when:
        list.add(1, 'x')

        then:
        list.initialized
        list.toList() == ['a', 'x', 'b']
    }

    void "test remove initializes fully and removes the element at the given index"() {
        given:
        def list = new StringResultList(0, ['a', 'b', 'c'].iterator())

        when:
        def removed = list.remove(1)

        then:
        removed == 'b'
        list.toList() == ['a', 'c']
    }

    void "test listIterator initializes fully and iterates from the given index"() {
        given:
        def list = new StringResultList(0, ['a', 'b', 'c'].iterator())

        when:
        def iterator = list.listIterator(1)

        then:
        list.initialized
        iterator.next() == 'b'
    }

    void "test getCursor returns the underlying cursor"() {
        given:
        def cursor = ['a'].iterator()
        def list = new StringResultList(0, cursor)

        expect:
        list.cursor.is(cursor)
    }

    static class StringResultList extends AbstractResultList {

        private final Iterator<String> source

        StringResultList(int offset, Iterator<String> source) {
            super(offset, source as Iterator<Object>)
            this.source = source
        }

        StringResultList(int offset, Integer size, Iterator<String> source) {
            super(offset, size, source as Iterator<Object>)
            this.source = source
        }

        @Override
        protected Object nextDecoded() {
            return source.next()
        }

        @Override
        void close() {
        }
    }

    static class UpperCasingResultList extends AbstractResultList {

        private final Iterator<String> source

        UpperCasingResultList(int offset, Iterator<String> source) {
            super(offset, source as Iterator<Object>)
            this.source = source
        }

        @Override
        protected Object nextDecoded() {
            return source.next()
        }

        @Override
        protected Object convertObject(Object o) {
            return ((String) o).toUpperCase()
        }

        @Override
        void close() {
        }
    }
}

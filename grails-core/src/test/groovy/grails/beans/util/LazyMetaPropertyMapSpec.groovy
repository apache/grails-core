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
package grails.beans.util

import spock.lang.Specification

class LazyMetaPropertyMapSpec extends Specification {

    void 'get reads a property value lazily via the metaclass'() {
        given:
        def bean = new LazyBean(name: 'Bob', age: 42)
        def map = new LazyMetaPropertyMap(bean)

        expect:
        map.get('name') == 'Bob'
        map.get('age') == 42
        map.get('name') == bean.name
    }

    void 'get accepts a GString-like CharSequence key'() {
        given:
        def bean = new LazyBean(name: 'Bob')
        def map = new LazyMetaPropertyMap(bean)
        String suffix = 'name'

        expect:
        map.get("$suffix") == 'Bob'
    }

    void 'get with a List of names returns a submap of the resolvable ones'() {
        given:
        def bean = new LazyBean(name: 'Bob', age: 42)
        def map = new LazyMetaPropertyMap(bean)

        when:
        def result = map.get(['name', 'age', 'missingProperty'])

        then:
        result == [name: 'Bob', age: 42]
    }

    void 'put writes through to the wrapped instance and returns the previous value'() {
        given:
        def bean = new LazyBean(name: 'Bob')
        def map = new LazyMetaPropertyMap(bean)

        when:
        def previous = map.put('name', 'Alice')

        then:
        previous == 'Bob'
        bean.name == 'Alice'
    }

    void 'containsKey is true for a real, non-configurational property and false otherwise'() {
        given:
        def bean = new LazyBean(name: 'Bob')
        def map = new LazyMetaPropertyMap(bean)

        expect:
        map.containsKey('name')
        !map.containsKey('doesNotExist')
        !map.containsKey('class')
    }

    void 'keySet excludes configurational and static properties'() {
        given:
        def bean = new LazyBean(name: 'Bob', age: 42)
        def map = new LazyMetaPropertyMap(bean)

        expect:
        map.keySet().containsAll(['name', 'age'])
        !map.keySet().contains('class')
        !map.keySet().contains('properties')
    }

    void 'isEmpty always returns false'() {
        expect:
        !new LazyMetaPropertyMap(new LazyBean()).isEmpty()
    }

    void 'remove and clear are unsupported'() {
        given:
        def map = new LazyMetaPropertyMap(new LazyBean())

        when:
        map.remove('name')

        then:
        thrown(UnsupportedOperationException)

        when:
        map.clear()

        then:
        thrown(UnsupportedOperationException)
    }

    void 'equals and hashCode compare by the wrapped instance'() {
        // LazyMetaPropertyMap implements Map, so Groovy's own dynamic .equals()/== dispatch
        // for a Map-typed receiver routes through Map-content comparison rather than reaching
        // this class's equals(Object) override when two maps happen to have equal content
        // (pre-existing Groovy behavior, unrelated to source language) - invoke the declared
        // method via reflection to exercise the real wrapped-instance-identity comparison.
        given:
        def bean = new LazyBean(name: 'Bob')
        def a = new LazyMetaPropertyMap(bean)
        def b = new LazyMetaPropertyMap(bean)
        def c = new LazyMetaPropertyMap(new LazyBean(name: 'Bob'))
        def equalsMethod = LazyMetaPropertyMap.getMethod('equals', Object)

        expect:
        a.hashCode() == b.hashCode()
        equalsMethod.invoke(a, b)
        !equalsMethod.invoke(a, c)
        !equalsMethod.invoke(a, 'not a map')
    }

    void 'getInstance returns the wrapped object'() {
        given:
        def bean = new LazyBean(name: 'Bob')

        expect:
        new LazyMetaPropertyMap(bean).instance.is(bean)
    }
}

class LazyBean {
    String name
    Integer age
}

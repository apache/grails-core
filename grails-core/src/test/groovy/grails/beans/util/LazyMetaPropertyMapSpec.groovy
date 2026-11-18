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

import groovy.transform.CompileStatic
import spock.lang.Specification

class LazyMetaPropertyMapSpec extends Specification {

    void 'get reads bean properties lazily and supports CharSequence keys and subsets'() {
        given:
        def bean = new TestBean(name: 'Grails', count: 7)
        def map = new LazyMetaPropertyMap(bean)

        expect:
        map.get('name') == 'Grails'
        map.get(new StringBuilder('count')) == 7
        map.get('missing') == null
        map.get('constraints') == null
        map.get(['name', 'count', 'missing', null]) == [name: 'Grails', count: 7]

        when:
        bean.name = 'changed'

        then:
        map.get('name') == 'changed'
        map.getInstance().is(bean)
    }

    void 'put returns the old value and updates the bean'() {
        given:
        def bean = new TestBean(name: 'original', count: 1)
        def map = new LazyMetaPropertyMap(bean)

        when:
        def oldName = map.put(new StringBuilder('name'), 'changed')
        def oldCount = map.put('count', [count: 2])
        map.putAll([name: 'final', count: 3])

        then:
        oldName == 'original'
        oldCount == 1
        bean.name == 'final'
        bean.count == 3
        map.get('name') == 'final'
        map.put('missing', 'ignored') == null
        map.put('id', 99L) == null
        bean.id == 1L
    }

    void 'keys and map views exclude static and configurational properties'() {
        given:
        def map = new LazyMetaPropertyMap(new TestBean(name: 'Grails', count: 7))

        expect:
        map.containsKey('name')
        map.containsKey(new StringBuilder('count'))
        !map.containsKey('missing')
        !map.containsKey('constraints')
        map.keySet() == ['name', 'count'] as Set
        map.size() == 2
        !map.isEmpty()
        map.values().toSet() == ['Grails', 7] as Set
        map.containsValue('Grails')
        !map.containsValue('missing')
        map.entrySet().collectEntries { [it.key, it.value] } == [name: 'Grails', count: 7]
    }

    void 'maps compare and hash using the wrapped bean'() {
        given:
        def bean = new TestBean()
        def map = new LazyMetaPropertyMap(bean)

        expect:
        mapsEqual(map, new LazyMetaPropertyMap(bean))
        map.hashCode() == bean.hashCode()
        !mapsEqual(map, new LazyMetaPropertyMap(new TestBean()))
        !mapsEqual(map, bean)
        !mapsEqual(map, null)
    }

    void 'null beans and non string containsKey arguments are rejected'() {
        when:
        operation()

        then:
        thrown(IllegalArgumentException)

        where:
        operation << [
            { new LazyMetaPropertyMap(null) },
            { new LazyMetaPropertyMap(new TestBean()).containsKey(1) }
        ]
    }

    void 'remove and clear are unsupported'() {
        given:
        def map = new LazyMetaPropertyMap(new TestBean(name: 'Grails'))

        when:
        operation(map)

        then:
        thrown(UnsupportedOperationException)
        map.get('name') == 'Grails'

        where:
        operation << [{ it.remove('name') }, { it.clear() }]
    }

    @CompileStatic
    private static boolean mapsEqual(LazyMetaPropertyMap map, Object other) {
        map.equals(other)
    }

    static class TestBean {
        String name
        int count
        Long id = 1L
        static String constraints = 'configuration'
    }
}

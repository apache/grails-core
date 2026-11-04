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
package org.grails.config

import org.springframework.boot.origin.OriginTrackedValue

import spock.lang.Specification

/**
 * @author graemerocher
 */
class NavigableMapPropertySourceSpec extends Specification {

    def "Ensure navigable maps are not returned from a NavigableMapPropertySource when using map syntax"() {
        given:"A navigable map"
            def map = new NavigableMap()
            map.foo = [bar: "myval"]
        when:"A NavigableMapPropertySource is created"
            def ps = new NavigableMapPropertySource("test", map)
        then:"Nulls are returned for submaps"
        map.keySet() == ['foo.bar', 'foo'] as Set
        ps.getPropertyNames() == ['foo.bar'] as String[]
        ps.getNavigablePropertyNames() == ['foo.bar', 'foo'] as String[]
        ps.getProperty('foo') == null
        ps.getNavigableProperty('foo') instanceof NavigableMap

    }
    def "Ensure navigable maps are not returned from a NavigableMapPropertySource when using dot syntax"() {
        given:"A navigable map"
        def map = new NavigableMap()
        map.foo.bar = "myval"
        when:"A NavigableMapPropertySource is created"
        def ps = new NavigableMapPropertySource("test", map)
        then:"Nulls are returned for submaps"
        map.keySet() == ['foo', 'foo.bar' ] as Set
        ps.getPropertyNames() == ['foo.bar'] as String[]
        ps.getNavigablePropertyNames() == ['foo' , 'foo.bar'] as String[]
        ps.getProperty('foo') == null
        ps.getNavigableProperty('foo') instanceof NavigableMap

    }
    def "Ensure a list of objects is presented element by element"() {
        given: "A navigable map holding a list of objects, as application.groovy declares one"
        def map = new NavigableMap()
        map.merge([app: [rules: [[pattern: '/a', access: ['permitAll']], [pattern: '/b']], names: ['p', 'q']]], false)

        when:
        def ps = new NavigableMapPropertySource("test", map)

        then: "Each element is presented under the indexed names Spring Boot binds"
        ps.getProperty('app.rules[0].pattern') == '/a'
        ps.getProperty('app.rules[0].access[0]') == 'permitAll'
        ps.getProperty('app.rules[1].pattern') == '/b'
        ps.getPropertyNames().toList().containsAll(['app.rules[0].pattern', 'app.rules[0].access[0]', 'app.rules[1].pattern'])

        and: "The list itself is not"
        ps.getProperty('app.rules') == null
        !ps.containsProperty('app.rules')
        !ps.getPropertyNames().contains('app.rules')
        ps.getNavigableProperty('app.rules')*.pattern == ['/a', '/b']

        and: "A list of plain values is also presented element by element"
        ps.getProperty('app.names[0]') == 'p'
        ps.getProperty('app.names[1]') == 'q'
        ps.getProperty('app.names') == null
        !ps.containsProperty('app.names')
        !ps.getPropertyNames().contains('app.names')
        ps.getNavigableProperty('app.names') == ['p', 'q']
    }

    def "Ensure a list holding lists of objects is presented element by element"() {
        given:
        def map = new NavigableMap()
        map.merge([app: [rows: [[[a: 1]], [[b: 2], 'plain']], grid: [[1, 2], [3]], mixed: ['a', [b: 1]]]], false)

        when:
        def ps = new NavigableMapPropertySource("test", map)

        then: "Each object is presented under the indexed names Spring Boot binds"
        ps.getProperty('app.rows[0][0].a') == 1
        ps.getProperty('app.rows[1][0].b') == 2
        ps.getProperty('app.rows[1][1]') == 'plain'
        ps.getProperty('app.mixed[0]') == 'a'
        ps.getProperty('app.mixed[1].b') == 1

        and: "The list itself is not"
        ps.getProperty('app.rows') == null
        !ps.containsProperty('app.rows')
        !ps.containsProperty('app.mixed')

        and: "A list holding lists of plain values is also presented element by element"
        ps.getProperty('app.grid[0][0]') == 1
        ps.getProperty('app.grid[0][1]') == 2
        ps.getProperty('app.grid[1][0]') == 3
        ps.getProperty('app.grid') == null
        !ps.containsProperty('app.grid')
        ps.getNavigableProperty('app.grid') == [[1, 2], [3]]
    }

    def "Ensure scalar lists preserve value types and empty elements in indexed properties"() {
        given:
        def map = new NavigableMap()
        map.merge([app: [values: ['${EXAMPLE_NAME:default}', 0, false, '', null, []], empty: []]], false)

        when:
        def ps = new NavigableMapPropertySource('test', map)

        then:
        ps.getProperty('app.values[0]') == '${EXAMPLE_NAME:default}'
        ps.getProperty('app.values[1]') == 0
        ps.getProperty('app.values[2]') == false
        ps.getProperty('app.values[3]') == ''
        ps.getProperty('app.values[4]') == ''
        ps.getProperty('app.values[5]') == ''
        ps.getPropertyNames().toList() == ['app.empty', 'app.values[0]', 'app.values[1]', 'app.values[2]', 'app.values[3]', 'app.values[4]', 'app.values[5]']
        ps.containsProperty('app.values[4]')
        !ps.containsProperty('app.values')

        and: "An empty list remains available to clear defaults during binding"
        ps.getProperty('app.empty') == []
        ps.containsProperty('app.empty')
        ps.getNavigableProperty('app.values') == ['${EXAMPLE_NAME:default}', 0, false, '', null, []]
        ps.getNavigablePropertyNames().contains('app.values')
    }

    def "Ensure origin tracked scalar lists and elements are unwrapped"() {
        given:
        def map = new NavigableMap()
        def values = OriginTrackedValue.of([OriginTrackedValue.of('${EXAMPLE_NAME:default}'), OriginTrackedValue.of(42)])
        map.put('app.values', values)

        when:
        def ps = new NavigableMapPropertySource('test', map)

        then:
        ps.getPropertyNames().toList() == ['app.values[0]', 'app.values[1]']
        ps.getProperty('app.values[0]') == '${EXAMPLE_NAME:default}'
        ps.getProperty('app.values[1]') == 42
        ps.containsProperty('app.values[0]')
        !ps.containsProperty('app.values')
        ps.getProperty('app.values') == null
        ps.getNavigableProperty('app.values').is(values)
    }

    def "Ensure an empty object, an empty list or a null value in a list of objects is presented as an empty string"() {
        given: "The elements Spring Boot's YAML loader presents as an empty string"
        def map = new NavigableMap()
        map.merge([app: [empty: [[:]], items: [[name: null, tags: []], [:]]]], false)

        when:
        def ps = new NavigableMapPropertySource("test", map)

        then: "Each is presented under its own name, as Spring Boot presents it"
        ps.getProperty('app.empty[0]') == ''
        ps.containsProperty('app.empty[0]')
        ps.getProperty('app.items[0].name') == ''
        ps.getProperty('app.items[0].tags') == ''
        ps.getProperty('app.items[1]') == ''
        ps.getPropertyNames().toList().containsAll(['app.empty[0]', 'app.items[0].name', 'app.items[0].tags', 'app.items[1]'])

        and: "The list itself is still not"
        !ps.containsProperty('app.empty')
        !ps.containsProperty('app.items')
    }
}

package org.grails.config

import spock.lang.Specification

/*
 * Copyright 2014 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
}

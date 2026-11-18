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
package grails.web.databinding

import spock.lang.Specification

import grails.databinding.SimpleMapDataBindingSource

class CollectionElementBindingSpec extends Specification {

    void 'list binding preserves existing elements and binds map elements'() {
        given:
        def existing = new BindingElement(name: 'existing')
        def target = new ElementContainer()

        when:
        new GrailsWebDataBinder(null).bind(target, new SimpleMapDataBindingSource(
                [elements: [existing, [name: 'created']]]), ['elements'])

        then:
        target.elements.size() == 2
        target.elements[0].is(existing)
        target.elements[1].name == 'created'
    }

    void 'array binding preserves null and existing elements while binding maps'() {
        given:
        def existing = new BindingElement(name: 'existing')
        def target = new ElementContainer()

        when:
        new GrailsWebDataBinder(null).bind(target, new SimpleMapDataBindingSource(
                [array: [existing, null, [name: 'created']]]), ['array'])

        then:
        target.array.length == 3
        target.array[0].is(existing)
        target.array[1] == null
        target.array[2].name == 'created'
    }

    void 'map binding preserves null and existing values while binding nested maps'() {
        given:
        def existing = new BindingElement(name: 'existing')
        def target = new ElementContainer()

        when:
        new GrailsWebDataBinder(null).bind(target, new SimpleMapDataBindingSource(
                [mapped: [first: existing, second: null, third: [name: 'created']]]), ['mapped'])

        then:
        target.mapped.size() == 3
        target.mapped.first.is(existing)
        target.mapped.containsKey('second')
        target.mapped.second == null
        target.mapped.third.name == 'created'
    }
}

class ElementContainer {
    List<BindingElement> elements
    BindingElement[] array
    Map<String, BindingElement> mapped
}

class BindingElement {
    String name
}

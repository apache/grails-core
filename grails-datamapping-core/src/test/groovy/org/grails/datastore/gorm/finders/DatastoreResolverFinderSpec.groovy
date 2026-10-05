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
package org.grails.datastore.gorm.finders

import grails.gorm.annotation.Entity
import org.grails.datastore.mapping.simple.SimpleMapDatastore
import spock.lang.AutoCleanup
import spock.lang.Specification
import spock.lang.Unroll

/**
 * {@code DefaultGormApiFactory#createDynamicFinders} registers every finder ({@code ListResultFinder},
 * {@code CountFinder}, {@code ListOrderByFinder}, etc.) through its {@code DatastoreResolver}-based
 * factory, and {@link FinderSupport} resolves the datastore on every invocation. Drives real
 * dynamic finder calls through a {@code SimpleMapDatastore}-backed entity to exercise that lazy
 * resolver path and the additional-criteria closure support.
 */
class DatastoreResolverFinderSpec extends Specification {

    @AutoCleanup
    SimpleMapDatastore datastore = new SimpleMapDatastore(ResolverFinderThing)

    void "findAllBy resolves its datastore via the lazy DatastoreResolver and executes a real query"() {
        given:
        ResolverFinderThing.newInstance(title: 'Alpha').save(flush: true)
        ResolverFinderThing.newInstance(title: 'Beta').save(flush: true)

        expect:
        ResolverFinderThing.findAllByTitle('Alpha').size() == 1
    }

    void "countBy resolves its datastore via the lazy DatastoreResolver and executes a real query"() {
        given:
        ResolverFinderThing.newInstance(title: 'Same').save(flush: true)
        ResolverFinderThing.newInstance(title: 'Same').save(flush: true)

        expect:
        ResolverFinderThing.countByTitle('Same') == 2
    }

    void "listOrderBy resolves its datastore via the lazy DatastoreResolver and executes a real query"() {
        given:
        ResolverFinderThing.newInstance(title: 'B').save(flush: true)
        ResolverFinderThing.newInstance(title: 'A').save(flush: true)

        expect:
        ResolverFinderThing.listOrderByTitle()*.title == ['A', 'B']
    }

    void "listOrderBy normalizes the order direction regardless of case and surrounding whitespace"() {
        given:
        ResolverFinderThing.newInstance(title: 'B').save(flush: true)
        ResolverFinderThing.newInstance(title: 'A').save(flush: true)

        expect:
        ResolverFinderThing.listOrderByTitle(order: ' DESC ')*.title == ['B', 'A']
        ResolverFinderThing.listOrderByTitle(order: 'Asc')*.title == ['A', 'B']
        ResolverFinderThing.listOrderByTitle(order: '')*.title == ['A', 'B']
    }

    @Unroll
    void "listOrderBy rejects order direction #description without echoing it"() {
        when:
        ResolverFinderThing.listOrderByTitle(order: order)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == 'Invalid sort direction'

        where:
        order        | description
        'sideways'   | 'that is not asc or desc'
        'desc extra' | 'carrying extra tokens'
    }

    void "listOrderBy with an additional criteria closure applies it via DynamicFinder.applyAdditionalCriteria"() {
        given: "ListOrderByFinder.invoke(Class, methodName, Closure, Object[]) is called directly since\n" +
                "routing a trailing closure through GormStaticApi's own dynamic dispatch into this\n" +
                "specific 4-arg overload isn't part of what this class itself needs proving"
        ResolverFinderThing.newInstance(title: 'Matched', age: 30).save(flush: true)
        ResolverFinderThing.newInstance(title: 'Matched', age: 99).save(flush: true)
        def finder = new ListOrderByFinder(datastore)

        when:
        def results = finder.invoke(ResolverFinderThing, 'listOrderByTitle', { lt('age', 50) }, [] as Object[])

        then:
        results.size() == 1
        results[0].age == 30
    }

    void "invoke throws IllegalStateException when no datastore can be resolved"() {
        given:
        def finder = new ListOrderByFinder((org.grails.datastore.mapping.core.Datastore) null)

        when:
        finder.invoke(ResolverFinderThing, 'listOrderByTitle', [] as Object[])

        then:
        def e = thrown(IllegalStateException)
        e.message == 'Cannot execute session query with null datastore'
    }
}

@Entity
class ResolverFinderThing {
    String title
    Integer age
}

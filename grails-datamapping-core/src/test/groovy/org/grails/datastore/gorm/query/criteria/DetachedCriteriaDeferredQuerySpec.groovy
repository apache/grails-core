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
package org.grails.datastore.gorm.query.criteria

import java.util.concurrent.atomic.AtomicInteger

import jakarta.persistence.criteria.JoinType
import spock.lang.Specification
import spock.lang.Unroll

import grails.gorm.DetachedCriteria
import org.grails.datastore.mapping.query.Query

/**
 * The deferred query of {@code buildLazy} runs once, on the criteria itself, the first time the criteria is read,
 * changed or derived from, whichever way that happens. The criteria is a builder with many ways in, so each one is
 * listed here: a way in that is missing from the list is one that can run the query late, or twice.
 */
class DetachedCriteriaDeferredQuerySpec extends Specification {

    private AtomicInteger runs = new AtomicInteger()

    private DetachedCriteria lazyCriteria() {
        runs = new AtomicInteger()
        // the closure resolves names on the criteria first, so it counts through a local it captures
        AtomicInteger counter = runs
        new DetachedCriteria(TestEntity).buildLazy {
            counter.incrementAndGet()
            add(new Query.Equals('name', 'A'))
            order('first')
            projectionList.property('first')
        }
    }

    @Unroll
    void 'changing the criteria with #way runs the deferred query first, once'() {
        given:
        def lazy = lazyCriteria()

        when:
        change.call(lazy)

        then: 'the deferred query ran before the change, and only once'
        lazy.@lazyQuery == null
        runs.get() == 1
        lazy.@criteria.findAll { it.property == 'name' }.size() == 1

        and: 'what the query added comes first and the change after it'
        lazy.@orders*.property.first() == 'first'

        where:
        way                      | change
        'setAlias'               | { it.setAlias('x') }
        'order(String)'          | { it.order('second') }
        'order(String, String)'  | { it.order('second', 'desc') }
        'order(Order)'           | { it.order(new Query.Order('second')) }
        'join(String)'           | { it.join('x') }
        'join(String, JoinType)' | { it.join('x', JoinType.LEFT) }
        'select(String)'         | { it.select('x') }
        'add'                    | { it.add(new Query.Equals('id', 1L)) }
    }

    @Unroll
    void 'adding the projection #way runs the deferred query first, and its projection comes first'() {
        given:
        def lazy = lazyCriteria()

        when:
        change.call(lazy.projectionList)

        then:
        lazy.@lazyQuery == null
        runs.get() == 1
        lazy.@projections.size() == 2
        lazy.@projections.first().propertyName == 'first'

        where:
        way                | change
        'avg'              | { it.avg('id') }
        'max'              | { it.max('id') }
        'min'              | { it.min('id') }
        'sum'              | { it.sum('id') }
        'property'         | { it.property('second') }
        'rowCount'         | { it.rowCount() }
        'distinct'         | { it.distinct() }
        'distinct(String)' | { it.distinct('id') }
        'countDistinct'    | { it.countDistinct('id') }
        'groupProperty'    | { it.groupProperty('id') }
        'count'            | { it.count() }
        'id'               | { it.id() }
    }

    @Unroll
    void 'reading #way runs the deferred query once, so every reader sees what it added'() {
        given:
        def lazy = lazyCriteria()

        when:
        def value = read.call(lazy)

        then:
        lazy.@lazyQuery == null
        runs.get() == 1
        check.call(value)

        where:
        way                | read                       | check
        'getCriteria'      | { it.criteria }            | { it*.property == ['name'] }
        'getOrders'        | { it.orders }              | { it*.property == ['first'] }
        'getProjections'   | { it.projections }         | { it.size() == 1 }
        'getAlias'         | { it.alias }               | { it == null }
        'getFetchStrategies' | { it.fetchStrategies }   | { it.isEmpty() }
        'getJoinTypes'     | { it.joinTypes }           | { it.isEmpty() }
    }

    @Unroll
    void 'deriving a criteria with #way runs the deferred query once and the derived one carries its result'() {
        given:
        def lazy = lazyCriteria()

        when:
        def derived = derive.call(lazy)

        then: 'the query ran once, on the criteria it was built for, and the derived criteria has no query pending'
        runs.get() == 1
        lazy.@lazyQuery == null
        derived.@lazyQuery == null
        derived.@criteria*.property == ['name']
        !derived.is(lazy)

        and: 'the original is not changed by what was derived from it'
        lazy.@criteria*.property == ['name']

        where:
        way                    | derive
        'clone'                | { it.clone() }
        'max'                  | { it.max(5) }
        'offset'               | { it.offset(2) }
        'withConnection'       | { it.withConnection('secondary') }
        'materializeLazyCriteria' | { it.materializeLazyCriteria() }
    }

    void 'a second deferred query keeps the first one instead of replacing it'() {
        given:
        def first = lazyCriteria()

        when:
        def second = first.buildLazy {
            add(new Query.Equals('id', 1L))
        }

        then: 'the first query ran when the second was built from it, and the second runs on first use'
        runs.get() == 1
        second.materializeLazyCriteria().@criteria*.property == ['name', 'id']
        runs.get() == 1
    }

    void 'a query that reads a value that changes keeps what it read the first time'() {
        given:
        def wanted = [name: 'A']
        def lazy = new DetachedCriteria(TestEntity).buildLazy {
            add(new Query.Equals('name', wanted.name))
        }
        lazy.materializeLazyCriteria()

        when:
        wanted.name = 'B'
        def derived = lazy.max(10)
        derived.add(new Query.NotEquals('name', 'Z'))

        then:
        derived.@criteria*.value == ['A', 'Z']
        lazy.materializeLazyCriteria().@criteria*.value == ['A']
    }

    @Unroll
    void 'a deferred query that derives a criteria with #way while it runs does not run again'() {
        given:
        AtomicInteger counter = new AtomicInteger()
        def lazy = new DetachedCriteria(TestEntity).buildLazy {
            counter.incrementAndGet()
            add(new Query.Equals('name', 'A'))
            step.call(delegate)
        }

        when:
        def read = lazy.materializeLazyCriteria()

        then: 'the query ran once, and the copy it read holds what the query added and no pending query'
        noExceptionThrown()
        counter.get() == 1
        read.@criteria*.property.contains('name')
        read.@lazyQuery == null
        lazy.@lazyQuery == null

        where:
        way             | step
        'sort'          | { it.sort('name') }
        'max'           | { it.max(5) }
        'where'         | { it.where { } }
        'build'         | { it.build { } }
        'a second lazy' | { it.buildLazy { } }
    }
}

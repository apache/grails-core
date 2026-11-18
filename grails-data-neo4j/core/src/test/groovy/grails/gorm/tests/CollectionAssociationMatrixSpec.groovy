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

package grails.gorm.tests

import grails.gorm.annotation.Entity
import org.apache.grails.data.neo4j.core.Neo4jGormDatastoreSpec
import spock.lang.Issue
import spock.lang.Unroll

/**
 * A criterion on a property that holds a collection of associated entities is about the collection, so in a
 * disjunction or a negation it must not require the collection for the whole query: a shelf with no items
 * still has to reach the other branches. Each criterion is tried in each context, and the expected shelves are
 * worked out from the fixture by one oracle, not written by hand for each row.
 */
@Issue('https://github.com/apache/grails-core/pull/16836')
class CollectionAssociationMatrixSpec extends Neo4jGormDatastoreSpec {

    void setupSpec() {
        manager.registerDomainClasses(MatrixShelf, MatrixItem, MatrixHolder)
    }

    /** s1 has i1 and i2, s2 has no item, s3 has i3 */
    private Map seed() {
        MatrixItem i1 = new MatrixItem(label: 'i1').save(flush: true, failOnError: true)
        MatrixItem i2 = new MatrixItem(label: 'i2').save(flush: true, failOnError: true)
        MatrixItem i3 = new MatrixItem(label: 'i3').save(flush: true, failOnError: true)
        MatrixShelf s1 = new MatrixShelf(name: 's1', items: [i1, i2]).save(flush: true, failOnError: true)
        MatrixShelf s2 = new MatrixShelf(name: 's2').save(flush: true, failOnError: true)
        MatrixShelf s3 = new MatrixShelf(name: 's3', items: [i3]).save(flush: true, failOnError: true)
        new MatrixHolder(label: 'h1', shelf: s1).save(flush: true, failOnError: true)
        new MatrixHolder(label: 'h2', shelf: s2).save(flush: true, failOnError: true)
        new MatrixHolder(label: 'h3', shelf: s3).save(flush: true, failOnError: true)
        new MatrixHolder(label: 'h0').save(flush: true, failOnError: true)
        MatrixShelf.withSession { it.clear() }
        [i1: [i1.id], i2: [i2.id], i3: [i3.id], i1i2: [i1.id, i2.id], item1: MatrixItem.get(i1.id)]
    }

    private static final Set<String> ALL = ['s1', 's2', 's3'] as Set

    /** the criterion, and the shelves it matches on its own */
    private static final List<List> CRITERIA = [
            ['inList an item of s1', { Map ids -> { -> inList('items', ids.i1) } }, ['s1'] as Set],
            ['inList the items of s1', { Map ids -> { -> inList('items', ids.i1i2) } }, ['s1'] as Set],
            ['inList no item', { Map ids -> { -> inList('items', [-1L]) } }, [] as Set],
            ['inList an item of s1 or s3', { Map ids -> { -> inList('items', ids.i1 + ids.i3) } }, ['s1', 's3'] as Set],
            ['eq an item of s1', { Map ids -> { -> eq('items', ids.item1) } }, ['s1'] as Set],
            ['ne an item of s1', { Map ids -> { -> ne('items', ids.item1) } }, ['s1', 's3'] as Set],
            ['isNull', { Map ids -> { -> isNull('items') } }, ['s2'] as Set],
            ['isNotNull', { Map ids -> { -> isNotNull('items') } }, ['s1', 's3'] as Set],
            ['isEmpty', { Map ids -> { -> isEmpty('items') } }, ['s2'] as Set],
            ['isNotEmpty', { Map ids -> { -> isNotEmpty('items') } }, ['s1', 's3'] as Set]
    ]

    /** runs a criterion closure against the builder of the block it stands in */
    private static final Closure CALL = { Closure criterion, Object builder ->
        Closure copy = (Closure) criterion.clone()
        copy.delegate = builder
        copy.resolveStrategy = Closure.DELEGATE_FIRST
        copy.call()
    }

    /** the context, and what it makes of the set a criterion matches */
    private static final List<List> CONTEXTS = [
            ['at the top', { Closure crit -> { -> CALL(crit, delegate) } }, { Set m -> m }],
            ['in a disjunction after the name', { Closure crit -> { -> or { eq('name', 's2'); CALL(crit, delegate) } } }, { Set m -> m + ['s2'] }],
            ['in a disjunction before the name', { Closure crit -> { -> or { CALL(crit, delegate); eq('name', 's2') } } }, { Set m -> m + ['s2'] }],
            ['in a conjunction inside a disjunction', { Closure crit -> { -> or { and { CALL(crit, delegate); ne('name', 'none') }; eq('name', 's2') } } }, { Set m -> m + ['s2'] }],
            ['in a disjunction inside a conjunction', { Closure crit -> { -> and { or { CALL(crit, delegate); eq('name', 's2') }; ne('name', 's3') } } }, { Set m -> (m + ['s2']) - ['s3'] }],
            ['in a negation', { Closure crit -> { -> not { CALL(crit, delegate) } } }, { Set m -> ALL - m }]
    ]

    @Unroll
    void "#criterion #context from the root"() {
        given:
        Map ids = seed()
        Closure crit = build.call(ids)

        when:
        List<String> found = MatrixShelf.createCriteria().list(wrap.call(crit))*.name.unique().sort()

        then:
        found == expected.sort()

        where:
        [criterion, build, matches, context, wrap, outcome] << CRITERIA.collectMany { c ->
            CONTEXTS.collect { ctx -> [c[0], c[1], c[2], ctx[0], ctx[1], ctx[2]] }
        }
        expected = outcome.call(matches) as List
    }

    @Unroll
    void "#first and #second together"() {
        given:
        Map ids = seed()
        Closure a = buildA.call(ids)
        Closure b = buildB.call(ids)

        when:
        List<String> found = MatrixShelf.createCriteria().list({ -> and { CALL(a, delegate); CALL(b, delegate) } })*.name.unique().sort()

        then: 'two criteria on one collection hold together when some element satisfies each, the same element or not'
        found == (matchesA.intersect(matchesB) as List).sort()

        where:
        [first, buildA, matchesA, second, buildB, matchesB] << CRITERIA.collectMany { x ->
            CRITERIA.collect { y -> [x[0], x[1], x[2], y[0], y[1], y[2]] }
        }
    }

    @Unroll
    void "#criterion #context inside a block of the shelf of a holder"() {
        given:
        Map ids = seed()
        Closure crit = build.call(ids)
        Closure inner = wrap.call(crit)

        when:
        List<String> found = MatrixHolder.createCriteria().list { shelf(inner) }*.label.unique().sort()

        then:
        found == expected.sort().collect { 'h' + it.substring(1) }

        where:
        [criterion, build, matches, context, wrap, outcome] << CRITERIA.collectMany { c ->
            CONTEXTS.collect { ctx -> [c[0], c[1], c[2], ctx[0], ctx[1], ctx[2]] }
        }
        expected = outcome.call(matches) as List
    }
}

@Entity
class MatrixShelf {
    String name
    static hasMany = [items: MatrixItem]
}

@Entity
class MatrixItem {
    String label
}

@Entity
class MatrixHolder {
    String label
    MatrixShelf shelf
}

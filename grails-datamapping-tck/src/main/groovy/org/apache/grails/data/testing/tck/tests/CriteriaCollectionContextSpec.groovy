/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.grails.data.testing.tck.tests

import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import org.apache.grails.data.testing.tck.domains.CollectionHolder
import org.apache.grails.data.testing.tck.domains.CollectionItem
import org.apache.grails.data.testing.tck.domains.CollectionShelf
import spock.lang.IgnoreIf
import spock.lang.Unroll

/**
 * A criterion on a collection of associated entities holds when some element satisfies it, and an association block
 * is one branch of the junction it stands in, so a shelf with no items still reaches the other branches of an
 * {@code or}. Each criterion is tried in each context, from the root of a query and from a block of another entity,
 * and the expected shelves are worked out from the fixture by one oracle, not written by hand for each row.
 * MongoDB has no association blocks and does not support isEmpty or isNotEmpty on a collection.
 */
@IgnoreIf({ System.getProperty('mongodb.gorm.suite') == 'true' })
class CriteriaCollectionContextSpec extends GrailsDataTckSpec {

    @Override
    void setupSpec() {
        manager.domainClasses.addAll([CollectionShelf, CollectionItem, CollectionHolder])
    }

    /** s1 has i1 and i2, s2 has no item, s3 has i3 */
    private Map seed() {
        CollectionItem i1 = new CollectionItem(label: 'i1').save(failOnError: true)
        CollectionItem i2 = new CollectionItem(label: 'i2').save(failOnError: true)
        CollectionItem i3 = new CollectionItem(label: 'i3').save(failOnError: true)
        CollectionShelf s1 = new CollectionShelf(name: 's1', items: [i1, i2]).save(failOnError: true)
        CollectionShelf s2 = new CollectionShelf(name: 's2').save(failOnError: true)
        CollectionShelf s3 = new CollectionShelf(name: 's3', items: [i3]).save(failOnError: true)
        new CollectionHolder(label: 'h1', shelf: s1).save(failOnError: true)
        new CollectionHolder(label: 'h2', shelf: s2).save(failOnError: true)
        new CollectionHolder(label: 'h3', shelf: s3).save(failOnError: true)
        new CollectionHolder(label: 'h0').save(flush: true, failOnError: true)
        manager.session.clear()
        [item1: CollectionItem.get(i1.id)]
    }

    private static final Set<String> ALL = ['s1', 's2', 's3'] as Set

    /** runs a criterion closure against the builder of the block it stands in */
    private static final Closure CALL = { Closure criterion, Object builder ->
        Closure copy = (Closure) criterion.clone()
        copy.delegate = builder
        copy.resolveStrategy = Closure.DELEGATE_FIRST
        copy.call()
    }

    /** the criterion, and the shelves it matches on its own */
    private static final List<List> CRITERIA = [
            ['a block where an item has label i1', { Map d -> { -> items { eq('label', 'i1') } } }, ['s1'] as Set],
            ['a block where an item has label i1 or i3', { Map d -> { -> items { inList('label', ['i1', 'i3']) } } }, ['s1', 's3'] as Set],
            ['a block that no item satisfies', { Map d -> { -> items { eq('label', 'none') } } }, [] as Set],
            ['a block where an item does not have label i1', { Map d -> { -> items { ne('label', 'i1') } } }, ['s1', 's3'] as Set],
            ['isEmpty', { Map d -> { -> isEmpty('items') } }, ['s2'] as Set],
            ['isNotEmpty', { Map d -> { -> isNotEmpty('items') } }, ['s1', 's3'] as Set]
    ]

    /** the context, and what it makes of the set a criterion matches */
    private static final List<List> CONTEXTS = [
            ['at the top', { Closure crit -> { -> CALL(crit, delegate) } }, { Set m -> m }],
            ['in a disjunction after the name', { Closure crit -> { -> or { eq('name', 's2'); CALL(crit, delegate) } } }, { Set m -> m + ['s2'] }],
            ['in a disjunction before the name', { Closure crit -> { -> or { CALL(crit, delegate); eq('name', 's2') } } }, { Set m -> m + ['s2'] }],
            ['in a conjunction inside a disjunction', { Closure crit -> { -> or { and { CALL(crit, delegate); ne('name', 'none') }; eq('name', 's2') } } }, { Set m -> m + ['s2'] }],
            ['in a disjunction inside a conjunction', { Closure crit -> { -> and { or { CALL(crit, delegate); eq('name', 's2') }; ne('name', 's3') } } }, { Set m -> (m + ['s2']) - ['s3'] }]
    ]

    @Unroll
    void "#criterion #context from the root"() {
        given:
        Map d = seed()
        Closure crit = build.call(d)

        when:
        List<String> found = CollectionShelf.createCriteria().list(wrap.call(crit))*.name.unique().sort()

        then:
        found == expected.sort()

        where:
        [criterion, build, matches, context, wrap, outcome] << CRITERIA.collectMany { c ->
            CONTEXTS.collect { ctx -> [c[0], c[1], c[2], ctx[0], ctx[1], ctx[2]] }
        }
        expected = outcome.call(matches) as List
    }

    @Unroll
    void "#criterion #context inside a block of the shelf of a holder"() {
        given:
        Map d = seed()
        Closure crit = build.call(d)
        Closure inner = wrap.call(crit)

        when:
        List<String> found = CollectionHolder.createCriteria().list { shelf(inner) }*.label.unique().sort()

        then:
        found == expected.sort().collect { 'h' + it.substring(1) }

        where:
        [criterion, build, matches, context, wrap, outcome] << CRITERIA.collectMany { c ->
            CONTEXTS.collect { ctx -> [c[0], c[1], c[2], ctx[0], ctx[1], ctx[2]] }
        }
        expected = outcome.call(matches) as List
    }
}

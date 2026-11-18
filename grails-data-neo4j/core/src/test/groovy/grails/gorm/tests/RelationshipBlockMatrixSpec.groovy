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
import grails.neo4j.Relationship
import org.apache.grails.data.neo4j.core.Neo4jGormDatastoreSpec
import spock.lang.Issue
import spock.lang.Unroll

/**
 * A block on an association to a relationship entity is about the relationship, so every property name in it is
 * looked up on the relationship entity, whatever the entity that owns the relationship has under the same name.
 * The owner has an association, and a property of another type, under the names the relationship has as plain
 * properties; each criterion is tried on each of them, from the root of a query and from a nested block.
 */
@Issue('https://github.com/apache/grails-core/pull/16836')
class RelationshipBlockMatrixSpec extends Neo4jGormDatastoreSpec {

    void setupSpec() {
        manager.registerDomainClasses(ColRoot, ColOwner, ColAddress, ColTarget, ColLink, ColPerson, ColKnows)
    }

    private void seed() {
        ColTarget target = new ColTarget(name: 't').save(flush: true, failOnError: true)
        ColAddress address = new ColAddress(city: 'c').save(flush: true, failOnError: true)
        ColOwner withLink = new ColOwner(name: 'with', total: 'text', address: address).save(flush: true, failOnError: true)
        ColOwner without = new ColOwner(name: 'without', total: 'text', address: address).save(flush: true, failOnError: true)
        new ColLink(from: withLink, to: target, address: 'set', kind: 'a', total: 5L).save(flush: true, failOnError: true)
        new ColRoot(label: 'r-with', owner: withLink).save(flush: true, failOnError: true)
        new ColRoot(label: 'r-without', owner: without).save(flush: true, failOnError: true)
        ColRoot.withSession { it.clear() }
    }

    /** property, criterion, arguments, whether the owner that has the relationship matches */
    private static final List<List> CELLS = [
            ['address', 'eq', ['set'], true], ['address', 'eq', ['other'], false],
            ['address', 'ne', ['set'], false], ['address', 'ne', ['other'], true],
            ['address', 'gt', ['a'], true], ['address', 'gt', ['z'], false],
            ['address', 'ge', ['set'], true], ['address', 'ge', ['t'], false],
            ['address', 'lt', ['z'], true], ['address', 'lt', ['a'], false],
            ['address', 'le', ['set'], true], ['address', 'le', ['a'], false],
            ['address', 'like', ['s%'], true], ['address', 'like', ['x%'], false],
            ['address', 'ilike', ['S%'], true], ['address', 'ilike', ['X%'], false],
            ['address', 'in', [['set', 'x']], true], ['address', 'in', [['x', 'y']], false],
            ['address', 'between', ['a', 'z'], true], ['address', 'between', ['t', 'z'], false],
            ['address', 'isNotNull', [], true], ['address', 'isNull', [], false],
            ['total', 'eq', [5L], true], ['total', 'eq', [6L], false],
            ['total', 'ne', [5L], false], ['total', 'ne', [6L], true],
            ['total', 'gt', [4L], true], ['total', 'gt', [5L], false],
            ['total', 'ge', [5L], true], ['total', 'ge', [6L], false],
            ['total', 'lt', [6L], true], ['total', 'lt', [5L], false],
            ['total', 'le', [5L], true], ['total', 'le', [4L], false],
            ['total', 'in', [[5L, 9L]], true], ['total', 'in', [[8L, 9L]], false],
            ['total', 'between', [1L, 9L], true], ['total', 'between', [6L, 9L], false],
            ['total', 'isNotNull', [], true], ['total', 'isNull', [], false],
            ['kind', 'eq', ['a'], true], ['kind', 'eq', ['z'], false],
            ['kind', 'like', ['a%'], true], ['kind', 'like', ['z%'], false],
            ['kind', 'in', [['a']], true], ['kind', 'in', [['z']], false],
            ['kind', 'isNotNull', [], true], ['kind', 'isNull', [], false]
    ]

    private static Closure block(String property, String criterion, List args) {
        { ->
            delegate."$criterion"(*([property] + args))
        }
    }

    @Unroll
    void "#entry: #property #criterion #args"() {
        given:
        seed()
        Closure inner = block(property, criterion, args)

        when:
        List<String> found = entry == 'owner { links }' ?
                ColOwner.createCriteria().list { links(inner) }*.name :
                ColRoot.createCriteria().list { owner { links(inner) } }*.label

        then:
        found == (expected ? [entry == 'owner { links }' ? 'with' : 'r-with'] : [])

        where:
        [entry, cell] << ['owner { links }', 'root { owner { links } }'].collectMany { e -> CELLS.collect { [e, it] } }
        property = cell[0]
        criterion = cell[1]
        args = cell[2]
        expected = cell[3]
    }

    @Unroll
    void "a #end block inside the relationship block, matching #matches, from #entry"() {
        given:
        seed()
        Closure inner = { ->
            delegate."$end" {
                eq(property, value)
            }
        }

        when:
        List<String> found = entry == 'owner { links }' ?
                ColOwner.createCriteria().list { links(inner) }*.name :
                ColRoot.createCriteria().list { owner { links(inner) } }*.label

        then:
        found == (matches ? [entry == 'owner { links }' ? 'with' : 'r-with'] : [])

        where:
        [entry, cell] << ['owner { links }', 'root { owner { links } }'].collectMany { e ->
            [['to', 'name', 't', true], ['to', 'name', 'x', false], ['from', 'name', 'with', true], ['from', 'name', 'x', false]].collect { [e, it] }
        }
        end = cell[0]
        property = cell[1]
        value = cell[2]
        matches = cell[3]
    }

    @Unroll
    void "a disjunction of #property #criterion #args with a name keeps the owner without the association"() {
        given:
        seed()
        ColOwner orphan = new ColOwner(name: 'orphan', total: 'text').save(flush: true, failOnError: true)
        ColOwner.withSession { it.clear() }
        Closure inner = block(property, criterion, args)

        when:
        List<String> found = ColOwner.createCriteria().list {
            or {
                links(inner)
                eq('name', 'orphan')
            }
        }*.name.sort()

        then:
        found == (expected ? ['orphan', 'with'] : ['orphan'])

        where:
        cell << CELLS
        property = cell[0]
        criterion = cell[1]
        args = cell[2]
        expected = cell[3]
    }

    @Unroll
    void "a disjunction of #criterion on an association of the owner and a name keeps the owner without the association"() {
        given:
        seed()
        new ColOwner(name: 'orphan', total: 'text').save(flush: true, failOnError: true)
        Long addressId = ColAddress.list().first().id
        ColOwner.withSession { it.clear() }

        when:
        List<String> found = ColOwner.createCriteria().list {
            or {
                eq('name', 'orphan')
                "$criterion"('address', *argsFor.call(addressId))
            }
        }*.name.sort()

        then:
        found == expected

        where:
        criterion | argsFor                          | expected
        'inList'  | { Long id -> [[id]] }            | ['orphan', 'with', 'without']
        'inList'  | { Long id -> [[id + 1000]] }     | ['orphan']
        'eq'      | { Long id -> [ColAddress.get(id)] } | ['orphan', 'with', 'without']
        'isNull'  | { Long id -> [] }                | ['orphan']
        'isNotNull' | { Long id -> [] }              | ['orphan', 'with', 'without']
    }

    @Unroll
    void "a #end block of a self-referential relationship is the #end of the relationship it was entered through"() {
        given:
        ColPerson alice = new ColPerson(name: 'Alice').save(flush: true, failOnError: true)
        ColPerson bob = new ColPerson(name: 'Bob').save(flush: true, failOnError: true)
        new ColKnows(from: alice, to: bob).save(flush: true, failOnError: true)
        ColPerson.withSession { it.clear() }

        when:
        List<String> found = ColPerson.createCriteria().list {
            knows {
                "$end" {
                    eq('name', name)
                }
            }
        }*.name

        then:
        found == expected

        where:
        end    | name    | expected
        'from' | 'Alice' | ['Alice']
        'from' | 'Bob'   | []
        'to'   | 'Bob'   | ['Alice']
        'to'   | 'Alice' | []
    }

    void "an association of the owner is still an association in the block of the owner"() {
        given:
        seed()

        expect:
        ColRoot.createCriteria().list { owner { address { eq('city', 'c') } } }.size() == 2
        ColOwner.createCriteria().list { isNotNull('address') }.size() == 2
    }
}

@Entity
class ColRoot {
    String label
    ColOwner owner
}

@Entity
class ColOwner {
    String name
    String total
    ColAddress address
    static hasMany = [links: ColLink]
}

@Entity
class ColAddress {
    String city
}

@Entity
class ColTarget {
    String name
}

@Entity
class ColLink implements Relationship<ColOwner, ColTarget> {
    String address
    String kind
    Long total
}

@Entity
class ColPerson {
    String name
    static hasMany = [knows: ColKnows]
}

@Entity
class ColKnows implements Relationship<ColPerson, ColPerson> {
}

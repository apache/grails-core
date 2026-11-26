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
package org.grails.datastore.gorm.mongo

import grails.gorm.DetachedCriteria

import org.apache.grails.data.mongo.core.GrailsDataMongoTckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec

/**
 * Every criteria that can be built from a small grammar, run against one set of owners, and compared with what a
 * reference evaluator says it returns. The expected value is not written per query: it is the evaluator's, which applies
 * SQL three-valued logic to each owner, where a condition on a value that is not there is unknown and an unknown that is
 * negated is still unknown. Hibernate 5 answered every query as the evaluator does when this spec was written, so the
 * evaluator is Hibernate 5's, and MongoDB is checked against it. A query that is not answered as the evaluator answers it is
 * listed with the owners it returned and the owners it should have.
 *
 * The grammar: a leaf is {@code eq}, {@code ne} or {@code inList} on the name, the age or the city of the embedded
 * address; a group is {@code and}, {@code or} or {@code not} of up to two leaves, and of one group and one leaf. The city
 * is written as a dotted property and as a block on the address, and each query goes through a criteria builder and
 * through a detached criteria. A detached criteria throws for an association block inside a group, so the
 * block is run there as a single condition only.
 *
 * Every owner has a name and an age, so that no condition is on a value that is null where the owner has the property:
 * the one value that is missing is the address of two owners. A null value of a property that is there is where
 * MongoDB and the join-based stores differ for {@code ne}, and it is a question of its own.
 */
class CriteriaSemanticMatrixSpec extends GrailsDataTckSpec<GrailsDataMongoTckManager> {

    void setupSpec() {
        manager.domainClasses.addAll([MatrixOwner])
    }

    /** name, age, city: the two owners without an address have a null city for the evaluator */
    private static final List<List> OWNERS = [
        ['Amelie', 30, 'Paris'],
        ['Bob', 30, 'Rome'],
        ['Bob', 40, 'Paris'],
        ['Cleo', 40, 'Rome'],
        ['Nina', 30, null],
        ['Bob', 40, null]
    ]

    private static final List<List> LEAVES = [
        ['eq', 'name', 'Bob'], ['ne', 'name', 'Bob'],
        ['eq', 'age', 30], ['ne', 'age', 30],
        ['eq', 'city', 'Paris'], ['ne', 'city', 'Paris'], ['inList', 'city', ['Paris', 'Rome']]
    ]

    private void seed() {
        OWNERS.each { String name, Integer age, String city ->
            new MatrixOwner(name: name, age: age, address: city ? new MatrixAddress(city: city) : null).save(flush: true, failOnError: true)
        }
        manager.session.clear()
    }

    void 'every query of the grammar returns the owners the evaluator says it does'() {
        given:
        seed()
        List<List> queries = queries()
        List<String> mismatches = []

        when:
        queries.each { List query ->
            Set<String> expected = OWNERS.findAll { evaluate(query, it) == Boolean.TRUE }.collect { label(it) } as Set
            [true, false].each { boolean block ->
                ['criteria', 'detached'].each { String entry ->
                    if (block && entry == 'detached' && !(query[0] in ['eq', 'ne', 'inList'])) {
                        return
                    }
                    Object actual = run(query, block, entry)
                    if (actual != expected.sort()) {
                        mismatches << "${describe(query)} as ${block ? 'a block' : 'a dotted property'} in a ${entry}: got ${actual}, expected ${expected.sort()}".toString()
                    }
                }
            }
        }

        then: 'no query of the ' + queries.size() + ' is answered differently'
        mismatches.isEmpty()
    }

    private Object run(List query, boolean block, String entry) {
        try {
            List<MatrixOwner> owners = entry == 'criteria' ?
                    MatrixOwner.createCriteria().list { render(query, delegate, block) } :
                    new DetachedCriteria(MatrixOwner).build { render(query, delegate, block) }.list()
            return owners.collect { MatrixOwner owner -> label([owner.name, owner.age, owner.address?.city]) }.sort()
        }
        catch (Throwable error) {
            return "THROWS ${error.class.simpleName}: ${error.message?.take(100)}".toString()
        }
    }

    private static String label(List owner) {
        "${owner[0]}/${owner[1]}/${owner[2] ?: '-'}".toString()
    }

    private static List<List> queries() {
        List<List> all = []
        List<List> groups = []
        LEAVES.each { all << it }
        LEAVES.each { all << ['not', it] }
        for (int i = 0; i < LEAVES.size(); i++) {
            for (int j = i + 1; j < LEAVES.size(); j++) {
                ['and', 'or'].each { String junction ->
                    groups << [junction, LEAVES[i], LEAVES[j]]
                }
            }
        }
        groups.each { all << it }
        groups.each { all << ['not', it] }
        groups.each { List group -> LEAVES.each { List leaf -> ['and', 'or'].each { String junction -> all << [junction, group, leaf] } } }
        all
    }

    /** the criteria of a query written on a builder, the city as a dotted property or as a block on the address */
    private static void render(List query, Object builder, boolean block) {
        switch (query[0]) {
            case 'not':
                builder.not { render(query[1], builder, block) }
                break
            case 'and':
                builder.and { query.tail().each { render(it, builder, block) } }
                break
            case 'or':
                builder.or { query.tail().each { render(it, builder, block) } }
                break
            default:
                String operator = query[0]
                String property = query[1]
                if (property == 'city' && block) {
                    Object value = query[2]
                    Closure inner = { -> "$operator"('city', value) }
                    Closure outer = { -> address(inner) }
                    outer.delegate = builder
                    outer.resolveStrategy = Closure.DELEGATE_FIRST
                    outer.call()
                }
                else {
                    builder."$operator"(property == 'city' ? 'address.city' : property, query[2])
                }
        }
    }

    /** true, false or null for unknown, as SQL answers a condition on one owner */
    private static Boolean evaluate(List query, List owner) {
        switch (query[0]) {
            case 'not':
                Boolean inner = evaluate(query[1], owner)
                return inner == null ? null : !inner
            case 'and':
                List<Boolean> parts = query.tail().collect { evaluate(it, owner) }
                if (parts.any { it == Boolean.FALSE }) {
                    return false
                }
                return parts.every { it == Boolean.TRUE } ? true : null
            case 'or':
                List<Boolean> parts = query.tail().collect { evaluate(it, owner) }
                if (parts.any { it == Boolean.TRUE }) {
                    return true
                }
                return parts.every { it == Boolean.FALSE } ? false : null
            default:
                Object value = owner[['name': 0, 'age': 1, 'city': 2][query[1]]]
                if (value == null) {
                    return null
                }
                switch (query[0]) {
                    case 'eq': return value == query[2]
                    case 'ne': return value != query[2]
                    default: return query[2].contains(value)
                }
        }
    }

    private static String describe(List query) {
        switch (query[0]) {
            case 'not': return "not { ${describe(query[1])} }"
            case 'and': return "and { ${query.tail().collect { describe(it) }.join('; ')} }"
            case 'or': return "or { ${query.tail().collect { describe(it) }.join('; ')} }"
            default: return "${query[0]}(${query[1]}, ${query[2]})"
        }
    }
}

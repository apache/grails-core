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
import org.apache.grails.data.testing.tck.domains.ChildEntity
import org.apache.grails.data.testing.tck.domains.TestEntity
import spock.lang.IgnoreIf
import spock.lang.Issue
import spock.lang.Unroll

/**
 * An empty junction, {@code or {}} or {@code and {}}, in every position a criteria can put one: alone, beside a property, in
 * another junction, under a negation, in an association block, and in each form a query takes (list, count, get, paged and
 * ordered, projected, detached, in a subquery). The expected values are what Hibernate 5, the reference, returns, so each
 * datastore answers the same: an empty junction is true wherever it stands, so it adds no restriction beside a property,
 * makes the {@code or} it stands in true, is neutral in an {@code and}, and makes a negation false. Three shapes are H5
 * behaviours that are kept and named here: an empty {@code not {}} is an error, and a negated association block, and an
 * association block with no criteria beside another branch of an {@code or}, still require an associated entity.
 *
 * Each row lists the datastores that skip it until their own fix is merged, by the {@code <store>.gorm.suite} property:
 * the in-memory store and Neo4j skip every row (they treat an empty junction as matching nothing), MongoDB has no association
 * blocks and one subquery row to fix, and Hibernate 5 skips the three association blocks in an {@code or} until the fix of
 * #16931 is merged up from 7.0.x. Each datastore's change removes its own skips.
 */
@Issue('https://github.com/apache/grails-core/issues/14635')
class CriteriaEmptyJunctionSpec extends GrailsDataTckSpec {

    void setupSpec() {
        manager.registerDomainClasses(TestEntity, ChildEntity)
    }

    /** with has a child, other has a child, orphan has none */
    private void seed() {
        new TestEntity(name: 'with', child: new ChildEntity(name: 'kid')).save(flush: true, failOnError: true)
        new TestEntity(name: 'other', child: new ChildEntity(name: 'stranger')).save(flush: true, failOnError: true)
        new TestEntity(name: 'orphan').save(flush: true, failOnError: true)
        manager.session.clear()
    }

    /** a query that fails is the string THROWS, whatever the datastore calls the error */
    private static Object outcome(Closure query) {
        try {
            return query.call()
        }
        catch (Throwable ignored) {
            return 'THROWS'
        }
    }

    @Unroll
    @IgnoreIf({ data.skip.any { String store -> System.getProperty(store + '.gorm.suite') == 'true' } })
    void 'an empty junction in #form'() {
        given:
        seed()

        when:
        Object found = outcome { -> TestEntity.createCriteria().list(body)*.name.unique().sort() }

        then:
        found == expected

        where:
        [form, body, expected, skip] << LISTS
    }

    @Unroll
    @IgnoreIf({ data.skip.any { String store -> System.getProperty(store + '.gorm.suite') == 'true' } })
    void 'an empty junction in the query form #form'() {
        given:
        seed()

        when:
        Object found = outcome(body)

        then:
        found == expected

        where:
        [form, body, expected, skip] << QUERIES
    }

    private static final List<List> LISTS = [
        ['or {}', { -> or { } }, ['orphan', 'other', 'with'], ['simple', 'neo4j']],
        ['and {}', { -> and { } }, ['orphan', 'other', 'with'], ['simple', 'neo4j']],
        ['not { or {} }', { -> not { or { } } }, [], ['simple', 'neo4j']],
        ['not { and {} }', { -> not { and { } } }, [], ['simple', 'neo4j']],
        ['or {} + eq name=orphan', { -> or { }; eq('name', 'orphan') }, ['orphan'], ['simple', 'neo4j']],
        ['and {} + eq name=orphan', { -> and { }; eq('name', 'orphan') }, ['orphan'], ['simple', 'neo4j']],
        ['not { or {} } + eq name=orphan', { -> not { or { } }; eq('name', 'orphan') }, [], ['simple', 'neo4j']],
        ['not { and {} } + eq name=orphan', { -> not { and { } }; eq('name', 'orphan') }, [], ['simple', 'neo4j']],
        ['or { or {}; eq name=orphan }', { -> or { or { }; eq('name', 'orphan') } }, ['orphan', 'other', 'with'], ['simple', 'neo4j']],
        ['or { and {}; eq name=orphan }', { -> or { and { }; eq('name', 'orphan') } }, ['orphan', 'other', 'with'], ['simple', 'neo4j']],
        ['and { or {}; eq name=orphan }', { -> and { or { }; eq('name', 'orphan') } }, ['orphan'], ['simple', 'neo4j']],
        ['and { and {}; eq name=orphan }', { -> and { and { }; eq('name', 'orphan') } }, ['orphan'], ['simple', 'neo4j']],
        ['child { or {} }', { -> child { or { } } }, ['other', 'with'], ['simple', 'neo4j', 'mongodb']],
        ['child { and {} }', { -> child { and { } } }, ['other', 'with'], ['simple', 'neo4j', 'mongodb']],
        ['child { not { or {} } }', { -> child { not { or { } } } }, [], ['simple', 'neo4j', 'mongodb']],
        ['child { not { and {} } }', { -> child { not { and { } } } }, [], ['simple', 'neo4j', 'mongodb']],
        ['child { or {}; eq name=kid }', { -> child { or { }; eq('name', 'kid') } }, ['with'], ['simple', 'neo4j', 'mongodb']],
        ['child { and {}; eq name=kid }', { -> child { and { }; eq('name', 'kid') } }, ['with'], ['simple', 'neo4j', 'mongodb']],
        ['child { not { and {} }; eq name=kid }', { -> child { not { and { } }; eq('name', 'kid') } }, [], ['simple', 'neo4j', 'mongodb']],
        ['child { not { or {} }; eq name=kid }', { -> child { not { or { } }; eq('name', 'kid') } }, [], ['simple', 'neo4j', 'mongodb']],
        ['or { child { and {} }; eq name=orphan }', { -> or { child { and { } }; eq('name', 'orphan') } }, ['orphan', 'other', 'with'], ['simple', 'neo4j', 'mongodb', 'hibernate5']],
        ['or { child { or {} }; eq name=orphan }', { -> or { child { or { } }; eq('name', 'orphan') } }, ['orphan', 'other', 'with'], ['simple', 'neo4j', 'mongodb', 'hibernate5']],
        ['child {} (an empty block)', { -> child { } }, ['other', 'with'], ['simple', 'neo4j', 'mongodb']],
        ['or { child {}; eq name=orphan }', { -> or { child { }; eq('name', 'orphan') } }, [], ['simple', 'neo4j', 'mongodb']],
        ['or { or { child {} }; eq name=with }', { -> or { or { child { } }; eq('name', 'with') } }, ['other', 'with'], ['neo4j', 'mongodb']],
        ['or { and { child {} }; eq name=with }', { -> or { and { child { } }; eq('name', 'with') } }, ['other', 'with'], ['neo4j', 'mongodb']],
        ['or { or { or { child {} } }; eq name=with }', { -> or { or { or { child { } } }; eq('name', 'with') } }, ['other', 'with'], ['neo4j', 'mongodb']],
        ['or { or { child {} }; eq name=orphan }', { -> or { or { child { } }; eq('name', 'orphan') } }, ['other', 'with'], ['neo4j', 'mongodb']],
        ['and { or { child {} }; eq name=with }', { -> and { or { child { } }; eq('name', 'with') } }, ['with'], ['neo4j', 'mongodb']],
        ['and { or { child {} }; eq name=orphan }', { -> and { or { child { } }; eq('name', 'orphan') } }, [], ['neo4j', 'mongodb']],
        ['not { not { or {} } }', { -> not { not { or { } } } }, ['orphan', 'other', 'with'], ['simple', 'neo4j']],
        ['or { and { or {} } }', { -> or { and { or { } } } }, ['orphan', 'other', 'with'], ['simple', 'neo4j']],
        ['and { or { or {} } }', { -> and { or { or { } } } }, ['orphan', 'other', 'with'], ['simple', 'neo4j']],
        ['or { or {}; and {} }', { -> or { or { }; and { } } }, ['orphan', 'other', 'with'], ['simple', 'neo4j']],
        ['and { or {}; or {} }', { -> and { or { }; or { } } }, ['orphan', 'other', 'with'], ['simple', 'neo4j']],
        ['or { or {} }', { -> or { or { } } }, ['orphan', 'other', 'with'], ['simple', 'neo4j']],
        ['not {}', { -> not { } }, 'THROWS', ['simple', 'neo4j']],
        ['not { or {}; eq name=orphan }', { -> not { or { }; eq('name', 'orphan') } }, [], ['simple', 'neo4j']],
        ['not { and {}; eq name=orphan }', { -> not { and { }; eq('name', 'orphan') } }, [], ['simple', 'neo4j']],
        ['not { child { or {} } }', { -> not { child { or { } } } }, [], ['simple', 'neo4j', 'mongodb']],
        ['child { or { or {}; eq name=kid } }', { -> child { or { or { }; eq('name', 'kid') } } }, ['other', 'with'], ['simple', 'neo4j', 'mongodb']],
        ['or { child { not { or {} } }; eq name=orphan }', { -> or { child { not { or { } } }; eq('name', 'orphan') } }, ['orphan'], ['simple', 'neo4j', 'mongodb', 'hibernate5']],
        ['or { eq name=orphan; or {} }', { -> or { eq('name', 'orphan'); or { } } }, ['orphan', 'other', 'with'], ['simple', 'neo4j']],
        ['or { eq name=orphan; and {} }', { -> or { eq('name', 'orphan'); and { } } }, ['orphan', 'other', 'with'], ['simple', 'neo4j']],
        ['and { eq name=orphan; or {} }', { -> and { eq('name', 'orphan'); or { } } }, ['orphan'], ['simple', 'neo4j']]
    ]

    private static final List<List> QUERIES = [
        ['count { or {} }', { -> TestEntity.createCriteria().count { or { } } }, 3, ['simple', 'neo4j']],
        ['count { not { or {} } }', { -> TestEntity.createCriteria().count { not { or { } } } }, 0, ['simple', 'neo4j']],
        ['count { not { and {} } }', { -> TestEntity.createCriteria().count { not { and { } } } }, 0, ['simple', 'neo4j']],
        ['get { not { and {} } } (no row matches)', { -> TestEntity.createCriteria().get { not { and { } } }?.name }, null, ['simple', 'neo4j']],
        ['get { or {}; eq name=orphan }', { -> TestEntity.createCriteria().get { or { }; eq('name', 'orphan') }?.name }, 'orphan', ['simple', 'neo4j']],
        ['list(max:1) { or {}; order name }', { -> TestEntity.createCriteria().list(max: 1) { or { }; order('name') }*.name }, ['orphan'], ['simple', 'neo4j']],
        ['list(max:1) { not { and {} }; order name }', { -> TestEntity.createCriteria().list(max: 1) { not { and { } }; order('name') }*.name }, [], ['simple', 'neo4j']],
        ['projections name { or {} }', { -> TestEntity.createCriteria().list { projections { property('name') }; or { } }.sort() }, ['orphan', 'other', 'with'], ['simple', 'neo4j']],
        ['DetachedCriteria { or {} } list', { -> new grails.gorm.DetachedCriteria(TestEntity).build { or { } }.list()*.name.sort() }, ['orphan', 'other', 'with'], ['simple', 'neo4j']],
        ['DetachedCriteria { not { and {} } } list', { -> new grails.gorm.DetachedCriteria(TestEntity).build { not { and { } } }.list()*.name.sort() }, [], ['simple', 'neo4j']],
        ['DetachedCriteria { or {} } count', { -> new grails.gorm.DetachedCriteria(TestEntity).build { or { } }.count() }, 3, ['simple', 'neo4j']],
        ['inList id of a detached { or {} }', { -> TestEntity.createCriteria().list { inList('id', new grails.gorm.DetachedCriteria(TestEntity).build { or { } }.id()) }*.name.sort() }, ['orphan', 'other', 'with'], ['simple', 'neo4j', 'mongodb']],
        ['inList id of a detached { not { and {} } }', { -> TestEntity.createCriteria().list { inList('id', new grails.gorm.DetachedCriteria(TestEntity).build { not { and { } } }.id()) }*.name.sort() }, [], ['simple', 'neo4j']]
    ]
}

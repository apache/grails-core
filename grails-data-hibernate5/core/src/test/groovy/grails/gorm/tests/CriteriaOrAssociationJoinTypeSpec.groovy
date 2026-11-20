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

import org.hibernate.criterion.CriteriaSpecification
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

import grails.gorm.annotation.Entity
import grails.gorm.transactions.Rollback
import org.grails.orm.hibernate.HibernateDatastore

/**
 * An association block that names its join type keeps it: a block that asks for a left join finds the entities without
 * the association, whether it is a branch of an {@code or}, or follows a block in one that joined it with a left join
 * because it is a branch of the {@code or}.
 */
@Rollback
class CriteriaOrAssociationJoinTypeSpec extends Specification {

    @Shared
    @AutoCleanup
    HibernateDatastore datastore = new HibernateDatastore(JoinParent, JoinChild)

    private void createParents() {
        new JoinParent(name: 'with', child: new JoinChild(name: 'kid')).save(flush: true)
        new JoinParent(name: 'other', child: new JoinChild(name: 'stranger')).save(flush: true)
        new JoinParent(name: 'orphan').save(flush: true)
    }

    void 'a block that asks for a left join and is a branch of or keeps the entities without the association'() {
        given:
        createParents()

        when:
        def found = JoinParent.createCriteria().list {
            or {
                child(CriteriaSpecification.LEFT_JOIN) { isNull('name') }
                eq('name', 'nobody')
            }
        }

        then:
        found*.name == ['orphan']
    }

    void 'a later block that asks for a left join keeps the entities without the association'() {
        given:
        createParents()

        when:
        def found = JoinParent.createCriteria().list {
            or {
                child { eq('name', 'kid') }
                eq('name', 'orphan')
            }
            child(CriteriaSpecification.LEFT_JOIN) { isNull('name') }
        }

        then:
        found*.name == ['orphan']
    }
    @Unroll
    void 'after an or that asks for the child, a second block #shape gives #expected'() {
        given:
        createParents()
        Closure second = block

        when:
        def found = JoinParent.createCriteria().list {
            or {
                child { eq('name', 'kid') }
                eq('name', 'orphan')
            }
            with(second)
        }

        then: 'with has the child kid, other has stranger, orphan has none'
        found*.name.sort() == expected

        where:
        shape                                               | block                                                                                   || expected
        'top level, plain block'                            | { child { eq('name', 'kid') } }                                                         || ['with']
        'top level, inner join'                             | { child(CriteriaSpecification.INNER_JOIN) { eq('name', 'kid') } }                       || ['with']
        'top level, left join with isNull'                  | { child(CriteriaSpecification.LEFT_JOIN) { isNull('name') } }                           || ['orphan']
        'top level, left join with a value'                 | { child(CriteriaSpecification.LEFT_JOIN) { eq('name', 'kid') } }                        || ['with']
        'top level, without criteria'                       | { child { } }                                                                           || ['with']
        'a branch of or, plain block'                       | { or { eq('name', 'orphan'); child { eq('name', 'kid') } } }                            || ['orphan', 'with']
        'a branch of or, left join'                         | { or { eq('name', 'orphan'); child(CriteriaSpecification.LEFT_JOIN) { eq('name', 'kid') } } } || ['orphan', 'with']
        'a branch of or, inner join'                        | { or { eq('name', 'orphan'); child(CriteriaSpecification.INNER_JOIN) { eq('name', 'kid') } } } || ['with']
        'a branch of or, inner join without criteria (not a branch: the entity must have a child)' | { or { eq('name', 'orphan'); child(CriteriaSpecification.INNER_JOIN) { } } } || []
        'in a not, inner join'                              | { not { child(CriteriaSpecification.INNER_JOIN) { eq('name', 'stranger') } } }          || ['with']
    }

    void 'a block that names an inner join in a second or asks for the association on the whole query'() {
        given:
        createParents()

        when:
        def found = JoinParent.createCriteria().list {
            or {
                child { eq('name', 'kid') }
                eq('name', 'orphan')
            }
            or {
                eq('name', 'orphan')
                child(CriteriaSpecification.INNER_JOIN) { eq('name', 'kid') }
            }
        }

        then: 'orphan has no child, and the inner join asked for one'
        found*.name == ['with']
    }

    private static final List<Map> BLOCKS = [
            [n: 'left join', c: { child(CriteriaSpecification.LEFT_JOIN) { } }, p: { Map x -> true }],
            [n: 'left join eq kid', c: { child(CriteriaSpecification.LEFT_JOIN) { eq('name', 'kid') } }, p: { Map x -> x.child == 'kid' }],
            [n: 'left join isNull', c: { child(CriteriaSpecification.LEFT_JOIN) { isNull('name') } }, p: { Map x -> x.child == null }],
            [n: 'inner join', c: { child(CriteriaSpecification.INNER_JOIN) { } }, p: { Map x -> x.child != null }],
            [n: 'inner join eq kid', c: { child(CriteriaSpecification.INNER_JOIN) { eq('name', 'kid') } }, p: { Map x -> x.child == 'kid' }],
            [n: 'plain', c: { child { } }, p: { Map x -> x.child != null }],
            [n: 'plain eq kid', c: { child { eq('name', 'kid') } }, p: { Map x -> x.child == 'kid' }],
            [n: 'or of orphan and kid', c: { or { eq('name', 'orphan'); child { eq('name', 'kid') } } }, p: { Map x -> x.name == 'orphan' || x.child == 'kid' }],
            [n: 'no block', c: { }, p: { Map x -> true }]
    ]

    void 'two blocks on the child find the entities that meet both, whichever asked for its join first'() {
        given:
        createParents()
        List<Map> data = [[name: 'with', child: 'kid'], [name: 'other', child: 'stranger'], [name: 'orphan', child: null]]
        List<String> mismatches = []

        when:
        BLOCKS.each { Map first ->
            BLOCKS.each { Map second ->
                List<String> expected = data.findAll { Map x -> first.p.call(x) && second.p.call(x) }*.name.sort()
                Closure one = first.c
                Closure two = second.c
                List<String> found = JoinParent.createCriteria().list {
                    with(one)
                    with(two)
                }*.name.sort()
                if (found != expected) {
                    mismatches << "${first.n} then ${second.n}: found ${found}, expected ${expected}".toString()
                }
            }
        }

        then:
        mismatches == []
    }

}

@Entity
class JoinParent {

    String name
    JoinChild child

    static constraints = {
        child(nullable: true)
    }
}

@Entity
class JoinChild {

    String name
}

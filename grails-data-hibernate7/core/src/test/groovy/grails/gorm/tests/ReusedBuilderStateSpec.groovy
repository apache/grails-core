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

import org.hibernate.Hibernate
import org.hibernate.LockMode
import org.hibernate.Session
import spock.lang.Issue

import grails.gorm.annotation.Entity

/**
 * A criteria builder runs every list, listDistinct, get, count and scroll call as a new query: what a call
 * runs with is what the builder held when it started, and nothing the call set reaches the next one.
 */
@Issue('https://github.com/apache/grails-core/issues/16862')
class ReusedBuilderStateSpec extends HibernateGormDatastoreSpec {

    void setupSpec() {
        manager.registerDomainClasses(ReusedStateClub, ReusedStateTeam)
    }

    void setup() {
        def clubA = new ReusedStateClub(name: 'A').save(failOnError: true)
        def clubB = new ReusedStateClub(name: 'B').save(failOnError: true)
        new ReusedStateTeam(name: 'both', club: clubA).save(failOnError: true)
        new ReusedStateTeam(name: 'x', club: clubB).save(failOnError: true)
        ['y', 'z'].each { new ReusedStateTeam(name: it).save(failOnError: true) }
        ReusedStateTeam.withSession { it.flush(); it.clear() }
    }

    void 'second call on a reused builder matches a new builder: #label'() {
        when:
        def c = ReusedStateTeam.createCriteria()
        first(c)
        def reused = second(c)
        def fresh = second(ReusedStateTeam.createCriteria())

        then:
        norm(reused) == norm(fresh)

        where:
        label                      | first                                              | second
        'list then count'          | { it.list { eq('name', 'both') } }                 | { it.count { } }
        'list then count eq'       | { it.list { eq('name', 'both') } }                 | { it.count { eq('name', 'x') } }
        'list then get'            | { it.list { eq('name', 'both') } }                 | { it.get { eq('name', 'x') } }
        'list then listDistinct'   | { it.list { eq('name', 'both') } }                 | { it.listDistinct { } }
        'projected list then list' | { it.list { projections { property('name') } } }  | { it.list { } }
        'count then count'         | { it.count { } }                                   | { it.count { } }
        'count then list'          | { it.count { } }                                   | { it.list { } }
        'get then list'            | { it.get { eq('name', 'both') } }                  | { it.list { } }
        'get then count'           | { it.get { eq('name', 'both') } }                  | { it.count { } }
        'paged list then list'     | { it.list(max: 1) { } }                            | { it.list { } }
        'scroll then list'         | { it.scroll { }.close() }                          | { it.list { } }
        'listDistinct then count'  | { it.listDistinct { } }                            | { it.count { } }
        'firstResult then list'    | { it.list { firstResult(3) } }                     | { it.list { } }
        'maxResults then list'     | { it.list { maxResults(1) } }                      | { it.list { } }
        'throw then list'          | { throwing(it) { it.list { eq('name', 'both'); throw new IllegalStateException('x') } } } | { it.list() }
        'checked throw then list'  | { throwing(it) { it.list { eq('name', 'both'); throw new Exception('x') } } }             | { it.list() }
        'throw then count'         | { throwing(it) { it.list { eq('name', 'both'); throw new IllegalStateException('x') } } } | { it.count { } }
        'association throws'       | { throwing(it) { it.list { club { throw new IllegalStateException('x') } } } }            | { it.list { club { eq('name', 'A') } } }
        'association then list'    | { it.list { club { eq('name', 'A') } } }           | { it.list { eq('name', 'x') } }
        'list() then list'         | { it.eq('name', 'both'); it.list() }               | { it.list { eq('name', 'x') } }
        'list() then count'        | { it.eq('name', 'both'); it.list() }               | { it.count { } }
        'singleResult then list'   | { it.eq('name', 'both'); it.singleResult() }       | { it.list { } }
        'maxResults list() then list' | { it.maxResults(1); it.list() }                 | { it.list { } }
    }

    void 'a fetch join of one call does not reach the next call'() {
        given:
        def c = ReusedStateTeam.createCriteria()
        c.list { join 'club' }
        ReusedStateTeam.withSession { it.clear() }

        when:
        def team = c.list { eq('name', 'both') }[0]

        then:
        !Hibernate.isInitialized(team.club)
    }

    void 'what the builder holds when a call starts applies to that call: #label'() {
        given:
        def c = ReusedStateTeam.createCriteria()
        prepare(c)

        expect:
        size(action(c)) == expected

        where:
        label                       | prepare                        | action                                || expected
        'maxResults then list'      | { it.maxResults(1) }           | { it.list { } }                       || 1
        'maxResults then filtered'  | { it.maxResults(1) }           | { it.list { ne('name', 'x') } }       || 1
        'firstResult then list'     | { it.firstResult(3) }          | { it.list { } }                       || 1
        'criterion then count'      | { it.eq('name', 'both') }      | { it.count { } }                      || 1
        'criterion then list'       | { it.eq('name', 'both') }      | { it.list { } }                       || 1
        'maxResults then list()'    | { it.maxResults(1) }           | { it.list() }                         || 1
        'criterion then list()'     | { it.eq('name', 'both') }      | { it.list() }                         || 1
    }

    void 'a criterion held by the builder applies to a get'() {
        given:
        def c = ReusedStateTeam.createCriteria()
        c.eq('name', 'both')

        expect:
        c.get { }.name == 'both'
    }

    void 'a criterion held by the builder applies to singleResult and is gone afterwards'() {
        given:
        def c = ReusedStateTeam.createCriteria()
        c.eq('name', 'both')

        expect:
        c.singleResult().name == 'both'
        c.list { }.size() == 4
    }

    void 'a unique result requested before the call is the single instance'() {
        given:
        def c = ReusedStateTeam.createCriteria()
        c.uniqueResult = true

        expect:
        c.list { eq('name', 'both') }.name == 'both'
        c.list { }.size() == 4
    }

    void 'settings made on the builder apply to one call only'() {
        given:
        def c = ReusedStateTeam.createCriteria()
        c.maxResults(1)

        expect:
        c.list { }.size() == 1
        c.list { }.size() == 4
    }

    void 'the total of a page does not follow a later call on the builder: #label'() {
        given:
        def c = ReusedStateTeam.createCriteria()
        def page = c.list(max: 10) { eq('name', 'both') }

        when:
        later(c)

        then:
        page.size() == 1
        page.totalCount == 1

        where:
        label                | later
        'count'              | { it.count { } }
        'listDistinct'       | { it.listDistinct { } }
        'list'               | { it.list { } }
        'paged list'         | { it.list(max: 10) { } }
        'list that throws'   | { throwing(it) { it.list { throw new IllegalStateException('x') } } }
    }

    void 'the total of a paged first row does not include rows of a later count'() {
        given:
        def c = ReusedStateTeam.createCriteria()
        def page = c.list(max: 1) { eq('name', 'both') }

        when:
        c.count { }

        then:
        page.totalCount == 1
    }

    void 'readOnly set in the closure of one call does not reach the next call'() {
        given:
        def c = ReusedStateTeam.createCriteria()

        expect: 'the setting works for the call that makes it'
        readOnly(c.list { readOnly true; eq('name', 'y') }[0])

        when:
        ReusedStateTeam.withSession { it.clear() }
        def team = c.list { eq('name', 'y') }[0]

        then:
        !readOnly(team)
    }

    void 'lock #label'() {
        given:
        def c = ReusedStateTeam.createCriteria()

        when:
        def team = c.list { lock(flag); eq('name', 'y') }[0]

        then:
        lockMode(team) == mode

        where:
        label         | flag  | mode
        'true locks'  | true  | LockMode.PESSIMISTIC_WRITE
        'false reads' | false | LockMode.READ
    }

    void 'lock set in the closure of one call does not reach the next call'() {
        given:
        def c = ReusedStateTeam.createCriteria()
        c.list { lock true }
        ReusedStateTeam.withSession { it.clear() }

        when:
        def team = c.list { eq('name', 'y') }[0]

        then:
        lockMode(team) == LockMode.READ
    }

    void 'the builder targets its own class after an association block throws'() {
        given:
        def c = ReusedStateTeam.createCriteria()
        throwing(c) { c.list { club { throw new IllegalStateException('x') } } }

        expect:
        c.targetClass == ReusedStateTeam
    }

    private static void throwing(Object builder, Closure<?> call) {
        boolean thrown = false
        try {
            call(builder)
        } catch (Exception ignored) {
            thrown = true
        }
        assert thrown : 'the call was expected to throw'
    }

    private static int size(Object o) {
        o instanceof Collection ? o.size() : (o instanceof Number ? o.intValue() : 1)
    }

    private static boolean readOnly(Object entity) {
        ReusedStateTeam.withSession { Session session -> session.isReadOnly(entity) }
    }

    private static LockMode lockMode(Object entity) {
        ReusedStateTeam.withSession { Session session -> session.getCurrentLockMode(entity) }
    }

    private static Object norm(Object o) {
        o instanceof Collection ? o.collect { norm(it) }.sort { it.toString() } : (o instanceof ReusedStateTeam ? 'T:' + o.name : o)
    }
}

@Entity
class ReusedStateClub {
    String name
}

@Entity
class ReusedStateTeam {
    String name
    ReusedStateClub club
}

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

import grails.gorm.annotation.Entity
import grails.gorm.transactions.Rollback
import org.grails.orm.hibernate.HibernateDatastore

/**
 * A block on an association of the entity's own class is left-joined by default, and a later block that names an inner
 * join reuses that join: it still asks for an associated entity, so an entity without one is not returned.
 */
@Rollback
class CriteriaSelfReferenceInnerJoinSpec extends Specification {

    @Shared
    @AutoCleanup
    HibernateDatastore datastore = new HibernateDatastore(SelfRefEmployee)

    private static final List<Map> BLOCKS = [
            [n: 'plain', c: { manager { } }, inner: false],
            [n: 'plain boss', c: { manager { eq('name', 'boss') } }, inner: false],
            [n: 'inner', c: { manager(CriteriaSpecification.INNER_JOIN) { } }, inner: true],
            [n: 'inner boss', c: { manager(CriteriaSpecification.INNER_JOIN) { eq('name', 'boss') } }, inner: true],
            [n: 'or orphan|plain boss', c: { or { eq('name', 'orphan'); manager { eq('name', 'boss') } } }, inner: false],
            [n: 'or orphan|inner boss', c: { or { eq('name', 'orphan'); manager(CriteriaSpecification.INNER_JOIN) { eq('name', 'boss') } } }, inner: true],
            [n: 'or orphan|inner empty', c: { or { eq('name', 'orphan'); manager(CriteriaSpecification.INNER_JOIN) { } } }, inner: true],
            [n: 'no block', c: { }, inner: false]
    ]

    void 'three blocks on a self-referencing association, one of them naming an inner join, never return an entity without the association'() {
        given:
        SelfRefEmployee boss = new SelfRefEmployee(name: 'boss').save(flush: true)
        new SelfRefEmployee(name: 'worker', manager: boss).save(flush: true)
        new SelfRefEmployee(name: 'orphan').save(flush: true)
        List<String> bad = []

        when:
        BLOCKS.each { Map a -> BLOCKS.each { Map b -> BLOCKS.each { Map c ->
            if (!(a.inner || b.inner || c.inner)) return
            Closure one = a.c; Closure two = b.c; Closure three = c.c
            List<String> found
            try {
                found = SelfRefEmployee.createCriteria().list { with(one); with(two); with(three) }*.name.sort()
            } catch (Throwable e) {
                found = ['THROWS ' + e.class.simpleName]
            }
            // 'orphan' and 'boss' have no manager; every block that names an inner join asks for one
            if (found.contains('orphan') || found.contains('boss')) bad << "${a.n} / ${b.n} / ${c.n}: found ${found}".toString()
        } } }

        then:
        bad == []
    }
}

@Entity
class SelfRefEmployee {
    String name
    SelfRefEmployee manager
    static constraints = { manager(nullable: true) }
}

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

package functional.tests

import grails.gorm.DetachedCriteria
import grails.gorm.transactions.Rollback
import grails.testing.mixin.integration.Integration
import spock.lang.Specification

/**
 * Runs scalar subqueries in comparisons against a real database and checks the rows returned.
 * The qty values are 1, 2, 3 and null, so avg is 2, max is 3 and min is 1.
 */
@Integration(applicationClass = Application)
@Rollback
class CriteriaSubqueryScenarioSpec extends Specification {

    private void seed() {
        def ann = new ScenarioOwner(name: 'ann').save(flush: true, failOnError: true)
        def bob = new ScenarioOwner(name: 'bob').save(flush: true, failOnError: true)
        new ScenarioItem(name: 'a', qty: 1, status: ScenarioStatus.NEW, owner: ann).save(flush: true, failOnError: true)
        new ScenarioItem(name: 'b', qty: 2, status: ScenarioStatus.NEW, owner: ann).save(flush: true, failOnError: true)
        new ScenarioItem(name: 'c', qty: 3, status: ScenarioStatus.DONE, owner: bob).save(flush: true, failOnError: true)
        new ScenarioItem(name: 'd', qty: null, status: ScenarioStatus.DONE, owner: null).save(flush: true, failOnError: true)
        ScenarioItem.withSession { it.clear() }
    }

    private static List<String> names(Collection<ScenarioItem> items) {
        items*.name.sort()
    }

    void "where: scalar subquery gt avg"() {
        given:
        seed()

        when:
        def rows = ScenarioItem.where { qty > avg(qty) }.list()

        then:
        names(rows) == ['c']
    }

    void "where: scalar subquery ge avg"() {
        given:
        seed()

        when:
        def rows = ScenarioItem.where { qty >= avg(qty) }.list()

        then:
        names(rows) == ['b', 'c']
    }

    void "where: scalar subquery lt avg"() {
        given:
        seed()

        when:
        def rows = ScenarioItem.where { qty < avg(qty) }.list()

        then:
        names(rows) == ['a']
    }

    void "where: scalar subquery le avg"() {
        given:
        seed()

        when:
        def rows = ScenarioItem.where { qty <= avg(qty) }.list()

        then:
        names(rows) == ['a', 'b']
    }

    void "where: scalar subquery eq max"() {
        given:
        seed()

        when:
        def rows = ScenarioItem.where { qty == max(qty) }.list()

        then:
        names(rows) == ['c']
    }

    void "where: scalar subquery eq min"() {
        given:
        seed()

        when:
        def rows = ScenarioItem.where { qty == min(qty) }.list()

        then:
        names(rows) == ['a']
    }

    void "where: scalar subquery ne max leaves out null rows"() {
        given:
        seed()

        when:
        def rows = ScenarioItem.where { qty != max(qty) }.list()

        then:
        names(rows) == ['a', 'b']
    }

    void "where: scalar subquery combined with another criterion"() {
        given:
        seed()

        when:
        def rows = ScenarioItem.where { qty > avg(qty) || name == 'a' }.list()

        then:
        names(rows) == ['a', 'c']
    }

    void "where: in a subquery of the same entity"() {
        given:
        seed()
        def newQtys = ScenarioItem.where { status == ScenarioStatus.NEW }.property('qty')

        when:
        def rows = ScenarioItem.where { qty in newQtys }.list()

        then:
        names(rows) == ['a', 'b']
    }

    void "where: a subquery over no rows compares as null and matches nothing"() {
        given:
        seed()

        when:
        def rows = ScenarioItem.where { qty == max(qty) && name == 'zzz' }.list()

        then:
        rows.isEmpty()
    }

    void "criteria builder: gt, lt and eq against a subquery"() {
        given:
        seed()
        def avgQty = new DetachedCriteria(ScenarioItem).build { projections { avg 'qty' } }
        def maxQty = new DetachedCriteria(ScenarioItem).build { projections { max 'qty' } }

        when:
        def above = ScenarioItem.createCriteria().list { gt 'qty', avgQty }
        def below = ScenarioItem.createCriteria().list { lt 'qty', avgQty }
        def top = ScenarioItem.createCriteria().list { eq 'qty', maxQty }

        then:
        names(above) == ['c']
        names(below) == ['a']
        names(top) == ['c']
    }
}

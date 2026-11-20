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

import grails.gorm.transactions.Rollback
import grails.testing.mixin.integration.Integration
import spock.lang.Specification
import spock.lang.Unroll

/**
 * Runs each comparison and null criterion against a real database through the three public
 * query surfaces (where, the criteria builder and dynamic finders) and checks the returned rows,
 * not the shape of the predicate that was built.
 */
@Integration(applicationClass = Application)
@Rollback
class CriteriaComparisonScenarioSpec extends Specification {

    private void seed() {
        def ann = new ScenarioOwner(name: 'ann').save(flush: true, failOnError: true)
        def bob = new ScenarioOwner(name: 'bob').save(flush: true, failOnError: true)
        new ScenarioItem(name: 'a', qty: 1, status: ScenarioStatus.NEW, owner: ann, dims: new ScenarioDims(width: 10)).save(flush: true, failOnError: true)
        new ScenarioItem(name: 'b', qty: 2, status: ScenarioStatus.NEW, owner: ann, dims: new ScenarioDims(width: 20)).save(flush: true, failOnError: true)
        new ScenarioItem(name: 'c', qty: 3, status: ScenarioStatus.DONE, owner: bob, dims: new ScenarioDims(width: 30)).save(flush: true, failOnError: true)
        new ScenarioItem(name: 'd', qty: null, status: ScenarioStatus.DONE, owner: null, dims: null).save(flush: true, failOnError: true)
        ScenarioItem.withSession { it.clear() }
    }

    private static List<String> names(Collection<ScenarioItem> items) {
        items*.name.sort()
    }

    void "where: simple eq"() {
        given:
        seed()

        when:
        def rows = ScenarioItem.where { qty == 2 }.list()

        then:
        names(rows) == ['b']
    }

    void "where: != leaves out rows whose value is null, as SQL does"() {
        given:
        seed()

        when:
        def rows = ScenarioItem.where { qty != 2 }.list()

        then:
        names(rows) == ['a', 'c']
    }

    void "where: simple gt"() {
        given:
        seed()

        when:
        def rows = ScenarioItem.where { qty > 1 }.list()

        then:
        names(rows) == ['b', 'c']
    }

    void "where: simple ge"() {
        given:
        seed()

        when:
        def rows = ScenarioItem.where { qty >= 2 }.list()

        then:
        names(rows) == ['b', 'c']
    }

    void "where: simple lt"() {
        given:
        seed()

        when:
        def rows = ScenarioItem.where { qty < 3 }.list()

        then:
        names(rows) == ['a', 'b']
    }

    void "where: simple le"() {
        given:
        seed()

        when:
        def rows = ScenarioItem.where { qty <= 2 }.list()

        then:
        names(rows) == ['a', 'b']
    }

    void "where: simple is null"() {
        given:
        seed()

        when:
        def rows = ScenarioItem.where { qty == null }.list()

        then:
        names(rows) == ['d']
    }

    void "where: simple is not null"() {
        given:
        seed()

        when:
        def rows = ScenarioItem.where { qty != null }.list()

        then:
        names(rows) == ['a', 'b', 'c']
    }

    void "where: simple in list"() {
        given:
        seed()

        when:
        def rows = ScenarioItem.where { qty in [1, 3] }.list()

        then:
        names(rows) == ['a', 'c']
    }

    void "where: simple and"() {
        given:
        seed()

        when:
        def rows = ScenarioItem.where { qty > 1 && name != 'c' }.list()

        then:
        names(rows) == ['b']
    }

    void "where: simple or"() {
        given:
        seed()

        when:
        def rows = ScenarioItem.where { qty == 1 || qty == 3 }.list()

        then:
        names(rows) == ['a', 'c']
    }

    void "where: a negation leaves out the rows whose value is null, as != does"() {
        given:
        seed()

        when:
        def rows = ScenarioItem.where { !(qty == 1) }.list()

        then:
        names(rows) == ['b', 'c']
    }

    void "where: simple range"() {
        given:
        seed()

        when:
        def rows = ScenarioItem.where { qty in 1..2 }.list()

        then:
        names(rows) == ['a', 'b']
    }

    void "where: enum eq"() {
        given:
        seed()

        when:
        def rows = ScenarioItem.where { status == ScenarioStatus.DONE }.list()

        then:
        names(rows) == ['c', 'd']
    }

    void "where: enum ne"() {
        given:
        seed()

        when:
        def rows = ScenarioItem.where { status != ScenarioStatus.DONE }.list()

        then:
        names(rows) == ['a', 'b']
    }

    void "where: enum in list"() {
        given:
        seed()

        when:
        def rows = ScenarioItem.where { status in [ScenarioStatus.NEW] }.list()

        then:
        names(rows) == ['a', 'b']
    }

    void "where: association is null"() {
        given:
        seed()

        when:
        def rows = ScenarioItem.where { owner == null }.list()

        then:
        names(rows) == ['d']
    }

    void "where: association is not null"() {
        given:
        seed()

        when:
        def rows = ScenarioItem.where { owner != null }.list()

        then:
        names(rows) == ['a', 'b', 'c']
    }

    void "where: association property eq"() {
        given:
        seed()

        when:
        def rows = ScenarioItem.where { owner.name == 'ann' }.list()

        then:
        names(rows) == ['a', 'b']
    }

    void "where: association property ne"() {
        given:
        seed()

        when:
        def rows = ScenarioItem.where { owner.name != 'ann' }.list()

        then:
        names(rows) == ['c']
    }

    void "where: embedded property eq"() {
        given:
        seed()

        when:
        def rows = ScenarioItem.where { dims.width == 20 }.list()

        then:
        names(rows) == ['b']
    }

    void "where: embedded property gt"() {
        given:
        seed()

        when:
        def rows = ScenarioItem.where { dims.width > 10 }.list()

        then:
        names(rows) == ['b', 'c']
    }

    @Unroll
    void "criteria builder: #op"() {
        given:
        seed()

        when:
        def rows = ScenarioItem.createCriteria().list(query)

        then:
        names(rows) == expected

        where:
        op                | query                                              || expected
        'eq'              | { eq 'qty', 2 }                                    || ['b']
        'ne null'         | { ne 'qty', null }                                 || ['a', 'b', 'c']
        'gt'              | { gt 'qty', 1 }                                    || ['b', 'c']
        'ge'              | { ge 'qty', 2 }                                    || ['b', 'c']
        'lt'              | { lt 'qty', 3 }                                    || ['a', 'b']
        'le'              | { le 'qty', 2 }                                    || ['a', 'b']
        'between'         | { between 'qty', 1, 2 }                            || ['a', 'b']
        'isNull'          | { isNull 'qty' }                                   || ['d']
        'isNotNull'       | { isNotNull 'qty' }                                || ['a', 'b', 'c']
        'inList'          | { 'in' 'qty', [1, 3] }                             || ['a', 'c']
        'not'             | { not { eq 'qty', 1 } }                            || ['b', 'c']
        'or'              | { or { eq 'qty', 1; eq 'qty', 3 } }                || ['a', 'c']
        'association'     | { owner { eq 'name', 'ann' } }                     || ['a', 'b']
        'embedded'        | { eq 'dims.width', 20 }                            || ['b']
    }

    void "criteria builder: ne leaves out rows whose value is null, as SQL does"() {
        given:
        seed()

        when:
        def rows = ScenarioItem.createCriteria().list { ne 'qty', 2 }

        then:
        names(rows) == ['a', 'c']
    }

    void "dynamic finders compare, negate and test null"() {
        given:
        seed()

        expect:
        names(ScenarioItem.findAllByQtyGreaterThan(1)) == ['b', 'c']
        names(ScenarioItem.findAllByQtyLessThanEquals(2)) == ['a', 'b']
        names(ScenarioItem.findAllByQtyBetween(1, 2)) == ['a', 'b']
        names(ScenarioItem.findAllByQtyIsNull()) == ['d']
        names(ScenarioItem.findAllByQtyIsNotNull()) == ['a', 'b', 'c']
        names(ScenarioItem.findAllByStatusAndQtyGreaterThan(ScenarioStatus.NEW, 1)) == ['b']
        names(ScenarioItem.findAllByStatusOrQty(ScenarioStatus.DONE, 1)) == ['a', 'c', 'd']
        names(ScenarioItem.findAllByOwner(ScenarioOwner.findByName('ann'))) == ['a', 'b']
        names(ScenarioItem.findAllByOwnerIsNull()) == ['d']
    }

    void "count follows the same criteria"() {
        given:
        seed()

        when:
        def many = ScenarioItem.where { qty > 1 }.count()
        def nulls = ScenarioItem.where { qty == null }.count()
        def none = ScenarioItem.where { qty > 99 }.count()
        def present = ScenarioItem.where { owner.name == 'bob' }.count()
        def absent = ScenarioItem.where { owner.name == 'nobody' }.count()

        then:
        many == 2
        nulls == 1
        none == 0
        present == 1
        absent == 0
    }
}

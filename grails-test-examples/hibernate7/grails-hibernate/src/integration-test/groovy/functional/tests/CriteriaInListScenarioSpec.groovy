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

/**
 * Runs inList with null elements, reused subquery criteria and unresolved property names
 * against a real database. The qty values are 1, 2 and null.
 */
@Integration(applicationClass = Application)
@Rollback
class CriteriaInListScenarioSpec extends Specification {

    private void seed() {
        def ann = new ScenarioOwner(name: 'ann').save(flush: true, failOnError: true)
        new ScenarioItem(name: 'a', qty: 1, status: ScenarioStatus.NEW, owner: ann).save(flush: true, failOnError: true)
        new ScenarioItem(name: 'b', qty: 2, status: ScenarioStatus.NEW, owner: ann).save(flush: true, failOnError: true)
        new ScenarioItem(name: 'd', qty: null, status: ScenarioStatus.DONE, owner: null).save(flush: true, failOnError: true)
        ScenarioItem.withSession { it.clear() }
    }

    private static List<String> names(Collection<ScenarioItem> items) {
        items*.name.sort()
    }

    void "criteria: inList ignores a null element and matches the rest"() {
        given:
        seed()

        expect:
        names(ScenarioItem.createCriteria().list { inList 'qty', [1, null] }) == ['a']
    }

    void "criteria: inList of only nulls matches nothing"() {
        given:
        seed()

        expect:
        names(ScenarioItem.createCriteria().list { inList 'qty', [null] }) == []
    }

    void "criteria: inList of an empty list matches nothing"() {
        given:
        seed()

        expect:
        names(ScenarioItem.createCriteria().list { inList 'qty', [] }) == []
    }

    void "where: in with a null element matches the rest"() {
        given:
        seed()

        when:
        def rows = ScenarioItem.where { qty in [1, null] }.list()

        then:
        names(rows) == ['a']
    }

    void "where: in with an empty list matches nothing"() {
        given:
        seed()

        when:
        def rows = ScenarioItem.where { qty in [] }.list()

        then:
        names(rows) == []
    }

    void "subquery criteria can be used by two queries"() {
        given:
        seed()
        def annIds = ScenarioOwner.where { name == 'ann' }.property('id')

        when:
        def first = ScenarioItem.where { owner.id in annIds }.list()
        def second = ScenarioItem.where { owner.id in annIds }.list()

        then:
        names(first) == ['a', 'b']
        names(second) == ['a', 'b']
    }

    void "criteria: an unknown property fails instead of matching everything"() {
        given:
        seed()

        when:
        ScenarioItem.createCriteria().list { eq 'nope', 1 }

        then:
        def e = thrown(Exception)
        e.message.contains('nope')
    }

    void "criteria: isNull on an unknown property fails"() {
        given:
        seed()

        when:
        ScenarioItem.createCriteria().list { isNull 'nope' }

        then:
        def e = thrown(Exception)
        e.message.contains('nope')
    }

    void "criteria: eqProperty against an unknown property fails"() {
        given:
        seed()

        when:
        ScenarioItem.createCriteria().list { eqProperty 'qty', 'nope' }

        then:
        def e = thrown(Exception)
        e.message.contains('nope')
    }
}

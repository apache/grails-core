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
 * Runs subqueries that refer to the alias of the outer query, directly and through an association,
 * against a real database.
 */
@Integration(applicationClass = Application)
@Rollback
class SubqueryAliasScenarioSpec extends Specification {

    private void seed() {
        def ann = new ScenarioOwner(name: 'ann').save(flush: true, failOnError: true)
        def bob = new ScenarioOwner(name: 'bob').save(flush: true, failOnError: true)
        new ScenarioOwner(name: 'zed').save(flush: true, failOnError: true)
        new ScenarioItem(name: 'a', qty: 1, status: ScenarioStatus.NEW, owner: ann).save(flush: true, failOnError: true)
        new ScenarioItem(name: 'b', qty: 2, status: ScenarioStatus.NEW, owner: ann).save(flush: true, failOnError: true)
        new ScenarioItem(name: 'c', qty: 3, status: ScenarioStatus.DONE, owner: bob).save(flush: true, failOnError: true)
        new ScenarioItem(name: 'd', qty: null, status: ScenarioStatus.DONE, owner: null).save(flush: true, failOnError: true)
        ScenarioItem.withSession { it.clear() }
    }

    private static List<String> names(Collection<ScenarioItem> items) {
        items*.name.sort()
    }

    void "exists with a subquery correlated on the root alias"() {
        given:
        seed()

        when:
        def rows = ScenarioItem.where {
            def i = ScenarioItem
            exists(ScenarioOwner.where { id == i.owner }.property('name'))
        }.list()

        then:
        names(rows) == ['a', 'b', 'c']
    }

    void "exists with a subquery correlated on the root alias and another restriction"() {
        given:
        seed()

        when:
        def rows = ScenarioItem.where {
            def i = ScenarioItem
            status == ScenarioStatus.NEW
            exists(ScenarioOwner.where { id == i.owner }.property('name'))
        }.list()

        then:
        names(rows) == ['a', 'b']
    }

    void "not exists with a subquery correlated on the root alias"() {
        given:
        seed()

        when:
        def rows = ScenarioItem.where {
            def i = ScenarioItem
            notExists(ScenarioOwner.where { id == i.owner }.property('name'))
        }.list()

        then:
        names(rows) == ['d']
    }

    void "a subquery compares with the id reached through an association of the alias"() {
        given:
        seed()

        when:
        def rows = ScenarioItem.where {
            def i = ScenarioItem
            exists(ScenarioOwner.where { id == i.owner.id }.property('name'))
        }.list()

        then:
        names(rows) == ['a', 'b', 'c']
    }
}

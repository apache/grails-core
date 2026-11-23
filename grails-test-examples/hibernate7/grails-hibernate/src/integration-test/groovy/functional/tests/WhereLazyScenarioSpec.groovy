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

import java.util.concurrent.Callable
import java.util.concurrent.Executors

import grails.gorm.DetachedCriteria
import grails.testing.mixin.integration.Integration
import spock.lang.Specification

/**
 * Runs criteria built with whereLazy against a real database: the same criteria used again,
 * as a subquery, for bulk writes and from several threads. The rows are committed so that
 * other threads can see them, and removed again after each scenario.
 */
@Integration(applicationClass = Application)
class WhereLazyScenarioSpec extends Specification {

    void setup() {
        ScenarioItem.withNewTransaction {
            def ann = new ScenarioOwner(name: 'ann').save(flush: true, failOnError: true)
            def bob = new ScenarioOwner(name: 'bob').save(flush: true, failOnError: true)
            new ScenarioItem(name: 'a', qty: 1, status: ScenarioStatus.NEW, owner: ann).save(flush: true, failOnError: true)
            new ScenarioItem(name: 'b', qty: 2, status: ScenarioStatus.NEW, owner: ann).save(flush: true, failOnError: true)
            new ScenarioItem(name: 'c', qty: 3, status: ScenarioStatus.DONE, owner: bob).save(flush: true, failOnError: true)
        }
    }

    void cleanup() {
        ScenarioItem.withNewTransaction {
            ScenarioItem.list()*.delete(flush: true)
            ScenarioOwner.list()*.delete(flush: true)
        }
    }

    private static <T> T inSession(Closure<T> work) {
        ScenarioItem.withNewSession(work)
    }

    private static List<String> names(Collection<ScenarioItem> items) {
        items*.name.sort()
    }

    void "a lazy criteria returns the same rows each time it is used"() {
        given:
        DetachedCriteria<ScenarioItem> crit = ScenarioItem.whereLazy { qty >= 2 }

        when:
        def first = inSession { names(crit.list()) }
        def second = inSession { names(crit.list()) }
        def total = inSession { crit.count() }

        then:
        first == ['b', 'c']
        second == ['b', 'c']
        total == 2
    }

    void "a lazy criteria can be narrowed by a second where"() {
        given:
        DetachedCriteria<ScenarioItem> crit = ScenarioItem.whereLazy { qty >= 2 }

        when:
        def narrowed = inSession { names(crit.where { status == ScenarioStatus.DONE }.list()) }
        def original = inSession { names(crit.list()) }

        then:
        narrowed == ['c']
        original == ['b', 'c']
    }

    void "a lazy criteria keeps its restriction as a subquery"() {
        given:
        def annIds = ScenarioOwner.whereLazy { name == 'ann' }.property('id')

        when:
        def rows = inSession { ScenarioItem.where { owner.id in annIds }.list() }
        def again = inSession { ScenarioItem.where { owner.id in annIds }.list() }

        then:
        names(rows) == ['a', 'b']
        names(again) == ['a', 'b']
    }

    void "a lazy criteria limits updateAll and deleteAll to its rows"() {
        given:
        DetachedCriteria<ScenarioItem> crit = ScenarioItem.whereLazy { qty == 1 }

        when:
        def updated = ScenarioItem.withNewTransaction { crit.updateAll(status: ScenarioStatus.DONE) }

        then:
        updated == 1
        ScenarioItem.withNewSession { ScenarioItem.countByStatus(ScenarioStatus.DONE) } == 2

        when:
        def deleted = ScenarioItem.withNewTransaction { crit.deleteAll() }

        then:
        deleted == 1
        ScenarioItem.withNewSession { ScenarioItem.count() } == 2
    }

    void "one lazy criteria shared by several threads gives each the same rows"() {
        given:
        DetachedCriteria<ScenarioItem> crit = ScenarioItem.whereLazy { qty >= 2 }
        def pool = Executors.newFixedThreadPool(8)

        when:
        def futures = (1..32).collect {
            pool.submit({
                ScenarioItem.withNewSession { names(crit.list()) }
            } as Callable)
        }
        def results = futures*.get()

        then:
        results.every { it == ['b', 'c'] }

        cleanup:
        pool.shutdownNow()
    }
}

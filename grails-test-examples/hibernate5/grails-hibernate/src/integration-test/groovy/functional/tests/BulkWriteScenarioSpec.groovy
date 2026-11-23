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

import grails.testing.mixin.integration.Integration
import spock.lang.Specification

/**
 * Runs deleteAll and updateAll for the query shapes a bulk write has to translate, and checks
 * the rows that are left. The rows are committed and removed after each scenario.
 */
@Integration(applicationClass = Application)
class BulkWriteScenarioSpec extends Specification {

    void setup() {
        ScenarioItem.withNewTransaction {
            def ann = new ScenarioOwner(name: 'ann').save(flush: true, failOnError: true)
            def bob = new ScenarioOwner(name: 'bob').save(flush: true, failOnError: true)
            new ScenarioItem(name: 'a', qty: 1, status: ScenarioStatus.NEW, owner: ann).save(flush: true, failOnError: true)
            new ScenarioItem(name: 'b', qty: 2, status: ScenarioStatus.NEW, owner: ann).save(flush: true, failOnError: true)
            new ScenarioItem(name: 'c', qty: 3, status: ScenarioStatus.DONE, owner: bob).save(flush: true, failOnError: true)
            new ScenarioItem(name: 'd', qty: null, status: ScenarioStatus.DONE, owner: null).save(flush: true, failOnError: true)
        }
    }

    void cleanup() {
        ScenarioItem.withNewTransaction {
            ScenarioItem.list()*.delete(flush: true)
            ScenarioOwner.list()*.delete(flush: true)
        }
    }

    private static List<String> left() {
        ScenarioItem.withNewSession { ScenarioItem.list()*.name.sort() }
    }

    private static Map<String, Integer> quantities() {
        ScenarioItem.withNewSession { ScenarioItem.list().collectEntries { [(it.name): it.qty] } }
    }

    void "deleteAll removes the rows a comparison matches"() {
        when:
        def deleted = ScenarioItem.withNewTransaction { ScenarioItem.where { qty > 1 }.deleteAll() }

        then:
        deleted == 2
        left() == ['a', 'd']
    }

    void "deleteAll with or removes the rows either side matches"() {
        when:
        def deleted = ScenarioItem.withNewTransaction { ScenarioItem.where { qty == 1 || qty == 3 }.deleteAll() }

        then:
        deleted == 2
        left() == ['b', 'd']
    }

    void "deleteAll with a null comparison removes the rows with a null value"() {
        when:
        def deleted = ScenarioItem.withNewTransaction { ScenarioItem.where { qty == null }.deleteAll() }

        then:
        deleted == 1
        left() == ['a', 'b', 'c']
    }

    void "deleteAll with != leaves the rows with a null value"() {
        when:
        def deleted = ScenarioItem.withNewTransaction { ScenarioItem.where { qty != 1 }.deleteAll() }

        then:
        deleted == 2
        left() == ['a', 'd']
    }

    void "deleteAll through a dotted association path"() {
        when:
        def deleted = ScenarioItem.withNewTransaction { ScenarioItem.where { owner.name == 'ann' }.deleteAll() }

        then:
        deleted == 2
        left() == ['c', 'd']
    }

    void "deleteAll through an association block"() {
        when:
        def deleted = ScenarioItem.withNewTransaction { ScenarioItem.where { owner { name == 'ann' } }.deleteAll() }

        then:
        deleted == 2
        left() == ['c', 'd']
    }

    void "deleteAll with a subquery"() {
        given:
        def annIds = ScenarioOwner.where { name == 'ann' }.property('id')

        when:
        def deleted = ScenarioItem.withNewTransaction { ScenarioItem.where { owner.id in annIds }.deleteAll() }

        then:
        deleted == 2
        left() == ['c', 'd']
    }

    void "deleteAll with a lazy criteria"() {
        when:
        def deleted = ScenarioItem.withNewTransaction { ScenarioItem.whereLazy { qty == 2 }.deleteAll() }

        then:
        deleted == 1
        left() == ['a', 'c', 'd']
    }

    void "updateAll changes only the rows an association path matches"() {
        when:
        def updated = ScenarioItem.withNewTransaction { ScenarioItem.where { owner.name == 'bob' }.updateAll(qty: 9) }

        then:
        updated == 1
        quantities() == [a: 1, b: 2, c: 9, d: null]
    }
}

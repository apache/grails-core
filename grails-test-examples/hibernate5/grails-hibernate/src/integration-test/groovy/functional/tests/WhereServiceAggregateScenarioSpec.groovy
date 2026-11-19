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
 * A data service {@code @Where} closure that compares a property with an aggregate runs the aggregate as a
 * subquery. The qty values are 1, 2, 3 and null, so avg is 2, max is 3 and min is 1.
 */
@Integration(applicationClass = Application)
@Rollback
class WhereServiceAggregateScenarioSpec extends Specification {

    ScenarioItemService scenarioItemService

    private void seed() {
        new ScenarioItem(name: 'a', qty: 1, status: ScenarioStatus.NEW).save(flush: true, failOnError: true)
        new ScenarioItem(name: 'b', qty: 2, status: ScenarioStatus.NEW).save(flush: true, failOnError: true)
        new ScenarioItem(name: 'c', qty: 3, status: ScenarioStatus.DONE).save(flush: true, failOnError: true)
        new ScenarioItem(name: 'd', qty: null, status: ScenarioStatus.DONE).save(flush: true, failOnError: true)
        ScenarioItem.withSession { it.clear() }
    }

    private static List<String> names(Collection<ScenarioItem> items) {
        items*.name.sort()
    }

    void "a property above the average"() {
        given:
        seed()

        expect:
        names(scenarioItemService.aboveAverage()) == ['c']
    }

    void "a property at least the average"() {
        given:
        seed()

        expect:
        names(scenarioItemService.atLeastAverage()) == ['b', 'c']
    }

    void "a property below the average"() {
        given:
        seed()

        expect:
        names(scenarioItemService.belowAverage()) == ['a']
    }

    void "a property at most the average"() {
        given:
        seed()

        expect:
        names(scenarioItemService.atMostAverage()) == ['a', 'b']
    }

    void "a property equal to the maximum"() {
        given:
        seed()

        expect:
        names(scenarioItemService.highest()) == ['c']
    }

    void "a property equal to the minimum"() {
        given:
        seed()

        expect:
        names(scenarioItemService.lowest()) == ['a']
    }

    void "an aggregate comparison combined with a method argument"() {
        given:
        seed()

        expect:
        names(scenarioItemService.aboveAverageWithStatus(ScenarioStatus.DONE)) == ['c']
        scenarioItemService.aboveAverageWithStatus(ScenarioStatus.NEW).isEmpty()
    }
}

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
import org.hibernate.FlushMode
import spock.lang.Specification

/**
 * Runs criteria queries with the query arguments readOnly, fetchSize, timeout and flushMode against a
 * real database. The rows are committed and removed after each scenario.
 */
@Integration(applicationClass = Application)
class CriteriaQueryArgsScenarioSpec extends Specification {

    void setup() {
        ScenarioItem.withNewTransaction {
            new ScenarioItem(name: 'a', qty: 1, status: ScenarioStatus.NEW).save(flush: true, failOnError: true)
            new ScenarioItem(name: 'b', qty: 2, status: ScenarioStatus.NEW).save(flush: true, failOnError: true)
        }
    }

    void cleanup() {
        ScenarioItem.withNewTransaction {
            ScenarioItem.list()*.delete(flush: true)
        }
    }

    private static Integer storedQtyOfA() {
        ScenarioItem.withNewSession { ScenarioItem.findByName('a').qty }
    }

    private static void changeQtyOfA(Closure<List<ScenarioItem>> load) {
        ScenarioItem.withNewTransaction {
            ScenarioItem item = load.call()[0]
            item.qty = 99
            ScenarioItem.withSession { it.flush() }
        }
    }

    void "withCriteria without readOnly writes a change to a loaded row"() {
        when:
        changeQtyOfA { ScenarioItem.withCriteria { eq 'name', 'a' } }

        then:
        storedQtyOfA() == 99
    }

    void "withCriteria with readOnly does not write a change to a loaded row"() {
        when:
        changeQtyOfA { ScenarioItem.withCriteria([readOnly: true]) { eq 'name', 'a' } }

        then:
        storedQtyOfA() == 1
    }

    void "a criteria list with readOnly does not write a change to a loaded row"() {
        when:
        changeQtyOfA { ScenarioItem.createCriteria().list([readOnly: true]) { eq 'name', 'a' } }

        then:
        storedQtyOfA() == 1
    }

    void "fetchSize, timeout and flushMode do not change the rows returned"() {
        expect:
        ScenarioItem.withNewSession {
            ScenarioItem.withCriteria([fetchSize: 10, timeout: 10, flushMode: FlushMode.COMMIT]) { order 'name' }*.name
        } == ['a', 'b']
    }

    void "a fetchSize that is not a whole number is refused and names the value"() {
        when:
        ScenarioItem.withNewSession { ScenarioItem.withCriteria([fetchSize: 'abc']) { order 'name' } }

        then:
        def e = thrown(Exception)
        e.message.contains('abc')
    }

    void "a timeout that is not a whole number is refused and names the value"() {
        when:
        ScenarioItem.withNewSession { ScenarioItem.createCriteria().list([timeout: 'abc']) { order 'name' } }

        then:
        def e = thrown(Exception)
        e.message.contains('abc')
    }
}

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

import org.hibernate.SessionFactory
import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Issue
import spock.lang.Specification

import grails.testing.mixin.integration.Integration

/**
 * Query behaviour that reporters asked for years ago and that is now pinned (#14343, #14374).
 */
@Integration(applicationClass = Application)
class FetchJoinAndElementCollectionScenarioSpec extends Specification {

    @Autowired
    SessionFactory sessionFactory

    void cleanup() {
        ScenarioFetchTrip.withNewTransaction {
            ScenarioFetchTrip.list()*.delete(flush: true)
            ScenarioFetchPlace.list()*.delete(flush: true)
            ScenarioContact.list()*.delete(flush: true)
        }
    }

    @Issue('https://github.com/apache/grails-core/issues/14343')
    void "findAll loads a to-one association mapped with fetch join without one select per row"() {
        given:
        ScenarioFetchTrip.withNewTransaction {
            (1..3).each {
                new ScenarioFetchTrip(
                        number: "T$it",
                        destination: new ScenarioFetchPlace(city: "c$it").save(failOnError: true),
                        origin: new ScenarioFetchPlace(city: "o$it").save(failOnError: true)).save(failOnError: true)
            }
        }
        sessionFactory.statistics.statisticsEnabled = true

        when:
        long before = sessionFactory.statistics.prepareStatementCount
        List<String> cities = ScenarioFetchTrip.withNewSession { ScenarioFetchTrip.findAll()*.destination*.city }
        long used = sessionFactory.statistics.prepareStatementCount - before

        then:
        cities.sort() == ['c1', 'c2', 'c3']
        used <= 2
    }

    @Issue('https://github.com/apache/grails-core/issues/14374')
    void "a criteria block over a collection of enums restricts its elements"() {
        given:
        ScenarioContact.withNewTransaction {
            new ScenarioContact(name: 'a', kinds: [ScenarioContactKind.EMAIL, ScenarioContactKind.PHONE]).save(failOnError: true)
            new ScenarioContact(name: 'b', kinds: [ScenarioContactKind.NONE]).save(failOnError: true)
        }

        expect:
        ScenarioContact.withNewSession {
            ScenarioContact.createCriteria().list { kinds { eq('elements', ScenarioContactKind.EMAIL) } }*.name
        } == ['a']
        ScenarioContact.withNewSession {
            ScenarioContact.createCriteria().list { kinds { 'in'('elements', [ScenarioContactKind.PHONE, ScenarioContactKind.NONE]) } }*.name.sort()
        } == ['a', 'b']
    }
}

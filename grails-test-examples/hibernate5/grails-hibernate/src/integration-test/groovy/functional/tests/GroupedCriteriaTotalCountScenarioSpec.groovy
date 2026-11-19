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

import spock.lang.Specification

import grails.testing.mixin.integration.Integration

/**
 * The total count of a paged criteria that groups is the number of groups, not the number of rows (#14431).
 */
@Integration(applicationClass = Application)
class GroupedCriteriaTotalCountScenarioSpec extends Specification {

    void setup() {
        ScenarioGroupedItem.withNewTransaction {
            ['a', 'a', 'a', 'b', 'b', 'c'].eachWithIndex { String city, int i ->
                new ScenarioGroupedItem(name: "item$i", city: city).save(failOnError: true)
            }
        }
    }

    void cleanup() {
        ScenarioGroupedItem.withNewTransaction {
            ScenarioGroupedItem.list()*.delete(flush: true)
        }
    }

    void "a paged grouped criteria reports the number of groups as its total"() {
        when:
        def results = ScenarioGroupedItem.withNewSession {
            def page = ScenarioGroupedItem.createCriteria().list(max: 2) {
                projections {
                    groupProperty('city')
                }
                order('city')
            }
            [page: new ArrayList(page), total: page.totalCount]
        }

        then:
        results.page == ['a', 'b']
        results.total == 3
    }

    void "a paged ungrouped criteria still reports the number of rows"() {
        when:
        def results = ScenarioGroupedItem.withNewSession {
            def page = ScenarioGroupedItem.createCriteria().list(max: 2) {
                order('name')
            }
            [size: page.size(), total: page.totalCount]
        }

        then:
        results.size == 2
        results.total == 6
    }
}

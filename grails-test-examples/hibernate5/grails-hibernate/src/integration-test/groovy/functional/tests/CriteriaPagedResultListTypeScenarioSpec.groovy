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

import spock.lang.Issue
import spock.lang.Specification

import grails.testing.mixin.integration.Integration

@Integration(applicationClass = Application)
class CriteriaPagedResultListTypeScenarioSpec extends Specification {

    void cleanup() {
        ScenarioPagedItem.withNewTransaction {
            ScenarioPagedItem.list()*.delete(flush: true)
        }
    }

    @Issue('https://github.com/apache/grails-core/issues/14339')
    void "a paged criteria list can be used as either paged result list type"() {
        given:
        ScenarioPagedItem.withNewTransaction {
            (1..5).each { new ScenarioPagedItem(name: "n$it").save(failOnError: true) }
        }

        when:
        Map page = ScenarioPagedItem.withNewSession {
            def result = ScenarioPagedItem.createCriteria().list(max: 2) { order 'name' }
            [hibernateType: result instanceof org.grails.orm.hibernate.query.PagedResultList,
             names        : result*.name,
             total        : ((grails.gorm.PagedResultList) result).totalCount]
        }
        Map beyond = ScenarioPagedItem.withNewSession {
            def result = ScenarioPagedItem.createCriteria().list(max: 2, offset: 50) { order 'name' }
            [empty: result.isEmpty(), total: ((org.grails.orm.hibernate.query.PagedResultList) result).totalCount]
        }

        then:
        page.hibernateType
        page.names == ['n1', 'n2']
        page.total == 5
        beyond.empty
        beyond.total == 5
    }
}

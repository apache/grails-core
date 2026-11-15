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

package grails.gorm.tests

import grails.gorm.annotation.Entity
import spock.lang.Issue

@Issue('https://github.com/apache/grails-core/issues/14431')
class GroupedCriteriaTotalCountSpec extends HibernateGormDatastoreSpec {

    void setupSpec() {
        manager.registerDomainClasses(GroupedCountItem)
    }

    void setup() {
        ['a', 'a', 'a', 'b', 'b', 'c'].eachWithIndex { String city, int i ->
            new GroupedCountItem(name: "item$i", city: city).save(failOnError: true)
        }
        session.flush()
        session.clear()
    }

    void "totalCount of a grouped criteria is the number of groups"() {
        when:
        def results = GroupedCountItem.createCriteria().list(max: 2) {
            projections {
                groupProperty('city')
            }
            order('city')
        }

        then: 'the page holds the first two groups and the total counts all three'
        results == ['a', 'b']
        results.totalCount == 3
    }

    void "totalCount of a grouped criteria honours the restrictions"() {
        when:
        def results = GroupedCountItem.createCriteria().list(max: 1) {
            ne('city', 'c')
            projections {
                groupProperty('city')
            }
            order('city')
        }

        then:
        results == ['a']
        results.totalCount == 2
    }

    void "totalCount of an ungrouped criteria still counts the rows"() {
        when:
        def results = GroupedCountItem.createCriteria().list(max: 2) {
            order('name')
        }

        then:
        results.size() == 2
        results.totalCount == 6
    }
}

@Entity
class GroupedCountItem {
    String name
    String city
}

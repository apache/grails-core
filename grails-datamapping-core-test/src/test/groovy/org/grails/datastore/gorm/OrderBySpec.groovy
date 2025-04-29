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
package org.grails.datastore.gorm

import org.apache.grails.data.testing.tck.domains.ChildEntity
import org.apache.grails.data.testing.tck.domains.TestEntity
import org.apache.grails.data.simple.core.GrailsDataCoreTckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec

/**
 * @author Daniel Wiell
 */
class OrderBySpec extends GrailsDataTckSpec<GrailsDataCoreTckManager> {
    def setup() {
        def age = 40
        ["Bob", "Fred", "Barney", "Frank", "Joe", "Ernie"].each {
            new TestEntity(name: it, age: age++, child: new ChildEntity(name: "$it Child")).save()
        }
    }

    def 'Test order by property name with dynamic finder returning first result'() {
        when:
        def result = TestEntity.findByAgeGreaterThanEquals(40, [sort: "age", order: 'desc'])

        then:
        45 == result.age
    }

    def 'Test order by property name with dynamic finder using max'() {
        when:
        def results = TestEntity.findAllByAgeGreaterThanEquals(40, [sort: "age", order: 'desc', max: 1])

        then:
        45 == results[0].age
    }

    def 'Test order by with list() method using max'() {
        when:
        def results = TestEntity.list(sort: "age", order: 'desc', max: 1)

        then:
        45 == results[0].age
    }

    def 'Test order by with criteria using maxResults'() {
        when:
        def results = TestEntity.withCriteria {
            order 'age', 'desc'
            maxResults 1
        }

        then:
        45 == results[0].age
    }
}

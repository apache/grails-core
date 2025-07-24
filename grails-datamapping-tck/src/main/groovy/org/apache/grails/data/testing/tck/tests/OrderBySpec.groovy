/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.grails.data.testing.tck.tests

import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import org.apache.grails.data.testing.tck.domains.ChildEntity
import org.apache.grails.data.testing.tck.domains.TestEntity

/**
 * Abstract base test for order by queries. Subclasses should do the necessary setup to configure GORM
 */
class OrderBySpec extends GrailsDataTckSpec {

    void "Test order with criteria"() {
        given:
        def age = 40

        ["Bob", "Fred", "Barney", "Frank", "Joe", "Ernie"].each {
            new TestEntity(name: it, age: age++, child: new ChildEntity(name: "$it Child")).save()
        }

        when:
        def results = TestEntity.createCriteria().list {
            order "age"
        }
        then:
        40 == results[0].age
        41 == results[1].age
        42 == results[2].age

        when:
        results = TestEntity.createCriteria().list {
            order "age", "desc"
        }

        then:
        45 == results[0].age
        44 == results[1].age
        43 == results[2].age
    }

    void "Test order by with list() method"() {
        given:
        def age = 40

        ["Bob", "Fred", "Barney", "Frank", "Joe", "Ernie"].each {
            new TestEntity(name: it, age: age++, child: new ChildEntity(name: "$it Child")).save()
        }

        when:
        def results = TestEntity.list(sort: "age")

        then:
        40 == results[0].age
        41 == results[1].age
        42 == results[2].age

        when:
        results = TestEntity.list(sort: "age", order: "desc")

        then:
        45 == results[0].age
        44 == results[1].age
        43 == results[2].age
    }

    void "Test order by property name with dynamic finder"() {
        given:
        def age = 40

        ["Bob", "Fred", "Barney", "Frank", "Joe", "Ernie"].each {
            new TestEntity(name: it, age: age++, child: new ChildEntity(name: "$it Child")).save()
        }

        when:
        def results = TestEntity.findAllByAgeGreaterThanEquals(40, [sort: "age"])

        then:
        40 == results[0].age
        41 == results[1].age
        42 == results[2].age

        when:
        results = TestEntity.findAllByAgeGreaterThanEquals(40, [sort: "age", order: "desc"])

        then:
        45 == results[0].age
        44 == results[1].age
        43 == results[2].age
    }
}

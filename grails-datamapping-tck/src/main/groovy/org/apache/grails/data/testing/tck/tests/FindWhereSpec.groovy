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

import org.apache.grails.data.testing.tck.domains.TestEntity
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec

class FindWhereSpec extends GrailsDataTckSpec {

    def "Test findWhere returns a matching Instance"() {
        given:
        def entityId = new TestEntity(name: 'David', age: 27).save().id


        when:
        def entity = TestEntity.findWhere(name: 'David')

        then:
        'David' == entity.name
        27 == entity.age
        entityId == entity.id
    }

    def "Test findWhere with a GString property"() {
        given:
        def entityId = new TestEntity(name: 'David', age: 27).save().id
        def property = "name"

        when:
        def entity = TestEntity.findWhere("${property}": 'David')

        then:
        'David' == entity.name
        27 == entity.age
        entityId == entity.id
    }

    def "Test findAllWhere returns a matching Instance"() {
        given:
        def entityId = new TestEntity(name: 'David', age: 27).save().id

        when:
        def entity = TestEntity.findAllWhere(name: 'David')

        then:
        'David' == entity[0].name
        27 == entity[0].age
        entityId == entity[0].id
    }

    def "Test findAllWhere with a GString property"() {
        given:
        def entityId = new TestEntity(name: 'David', age: 27).save().id
        def property = "name"

        when:
        def entity = TestEntity.findAllWhere("${property}": 'David')

        then:
        'David' == entity[0].name
        27 == entity[0].age
        entityId == entity[0].id
    }
}

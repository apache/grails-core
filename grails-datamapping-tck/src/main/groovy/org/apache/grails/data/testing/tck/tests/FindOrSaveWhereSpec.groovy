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

class FindOrSaveWhereSpec extends GrailsDataTckSpec {

    void setupSpec() {
        manager.addAllDomainClasses([TestEntity])
    }

    def "Test findOrSaveWhere returns a new instance if it doesn't exist in the database"() {
        when:
        def entity = TestEntity.findOrSaveWhere(name: 'Lake', age: 63)

        then:
        'Lake' == entity.name
        63 == entity.age
        null != entity.id
    }

    def "Test findOrSaveWhere returns a persistent instance if it exists in the database"() {
        given:
        def entityId = new TestEntity(name: 'Levin', age: 64).save().id

        when:
        def entity = TestEntity.findOrSaveWhere(name: 'Levin', age: 64)

        then:
        entity.id != null
        entityId == entity.id
        'Levin' == entity.name
        64 == entity.age
    }
}

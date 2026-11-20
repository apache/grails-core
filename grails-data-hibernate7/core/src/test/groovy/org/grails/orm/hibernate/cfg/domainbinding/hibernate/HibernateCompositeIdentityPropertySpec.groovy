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
package org.grails.orm.hibernate.cfg.domainbinding.hibernate

import grails.gorm.tests.HibernateGormDatastoreSpec
import grails.persistence.Entity

class HibernateCompositeIdentityPropertySpec extends HibernateGormDatastoreSpec {

    void setupSpec() {
        manager.registerDomainClasses(HCIPSimpleEntity, HCIPCompositeEntity)
    }

    def "two-arg constructor creates property with empty parts array"() {
        given:
        def context = getMappingContext()
        def entity = context.getPersistentEntity(HCIPSimpleEntity.name)

        when:
        def prop = new HibernateCompositeIdentityProperty(entity, context, "id", Long)

        then:
        prop.name == "id"
        prop.type == Long
        prop.getParts() != null
        prop.getParts().length == 0
    }

    def "three-arg constructor with parts stores them"() {
        given:
        def context = getMappingContext()
        def entity = context.getPersistentEntity(HCIPSimpleEntity.name)
        def part1 = Mock(HibernatePersistentProperty) { getName() >> "firstName" }
        def part2 = Mock(HibernatePersistentProperty) { getName() >> "lastName" }

        when:
        def prop = new HibernateCompositeIdentityProperty(
                entity, context, "id", Serializable, [part1, part2] as HibernatePersistentProperty[])

        then:
        prop.getParts().length == 2
        prop.getParts()[0].name == "firstName"
        prop.getParts()[1].name == "lastName"
    }

    def "three-arg constructor with null parts defaults to empty array"() {
        given:
        def context = getMappingContext()
        def entity = context.getPersistentEntity(HCIPSimpleEntity.name)

        when:
        def prop = new HibernateCompositeIdentityProperty(
                entity, context, "id", Serializable, null)

        then:
        prop.getParts() != null
        prop.getParts().length == 0
    }

    def "identity property resolved from composite entity is HibernateCompositeIdentityProperty"() {
        given:
        def entity = getMappingContext().getPersistentEntity(HCIPCompositeEntity.name)

        when:
        def identityProperty = entity.getIdentityProperty()

        then:
        identityProperty instanceof HibernateCompositeIdentityProperty
    }

    def "composite identity resolved from mapping context carries all part properties"() {
        given:
        def entity = (HibernatePersistentEntity) getMappingContext().getPersistentEntity(HCIPCompositeEntity.name)

        when:
        def identityProperty = entity.getIdentityProperty() as HibernateCompositeIdentityProperty
        def parts = identityProperty.getParts()

        then:
        parts != null
        parts.length == 2
        parts.every { it instanceof HibernatePersistentProperty }
        parts*.name.sort() == ["code", "name"]
    }

    def "getParts returns the exact array instance provided at construction"() {
        given:
        def context = getMappingContext()
        def entity = context.getPersistentEntity(HCIPSimpleEntity.name)
        def part = Mock(HibernatePersistentProperty) { getName() >> "sku" }
        def partsArray = [part] as HibernatePersistentProperty[]

        when:
        def prop = new HibernateCompositeIdentityProperty(entity, context, "id", Long, partsArray)

        then:
        prop.getParts().is(partsArray)
    }
}

@Entity
class HCIPSimpleEntity {
    Long id
    String name
}

@Entity
class HCIPCompositeEntity implements Serializable {
    String name
    Integer code
    static mapping = {
        id composite: ['name', 'code']
    }
}

/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  'License'); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  'AS IS' BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.grails.orm.hibernate.cfg.domainbinding.hibernate

import grails.gorm.annotation.Entity
import grails.gorm.specs.HibernateGormDatastoreSpec

class HibernateSimpleIdentityPropertySpec extends HibernateGormDatastoreSpec {

    void setupSpec() {
        manager.addAllDomainClasses([HSIPSimpleEntity, HSIPAssignedEntity])
    }

    def 'name-and-type constructor creates property with given name and type'() {
        given:
        def context = getMappingContext()
        def entity = context.getPersistentEntity(HSIPSimpleEntity.name)

        when:
        def prop = new HibernateSimpleIdentityProperty(entity, context, 'id', Long)

        then:
        prop.name == 'id'
        prop.type == Long
    }

    def 'name-and-type constructor property is instance of HibernateSimpleIdentityProperty'() {
        given:
        def context = getMappingContext()
        def entity = context.getPersistentEntity(HSIPSimpleEntity.name)

        when:
        def prop = new HibernateSimpleIdentityProperty(entity, context, 'id', Long)

        then:
        prop instanceof HibernateSimpleIdentityProperty
        prop instanceof HibernateIdentityProperty
    }

    def 'name-and-type constructor stores the correct owner entity'() {
        given:
        def context = getMappingContext()
        def entity = context.getPersistentEntity(HSIPSimpleEntity.name)

        when:
        def prop = new HibernateSimpleIdentityProperty(entity, context, 'id', Long)

        then:
        prop.owner.is(entity)
    }

    def 'identity property resolved from simple entity is HibernateSimpleIdentityProperty'() {
        given:
        def entity = getMappingContext().getPersistentEntity(HSIPSimpleEntity.name) as HibernatePersistentEntity

        when:
        def identityProperty = entity.getIdentityProperty()

        then:
        identityProperty instanceof HibernateSimpleIdentityProperty
    }

    def 'getGeneratorName returns the generator from the entity mapping'() {
        given:
        def entity = getMappingContext().getPersistentEntity(HSIPSimpleEntity.name) as HibernatePersistentEntity

        when:
        def identityProperty = entity.getIdentityProperty() as HibernateSimpleIdentityProperty
        def generatorName = identityProperty.getGeneratorName()

        then:
        generatorName != null
        !generatorName.isEmpty()
    }

    def "getGeneratorName returns 'assigned' for entity with assigned generator"() {
        given:
        def entity = getMappingContext().getPersistentEntity(HSIPAssignedEntity.name) as HibernatePersistentEntity

        when:
        def identityProperty = entity.getIdentityProperty() as HibernateSimpleIdentityProperty
        def generatorName = identityProperty.getGeneratorName()

        then:
        generatorName == 'assigned'
    }
}

@Entity
class HSIPSimpleEntity {

    Long id
    String name
}

@Entity
class HSIPAssignedEntity {

    Long id
    String name
    static mapping = {
        id generator: 'assigned'
    }
}

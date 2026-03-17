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

package org.grails.orm.hibernate.cfg.domainbinding.binder

import grails.gorm.annotation.Entity
import grails.gorm.hibernate.HibernateEntity
import grails.gorm.specs.HibernateGormDatastoreSpec
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateEmbeddedProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentProperty
import org.grails.orm.hibernate.cfg.domainbinding.util.PropertyFromValueCreator
import org.hibernate.mapping.BasicValue
import org.hibernate.mapping.Column
import org.hibernate.mapping.Component
import org.hibernate.mapping.Property
import org.hibernate.mapping.RootClass
import spock.lang.Subject

class ComponentUpdaterSpec extends HibernateGormDatastoreSpec {

    def propertyFromValueCreator = Mock(PropertyFromValueCreator)

    @Subject
    ComponentUpdater updater

    def setupSpec() {
        manager.addAllDomainClasses([CUPerson, CUAddress])
    }

    def setup() {
        updater = new ComponentUpdater(propertyFromValueCreator)
    }

    def "should add property to component and set columns nullable if component property is nullable"() {
        given:
        def metadataBuildingContext = getGrailsDomainBinder().getMetadataBuildingContext()
        def root = new RootClass(metadataBuildingContext)
        def component = new Component(metadataBuildingContext, root)

        def personEntity = mappingContext.getPersistentEntity(CUPerson.name)
        HibernateEmbeddedProperty componentProperty = personEntity.persistentProperties.find { it.name == 'address' } as HibernateEmbeddedProperty
        HibernatePersistentProperty streetProp = componentProperty.associatedEntity.persistentProperties.find { it.name == 'street' } as HibernatePersistentProperty

        def value = new BasicValue(metadataBuildingContext, root.getTable())
        def column = new Column("street")
        value.addColumn(column)
        def hibernateProperty = new Property()
        hibernateProperty.setName("street")

        when:
        updater.updateComponent(component, componentProperty, streetProp, value)

        then:
        1 * propertyFromValueCreator.createProperty(value, streetProp) >> hibernateProperty
        component.getProperty("street") == hibernateProperty
        column.isNullable() // address is nullable on CUPerson
    }

    def "should not set columns nullable if component property is not nullable"() {
        given:
        def metadataBuildingContext = getGrailsDomainBinder().getMetadataBuildingContext()
        def root = new RootClass(metadataBuildingContext)
        def component = new Component(metadataBuildingContext, root)

        def personEntity = mappingContext.getPersistentEntity(CUPerson.name)
        HibernateEmbeddedProperty componentProperty = personEntity.persistentProperties.find { it.name == 'requiredAddress' } as HibernateEmbeddedProperty
        HibernatePersistentProperty streetProp = componentProperty.associatedEntity.persistentProperties.find { it.name == 'street' } as HibernatePersistentProperty

        def value = new BasicValue(metadataBuildingContext, root.getTable())
        def column = new Column("street")
        column.setNullable(false)
        value.addColumn(column)
        def hibernateProperty = new Property()
        hibernateProperty.setName("street")

        when:
        updater.updateComponent(component, componentProperty, streetProp, value)

        then:
        1 * propertyFromValueCreator.createProperty(value, streetProp) >> hibernateProperty
        !column.isNullable()
    }
}

class CUAddress {
    String street
    String city
}

@Entity
class CUPerson implements HibernateEntity<CUPerson> {
    CUAddress address
    CUAddress requiredAddress
    static embedded = ['address', 'requiredAddress']
    static constraints = {
        address nullable: true
        requiredAddress nullable: false
    }
}

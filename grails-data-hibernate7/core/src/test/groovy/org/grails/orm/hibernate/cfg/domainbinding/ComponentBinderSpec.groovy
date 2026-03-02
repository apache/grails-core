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

package org.grails.orm.hibernate.cfg.domainbinding

import grails.gorm.specs.HibernateGormDatastoreSpec
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity
import org.grails.orm.hibernate.cfg.MappingCacheHolder
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateEmbeddedProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateSimpleProperty
import org.grails.orm.hibernate.cfg.domainbinding.binder.ComponentBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.ComponentUpdater
import org.hibernate.mapping.BasicValue
import org.hibernate.mapping.Component
import org.hibernate.mapping.RootClass
import org.hibernate.mapping.Table
import org.hibernate.mapping.Value
import spock.lang.Subject

class ComponentBinderSpec extends HibernateGormDatastoreSpec {

    // Mock Collaborators
    MappingCacheHolder mappingCacheHolder = Mock(MappingCacheHolder)
    ComponentUpdater componentUpdater = Mock(ComponentUpdater)
    org.grails.orm.hibernate.cfg.domainbinding.binder.GrailsPropertyBinder grailsPropertyBinder = Mock(org.grails.orm.hibernate.cfg.domainbinding.binder.GrailsPropertyBinder)

    @Subject
    ComponentBinder binder

    def setup() {
        def metadataBuildingContext = getGrailsDomainBinder().getMetadataBuildingContext()
        binder = new ComponentBinder(
                metadataBuildingContext,
                mappingCacheHolder,
                componentUpdater,
                metadataBuildingContext.getMetadataCollector()
        )
        binder.setGrailsPropertyBinder(grailsPropertyBinder)
    }

    def "should bind component and its properties"() {
        given:
        def metadataBuildingContext = getGrailsDomainBinder().getMetadataBuildingContext()
        def root = new RootClass(metadataBuildingContext)
        root.setEntityName("MyEntity")
        root.setTable(new Table("my_entity"))
        
        def embeddedProp = Mock(TestEmbedded)
        def associatedEntity = GroovyMock(GrailsHibernatePersistentEntity)
        
        embeddedProp.getType() >> Address
        embeddedProp.getName() >> "address"
        embeddedProp.getAssociatedEntity() >> associatedEntity
        embeddedProp.getOwner() >> Mock(GrailsHibernatePersistentEntity) {
            getJavaClass() >> MyEntity
        }
        embeddedProp.isValidHibernateOneToOne() >> false
        embeddedProp.isValidHibernateManyToOne() >> false

        def associatedEntity = GroovyMock(GrailsHibernatePersistentEntity)
        def embeddedProp = mockEmbeddedProperty(associatedEntity, "address", Address)

        // The Fix: Mock must return the root class so .getTable() doesn't NPE
        associatedEntity.getPersistentClass() >> root

        def prop1 = Mock(HibernateSimpleProperty)
        prop1.getName() >> "street"
        prop1.getType() >> String
        prop1.isValidHibernateOneToOne() >> false
        prop1.isValidHibernateManyToOne() >> false
        associatedEntity.getHibernateParentProperty(MyEntity) >> Optional.empty()
        associatedEntity.getHibernatePersistentProperties(MyEntity) >> [prop1]

        when:
        def component = binder.bindComponent(root, embeddedProp, "")

        then:
        component.getComponentClassName() == Address.name
        component.getRoleName() == Address.name + ".address"
        1 * mappingCacheHolder.cacheMapping(associatedEntity)
        1 * grailsPropertyBinder.bindProperty(root, root.getTable(), "address", embeddedProp, prop1) >> new BasicValue(metadataBuildingContext, root.getTable())
        1 * componentUpdater.updateComponent(_ as Component, embeddedProp, prop1, _ as Value)
    }

    def "should skip identity and version properties"() {
        given:
        def metadataBuildingContext = getGrailsDomainBinder().getMetadataBuildingContext()
        def root = new RootClass(metadataBuildingContext)
        root.setTable(new Table("my_entity"))

        def associatedEntity = GroovyMock(GrailsHibernatePersistentEntity)
        def embeddedProp = mockEmbeddedProperty(associatedEntity, "address", Address)

        embeddedProp.getType() >> Address
        embeddedProp.getName() >> "address"
        embeddedProp.getAssociatedEntity() >> associatedEntity
        embeddedProp.getOwner() >> Mock(GrailsHibernatePersistentEntity) {
            getJavaClass() >> MyEntity
        }
        embeddedProp.isValidHibernateOneToOne() >> false
        embeddedProp.isValidHibernateManyToOne() >> false

        def idProp = Mock(org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateIdentityProperty)
        idProp.getName() >> "id"

        def normalProp = Mock(HibernateSimpleProperty)
        normalProp.getName() >> "street"
        normalProp.getType() >> String
        normalProp.isValidHibernateOneToOne() >> false
        normalProp.isValidHibernateManyToOne() >> false

        associatedEntity.getHibernateParentProperty(MyEntity) >> Optional.empty()
        associatedEntity.getHibernatePersistentProperties(MyEntity) >> [normalProp]

        when:
        binder.bindComponent(root, embeddedProp, "")

        then:
        0 * componentUpdater.updateComponent(_, _, idProp, _)
        0 * componentUpdater.updateComponent(_, _, versionProp, _)
        1 * grailsPropertyBinder.bindProperty(root, root.getTable(), "address", embeddedProp, normalProp) >> new BasicValue(metadataBuildingContext, root.getTable())
        1 * componentUpdater.updateComponent(_, _, normalProp, _)
    }

    def "should set parent property when component has reference back to owner"() {
        given:
        def metadataBuildingContext = getGrailsDomainBinder().getMetadataBuildingContext()
        def root = new RootClass(metadataBuildingContext)
        root.setTable(new Table("my_entity"))

        def associatedEntity = GroovyMock(GrailsHibernatePersistentEntity)
        def embeddedProp = mockEmbeddedProperty(associatedEntity, "address", Address)

        associatedEntity.getPersistentClass() >> root

        def parentProp = Mock(HibernateSimpleProperty)
        parentProp.getName() >> "myEntity"

        associatedEntity.getHibernateParentProperty(MyEntity) >> Optional.of(parentProp)
        associatedEntity.getHibernatePersistentProperties(MyEntity) >> []

        when:
        def component = binder.bindComponent(root, embeddedProp, "")

        then:
        component.getParentProperty() == "myEntity"
    }

    // Helper to reduce boilerplate
    private HibernateEmbeddedProperty mockEmbeddedProperty(GrailsHibernatePersistentEntity associatedEntity, String name, Class type) {
        def embeddedProp = Mock(HibernateEmbeddedProperty)
        embeddedProp.getName() >> name
        embeddedProp.getType() >> type
        embeddedProp.getAssociatedEntity() >> associatedEntity
        embeddedProp.getOwner() >> Mock(GrailsHibernatePersistentEntity) {
            getJavaClass() >> MyEntity
        }
        embeddedProp.isValidHibernateOneToOne() >> false
        embeddedProp.isValidHibernateManyToOne() >> false

        associatedEntity.getName() >> "Address"
        def parentProp = Mock(TestSimple)
        parentProp.getName() >> "myEntity"
        parentProp.getType() >> MyEntity

        associatedEntity.getIdentity() >> null
        associatedEntity.getHibernateParentProperty(MyEntity) >> Optional.of(parentProp)
        associatedEntity.getHibernatePersistentProperties(MyEntity) >> []

        def mappings = metadataBuildingContext.getMetadataCollector()

        when:
        def component = binder.bindComponent(root, embeddedProp, "")

        then:
        component.getParentProperty() == "myEntity"
        0 * grailsPropertyBinder.bindProperty(_, _, _, _, parentProp, _)
        0 * componentUpdater.updateComponent(_, _, parentProp, _)
    }

    static class MyEntity {}
    static class Address {}
}
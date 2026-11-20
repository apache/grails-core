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

import grails.gorm.tests.HibernateGormDatastoreSpec
import org.grails.orm.hibernate.cfg.domainbinding.binder.GrailsPropertyBinder
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity
import org.grails.orm.hibernate.cfg.MappingCacheHolder
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateEmbeddedCollectionProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateEmbeddedProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateSimpleProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateIdentityProperty
import org.grails.orm.hibernate.cfg.domainbinding.binder.ComponentBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.ComponentUpdater
import org.hibernate.mapping.BasicValue
import org.hibernate.mapping.Component
import org.hibernate.mapping.PersistentClass
import org.hibernate.mapping.RootClass
import org.hibernate.mapping.Table
import org.hibernate.mapping.Value
import spock.lang.Subject

class ComponentBinderSpec extends HibernateGormDatastoreSpec {

    // Mock Collaborators
    MappingCacheHolder mappingCacheHolder = Mock(MappingCacheHolder)
    ComponentUpdater componentUpdater = Mock(ComponentUpdater)
    GrailsPropertyBinder grailsPropertyBinder = Mock(GrailsPropertyBinder)

    @Subject
    ComponentBinder binder

    def setup() {
        def metadataBuildingContext = getGrailsDomainBinder().getMetadataBuildingContext()
        binder = new ComponentBinder(metadataBuildingContext, mappingCacheHolder, componentUpdater)
        binder.setGrailsPropertyBinder(grailsPropertyBinder)
    }

    def "should bind component and its properties"() {
        given:
        def metadataBuildingContext = getGrailsDomainBinder().getMetadataBuildingContext()
        def root = new RootClass(metadataBuildingContext)
        root.setEntityName("MyEntity")
        root.setTable(new Table("my_entity"))

        def associatedEntity = GroovyMock(GrailsHibernatePersistentEntity)
        def embeddedProp = mockEmbeddedProperty(associatedEntity, "address", Address, root)

        // Ensure the associated entity also knows its class for initialization logic
        associatedEntity.getPersistentClass() >> root

        def prop1 = Mock(HibernateSimpleProperty)
        prop1.getName() >> "street"
        prop1.getType() >> String

        associatedEntity.getHibernateParentProperty(MyEntity) >> Optional.empty()
        associatedEntity.getHibernatePersistentProperties(MyEntity) >> [prop1]

        when:
        def component = binder.bindComponent(embeddedProp, "")

        then:
        component.getComponentClassName() == Address.name
        component.getRoleName() == Address.name + ".address"
        1 * mappingCacheHolder.cacheMapping(associatedEntity)
        1 * grailsPropertyBinder.bindProperty(prop1, embeddedProp, "address") >> new BasicValue(metadataBuildingContext, root.getTable())
        1 * componentUpdater.updateComponent(_ as Component, embeddedProp, prop1, _ as Value)
    }

    def "should skip identity properties during binding"() {
        given:
        def metadataBuildingContext = getGrailsDomainBinder().getMetadataBuildingContext()
        def root = new RootClass(metadataBuildingContext)
        root.setTable(new Table("my_entity"))

        def associatedEntity = GroovyMock(GrailsHibernatePersistentEntity)
        def embeddedProp = mockEmbeddedProperty(associatedEntity, "address", Address, root)

        associatedEntity.getPersistentClass() >> root

        // HibernatePersistentProperty includes ID properties; usually filtered by the loop logic
        def idProp = Mock(HibernateIdentityProperty)
        idProp.getName() >> "id"

        def normalProp = Mock(HibernateSimpleProperty)
        normalProp.getName() >> "street"
        normalProp.getType() >> String

        associatedEntity.getHibernateParentProperty(MyEntity) >> Optional.empty()
        associatedEntity.getHibernatePersistentProperties(MyEntity) >> [normalProp]

        when:
        binder.bindComponent(embeddedProp, "")

        then:
        // Logic check: if idProp is not in the list returned by getHibernatePersistentProperties, it's skipped
        0 * componentUpdater.updateComponent(_, _, idProp, _)
        1 * componentUpdater.updateComponent(_, _, normalProp, _)
    }

    def "should set parent property when component has reference back to owner"() {
        given:
        def metadataBuildingContext = getGrailsDomainBinder().getMetadataBuildingContext()
        def root = new RootClass(metadataBuildingContext)
        root.setTable(new Table("my_entity"))

        def associatedEntity = GroovyMock(GrailsHibernatePersistentEntity)
        def embeddedProp = mockEmbeddedProperty(associatedEntity, "address", Address, root)

        associatedEntity.getPersistentClass() >> root

        def parentProp = Mock(HibernateSimpleProperty)
        parentProp.getName() >> "myEntity"

        associatedEntity.getHibernateParentProperty(MyEntity) >> Optional.of(parentProp)
        associatedEntity.getHibernatePersistentProperties(MyEntity) >> []

        when:
        def component = binder.bindComponent(embeddedProp, "")

        then:
        component.getParentProperty() == "myEntity"
    }

    /**
     * Helper to reduce boilerplate.
     * The 'root' (PersistentClass) is required by the Component constructor to avoid NPE.
     */
    private HibernateEmbeddedProperty mockEmbeddedProperty(
            GrailsHibernatePersistentEntity associatedEntity,
            String name,
            Class type,
            PersistentClass root) {

        def embeddedProp = Mock(HibernateEmbeddedProperty)
        embeddedProp.getName() >> name
        embeddedProp.getType() >> type
        embeddedProp.getAssociatedEntity() >> associatedEntity
        embeddedProp.getPersistentClass() >> root // CRITICAL FIX

        embeddedProp.getOwner() >> Mock(GrailsHibernatePersistentEntity) {
            getJavaClass() >> MyEntity
        }
        return embeddedProp
    }

    private HibernateEmbeddedCollectionProperty mockEmbeddedCollectionProperty(
            GrailsHibernatePersistentEntity associatedEntity,
            String name,
            Class type,
            org.hibernate.mapping.Collection collection) {

        def prop = Mock(HibernateEmbeddedCollectionProperty)
        prop.getName() >> name
        prop.getType() >> type
        prop.getComponentType() >> type
        prop.getAssociatedEntity() >> associatedEntity
        prop.getCollection() >> collection
        prop.getOwner() >> Mock(GrailsHibernatePersistentEntity) {
            getJavaClass() >> MyEntity
        }
        return prop
    }

    def "bindEmbeddedCollectionComponent creates a Component element for the collection"() {
        given:
        def mbc = getGrailsDomainBinder().getMetadataBuildingContext()
        def ownerClass = new RootClass(mbc)
        ownerClass.setEntityName(MyEntity.name)
        ownerClass.setTable(new Table("my_entity"))
        def bag = new org.hibernate.mapping.Bag(mbc, ownerClass)
        bag.setCollectionTable(new Table("my_entity_dim"))

        def associatedEntity = GroovyMock(GrailsHibernatePersistentEntity)
        associatedEntity.getPersistentClass() >> ownerClass

        def widthProp = Mock(HibernateSimpleProperty)
        widthProp.getName() >> "width"
        widthProp.getType() >> int
        widthProp.getMappedForm() >> Mock(org.grails.orm.hibernate.cfg.PropertyConfig)
        widthProp.getType() >> int

        associatedEntity.getHibernatePersistentProperties(MyEntity) >> [widthProp]

        grailsPropertyBinder.bindProperty(widthProp, null, "dimensions") >> Mock(BasicValue)

        def prop = mockEmbeddedCollectionProperty(associatedEntity, "dimensions", Dimension, bag)

        when:
        def component = binder.bindEmbeddedCollectionComponent(prop)

        then:
        component != null
        component.componentClassName == Dimension.name
        1 * mappingCacheHolder.cacheMapping(associatedEntity)
    }

    static class MyEntity {}
    static class Address {}
    static class Dimension { int width }
}

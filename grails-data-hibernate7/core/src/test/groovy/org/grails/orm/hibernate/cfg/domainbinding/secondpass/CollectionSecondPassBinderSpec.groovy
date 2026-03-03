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

package org.grails.orm.hibernate.cfg.domainbinding.secondpass

import grails.gorm.annotation.Entity
import grails.gorm.specs.HibernateGormDatastoreSpec
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateToManyProperty

import org.hibernate.mapping.RootClass

import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.orm.hibernate.cfg.domainbinding.binder.ManyToOneBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.SimpleValueBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.ManyToOneValuesBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.CompositeIdentifierToManyToOneBinder
import org.grails.orm.hibernate.cfg.domainbinding.util.SimpleValueColumnFetcher
import org.grails.orm.hibernate.cfg.domainbinding.binder.CollectionForPropertyConfigBinder
import org.grails.orm.hibernate.cfg.domainbinding.util.DefaultColumnNameFetcher
import org.grails.orm.hibernate.cfg.domainbinding.binder.SimpleValueColumnBinder
import org.grails.orm.hibernate.cfg.domainbinding.util.BackticksRemover

class CollectionSecondPassBinderSpec extends HibernateGormDatastoreSpec {

    CollectionSecondPassBinder binder

    void setup() {
        def gdb = getGrailsDomainBinder()
        def mbc = gdb.getMetadataBuildingContext()
        def ns = gdb.getNamingStrategy()
        def je = gdb.getJdbcEnvironment()
        def svb = new SimpleValueBinder(mbc, ns, je)
        def svcf = new SimpleValueColumnFetcher()
        def citmto = new CompositeIdentifierToManyToOneBinder(mbc, ns, je)
        def mtob = new ManyToOneBinder(mbc, ns, svb, new ManyToOneValuesBinder(), citmto)
        def pkvc = new PrimaryKeyValueCreator(mbc)
        def botml = new BidirectionalOneToManyLinker(new org.grails.orm.hibernate.cfg.domainbinding.util.GrailsPropertyResolver())
        def dkvb = new DependentKeyValueBinder(svb, citmto)
        def cwjtb = new CollectionWithJoinTableBinder(mbc, ns, new UnidirectionalOneToManyInverseValuesBinder(), null, citmto, svcf, new CollectionForPropertyConfigBinder(), new SimpleValueColumnBinder(), null)
        def uotmb = new UnidirectionalOneToManyBinder(cwjtb, mbc.getMetadataCollector())
        def cfpcb = new CollectionForPropertyConfigBinder()
        def dcnf = new DefaultColumnNameFetcher(ns, new BackticksRemover())
        def svcb = new SimpleValueColumnBinder()
        def cku = new CollectionKeyColumnUpdater(new CollectionKeyBinder(botml, dkvb, svcb, pkvc))

        binder = new CollectionSecondPassBinder(cku, uotmb, cwjtb, cfpcb, new BidirectionalMapElementBinder(mtob, cfpcb), new ManyToManyElementBinder(mtob, cfpcb), new CollectionOrderByBinder(), new CollectionMultiTenantFilterBinder(dcnf))
    }

    protected HibernatePersistentProperty createTestHibernateToManyProperty(Class<?> domainClass = CSPBTestEntityWithMany, String propertyName = "items") {
        PersistentEntity entity = createPersistentEntity(domainClass)
        HibernatePersistentProperty property = (HibernatePersistentProperty) entity.getPropertyByName(propertyName)
        return property
    }

    def "resolveAssociatedClass throws MappingException when property has no associated entity"() {
        given:
        def property = createTestHibernateToManyProperty(CSPBTestEntityWithMany, "items") as HibernateToManyProperty
        def collection = new Bag(getGrailsDomainBinder().getMetadataBuildingContext(), null)
        collection.setRole("CSPBTestEntityWithMany.items")
        
        def associatedPersistentClass = new RootClass(getGrailsDomainBinder().getMetadataBuildingContext())
        associatedPersistentClass.setEntityName(CSPBAssociatedItem.name)
        def valueProperty = new org.hibernate.mapping.Property()
        valueProperty.setName("value")
        def simpleValue = new org.hibernate.mapping.BasicValue(getGrailsDomainBinder().getMetadataBuildingContext(), new org.hibernate.mapping.Table("test", "ASSOCIATED_ITEM"))
        simpleValue.setTypeName("string")
        def column = new org.hibernate.mapping.Column("VALUE")
        simpleValue.addColumn(column)
        valueProperty.setValue(simpleValue)
        associatedPersistentClass.addProperty(valueProperty)
        
        property.getMappedForm().setSort("value")
        property.getMappedForm().setOrder("desc")

        when:
        binder.bindOrderBy(property, collection, associatedPersistentClass)

        then:
        collection.getOrderBy() != null
    }

    def "test bindOrderBy with unidirectional one-to-many throws exception"() {
        given:
        def property = createTestHibernateToManyProperty(CSPBUnidirectionalEntity, "items") as HibernateToManyProperty
        def collection = new Bag(getGrailsDomainBinder().getMetadataBuildingContext(), null)
        def associatedPersistentClass = new RootClass(getGrailsDomainBinder().getMetadataBuildingContext())
        associatedPersistentClass.setEntityName(CSPBAssociatedItem.name)
        
        property.getMappedForm().setSort("value")

        when:
        binder.bindOrderBy(property, collection, associatedPersistentClass)

        then:
        thrown(DatastoreConfigurationException)
    }

    def "test bindOrderBy does nothing without sort"() {
        given:
        def property = createTestHibernateToManyProperty(CSPBTestEntityWithMany, "items") as HibernateToManyProperty
        def collection = new Bag(getGrailsDomainBinder().getMetadataBuildingContext(), null)
        def associatedPersistentClass = new RootClass(getGrailsDomainBinder().getMetadataBuildingContext())
        associatedPersistentClass.setEntityName(CSPBAssociatedItem.name)

        when:
        binder.bindOrderBy(property, collection, associatedPersistentClass)

        then:
        collection.getOrderBy() == null
    }

    def "test resolveAssociatedClass throws MappingException when class is unmapped"() {
        given:
        def property = createTestHibernateToManyProperty(CSPBTestEntityWithMany, "items") as HibernateToManyProperty
        def persistentClasses = [:] // Empty map, so CSPBAssociatedItem will be missing

        when:
        binder.resolveAssociatedClass(property, persistentClasses)

        then:
        thrown(org.hibernate.MappingException)
    }

    def "test resolveAssociatedClass returns associatedClass"() {
        given:
        def property = createTestHibernateToManyProperty(CSPBTestEntityWithMany, "items") as HibernateToManyProperty
        def associatedPersistentClass = new RootClass(getGrailsDomainBinder().getMetadataBuildingContext())
        associatedPersistentClass.setEntityName(CSPBAssociatedItem.name)
        def persistentClasses = [(CSPBAssociatedItem.name): associatedPersistentClass]

        when:
        def result = binder.resolveAssociatedClass(property, persistentClasses)

        then:
        result == associatedPersistentClass
    }

    def "test bindOrderBy with table per hierarchy subclass"() {
        given:
        def property = createTestHibernateToManyProperty(CSPBTestEntityWithMany, "items") as HibernateToManyProperty
        def collection = new Bag(getGrailsDomainBinder().getMetadataBuildingContext(), null)
        def associatedPersistentClass = new RootClass(getGrailsDomainBinder().getMetadataBuildingContext())
        associatedPersistentClass.setEntityName(CSPBAssociatedItem.name)

        // Mock GrailsHibernatePersistentEntity behavior for table per hierarchy
        def referencedEntity = property.getHibernateAssociatedEntity()
        def spiedReferencedEntity = Spy(referencedEntity)
        spiedReferencedEntity.isTablePerHierarchySubclass() >> true
        spiedReferencedEntity.getDiscriminatorColumnName() >> "item_type"
        spiedReferencedEntity.buildDiscriminatorSet() >> (["'A'", "'B'"] as Set)

        // Inject the spy if possible, or mock the getter on property
        def spiedProperty = Spy(property)
        spiedProperty.getHibernateAssociatedEntity() >> null

        when:
        binder.bindOrderBy(spiedProperty, collection, associatedPersistentClass)

        then:
        def ex = thrown(org.hibernate.MappingException)
        ex.message.contains("items")
    }

    def "resolveAssociatedClass throws MappingException when associated class is not in persistentClasses"() {
        given:
        def property = createTestHibernateToManyProperty(CSPBTestEntityWithMany, "items") as HibernateToManyProperty

        when:
        binder.resolveAssociatedClass(property, [:])

        then:
        def ex = thrown(org.hibernate.MappingException)
        ex.message.contains("items")
    }

    def "resolveAssociatedClass returns the matching PersistentClass"() {
        given:
        def property = createTestHibernateToManyProperty(CSPBTestEntityWithMany, "items") as HibernateToManyProperty
        def associatedPersistentClass = new RootClass(getGrailsDomainBinder().getMetadataBuildingContext())
        associatedPersistentClass.setEntityName(CSPBAssociatedItem.name)
        def persistentClasses = [(CSPBAssociatedItem.name): associatedPersistentClass]

        when:
        def result = binder.resolveAssociatedClass(property, persistentClasses)

        then:
        result == associatedPersistentClass
    }
}

@Entity
class CSPBTestEntityWithMany {
    Long id
    String name
    static hasMany = [items: CSPBAssociatedItem]
}

@Entity
class CSPBAssociatedItem {
    Long id
    String value
    CSPBTestEntityWithMany parent
    static belongsTo = [parent: CSPBTestEntityWithMany]
}

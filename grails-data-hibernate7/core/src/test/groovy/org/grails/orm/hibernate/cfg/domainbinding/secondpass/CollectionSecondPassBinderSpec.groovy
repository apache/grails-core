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
import grails.gorm.hibernate.HibernateEntity
import grails.gorm.specs.HibernateGormDatastoreSpec
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateToManyProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateManyToManyProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateOneToManyProperty

import org.hibernate.mapping.RootClass
import org.hibernate.mapping.OneToMany
import org.hibernate.mapping.ManyToOne
import org.hibernate.mapping.Column

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
        def cwjtb = new CollectionWithJoinTableBinder(ns, new UnidirectionalOneToManyInverseValuesBinder(mbc), citmto, new CollectionForPropertyConfigBinder(), new SimpleValueColumnBinder(), new BasicCollectionElementBinder(mbc, ns, null, new SimpleValueColumnBinder(), svcf, null))
        def uotmb = new UnidirectionalOneToManyBinder(cwjtb, mbc.getMetadataCollector())
        def cfpcb = new CollectionForPropertyConfigBinder()
        def dcnf = new DefaultColumnNameFetcher(ns, new BackticksRemover())
        def svcb = new SimpleValueColumnBinder()
        def cku = new CollectionKeyColumnUpdater(new CollectionKeyBinder(botml, dkvb, svcb, pkvc))

        binder = new CollectionSecondPassBinder(cku, uotmb, cwjtb, cfpcb, new BidirectionalMapElementBinder(mtob, cfpcb), new ManyToManyElementBinder(mtob, cfpcb), new CollectionOrderByBinder(), new CollectionMultiTenantFilterBinder(dcnf))
    }

    protected HibernatePersistentProperty createTestHibernateToManyProperty(Class<?> domainClass, String propertyName) {
        PersistentEntity entity = createPersistentEntity(domainClass)
        HibernatePersistentProperty property = (HibernatePersistentProperty) entity.getPropertyByName(propertyName)
        return property
    }

    def "bindCollectionSecondPass succeeds for Basic String collection"() {
        given: "An entity with a basic String collection"
        def property = createTestHibernateToManyProperty(CSPBHTMPOrder, "items") as HibernateToManyProperty

        and: "We trigger the first pass mapping"
        hibernateFirstPass()

        expect: "The Hibernate collection object is now initialized"
        property.getCollection() != null

        when: "Binding second pass"
        binder.bindCollectionSecondPass(property, [:])

        then:
        noExceptionThrown()
    }

    def "bindCollectionSecondPass succeeds for Unidirectional One-to-Many"() {
        given: "An entity with a unidirectional one-to-many collection"
        def property = createTestHibernateToManyProperty(CSPBUniOwner, "items") as HibernateOneToManyProperty
        
        and: "Hibernate RootClasses"
        hibernateFirstPass()
        def ownerRoot = getGrailsDomainBinder().getMetadataBuildingContext().getMetadataCollector().getEntityBinding(CSPBUniOwner.name)
        def itemRoot = getGrailsDomainBinder().getMetadataBuildingContext().getMetadataCollector().getEntityBinding(CSPBUniItem.name)
        def persistentClasses = [(CSPBUniOwner.name): ownerRoot, (CSPBUniItem.name): itemRoot]

        when: "Binding second pass"
        binder.bindCollectionSecondPass(property, persistentClasses)

        then:
        noExceptionThrown()
        // In GORM/Hibernate7, unidirectional one-to-many often defaults to join table, so it might not be OneToMany mapping object
        property.getCollection() != null
    }

    def "bindCollectionSecondPass succeeds for Bidirectional Many-to-Many"() {
        given: "Entities with a bidirectional many-to-many collection"
        createPersistentEntity(CSPBManyToManyB)
        def property = createTestHibernateToManyProperty(CSPBManyToManyA, "others") as HibernateManyToManyProperty
        
        and: "Hibernate RootClasses"
        hibernateFirstPass()
        def aRoot = getGrailsDomainBinder().getMetadataBuildingContext().getMetadataCollector().getEntityBinding(CSPBManyToManyA.name)
        def bRoot = getGrailsDomainBinder().getMetadataBuildingContext().getMetadataCollector().getEntityBinding(CSPBManyToManyB.name)
        def persistentClasses = [(CSPBManyToManyA.name): aRoot, (CSPBManyToManyB.name): bRoot]

        when: "Binding second pass"
        binder.bindCollectionSecondPass(property, persistentClasses)

        then:
        noExceptionThrown()
        property.isBidirectional()
        // In Hibernate 7 many-to-many element is mapped as ManyToOne to the join table
        property.getCollection().getElement() instanceof ManyToOne
    }

    def "bindCollectionSecondPass handles orderBy configuration"() {
        given: "An entity with orderBy in mapping (bidirectional to allow sort)"
        def property = createTestHibernateToManyProperty(CSPBOrderOwner, "items") as HibernateToManyProperty
        
        and: "Hibernate RootClasses"
        hibernateFirstPass()
        def ownerRoot = getGrailsDomainBinder().getMetadataBuildingContext().getMetadataCollector().getEntityBinding(CSPBOrderOwner.name)
        def itemRoot = getGrailsDomainBinder().getMetadataBuildingContext().getMetadataCollector().getEntityBinding(CSPBOrderItem.name)
        def persistentClasses = [(CSPBOrderOwner.name): ownerRoot, (CSPBOrderItem.name): itemRoot]

        when: "Binding second pass"
        binder.bindCollectionSecondPass(property, persistentClasses)

        then:
        noExceptionThrown()
        property.getCollection().getOrderBy() != null
    }

    def "bindCollectionSecondPass throws MappingException for missing associated class"() {
        given: "An association"
        def property = createTestHibernateToManyProperty(CSPBBidiOwner, "items") as HibernateToManyProperty
        
        and: "Missing associated class in persistentClasses map"
        hibernateFirstPass()

        when: "Binding second pass"
        binder.bindCollectionSecondPass(property, [:])

        then:
        def ex = thrown(org.hibernate.MappingException)
        ex.message.contains("Association [items] has no associated class")
    }

    def "resolveAssociatedClass throws MappingException when entity association is missing from persistentClasses"() {
        given: "A standard entity association (not a Basic collection)"
        def property = createTestHibernateToManyProperty(CSPBTestEntityWithMany, "items") as HibernateToManyProperty

        when: "Attempting to resolve associated class with an empty map"
        binder.resolveAssociatedClass(property, [:])

        then: "A MappingException is thrown because this is a real entity relationship"
        def ex = thrown(org.hibernate.MappingException)
        ex.message.contains("Association [items] has no associated class")
    }

    def "resolveAssociatedClass returns the matching PersistentClass"() {
        given:
        def property = createTestHibernateToManyProperty(CSPBTestEntityWithMany, "items") as HibernateToManyProperty
        hibernateFirstPass()
        def associatedPersistentClass = getGrailsDomainBinder().getMetadataBuildingContext().getMetadataCollector().getEntityBinding(CSPBAssociatedItem.name)
        def persistentClasses = [(CSPBAssociatedItem.name): associatedPersistentClass]

        when:
        def result = binder.resolveAssociatedClass(property, persistentClasses)

        then:
        result == associatedPersistentClass
    }
}

@Entity
class CSPBTestEntityWithMany implements HibernateEntity<CSPBTestEntityWithMany> {
    Long id
    String name
    static hasMany = [items: CSPBAssociatedItem]
}

@Entity
class CSPBAssociatedItem implements HibernateEntity<CSPBAssociatedItem> {
    Long id
    String value
    CSPBTestEntityWithMany parent
    static belongsTo = [parent: CSPBTestEntityWithMany]
}

@Entity
class CSPBHTMPOrder implements HibernateEntity<CSPBHTMPOrder> {
    Long id
    List<String> items = []
    static hasMany = [items: String]
}

@Entity
class CSPBUniOwner implements HibernateEntity<CSPBUniOwner> {
    Long id
    static hasMany = [items: CSPBUniItem]
}

@Entity
class CSPBUniItem implements HibernateEntity<CSPBUniItem> {
    Long id
    String name
}

@Entity
class CSPBManyToManyA implements HibernateEntity<CSPBManyToManyA> {
    Long id
    static hasMany = [others: CSPBManyToManyB]
}

@Entity
class CSPBManyToManyB implements HibernateEntity<CSPBManyToManyB> {
    Long id
    static hasMany = [owners: CSPBManyToManyA]
    static belongsTo = CSPBManyToManyA
}

@Entity
class CSPBOrderOwner implements HibernateEntity<CSPBOrderOwner> {
    Long id
    static hasMany = [items: CSPBOrderItem]
    static mapping = {
        items joinTable: [name: "ordered_items"], sort: "name", order: "desc"
    }
}

@Entity
class CSPBOrderItem implements HibernateEntity<CSPBOrderItem> {
    Long id
    String name
    CSPBOrderOwner owner
    static belongsTo = [owner: CSPBOrderOwner]
}

@Entity
class CSPBBidiOwner implements HibernateEntity<CSPBBidiOwner> {
    Long id
    static hasMany = [items: CSPBBidiItem]
}

@Entity
class CSPBBidiItem implements HibernateEntity<CSPBBidiItem> {
    Long id
    CSPBBidiOwner owner
    static belongsTo = [owner: CSPBBidiOwner]
}

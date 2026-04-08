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
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateToManyEntityProperty

import org.hibernate.mapping.ManyToOne

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
    BidirectionalMapElementBinder mockBidirectionalMapElementBinder = Mock(BidirectionalMapElementBinder)

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

        binder = new CollectionSecondPassBinder(
                cku,
                uotmb,
                cwjtb,
                mockBidirectionalMapElementBinder,
                new ManyToOneElementBinder(mtob, cfpcb),
                new HibernateToManyEntityOrderByBinder(),
                new ToManyEntityMultiTenantFilterBinder(dcnf)
        )
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
        binder.bindCollectionSecondPass(property)

        then:
        noExceptionThrown()
        !(property instanceof HibernateToManyEntityProperty)
    }

    def "bindCollectionSecondPass succeeds for Unidirectional One-to-Many"() {
        given: "An entity with a unidirectional one-to-many collection"
        def property = createTestHibernateToManyProperty(CSPBUniOwner, "items") as HibernateOneToManyProperty
        
        and: "Hibernate RootClasses"
        hibernateFirstPass()

        when: "Binding second pass"
        binder.bindCollectionSecondPass(property)

        then:
        noExceptionThrown()
        property instanceof HibernateToManyEntityProperty
        property.getCollection() != null
    }

    def "bindCollectionSecondPass succeeds for Bidirectional Many-to-Many"() {
        given: "Entities with a bidirectional many-to-many collection"
        createPersistentEntity(CSPBManyToManyB)
        def property = createTestHibernateToManyProperty(CSPBManyToManyA, "others") as HibernateManyToManyProperty
        
        and: "Hibernate RootClasses"
        hibernateFirstPass()

        when: "Binding second pass"
        binder.bindCollectionSecondPass(property)

        then:
        noExceptionThrown()
        property instanceof HibernateToManyEntityProperty
        property.isBidirectional()
        // In Hibernate 7 many-to-many element is mapped as ManyToOne to the join table
        property.getCollection().getElement() instanceof ManyToOne
    }

    def "bindCollectionSecondPass handles orderBy configuration"() {
        given: "An entity with orderBy in mapping (bidirectional to allow sort)"
        def property = createTestHibernateToManyProperty(CSPBOrderOwner, "items") as HibernateToManyProperty
        
        and: "Hibernate RootClasses"
        hibernateFirstPass()

        when: "Binding second pass"
        binder.bindCollectionSecondPass(property)

        then:
        noExceptionThrown()
        property instanceof HibernateToManyEntityProperty
        property.getCollection().getOrderBy() != null
    }

    def "bindCollectionSecondPass succeeds for Embedded Collection"() {
        given: "An entity with a collection handled as an embedded collection (e.g. Basic collection)"
        def property = createTestHibernateToManyProperty(CSPBHTMPOrder, "items") as HibernateToManyProperty
        
        and: "Hibernate RootClasses"
        hibernateFirstPass()

        when: "Binding second pass"
        binder.bindCollectionSecondPass(property)

        then:
        noExceptionThrown()
        !(property instanceof HibernateToManyEntityProperty)
        property.getCollection() != null
    }

    def "bindCollectionSecondPass succeeds for Bidirectional One-to-Many Map"() {
        given: "An entity with a bidirectional one-to-many map"
        def property = createTestHibernateToManyProperty(CSPBMapOwner, "items") as HibernateToManyProperty
        
        and: "Hibernate RootClasses"
        hibernateFirstPass()

        when: "Binding second pass"
        binder.bindCollectionSecondPass(property)

        then:
        noExceptionThrown()
        property.isBidirectional()
        property.isBidirectionalToManyMap()
        1 * mockBidirectionalMapElementBinder.bind(property)
    }

    def "HibernateCollectionProperty getAssociatedClass returns PersistentClass or throws MappingException"() {
        given: "A collection property that implements HibernateCollectionProperty"
        def property = createTestHibernateToManyProperty(CSPBUniOwner, "items") as HibernateToManyEntityProperty
        
        expect:
        property instanceof HibernateToManyEntityProperty

        when: "Persistent class is present (after first pass)"
        hibernateFirstPass()
        def associatedClass = property.getAssociatedClass()

        then:
        associatedClass != null
        associatedClass.entityName == CSPBUniItem.name

        when: "Associated entity is present but PersistentClass is missing"
        property.getHibernateAssociatedEntity().setPersistentClass(null)
        property.getAssociatedClass()

        then:
        def ex = thrown(org.hibernate.MappingException)
        ex.message.contains("items")
        ex.message.contains("has no associated class")
    }

    def "bindCollectionSecondPass skips element binding for embedded collection when componentBinder is null"() {
        given: "An embedded collection property with componentBinder not set on the binder"
        def mbc = getGrailsDomainBinder().getMetadataBuildingContext()
        def ownerClass = new org.hibernate.mapping.RootClass(mbc)
        ownerClass.setEntityName("EmbeddedOwner")
        def ownerTable = new org.hibernate.mapping.Table("test", "embedded_owner")
        ownerClass.setTable(ownerTable)
        def idValue = new org.hibernate.mapping.BasicValue(mbc, ownerTable)
        idValue.setTypeName("long")
        idValue.addColumn(new org.hibernate.mapping.Column("id"))
        ownerClass.setIdentifier(idValue)
        def bag = new org.hibernate.mapping.Bag(mbc, ownerClass)
        bag.setCollectionTable(new org.hibernate.mapping.Table("test", "embedded_owner_dims"))

        def embeddedProperty = Mock(org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateEmbeddedCollectionProperty)
        embeddedProperty.getCollection() >> bag
        embeddedProperty.isBidirectional() >> false
        embeddedProperty.isSorted() >> false
        embeddedProperty.getCacheUsage() >> null
        embeddedProperty.getHibernateMappedForm() >> Mock(org.grails.orm.hibernate.cfg.PropertyConfig) {
            hasJoinKeyMapping() >> false
        }
        def ownerEntity = Mock(org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity) {
            getPersistentPropertiesToBind() >> []
        }
        embeddedProperty.getOwner() >> ownerEntity
        embeddedProperty.getHibernateOwner() >> ownerEntity

        when: "second pass is run without a componentBinder"
        binder.bindCollectionSecondPass(embeddedProperty)

        then: "no exception — the element binding is skipped gracefully"
        noExceptionThrown()
        bag.element == null
    }

    def "setComponentBinder wires ComponentBinder into the binder"() {
        given:
        def mockComponentBinder = Mock(org.grails.orm.hibernate.cfg.domainbinding.binder.ComponentBinder)

        when:
        binder.setComponentBinder(mockComponentBinder)

        then: "no exception thrown — the setter is available"
        noExceptionThrown()
    }

    def "bindCollectionSecondPass calls componentBinder when set for embedded collection"() {
        given:
        def mbc = getGrailsDomainBinder().getMetadataBuildingContext()
        def ownerClass = new org.hibernate.mapping.RootClass(mbc)
        ownerClass.setEntityName("EmbeddedOwner2")
        def ownerTable = new org.hibernate.mapping.Table("test", "embedded_owner2")
        ownerClass.setTable(ownerTable)
        def idValue = new org.hibernate.mapping.BasicValue(mbc, ownerTable)
        idValue.setTypeName("long")
        idValue.addColumn(new org.hibernate.mapping.Column("id"))
        ownerClass.setIdentifier(idValue)
        def bag = new org.hibernate.mapping.Bag(mbc, ownerClass)
        bag.setCollectionTable(new org.hibernate.mapping.Table("test", "embedded_owner2_dims"))

        def mockComponent = Mock(org.hibernate.mapping.Component)
        def mockComponentBinder = Mock(org.grails.orm.hibernate.cfg.domainbinding.binder.ComponentBinder)

        def embeddedProperty = Mock(org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateEmbeddedCollectionProperty)
        embeddedProperty.getCollection() >> bag
        embeddedProperty.isBidirectional() >> false
        embeddedProperty.isSorted() >> false
        embeddedProperty.getCacheUsage() >> null
        embeddedProperty.getHibernateMappedForm() >> Mock(org.grails.orm.hibernate.cfg.PropertyConfig) {
            hasJoinKeyMapping() >> false
        }
        def ownerEntity = Mock(org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity) {
            getPersistentPropertiesToBind() >> []
        }
        embeddedProperty.getOwner() >> ownerEntity
        embeddedProperty.getHibernateOwner() >> ownerEntity

        binder.setComponentBinder(mockComponentBinder)

        when:
        binder.bindCollectionSecondPass(embeddedProperty)

        then:
        1 * mockComponentBinder.bindEmbeddedCollectionComponent(embeddedProperty) >> mockComponent
        bag.element == mockComponent
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

@Entity
class CSPBMapOwner implements HibernateEntity<CSPBMapOwner> {
    Long id
    Map<String, CSPBMapItem> items
    static hasMany = [items: CSPBMapItem]
}

@Entity
class CSPBMapItem implements HibernateEntity<CSPBMapItem> {
    Long id
    CSPBMapOwner owner
    static belongsTo = [owner: CSPBMapOwner]
}

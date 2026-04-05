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
import org.grails.orm.hibernate.cfg.domainbinding.binder.CompositeIdentifierToManyToOneBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.SimpleValueBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.SimpleValueColumnBinder
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateToManyProperty
import org.grails.orm.hibernate.cfg.domainbinding.util.GrailsPropertyResolver
import org.hibernate.mapping.Bag
import org.hibernate.mapping.BasicValue
import org.hibernate.mapping.Column
import org.hibernate.mapping.Component
import org.hibernate.mapping.Property
import org.hibernate.mapping.RootClass
import org.hibernate.mapping.Table
import spock.lang.Subject

class CollectionKeyBinderSpec extends HibernateGormDatastoreSpec {

    @Subject
    CollectionKeyBinder binder

    void setupSpec() {
        manager.addAllDomainClasses([
            CKBBidOwner,
            CKBBidItem,
            CKBManyToManyOwner,
            CKBManyToManyItem,
            CKBUniOwner,
            CKBUniItem,
            CKBJoinKeyOwner,
            CKBJoinKeyItem,
            CKBCompositeOwner,
            CKBCompositeItem
        ])
    }

    void setup() {
        def gdb = getGrailsDomainBinder()
        def mbc = gdb.getMetadataBuildingContext()
        def ns = gdb.getNamingStrategy()
        def je = gdb.getJdbcEnvironment()
        def svb = new SimpleValueBinder(mbc, ns, je)
        def citmto = new CompositeIdentifierToManyToOneBinder(new org.grails.orm.hibernate.cfg.domainbinding.util.ForeignKeyColumnCountCalculator(), ns, new org.grails.orm.hibernate.cfg.domainbinding.util.DefaultColumnNameFetcher(ns), new org.grails.orm.hibernate.cfg.domainbinding.util.BackticksRemover(), svb)
        def botml = new BidirectionalOneToManyLinker(new GrailsPropertyResolver())
        def dkvb = new DependentKeyValueBinder(svb, citmto)
        def svcb = new SimpleValueColumnBinder()
        def pkvc = new PrimaryKeyValueCreator(mbc)
        binder = new CollectionKeyBinder(botml, dkvb, svcb, pkvc)
    }

    private HibernateToManyProperty propertyFor(Class ownerClass, String name = "items") {
        (getPersistentEntity(ownerClass) as GrailsHibernatePersistentEntity).getPropertyByName(name) as HibernateToManyProperty
    }

    private RootClass rootClassWith(String entityName, String propName, String columnName) {
        def mbc = getGrailsDomainBinder().getMetadataBuildingContext()
        def rootClass = new RootClass(mbc)
        rootClass.setEntityName(entityName)
        def table = new Table("test", entityName.toLowerCase())
        def simpleValue = new BasicValue(mbc, table)
        simpleValue.setTypeName("long")
        simpleValue.addColumn(new Column(columnName))
        def prop = new Property()
        prop.setName(propName)
        prop.setValue(simpleValue)
        rootClass.addProperty(prop)
        return rootClass
    }

    private RootClass ownerRootClass(String tableName) {
        def mbc = getGrailsDomainBinder().getMetadataBuildingContext()
        def rootClass = new RootClass(mbc)
        def table = new Table("test", tableName)
        rootClass.setTable(table)
        def idValue = new BasicValue(mbc, table)
        idValue.setTypeName("long")
        idValue.addColumn(new Column("id"))
        rootClass.setIdentifier(idValue)
        return rootClass
    }

    private Bag bagWithOwner(RootClass owner, String collectionTableName) {
        def mbc = getGrailsDomainBinder().getMetadataBuildingContext()
        def bag = new Bag(mbc, owner)
        bag.setCollectionTable(new Table("test", collectionTableName))
        return bag
    }

    def "bind sets collection inverse for bidirectional one-to-many with foreign key"() {
        given:
        def property = propertyFor(CKBBidOwner)
        def ownerClass = ownerRootClass("ckb_bid_owner")
        def collection = bagWithOwner(ownerClass, "ckb_bid_item")
        property.setCollection(collection)

        and: "Setup associated class for the linker"
        def associatedClass = rootClassWith(CKBBidItem.name, "owner", "OWNER_ID")
        property.getHibernateInverseSide().getHibernateOwner().setPersistentClass(associatedClass)

        when:
        binder.bind(property)

        then:
        collection.isInverse()
        collection.getKey().getColumnSpan() > 0
    }

    def "bind delegates to dependentKeyValueBinder for bidirectional many-to-many"() {
        given:
        def property = propertyFor(CKBManyToManyOwner)
        def ownerClass = ownerRootClass("ckb_mtm_owner")
        def collection = bagWithOwner(ownerClass, "ckb_mtm_join")
        property.setCollection(collection)

        when:
        binder.bind(property)

        then:
        collection.getKey().getColumnSpan() > 0
        !collection.isInverse()
    }

    def "bind uses simpleValueColumnBinder for unidirectional with join key mapping"() {
        given:
        def property = propertyFor(CKBJoinKeyOwner)
        def ownerClass = ownerRootClass("ckb_join_key_owner")
        def collection = bagWithOwner(ownerClass, "ckb_join_key_owner_ckb_join_key_item")
        property.setCollection(collection)

        when:
        binder.bind(property)

        then:
        collection.getKey().getTypeName() == "long"
        collection.getKey().getColumnSpan() > 0
        !collection.isInverse()
    }

    def "bind delegates to dependentKeyValueBinder for unidirectional without join key mapping"() {
        given:
        def property = propertyFor(CKBUniOwner)
        def ownerClass = ownerRootClass("ckb_uni_owner")
        def collection = bagWithOwner(ownerClass, "ckb_uni_owner_ckb_uni_item")
        property.setCollection(collection)

        when:
        binder.bind(property)

        then:
        collection.getKey().getColumnSpan() > 0
        !collection.isInverse()
    }

    def "bind sets isSorted true for composite keys"() {
        given:
        def property = propertyFor(CKBCompositeOwner)
        def ownerClass = ownerRootClass("ckb_comp_owner")
        def table = ownerClass.getTable()
        
        // Mock a composite key
        def mbc = getGrailsDomainBinder().getMetadataBuildingContext()
        def compositeKey = new Component(mbc, ownerClass)
        ownerClass.setIdentifier(compositeKey)
        
        def collection = bagWithOwner(ownerClass, "ckb_comp_join")
        property.setCollection(collection)

        when:
        def key = binder.bind(property)

        then:
        key.isSorted()
    }
}

@Entity
class CKBBidOwner {
    Long id
    static hasMany = [items: CKBBidItem]
}

@Entity
class CKBBidItem {
    Long id
    CKBBidOwner owner
    static belongsTo = [owner: CKBBidOwner]
}

@Entity
class CKBManyToManyOwner {
    Long id
    static hasMany = [items: CKBManyToManyItem]
}

@Entity
class CKBManyToManyItem {
    Long id
    static hasMany = [owners: CKBManyToManyOwner]
}

@Entity
class CKBUniOwner {
    Long id
    static hasMany = [items: CKBUniItem]
}

@Entity
class CKBUniItem {
    Long id
    String description
}

@Entity
class CKBJoinKeyOwner {
    Long id
    static hasMany = [items: CKBJoinKeyItem]
    static mapping = {
        items joinTable: [key: 'owner_fk']
    }
}

@Entity
class CKBJoinKeyItem {
    Long id
    String description
}

@Entity
class CKBCompositeOwner implements Serializable {
    String name
    Integer code
    static hasMany = [items: CKBCompositeItem]
    static mapping = {
        id composite: ['name', 'code']
    }
}

@Entity
class CKBCompositeItem {
    Long id
    String val
}

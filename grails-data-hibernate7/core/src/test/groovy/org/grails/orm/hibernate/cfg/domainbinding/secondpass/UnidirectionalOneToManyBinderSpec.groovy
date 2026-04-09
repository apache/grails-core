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

package org.grails.orm.hibernate.cfg.domainbinding.secondpass;

import grails.gorm.specs.HibernateGormDatastoreSpec
import grails.persistence.Entity
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity
import org.grails.orm.hibernate.cfg.domainbinding.binder.CollectionForPropertyConfigBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.ColumnConfigToColumnBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.CompositeIdentifierToManyToOneBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.EnumTypeBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.SimpleValueColumnBinder
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateOneToManyProperty
import org.grails.orm.hibernate.cfg.domainbinding.util.BackticksRemover
import org.grails.orm.hibernate.cfg.domainbinding.util.ColumnNameForPropertyAndPathFetcher
import org.grails.orm.hibernate.cfg.domainbinding.util.DefaultColumnNameFetcher
import org.grails.orm.hibernate.cfg.domainbinding.util.SimpleValueColumnFetcher

import org.hibernate.mapping.Bag
import org.hibernate.mapping.BasicValue
import org.hibernate.mapping.OneToMany
import spock.lang.Subject

class UnidirectionalOneToManyBinderSpec extends HibernateGormDatastoreSpec {

    @Subject
    UnidirectionalOneToManyBinder binder

    def setupSpec() {
        manager.addAllDomainClasses([
                UniOwner, UniPet
        ])
    }

    def setup() {
        def grailsDomainBinder = getGrailsDomainBinder()
        def metadataBuildingContext = grailsDomainBinder.getMetadataBuildingContext()
        def namingStrategy = grailsDomainBinder.getNamingStrategy()
        def jdbcEnvironment = grailsDomainBinder.getJdbcEnvironment()
        def defaultColumnNameFetcher = new DefaultColumnNameFetcher(namingStrategy)
        def backticksRemover = new BackticksRemover()
        def columnNameForPropertyAndPathFetcher = new ColumnNameForPropertyAndPathFetcher(namingStrategy, defaultColumnNameFetcher, backticksRemover)

        def unidirectionalOneToManyInverseValuesBinder = new UnidirectionalOneToManyInverseValuesBinder(metadataBuildingContext)
        def enumTypeBinder = new EnumTypeBinder(metadataBuildingContext, columnNameForPropertyAndPathFetcher,namingStrategy)
        def compositeIdentifierToManyToOneBinder = new CompositeIdentifierToManyToOneBinder(metadataBuildingContext, namingStrategy, jdbcEnvironment)
        def simpleValueColumnFetcher = new SimpleValueColumnFetcher()
        def collectionForPropertyConfigBinder = new CollectionForPropertyConfigBinder()

        def collectionWithJoinTableBinder = new CollectionWithJoinTableBinder(
                namingStrategy,
                unidirectionalOneToManyInverseValuesBinder,
                compositeIdentifierToManyToOneBinder,
                collectionForPropertyConfigBinder,
                new SimpleValueColumnBinder(),
                new BasicCollectionElementBinder(
                        metadataBuildingContext,
                        namingStrategy,
                        enumTypeBinder,
                        new SimpleValueColumnBinder(),
                        simpleValueColumnFetcher,
                        new ColumnConfigToColumnBinder())
        )
        binder = new UnidirectionalOneToManyBinder(collectionWithJoinTableBinder, grailsDomainBinder.metadataBuildingContext.metadataCollector)
    }

    def "test bindUnidirectionalOneToMany with join table"() {
        given:
        def grailsDomainBinder = getGrailsDomainBinder()
        def ownerEntity = grailsDomainBinder.hibernateMappingContext.getPersistentEntity(UniOwner.name) as GrailsHibernatePersistentEntity
        def petEntity = grailsDomainBinder.hibernateMappingContext.getPersistentEntity(UniPet.name) as GrailsHibernatePersistentEntity

        def ownerToPetsProperty = ownerEntity.getPropertyByName("pets") as HibernateOneToManyProperty

        def mappings = grailsDomainBinder.metadataBuildingContext.metadataCollector
        def ownerPersistentClass = mappings.getEntityBinding(UniOwner.name)
        def collection = new Bag(grailsDomainBinder.metadataBuildingContext, ownerPersistentClass)
        def role = UniOwner.name + ".pets"
        collection.setRole(role)
        collection.setCollectionTable(ownerPersistentClass.getTable()) // Just use owner table for simplicity in this test
        def element = new OneToMany(grailsDomainBinder.metadataBuildingContext, ownerPersistentClass)
        element.setReferencedEntityName(petEntity.getName())
        collection.setElement(element)
        collection.setKey(new BasicValue(grailsDomainBinder.metadataBuildingContext, ownerPersistentClass.getTable()))

        ownerToPetsProperty.setCollection(collection)

        when:
        binder.bind(ownerToPetsProperty)

        then:
        collection.isInverse() == false
        // By default it uses join table because shouldBindWithForeignKey() is false for unidirectional OTM in hibernate7
        collection.getElement() instanceof org.hibernate.mapping.ManyToOne 
    }

    def "test bindUnidirectionalOneToMany with backref"() {
        given:
        def grailsDomainBinder = getGrailsDomainBinder()
        def ownerEntity = grailsDomainBinder.hibernateMappingContext.getPersistentEntity(UniOwner.name) as GrailsHibernatePersistentEntity
        def petEntity = grailsDomainBinder.hibernateMappingContext.getPersistentEntity(UniPet.name) as GrailsHibernatePersistentEntity

        def mappings = grailsDomainBinder.metadataBuildingContext.metadataCollector
        def ownerPersistentClass = mappings.getEntityBinding(UniOwner.name)
        def petPersistentClass = mappings.getEntityBinding(UniPet.name)

        // 1. Initialize the collection
        def collection = new Bag(grailsDomainBinder.metadataBuildingContext, ownerPersistentClass)
        collection.setRole(UniOwner.name + ".pets")

        // 2. IMPORTANT: Initialize and set the element (This fixes the NPE)
        def element = new OneToMany(grailsDomainBinder.metadataBuildingContext, ownerPersistentClass)
        element.setReferencedEntityName(petEntity.getName())
        collection.setElement(element)

        // 3. Set the key (the FK column mapping on the other side)
        collection.setKey(new BasicValue(grailsDomainBinder.metadataBuildingContext, petPersistentClass.getTable()))

        def ownerToPetsProperty = Stub(HibernateOneToManyProperty) {
            shouldBindWithForeignKey() >> true
            getOwner() >> ownerEntity
            getName() >> "pets"
            getCollection() >> collection
        }

        when:
        binder.bind(ownerToPetsProperty)

        then:
        collection.isInverse() == false
        petPersistentClass.getProperty("_UniOwner_petsBackref") != null
    }
}

@Entity
class UniOwner {
    Long id
    Set<UniPet> pets
    static hasMany = [pets: UniPet]
}

@Entity
class UniPet {
    Long id
}

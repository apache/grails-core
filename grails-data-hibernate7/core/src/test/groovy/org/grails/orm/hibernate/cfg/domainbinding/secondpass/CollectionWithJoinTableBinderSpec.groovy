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
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.orm.hibernate.cfg.domainbinding.binder.CollectionForPropertyConfigBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.ColumnConfigToColumnBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.CompositeIdentifierToManyToOneBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.EnumTypeBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.SimpleValueColumnBinder
import org.grails.orm.hibernate.cfg.domainbinding.util.SimpleValueColumnFetcher
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateToManyProperty
import org.hibernate.boot.spi.InFlightMetadataCollector
import org.hibernate.mapping.Collection
import org.hibernate.mapping.ManyToOne
import org.hibernate.mapping.RootClass
import org.hibernate.mapping.Set
import org.hibernate.mapping.Table
import org.hibernate.type.spi.TypeConfiguration
import spock.lang.Subject

class CollectionWithJoinTableBinderSpec extends HibernateGormDatastoreSpec {

    @Subject
    CollectionWithJoinTableBinder binder

    UnidirectionalOneToManyInverseValuesBinder unidirectionalOneToManyInverseValuesBinder = Mock(UnidirectionalOneToManyInverseValuesBinder)
    EnumTypeBinder enumTypeBinder = Mock(EnumTypeBinder)
    CompositeIdentifierToManyToOneBinder compositeIdentifierToManyToOneBinder = Mock(CompositeIdentifierToManyToOneBinder)
    SimpleValueColumnFetcher simpleValueColumnFetcher = Mock(SimpleValueColumnFetcher)
    CollectionForPropertyConfigBinder collectionForPropertyConfigBinder = Mock(CollectionForPropertyConfigBinder)
    SimpleValueColumnBinder simpleValueColumnBinder = new SimpleValueColumnBinder()
    ColumnConfigToColumnBinder columnConfigToColumnBinder = new ColumnConfigToColumnBinder()

    void setup() {
        def domainBinder = getGrailsDomainBinder()
        binder = new CollectionWithJoinTableBinder(
                domainBinder.metadataBuildingContext,
                domainBinder.namingStrategy,
                unidirectionalOneToManyInverseValuesBinder,
                enumTypeBinder,
                compositeIdentifierToManyToOneBinder,
                simpleValueColumnFetcher,
                collectionForPropertyConfigBinder,
                simpleValueColumnBinder,
                columnConfigToColumnBinder
        )
    }

    void "test bindCollectionWithJoinTable for basic type"() {
        given:
        PersistentEntity authorEntity = createPersistentEntity(CWJTBAuthor)
        HibernateToManyProperty property = (HibernateToManyProperty) authorEntity.getPropertyByName("tags")
        def domainBinder = getGrailsDomainBinder()

        InFlightMetadataCollector mappings = Mock(InFlightMetadataCollector)
        mappings.getTypeConfiguration() >> new TypeConfiguration()

        def owner = new RootClass(domainBinder.metadataBuildingContext)
        Collection collection = new Set(domainBinder.metadataBuildingContext, owner)
        collection.setCollectionTable(new Table("CWJTB_TAGS"))

        when:
        binder.bindCollectionWithJoinTable(property, mappings, collection)

        then:
        collection.getElement() != null
        1 * collectionForPropertyConfigBinder.bindCollectionForPropertyConfig(collection, property)
    }

    void "test bindCollectionWithJoinTable for entity association"() {
        given:
        createPersistentEntity(CWJTBBook)
        PersistentEntity authorEntity = createPersistentEntity(CWJTBAuthor)
        HibernateToManyProperty property = (HibernateToManyProperty) authorEntity.getPropertyByName("books")
        def domainBinder = getGrailsDomainBinder()

        InFlightMetadataCollector mappings = Mock(InFlightMetadataCollector)

        def owner = new RootClass(domainBinder.metadataBuildingContext)
        Collection collection = new Set(domainBinder.metadataBuildingContext, owner)
        collection.setCollectionTable(new Table("CWJTB_BOOKS"))

        when:
        binder.bindCollectionWithJoinTable(property, mappings, collection)

        then:
        collection.getElement() instanceof ManyToOne
        1 * collectionForPropertyConfigBinder.bindCollectionForPropertyConfig(collection, property)
    }
}

@Entity
class CWJTBBook {
    Long id
    String title
}

@Entity
class CWJTBAuthor {
    Long id
    String name
    java.util.Set<CWJTBBook> books
    java.util.Set<String> tags
    static hasMany = [books: CWJTBBook, tags: String]
}

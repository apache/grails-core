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

import grails.gorm.specs.HibernateGormDatastoreSpec
import org.grails.orm.hibernate.cfg.PersistentEntityNamingStrategy
import org.grails.orm.hibernate.cfg.domainbinding.binder.SimpleValueColumnBinder
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateToManyProperty
import org.hibernate.boot.spi.InFlightMetadataCollector
import org.hibernate.boot.spi.MetadataBuildingContext
import org.hibernate.mapping.OneToMany
import org.hibernate.mapping.RootClass
import org.hibernate.boot.spi.MetadataBuildingContext
import org.grails.orm.hibernate.cfg.PersistentEntityNamingStrategy
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment
import org.grails.orm.hibernate.cfg.domainbinding.binder.SimpleValueBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.EnumTypeBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.ForeignKeyOneToOneBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.ManyToOneBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.ManyToOneValuesBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.CompositeIdentifierToManyToOneBinder
import org.grails.orm.hibernate.cfg.domainbinding.util.SimpleValueColumnFetcher
import org.grails.orm.hibernate.cfg.domainbinding.binder.CollectionBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.OneToOneBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.ClassBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.ComponentBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.ComponentUpdater
import org.grails.orm.hibernate.cfg.domainbinding.util.BasicValueIdCreator
import org.grails.orm.hibernate.cfg.domainbinding.binder.GrailsPropertyBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.IdentityBinder
import org.hibernate.boot.spi.InFlightMetadataCollector
import org.grails.orm.hibernate.cfg.domainbinding.binder.VersionBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.CompositeIdBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.SimpleIdBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.PropertyBinder
import org.hibernate.mapping.BasicValue
import org.grails.orm.hibernate.cfg.domainbinding.util.BackticksRemover
import org.grails.orm.hibernate.cfg.domainbinding.util.DefaultColumnNameFetcher
import org.grails.orm.hibernate.cfg.domainbinding.util.ColumnNameForPropertyAndPathFetcher
import org.grails.orm.hibernate.cfg.domainbinding.collectionType.CollectionHolder
import org.grails.orm.hibernate.cfg.domainbinding.util.PropertyFromValueCreator
import org.grails.orm.hibernate.cfg.domainbinding.binder.GrailsDomainBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.SubClassBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.SubclassMappingBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.RootBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.RootPersistentClassCommonValuesBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.DiscriminatorPropertyBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.ColumnConfigToColumnBinder

import org.grails.orm.hibernate.cfg.domainbinding.binder.ClassPropertiesBinder
import org.grails.orm.hibernate.cfg.domainbinding.util.MultiTenantFilterBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.JoinedSubClassBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.UnionSubclassBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.SingleTableSubclassBinder

class ListSecondPassBinderSpec extends HibernateGormDatastoreSpec {

    protected Map getBinders(GrailsDomainBinder binder, InFlightMetadataCollector collector = getCollector()) {
        MetadataBuildingContext metadataBuildingContext = binder.getMetadataBuildingContext()
        PersistentEntityNamingStrategy namingStrategy = binder.getNamingStrategy()
        JdbcEnvironment jdbcEnvironment = binder.getJdbcEnvironment()
        BackticksRemover backticksRemover = new BackticksRemover()
        DefaultColumnNameFetcher defaultColumnNameFetcher = new DefaultColumnNameFetcher(namingStrategy, backticksRemover)
        ColumnNameForPropertyAndPathFetcher columnNameForPropertyAndPathFetcher = new ColumnNameForPropertyAndPathFetcher(namingStrategy, defaultColumnNameFetcher, backticksRemover)
        CollectionHolder collectionHolder = new CollectionHolder(metadataBuildingContext)
        SimpleValueBinder simpleValueBinder = new SimpleValueBinder(metadataBuildingContext, namingStrategy, jdbcEnvironment)
        EnumTypeBinder enumTypeBinderToUse = new EnumTypeBinder(metadataBuildingContext, columnNameForPropertyAndPathFetcher)
        SimpleValueColumnFetcher simpleValueColumnFetcher = new SimpleValueColumnFetcher()
        CompositeIdentifierToManyToOneBinder compositeIdentifierToManyToOneBinder = new CompositeIdentifierToManyToOneBinder(

                new org.grails.orm.hibernate.cfg.domainbinding.util.ForeignKeyColumnCountCalculator(),
                namingStrategy,
                defaultColumnNameFetcher,
                backticksRemover,
                simpleValueBinder
        )
        OneToOneBinder oneToOneBinder = new OneToOneBinder(metadataBuildingContext, simpleValueBinder)
        ManyToOneBinder manyToOneBinder = new ManyToOneBinder(metadataBuildingContext, namingStrategy, simpleValueBinder, new ManyToOneValuesBinder(), compositeIdentifierToManyToOneBinder)
        ForeignKeyOneToOneBinder foreignKeyOneToOneBinder = new ForeignKeyOneToOneBinder(manyToOneBinder, simpleValueColumnFetcher)

        CollectionBinder collectionBinder = new CollectionBinder(
                metadataBuildingContext,
                namingStrategy
                ,
                simpleValueBinder,
                enumTypeBinderToUse,
                manyToOneBinder,
                compositeIdentifierToManyToOneBinder,
                simpleValueColumnFetcher
                ,
                collectionHolder,
                collector
        )
        PropertyFromValueCreator propertyFromValueCreator = new PropertyFromValueCreator()
        ComponentUpdater componentUpdater = new ComponentUpdater(propertyFromValueCreator)
        ComponentBinder componentBinder = new ComponentBinder(
                metadataBuildingContext,
                binder.getMappingCacheHolder(),
                componentUpdater
        )

        GrailsPropertyBinder propertyBinder = new GrailsPropertyBinder(


                enumTypeBinderToUse,
                componentBinder,
                collectionBinder,
                simpleValueBinder
                ,
                oneToOneBinder,
                manyToOneBinder,
                foreignKeyOneToOneBinder

        )
        CompositeIdBinder compositeIdBinder = new CompositeIdBinder(metadataBuildingContext, componentUpdater, propertyBinder)
        PropertyBinder propertyBinderHelper = new PropertyBinder()
        SimpleIdBinder simpleIdBinder = new SimpleIdBinder(metadataBuildingContext, new BasicValueIdCreator(jdbcEnvironment, namingStrategy), simpleValueBinder, propertyBinderHelper)
        IdentityBinder identityBinder = new IdentityBinder(simpleIdBinder, compositeIdBinder)
        VersionBinder versionBinder = new VersionBinder(metadataBuildingContext, simpleValueBinder, propertyBinderHelper, BasicValue::new)

        ClassBinder classBinder = new ClassBinder(collector)
        ClassPropertiesBinder classPropertiesBinder = new ClassPropertiesBinder(propertyBinder, propertyFromValueCreator)
        MultiTenantFilterBinder multiTenantFilterBinder = new MultiTenantFilterBinder(new org.grails.orm.hibernate.cfg.domainbinding.util.GrailsPropertyResolver(), new org.grails.orm.hibernate.cfg.domainbinding.util.MultiTenantFilterDefinitionBinder(), collector, defaultColumnNameFetcher)
        JoinedSubClassBinder joinedSubClassBinder = new JoinedSubClassBinder(metadataBuildingContext, namingStrategy, new org.grails.orm.hibernate.cfg.domainbinding.binder.SimpleValueColumnBinder(), columnNameForPropertyAndPathFetcher, classBinder, collector)
        UnionSubclassBinder unionSubclassBinder = new UnionSubclassBinder(metadataBuildingContext, namingStrategy, classBinder, collector)
        SingleTableSubclassBinder singleTableSubclassBinder = new SingleTableSubclassBinder(classBinder, metadataBuildingContext)

        SubclassMappingBinder subclassMappingBinder = new SubclassMappingBinder(joinedSubClassBinder, unionSubclassBinder, singleTableSubclassBinder, classPropertiesBinder)
        SubClassBinder subClassBinder = new SubClassBinder(subclassMappingBinder, multiTenantFilterBinder, "dataSource")
        RootPersistentClassCommonValuesBinder rootPersistentClassCommonValuesBinder = new RootPersistentClassCommonValuesBinder(metadataBuildingContext, namingStrategy, identityBinder, versionBinder, classBinder, classPropertiesBinder, collector)
        DiscriminatorPropertyBinder discriminatorPropertyBinder = new DiscriminatorPropertyBinder(metadataBuildingContext, binder.getMappingCacheHolder(), new org.grails.orm.hibernate.cfg.domainbinding.binder.ConfiguredDiscriminatorBinder(new org.grails.orm.hibernate.cfg.domainbinding.binder.SimpleValueColumnBinder(), new ColumnConfigToColumnBinder()), new org.grails.orm.hibernate.cfg.domainbinding.binder.DefaultDiscriminatorBinder(new org.grails.orm.hibernate.cfg.domainbinding.binder.SimpleValueColumnBinder()))
        RootBinder rootBinder = new RootBinder("default", multiTenantFilterBinder, subClassBinder, rootPersistentClassCommonValuesBinder, discriminatorPropertyBinder, collector, binder.getMappingCacheHolder())

        return [
            propertyBinder: propertyBinder,
            collectionBinder: collectionBinder,
            identityBinder: identityBinder,
            versionBinder: versionBinder,
            defaultColumnNameFetcher: defaultColumnNameFetcher,
            columnNameForPropertyAndPathFetcher: columnNameForPropertyAndPathFetcher,
            classBinder: classBinder,
            classPropertiesBinder: classPropertiesBinder,
            multiTenantFilterBinder: multiTenantFilterBinder,
            joinedSubClassBinder: joinedSubClassBinder,
            unionSubclassBinder: unionSubclassBinder,
            singleTableSubclassBinder: singleTableSubclassBinder,
            subClassBinder: subClassBinder,
            rootBinder: rootBinder
        ]
    }

    protected void bindRoot(GrailsDomainBinder binder, GrailsHibernatePersistentEntity entity, InFlightMetadataCollector mappings, String sessionFactoryBeanName) {
        def binders = getBinders(binder, mappings)
        binders.rootBinder.bindRoot(entity)
    }

    void setupSpec() {
        manager.addAllDomainClasses([
            org.apache.grails.data.testing.tck.domains.Pet,
            org.apache.grails.data.testing.tck.domains.Person,
            org.apache.grails.data.testing.tck.domains.PetType,
            LSBAuthor,
            LSBBook
        ])
    }

    void "Test bind collection"() {
        given:
        def binder = getGrailsDomainBinder()
        def collectionBinder = getBinders(binder).collectionBinder
        def collector = getCollector()

        def personEntity = getPersistentEntity(org.apache.grails.data.testing.tck.domains.Person) as GrailsHibernatePersistentEntity
        def petEntity = getPersistentEntity(org.apache.grails.data.testing.tck.domains.Pet) as GrailsHibernatePersistentEntity

        def rootClass = new RootClass(binder.getMetadataBuildingContext())
        rootClass.setEntityName(personEntity.name)
        rootClass.setTable(collector.addTable(null, null, "PERSON", null, false, binder.getMetadataBuildingContext()))

        def petsProp = personEntity.getPropertyByName("pets") as HibernateToManyProperty

        when:
        def collection = collectionBinder.bindCollection(petsProp, rootClass, "")

        then:
        collection.role == "${personEntity.name}.pets".toString()
        collection.element instanceof OneToMany
        (collection.element as OneToMany).referencedEntityName == petEntity.name
    }

    void "test bidirectional list binding"() {
        given:
        def binder = getGrailsDomainBinder()
        def collector = getCollector()
        def metadataBuildingContext = binder.getMetadataBuildingContext()
        def binders = getBinders(binder, collector)
        def collectionBinder = binders.collectionBinder
        def listBinder = collectionBinder.listSecondPassBinder

        def authorEntity = getPersistentEntity(LSBAuthor) as GrailsHibernatePersistentEntity
        def bookEntity = getPersistentEntity(LSBBook) as GrailsHibernatePersistentEntity
        def booksProp = authorEntity.getPropertyByName("books") as HibernateToManyProperty

        def rootClass = new RootClass(metadataBuildingContext)
        rootClass.setEntityName(authorEntity.name)
        rootClass.setJpaEntityName(authorEntity.name)
        rootClass.setTable(collector.addTable(null, null, "LSB_AUTHOR", null, false, metadataBuildingContext))
        collector.addEntityBinding(rootClass)

        def bookRootClass = new RootClass(metadataBuildingContext)
        bookRootClass.setEntityName(bookEntity.name)
        bookRootClass.setJpaEntityName(bookEntity.name)
        bookRootClass.setTable(collector.addTable(null, null, "LSB_BOOK", null, false, metadataBuildingContext))
        collector.addEntityBinding(bookRootClass)

        def authorValue = new org.hibernate.mapping.ManyToOne(metadataBuildingContext, bookRootClass.getTable())
        authorValue.setReferencedEntityName(authorEntity.name)
        def authorCol = new org.hibernate.mapping.Column("author_id")
        authorCol.setValue(authorValue)
        authorValue.addColumn(authorCol)
        def authorProp = new org.hibernate.mapping.Property()
        authorProp.setName("author")
        authorProp.setValue(authorValue)
        bookRootClass.addProperty(authorProp)

        def persistentClasses = [
            (authorEntity.name): rootClass,
            (bookEntity.name): bookRootClass
        ]

        def list = new org.hibernate.mapping.List(metadataBuildingContext, rootClass)
        list.setRole("${authorEntity.name}.books".toString())
        list.setCollectionTable(rootClass.getTable())

        def element = new org.hibernate.mapping.ManyToOne(metadataBuildingContext, list.getCollectionTable())
        element.setReferencedEntityName(LSBBook.name)
        list.setElement(element)

        when:
        listBinder.bindListSecondPass(booksProp, persistentClasses, list)

        then:
        noExceptionThrown()
        list.index != null
        list.baseIndex == 0
    }
}

@grails.gorm.annotation.Entity
class LSBAuthor {
    Long id
    List<LSBBook> books
    static hasMany = [books: LSBBook]
}

@grails.gorm.annotation.Entity
class LSBBook {
    Long id
    LSBAuthor author
    static belongsTo = [author: LSBAuthor]
}

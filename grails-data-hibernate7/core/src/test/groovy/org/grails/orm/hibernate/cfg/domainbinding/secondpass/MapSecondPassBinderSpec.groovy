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
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateToManyProperty

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
import org.grails.orm.hibernate.cfg.domainbinding.util.TableForManyCalculator
import org.grails.orm.hibernate.cfg.domainbinding.binder.CollectionBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.OneToOneBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.ClassBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.ComponentBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.ComponentUpdater
import org.grails.orm.hibernate.cfg.domainbinding.util.BasicValueCreator
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
import org.grails.orm.hibernate.cfg.domainbinding.util.PropertyFromValueCreator
import org.grails.orm.hibernate.cfg.domainbinding.collectionType.CollectionHolder
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

class MapSecondPassBinderSpec extends HibernateGormDatastoreSpec {

    protected Map getBinders(GrailsDomainBinder binder) {
        def collector = getCollector()
        MetadataBuildingContext metadataBuildingContext = binder.getMetadataBuildingContext()
        PersistentEntityNamingStrategy namingStrategy = binder.getNamingStrategy()
        JdbcEnvironment jdbcEnvironment = binder.getJdbcEnvironment()
        BackticksRemover backticksRemover = new BackticksRemover()
        DefaultColumnNameFetcher defaultColumnNameFetcher = new DefaultColumnNameFetcher(namingStrategy, backticksRemover)
        ColumnNameForPropertyAndPathFetcher columnNameForPropertyAndPathFetcher = new ColumnNameForPropertyAndPathFetcher(namingStrategy, defaultColumnNameFetcher, backticksRemover)
        CollectionHolder collectionHolder = new CollectionHolder(metadataBuildingContext)
        SimpleValueBinder simpleValueBinder = new SimpleValueBinder(metadataBuildingContext, namingStrategy, jdbcEnvironment)
        EnumTypeBinder enumTypeBinderToUse = new EnumTypeBinder(metadataBuildingContext, columnNameForPropertyAndPathFetcher, namingStrategy)
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

        TableForManyCalculator tableForManyCalculator = new TableForManyCalculator(namingStrategy, getCollector())
        CollectionBinder collectionBinder = new CollectionBinder(
                metadataBuildingContext,
                namingStrategy,
                simpleValueBinder,
                enumTypeBinderToUse,
                manyToOneBinder,
                compositeIdentifierToManyToOneBinder,
                simpleValueColumnFetcher,
                collectionHolder,
                getCollector(),
                tableForManyCalculator
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
        SimpleIdBinder simpleIdBinder = new SimpleIdBinder(metadataBuildingContext, new BasicValueCreator(metadataBuildingContext, jdbcEnvironment, namingStrategy), simpleValueBinder, propertyBinderHelper)
        IdentityBinder identityBinder = new IdentityBinder(simpleIdBinder, compositeIdBinder)
        VersionBinder versionBinder = new VersionBinder(metadataBuildingContext, simpleValueBinder, propertyBinderHelper, BasicValue::new)

        ClassBinder classBinder = new ClassBinder(getCollector())
        ClassPropertiesBinder classPropertiesBinder = new ClassPropertiesBinder(propertyBinder, propertyFromValueCreator)
        MultiTenantFilterBinder multiTenantFilterBinder = new MultiTenantFilterBinder(new org.grails.orm.hibernate.cfg.domainbinding.util.GrailsPropertyResolver(), new org.grails.orm.hibernate.cfg.domainbinding.util.MultiTenantFilterDefinitionBinder(), getCollector(), defaultColumnNameFetcher)
        JoinedSubClassBinder joinedSubClassBinder = new JoinedSubClassBinder(metadataBuildingContext, namingStrategy, new org.grails.orm.hibernate.cfg.domainbinding.binder.SimpleValueColumnBinder(), columnNameForPropertyAndPathFetcher, classBinder, getCollector())
        UnionSubclassBinder unionSubclassBinder = new UnionSubclassBinder(metadataBuildingContext, namingStrategy, classBinder, getCollector())
        SingleTableSubclassBinder singleTableSubclassBinder = new SingleTableSubclassBinder(classBinder, metadataBuildingContext)

        SubclassMappingBinder subclassMappingBinder = new SubclassMappingBinder(joinedSubClassBinder, unionSubclassBinder, singleTableSubclassBinder, classPropertiesBinder)
        SubClassBinder subClassBinder = new SubClassBinder(subclassMappingBinder, multiTenantFilterBinder, "dataSource")
        RootPersistentClassCommonValuesBinder rootPersistentClassCommonValuesBinder = new RootPersistentClassCommonValuesBinder(metadataBuildingContext, namingStrategy, identityBinder, versionBinder, classBinder, classPropertiesBinder, getCollector())
        DiscriminatorPropertyBinder discriminatorPropertyBinder = new DiscriminatorPropertyBinder(metadataBuildingContext, binder.getMappingCacheHolder(), new org.grails.orm.hibernate.cfg.domainbinding.binder.ConfiguredDiscriminatorBinder(new org.grails.orm.hibernate.cfg.domainbinding.binder.SimpleValueColumnBinder(), new ColumnConfigToColumnBinder()), new org.grails.orm.hibernate.cfg.domainbinding.binder.DefaultDiscriminatorBinder(new org.grails.orm.hibernate.cfg.domainbinding.binder.SimpleValueColumnBinder()))
        RootBinder rootBinder = new RootBinder("default", multiTenantFilterBinder, subClassBinder, rootPersistentClassCommonValuesBinder, discriminatorPropertyBinder, getCollector(), binder.getMappingCacheHolder())

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
        def binders = getBinders(binder)
        binders.rootBinder.bindRoot(entity)
    }

    void setupSpec() {
        manager.addAllDomainClasses([
            org.apache.grails.data.testing.tck.domains.Pet,
            org.apache.grails.data.testing.tck.domains.Person,
            org.apache.grails.data.testing.tck.domains.PetType,
            MapSPBAuthor,
            MapSPBBook,
            MapSPBOwner
        ])
    }

    void "Test bind map"() {
        given:
        def binder = getGrailsDomainBinder()
        def collector = getCollector()
        def metadataBuildingContext = binder.getMetadataBuildingContext()
        def namingStrategy = binder.getNamingStrategy()
        def binders = getBinders(binder)
        def collectionBinder = binders.collectionBinder
        def mapBinder = collectionBinder.mapSecondPassBinder

        def authorEntity = getPersistentEntity(MapSPBAuthor) as GrailsHibernatePersistentEntity
        def bookEntity = getPersistentEntity(MapSPBBook) as GrailsHibernatePersistentEntity
        def booksProp = authorEntity.getPropertyByName("books") as HibernateToManyProperty

        def rootClass = new RootClass(metadataBuildingContext)
        rootClass.setEntityName(authorEntity.name)
        rootClass.setClassName(authorEntity.name)
        rootClass.setJpaEntityName(authorEntity.name)
        rootClass.setTable(collector.addTable(null, null, "MAPSPB_AUTHOR", null, false, metadataBuildingContext))
        collector.addEntityBinding(rootClass)

        def bookRootClass = new RootClass(metadataBuildingContext)
        bookRootClass.setEntityName(bookEntity.name)
        bookRootClass.setClassName(bookEntity.name)
        bookRootClass.setJpaEntityName(bookEntity.name)
        bookRootClass.setTable(collector.addTable(null, null, "MAPSPB_BOOK", null, false, metadataBuildingContext))
        collector.addEntityBinding(bookRootClass)

        def persistentClasses = [
            (authorEntity.name): rootClass,
            (bookEntity.name): bookRootClass
        ]

        def map = new org.hibernate.mapping.Map(metadataBuildingContext, rootClass)
        map.setRole("${authorEntity.name}.books".toString())
        map.setCollectionTable(rootClass.getTable())

        booksProp.setCollection(map)

        when:
        mapBinder.bindMapSecondPass(booksProp)

        then:
        noExceptionThrown()
        map.index != null
        map.index.isTypeSpecified()
        map.element != null
        !map.inverse
    }

    void "Test bind map with custom index column"() {
        given:
        def binder = getGrailsDomainBinder()
        def collector = getCollector()
        def metadataBuildingContext = binder.getMetadataBuildingContext()
        def namingStrategy = binder.getNamingStrategy()
        def binders = getBinders(binder)
        def collectionBinder = binders.collectionBinder
        def mapBinder = collectionBinder.mapSecondPassBinder

        def authorEntity = getPersistentEntity(MapSPBAuthor) as GrailsHibernatePersistentEntity
        def bookEntity = getPersistentEntity(MapSPBBook) as GrailsHibernatePersistentEntity
        def booksProp = authorEntity.getPropertyByName("books") as HibernateToManyProperty

        def rootClass = new RootClass(metadataBuildingContext)
        rootClass.setEntityName(authorEntity.name)
        rootClass.setClassName(authorEntity.name)
        rootClass.setJpaEntityName(authorEntity.name)
        rootClass.setTable(collector.addTable(null, null, "MAPSPB_AUTHOR", null, false, metadataBuildingContext))
        collector.addEntityBinding(rootClass)

        def bookRootClass = new RootClass(metadataBuildingContext)
        bookRootClass.setEntityName(bookEntity.name)
        bookRootClass.setClassName(bookEntity.name)
        bookRootClass.setJpaEntityName(bookEntity.name)
        bookRootClass.setTable(collector.addTable(null, null, "MAPSPB_BOOK", null, false, metadataBuildingContext))
        collector.addEntityBinding(bookRootClass)

        def persistentClasses = [
            (authorEntity.name): rootClass,
            (MapSPBBook.name): bookRootClass
        ]

        def map = new org.hibernate.mapping.Map(metadataBuildingContext, rootClass)
        map.setRole("${authorEntity.name}.books".toString())
        map.setCollectionTable(rootClass.getTable())
        
        def element = new org.hibernate.mapping.ManyToOne(metadataBuildingContext, map.getCollectionTable())
        element.setReferencedEntityName(MapSPBBook.name)
        map.setElement(element)

        booksProp.setCollection(map)

        when:
        mapBinder.bindMapSecondPass(booksProp)

        then:
        noExceptionThrown()
        map.index != null
        map.index.isTypeSpecified()
        map.index.getColumns()[0].name == "books_idx"
    }

    void "Test bind map with basic collection element sets the element value"() {
        given:
        def binder = getGrailsDomainBinder()
        def collector = getCollector()
        def metadataBuildingContext = binder.getMetadataBuildingContext()
        def binders = getBinders(binder)
        def collectionBinder = binders.collectionBinder
        def mapBinder = collectionBinder.mapSecondPassBinder

        def ownerEntity = getPersistentEntity(MapSPBOwner) as GrailsHibernatePersistentEntity
        def attrsProp = ownerEntity.getPropertyByName("attributes") as HibernateToManyProperty

        def rootClass = new RootClass(metadataBuildingContext)
        rootClass.setEntityName(ownerEntity.name)
        rootClass.setClassName(ownerEntity.name)
        rootClass.setJpaEntityName(ownerEntity.name)
        rootClass.setTable(collector.addTable(null, null, "MAPSPB_OWNER", null, false, metadataBuildingContext))
        collector.addEntityBinding(rootClass)

        def map = new org.hibernate.mapping.Map(metadataBuildingContext, rootClass)
        map.setRole("${ownerEntity.name}.attributes".toString())
        map.setCollectionTable(rootClass.getTable())

        attrsProp.setCollection(map)

        when:
        mapBinder.bindMapSecondPass(attrsProp)

        then:
        noExceptionThrown()
        map.index != null
        map.index.isTypeSpecified()
        map.element != null
        map.element instanceof org.hibernate.mapping.BasicValue
        !map.inverse
    }

    void "Test bind map with basic collection element uses correct column names"() {
        given:
        def binder = getGrailsDomainBinder()
        def collector = getCollector()
        def metadataBuildingContext = binder.getMetadataBuildingContext()
        def binders = getBinders(binder)
        def collectionBinder = binders.collectionBinder
        def mapBinder = collectionBinder.mapSecondPassBinder

        def ownerEntity = getPersistentEntity(MapSPBOwner) as GrailsHibernatePersistentEntity
        def attrsProp = ownerEntity.getPropertyByName("attributes") as HibernateToManyProperty

        def rootClass = new RootClass(metadataBuildingContext)
        rootClass.setEntityName(ownerEntity.name)
        rootClass.setClassName(ownerEntity.name)
        rootClass.setJpaEntityName(ownerEntity.name)
        rootClass.setTable(collector.addTable(null, null, "MAPSPB_OWNER2", null, false, metadataBuildingContext))
        collector.addEntityBinding(rootClass)

        def map = new org.hibernate.mapping.Map(metadataBuildingContext, rootClass)
        map.setRole("${ownerEntity.name}.attributes2".toString())
        map.setCollectionTable(rootClass.getTable())

        attrsProp.setCollection(map)

        when:
        mapBinder.bindMapSecondPass(attrsProp)

        then:
        noExceptionThrown()
        def indexColumn = map.index.getColumns()[0]
        def elementColumn = map.element.getColumns()[0]
        indexColumn != null
        elementColumn != null
    }
}

@grails.gorm.annotation.Entity
class MapSPBAuthor {
    Long id
    Map<String, MapSPBBook> books
    static hasMany = [books: MapSPBBook]
    static mapping = {
        books index: {
            column 'books_idx'
        }
    }
}

@grails.gorm.annotation.Entity
class MapSPBBook {
    Long id
    String title
}

@grails.gorm.annotation.Entity
class MapSPBOwner {
    Long id
    Map<String, String> attributes
    static hasMany = [attributes: String]
}

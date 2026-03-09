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

import grails.gorm.annotation.Entity
import grails.gorm.specs.HibernateGormDatastoreSpec
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentProperty
import org.grails.orm.hibernate.cfg.domainbinding.binder.GrailsDomainBinder

import org.hibernate.mapping.ManyToOne
import org.hibernate.mapping.Property
import org.hibernate.mapping.RootClass
import org.hibernate.mapping.Value

import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateBasicProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateEmbeddedProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateManyToOneProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateOneToOneProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateSimpleEnumProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateSimpleProperty
import org.grails.orm.hibernate.cfg.domainbinding.binder.CollectionBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.ClassBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.ComponentBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.ComponentUpdater
import org.grails.orm.hibernate.cfg.domainbinding.binder.EnumTypeBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.GrailsPropertyBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.ForeignKeyOneToOneBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.ManyToOneBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.OneToOneBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.SimpleValueBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.SubClassBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.SubclassMappingBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.RootBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.RootPersistentClassCommonValuesBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.DiscriminatorPropertyBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.ColumnConfigToColumnBinder
import org.grails.orm.hibernate.cfg.domainbinding.util.BackticksRemover
import org.grails.orm.hibernate.cfg.domainbinding.util.ColumnNameForPropertyAndPathFetcher
import org.grails.orm.hibernate.cfg.domainbinding.collectionType.CollectionHolder
import org.grails.orm.hibernate.cfg.domainbinding.util.DefaultColumnNameFetcher
import org.grails.orm.hibernate.cfg.domainbinding.util.PropertyFromValueCreator

import org.hibernate.mapping.BasicValue
import org.hibernate.boot.spi.MetadataBuildingContext
import org.grails.orm.hibernate.cfg.PersistentEntityNamingStrategy
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment
import org.grails.orm.hibernate.cfg.domainbinding.binder.ManyToOneValuesBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.CompositeIdentifierToManyToOneBinder
import org.grails.orm.hibernate.cfg.domainbinding.util.SimpleValueColumnFetcher
import org.grails.orm.hibernate.cfg.domainbinding.binder.IdentityBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.VersionBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.CompositeIdBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.SimpleIdBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.NaturalIdentifierBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.PropertyBinder
import org.grails.orm.hibernate.cfg.domainbinding.util.BasicValueIdCreator
import org.hibernate.boot.spi.InFlightMetadataCollector

import org.grails.orm.hibernate.cfg.domainbinding.binder.ClassPropertiesBinder
import org.grails.orm.hibernate.cfg.domainbinding.util.MultiTenantFilterBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.JoinedSubClassBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.UnionSubclassBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.SingleTableSubclassBinder

import static org.grails.orm.hibernate.cfg.domainbinding.binder.GrailsDomainBinder.EMPTY_PATH

class GrailsPropertyBinderSpec extends HibernateGormDatastoreSpec {



    abstract static class TestEmbedded extends HibernateEmbeddedProperty {
        TestEmbedded(PersistentEntity owner, MappingContext context, java.beans.PropertyDescriptor descriptor) {
            super(owner, context, descriptor);
        }
    }

    abstract static class TestBasic extends HibernateBasicProperty {
        TestBasic(GrailsHibernatePersistentEntity owner, MappingContext context, java.beans.PropertyDescriptor descriptor) {
            super(owner, context, descriptor);
        }
    }

    abstract static class TestSimple extends HibernateSimpleProperty {
        TestSimple(PersistentEntity owner, MappingContext context, java.beans.PropertyDescriptor descriptor) {
            super(owner, context, descriptor);
        }
    }

    abstract static class TestSimpleEnum extends HibernateSimpleEnumProperty {
        TestSimpleEnum(PersistentEntity owner, MappingContext context, java.beans.PropertyDescriptor descriptor) {
            super(owner, context, descriptor);
        }
    }

    private void setupProperty(PersistentProperty prop, String name, Mapping mapping, PersistentEntity owner) {
        prop.getName() >> name
        _ * prop.getOwner() >> owner
        if (prop instanceof HibernatePersistentProperty) {
            _ * ((HibernatePersistentProperty)prop).getHibernateOwner() >> owner
        }
        def config = new PropertyConfig()
        mapping.getColumns().put(name, config)
        prop.getMappedForm() >> config
    }

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
        componentBinder.setGrailsPropertyBinder(propertyBinder)
        CompositeIdBinder compositeIdBinder = new CompositeIdBinder(metadataBuildingContext, componentUpdater, propertyBinder);
        PropertyBinder propertyBinderHelper = new PropertyBinder()
        SimpleIdBinder simpleIdBinder = new SimpleIdBinder(metadataBuildingContext, new BasicValueIdCreator(jdbcEnvironment, namingStrategy), simpleValueBinder, propertyBinderHelper)
        IdentityBinder identityBinder = new IdentityBinder(simpleIdBinder, compositeIdBinder)
        VersionBinder versionBinder = new VersionBinder(metadataBuildingContext, simpleValueBinder, propertyBinderHelper, BasicValue::new)
        NaturalIdentifierBinder naturalIdentifierBinder = new NaturalIdentifierBinder()
        
        ClassBinder classBinder = new ClassBinder(collector)
        ClassPropertiesBinder classPropertiesBinder = new ClassPropertiesBinder(propertyBinder, propertyFromValueCreator, naturalIdentifierBinder)
        MultiTenantFilterBinder multiTenantFilterBinder = new MultiTenantFilterBinder(new org.grails.orm.hibernate.cfg.domainbinding.util.GrailsPropertyResolver(), new org.grails.orm.hibernate.cfg.domainbinding.util.MultiTenantFilterDefinitionBinder(), collector, defaultColumnNameFetcher)
        JoinedSubClassBinder joinedSubClassBinder = new JoinedSubClassBinder(metadataBuildingContext, namingStrategy, new org.grails.orm.hibernate.cfg.domainbinding.binder.SimpleValueColumnBinder(), columnNameForPropertyAndPathFetcher, classBinder, collector)
        UnionSubclassBinder unionSubclassBinder = new UnionSubclassBinder(metadataBuildingContext, namingStrategy, classBinder, collector)
        SingleTableSubclassBinder singleTableSubclassBinder = new SingleTableSubclassBinder(classBinder)

        SubclassMappingBinder subclassMappingBinder = new SubclassMappingBinder(metadataBuildingContext, joinedSubClassBinder, unionSubclassBinder, singleTableSubclassBinder, classPropertiesBinder)
        SubClassBinder subClassBinder = new SubClassBinder(binder.getMappingCacheHolder(), subclassMappingBinder, multiTenantFilterBinder, "dataSource")
        RootPersistentClassCommonValuesBinder rootPersistentClassCommonValuesBinder = new RootPersistentClassCommonValuesBinder(metadataBuildingContext, namingStrategy, identityBinder, versionBinder, classBinder, classPropertiesBinder, collector)
        DiscriminatorPropertyBinder discriminatorPropertyBinder = new DiscriminatorPropertyBinder(metadataBuildingContext, binder.getMappingCacheHolder(), new org.grails.orm.hibernate.cfg.domainbinding.binder.ConfiguredDiscriminatorBinder(new org.grails.orm.hibernate.cfg.domainbinding.binder.SimpleValueColumnBinder(), new ColumnConfigToColumnBinder()), new org.grails.orm.hibernate.cfg.domainbinding.binder.DefaultDiscriminatorBinder(new org.grails.orm.hibernate.cfg.domainbinding.binder.SimpleValueColumnBinder()))
        RootBinder rootBinder = new RootBinder("default", multiTenantFilterBinder, subClassBinder, rootPersistentClassCommonValuesBinder, discriminatorPropertyBinder, collector)

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
            naturalIdentifierBinder: naturalIdentifierBinder,
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
            org.apache.grails.data.testing.tck.domains.PersonWithCompositeKey
        ])
    }

    void "Test bind simple property"() {
        given:
        def collector = getCollector()
        def binder = getGrailsDomainBinder()
        def propertyBinder = getBinders(binder).propertyBinder

        // 1. Create the entity metadata
        def persistentEntity = createPersistentEntity(binder, "SimpleBook", [title: String], [:])

        // 2. Setup the Hibernate mapping object
        def rootClass = new RootClass(binder.getMetadataBuildingContext())
        rootClass.setEntityName(persistentEntity.name)
        rootClass.setJpaEntityName(persistentEntity.name)
        rootClass.setTable(collector.addTable(null, null, "SIMPLE_BOOK", null, false, binder.getMetadataBuildingContext()))

        // --- THE FIX: Bridge the GORM entity to the Hibernate RootClass ---
        ((GrailsHibernatePersistentEntity)persistentEntity).setPersistentClass(rootClass)
        // ------------------------------------------------------------------

        when:
        def titleProp = persistentEntity.getPropertyByName("title") as HibernatePersistentProperty
        Value value = propertyBinder.bindProperty(rootClass, rootClass.table, EMPTY_PATH, null, titleProp)
        rootClass.addProperty(new PropertyFromValueCreator().createProperty(value, titleProp))

        then:
        Property prop = rootClass.getProperty("title")
        prop != null
        prop.value instanceof org.hibernate.mapping.SimpleValue
        ((org.hibernate.mapping.SimpleValue)prop.value).typeName == String.name
    }

    void "Test bind enum property"() {
        given:
        def collector = getCollector()
        def binder = getGrailsDomainBinder()
        def propertyBinder = getBinders(binder).propertyBinder
        def persistentEntity = createPersistentEntity(binder, "EnumBook", [status: java.util.concurrent.TimeUnit], [:])
        def rootClass = new RootClass(binder.getMetadataBuildingContext())
        rootClass.setEntityName(persistentEntity.name)
        rootClass.setTable(collector.addTable(null, null, "ENUM_BOOK", null, false, binder.getMetadataBuildingContext()))

        def statusProp = Mock(TestSimpleEnum)
        setupProperty(statusProp, "status", new Mapping(), persistentEntity)
        statusProp.getType() >> java.util.concurrent.TimeUnit
        statusProp.isValidHibernateOneToOne() >> false
        statusProp.isValidHibernateManyToOne() >> false

        when:
        Value value = propertyBinder.bindProperty(rootClass, rootClass.table, EMPTY_PATH, null, statusProp)
        rootClass.addProperty(new PropertyFromValueCreator().createProperty(value, statusProp))

        then:
        Property prop = rootClass.getProperty("status")
        prop != null
        prop.value instanceof BasicValue
        // Default enum mapping uses Hibernate 7 native STRING style (no typeName)
        ((BasicValue)prop.value).typeName == null
        ((BasicValue)prop.value).enumerationStyle == jakarta.persistence.EnumType.STRING
    }

    void "Test bind many-to-one"() {
        given:
        def binder = getGrailsDomainBinder()
        def propertyBinder = getBinders(binder).propertyBinder
        def collector = getCollector()

        def petEntity = getPersistentEntity(org.apache.grails.data.testing.tck.domains.Pet) as GrailsHibernatePersistentEntity
        def personEntity = getPersistentEntity(org.apache.grails.data.testing.tck.domains.Person) as GrailsHibernatePersistentEntity

        def rootClass = new RootClass(binder.getMetadataBuildingContext())
        rootClass.setEntityName(petEntity.name)
        rootClass.setTable(collector.addTable(null, null, "PET", null, false, binder.getMetadataBuildingContext()))

        when:
        def ownerProp = petEntity.getPropertyByName("owner") as HibernatePersistentProperty
        Value value = propertyBinder.bindProperty(rootClass, rootClass.table, EMPTY_PATH, null, ownerProp)
        rootClass.addProperty(new PropertyFromValueCreator().createProperty(value, ownerProp))

        then:
        Property prop = rootClass.getProperty("owner")
        prop != null
        prop.value instanceof ManyToOne
        ((ManyToOne)prop.value).referencedEntityName == personEntity.name
    }

    void "Test bind embedded property"() {
        given:
        def collector = getCollector()
        def binder = getGrailsDomainBinder()
        def propertyBinder = getBinders(binder).propertyBinder

        // 1. Create the entities
        def persistentEntity = createPersistentEntity(binder, "Employee", [name: String, homeAddress: Address], [:], ["homeAddress"])

        // 2. Setup Hibernate RootClass and Table
        def rootClass = new RootClass(binder.getMetadataBuildingContext())
        rootClass.setEntityName(persistentEntity.name)
        def table = collector.addTable(null, null, "EMPLOYEE", null, false, binder.getMetadataBuildingContext())
        rootClass.setTable(table)

        // 3. THE CRITICAL FIX: Link the GORM entity to the Hibernate RootClass
        // This prevents the NPE on line 73 of GrailsPropertyBinder
        ((GrailsHibernatePersistentEntity)persistentEntity).setPersistentClass(rootClass)

        when:
        def addressProp = persistentEntity.getPropertyByName("homeAddress") as HibernatePersistentProperty
        Value value = propertyBinder.bindProperty(rootClass, rootClass.table, EMPTY_PATH, null, addressProp)
        rootClass.addProperty(new PropertyFromValueCreator().createProperty(value, addressProp))

        then:
        Property prop = rootClass.getProperty("homeAddress")
        prop != null
        prop.value instanceof org.hibernate.mapping.Component

        def component = prop.value as org.hibernate.mapping.Component
        component.getComponentClassName() == Address.name
    }

    void "Test bind set collection"() {
        given:
        def binder = getGrailsDomainBinder()
        def propertyBinder = getBinders(binder).propertyBinder
        def collector = getCollector()

        def personEntity = getPersistentEntity(org.apache.grails.data.testing.tck.domains.Person) as GrailsHibernatePersistentEntity
        def petEntity = getPersistentEntity(org.apache.grails.data.testing.tck.domains.Pet) as GrailsHibernatePersistentEntity

        def rootClass = new RootClass(binder.getMetadataBuildingContext())
        rootClass.setEntityName(personEntity.name)
        rootClass.setTable(collector.addTable(null, null, "PERSON", null, false, binder.getMetadataBuildingContext()))

        // --- FIX STARTS HERE ---
        // Link the owner of the "pets" property
        personEntity.setPersistentClass(rootClass)

        // Link the target entity of the collection
        // (In a real app, Pet would have its own RootClass, but for a
        // unit test, linking it to the current context is often enough)
        petEntity.setPersistentClass(rootClass)
        // -----------------------

        when:
        def petsProp = personEntity.getPropertyByName("pets") as HibernatePersistentProperty
        Value value = propertyBinder.bindProperty(rootClass, rootClass.table, EMPTY_PATH, null, petsProp)
        rootClass.addProperty(new PropertyFromValueCreator().createProperty(value, petsProp))

        then:
        Property prop = rootClass.getProperty("pets")
        prop != null
        prop.value instanceof org.hibernate.mapping.Set
        def set = prop.value as org.hibernate.mapping.Set
        set.element instanceof org.hibernate.mapping.OneToMany
        (set.element as org.hibernate.mapping.OneToMany).referencedEntityName == petEntity.name
    }

    void "Test bind list collection"() {
        given:
        def binder = getGrailsDomainBinder()
        def collector = getCollector()
        def propertyBinder = getBinders(binder, collector).propertyBinder
        def bookEntity = createPersistentEntity(ListBook)
        def authorEntity = createPersistentEntity(ListAuthor)

        // Register referenced entity in Hibernate
        bindRoot(binder, bookEntity, collector, "sessionFactory")

        def rootClass = new RootClass(binder.getMetadataBuildingContext())
        rootClass.setEntityName(authorEntity.name)
        rootClass.setJpaEntityName(authorEntity.name)
        rootClass.setTable(collector.addTable(null, null, "LIST_AUTHOR", null, false, binder.getMetadataBuildingContext()))

        // --- FIX STARTS HERE ---
        // Link the GORM entity metadata to the Hibernate mapping object
        ((GrailsHibernatePersistentEntity)authorEntity).setPersistentClass(rootClass)
        // -----------------------

        def pk = new org.hibernate.mapping.PrimaryKey(rootClass.table)
        def idCol = new org.hibernate.mapping.Column("id")
        rootClass.table.addColumn(idCol)
        pk.addColumn(idCol)
        rootClass.table.setPrimaryKey(pk)
        collector.addEntityBinding(rootClass)

        when:
        def booksProp = authorEntity.getPropertyByName("books") as HibernatePersistentProperty
        Value value = propertyBinder.bindProperty(rootClass, rootClass.table, EMPTY_PATH, null, booksProp)
        rootClass.addProperty(new PropertyFromValueCreator().createProperty(value, booksProp))
        collector.processSecondPasses(binder.getMetadataBuildingContext())

        then:
        Property prop = rootClass.getProperty("books")
        prop != null
        prop.value instanceof org.hibernate.mapping.List
        def list = prop.value as org.hibernate.mapping.List
        list.index != null
        list.element != null
    }

    void "Test bind map collection"() {
        given:
        def binder = getGrailsDomainBinder()
        def collector = getCollector()
        def propertyBinder = getBinders(binder, collector).propertyBinder

        def bookEntity = createPersistentEntity(MapBook)
        def authorEntity = createPersistentEntity(MapAuthor)

        // Register referenced entity in Hibernate
        bindRoot(binder, bookEntity, collector, "sessionFactory")

        // Manually create RootClass for the main entity
        def rootClass = new RootClass(binder.getMetadataBuildingContext())
        rootClass.setEntityName(authorEntity.name)
        rootClass.setJpaEntityName(authorEntity.name)
        rootClass.setTable(collector.addTable(null, null, "MAP_AUTHOR", null, false, binder.getMetadataBuildingContext()))

        // --- STEP 1 & 2: Link the GORM entity to the Hibernate RootClass ---
        ((GrailsHibernatePersistentEntity)authorEntity).setPersistentClass(rootClass)
        // ------------------------------------------------------------------

        def pk = new org.hibernate.mapping.PrimaryKey(rootClass.table)
        def idCol = new org.hibernate.mapping.Column("id")
        rootClass.table.addColumn(idCol)
        pk.addColumn(idCol)
        rootClass.table.setPrimaryKey(pk)
        collector.addEntityBinding(rootClass)

        when:
        def booksProp = authorEntity.getPropertyByName("books") as HibernatePersistentProperty
        Value value = propertyBinder.bindProperty(rootClass, rootClass.table, EMPTY_PATH, null, booksProp)
        rootClass.addProperty(new PropertyFromValueCreator().createProperty(value, booksProp))
        collector.processSecondPasses(binder.getMetadataBuildingContext())

        then:
        Property prop = rootClass.getProperty("books")
        prop != null
        prop.value instanceof org.hibernate.mapping.Map
        def map = prop.value as org.hibernate.mapping.Map
        map.index != null
        map.element != null
    }

    void "Test bind composite identifier"() {
        given:
        def binder = getGrailsDomainBinder()
        def collector = getCollector()

        def personEntity = getPersistentEntity(org.apache.grails.data.testing.tck.domains.PersonWithCompositeKey) as GrailsHibernatePersistentEntity
        
        when:
        bindRoot(binder, personEntity, collector, "sessionFactory")
        def rootClass = collector.getEntityBinding(personEntity.name)

        then:
        rootClass.identifier instanceof org.hibernate.mapping.Component
        def identifier = rootClass.identifier as org.hibernate.mapping.Component
        identifier.propertySpan == 2
        identifier.getProperty("firstName") != null
        identifier.getProperty("lastName") != null
    }

    // New test for OneToOne property binding
    void "Test bind one-to-one property"() {
        given:
        def binder = getGrailsDomainBinder()
        def propertyBinder = getBinders(binder).propertyBinder
        def collector = getCollector()

        def authorEntity = createPersistentEntity(AuthorWithOneToOne) as GrailsHibernatePersistentEntity
        def bookEntity = createPersistentEntity(BookForOneToOne) as GrailsHibernatePersistentEntity

        // Register referenced entity in Hibernate (this creates a RootClass for Book)
        bindRoot(binder, bookEntity, collector, "sessionFactory")

        def rootClass = new RootClass(binder.getMetadataBuildingContext())
        rootClass.setEntityName(authorEntity.name)
        rootClass.setJpaEntityName(authorEntity.name)
        rootClass.setTable(collector.addTable(null, null, "AUTHOR_ONE_TO_ONE", null, false, binder.getMetadataBuildingContext()))

        // --- THE FIX: Bridge BOTH entities ---
        // 1. Link the Author (Owner) to the manually created rootClass
        authorEntity.setPersistentClass(rootClass)

        // 2. Link the Book (Child) to the RootClass created by bindRoot
        def bookRootClass = collector.getEntityBinding(bookEntity.name)
        bookEntity.setPersistentClass(bookRootClass)
        // --------------------------------------

        def pk = new org.hibernate.mapping.PrimaryKey(rootClass.table)
        def idCol = new org.hibernate.mapping.Column("id")
        rootClass.table.addColumn(idCol)
        pk.addColumn(idCol)
        rootClass.table.setPrimaryKey(pk)
        collector.addEntityBinding(rootClass)

        when:
        def childBookProp = authorEntity.getPropertyByName("childBook") as HibernatePersistentProperty
        Value value = propertyBinder.bindProperty(rootClass, rootClass.table, EMPTY_PATH, null, childBookProp)
        rootClass.addProperty(new PropertyFromValueCreator().createProperty(value, childBookProp))
        collector.processSecondPasses(binder.getMetadataBuildingContext())

        then:
        Property prop = rootClass.getProperty("childBook")
        prop != null
        prop.value instanceof org.hibernate.mapping.OneToOne
        def oneToOne = prop.value as org.hibernate.mapping.OneToOne
        oneToOne.referencedEntityName == bookEntity.name
    }

    void "should use binders from public constructor"() {
        given:
        def metadataBuildingContext = Mock(org.hibernate.boot.spi.MetadataBuildingContext)
        def namingStrategy = Mock(org.grails.orm.hibernate.cfg.PersistentEntityNamingStrategy)
        // CollectionHolder is a Java record (final), so we instantiate it
        def collectionType = new org.grails.orm.hibernate.cfg.domainbinding.collectionType.CollectionType(Object.class, metadataBuildingContext) {
            @Override
            org.hibernate.mapping.Collection createCollection(org.hibernate.mapping.PersistentClass owner) {
                return null
            }
        }
        def collectionHolder = new org.grails.orm.hibernate.cfg.domainbinding.collectionType.CollectionHolder([(Object.class): collectionType])
        def enumTypeBinder = Mock(EnumTypeBinder)
        def componentBinder = Mock(ComponentBinder)
        def collectionBinder = Mock(CollectionBinder)
        def propertyFromValueCreator = Mock(PropertyFromValueCreator)
        def simpleValueBinder = Mock(SimpleValueBinder)
        def columnNameForPropertyAndPathFetcher = Mock(ColumnNameForPropertyAndPathFetcher)
        def oneToOneBinder = Mock(OneToOneBinder)
        def manyToOneBinder = Mock(ManyToOneBinder)
        def foreignKeyOneToOneBinder = Mock(ForeignKeyOneToOneBinder)

        // Instantiate GrailsPropertyBinder using the public constructor with necessary mocks
        def propertyBinder = new GrailsPropertyBinder(


                enumTypeBinder,
                componentBinder,
                collectionBinder,
                simpleValueBinder
                ,
                oneToOneBinder,
                manyToOneBinder,
                foreignKeyOneToOneBinder

        )

        def mappings = Mock(org.hibernate.boot.spi.InFlightMetadataCollector)
        metadataBuildingContext.getMetadataCollector() >> mappings

        def rootClass = new RootClass(metadataBuildingContext)
        def currentGrailsProp = Mock(HibernatePersistentProperty)
        def table = new org.hibernate.mapping.Table("TEST_TABLE")
        rootClass.setTable(table)

        // Stubbing getTable() to return our table variable
        currentGrailsProp.getTable() >> table

        // Mocking other necessary properties of currentGrailsProp
        def mockOwner = Mock(GrailsHibernatePersistentEntity)
        def mockMapping = new org.grails.orm.hibernate.cfg.Mapping()
        mockMapping.setComment("test comment") // Provide a comment
        currentGrailsProp.getHibernateOwner() >> mockOwner
        mockOwner.getMappedForm() >> mockMapping // Return the Mapping object

        // Stubbing getOwner() to return mockOwner
        currentGrailsProp.getOwner() >> mockOwner
        mockOwner.isRoot() >> true // Stub isRoot() to prevent NPE in ColumnBinder

        // Mocking other necessary properties of currentGrailsProp
        currentGrailsProp.getType() >> String.class
        currentGrailsProp.getName() >> "title"
        simpleValueBinder.bindSimpleValue(currentGrailsProp, null, table, EMPTY_PATH) >> new BasicValue(metadataBuildingContext, table)

        when:
        // Capture the return value of bindProperty
        def resultValue = propertyBinder.bindProperty(rootClass, table, EMPTY_PATH, null, currentGrailsProp)

        then:
        // Assert that bindProperty returns a Value object
        resultValue instanceof Value
    }
}


// Define simple entities for the OneToOne test
@Entity
class AuthorWithOneToOne { // Added 'static'
    Long id
    BookForOneToOne childBook
    static hasOne = [childBook: BookForOneToOne]
}

@Entity
class BookForOneToOne { // Added 'static'
    Long id
    String title
    AuthorWithOneToOne parentAuthor
}
class Address {
    String city
    String zip
}

@Entity
class TestEntityWithSerializableCollection {
    Long id
    List<SerializableObject> serializableObjects
    static mapping = {
        serializableObjects type: 'serializable'
    }
}

class SerializableObject {
    String data
}

@Entity
class ListAuthor {
    Long id
    List<ListBook> books
    static hasMany = [books: ListBook]
}

@Entity
class ListBook {
    Long id
    String title
}

@Entity
class MapAuthor {
    Long id
    Map<String, MapBook> books
    static hasMany = [books: MapBook]
}

@Entity
class MapBook {
    Long id
    String title
}

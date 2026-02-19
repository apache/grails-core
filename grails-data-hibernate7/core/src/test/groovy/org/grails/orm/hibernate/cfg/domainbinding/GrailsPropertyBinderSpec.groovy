package org.grails.orm.hibernate.cfg.domainbinding

import grails.gorm.annotation.Entity
import grails.gorm.specs.HibernateGormDatastoreSpec
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentProperty
import org.grails.orm.hibernate.cfg.domainbinding.binder.GrailsDomainBinder
import org.grails.orm.hibernate.cfg.Mapping
import org.grails.orm.hibernate.cfg.PropertyConfig
import org.hibernate.mapping.ManyToOne
import org.hibernate.mapping.Property
import org.hibernate.mapping.RootClass
import org.hibernate.mapping.SimpleValue
import org.hibernate.mapping.Value

import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateBasicProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateEmbeddedProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateManyToOneProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateOneToOneProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateSimpleProperty
import org.grails.orm.hibernate.cfg.domainbinding.binder.CollectionBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.ClassBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.ComponentBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.ComponentUpdater
import org.grails.orm.hibernate.cfg.domainbinding.binder.EnumTypeBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.GrailsPropertyBinder
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
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.MappingContext

import static org.grails.orm.hibernate.cfg.domainbinding.binder.GrailsDomainBinder.EMPTY_PATH

class GrailsPropertyBinderSpec extends HibernateGormDatastoreSpec {

    abstract static class TestManyToOne extends HibernateManyToOneProperty {
        TestManyToOne(PersistentEntity owner, MappingContext context, java.beans.PropertyDescriptor descriptor) {
            super(owner, context, descriptor);
        }
    }

    abstract static class TestOneToOne extends HibernateOneToOneProperty {
        TestOneToOne(PersistentEntity owner, MappingContext context, java.beans.PropertyDescriptor descriptor) {
            super(owner, context, descriptor);
        }
    }

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

    private void setupProperty(PersistentProperty prop, String name, Mapping mapping, PersistentEntity owner) {
        prop.getName() >> name
        _ * prop.getOwner() >> owner
        if (prop instanceof GrailsHibernatePersistentProperty) {
            _ * ((GrailsHibernatePersistentProperty)prop).getHibernateOwner() >> owner
        }
        def config = new PropertyConfig()
        mapping.getColumns().put(name, config)
        prop.getMappedForm() >> config
    }

    protected Map getBinders(GrailsDomainBinder binder) {
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
                metadataBuildingContext,
                new org.grails.orm.hibernate.cfg.domainbinding.util.ForeignKeyColumnCountCalculator(),
                namingStrategy,
                defaultColumnNameFetcher,
                backticksRemover,
                simpleValueBinder
        )
        OneToOneBinder oneToOneBinder = new OneToOneBinder(metadataBuildingContext, namingStrategy, simpleValueBinder)
        ManyToOneBinder manyToOneBinder = new ManyToOneBinder(metadataBuildingContext, namingStrategy, simpleValueBinder, new ManyToOneValuesBinder(), compositeIdentifierToManyToOneBinder, simpleValueColumnFetcher)

        CollectionBinder collectionBinder = new CollectionBinder(
                metadataBuildingContext,
                namingStrategy,
                jdbcEnvironment,
                simpleValueBinder,
                enumTypeBinderToUse,
                manyToOneBinder,
                compositeIdentifierToManyToOneBinder,
                simpleValueColumnFetcher,
                columnNameForPropertyAndPathFetcher,
                collectionHolder
        )
        PropertyFromValueCreator propertyFromValueCreator = new PropertyFromValueCreator()
        ComponentUpdater componentUpdater = new ComponentUpdater(propertyFromValueCreator)
        ComponentBinder componentBinder = new ComponentBinder(
                metadataBuildingContext,
                binder.getMappingCacheHolder(),
                enumTypeBinderToUse,
                collectionBinder,
                simpleValueBinder,
                oneToOneBinder,
                manyToOneBinder,
                columnNameForPropertyAndPathFetcher,
                componentUpdater
        )
        GrailsPropertyBinder propertyBinder = new GrailsPropertyBinder(
                metadataBuildingContext,
                namingStrategy,
                enumTypeBinderToUse,
                componentBinder,
                collectionBinder,
                simpleValueBinder,
                columnNameForPropertyAndPathFetcher,
                oneToOneBinder,
                manyToOneBinder,
                propertyFromValueCreator
        )
        componentBinder.setGrailsPropertyBinder(propertyBinder)
        CompositeIdBinder compositeIdBinder = new CompositeIdBinder(metadataBuildingContext, componentBinder, componentUpdater, propertyBinder);
        PropertyBinder propertyBinderHelper = new PropertyBinder()
        SimpleIdBinder simpleIdBinder = new SimpleIdBinder(metadataBuildingContext, namingStrategy, jdbcEnvironment, new BasicValueIdCreator(jdbcEnvironment), simpleValueBinder, propertyBinderHelper)
        IdentityBinder identityBinder = new IdentityBinder(simpleIdBinder, compositeIdBinder)
        VersionBinder versionBinder = new VersionBinder(metadataBuildingContext, simpleValueBinder, propertyBinderHelper, BasicValue::new)
        NaturalIdentifierBinder naturalIdentifierBinder = new NaturalIdentifierBinder()
        
        ClassBinder classBinder = new ClassBinder()
        ClassPropertiesBinder classPropertiesBinder = new ClassPropertiesBinder(propertyBinder, propertyFromValueCreator, naturalIdentifierBinder)
        MultiTenantFilterBinder multiTenantFilterBinder = new MultiTenantFilterBinder()
        JoinedSubClassBinder joinedSubClassBinder = new JoinedSubClassBinder(metadataBuildingContext, namingStrategy, new org.grails.orm.hibernate.cfg.domainbinding.binder.SimpleValueColumnBinder(), columnNameForPropertyAndPathFetcher, classBinder)
        UnionSubclassBinder unionSubclassBinder = new UnionSubclassBinder(metadataBuildingContext, namingStrategy, classBinder)
        SingleTableSubclassBinder singleTableSubclassBinder = new SingleTableSubclassBinder(classBinder)

        SubclassMappingBinder subclassMappingBinder = new SubclassMappingBinder(metadataBuildingContext, joinedSubClassBinder, unionSubclassBinder, singleTableSubclassBinder, classPropertiesBinder)
        SubClassBinder subClassBinder = new SubClassBinder(binder.getMappingCacheHolder(), subclassMappingBinder, multiTenantFilterBinder, defaultColumnNameFetcher, "dataSource")
        RootPersistentClassCommonValuesBinder rootPersistentClassCommonValuesBinder = new RootPersistentClassCommonValuesBinder(metadataBuildingContext, namingStrategy, identityBinder, versionBinder, classBinder, classPropertiesBinder)
        DiscriminatorPropertyBinder discriminatorPropertyBinder = new DiscriminatorPropertyBinder(metadataBuildingContext, new org.grails.orm.hibernate.cfg.domainbinding.binder.SimpleValueColumnBinder(), new ColumnConfigToColumnBinder())
        RootBinder rootBinder = new RootBinder(metadataBuildingContext, "default", namingStrategy, multiTenantFilterBinder, subClassBinder, defaultColumnNameFetcher, rootPersistentClassCommonValuesBinder, discriminatorPropertyBinder)

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
        def binders = getBinders(binder)
        binders.rootBinder.bindRoot(entity, mappings)
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
        def persistentEntity = createPersistentEntity(binder, "SimpleBook", [title: String], [:])
        def rootClass = new RootClass(binder.getMetadataBuildingContext())
        rootClass.setEntityName(persistentEntity.name)
        rootClass.setJpaEntityName(persistentEntity.name)
        rootClass.setTable(collector.addTable(null, null, "SIMPLE_BOOK", null, false, binder.getMetadataBuildingContext()))

        when:
        def titleProp = persistentEntity.getPropertyByName("title") as GrailsHibernatePersistentProperty
        Value value = propertyBinder.bindProperty(rootClass, rootClass.table, EMPTY_PATH, null, titleProp, collector)
        rootClass.addProperty(new PropertyFromValueCreator().createProperty(value, titleProp))

        then:
        Property prop = rootClass.getProperty("title")
        prop != null
        prop.value instanceof SimpleValue
        ((SimpleValue)prop.value).typeName == String.name
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

        def statusProp = Mock(TestBasic)
        setupProperty(statusProp, "status", new Mapping(), persistentEntity)
        statusProp.getType() >> java.util.concurrent.TimeUnit
        statusProp.isEnumType() >> true
        statusProp.isHibernateOneToOne() >> false
        statusProp.isHibernateManyToOne() >> false

        when:
        Value value = propertyBinder.bindProperty(rootClass, rootClass.table, EMPTY_PATH, null, statusProp, collector)
        rootClass.addProperty(new PropertyFromValueCreator().createProperty(value, statusProp))

        then:
        Property prop = rootClass.getProperty("status")
        prop != null
        prop.value instanceof SimpleValue
        // Enums use HibernateLegacyEnumType by default in Grails
        ((SimpleValue)prop.value).typeName == GrailsDomainBinder.ENUM_TYPE_CLASS
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
        def ownerProp = petEntity.getPropertyByName("owner") as GrailsHibernatePersistentProperty
        Value value = propertyBinder.bindProperty(rootClass, rootClass.table, EMPTY_PATH, null, ownerProp, collector)
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
        
        def persistentEntity = createPersistentEntity(binder, "Employee", [name: String, homeAddress: Address], [:], ["homeAddress"])
        def rootClass = new RootClass(binder.getMetadataBuildingContext())
        rootClass.setEntityName(persistentEntity.name)
        rootClass.setTable(collector.addTable(null, null, "EMPLOYEE", null, false, binder.getMetadataBuildingContext()))

        when:
        def addressProp = persistentEntity.getPropertyByName("homeAddress") as GrailsHibernatePersistentProperty
        Value value = propertyBinder.bindProperty(rootClass, rootClass.table, EMPTY_PATH, null, addressProp, collector)
        rootClass.addProperty(new PropertyFromValueCreator().createProperty(value, addressProp))

        then:
        Property prop = rootClass.getProperty("homeAddress")
        prop != null
        prop.value instanceof org.hibernate.mapping.Component
        def component = prop.value as org.hibernate.mapping.Component
        component.propertySpan == 2
        component.getProperty("city") != null
        component.getProperty("zip") != null
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

        when:
        def petsProp = personEntity.getPropertyByName("pets") as GrailsHibernatePersistentProperty
        Value value = propertyBinder.bindProperty(rootClass, rootClass.table, EMPTY_PATH, null, petsProp, collector)
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
        def propertyBinder = getBinders(binder).propertyBinder
        def collector = getCollector()

        def bookEntity = createPersistentEntity(ListBook)
        def authorEntity = createPersistentEntity(ListAuthor)

        // Register referenced entity in Hibernate
        bindRoot(binder, bookEntity, collector, "sessionFactory")

        // Manually create RootClass for the main entity to avoid duplicate property binding
        def rootClass = new RootClass(binder.getMetadataBuildingContext())
        rootClass.setEntityName(authorEntity.name)
        rootClass.setJpaEntityName(authorEntity.name)
        rootClass.setTable(collector.addTable(null, null, "LIST_AUTHOR", null, false, binder.getMetadataBuildingContext()))
        // Add a primary key to avoid NPE in alignColumns
        def pk = new org.hibernate.mapping.PrimaryKey(rootClass.table)
        def idCol = new org.hibernate.mapping.Column("id")
        rootClass.table.addColumn(idCol)
        pk.addColumn(idCol)
        rootClass.table.setPrimaryKey(pk)
        collector.addEntityBinding(rootClass)

        when:
        def booksProp = authorEntity.getPropertyByName("books") as GrailsHibernatePersistentProperty
        Value value = propertyBinder.bindProperty(rootClass, rootClass.table, EMPTY_PATH, null, booksProp, collector)
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
        def propertyBinder = getBinders(binder).propertyBinder
        def collector = getCollector()

        def bookEntity = createPersistentEntity(MapBook)
        def authorEntity = createPersistentEntity(MapAuthor)

        // Register referenced entity in Hibernate
        bindRoot(binder, bookEntity, collector, "sessionFactory")

        // Manually create RootClass for the main entity
        def rootClass = new RootClass(binder.getMetadataBuildingContext())
        rootClass.setEntityName(authorEntity.name)
        rootClass.setJpaEntityName(authorEntity.name)
        rootClass.setTable(collector.addTable(null, null, "MAP_AUTHOR", null, false, binder.getMetadataBuildingContext()))
        def pk = new org.hibernate.mapping.PrimaryKey(rootClass.table)
        def idCol = new org.hibernate.mapping.Column("id")
        rootClass.table.addColumn(idCol)
        pk.addColumn(idCol)
        rootClass.table.setPrimaryKey(pk)
        collector.addEntityBinding(rootClass)

        when:
        def booksProp = authorEntity.getPropertyByName("books") as GrailsHibernatePersistentProperty
        Value value = propertyBinder.bindProperty(rootClass, rootClass.table, EMPTY_PATH, null, booksProp, collector)
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

        // Create two entities: Author (with hasOne child) and Book (the child)
        def authorEntity = createPersistentEntity(AuthorWithOneToOne) as GrailsHibernatePersistentEntity
        def bookEntity = createPersistentEntity(BookForOneToOne) as GrailsHibernatePersistentEntity

        // Register referenced entity in Hibernate
        bindRoot(binder, bookEntity, collector, "sessionFactory")

        // Manually create RootClass for the main entity (AuthorWithOneToOne)
        def rootClass = new RootClass(binder.getMetadataBuildingContext())
        rootClass.setEntityName(authorEntity.name)
        rootClass.setJpaEntityName(authorEntity.name)
        rootClass.setTable(collector.addTable(null, null, "AUTHOR_ONE_TO_ONE", null, false, binder.getMetadataBuildingContext()))
        // Add a primary key to avoid NPE in alignColumns or other Hibernate internals
        def pk = new org.hibernate.mapping.PrimaryKey(rootClass.table)
        def idCol = new org.hibernate.mapping.Column("id")
        rootClass.table.addColumn(idCol)
        pk.addColumn(idCol)
        rootClass.table.setPrimaryKey(pk)
        collector.addEntityBinding(rootClass)

        when:
        def childBookProp = authorEntity.getPropertyByName("childBook") as GrailsHibernatePersistentProperty
        Value value = propertyBinder.bindProperty(rootClass, rootClass.table, EMPTY_PATH, null, childBookProp, collector)
        rootClass.addProperty(new PropertyFromValueCreator().createProperty(value, childBookProp))
        // Process second passes to ensure Hibernate's internal mappings are finalized
        collector.processSecondPasses(binder.getMetadataBuildingContext())

        then:
        Property prop = rootClass.getProperty("childBook")
        prop != null
        prop.value instanceof org.hibernate.mapping.OneToOne
        def oneToOne = prop.value as org.hibernate.mapping.OneToOne
        oneToOne.referencedEntityName == bookEntity.name
    }

    void "should use binders from protected constructor"() {
        given:
        def metadataBuildingContext = Mock(org.hibernate.boot.spi.MetadataBuildingContext)
        def namingStrategy = Mock(org.grails.orm.hibernate.cfg.PersistentEntityNamingStrategy)
        // CollectionHolder is a Java record (final), so we instantiate it
        def collectionHolder = new org.grails.orm.hibernate.cfg.domainbinding.collectionType.CollectionHolder(new HashMap<Class<?>, org.grails.orm.hibernate.cfg.domainbinding.collectionType.CollectionType>())
        def enumTypeBinder = Mock(EnumTypeBinder)
        def componentBinder = Mock(ComponentBinder)
        def collectionBinder = Mock(CollectionBinder)
        def propertyFromValueCreator = Mock(PropertyFromValueCreator)
        def simpleValueBinder = Mock(SimpleValueBinder)
        def columnNameForPropertyAndPathFetcher = Mock(ColumnNameForPropertyAndPathFetcher)
        def oneToOneBinder = Mock(OneToOneBinder)
        def manyToOneBinder = Mock(ManyToOneBinder)

        // Instantiate GrailsPropertyBinder using the protected constructor with necessary mocks
        def propertyBinder = new GrailsPropertyBinder(
                metadataBuildingContext,
                namingStrategy,
                enumTypeBinder,
                componentBinder,
                collectionBinder,
                simpleValueBinder,
                columnNameForPropertyAndPathFetcher,
                oneToOneBinder,
                manyToOneBinder,
                propertyFromValueCreator
        )

        def mappings = Mock(org.hibernate.boot.spi.InFlightMetadataCollector)
        metadataBuildingContext.getMetadataCollector() >> mappings

        def rootClass = new RootClass(metadataBuildingContext)
        def currentGrailsProp = Mock(GrailsHibernatePersistentProperty)
        def table = new org.hibernate.mapping.Table("TEST_TABLE")
        rootClass.setTable(table)

        // Mocking currentGrailsProp and its dependencies to prevent NPEs
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
        def resultValue = propertyBinder.bindProperty(rootClass, table, EMPTY_PATH, null, currentGrailsProp, mappings)

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

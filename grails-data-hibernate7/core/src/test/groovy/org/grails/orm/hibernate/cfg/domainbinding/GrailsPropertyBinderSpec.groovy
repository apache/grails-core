package org.grails.orm.hibernate.cfg.domainbinding

import grails.gorm.annotation.Entity
import grails.gorm.specs.HibernateGormDatastoreSpec
import org.grails.orm.hibernate.cfg.GrailsHibernatePersistentEntity
import org.grails.orm.hibernate.cfg.GrailsHibernatePersistentProperty
import org.grails.orm.hibernate.cfg.GrailsDomainBinder
import org.hibernate.mapping.ManyToOne
import org.hibernate.mapping.Property
import org.hibernate.mapping.RootClass
import org.hibernate.mapping.SimpleValue
import org.grails.orm.hibernate.cfg.PropertyConfig
import org.grails.orm.hibernate.cfg.ColumnConfig

import org.hibernate.mapping.Value

class GrailsPropertyBinderSpec extends HibernateGormDatastoreSpec {

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
        def propertyBinder = binder.getGrailsPropertyBinder()
        def persistentEntity = createPersistentEntity(binder, "SimpleBook", [title: String], [:])
        def rootClass = new RootClass(binder.getMetadataBuildingContext())
        rootClass.setEntityName(persistentEntity.name)
        rootClass.setJpaEntityName(persistentEntity.name)
        rootClass.setTable(collector.addTable(null, null, "SIMPLE_BOOK", null, false, binder.getMetadataBuildingContext()))

        when:
        def titleProp = persistentEntity.getPropertyByName("title") as GrailsHibernatePersistentProperty
        Value value = propertyBinder.bindProperty(rootClass, titleProp, collector, "sessionFactory")
        rootClass.addProperty(binder.getPropertyFromValueCreator().createProperty(value, titleProp))

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
        def propertyBinder = binder.getGrailsPropertyBinder()
        def persistentEntity = createPersistentEntity(binder, "EnumBook", [status: java.util.concurrent.TimeUnit], [:])
        def rootClass = new RootClass(binder.getMetadataBuildingContext())
        rootClass.setEntityName(persistentEntity.name)
        rootClass.setTable(collector.addTable(null, null, "ENUM_BOOK", null, false, binder.getMetadataBuildingContext()))

        when:
        def statusProp = persistentEntity.getPropertyByName("status") as GrailsHibernatePersistentProperty
        Value value = propertyBinder.bindProperty(rootClass, statusProp, collector, "sessionFactory")
        rootClass.addProperty(binder.getPropertyFromValueCreator().createProperty(value, statusProp))

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
        def propertyBinder = binder.getGrailsPropertyBinder()
        def collector = getCollector()

        def petEntity = getPersistentEntity(org.apache.grails.data.testing.tck.domains.Pet) as GrailsHibernatePersistentEntity
        def personEntity = getPersistentEntity(org.apache.grails.data.testing.tck.domains.Person) as GrailsHibernatePersistentEntity

        def rootClass = new RootClass(binder.getMetadataBuildingContext())
        rootClass.setEntityName(petEntity.name)
        rootClass.setTable(collector.addTable(null, null, "PET", null, false, binder.getMetadataBuildingContext()))

        when:
        def ownerProp = petEntity.getPropertyByName("owner") as GrailsHibernatePersistentProperty
        Value value = propertyBinder.bindProperty(rootClass, ownerProp, collector, "sessionFactory")
        rootClass.addProperty(binder.getPropertyFromValueCreator().createProperty(value, ownerProp))

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
        def propertyBinder = binder.getGrailsPropertyBinder()
        
        def persistentEntity = createPersistentEntity(binder, "Employee", [name: String, homeAddress: Address], [:], ["homeAddress"])
        def rootClass = new RootClass(binder.getMetadataBuildingContext())
        rootClass.setEntityName(persistentEntity.name)
        rootClass.setTable(collector.addTable(null, null, "EMPLOYEE", null, false, binder.getMetadataBuildingContext()))

        when:
        def addressProp = persistentEntity.getPropertyByName("homeAddress") as GrailsHibernatePersistentProperty
        Value value = propertyBinder.bindProperty(rootClass, addressProp, collector, "sessionFactory")
        rootClass.addProperty(binder.getPropertyFromValueCreator().createProperty(value, addressProp))

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
        def propertyBinder = binder.getGrailsPropertyBinder()
        def collector = getCollector()

        def personEntity = getPersistentEntity(org.apache.grails.data.testing.tck.domains.Person) as GrailsHibernatePersistentEntity
        def petEntity = getPersistentEntity(org.apache.grails.data.testing.tck.domains.Pet) as GrailsHibernatePersistentEntity

        def rootClass = new RootClass(binder.getMetadataBuildingContext())
        rootClass.setEntityName(personEntity.name)
        rootClass.setTable(collector.addTable(null, null, "PERSON", null, false, binder.getMetadataBuildingContext()))

        when:
        def petsProp = personEntity.getPropertyByName("pets") as GrailsHibernatePersistentProperty
        Value value = propertyBinder.bindProperty(rootClass, petsProp, collector, "sessionFactory")
        rootClass.addProperty(binder.getPropertyFromValueCreator().createProperty(value, petsProp))

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
        def propertyBinder = binder.getGrailsPropertyBinder()
        def collector = getCollector()

        def bookEntity = createPersistentEntity(ListBook)
        def authorEntity = createPersistentEntity(ListAuthor)

        // Register referenced entity in Hibernate
        binder.bindRoot(bookEntity, collector, "sessionFactory")

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
        Value value = propertyBinder.bindProperty(rootClass, booksProp, collector, "sessionFactory")
        rootClass.addProperty(binder.getPropertyFromValueCreator().createProperty(value, booksProp))
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
        def propertyBinder = binder.getGrailsPropertyBinder()
        def collector = getCollector()

        def bookEntity = createPersistentEntity(MapBook)
        def authorEntity = createPersistentEntity(MapAuthor)

        // Register referenced entity in Hibernate
        binder.bindRoot(bookEntity, collector, "sessionFactory")

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
        Value value = propertyBinder.bindProperty(rootClass, booksProp, collector, "sessionFactory")
        rootClass.addProperty(binder.getPropertyFromValueCreator().createProperty(value, booksProp))
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
        binder.bindRoot(personEntity, collector, "sessionFactory")
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
        def propertyBinder = binder.getGrailsPropertyBinder()
        def collector = getCollector()

        // Create two entities: Author (with hasOne child) and Book (the child)
        def authorEntity = createPersistentEntity(AuthorWithOneToOne) as GrailsHibernatePersistentEntity
        def bookEntity = createPersistentEntity(BookForOneToOne) as GrailsHibernatePersistentEntity

        // Register referenced entity in Hibernate
        binder.bindRoot(bookEntity, collector, "sessionFactory")

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
        Value value = propertyBinder.bindProperty(rootClass, childBookProp, collector, "sessionFactory")
        rootClass.addProperty(binder.getPropertyFromValueCreator().createProperty(value, childBookProp))
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
        def componentPropertyBinder = Mock(ComponentPropertyBinder)
        def collectionBinder = Mock(CollectionBinder)
        def propertyFromValueCreator = Mock(PropertyFromValueCreator) // Mock propertyFromValueCreator as it is a constructor parameter

        // Instantiate GrailsPropertyBinder using the protected constructor with necessary mocks
        def propertyBinder = new GrailsPropertyBinder(
                metadataBuildingContext,
                namingStrategy,
                collectionHolder,
                enumTypeBinder,
                componentPropertyBinder,
                collectionBinder,
                propertyFromValueCreator
        )

        def mappings = Mock(org.hibernate.boot.spi.InFlightMetadataCollector)
        metadataBuildingContext.getMetadataCollector() >> mappings

        def rootClass = new RootClass(metadataBuildingContext)
        def currentGrailsProp = Mock(GrailsHibernatePersistentProperty)
        def table = new org.hibernate.mapping.Table("TEST_TABLE")
        rootClass.setTable(table)

        // Mocking currentGrailsProp and its dependencies to prevent NPEs
        def mockOwner = Mock(org.grails.orm.hibernate.cfg.GrailsHibernatePersistentEntity)
        def mockMapping = new org.grails.orm.hibernate.cfg.Mapping()
        mockMapping.setComment("test comment") // Provide a comment
        currentGrailsProp.getHibernateOwner() >> mockOwner
        mockOwner.getMappedForm() >> mockMapping // Return the Mapping object

        // Stubbing getOwner() to return mockOwner
        currentGrailsProp.getOwner() >> mockOwner
        mockOwner.isRoot() >> true // Stub isRoot() to prevent NPE in ColumnBinder

        // Mock PropertyConfig and stub its methods for SimpleValueBinder
        def propertyConfigMock = Mock(PropertyConfig)
        currentGrailsProp.getMappedForm() >> propertyConfigMock // For SimpleValueBinder logic, need PropertyConfig

        propertyConfigMock.getGenerator() >> null
        propertyConfigMock.getTypeParams() >> new Properties()
        propertyConfigMock.isDerived() >> false
        def columnConfigMock = Mock(ColumnConfig) // Mock ColumnConfig for getColumns()
        columnConfigMock.getName() >> "test_column" // Provide a column name to prevent NPE
        propertyConfigMock.getColumns() >> Arrays.asList(columnConfigMock)

        // Mocking other necessary properties of currentGrailsProp
        currentGrailsProp.getType() >> String.class
        currentGrailsProp.getName() >> "title"

        when:
        // Capture the return value of bindProperty
        def resultValue = propertyBinder.bindProperty(rootClass, currentGrailsProp, mappings, "sessionFactory")

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
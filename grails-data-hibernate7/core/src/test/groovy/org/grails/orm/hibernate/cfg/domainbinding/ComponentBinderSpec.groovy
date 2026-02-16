package org.grails.orm.hibernate.cfg.domainbinding

import grails.gorm.specs.HibernateGormDatastoreSpec
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.model.types.Association
import org.grails.orm.hibernate.cfg.GrailsHibernatePersistentEntity
import org.grails.orm.hibernate.cfg.GrailsHibernatePersistentProperty
import org.grails.orm.hibernate.cfg.Mapping
import org.grails.orm.hibernate.cfg.MappingCacheHolder
import org.grails.orm.hibernate.cfg.PersistentEntityNamingStrategy
import org.grails.orm.hibernate.cfg.PropertyConfig
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateBasicProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateEmbeddedProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateManyToOneProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateOneToOneProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateSimpleProperty
import org.grails.orm.hibernate.cfg.domainbinding.binder.CollectionBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.ComponentBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.ComponentUpdater
import org.grails.orm.hibernate.cfg.domainbinding.binder.EnumTypeBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.OneToOneBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.ManyToOneBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.SimpleValueBinder
import org.grails.orm.hibernate.cfg.domainbinding.util.ColumnNameForPropertyAndPathFetcher
import org.grails.orm.hibernate.cfg.domainbinding.collectionType.CollectionHolder
import org.hibernate.boot.spi.InFlightMetadataCollector
import org.hibernate.boot.spi.MetadataBuildingContext
import org.hibernate.mapping.BasicValue
import org.hibernate.mapping.Component
import org.hibernate.mapping.ManyToOne as HibernateManyToOne
import org.hibernate.mapping.OneToOne as HibernateOneToOne
import org.hibernate.mapping.Property
import org.hibernate.mapping.RootClass
import org.hibernate.mapping.Table
import org.hibernate.mapping.Value
import spock.lang.Subject

import org.grails.orm.hibernate.cfg.domainbinding.util.PropertyFromValueCreator

class ComponentBinderSpec extends HibernateGormDatastoreSpec {

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

    abstract static class TestOneToMany extends org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateOneToManyProperty {
        TestOneToMany(PersistentEntity owner, MappingContext context, java.beans.PropertyDescriptor descriptor) {
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

    MappingCacheHolder mappingCacheHolder = Mock(MappingCacheHolder)
    CollectionHolder collectionHolder
    EnumTypeBinder enumTypeBinder = Mock(EnumTypeBinder)
    CollectionBinder collectionBinder = Mock(CollectionBinder)
    PropertyFromValueCreator propertyFromValueCreator = Mock(PropertyFromValueCreator)
    OneToOneBinder oneToOneBinder = Mock(OneToOneBinder)
    ManyToOneBinder manyToOneBinder = Mock(ManyToOneBinder)
    ColumnNameForPropertyAndPathFetcher columnNameFetcher = Mock(ColumnNameForPropertyAndPathFetcher)
    ComponentUpdater componentUpdater = Mock(ComponentUpdater)
    SimpleValueBinder mockSimpleValueBinder = Mock(SimpleValueBinder)

    @Subject
    ComponentBinder binder

    def setup() {
        def metadataBuildingContext = getGrailsDomainBinder().getMetadataBuildingContext()
        mockSimpleValueBinder = Mock(SimpleValueBinder)
        binder = new ComponentBinder(
                metadataBuildingContext,
                mappingCacheHolder,
                enumTypeBinder,
                collectionBinder,
                mockSimpleValueBinder,
                oneToOneBinder,
                manyToOneBinder,
                columnNameFetcher,
                componentUpdater
        )
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

    def "should bind component and its properties"() {
        given:
        def metadataBuildingContext = getGrailsDomainBinder().getMetadataBuildingContext()
        def root = new RootClass(metadataBuildingContext)
        root.setEntityName("MyEntity")
        root.setTable(new Table("my_entity"))
        
        def embeddedProp = Mock(TestEmbedded)
        def associatedEntity = GroovyMock(GrailsHibernatePersistentEntity)
        
        embeddedProp.getType() >> Address
        embeddedProp.getName() >> "address"
        embeddedProp.getAssociatedEntity() >> associatedEntity
        embeddedProp.getOwner() >> Mock(GrailsHibernatePersistentEntity) {
            getJavaClass() >> MyEntity
        }
        embeddedProp.isHibernateOneToOne() >> false
        embeddedProp.isHibernateManyToOne() >> false

        associatedEntity.getName() >> "Address"
        def prop1 = Mock(TestSimple)
        prop1.getName() >> "street"
        prop1.getType() >> String
        prop1.isHibernateOneToOne() >> false
        prop1.isHibernateManyToOne() >> false
        associatedEntity.getHibernatePersistentProperties() >> [prop1]
        associatedEntity.getIdentity() >> null

        def mappings = metadataBuildingContext.getMetadataCollector()

        when:
        def component = binder.bindComponent(root, embeddedProp, mappings)

        then:
        component.getComponentClassName() == Address.name
        component.getRoleName() == Address.name + ".address"
        1 * mappingCacheHolder.cacheMapping(associatedEntity)
        1 * mockSimpleValueBinder.bindSimpleValue(prop1, embeddedProp, _ as Table, "address") >> new BasicValue(metadataBuildingContext, root.getTable())
        1 * componentUpdater.updateComponent(_ as Component, embeddedProp, prop1, _ as Value)
    }

    def "should skip identity and version properties"() {
        given:
        def metadataBuildingContext = getGrailsDomainBinder().getMetadataBuildingContext()
        def root = new RootClass(metadataBuildingContext)
        root.setEntityName("MyEntity")
        root.setTable(new Table("my_entity"))

        def embeddedProp = Mock(TestEmbedded)
        def associatedEntity = GroovyMock(GrailsHibernatePersistentEntity)

        embeddedProp.getType() >> Address
        embeddedProp.getName() >> "address"
        embeddedProp.getAssociatedEntity() >> associatedEntity
        embeddedProp.getOwner() >> Mock(GrailsHibernatePersistentEntity) {
            getJavaClass() >> MyEntity
        }
        embeddedProp.isHibernateOneToOne() >> false
        embeddedProp.isHibernateManyToOne() >> false

        associatedEntity.getName() >> "Address"
        def idProp = Mock(org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateIdentityProperty)
        idProp.getName() >> "id"
        def versionProp = Mock(TestSimple)
        versionProp.getName() >> "version"
        def normalProp = Mock(TestSimple)
        normalProp.getName() >> "street"
        normalProp.getType() >> String
        normalProp.isHibernateOneToOne() >> false
        normalProp.isHibernateManyToOne() >> false

        associatedEntity.getIdentity() >> idProp
        associatedEntity.getHibernatePersistentProperties() >> [idProp, versionProp, normalProp]

        def mappings = metadataBuildingContext.getMetadataCollector()

        when:
        binder.bindComponent(root, embeddedProp, mappings)

        then:
        0 * componentUpdater.updateComponent(_, _, idProp, _)
        0 * componentUpdater.updateComponent(_, _, versionProp, _)
        1 * mockSimpleValueBinder.bindSimpleValue(normalProp, embeddedProp, _ as Table, "address") >> new BasicValue(metadataBuildingContext, root.getTable())
        1 * componentUpdater.updateComponent(_, _, normalProp, _)
    }

    def "should set parent property when component has reference back to owner"() {
        given:
        def metadataBuildingContext = getGrailsDomainBinder().getMetadataBuildingContext()
        def root = new RootClass(metadataBuildingContext)
        root.setEntityName("MyEntity")
        root.setTable(new Table("my_entity"))

        def embeddedProp = Mock(TestEmbedded)
        def associatedEntity = GroovyMock(GrailsHibernatePersistentEntity)

        embeddedProp.getType() >> Address
        embeddedProp.getName() >> "address"
        embeddedProp.getAssociatedEntity() >> associatedEntity
        embeddedProp.getOwner() >> Mock(GrailsHibernatePersistentEntity) {
            getJavaClass() >> MyEntity
        }
        embeddedProp.isHibernateOneToOne() >> false
        embeddedProp.isHibernateManyToOne() >> false

        associatedEntity.getName() >> "Address"
        def parentProp = Mock(TestSimple)
        parentProp.getName() >> "myEntity"
        parentProp.getType() >> MyEntity

        associatedEntity.getIdentity() >> null
        associatedEntity.getHibernatePersistentProperties() >> [parentProp]

        def mappings = metadataBuildingContext.getMetadataCollector()

        when:
        def component = binder.bindComponent(root, embeddedProp, mappings)

        then:
        component.getParentProperty() == "myEntity"
        0 * mockSimpleValueBinder.bindSimpleValue(parentProp, _, _, _)
        0 * componentUpdater.updateComponent(_, _, parentProp, _)
    }

    def "should bind simple property"() {
        given:
        def metadataBuildingContext = getGrailsDomainBinder().getMetadataBuildingContext()
        def root = new RootClass(metadataBuildingContext)
        def table = new Table("my_table")
        def ownerEntity = Mock(GrailsHibernatePersistentEntity)
        ownerEntity.isRoot() >> true

        def currentGrailsProp = Mock(TestSimple)
        
        def componentProperty = Mock(GrailsHibernatePersistentProperty)
        def mappings = Mock(InFlightMetadataCollector)
        def hibernateProperty = new Property()
        hibernateProperty.setName("street")
        
        def mapping = new Mapping()
        ownerEntity.getMappedForm() >> mapping
        currentGrailsProp.getType() >> String
        currentGrailsProp.isHibernateOneToOne() >> false
        currentGrailsProp.isHibernateManyToOne() >> false
        setupProperty(currentGrailsProp, "street", mapping, ownerEntity)
        setupProperty(componentProperty, "address", mapping, ownerEntity)
        
        when:
        binder.bindComponentProperty(componentProperty, currentGrailsProp, root, "address", table, mappings)

        then:
        1 * mockSimpleValueBinder.bindSimpleValue(currentGrailsProp, componentProperty, table, "address") >> new BasicValue(metadataBuildingContext, table)
        0 * componentUpdater.updateComponent(_, _, _, _)
    }

    def "should bind many-to-one property"() {
        given:
        def metadataBuildingContext = getGrailsDomainBinder().getMetadataBuildingContext()
        def root = new RootClass(metadataBuildingContext)
        def table = new Table("my_table")
        def ownerEntity = Mock(GrailsHibernatePersistentEntity)
        ownerEntity.isRoot() >> true
        def currentGrailsProp = Mock(TestManyToOne)
        def componentProperty = Mock(GrailsHibernatePersistentProperty)
        def mappings = Mock(InFlightMetadataCollector)
        def hibernateProperty = new Property()
        hibernateProperty.setName("owner")
        def hibernateManyToOne = new HibernateManyToOne(metadataBuildingContext, table)

        def mapping = new Mapping()
        ownerEntity.getMappedForm() >> mapping
        currentGrailsProp.getAssociatedEntity() >> Mock(GrailsHibernatePersistentEntity) { 
            getName() >> "Owner" 
            getMappedForm() >> new Mapping()
            isRoot() >> true
        }
        currentGrailsProp.getType() >> Object
        currentGrailsProp.isHibernateOneToOne() >> false
        currentGrailsProp.isHibernateManyToOne() >> true
        setupProperty(currentGrailsProp, "owner", mapping, ownerEntity)
        setupProperty(componentProperty, "address", mapping, ownerEntity)
        
        when:
        binder.bindComponentProperty(componentProperty, currentGrailsProp, root, "address", table, mappings)

        then:
        1 * manyToOneBinder.bindManyToOne(currentGrailsProp, table, "address") >> hibernateManyToOne
        0 * componentUpdater.updateComponent(_, _, _, _)
    }

    def "should bind one-to-one property"() {
        given:
        def metadataBuildingContext = getGrailsDomainBinder().getMetadataBuildingContext()
        def root = new RootClass(metadataBuildingContext)
        def table = new Table("my_table")
        def ownerEntity = Mock(GrailsHibernatePersistentEntity)
        ownerEntity.isRoot() >> true
        def currentGrailsProp = Mock(TestOneToOne)
        def componentProperty = Mock(GrailsHibernatePersistentProperty)
        def mappings = Mock(InFlightMetadataCollector)
        def hibernateProperty = new Property()
        hibernateProperty.setName("detail")

        def mapping = new Mapping()
        ownerEntity.getMappedForm() >> mapping
        ((Association)currentGrailsProp).getInverseSide() >> Mock(Association) {
            isHasOne() >> false
            getOwner() >> Mock(GrailsHibernatePersistentEntity) { 
                getName() >> "Other" 
                isRoot() >> true
            }
            getName() >> "other"
        }
        currentGrailsProp.getType() >> Object
        ((Association)currentGrailsProp).canBindOneToOneWithSingleColumnAndForeignKey() >> true
        currentGrailsProp.isHibernateOneToOne() >> true
        setupProperty(currentGrailsProp, "detail", mapping, ownerEntity)
        setupProperty(componentProperty, "address", mapping, ownerEntity)
        def hibernateOneToOne = new HibernateOneToOne(metadataBuildingContext, table, root)
        
        when:
        binder.bindComponentProperty(componentProperty, currentGrailsProp, root, "address", table, mappings)

        then:
        1 * oneToOneBinder.bindOneToOne(currentGrailsProp, root, table, "address") >> hibernateOneToOne
        0 * componentUpdater.updateComponent(_, _, _, _)
    }

    def "should bind enum property"() {
        given:
        def metadataBuildingContext = getGrailsDomainBinder().getMetadataBuildingContext()
        def root = new RootClass(metadataBuildingContext)
        def table = new Table("my_table")
        def ownerEntity = Mock(GrailsHibernatePersistentEntity)
        ownerEntity.isRoot() >> true
        def currentGrailsProp = Mock(TestBasic)
        def componentProperty = Mock(GrailsHibernatePersistentProperty)
        def mappings = Mock(InFlightMetadataCollector)
        def hibernateProperty = new Property()
        hibernateProperty.setName("type")

        def mapping = new Mapping()
        ownerEntity.getMappedForm() >> mapping
        currentGrailsProp.getType() >> MyEnum
        currentGrailsProp.isEnumType() >> true
        currentGrailsProp.isHibernateOneToOne() >> false
        currentGrailsProp.isHibernateManyToOne() >> false
        setupProperty(currentGrailsProp, "type", mapping, ownerEntity)
        setupProperty(componentProperty, "address", mapping, ownerEntity)
        
        columnNameFetcher.getColumnNameForPropertyAndPath(currentGrailsProp, "address", null) >> "address_type_col"

        when:
        binder.bindComponentProperty(componentProperty, currentGrailsProp, root, "address", table, mappings)

        then:
        1 * enumTypeBinder.bindEnumType(currentGrailsProp, MyEnum, table, "address") >> new BasicValue(metadataBuildingContext, table)
        0 * componentUpdater.updateComponent(_, _, _, _)
    }

    def "should set columns to nullable when component property is nullable"() {
        given:
        def metadataBuildingContext = getGrailsDomainBinder().getMetadataBuildingContext()
        def root = new RootClass(metadataBuildingContext)
        def table = new Table("my_table")
        def ownerEntity = Mock(GrailsHibernatePersistentEntity)
        ownerEntity.isRoot() >> true
        def currentGrailsProp = Mock(TestSimple)
        def componentProperty = Mock(GrailsHibernatePersistentProperty)
        def mappings = Mock(InFlightMetadataCollector)
        def hibernateProperty = new Property()
        hibernateProperty.setName("street")
        
        def mapping = new Mapping()
        ownerEntity.getMappedForm() >> mapping
        currentGrailsProp.getType() >> String
        currentGrailsProp.isHibernateOneToOne() >> false
        currentGrailsProp.isHibernateManyToOne() >> false
        setupProperty(currentGrailsProp, "street", mapping, ownerEntity)
        setupProperty(componentProperty, "address", mapping, ownerEntity)
        
        ownerEntity.isComponentPropertyNullable(componentProperty) >> true
        
        when:
        binder.bindComponentProperty(componentProperty, currentGrailsProp, root, "address", table, mappings)

        then:
        1 * mockSimpleValueBinder.bindSimpleValue(
            currentGrailsProp, 
            componentProperty, 
            table, 
            "address"
        ) >> new BasicValue(metadataBuildingContext, table)
        0 * componentUpdater.updateComponent(_, _, _, _)
    }

    def "should bind collection property within component"() {
        given:
        def metadataBuildingContext = getGrailsDomainBinder().getMetadataBuildingContext()
        def root = new RootClass(metadataBuildingContext)
        def table = new Table("my_table")
        def currentGrailsProp = Mock(org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateToManyProperty)
        def componentProperty = Mock(GrailsHibernatePersistentProperty)
        def mappings = Mock(InFlightMetadataCollector)
        
        currentGrailsProp.getType() >> Set
        currentGrailsProp.isHibernateOneToOne() >> false
        currentGrailsProp.isHibernateManyToOne() >> false

        when:
        def result = binder.bindComponentProperty(componentProperty, currentGrailsProp, root, "address", table, mappings)

        then:
        1 * collectionBinder.bindCollection(currentGrailsProp, root, mappings, "address") >> Mock(org.hibernate.mapping.Set)
    }

    def "should bind nested component recursively"() {
        given:
        def metadataBuildingContext = getGrailsDomainBinder().getMetadataBuildingContext()
        def root = new RootClass(metadataBuildingContext)
        root.setTable(new Table("my_table"))
        def table = root.getTable()
        def mappings = Mock(InFlightMetadataCollector)
        
        def nestedEmbeddedProp = Mock(TestEmbedded)
        def nestedAssociatedEntity = Mock(GrailsHibernatePersistentEntity)
        def parentProp = Mock(GrailsHibernatePersistentProperty)
        
        nestedEmbeddedProp.getType() >> Address
        nestedEmbeddedProp.getName() >> "nestedAddress"
        nestedEmbeddedProp.getAssociatedEntity() >> nestedAssociatedEntity
        nestedEmbeddedProp.getOwner() >> Mock(GrailsHibernatePersistentEntity) {
            getJavaClass() >> Address
        }
        nestedEmbeddedProp.isHibernateOneToOne() >> false
        nestedEmbeddedProp.isHibernateManyToOne() >> false
        
        nestedAssociatedEntity.getName() >> "NestedAddress"
        def nestedProp1 = Mock(TestSimple)
        nestedProp1.getName() >> "street"
        nestedProp1.getType() >> String
        nestedProp1.isHibernateOneToOne() >> false
        nestedProp1.isHibernateManyToOne() >> false
        nestedAssociatedEntity.getHibernatePersistentProperties() >> [nestedProp1]
        nestedAssociatedEntity.getIdentity() >> null

        when:
        def result = binder.bindComponentProperty(parentProp, nestedEmbeddedProp, root, "address.nested", table, mappings)

        then:
        result instanceof Component
        result.getComponentClassName() == Address.name
        1 * mappingCacheHolder.cacheMapping(nestedAssociatedEntity)
        1 * mockSimpleValueBinder.bindSimpleValue(nestedProp1, nestedEmbeddedProp, _ as Table, "nestedAddress") >> new BasicValue(metadataBuildingContext, table)
        1 * componentUpdater.updateComponent(_ as Component, nestedEmbeddedProp, nestedProp1, _ as Value)
    }

    def "should bind one-to-one as many-to-one when it cannot be bound with single column"() {
        given:
        def metadataBuildingContext = getGrailsDomainBinder().getMetadataBuildingContext()
        def root = new RootClass(metadataBuildingContext)
        def table = new Table("my_table")
        def currentGrailsProp = Mock(TestOneToOne)
        def componentProperty = Mock(GrailsHibernatePersistentProperty)
        def mappings = Mock(InFlightMetadataCollector)
        
        currentGrailsProp.canBindOneToOneWithSingleColumnAndForeignKey() >> false
        currentGrailsProp.getType() >> Object
        currentGrailsProp.isHibernateOneToOne() >> false
        currentGrailsProp.isHibernateManyToOne() >> true
        def hibernateManyToOne = new HibernateManyToOne(metadataBuildingContext, table)

        when:
        def result = binder.bindComponentProperty(componentProperty, currentGrailsProp, root, "address", table, mappings)

        then:
        1 * manyToOneBinder.bindManyToOne(currentGrailsProp, table, "address") >> hibernateManyToOne
        result == hibernateManyToOne
    }

    static class MyEntity {}
    static class Address {}
    enum MyEnum { VAL }
}

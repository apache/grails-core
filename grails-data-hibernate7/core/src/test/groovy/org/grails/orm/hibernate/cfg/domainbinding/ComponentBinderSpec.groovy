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
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateEmbeddedProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateManyToOneProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateOneToOneProperty
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

    MappingCacheHolder mappingCacheHolder = Mock(MappingCacheHolder)
    CollectionHolder collectionHolder = new CollectionHolder([:])
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
        binder = new ComponentBinder(
                getGrailsDomainBinder().getMetadataBuildingContext(),
                mappingCacheHolder,
                collectionHolder,
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
        
        def embeddedProp = GroovyMock(HibernateEmbeddedProperty)
        def associatedEntity = GroovyMock(GrailsHibernatePersistentEntity)
        
        embeddedProp.getType() >> Address
        embeddedProp.getName() >> "address"
        embeddedProp.getAssociatedEntity() >> associatedEntity
        embeddedProp.getOwner() >> Mock(GrailsHibernatePersistentEntity) {
            getJavaClass() >> MyEntity
        }

        associatedEntity.getName() >> "Address"
        def prop1 = Mock(GrailsHibernatePersistentProperty)
        prop1.getName() >> "street"
        prop1.getType() >> String
        associatedEntity.getHibernatePersistentProperties() >> [prop1]
        associatedEntity.getIdentity() >> null

        def mappings = metadataBuildingContext.getMetadataCollector()

        when:
        def component = binder.bindComponent(root, embeddedProp, mappings)

        then:
        component.getComponentClassName() == Address.name
        component.getRoleName() == Address.name + ".address"
        1 * mappingCacheHolder.cacheMapping(associatedEntity)
        1 * mockSimpleValueBinder.bindSimpleValue(prop1, embeddedProp, _ as BasicValue, "address")
        1 * componentUpdater.updateComponent(_ as Component, embeddedProp, prop1, _ as Value)
    }

    def "should bind simple property"() {
        given:
        def metadataBuildingContext = getGrailsDomainBinder().getMetadataBuildingContext()
        def root = new RootClass(metadataBuildingContext)
        def component = new Component(metadataBuildingContext, root)
        def table = new Table("my_table")
        def ownerEntity = Mock(GrailsHibernatePersistentEntity)
        ownerEntity.isRoot() >> true

        def currentGrailsProp = Mock(GrailsHibernatePersistentProperty)
        
        def componentProperty = Mock(GrailsHibernatePersistentProperty)
        def mappings = Mock(InFlightMetadataCollector)
        def hibernateProperty = new Property()
        hibernateProperty.setName("street")
        
        def mapping = new Mapping()
        ownerEntity.getMappedForm() >> mapping
        currentGrailsProp.getType() >> String
        setupProperty(currentGrailsProp, "street", mapping, ownerEntity)
        setupProperty(componentProperty, "address", mapping, ownerEntity)
        
        when:
        binder.bindComponentProperty(component, componentProperty, currentGrailsProp, root, "address", table, mappings)

        then:
        1 * mockSimpleValueBinder.bindSimpleValue(currentGrailsProp, componentProperty, _ as BasicValue, "address")
        0 * componentUpdater.updateComponent(_, _, _, _)
    }

    def "should bind many-to-one property"() {
        given:
        def metadataBuildingContext = getGrailsDomainBinder().getMetadataBuildingContext()
        def root = new RootClass(metadataBuildingContext)
        def component = new Component(metadataBuildingContext, root)
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
        setupProperty(currentGrailsProp, "owner", mapping, ownerEntity)
        setupProperty(componentProperty, "address", mapping, ownerEntity)
        
        when:
        binder.bindComponentProperty(component, componentProperty, currentGrailsProp, root, "address", table, mappings)

        then:
        1 * manyToOneBinder.bindManyToOne(currentGrailsProp, table, "address") >> hibernateManyToOne
        0 * componentUpdater.updateComponent(_, _, _, _)
    }

    def "should bind one-to-one property"() {
        given:
        def metadataBuildingContext = getGrailsDomainBinder().getMetadataBuildingContext()
        def root = new RootClass(metadataBuildingContext)
        def component = new Component(metadataBuildingContext, root)
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
        setupProperty(currentGrailsProp, "detail", mapping, ownerEntity)
        setupProperty(componentProperty, "address", mapping, ownerEntity)
        def hibernateOneToOne = new HibernateOneToOne(metadataBuildingContext, table, root)
        
        when:
        binder.bindComponentProperty(component, componentProperty, currentGrailsProp, root, "address", table, mappings)

        then:
        1 * oneToOneBinder.bindOneToOne(currentGrailsProp, root, table, "address") >> hibernateOneToOne
        0 * componentUpdater.updateComponent(_, _, _, _)
    }

    def "should bind enum property"() {
        given:
        def metadataBuildingContext = getGrailsDomainBinder().getMetadataBuildingContext()
        def root = new RootClass(metadataBuildingContext)
        def component = new Component(metadataBuildingContext, root)
        def table = new Table("my_table")
        def ownerEntity = Mock(GrailsHibernatePersistentEntity)
        ownerEntity.isRoot() >> true
        def currentGrailsProp = Mock(GrailsHibernatePersistentProperty)
        def componentProperty = Mock(GrailsHibernatePersistentProperty)
        def mappings = Mock(InFlightMetadataCollector)
        def hibernateProperty = new Property()
        hibernateProperty.setName("type")

        def mapping = new Mapping()
        ownerEntity.getMappedForm() >> mapping
        currentGrailsProp.getType() >> MyEnum
        setupProperty(currentGrailsProp, "type", mapping, ownerEntity)
        setupProperty(componentProperty, "address", mapping, ownerEntity)
        
        columnNameFetcher.getColumnNameForPropertyAndPath(currentGrailsProp, "address", null) >> "address_type_col"

        when:
        binder.bindComponentProperty(component, componentProperty, currentGrailsProp, root, "address", table, mappings)

        then:
        1 * enumTypeBinder.bindEnumType(currentGrailsProp, MyEnum, _ as BasicValue, "address_type_col")
        0 * componentUpdater.updateComponent(_, _, _, _)
    }

    def "should set columns to nullable when component property is nullable"() {
        given:
        def metadataBuildingContext = getGrailsDomainBinder().getMetadataBuildingContext()
        def root = new RootClass(metadataBuildingContext)
        def component = new Component(metadataBuildingContext, root)
        def table = new Table("my_table")
        def ownerEntity = Mock(GrailsHibernatePersistentEntity)
        ownerEntity.isRoot() >> true
        def currentGrailsProp = Mock(GrailsHibernatePersistentProperty)
        def componentProperty = Mock(GrailsHibernatePersistentProperty)
        def mappings = Mock(InFlightMetadataCollector)
        def hibernateProperty = new Property()
        hibernateProperty.setName("street")
        
        def mapping = new Mapping()
        ownerEntity.getMappedForm() >> mapping
        currentGrailsProp.getType() >> String
        setupProperty(currentGrailsProp, "street", mapping, ownerEntity)
        setupProperty(componentProperty, "address", mapping, ownerEntity)
        
        ownerEntity.isComponentPropertyNullable(componentProperty) >> true
        
        when:
        binder.bindComponentProperty(component, componentProperty, currentGrailsProp, root, "address", table, mappings)

        then:
        1 * mockSimpleValueBinder.bindSimpleValue(
            currentGrailsProp, 
            componentProperty, 
            _ as BasicValue, 
            "address"
        )
        0 * componentUpdater.updateComponent(_, _, _, _)
    }

    static class MyEntity {}
    static class Address {}
    enum MyEnum { VAL }
}

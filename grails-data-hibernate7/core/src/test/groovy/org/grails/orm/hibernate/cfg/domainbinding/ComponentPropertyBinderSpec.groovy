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
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateManyToOneProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateOneToOneProperty
import org.grails.orm.hibernate.cfg.domainbinding.binder.CollectionBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.ComponentBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.ComponentPropertyBinder
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
import spock.lang.Subject

import org.grails.orm.hibernate.cfg.domainbinding.util.PropertyFromValueCreator

class ComponentPropertyBinderSpec extends HibernateGormDatastoreSpec {

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

    PersistentEntityNamingStrategy namingStrategy = Mock(PersistentEntityNamingStrategy)
    MappingCacheHolder mappingCacheHolder = Mock(MappingCacheHolder)
    CollectionHolder collectionHolder = new CollectionHolder([:])
    EnumTypeBinder enumTypeBinder = Mock(EnumTypeBinder)
    CollectionBinder collectionBinder = Mock(CollectionBinder)
    PropertyFromValueCreator propertyFromValueCreator = Mock(PropertyFromValueCreator)
    ComponentBinder componentBinder = Mock(ComponentBinder)
    OneToOneBinder oneToOneBinder = Mock(OneToOneBinder)
    ManyToOneBinder manyToOneBinder = Mock(ManyToOneBinder)
    ColumnNameForPropertyAndPathFetcher columnNameFetcher = Mock(ColumnNameForPropertyAndPathFetcher)
    ComponentUpdater componentUpdater

    @Subject
    ComponentPropertyBinder binder

    def mockSimpleValueBinder = Mock(SimpleValueBinder) // Mock SimpleValueBinder

    def setup() {
        componentUpdater = new ComponentUpdater(propertyFromValueCreator)
        
        binder = new ComponentPropertyBinder(
                getGrailsDomainBinder().getMetadataBuildingContext(),
                collectionHolder,
                enumTypeBinder,
                collectionBinder,
                mockSimpleValueBinder,
                oneToOneBinder,
                manyToOneBinder,
                columnNameFetcher,
                componentUpdater
        )
        binder.setComponentBinder(componentBinder)
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
        1 * propertyFromValueCreator.createProperty(_ as BasicValue, currentGrailsProp) >> hibernateProperty
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
        1 * manyToOneBinder.bindManyToOne(currentGrailsProp, _ as HibernateManyToOne, "address")
        1 * propertyFromValueCreator.createProperty(_ as HibernateManyToOne, currentGrailsProp) >> hibernateProperty
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
        
        when:
        binder.bindComponentProperty(component, componentProperty, currentGrailsProp, root, "address", table, mappings)

        then:
        1 * oneToOneBinder.bindOneToOne(currentGrailsProp, _ as HibernateOneToOne, "address")
        1 * propertyFromValueCreator.createProperty(_ as HibernateOneToOne, currentGrailsProp) >> hibernateProperty
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
        1 * propertyFromValueCreator.createProperty(_ as BasicValue, currentGrailsProp) >> hibernateProperty
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
        1 * propertyFromValueCreator.createProperty(_ as BasicValue, currentGrailsProp) >> hibernateProperty
    }

    enum MyEnum { VAL }
}

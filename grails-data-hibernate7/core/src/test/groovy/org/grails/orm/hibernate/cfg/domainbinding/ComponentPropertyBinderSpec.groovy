package org.grails.orm.hibernate.cfg.domainbinding

import grails.gorm.specs.HibernateGormDatastoreSpec
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.model.types.Association
import org.grails.datastore.mapping.model.types.Embedded
import org.grails.datastore.mapping.model.types.ManyToOne as GormManyToOne
import org.grails.datastore.mapping.model.types.OneToOne as GormOneToOne
import org.grails.orm.hibernate.cfg.GrailsHibernatePersistentEntity
import org.grails.orm.hibernate.cfg.GrailsHibernatePersistentProperty
import org.grails.orm.hibernate.cfg.Mapping
import org.grails.orm.hibernate.cfg.MappingCacheHolder
import org.grails.orm.hibernate.cfg.PersistentEntityNamingStrategy
import org.grails.orm.hibernate.cfg.PropertyConfig
import org.grails.orm.hibernate.cfg.domainbinding.collectionType.CollectionHolder
import org.hibernate.boot.spi.InFlightMetadataCollector
import org.hibernate.mapping.BasicValue
import org.hibernate.mapping.Column
import org.hibernate.mapping.Component
import org.hibernate.mapping.ManyToOne as HibernateManyToOne
import org.hibernate.mapping.OneToOne as HibernateOneToOne
import org.hibernate.mapping.PersistentClass
import org.hibernate.mapping.Property
import org.hibernate.mapping.RootClass
import org.hibernate.mapping.Table
import spock.lang.Subject

class ComponentPropertyBinderSpec extends HibernateGormDatastoreSpec {

    abstract static class TestManyToOne extends GormManyToOne<PropertyConfig> implements GrailsHibernatePersistentProperty {
        TestManyToOne(PersistentEntity owner, MappingContext context, java.beans.PropertyDescriptor descriptor) {
            super(owner, context, descriptor)
        }
    }

    abstract static class TestOneToOne extends GormOneToOne<PropertyConfig> implements GrailsHibernatePersistentProperty {
        TestOneToOne(PersistentEntity owner, MappingContext context, java.beans.PropertyDescriptor descriptor) {
            super(owner, context, descriptor)
        }
    }

    PersistentEntityNamingStrategy namingStrategy = Mock(PersistentEntityNamingStrategy)
    MappingCacheHolder mappingCacheHolder = Mock(MappingCacheHolder)
    CollectionHolder collectionHolder = new CollectionHolder([:])
    EnumTypeBinder enumTypeBinder = Mock(EnumTypeBinder)
    CollectionBinder collectionBinder = Mock(CollectionBinder)
    PropertyFromValueCreator propertyFromValueCreator = Mock(PropertyFromValueCreator)
    ComponentBinder componentBinder = Mock(ComponentBinder)

    @Subject
    ComponentPropertyBinder binder

    def setup() {
        binder = new ComponentPropertyBinder(
                getGrailsDomainBinder().getMetadataBuildingContext(),
                namingStrategy,
                mappingCacheHolder,
                collectionHolder,
                enumTypeBinder,
                collectionBinder,
                propertyFromValueCreator,
                componentBinder
        )
    }

    private void setupProperty(PersistentProperty prop, String name, Mapping mapping, PersistentEntity owner) {
        prop.getName() >> name
        prop.getOwner() >> owner
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
        def componentProperty = Mock(PersistentProperty)
        def mappings = Mock(InFlightMetadataCollector)
        def hibernateProperty = new Property()
        hibernateProperty.setName("street")
        
        def mapping = new Mapping()
        ownerEntity.getMappedForm() >> mapping
        currentGrailsProp.getOwner() >> ownerEntity
        currentGrailsProp.getType() >> String
        setupProperty(currentGrailsProp, "street", mapping, ownerEntity)
        
        propertyFromValueCreator.createProperty(_ as BasicValue, currentGrailsProp) >> hibernateProperty

        when:
        binder.bindComponentProperty(component, componentProperty, currentGrailsProp, root, "address", table, mappings, "sessionFactory")

        then:
        1 * propertyFromValueCreator.createProperty(_ as BasicValue, currentGrailsProp) >> hibernateProperty
        component.getProperty("street") == hibernateProperty
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
        def componentProperty = Mock(PersistentProperty)
        def mappings = Mock(InFlightMetadataCollector)
        def hibernateProperty = new Property()
        hibernateProperty.setName("owner")

        def mapping = new Mapping()
        ownerEntity.getMappedForm() >> mapping
        currentGrailsProp.getOwner() >> ownerEntity
        currentGrailsProp.getAssociatedEntity() >> Mock(GrailsHibernatePersistentEntity) { getName() >> "Owner" }
        currentGrailsProp.getType() >> Object
        setupProperty(currentGrailsProp, "owner", mapping, ownerEntity)
        
        propertyFromValueCreator.createProperty(_ as HibernateManyToOne, currentGrailsProp) >> hibernateProperty

        when:
        binder.bindComponentProperty(component, componentProperty, currentGrailsProp, root, "address", table, mappings, "sessionFactory")

        then:
        1 * propertyFromValueCreator.createProperty(_ as HibernateManyToOne, currentGrailsProp) >> hibernateProperty
        component.getProperty("owner") == hibernateProperty
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
        def componentProperty = Mock(PersistentProperty)
        def mappings = Mock(InFlightMetadataCollector)
        def hibernateProperty = new Property()
        hibernateProperty.setName("detail")

        def mapping = new Mapping()
        ownerEntity.getMappedForm() >> mapping
        currentGrailsProp.getOwner() >> ownerEntity
        ((Association)currentGrailsProp).getInverseSide() >> Mock(Association) {
            isHasOne() >> false
            getOwner() >> Mock(GrailsHibernatePersistentEntity) { getName() >> "Other" }
            getName() >> "other"
        }
        currentGrailsProp.getType() >> Object
        ((Association)currentGrailsProp).canBindOneToOneWithSingleColumnAndForeignKey() >> true
        setupProperty(currentGrailsProp, "detail", mapping, ownerEntity)
        
        propertyFromValueCreator.createProperty(_ as HibernateOneToOne, currentGrailsProp) >> hibernateProperty

        when:
        binder.bindComponentProperty(component, componentProperty, currentGrailsProp, root, "address", table, mappings, "sessionFactory")

        then:
        1 * propertyFromValueCreator.createProperty(_ as HibernateOneToOne, currentGrailsProp) >> hibernateProperty
        component.getProperty("detail") == hibernateProperty
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
        def componentProperty = Mock(PersistentProperty)
        def mappings = Mock(InFlightMetadataCollector)
        def hibernateProperty = new Property()
        hibernateProperty.setName("type")

        def mapping = new Mapping()
        ownerEntity.getMappedForm() >> mapping
        currentGrailsProp.getOwner() >> ownerEntity
        currentGrailsProp.getType() >> MyEnum
        setupProperty(currentGrailsProp, "type", mapping, ownerEntity)
        
        namingStrategy.resolveColumnName("type") >> "type_col"
        namingStrategy.resolveColumnName("address") >> "address"
        propertyFromValueCreator.createProperty(_ as BasicValue, currentGrailsProp) >> hibernateProperty

        when:
        binder.bindComponentProperty(component, componentProperty, currentGrailsProp, root, "address", table, mappings, "sessionFactory")

        then:
        1 * enumTypeBinder.bindEnumType(currentGrailsProp, MyEnum, _ as BasicValue, "address_type_col")
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
        def componentProperty = Mock(PersistentProperty)
        def ownerEntityGHPE = Mock(GrailsHibernatePersistentEntity)
        def mappings = Mock(InFlightMetadataCollector)
        def hibernateProperty = new Property()
        hibernateProperty.setName("street")
        def value = new BasicValue(metadataBuildingContext, table)
        def column = new Column("col1")
        value.addColumn(column)

        def mapping = new Mapping()
        ownerEntityGHPE.getMappedForm() >> mapping
        currentGrailsProp.getOwner() >> ownerEntityGHPE
        currentGrailsProp.getType() >> String
        setupProperty(currentGrailsProp, "street", mapping, ownerEntityGHPE)
        
        componentProperty.getOwner() >> ownerEntity
        ownerEntity.isComponentPropertyNullable(componentProperty) >> true
        
        propertyFromValueCreator.createProperty(value, currentGrailsProp) >> hibernateProperty
        hibernateProperty.setValue(value)

        when:
        binder.bindComponentProperty(component, componentProperty, currentGrailsProp, root, "address", table, mappings, "sessionFactory")

        then:
        column.isNullable()
    }

    enum MyEnum { VAL }
}
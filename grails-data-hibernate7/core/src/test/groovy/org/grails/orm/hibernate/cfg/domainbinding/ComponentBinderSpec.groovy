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
    org.grails.orm.hibernate.cfg.domainbinding.binder.GrailsPropertyBinder grailsPropertyBinder = Mock(org.grails.orm.hibernate.cfg.domainbinding.binder.GrailsPropertyBinder)

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
        binder.setGrailsPropertyBinder(grailsPropertyBinder)
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
        associatedEntity.getHibernateParentProperty(MyEntity) >> Optional.empty()
        associatedEntity.getHibernatePersistentProperties(MyEntity) >> [prop1]

        def mappings = metadataBuildingContext.getMetadataCollector()

        when:
        def component = binder.bindComponent(root, embeddedProp, mappings, "")

        then:
        component.getComponentClassName() == Address.name
        component.getRoleName() == Address.name + ".address"
        1 * mappingCacheHolder.cacheMapping(associatedEntity)
        1 * grailsPropertyBinder.bindProperty(root, root.getTable(), "address", embeddedProp, prop1, mappings) >> new BasicValue(metadataBuildingContext, root.getTable())
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
        associatedEntity.getHibernateParentProperty(MyEntity) >> Optional.empty()
        associatedEntity.getHibernatePersistentProperties(MyEntity) >> [normalProp]

        def mappings = metadataBuildingContext.getMetadataCollector()

        when:
        binder.bindComponent(root, embeddedProp, mappings, "")

        then:
        0 * componentUpdater.updateComponent(_, _, idProp, _)
        0 * componentUpdater.updateComponent(_, _, versionProp, _)
        1 * grailsPropertyBinder.bindProperty(root, root.getTable(), "address", embeddedProp, normalProp, mappings) >> new BasicValue(metadataBuildingContext, root.getTable())
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
        associatedEntity.getHibernateParentProperty(MyEntity) >> Optional.of(parentProp)
        associatedEntity.getHibernatePersistentProperties(MyEntity) >> []

        def mappings = metadataBuildingContext.getMetadataCollector()

        when:
        def component = binder.bindComponent(root, embeddedProp, mappings, "")

        then:
        component.getParentProperty() == "myEntity"
        0 * grailsPropertyBinder.bindProperty(_, _, _, _, parentProp, _)
        0 * componentUpdater.updateComponent(_, _, parentProp, _)
    }

    static class MyEntity {}
    static class Address {}
    enum MyEnum { VAL }
}

package org.grails.orm.hibernate.cfg.domainbinding

import grails.gorm.specs.HibernateGormDatastoreSpec
import org.grails.orm.hibernate.cfg.domainbinding.binder.GrailsPropertyBinder
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity
import org.grails.orm.hibernate.cfg.MappingCacheHolder
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateEmbeddedProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateSimpleProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateIdentityProperty
import org.grails.orm.hibernate.cfg.domainbinding.binder.ComponentBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.ComponentUpdater
import org.hibernate.mapping.BasicValue
import org.hibernate.mapping.Component
import org.hibernate.mapping.PersistentClass
import org.hibernate.mapping.RootClass
import org.hibernate.mapping.Table
import org.hibernate.mapping.Value
import spock.lang.Subject

class ComponentBinderSpec extends HibernateGormDatastoreSpec {

    // Mock Collaborators
    MappingCacheHolder mappingCacheHolder = Mock(MappingCacheHolder)
    ComponentUpdater componentUpdater = Mock(ComponentUpdater)
    GrailsPropertyBinder grailsPropertyBinder = Mock(GrailsPropertyBinder)

    @Subject
    ComponentBinder binder

    def setup() {
        def metadataBuildingContext = getGrailsDomainBinder().getMetadataBuildingContext()
        binder = new ComponentBinder(metadataBuildingContext, mappingCacheHolder, componentUpdater)
        binder.setGrailsPropertyBinder(grailsPropertyBinder)
    }

    def "should bind component and its properties"() {
        given:
        def metadataBuildingContext = getGrailsDomainBinder().getMetadataBuildingContext()
        def root = new RootClass(metadataBuildingContext)
        root.setEntityName("MyEntity")
        root.setTable(new Table("my_entity"))

        def associatedEntity = GroovyMock(GrailsHibernatePersistentEntity)
        def embeddedProp = mockEmbeddedProperty(associatedEntity, "address", Address, root)

        // Ensure the associated entity also knows its class for initialization logic
        associatedEntity.getPersistentClass() >> root

        def prop1 = Mock(HibernateSimpleProperty)
        prop1.getName() >> "street"
        prop1.getType() >> String

        associatedEntity.getHibernateParentProperty(MyEntity) >> Optional.empty()
        associatedEntity.getHibernatePersistentProperties(MyEntity) >> [prop1]

        when:
        def component = binder.bindComponent(embeddedProp, "")

        then:
        component.getComponentClassName() == Address.name
        component.getRoleName() == Address.name + ".address"
        1 * mappingCacheHolder.cacheMapping(associatedEntity)
        1 * grailsPropertyBinder.bindProperty(prop1, embeddedProp, "address") >> new BasicValue(metadataBuildingContext, root.getTable())
        1 * componentUpdater.updateComponent(_ as Component, embeddedProp, prop1, _ as Value)
    }

    def "should skip identity properties during binding"() {
        given:
        def metadataBuildingContext = getGrailsDomainBinder().getMetadataBuildingContext()
        def root = new RootClass(metadataBuildingContext)
        root.setTable(new Table("my_entity"))

        def associatedEntity = GroovyMock(GrailsHibernatePersistentEntity)
        def embeddedProp = mockEmbeddedProperty(associatedEntity, "address", Address, root)

        associatedEntity.getPersistentClass() >> root

        // HibernatePersistentProperty includes ID properties; usually filtered by the loop logic
        def idProp = Mock(HibernateIdentityProperty)
        idProp.getName() >> "id"

        def normalProp = Mock(HibernateSimpleProperty)
        normalProp.getName() >> "street"
        normalProp.getType() >> String

        associatedEntity.getHibernateParentProperty(MyEntity) >> Optional.empty()
        associatedEntity.getHibernatePersistentProperties(MyEntity) >> [normalProp]

        when:
        binder.bindComponent(embeddedProp, "")

        then:
        // Logic check: if idProp is not in the list returned by getHibernatePersistentProperties, it's skipped
        0 * componentUpdater.updateComponent(_, _, idProp, _)
        1 * componentUpdater.updateComponent(_, _, normalProp, _)
    }

    def "should set parent property when component has reference back to owner"() {
        given:
        def metadataBuildingContext = getGrailsDomainBinder().getMetadataBuildingContext()
        def root = new RootClass(metadataBuildingContext)
        root.setTable(new Table("my_entity"))

        def associatedEntity = GroovyMock(GrailsHibernatePersistentEntity)
        def embeddedProp = mockEmbeddedProperty(associatedEntity, "address", Address, root)

        associatedEntity.getPersistentClass() >> root

        def parentProp = Mock(HibernateSimpleProperty)
        parentProp.getName() >> "myEntity"

        associatedEntity.getHibernateParentProperty(MyEntity) >> Optional.of(parentProp)
        associatedEntity.getHibernatePersistentProperties(MyEntity) >> []

        when:
        def component = binder.bindComponent(embeddedProp, "")

        then:
        component.getParentProperty() == "myEntity"
    }

    /**
     * Helper to reduce boilerplate.
     * The 'root' (PersistentClass) is required by the Component constructor to avoid NPE.
     */
    private HibernateEmbeddedProperty mockEmbeddedProperty(
            GrailsHibernatePersistentEntity associatedEntity,
            String name,
            Class type,
            PersistentClass root) {

        def embeddedProp = Mock(HibernateEmbeddedProperty)
        embeddedProp.getName() >> name
        embeddedProp.getType() >> type
        embeddedProp.getAssociatedEntity() >> associatedEntity
        embeddedProp.getPersistentClass() >> root // CRITICAL FIX

        embeddedProp.getOwner() >> Mock(GrailsHibernatePersistentEntity) {
            getJavaClass() >> MyEntity
        }
        return embeddedProp
    }

    static class MyEntity {}
    static class Address {}
}
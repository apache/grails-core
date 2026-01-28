package org.grails.orm.hibernate.cfg.domainbinding

import grails.gorm.specs.HibernateGormDatastoreSpec
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.model.types.Embedded
import org.grails.orm.hibernate.cfg.GrailsHibernatePersistentEntity
import org.grails.orm.hibernate.cfg.MappingCacheHolder
import org.hibernate.boot.spi.InFlightMetadataCollector
import org.hibernate.mapping.Component
import org.hibernate.mapping.PersistentClass
import org.hibernate.mapping.RootClass
import spock.lang.Subject

class ComponentBinderSpec extends HibernateGormDatastoreSpec {

    MappingCacheHolder mappingCacheHolder = Mock(MappingCacheHolder)
    ComponentPropertyBinder componentPropertyBinder = Mock(ComponentPropertyBinder)

    @Subject
    ComponentBinder binder

    def setup() {
        binder = new ComponentBinder(mappingCacheHolder, componentPropertyBinder)
    }

    def "should bind component and its properties"() {
        given:
        def metadataBuildingContext = getGrailsDomainBinder().getMetadataBuildingContext()
        def root = new RootClass(metadataBuildingContext)
        root.setEntityName("MyEntity")
        def component = new Component(metadataBuildingContext, root)
        
        def embeddedProp = GroovyMock(Embedded)
        def associatedEntity = GroovyMock(GrailsHibernatePersistentEntity)
        def prop1 = GroovyMock(PersistentProperty)
        
        embeddedProp.getType() >> Address
        embeddedProp.getName() >> "address"
        embeddedProp.getAssociatedEntity() >> associatedEntity
        embeddedProp.getOwner() >> Mock(GrailsHibernatePersistentEntity) {
            getJavaClass() >> MyEntity
        }

        associatedEntity.getName() >> "Address"
        associatedEntity.getPersistentProperties() >> [prop1]
        associatedEntity.getIdentity() >> null
        
        prop1.getName() >> "street"
        prop1.getType() >> String

        def mappings = metadataBuildingContext.getMetadataCollector()

        when:
        binder.bindComponent(component, embeddedProp, true, mappings, "sessionFactory")

        then:
        component.getComponentClassName() == Address.name
        component.getRoleName() == Address.name + ".address"
        1 * mappingCacheHolder.cacheMapping(associatedEntity)
        1 * componentPropertyBinder.bindComponentProperty(component, embeddedProp, prop1, _ as PersistentClass, "address", _, mappings, "sessionFactory")
    }

    static class MyEntity {}
    static class Address {}
}

package org.grails.orm.hibernate.cfg.domainbinding

import grails.gorm.specs.HibernateGormDatastoreSpec
import org.grails.orm.hibernate.cfg.GrailsHibernatePersistentProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateEmbeddedProperty
import org.grails.orm.hibernate.cfg.GrailsHibernatePersistentEntity
import org.grails.orm.hibernate.cfg.MappingCacheHolder

import org.hibernate.mapping.Component
import org.hibernate.mapping.RootClass
import spock.lang.Subject

import org.grails.orm.hibernate.cfg.domainbinding.binder.ComponentBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.ComponentPropertyBinder

class ComponentBinderSpec extends HibernateGormDatastoreSpec {

    MappingCacheHolder mappingCacheHolder = Mock(MappingCacheHolder)
    ComponentPropertyBinder componentPropertyBinder = Mock(ComponentPropertyBinder)

    @Subject
    ComponentBinder binder

    def setup() {
        binder = new ComponentBinder(getGrailsDomainBinder().getMetadataBuildingContext(), mappingCacheHolder, componentPropertyBinder)
    }

    def "should bind component and its properties"() {
        given:
        def metadataBuildingContext = getGrailsDomainBinder().getMetadataBuildingContext()
        def root = new RootClass(metadataBuildingContext)
        root.setEntityName("MyEntity")
        
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
        1 * componentPropertyBinder.bindComponentProperty(_, _, _, _, _, _, _)
    }

    static class MyEntity {}
    static class Address {}
}

package org.grails.orm.hibernate.cfg.domainbinding

import grails.gorm.specs.HibernateGormDatastoreSpec
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentProperty
import org.grails.orm.hibernate.cfg.CompositeIdentity
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity

import org.hibernate.mapping.Component
import org.hibernate.mapping.RootClass
import org.hibernate.mapping.Table
import spock.lang.Subject

import org.grails.orm.hibernate.cfg.domainbinding.binder.ComponentBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.CompositeIdBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.ComponentUpdater
import org.grails.orm.hibernate.cfg.domainbinding.binder.GrailsPropertyBinder
import org.hibernate.mapping.Value

class CompositeIdBinderSpec extends HibernateGormDatastoreSpec {

    def componentBinder = Mock(ComponentBinder)
    def componentUpdater = Mock(ComponentUpdater)
    def grailsPropertyBinder = Mock(GrailsPropertyBinder)

    @Subject
    CompositeIdBinder binder

    def setup() {
        binder = new CompositeIdBinder(getGrailsDomainBinder().getMetadataBuildingContext(), componentUpdater, grailsPropertyBinder)
    }

    def "should bind composite id using property names from CompositeIdentity"() {
        given:
        def domainClass = Mock(GrailsHibernatePersistentEntity)
        def metadataBuildingContext = getGrailsDomainBinder().getMetadataBuildingContext()
        def root = new RootClass(metadataBuildingContext)
        root.setEntityName("MyEntity")
        def mappings = metadataBuildingContext.getMetadataCollector()
        
        def compositeIdentity = new CompositeIdentity(propertyNames: ['prop1', 'prop2'] as String[])
        
        def prop1 = Mock(GrailsHibernatePersistentProperty)
        def prop2 = Mock(GrailsHibernatePersistentProperty)
        def identifierProp = Mock(GrailsHibernatePersistentProperty)
        domainClass.getPropertyByName("prop1") >> prop1
        domainClass.getPropertyByName("prop2") >> prop2
        domainClass.getIdentity() >> identifierProp
        domainClass.getName() >> "MyEntity"
        
        def table = new Table("my_entity")
        root.setTable(table)

        when:
        binder.bindCompositeId(domainClass, root, compositeIdentity, mappings)

        then:
        root.getIdentifier() instanceof Component
        root.getIdentifierMapper() instanceof Component
        root.hasEmbeddedIdentifier()
        2 * grailsPropertyBinder.bindProperty(root, table, "", identifierProp, _ as GrailsHibernatePersistentProperty, mappings) >> Mock(Value)
        2 * componentUpdater.updateComponent(_ as Component, identifierProp, _ as GrailsHibernatePersistentProperty, _ as Value)
    }

    def "should fallback to domainClass composite identity when CompositeIdentity is null"() {
        given:
        def domainClass = Mock(GrailsHibernatePersistentEntity)
        def metadataBuildingContext = getGrailsDomainBinder().getMetadataBuildingContext()
        def root = new RootClass(metadataBuildingContext)
        root.setEntityName("MyEntity")
        def mappings = metadataBuildingContext.getMetadataCollector()
        
        def prop1 = Mock(GrailsHibernatePersistentProperty)
        def identifierProp = Mock(GrailsHibernatePersistentProperty)
        domainClass.getCompositeIdentity() >> ([prop1] as GrailsHibernatePersistentProperty[])
        domainClass.getIdentity() >> identifierProp
        domainClass.getName() >> "MyEntity"
        
        def table = new Table("my_entity")
        root.setTable(table)

        when:
        binder.bindCompositeId(domainClass, root, null, mappings)

        then:
        1 * grailsPropertyBinder.bindProperty(root, table, "", identifierProp, prop1, mappings) >> Mock(Value)
        1 * componentUpdater.updateComponent(_ as Component, identifierProp, prop1, _ as Value)
    }

    def "should throw MappingException if no composite properties found"() {
        given:
        def domainClass = Mock(GrailsHibernatePersistentEntity)
        def metadataBuildingContext = getGrailsDomainBinder().getMetadataBuildingContext()
        def root = new RootClass(metadataBuildingContext)
        root.setEntityName("MyEntity")
        def mappings = metadataBuildingContext.getMetadataCollector()
        domainClass.getCompositeIdentity() >> null
        domainClass.getName() >> "MyEntity"

        when:
        binder.bindCompositeId(domainClass, root, null, mappings)

        then:
        thrown(org.hibernate.MappingException)
    }
}

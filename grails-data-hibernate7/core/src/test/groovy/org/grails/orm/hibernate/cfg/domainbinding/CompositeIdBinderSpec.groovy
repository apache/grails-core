package org.grails.orm.hibernate.cfg.domainbinding

import grails.gorm.specs.HibernateGormDatastoreSpec
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.orm.hibernate.cfg.GrailsHibernatePersistentProperty
import org.grails.orm.hibernate.cfg.CompositeIdentity
import org.grails.orm.hibernate.cfg.GrailsHibernatePersistentEntity

import org.hibernate.mapping.Component
import org.hibernate.mapping.RootClass
import org.hibernate.mapping.Table
import spock.lang.Subject

import org.grails.orm.hibernate.cfg.domainbinding.binder.ComponentBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.CompositeIdBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.ComponentUpdater
import org.hibernate.mapping.Value

class CompositeIdBinderSpec extends HibernateGormDatastoreSpec {

    def componentBinder = Mock(ComponentBinder)
    def componentUpdater = Mock(ComponentUpdater)

    @Subject
    CompositeIdBinder binder

    def setup() {
        binder = new CompositeIdBinder(getGrailsDomainBinder().getMetadataBuildingContext(), componentBinder, componentUpdater)
    }

    def "should bind composite id using property names from CompositeIdentity"() {
        given:
        def domainClass = GroovyMock(GrailsHibernatePersistentEntity)
        def metadataBuildingContext = getGrailsDomainBinder().getMetadataBuildingContext()
        def root = new RootClass(metadataBuildingContext)
        root.setEntityName("MyEntity")
        def mappings = metadataBuildingContext.getMetadataCollector()
        
        def compositeIdentity = new CompositeIdentity(propertyNames: ['prop1', 'prop2'] as String[])
        
        def prop1 = GroovyMock(GrailsHibernatePersistentProperty)
        def prop2 = GroovyMock(GrailsHibernatePersistentProperty)
        def identifierProp = GroovyMock(GrailsHibernatePersistentProperty)
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
        2 * componentBinder.bindComponentProperty(identifierProp, _ as PersistentProperty, root, "", table, mappings) >> Mock(Value)
        2 * componentUpdater.updateComponent(_ as Component, identifierProp, _ as PersistentProperty, _ as Value)
    }

    def "should fallback to domainClass composite identity when CompositeIdentity is null"() {
        given:
        def domainClass = GroovyMock(GrailsHibernatePersistentEntity)
        def metadataBuildingContext = getGrailsDomainBinder().getMetadataBuildingContext()
        def root = new RootClass(metadataBuildingContext)
        root.setEntityName("MyEntity")
        def mappings = metadataBuildingContext.getMetadataCollector()
        
        def prop1 = GroovyMock(GrailsHibernatePersistentProperty)
        def identifierProp = GroovyMock(GrailsHibernatePersistentProperty)
        domainClass.getCompositeIdentity() >> ([prop1] as GrailsHibernatePersistentProperty[])
        domainClass.getIdentity() >> identifierProp
        domainClass.getName() >> "MyEntity"
        
        def table = new Table("my_entity")
        root.setTable(table)

        when:
        binder.bindCompositeId(domainClass, root, null, mappings)

        then:
        1 * componentBinder.bindComponentProperty(identifierProp, prop1, root, "", table, mappings) >> Mock(Value)
        1 * componentUpdater.updateComponent(_ as Component, identifierProp, prop1, _ as Value)
    }

    def "should throw MappingException if no composite properties found"() {
        given:
        def domainClass = GroovyMock(GrailsHibernatePersistentEntity)
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

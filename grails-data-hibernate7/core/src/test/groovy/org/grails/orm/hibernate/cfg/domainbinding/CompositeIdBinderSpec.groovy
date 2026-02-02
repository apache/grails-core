package org.grails.orm.hibernate.cfg.domainbinding

import grails.gorm.specs.HibernateGormDatastoreSpec
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.orm.hibernate.cfg.GrailsHibernatePersistentProperty
import org.grails.orm.hibernate.cfg.CompositeIdentity
import org.grails.orm.hibernate.cfg.GrailsHibernatePersistentEntity
import org.hibernate.boot.spi.InFlightMetadataCollector
import org.hibernate.mapping.Component
import org.hibernate.mapping.RootClass
import org.hibernate.mapping.Table
import spock.lang.Subject

class CompositeIdBinderSpec extends HibernateGormDatastoreSpec {

    def componentPropertyBinder = Mock(ComponentPropertyBinder)

    @Subject
    CompositeIdBinder binder

    def setup() {
        binder = new CompositeIdBinder(getGrailsDomainBinder().getMetadataBuildingContext(), componentPropertyBinder)
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
        binder.bindCompositeId(domainClass, root, compositeIdentity, mappings, "sessionFactory")

        then:
        root.getIdentifier() instanceof Component
        root.getIdentifierMapper() instanceof Component
        root.hasEmbeddedIdentifier()
        2 * componentPropertyBinder.bindComponentProperty(_ as Component, identifierProp, _ as PersistentProperty, root, "", table, mappings, "sessionFactory")
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
        binder.bindCompositeId(domainClass, root, null, mappings, "sessionFactory")

        then:
        1 * componentPropertyBinder.bindComponentProperty(_ as Component, identifierProp, prop1, root, "", table, mappings, "sessionFactory")
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
        binder.bindCompositeId(domainClass, root, null, mappings, "sessionFactory")

        then:
        thrown(org.hibernate.MappingException)
    }
}
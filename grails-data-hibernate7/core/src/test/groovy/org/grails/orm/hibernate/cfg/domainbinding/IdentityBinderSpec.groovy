package org.grails.orm.hibernate.cfg.domainbinding

import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.orm.hibernate.cfg.CompositeIdentity
import org.grails.orm.hibernate.cfg.GrailsHibernatePersistentEntity
import org.grails.orm.hibernate.cfg.Identity
import org.grails.orm.hibernate.cfg.Mapping
import org.hibernate.boot.spi.InFlightMetadataCollector
import org.hibernate.mapping.RootClass
import spock.lang.Specification
import spock.lang.Subject

class IdentityBinderSpec extends Specification {

    def simpleIdBinder = Mock(SimpleIdBinder)
    def compositeIdBinder = Mock(CompositeIdBinder)

    @Subject
    IdentityBinder binder = new IdentityBinder(simpleIdBinder, compositeIdBinder)

    def "should delegate to simpleIdBinder when mapping is null and domainClass has simple identity"() {
        given:
        def domainClass = GroovyMock(GrailsHibernatePersistentEntity)
        def root = GroovyMock(RootClass)
        def mappings = GroovyMock(InFlightMetadataCollector)
        def identifierProp = GroovyMock(PersistentProperty)
        domainClass.getIdentity() >> identifierProp
        domainClass.getCompositeIdentity() >> null

        when:
        binder.bindIdentity(domainClass, root, mappings, null, "sessionFactory")

        then:
        1 * simpleIdBinder.bindSimpleId(identifierProp, root, null)
    }

    def "should delegate to compositeIdBinder when mapping is null and domainClass has composite identity"() {
        given:
        def domainClass = GroovyMock(GrailsHibernatePersistentEntity)
        def root = GroovyMock(RootClass)
        def mappings = GroovyMock(InFlightMetadataCollector)
        def compositeProps = [GroovyMock(PersistentProperty)] as PersistentProperty[]
        domainClass.getCompositeIdentity() >> compositeProps

        when:
        binder.bindIdentity(domainClass, root, mappings, null, "sessionFactory")

        then:
        1 * compositeIdBinder.bindCompositeId(domainClass, root, null, mappings, "sessionFactory")
    }

    def "should delegate to compositeIdBinder when mapping specifies composite identity"() {
        given:
        def domainClass = GroovyMock(GrailsHibernatePersistentEntity)
        def root = GroovyMock(RootClass)
        def mappings = GroovyMock(InFlightMetadataCollector)
        def gormMapping = GroovyMock(Mapping)
        def compositeIdentity = GroovyMock(CompositeIdentity)
        gormMapping.getIdentity() >> compositeIdentity

        when:
        binder.bindIdentity(domainClass, root, mappings, gormMapping, "sessionFactory")

        then:
        1 * compositeIdBinder.bindCompositeId(domainClass, root, compositeIdentity, mappings, "sessionFactory")
    }

    def "should delegate to simpleIdBinder when mapping specifies simple identity"() {
        given:
        def domainClass = GroovyMock(GrailsHibernatePersistentEntity)
        def root = GroovyMock(RootClass)
        def mappings = GroovyMock(InFlightMetadataCollector)
        def gormMapping = GroovyMock(Mapping)
        def identity = new Identity(name: "foo")
        gormMapping.getIdentity() >> identity
        def identifierProp = GroovyMock(PersistentProperty)
        domainClass.getPropertyByName("foo") >> identifierProp
        domainClass.getIdentity() >> identifierProp
        domainClass.getName() >> "MyEntity"

        when:
        binder.bindIdentity(domainClass, root, mappings, gormMapping, "sessionFactory")

        then:
        1 * simpleIdBinder.bindSimpleId(identifierProp, root, identity)
    }

    def "should throw MappingException when mapping specifies a non-existent identifier property"() {
        given:
        def domainClass = GroovyMock(GrailsHibernatePersistentEntity)
        def root = GroovyMock(RootClass)
        def mappings = GroovyMock(InFlightMetadataCollector)
        def gormMapping = GroovyMock(Mapping)
        def identity = new Identity(name: "nonExistent")
        gormMapping.getIdentity() >> identity
        domainClass.getName() >> "MyEntity"
        domainClass.getPropertyByName("nonExistent") >> null

        when:
        binder.bindIdentity(domainClass, root, mappings, gormMapping, "sessionFactory")

        then:
        thrown(org.hibernate.MappingException)
    }

    def "should not lookup property by name if identity name matches domain class name"() {
        given:
        def domainClass = GroovyMock(GrailsHibernatePersistentEntity)
        def root = GroovyMock(RootClass)
        def mappings = GroovyMock(InFlightMetadataCollector)
        def gormMapping = GroovyMock(Mapping)
        def identity = new Identity(name: "MyEntity")
        gormMapping.getIdentity() >> identity
        def identifierProp = GroovyMock(PersistentProperty)
        domainClass.getIdentity() >> identifierProp
        domainClass.getName() >> "MyEntity"

        when:
        binder.bindIdentity(domainClass, root, mappings, gormMapping, "sessionFactory")

        then:
        0 * domainClass.getPropertyByName(_)
        1 * simpleIdBinder.bindSimpleId(identifierProp, root, identity)
    }
}

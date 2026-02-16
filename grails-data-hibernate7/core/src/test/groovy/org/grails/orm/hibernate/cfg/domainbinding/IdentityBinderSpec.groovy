package org.grails.orm.hibernate.cfg.domainbinding


import org.grails.orm.hibernate.cfg.GrailsHibernatePersistentProperty
import org.grails.orm.hibernate.cfg.CompositeIdentity
import org.grails.orm.hibernate.cfg.GrailsHibernatePersistentEntity
import org.grails.orm.hibernate.cfg.Identity
import org.grails.orm.hibernate.cfg.Mapping
import org.hibernate.boot.spi.InFlightMetadataCollector
import org.hibernate.mapping.RootClass
import grails.gorm.specs.HibernateGormDatastoreSpec
import spock.lang.Subject

import org.grails.orm.hibernate.cfg.domainbinding.binder.CompositeIdBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.IdentityBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.SimpleIdBinder

class IdentityBinderSpec extends HibernateGormDatastoreSpec {

    def simpleIdBinder = Mock(SimpleIdBinder)
    def compositeIdBinder = Mock(CompositeIdBinder)

    @Subject
    IdentityBinder binder

    def setup() {
        binder = new IdentityBinder(simpleIdBinder, compositeIdBinder)
    }

    def "should delegate to simpleIdBinder when mapping is null and domainClass has simple identity"() {
        given:
        def domainClass = Mock(GrailsHibernatePersistentEntity)
        def root = new RootClass(getGrailsDomainBinder().getMetadataBuildingContext())
        def mappings = Mock(InFlightMetadataCollector)
        def identifierProp = Mock(GrailsHibernatePersistentProperty)
        domainClass.getIdentity() >> identifierProp
        domainClass.getCompositeIdentity() >> null

        when:
        binder.bindIdentity(domainClass, root, mappings, null)

        then:
        1 * simpleIdBinder.bindSimpleId(domainClass, root, null, _)
    }

    def "should delegate to compositeIdBinder when mapping is null and domainClass has composite identity"() {
        given:
        def domainClass = Mock(GrailsHibernatePersistentEntity)
        def root = new RootClass(getGrailsDomainBinder().getMetadataBuildingContext())
        def mappings = Mock(InFlightMetadataCollector)
        def compositeProps = [Mock(GrailsHibernatePersistentProperty)] as GrailsHibernatePersistentProperty[]
        domainClass.getCompositeIdentity() >> compositeProps

        when:
        binder.bindIdentity(domainClass, root, mappings, null)

        then:
        1 * compositeIdBinder.bindCompositeId(domainClass, root, null, mappings)
    }

    def "should delegate to compositeIdBinder when mapping specifies composite identity"() {
        given:
        def domainClass = Mock(GrailsHibernatePersistentEntity)
        def root = new RootClass(getGrailsDomainBinder().getMetadataBuildingContext())
        def mappings = Mock(InFlightMetadataCollector)
        def gormMapping = Mock(Mapping)
        def compositeIdentity = Mock(CompositeIdentity)
        gormMapping.getIdentity() >> compositeIdentity

        when:
        binder.bindIdentity(domainClass, root, mappings, gormMapping)

        then:
        1 * compositeIdBinder.bindCompositeId(domainClass, root, compositeIdentity, mappings)
    }

    def "should delegate to simpleIdBinder when mapping specifies simple identity"() {
        given:
        def domainClass = Mock(GrailsHibernatePersistentEntity)
        def root = new RootClass(getGrailsDomainBinder().getMetadataBuildingContext())
        def mappings = Mock(InFlightMetadataCollector)
        def gormMapping = Mock(Mapping)
        def identity = new Identity(name: "foo")
        gormMapping.getIdentity() >> identity
        def identifierProp = Mock(GrailsHibernatePersistentProperty)
        domainClass.getPropertyByName("foo") >> identifierProp
        domainClass.getIdentity() >> identifierProp
        domainClass.getName() >> "MyEntity"

        when:
        binder.bindIdentity(domainClass, root, mappings, gormMapping)

        then:
        1 * simpleIdBinder.bindSimpleId(domainClass, root, identity, _)
    }

    def "should not lookup property by name if identity name matches domain class name"() {
        given:
        def domainClass = Mock(GrailsHibernatePersistentEntity)
        def root = new RootClass(getGrailsDomainBinder().getMetadataBuildingContext())
        def mappings = Mock(InFlightMetadataCollector)
        def gormMapping = Mock(Mapping)
        def identity = new Identity(name: "MyEntity")
        gormMapping.getIdentity() >> identity
        def identifierProp = Mock(GrailsHibernatePersistentProperty)
        domainClass.getIdentity() >> identifierProp
        domainClass.getName() >> "MyEntity"

        when:
        binder.bindIdentity(domainClass, root, mappings, gormMapping)

        then:
        1 * simpleIdBinder.bindSimpleId(domainClass, root, identity, _)
    }

    def "should set entity name on identity if it is null"() {
        given:
        def domainClass = Mock(GrailsHibernatePersistentEntity)
        def root = new RootClass(getGrailsDomainBinder().getMetadataBuildingContext())
        root.setEntityName("MyEntity")
        def mappings = Mock(InFlightMetadataCollector)
        def gormMapping = Mock(Mapping)
        def identity = new Identity()
        gormMapping.getIdentity() >> identity
        def identifierProp = Mock(GrailsHibernatePersistentProperty)
        domainClass.getIdentity() >> identifierProp
        domainClass.getName() >> "MyEntity"

        when:
        binder.bindIdentity(domainClass, root, mappings, gormMapping)

        then:
        identity.getName() == "MyEntity"
        1 * simpleIdBinder.bindSimpleId(domainClass, root, identity, _)
    }
}

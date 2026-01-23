package org.grails.orm.hibernate.proxy

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import grails.gorm.specs.HibernateGormDatastoreSpec
import org.apache.grails.data.testing.tck.domains.Location
import org.hibernate.Hibernate
import spock.lang.Shared
import org.grails.datastore.gorm.proxy.GroovyProxyFactory

class HibernateProxyHandler7Spec extends HibernateGormDatastoreSpec {

    private static final Logger LOG = LoggerFactory.getLogger(HibernateProxyHandler7Spec.class)
    @Shared HibernateProxyHandler proxyHandler = new HibernateProxyHandler()

    void setupSpec() {
        manager.addAllDomainClasses([Location])
    }

    void "test isInitialized for a non-proxied object"() {
        given:
        Location location = new Location(name: "Test Location").save(flush: true)

        expect:
        proxyHandler.isInitialized(location) == true
    }

    void "test isInitialized for a native Hibernate proxy before initialization"() {
        given:
        Location location = new Location(name: "Test Location").save(flush: true)
        manager.session.clear()
        manager.hibernateSession.clear()

        // Get a proxy without initializing it
        Location proxyLocation = Location.proxy(location.id)
        LOG.info "proxyLocation class: ${proxyLocation.getClass().name}"
        LOG.info "proxyLocation instanceof EntityProxy: ${proxyLocation instanceof org.grails.datastore.mapping.proxy.EntityProxy}"
        LOG.info "Hibernate.isInitialized(proxyLocation): ${org.hibernate.Hibernate.isInitialized(proxyLocation)}"

        expect:
        proxyHandler.isInitialized(proxyLocation) == false
        !Hibernate.isInitialized(proxyLocation)
    }

    void "test isInitialized for a native Hibernate proxy after initialization"() {
        given:
        Location location = new Location(name: "Test Location").save(flush: true)
        manager.session.clear()
        manager.hibernateSession.clear()

        Location proxyLocation = Location.proxy(location.id)
        proxyLocation.name // Accessing a property to initialize the proxy

        expect:
        proxyHandler.isInitialized(proxyLocation) == true
        Hibernate.isInitialized(proxyLocation)
    }

    void "test isInitialized for a Groovy proxy before initialization"() {
        given:
        def originalFactory = manager.session.mappingContext.proxyFactory
        manager.session.mappingContext.proxyFactory = new GroovyProxyFactory()
        Location location = new Location(name: "Test Location").save(flush: true)
        manager.session.clear()
        manager.hibernateSession.clear()

        // Get a proxy without initializing it
        Location proxyLocation = Location.proxy(location.id)

        expect:
        proxyHandler.isInitialized(proxyLocation) == false

        cleanup:
        manager.session.mappingContext.proxyFactory = originalFactory
    }

    void "test unwrap for a native Hibernate proxy"() {
        given:
        Location location = new Location(name: "Test Location").save(flush: true)
        manager.session.clear()
        manager.hibernateSession.clear()

        Location proxyLocation = Location.proxy(location.id)
        def unwrapped = proxyHandler.unwrap(proxyLocation)

        expect:
        unwrapped != proxyLocation
        unwrapped.name == location.name
    }

    void "test unwrap for a Groovy proxy"() {
        given:
        def originalFactory = manager.session.mappingContext.proxyFactory
        manager.session.mappingContext.proxyFactory = new GroovyProxyFactory()
        Location location = new Location(name: "Test Location").save(flush: true)
        manager.session.clear()
        manager.hibernateSession.clear()

        Location proxyLocation = Location.proxy(location.id)
        def unwrapped = proxyHandler.unwrap(proxyLocation)

        expect:
        unwrapped != proxyLocation
        unwrapped.name == location.name

        cleanup:
        manager.session.mappingContext.proxyFactory = originalFactory
    }
}
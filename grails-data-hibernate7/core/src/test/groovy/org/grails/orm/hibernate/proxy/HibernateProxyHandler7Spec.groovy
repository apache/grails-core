package org.grails.orm.hibernate.proxy

import org.hibernate.proxy.HibernateProxy
import grails.gorm.specs.HibernateGormDatastoreSpec
import org.apache.grails.data.testing.tck.domains.Location
import org.hibernate.Hibernate
import spock.lang.Shared
import org.grails.datastore.gorm.proxy.GroovyProxyFactory

class HibernateProxyHandler7Spec extends HibernateGormDatastoreSpec {

    @Shared HibernateProxyHandler proxyHandler = new HibernateProxyHandler()

    void setupSpec() {
        manager.addAllDomainClasses([Location])
    }

    void "test isInitialized for a non-proxied object"() {
        given:
        Location location = new Location(name: "Test Location").save(flush: true)

        expect:
        proxyHandler.isInitialized(location)
    }

    void "test isInitialized for a native Hibernate proxy before initialization"() {
        given:
        Long savedId

        // Step 1: Persist the data and close the session
        Location.withNewSession {
            Location.withTransaction {
                Location location = new Location(name: "Test Location", code: "TL1").save(flush: true)
                savedId = location.id
            }
        }

        expect: "The proxy remains uninitialized when loaded via the standard Hibernate reference API"
        Location.withNewSession { session ->
            // Use the native Hibernate session to get a reference
            // This is the "purest" way to get an uninitialized proxy
            def proxyLocation = session.getSessionFactory().currentSession.getReference(Location, savedId)

            // 1. Verify it is actually a proxy
            proxyLocation instanceof HibernateProxy

            // 2. Verify the handler sees it as uninitialized
            (!proxyHandler.isInitialized(proxyLocation))
        }
    }

    void "test isInitialized for a native Hibernate proxy after initialization"() {
        given:
        Location location = new Location(name: "Test Location").save(flush: true)
        manager.session.clear()
        manager.hibernateSession.clear()

        Location proxyLocation = Location.proxy(location.id)
        proxyLocation.name // Accessing a property to initialize the proxy

        expect:
        proxyHandler.isInitialized(proxyLocation)
        Hibernate.isInitialized(proxyLocation)
    }

    void "test isInitialized for a Groovy proxy before initialization"() {
        given:
        def originalFactory = manager.session.mappingContext.proxyFactory
        manager.session.mappingContext.proxyFactory = new GroovyProxyFactory()

        // 1. Save and flush in a transaction
        Long savedId
        Location.withTransaction {
            savedId = new Location(name: "Test Location", code: "TL-GROOVY").save(flush: true).id
        }

        // 2. Clear the sessions to ensure the next load isn't from cache
        manager.session.clear()
        manager.hibernateSession.clear()

        when: "We get a reference via the native Hibernate API"
        // getReference is the Hibernate 6 way to get a 'hollow' proxy safely
        def proxyLocation = manager.hibernateSession.getReference(Location, savedId)

        then: "The proxy handler should recognize it as uninitialized"
        // Ensure no methods (like .name or .toString()) are called on proxyLocation before this
        !proxyHandler.isInitialized(proxyLocation)

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
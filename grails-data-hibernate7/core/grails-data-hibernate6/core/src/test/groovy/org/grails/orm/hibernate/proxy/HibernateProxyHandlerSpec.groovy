package org.grails.orm.hibernate.proxy

import grails.gorm.annotation.Entity
import grails.gorm.specs.HibernateGormDatastoreSpec
import org.hibernate.Hibernate
import spock.lang.Shared

class HibernateProxyHandlerSpec extends HibernateGormDatastoreSpec {

    @Shared HibernateProxyHandler proxyHandler = new HibernateProxyHandler()

    void "test isInitialized for a non-proxied object"() {
        given:
        Location location = new Location(name: "Test Location").save(flush: true)

        expect:
        proxyHandler.isInitialized(location) == true
    }

    void "test isInitialized for a proxied object before initialization"() {
        given:
        Location location = new Location(name: "Test Location").save(flush: true)
        manager.session.clear()

        // Get a proxy without initializing it
        Location proxyLocation = Location.load(location.id)

        expect:
        proxyHandler.isInitialized(proxyLocation) == false
        !Hibernate.isInitialized(proxyLocation)
    }

    void "test isInitialized for a proxied object after initialization"() {
        given:
        Location location = new Location(name: "Test Location").save(flush: true)
        manager.session.clear()

        Location proxyLocation = Location.load(location.id)
        proxyLocation.name // Accessing a property to initialize the proxy

        expect:
        proxyHandler.isInitialized(proxyLocation) == true
        Hibernate.isInitialized(proxyLocation)
    }

    void "test unwrap for a non-proxied object"() {
        given:
        Location location = new Location(name: "Test Location").save(flush: true)

        expect:
        proxyHandler.unwrap(location) == location
    }

    void "test unwrap for a proxied object"() {
        given:
        Location location = new Location(name: "Test Location").save(flush: true)
        manager.session.clear()

        Location proxyLocation = Location.load(location.id)
        def unwrapped = proxyHandler.unwrap(proxyLocation)

        expect:
        unwrapped != proxyLocation
        unwrapped.name == location.name // After unwrap, it should be initialized and contain original data
    }
}

@Entity
class Location {
    Long id
    Long version
    String name
}

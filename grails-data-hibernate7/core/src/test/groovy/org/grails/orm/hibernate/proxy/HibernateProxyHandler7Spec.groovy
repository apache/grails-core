package org.grails.orm.hibernate.proxy

import org.hibernate.proxy.HibernateProxy
import grails.gorm.specs.HibernateGormDatastoreSpec
import grails.persistence.Entity
import org.apache.grails.data.testing.tck.domains.Location
import org.hibernate.Hibernate
import spock.lang.Shared
import org.grails.datastore.gorm.proxy.GroovyProxyFactory

class HibernateProxyHandler7Spec extends HibernateGormDatastoreSpec {

    @Shared HibernateProxyHandler proxyHandler = new HibernateProxyHandler()

    void setupSpec() {
        manager.addAllDomainClasses([Location,UpdatePerson,UpdatePet,UpdatePetType ])
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

    void 'Test update entity with association proxies'() {
        given:
        def person = new UpdatePerson(firstName: 'Bob', lastName: 'Builder')
        def petType = new UpdatePetType(name: 'snake')
        def pet = new UpdatePet(name: 'Fred', type: petType, owner: person)
        person.addToPets(pet)
        person.save(flush: true)
        manager.session.clear()

        when:
        person = UpdatePerson.get(person.id)
        person.firstName = 'changed'
        person.save(flush: true)
        manager.session.clear()
        person = UpdatePerson.get(person.id)
        def personPet = person.pets.iterator().next()

        then:
        person.firstName == 'changed'
        personPet.name == 'Fred'
        personPet.id == pet.id
        personPet.owner.id == person.id
        personPet.type.name == 'snake'
        personPet.type.id == petType.id
    }

    void 'Test update unidirectional oneToMany with proxy'() {
        given:
        Long personId
        Long petTypeId

        // Step 1: Persist initial data
        UpdatePerson.withNewSession { gormSession ->
            UpdatePerson.withTransaction {
                personId = new UpdatePerson(firstName: 'Bob', lastName: 'Builder').save(flush: true).id
                petTypeId = new UpdatePetType(name: 'snake').save(flush: true).id
            }
        }

        when: "Re-loading in a new session to test proxy behavior"
        UpdatePerson.withNewSession { gormSession ->
            UpdatePerson.withTransaction {
                def person = UpdatePerson.get(personId)
                def hibernateSession = gormSession.getSessionFactory().getCurrentSession()

                // Use the native Hibernate session to ensure a proxy
                def petTypeProxy = hibernateSession.getReference(UpdatePetType, petTypeId)

                // Verify it is indeed a proxy
                assert proxyHandler.isProxy(petTypeProxy)

                // Create a new pet with the proxy type
                def pet = new UpdatePet(name: 'Fred', type: petTypeProxy, owner: person)
                person.addToPets(pet)
                person.save(flush: true)
            }
        }

        then: "Verify the association was persisted correctly"
        def result = UpdatePerson.withNewSession {
            def person = UpdatePerson.get(personId)
            return [firstName: person.firstName, petsSize: person.pets.size(), petName: person.pets.first()?.name, petTypeId: person.pets.first()?.type?.id]
        }

        result.firstName == 'Bob'
        result.petsSize == 1
        result.petName == 'Fred'
        result.petTypeId == petTypeId
    }
}

@Entity
class UpdatePerson implements Serializable {
    Long id
    String firstName
    String lastName
    Set<UpdatePet> pets = []
    static hasMany = [pets: UpdatePet]
}

@Entity
class UpdatePet implements Serializable {
    Long id
    String name
    UpdatePetType type
    UpdatePerson owner
    static belongsTo = [owner: UpdatePerson]
}

@Entity
class UpdatePetType implements Serializable {
    Long id
    String name
}

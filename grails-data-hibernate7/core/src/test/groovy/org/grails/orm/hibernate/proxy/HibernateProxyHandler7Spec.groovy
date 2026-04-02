/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

package org.grails.orm.hibernate.proxy

import org.hibernate.proxy.HibernateProxy
import grails.gorm.specs.HibernateGormDatastoreSpec
import org.apache.grails.data.testing.tck.domains.Location
import org.apache.grails.data.testing.tck.domains.Person
import org.apache.grails.data.testing.tck.domains.Pet
import org.grails.datastore.gorm.proxy.GroovyProxyFactory
import org.hibernate.Hibernate
import spock.lang.Shared

/**
 * Integration tests for Hibernate 7 Proxy Handler covering all ProxyHandler
 * and ProxyFactory contract methods. Matches test coverage from the
 * Hibernate 5 HibernateProxyHandler5Spec.
 */
class HibernateProxyHandler7Spec extends HibernateGormDatastoreSpec {

    @Shared HibernateProxyHandler proxyHandler = new HibernateProxyHandler()

    void setupSpec() {
        manager.addAllDomainClasses([Location, Person, Pet])
    }

    void "test isInitialized for native Hibernate proxy"() {
        given:
        Long savedId = 1L
        Location.withTransaction {
            savedId = new Location(name: "Test Location", code: "TL1").save(flush: true).id
        }
        manager.session.clear()
        manager.hibernateSession.clear()

        when:
        def proxy = manager.hibernateSession.getReference(Location, savedId)

        then:
        proxy instanceof HibernateProxy
        !proxyHandler.isInitialized(proxy)

        when:
        proxy.name // access property

        then:
        proxyHandler.isInitialized(proxy)
    }

    void "test unwrap for a native Hibernate proxy"() {
        given:
        Long savedId = 1L
        Location.withTransaction {
            savedId = new Location(name: "Test Location").save(flush: true).id
        }
        manager.session.clear()
        manager.hibernateSession.clear()

        when:
        def proxy = manager.hibernateSession.getReference(Location, savedId)
        def unwrapped = proxyHandler.unwrap(proxy)

        then:
        unwrapped != proxy
        unwrapped instanceof Location
        unwrapped.name == "Test Location"
    }

    void "test getIdentifier"() {
        given:
        Long savedId = 1L
        Location.withTransaction {
            savedId = new Location(name: "Test").save(flush: true).id
        }
        manager.session.clear()
        manager.hibernateSession.clear()

        when:
        def proxy = manager.hibernateSession.getReference(Location, savedId)

        then:
        proxyHandler.getIdentifier(proxy) == savedId
    }

    void "test createProxy"() {
        given:
        Long savedId = 1L
        Location.withTransaction {
            savedId = new Location(name: "Test").save(flush: true).id
        }
        manager.session.clear()
        manager.hibernateSession.clear()

        when:
        Location proxy = proxyHandler.createProxy(manager.session, Location, savedId)

        then:
        proxy != null
        proxy instanceof HibernateProxy
        proxyHandler.getIdentifier(proxy) == savedId
        !proxyHandler.isInitialized(proxy)
    }

    void "test getAssociationProxy"() {
        given:
        Long petId
        Person.withTransaction {
            Person p = new Person(firstName: "Homer", lastName: "Simpson").save()
            petId = new Pet(name: "Santa's Little Helper", owner: p).save(flush: true).id
        }
        manager.session.clear()
        manager.hibernateSession.clear()

        when:
        Pet loadedPet = Pet.get(petId)
        def ownerProxy = proxyHandler.getAssociationProxy(loadedPet, 'owner')

        then:
        ownerProxy instanceof HibernateProxy
        !proxyHandler.isInitialized(ownerProxy)
    }

    void "test isInitialized for a non-proxied object"() {
        given:
        Location location = new Location(name: "Test Location").save(flush: true)

        expect:
        proxyHandler.isInitialized(location)
    }

    void "test isInitialized for a Groovy proxy before initialization"() {
        given:
        def originalFactory = manager.session.mappingContext.proxyFactory
        manager.session.mappingContext.proxyFactory = new GroovyProxyFactory()
        Location location = new Location(name: "Test Location").save(flush: true)
        manager.session.clear()
        manager.hibernateSession.clear()

        Location proxyLocation = Location.proxy(location.id)

        expect:
        !proxyHandler.isInitialized(proxyLocation)

        cleanup:
        manager.session.mappingContext.proxyFactory = originalFactory
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

    void "test isInitialized for null"() {
        expect:
        !proxyHandler.isInitialized(null)
    }

    void "test isInitialized for a persistent collection"() {
        given:
        Long personId
        Person.withTransaction {
            Person p = new Person(firstName: "Homer", lastName: "Simpson").save()
            new Pet(name: "Santa's Little Helper", owner: p).save(flush: true)
            personId = p.id
        }
        manager.session.clear()
        manager.hibernateSession.clear()

        Person loaded = Person.get(personId)
        def pets = loaded.pets

        expect:
        !proxyHandler.isInitialized(pets)

        when:
        pets.size()

        then:
        proxyHandler.isInitialized(pets)
    }

    void "test isInitialized for association name"() {
        given:
        Long personId
        Person.withTransaction {
            Person p = new Person(firstName: "Homer", lastName: "Simpson").save()
            new Pet(name: "Santa's Little Helper", owner: p).save(flush: true)
            personId = p.id
        }
        manager.session.clear()
        manager.hibernateSession.clear()

        Person loaded = Person.get(personId)

        expect:
        !proxyHandler.isInitialized(loaded, 'pets')

        when:
        loaded.pets.size()

        then:
        proxyHandler.isInitialized(loaded, 'pets')
    }

    void "test isInitialized for association name with null object"() {
        expect:
        !proxyHandler.isInitialized(null, 'any')
    }

    void "test isProxy"() {
        given:
        Long savedId
        Location.withTransaction {
            savedId = new Location(name: "Test").save(flush: true).id
        }
        manager.session.clear()
        manager.hibernateSession.clear()

        def proxy = manager.hibernateSession.getReference(Location, savedId)

        expect:
        proxyHandler.isProxy(proxy)
        !proxyHandler.isProxy(new Location(name: "Not a proxy"))
        !proxyHandler.isProxy(null)
    }

    void "test getProxiedClass"() {
        given:
        Long savedId
        Location.withTransaction {
            savedId = new Location(name: "Test").save(flush: true).id
        }
        manager.session.clear()
        manager.hibernateSession.clear()

        def proxy = manager.hibernateSession.getReference(Location, savedId)
        Location location = new Location(name: "Not a proxy")

        expect:
        proxyHandler.getProxiedClass(proxy) == Location
        proxyHandler.getProxiedClass(location) == Location
    }

    void "test initialize"() {
        given:
        Long savedId
        Location.withTransaction {
            savedId = new Location(name: "Test").save(flush: true).id
        }
        manager.session.clear()
        manager.hibernateSession.clear()

        def proxy = manager.hibernateSession.getReference(Location, savedId)

        expect:
        !Hibernate.isInitialized(proxy)

        when:
        proxyHandler.initialize(proxy)

        then:
        Hibernate.isInitialized(proxy)
    }

    void "test unwrap for persistent collection"() {
        given:
        Long personId
        Person.withTransaction {
            Person p = new Person(firstName: "Homer", lastName: "Simpson").save()
            new Pet(name: "Santa's Little Helper", owner: p).save(flush: true)
            personId = p.id
        }
        manager.session.clear()
        manager.hibernateSession.clear()

        Person loaded = Person.get(personId)
        def pets = loaded.pets

        expect:
        !proxyHandler.isInitialized(pets)

        when:
        def unwrapped = proxyHandler.unwrap(pets)

        then:
        unwrapped == pets
        proxyHandler.isInitialized(pets)
    }

    void "test createProxy with AssociationQueryExecutor"() {
        when:
        proxyHandler.createProxy(manager.session, null, null)

        then:
        thrown(UnsupportedOperationException)
    }

    void "test createProxy throws IllegalStateException if native interface is not GrailsHibernateTemplate"() {
        given:
        def mockSession = Stub(org.grails.datastore.mapping.core.Session)
        mockSession.getNativeInterface() >> "not a template"

        when:
        proxyHandler.createProxy(mockSession, Location, 1L)

        then:
        thrown(IllegalStateException)
    }

    void "test deprecated unwrapProxy and unwrapIfProxy"() {
        given:
        Long savedId
        Location.withTransaction {
            savedId = new Location(name: "Test").save(flush: true).id
        }
        manager.session.clear()
        manager.hibernateSession.clear()

        def proxy = manager.hibernateSession.getReference(Location, savedId)
        Location location = new Location(name: "Not a proxy")

        expect:
        proxyHandler.unwrapProxy(proxy) != proxy
        proxyHandler.unwrapIfProxy(proxy) != proxy
        proxyHandler.unwrapProxy(location) == location
        proxyHandler.unwrapIfProxy(location) == location
    }

    void "test getAssociationProxy returns null for non-association property"() {
        given:
        Long petId
        Person.withTransaction {
            Person p = new Person(firstName: "Homer", lastName: "Simpson").save()
            petId = new Pet(name: "Santa's Little Helper", owner: p).save(flush: true).id
        }
        manager.session.clear()
        manager.hibernateSession.clear()

        Pet loadedPet = Pet.get(petId)

        expect:
        proxyHandler.getAssociationProxy(loadedPet, 'name') == null
    }

    void "test getIdentifier for non-proxy returns null"() {
        given:
        Location location = new Location(name: "Test")

        expect:
        proxyHandler.getIdentifier(location) == null
    }
}

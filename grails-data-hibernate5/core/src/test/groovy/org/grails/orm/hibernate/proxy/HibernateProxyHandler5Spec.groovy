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

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import grails.gorm.tests.HibernateGormDatastoreSpec
import org.apache.grails.data.hibernate5.core.GrailsDataHibernate5TckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import org.apache.grails.data.testing.tck.domains.Location
import org.apache.grails.data.testing.tck.domains.Person
import org.apache.grails.data.testing.tck.domains.Pet
import org.hibernate.Hibernate
import spock.lang.Shared
import org.grails.datastore.gorm.proxy.GroovyProxyFactory

class HibernateProxyHandler5Spec extends  GrailsDataTckSpec<GrailsDataHibernate5TckManager> {

    private static final Logger LOG = LoggerFactory.getLogger(HibernateProxyHandler5Spec.class)
    @Shared HibernateProxyHandler proxyHandler = new HibernateProxyHandler()

    void setupSpec() {
        manager.registerDomainClasses(Location, Person, Pet)
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

    void "test isInitialized for null"() {
        expect:
        proxyHandler.isInitialized(null) == false
    }

    void "test isInitialized for a persistent collection"() {
        given:
        Person p = new Person(firstName: "Homer", lastName: "Simpson").save(flush: true)
        new Pet(name: "Santa's Little Helper", owner: p).save(flush: true)
        manager.session.clear()
        manager.hibernateSession.clear()

        Person loaded = Person.get(p.id)
        def pets = loaded.pets

        expect:
        proxyHandler.isInitialized(pets) == false

        when:
        pets.size()

        then:
        proxyHandler.isInitialized(pets) == true
    }

    void "test isInitialized for association name"() {
        given:
        Person p = new Person(firstName: "Homer", lastName: "Simpson").save(flush: true)
        new Pet(name: "Santa's Little Helper", owner: p).save(flush: true)
        manager.session.clear()
        manager.hibernateSession.clear()

        Person loaded = Person.get(p.id)

        expect:
        proxyHandler.isInitialized(loaded, 'pets') == false

        when:
        loaded.pets.size()

        then:
        proxyHandler.isInitialized(loaded, 'pets') == true
    }

    void "test isProxy"() {
        given:
        Location location = new Location(name: "Test").save(flush: true)
        manager.session.clear()
        manager.hibernateSession.clear()

        Location proxy = Location.proxy(location.id)

        expect:
        proxyHandler.isProxy(proxy) == true
        proxyHandler.isProxy(location) == false
        proxyHandler.isProxy(null) == false
    }

    void "test getIdentifier"() {
        given:
        Location location = new Location(name: "Test").save(flush: true)
        manager.session.clear()
        manager.hibernateSession.clear()

        Location proxy = Location.proxy(location.id)

        expect:
        proxyHandler.getIdentifier(proxy) == location.id
        proxyHandler.getIdentifier(location) == null
    }

    void "test getProxiedClass"() {
        given:
        Location location = new Location(name: "Test").save(flush: true)
        manager.session.clear()
        manager.hibernateSession.clear()

        Location proxy = Location.proxy(location.id)

        expect:
        proxyHandler.getProxiedClass(proxy) == Location
        proxyHandler.getProxiedClass(location) == Location
    }

    void "test initialize"() {
        given:
        Location location = new Location(name: "Test").save(flush: true)
        manager.session.clear()
        manager.hibernateSession.clear()

        Location proxy = Location.proxy(location.id)

        expect:
        !Hibernate.isInitialized(proxy)

        when:
        proxyHandler.initialize(proxy)

        then:
        Hibernate.isInitialized(proxy)
    }

    void "test unwrap for persistent collection"() {
        given:
        Person p = new Person(firstName: "Homer", lastName: "Simpson").save(flush: true)
        new Pet(name: "Santa's Little Helper", owner: p).save(flush: true)
        manager.session.clear()
        manager.hibernateSession.clear()

        Person loaded = Person.get(p.id)
        def pets = loaded.pets

        expect:
        !proxyHandler.isInitialized(pets)

        when:
        def unwrapped = proxyHandler.unwrap(pets)

        then:
        unwrapped == pets
        proxyHandler.isInitialized(pets)
    }

    void "test isInitialized for association name with null object"() {
        expect:
        proxyHandler.isInitialized(null, 'any') == false
    }

    void "test createProxy"() {
        given:
        Location location = new Location(name: "Test").save(flush: true)
        manager.session.clear()
        manager.hibernateSession.clear()

        when:
        Location proxy = proxyHandler.createProxy(manager.session, Location, location.id)

        then:
        proxy != null
        proxy instanceof org.hibernate.proxy.HibernateProxy
        proxy.id == location.id
        !Hibernate.isInitialized(proxy)
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
        Location location = new Location(name: "Test").save(flush: true)
        manager.session.clear()
        manager.hibernateSession.clear()

        Location proxy = Location.proxy(location.id)

        expect:
        proxyHandler.unwrapProxy(proxy) != proxy
        proxyHandler.unwrapIfProxy(proxy) != proxy
        proxyHandler.unwrapProxy(location) == location
        proxyHandler.unwrapIfProxy(location) == location
    }

    void "test getAssociationProxy"() {
        given:
        Person p = new Person(firstName: "Homer", lastName: "Simpson").save(flush: true)
        Pet pet = new Pet(name: "Santa's Little Helper", owner: p).save(flush: true)
        manager.session.clear()
        manager.hibernateSession.clear()

        Pet loadedPet = Pet.get(pet.id)

        expect:
        proxyHandler.getAssociationProxy(loadedPet, 'owner') instanceof org.hibernate.proxy.HibernateProxy
        proxyHandler.getAssociationProxy(loadedPet, 'name') == null
    }
}

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
import org.hibernate.Hibernate
import spock.lang.Shared

/**
 * Simplified integration test for Hibernate 7 Proxy Handler.
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
}

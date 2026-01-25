/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.grails.data.testing.tck.tests

import grails.persistence.Entity
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec

/**
 * @author graemerocher
 */
class UpdateWithProxyPresentSpec extends GrailsDataTckSpec {

    void setupSpec() {
        manager.addAllDomainClasses([UpdatePet, UpdatePerson, UpdatePetType])
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

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

import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import org.apache.grails.data.testing.tck.domains.Child
import org.apache.grails.data.testing.tck.domains.Parent
import org.apache.grails.data.testing.tck.domains.Person
import org.apache.grails.data.testing.tck.domains.Pet
import org.apache.grails.data.testing.tck.domains.PetType

/**
 * @author graemerocher
 */
class UpdateWithProxyPresentSpec extends GrailsDataTckSpec {

    void setupSpec() {
        manager.addAllDomainClasses([Pet, Person, PetType, Parent, Child])
    }

    void 'Test update entity with association proxies'() {
        given:
        def person = new Person(firstName: 'Bob', lastName: 'Builder')
        def petType = new PetType(name: 'snake')
        def pet = new Pet(name: 'Fred', type: petType, owner: person)
        person.addToPets(pet)
        person.save(flush: true)
        manager.session.clear()

        when:
        person = Person.get(person.id)
        person.firstName = 'changed'
        person.save(flush: true)
        manager.session.clear()
        person = Person.get(person.id)
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
        Long parentId
        Long childId

        // Step 1: Persist initial data
        Parent.withTransaction {
            parentId = new Parent(name: 'Bob').save(flush: true).id
            childId = new Child(name: 'Bill').save(flush: true).id
        }
        manager.session.clear()

        when: "Re-loading in a new transaction to test proxy behavior"
        Parent.withTransaction {
            def parent = Parent.get(parentId)

            // Use the native Hibernate session to ensure a clean, uninitialized proxy
            // GORM's .load() can sometimes be 'too helpful' and fetch the data
            def child = manager.hibernateSession.getReference(Child, childId)

            // Verify it is indeed a proxy before we use it
            assert proxyHandler.isProxy(child)
            assert !proxyHandler.isInitialized(child)

            // Add the proxy to the parent
            parent.addToChildren(child)
            parent.save(flush: true)
        }

        // Clear to ensure we fetch from DB in the next step
        manager.session.clear()

        then: "Verify the association was persisted correctly"
        Parent.withTransaction {
            def parent = Parent.get(parentId)
            assert parent.name == 'Bob'
            assert parent.children.size() == 1

            def child = parent.children.first()
            assert child.name == 'Bill'
        }
    }
}

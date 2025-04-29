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

import org.apache.grails.data.testing.tck.domains.Child
import org.apache.grails.data.testing.tck.domains.Parent
import org.apache.grails.data.testing.tck.domains.Person
import org.apache.grails.data.testing.tck.domains.Pet
import org.apache.grails.data.testing.tck.domains.PetType
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec

/**
 * @author graemerocher
 */
class UpdateWithProxyPresentSpec extends GrailsDataTckSpec {

    void setupSpec() {
        manager.domainClasses.addAll([Pet, Person, PetType, Parent, Child])
    }

    void "Test update entity with association proxies"() {
        given:
        def person = new Person(firstName: "Bob", lastName: "Builder")
        def petType = new PetType(name: "snake")
        def pet = new Pet(name: "Fred", type: petType, owner: person)
        person.addToPets(pet)
        person.save(flush: true)
        manager.session.clear()

        when:
        person = Person.get(person.id)
        person.firstName = "changed"
        person.save(flush: true)
        manager.session.clear()
        person = Person.get(person.id)
        def personPet = person.pets.iterator().next()

        then:
        person.firstName == "changed"
        personPet.name == "Fred"
        personPet.id == pet.id
        personPet.owner.id == person.id
        personPet.type.name == 'snake'
        personPet.type.id == petType.id
    }

    void "Test update unidirectional oneToMany with proxy"() {
        given:
        def parent = new Parent(name: "Bob").save(flush: true)
        def child = new Child(name: "Bill").save(flush: true)
        manager.session.clear()

        when:
        parent = Parent.get(parent.id)
        child = Child.load(child.id) // make sure we've got a proxy.
        then:
        manager.session.mappingContext.proxyFactory.isProxy(child) == true

        when:
        parent.addToChildren(child)
        parent.save(flush: true)
        manager.session.clear()
        parent = Parent.get(parent.id)

        then:
        parent.name == 'Bob'
        parent.children.size() == 1

        when:
        child = parent.children.first()

        then:
        child.name == "Bill"
    }
}

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

import org.apache.grails.data.testing.tck.domains.Country
import org.apache.grails.data.testing.tck.domains.Person
import org.apache.grails.data.testing.tck.domains.Pet
import org.apache.grails.data.testing.tck.domains.PetType
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec

/**
 * @author graemerocher
 */
class OneToManySpec extends GrailsDataTckSpec {

    void setupSpec() {
        manager.addAllDomainClasses([Country, Person, Pet, PetType])
    }

    void "test save and return unidirectional one to many"() {
        given:
        Person p = new Person(firstName: "Fred", lastName: "Flinstone")
        Country c = new Country(name: "Dinoville")
                .addToResidents(p)
                .save(flush: true)

        manager.session.clear()

        when:
        c = Country.findByName("Dinoville")

        then:
        c != null
        c.residents != null
        c.residents.size() == 1
        c.residents.every { it instanceof Person } == true

        when:
        c.addToResidents(new Person(firstName: "Barney", lastName: "Rubble"))
        c.save(flush: true)
        manager.session.clear()
        c = Country.findByName("Dinoville")

        then:
        c != null
        c.residents != null
        c.residents.size() == 2
        c.residents.every { it instanceof Person } == true
    }

    void "test save and return bidirectional one to many"() {
        given:
        Person p = new Person(firstName: "Fred", lastName: "Flinstone")
        p.addToPets(new Pet(name: "Dino", type: new PetType(name: "Dinosaur")))
        p.save(flush: true)

        new Person(firstName: "Barney", lastName: "Rubble")
                .addToPets(new Pet(name: "T Rex", type: new PetType(name: "Dinosaur")))
                .addToPets(new Pet(name: "Stego", type: new PetType(name: "Dinosaur")))
                .save(flush: true)

        manager.session.clear()

        when:
        p = Person.findByFirstName("Fred")

        then:
        p != null
        p.pets != null
        p.pets.size() == 1
        def pet = p.pets.iterator().next()
        pet instanceof Pet
        pet.name == 'Dino'
        pet.type != null
        pet.type.name == 'Dinosaur'

        when:
        p.addToPets(new Pet(name: "Rex", type: new PetType(name: "Dinosaur")))
        p.save(flush: true)
        manager.session.clear()
        p = Person.findByFirstName("Fred")

        then:
        p != null
        p.pets != null
        p.pets.size() == 2
        p.pets.every { it instanceof Pet } == true
    }

    void "test update inverse side of bidirectional one to many collection"() {
        given:
        Person p = new Person(firstName: "Fred", lastName: "Flinstone").save()
        new Pet(name: "Dino", type: new PetType(name: "Dinosaur"), owner: p).save()
        Person p2 = new Person(firstName: "Barney", lastName: "Rubble").save()
        new Pet(name: "T Rex", type: new PetType(name: "Dinosaur"), owner: p2).save()
        new Pet(name: "Stego", type: new PetType(name: "Dinosaur"), owner: p2).save(flush: true)

        manager.session.clear()

        when:
        p = Person.findByFirstName("Fred")

        then:
        p != null
        p.pets != null
        p.pets.size() == 1
        def pet = p.pets.iterator().next()
        pet instanceof Pet
        pet.name == 'Dino'
        pet.type != null
        pet.type.name == 'Dinosaur'
    }

    void "test update inverse side of bidirectional one to many happens before flushing the session"() {

        if (manager.session.datastore.getClass().name.contains('Hibernate')) {
            return
        }

        given:
        Person person = new Person(firstName: "Fred", lastName: "Flinstone").save()
        Pet dino = new Pet(name: "Dino", type: new PetType(name: "Dinosaur"), owner: person).save()
        Pet trex = new Pet(name: "Trex", type: new PetType(name: "Dinosaur"), owner: person).save()

        expect:
        dino.owner == person
        trex.owner == person
        person.pets.size() == 2

        when:
        manager.session.flush()
        manager.session.clear()
        person = Person.findByLastName('Flinstone')

        then:
        person
        person.pets.size() == 2
    }

    void "Test persist of association with proxy"() {
        given: "A domain model with a many-to-one"
        def person = new Person(firstName: "Fred", lastName: "Flintstone")
        person.save(flush: true)
        manager.session.clear()
        def pet = new Pet(name: "Dino", owner: Person.load(person.id))
        pet.save(flush: true)
        manager.session.clear()

        when: "The association is queried"
        pet = Pet.findByName("Dino")

        then: "The domain model is valid"
        pet != null
        pet.name == "Dino"
        pet.owner != null
        pet.owner.firstName == "Fred"
    }
}

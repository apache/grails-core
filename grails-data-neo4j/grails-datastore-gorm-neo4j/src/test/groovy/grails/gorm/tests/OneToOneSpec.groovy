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

package grails.gorm.tests

import org.grails.datastore.mapping.model.types.OneToOne

/**
 * Created by graemerocher on 14/03/2017.
 */
class OneToOneSpec extends GormDatastoreSpec {

    def "Test persist and retrieve unidirectional many-to-one"() {
        given:"A domain model with a many-to-one"
        def person = new Person(firstName:"Fred", lastName: "Flintstone")
        def pet = new Pet(name:"Dino", owner:person)
        person.save()
        pet.save(flush:true)
        session.clear()

        when:"The association is queried"
        pet = Pet.findByName("Dino")

        then:"The domain model is valid"
        pet != null
        pet.name == "Dino"
        pet.ownerId == person.id
        pet.owner.firstName == "Fred"
    }

    def "Test persist and retrieve one-to-one with inverse key"() {
        given:"A domain model with a one-to-one"
        def face = new Face(name:"Joe")
        def nose = new Nose(hasFreckles: true, face:face)
        face.nose = nose
        face.save(flush:true)
        session.clear()

        when:"The association is queried"
        face = Face.get(face.id)
        def association = Face.gormPersistentEntity.getPropertyByName('nose')
        then:"The domain model is valid"
        association instanceof OneToOne
        association.bidirectional
        association.associatedEntity.javaClass == Nose
        face != null
        face.noseId == face.id
        face.nose != null
        face.nose.hasFreckles == true

        when:"The inverse association is queried"
        session.clear()
        nose = Nose.get(nose.id)

        then:"The domain model is valid"
        nose != null
        nose.hasFreckles == true
        nose.face != null
        nose.face.name == "Joe"
    }
}

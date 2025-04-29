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
package org.grails.datastore.gorm

import org.apache.grails.data.testing.tck.domains.Face
import org.apache.grails.data.testing.tck.domains.Nose
import org.apache.grails.data.testing.tck.domains.Pet
import org.apache.grails.data.simple.core.GrailsDataCoreTckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import org.grails.datastore.mapping.proxy.EntityProxy

/**
 * @author Graeme Rocher
 */
class OneToOneWithProxiesSpec extends GrailsDataTckSpec<GrailsDataCoreTckManager> {

    def "Test persist and retrieve unidirectional many-to-one"() {
        given: "A domain model with a many-to-one"
        def person = new org.apache.grails.data.testing.tck.domains.Person(firstName: "Fred", lastName: "Flintstone")
        def pet = new Pet(name: "Dino", owner: person)
        person.save()
        pet.save(flush: true)
        manager.session.clear()

        when: "The association is queried"
        pet = Pet.findByName("Dino")

        then: "The domain model is valid"
        pet != null
        pet.name == "Dino"
        pet.owner instanceof EntityProxy
        pet.ownerId == person.id
        !pet.owner.isInitialized()
        pet.owner.firstName == "Fred"
        pet.owner.isInitialized()
    }

    def "Test persist and retrieve one-to-one with inverse key"() {
        given: "A domain model with a one-to-one"
        def face = new Face(name: "Joe")
        def nose = new Nose(hasFreckles: true, face: face)
        face.nose = nose
        face.save(flush: true)
        manager.session.clear()

        when: "The association is queried"
        face = Face.get(face.id)

        then: "The domain model is valid"

        face != null
        face.noseId == nose.id
        face.nose != null
        face.nose.hasFreckles == true

        when: "The inverse association is queried"
        manager.session.clear()
        nose = Nose.get(nose.id)

        then: "The domain model is valid"
        nose != null
        nose.hasFreckles == true
        nose.face != null
        nose.face.name == "Joe"
    }
}
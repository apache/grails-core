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
import org.apache.grails.data.testing.tck.domains.Face
import org.apache.grails.data.testing.tck.domains.Nose
import org.apache.grails.data.testing.tck.domains.Person
import org.apache.grails.data.testing.tck.domains.Pet
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import org.grails.datastore.mapping.model.types.OneToOne

class OneToOneSpec extends GrailsDataTckSpec {

    void setupSpec() {
        manager.domainClasses.addAll([Face, Nose, Person, Pet])
    }

    def "Test persist and retrieve unidirectional many-to-one"() {
        given: "A domain model with a many-to-one"
        def oneToManyEntity = new OwnerEntity()
        def manyToOneEntity = new OwnedEntity(oneToMany: oneToManyEntity)
        oneToManyEntity.save()
        manyToOneEntity.save(flush: true)
        manager.session.clear()

        when: "The association is queried"
        manyToOneEntity = OwnedEntity.list()[0]

        then: "The domain model is valid"
        manyToOneEntity != null
        manyToOneEntity.oneToMany.id == oneToManyEntity.id
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
        def association = Face.gormPersistentEntity.getPropertyByName('nose')

        then: "The domain model is valid"
        association instanceof OneToOne
        association.bidirectional
        association.associatedEntity.javaClass == Nose
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

@Entity
class OwnerEntity {

}

@Entity
class OwnedEntity {
    OwnerEntity oneToMany

    static belongsTo = [oneToMany: OwnerEntity]

}

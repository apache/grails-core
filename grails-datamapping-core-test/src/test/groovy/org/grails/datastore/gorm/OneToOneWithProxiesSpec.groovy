package org.grails.datastore.gorm

import grails.gorm.tck.Face
import grails.gorm.tests.GormDatastoreSpec
import grails.gorm.tck.Nose
import grails.gorm.tck.Pet
import org.grails.datastore.mapping.proxy.EntityProxy

/**
 * @author Graeme Rocher
 */
class OneToOneWithProxiesSpec  extends GormDatastoreSpec {

    def "Test persist and retrieve unidirectional many-to-one"() {
        given:"A domain model with a many-to-one"
            def person = new grails.gorm.tck.Person(firstName:"Fred", lastName: "Flintstone")
            def pet = new Pet(name:"Dino", owner:person)
            person.save()
            pet.save(flush:true)
            session.clear()

        when:"The association is queried"
            pet = Pet.findByName("Dino")

        then:"The domain model is valid"
            pet != null
            pet.name == "Dino"
            pet.owner instanceof EntityProxy
            pet.ownerId == person.id
            !pet.owner.isInitialized()
            pet.owner.firstName == "Fred"
            pet.owner.isInitialized()
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

        then:"The domain model is valid"

            face != null
            face.noseId == nose.id
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
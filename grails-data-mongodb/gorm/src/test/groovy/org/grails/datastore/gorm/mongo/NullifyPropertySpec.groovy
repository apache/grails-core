package org.grails.datastore.gorm.mongo

import grails.gorm.tests.Pet
import grails.gorm.tests.GormDatastoreSpec
import grails.gorm.tests.Person

/**
 * Tests the nullification of properties
 */
class NullifyPropertySpec extends GormDatastoreSpec {

    void "Test nullify basic property"() {
        given:"A an entity with a basic property"

            def pet = new Pet(name:"Spike")
            pet.save flush:true
            session.clear()
            pet = Pet.get(pet.id)

        when:"A property is nulled"
            pet.name = null
            pet.save flush:true, validate:false
            session.clear()
            pet = Pet.get(pet.id)

        then:"It is null when retrieved"
            pet != null
            pet.name == null
    }

    void "Test nullify to-one association"() {
      given:"A an entity with a to-one association"

            def bob = new Person(firstName:"Bob", lastName:"Builder")
            bob.save()
            def pet = new Pet(name:"Spike", owner:bob)
            pet.save flush:true
            session.clear()
            pet = Pet.get(pet.id)
            assert pet.owner != null

        when:"A property is nulled"
            pet.owner = null
            pet.save flush:true
            session.clear()
            pet = Pet.get(pet.id)

        then:"It is null when retrieved"
            pet != null
            pet.owner == null
    }
}

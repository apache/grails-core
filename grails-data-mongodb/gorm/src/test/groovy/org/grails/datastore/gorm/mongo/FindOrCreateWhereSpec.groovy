package org.grails.datastore.gorm.mongo

import grails.gorm.tests.Pet
import grails.gorm.tests.GormDatastoreSpec

class FindOrCreateWhereSpec extends GormDatastoreSpec {

    void "Test findOrCreateWhere with association"() {
        given:"A domain class with a bidirectional one-to-many"
            def person = new Person(firstName: "Fred", lastName: "Flinstone")
            person.save(flush:true)
            session.clear()
            person = Person.load(person.id)

        when:"findByOrCreateWhere is called"
            def pet = Pet.findOrCreateWhere(name:"Dino", owner:person)

        then:"The instance was created"
            pet != null
    }

    @Override
    List getDomainClasses() {
        [Person, Pet]
    }
}

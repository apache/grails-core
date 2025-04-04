package org.grails.datastore.gorm.mongo

import grails.gorm.tests.Pet
import grails.gorm.tests.Person
import grails.gorm.tck.PetType
import grails.gorm.tests.GormDatastoreSpec

/**
 * @author Graeme Rocher
 */
class GetAllSpec extends GormDatastoreSpec {

    void "test that 'null' returns null"() {
        expect:
        Pet.get('null') == null
    }

    void "Test the getAll method works with no arguments"() {
        given:"some sample data"
            createPets()

        when:"The getAll method is used to retrieve all pets"
            def results = Pet.getAll()

        then:"The correct number of results is returned"
            results.size() == 3
            results.every { it instanceof Pet }
    }

    void "Test the getAll method works with arguments"() {
        given:"some sample data"
        createPets()

        when:"The getAll method is used to retrieve all pets"
        def results = Pet.getAll(1,3)

        then:"The correct number of results is returned"
        results.size() == 2
        results.any {  it.id == 1 }
        results.any {  it.id == 3 }
    }

    void "Test the getAll method works with a list argument"() {
        given:"some sample data"
        createPets()

        when:"The getAll method is used to retrieve all pets"
        def results = Pet.getAll([1,3])

        then:"The correct number of results is returned"
        results.size() == 2
        results.any {  it.id == 1 }
        results.any {  it.id == 3 }
    }

    void createPets() {
        def owner = new Person(firstName: "Fred", lastName: "Flintstone").save()
        assert owner != null
        def saurapod = new PetType(name: "Saurapod").save()
        def tyrannosaur = new PetType(name: "Tyrannosaur").save()
        def plesiosaur = new PetType(name: "Plesiosaur").save()
        assert new Pet(name: "Dino", owner: owner, type: saurapod, age: 5).save()
        assert new Pet(name: "T-rex", owner: owner, type: tyrannosaur, age: 4).save()
        assert new Pet(name: "Flipper", owner: owner, type: plesiosaur, age: 10).save(flush:true)
    }
}

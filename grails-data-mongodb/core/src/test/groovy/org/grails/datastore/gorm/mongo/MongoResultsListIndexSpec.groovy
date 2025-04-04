package org.grails.datastore.gorm.mongo

import grails.gorm.tests.GormDatastoreSpec
import grails.gorm.tests.Person

/**
 * @author Graeme Rocher
 */
class MongoResultsListIndexSpec extends GormDatastoreSpec{

    void "Test that indexing into results works with MongoDB"() {
        given:"Some people"
            createPeople()

        when:"We index into the results"
            def people = Person.list()
            def bart = people[2]
            def homer = people[0]
            def barney = people[4]

        then:"The results are correct"
            bart.firstName == "Bart"
            homer.firstName == "Homer"
            barney.firstName == "Barney"
            people[10] == null
            people.size() == 6

        when:"An index out of range is used"
            people.get(10)

        then:"An exception is thrown"
            thrown IndexOutOfBoundsException
    }


    protected void createPeople() {
        new Person(firstName: "Homer", lastName: "Simpson").save()
        new Person(firstName: "Marge", lastName: "Simpson").save()
        new Person(firstName: "Bart", lastName: "Simpson").save()
        new Person(firstName: "Lisa", lastName: "Simpson").save()
        new Person(firstName: "Barney", lastName: "Rubble").save()
        new Person(firstName: "Fred", lastName: "Flinstone").save()
    }
}

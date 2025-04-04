package org.grails.datastore.gorm.mongo

import com.mongodb.client.MongoClient
import grails.gorm.tests.GormDatastoreSpec
import grails.gorm.tests.Person

/**
 * @author Graeme Rocher
 */
class SwitchDatabaseAtRuntimeSpec extends GormDatastoreSpec {

    @Override
    List getDomainClasses() {
        [Person]
    }

    void setup() {
        ((MongoClient) session.nativeInterface).getDatabase('thesimpsons').drop()
    }


    void "Test switch database at runtime"() {
        given:"Some test data"
            createPeople()
            def initialDb = Person.DB.name

        when:"A count is issued"
            int total = Person.count()

        then:"The result is correct"
            total == 6

        when:"We switch to another database"
            def previous = Person.useDatabase("thesimpsons")

        then:"The count is now 0"
            Person.count() == 0
            Person.DB.name == 'thesimpsons'

        when:"We save a new person"
            new Person(firstName: "Maggie", lastName: "Simpson").save(flush:true)

        then:"The count is now 1"
            Person.count() == 1
            Person.DB.name == 'thesimpsons'


        when:"we switch back all is good"
            Person.useDatabase(previous)

        then:"the people count is 6 again"
            Person.count() == 6
            Person.DB.name == initialDb
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

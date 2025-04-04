package org.grails.datastore.gorm.mongo

import grails.gorm.tests.GormDatastoreSpec
import grails.gorm.tests.Person

class NegateInListSpec extends GormDatastoreSpec {

    void "Test negate in list query"() {
        given:"two people"
            def p1 = new Person(firstName:"Homer", lastName: "Simpson").save()
            new Person(firstName:"Bart", lastName: "Simpson").save(flush:true)

        when:"We query for people who don't have the given id"
            def results = Person.withCriteria {
                not {
                    inList('id', [p1.id])
                }
            }

        then:"The results are correct"
            results.size() == 1
            results[0].firstName == "Bart"
    }
}

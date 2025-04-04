package org.grails.datastore.gorm

import grails.gorm.tests.GormDatastoreSpec
import grails.gorm.tck.Person

class DistinctProjectionSpec extends GormDatastoreSpec {

    def "Test that using the distinct projection returns distinct results"()  {
        given:"Some people with the same last names"
            new Person(firstName: "Homer", lastName: "Simpson").save()
            new Person(firstName: "Bart", lastName: "Simpson").save()
            new Person(firstName: "Barney", lastName: "Rubble").save(flush:true)

        when:"We query with criteria for distinct surnames"
            def results = Person.withCriteria {
                projections {
                    distinct "lastName"
                }
            }.sort()

        then:"The correct results are returned"
            results == ['Rubble', 'Simpson']
    }
}

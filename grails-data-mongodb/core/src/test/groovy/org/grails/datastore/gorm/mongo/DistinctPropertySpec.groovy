package org.grails.datastore.gorm.mongo

import grails.gorm.tests.GormDatastoreSpec
import spock.lang.Issue
import grails.persistence.Entity
import org.bson.types.ObjectId

/**
 */
class DistinctPropertySpec extends GormDatastoreSpec{

    @Issue('GPMONGODB-220')
    def "Test that a distinct project returns distinct results"() {
        given:"Some domain classes with distinct and non-distinct properties"
            createSampleData()

        when:"We query for non-distinct results using criteria"
            def results = Student.createCriteria().list {
                projections {
                    property('classcode')
                }
            }

        then:"The results are correct"
            results.size() == 3
            results.contains("01")
            results.contains("02")

        when:"We query for distinct results using criteria"
            results = Student.createCriteria().list {
                projections {
                    distinct('classcode')
                }
            }

        then:"The results are correct"
            results.size() == 2
            results.contains("01")
            results.contains("02")
    }

    void createSampleData() {
        [[classcode:"01",studentcode:"0101"],
        [classcode:"01",studentcode:"0102"],
        [classcode:"02",studentcode:"0201"]].each {
            new Student(it).save(flush:true)
        }
    }

    @Override
    List getDomainClasses() {
        [Student]
    }
}
@Entity
class Student {
    ObjectId id
    String classcode
    String studentcode
}

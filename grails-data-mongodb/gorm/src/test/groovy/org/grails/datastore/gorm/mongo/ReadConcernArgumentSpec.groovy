package org.grails.datastore.gorm.mongo

import com.mongodb.MongoException
import com.mongodb.MongoQueryException
import com.mongodb.ReadConcern
import com.mongodb.WriteConcern
import grails.gorm.CriteriaBuilder
import grails.gorm.DetachedCriteria
import grails.gorm.tests.GormDatastoreSpec
import org.grails.datastore.mapping.mongo.MongoCodecSession
import spock.lang.Ignore
import spock.lang.IgnoreIf

/**
 * Created by graemerocher on 03/02/2017.
 */
// These tests require MongoDB 3.4+ to run, Travis only has MongoDB 3.0 support atm
@IgnoreIf({System.getenv('TRAVIS')})
class ReadConcernArgumentSpec extends GormDatastoreSpec {

    @Ignore
    void "Test that read concern work on criteria queries"() {
        when:"A criteria query is created with a hint"
        CriteriaBuilder c = grails.gorm.tests.Person.createCriteria()
        c.list {
            eq 'firstName', 'Bob'
            arguments readConcern: ReadConcern.MAJORITY
        }

        then:"The query contains the hint"
        MongoQueryException exception = thrown()
        exception.message.contains('Query failed with error code 148')
        c.query.@queryArguments == [readConcern: ReadConcern.MAJORITY]

        when:"A dynamic finder uses a hint"
        def results = grails.gorm.tests.Person.findAllByFirstName("Bob", [readConcern: ReadConcern.MAJORITY])

        then:"The read concern is used"
        MongoQueryException exception2 = thrown()
        exception2.message.contains('Query failed with error code 148')
    }

    @Ignore
    void "Test that hints work on detached criteria queries"() {
        when:"A criteria query is created with a hint"
        DetachedCriteria<grails.gorm.tests.Person> detachedCriteria = new DetachedCriteria<>(grails.gorm.tests.Person)
        detachedCriteria = detachedCriteria.build {
            eq 'firstName', 'Bob'
        }

        def results = detachedCriteria.list(readConcern:ReadConcern.MAJORITY)
        for(e in results) {} // just to trigger the query
        then:"The hint is used"
        MongoQueryException exception2 = thrown()
        exception2.message.contains('Query failed with error code 148')
    }

    void "Test save with write concern"() {
        when:
        grails.gorm.tests.Person.withSession { MongoCodecSession session ->
            new grails.gorm.tests.Person(firstName: "Bob", lastName: "Smith").save(validate:false)
            session.flush(WriteConcern.MAJORITY)
        }

        then:
        grails.gorm.tests.Person.count() == 1

    }
}

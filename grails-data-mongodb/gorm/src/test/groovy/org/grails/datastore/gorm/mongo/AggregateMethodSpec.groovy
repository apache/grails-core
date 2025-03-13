package org.grails.datastore.gorm.mongo

import grails.gorm.tests.GormDatastoreSpec
import grails.mongodb.MongoEntity
import grails.persistence.Entity
import org.bson.types.ObjectId

/**
 * Created by graemerocher on 22/04/14.
 */
class AggregateMethodSpec extends GormDatastoreSpec{

    void "Test aggregate method"() {
        given:"Some test data"
            new City(city:"San Francisco", state: "CA", pop: 3000).save()
            new City(city:"LA", state: "CA", pop: 5000).save()
            new City(city:"Dallas", state: "TX", pop: 4000).save()
            new City(city:"Austin", state: "CA", pop: 15000).save()
            new City(city:"Sacramento", state: "CA", pop: 1000).save(flush:true)
            session.clear()

        when:"An aggregation query is executed"
            def results = City.aggregate([
                     ['$match':[pop:['$gte':1200]]]
            ])

        then:"The results are correct"
            results.size() == 4
            !results.find { it.city == "Sacramento" }
    }

    @Override
    List getDomainClasses() {
        [City]
    }
}

@Entity
class City implements MongoEntity<City> {
    ObjectId id
    String city
    String state
    int pop
}

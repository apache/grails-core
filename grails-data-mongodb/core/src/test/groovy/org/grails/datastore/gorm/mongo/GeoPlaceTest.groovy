package org.grails.datastore.gorm.mongo

import org.apache.grails.data.mongo.core.MongoDatastoreSpec
import grails.mongodb.geo.Point
import grails.persistence.Entity
import com.mongodb.client.MongoCollection
import org.bson.Document

@Entity
class GeoPlace {
    Long id
    String name
    Point point
    
    static mapping = {
        point geoIndex: '2dsphere'
    }
}

class GeoPlaceTest extends MongoDatastoreSpec {
    void setupSpec() {
        manager.addAllDomainClasses([GeoPlace])
    }
    
    void "test geo save"() {
        when:
        def point = new Point(5, 10)
        def p = new GeoPlace(name: "Test", point: point)
        println "Before save: id=${p.id}"
        p.save(flush: true, validate: false)
        println "After save: id=${p.id}"
        
        then:
        p.id != null
        
        when:
        // Check MongoDB
        MongoCollection<Document> col = manager.mongoDatastore.mongoClient
                .getDatabase("test")
                .getCollection("geoPlace")
        def docs = col.find().into([])
        println "MongoDB has ${docs.size()} docs"
        docs.each { println "Doc: $it" }
        
        then:
        docs.size() == 1
    }
}

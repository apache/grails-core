package org.grails.datastore.gorm.mongo

import org.apache.grails.data.mongo.core.MongoDatastoreSpec
import grails.mongodb.geo.Point
import grails.persistence.Entity
import com.mongodb.client.MongoCollection
import org.bson.Document

@Entity
class GeoPlace2 {
    Long id
    String name
    Point point
    
    static mapping = {
        point geoIndex: '2dsphere'
    }
}

class GeoRetrieveTest extends MongoDatastoreSpec {
    void setupSpec() {
        manager.addAllDomainClasses([GeoPlace2])
    }
    
    void "test geo retrieve"() {
        when:
        def point = new Point(5, 10)
        def p = new GeoPlace2(name: "Test", point: point)
        p.save(flush: true, validate: false)
        def savedId = p.id
        println "Saved with id: ${savedId}"
        manager.session.clear()
        
        then:
        savedId != null
        
        when:
        def retrieved = GeoPlace2.get(savedId)
        println "Retrieved: ${retrieved}"
        println "Retrieved.point: ${retrieved?.point}"
        
        then:
        retrieved != null
        retrieved.point == point
    }
}

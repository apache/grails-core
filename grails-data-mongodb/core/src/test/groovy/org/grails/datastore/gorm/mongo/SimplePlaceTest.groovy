package org.grails.datastore.gorm.mongo

import org.apache.grails.data.mongo.core.MongoDatastoreSpec
import grails.mongodb.geo.Point
import grails.persistence.Entity
import com.mongodb.client.MongoCollection
import org.bson.Document

@Entity
class SimplePlace {
    Long id
    String name
    Point point
    
    static mapping = {
        point geoIndex: '2dsphere'
    }
}

class SimplePlaceTest extends MongoDatastoreSpec {
    void setupSpec() {
        manager.addAllDomainClasses([SimplePlace])
    }
    
    void "test simple save"() {
        when:
        def p = new SimplePlace(name: "Test")
        p.save(flush: true, validate: false)
        
        then:
        p.id != null
        println "ID after save: ${p.id}"
    }
}

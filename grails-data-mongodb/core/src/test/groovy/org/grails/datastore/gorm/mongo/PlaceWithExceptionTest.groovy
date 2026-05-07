package org.grails.datastore.gorm.mongo

import org.apache.grails.data.mongo.core.MongoDatastoreSpec
import grails.mongodb.geo.*
import grails.persistence.Entity

@Entity
class PlaceException {
    Long id
    String name
    Point point
    Polygon polygon
    
    static mapping = {
        point geoIndex: '2dsphere'
    }
}

class PlaceWithExceptionTest extends MongoDatastoreSpec {
    void setupSpec() {
        manager.addAllDomainClasses([PlaceException])
    }
    
    void "test place with exception handling"() {
        when:
        def col = new GeometryCollection()
        col << Point.valueOf(5, 10)
        def p = new PlaceException(name: "Test")  // Don't set any GeoJSON fields
        p.save(flush: true, validate: false)
        println "Saved with id: ${p.id}"
        manager.session.clear()
        
        then:
        p.id != null
        
        when:
        try {
            p = PlaceException.get(p.id)
            println "Retrieved successfully: ${p}"
        } catch (Exception e) {
            println "Exception during get: ${e.class.name}"
            println "Message: ${e.message}"
            e.printStackTrace(System.out)
            throw e
        }
        
        then:
        p != null
    }
}

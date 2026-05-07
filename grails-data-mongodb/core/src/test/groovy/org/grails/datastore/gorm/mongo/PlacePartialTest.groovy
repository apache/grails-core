package org.grails.datastore.gorm.mongo

import org.apache.grails.data.mongo.core.MongoDatastoreSpec
import grails.mongodb.geo.*
import grails.persistence.Entity

@Entity
class PlacePartial {
    Long id
    String name
    Point point
    Polygon polygon
    LineString lineString
    Box box
    Circle circle
    Sphere sphere
    MultiPoint multiPoint
    MultiLineString multiLineString
    MultiPolygon multiPolygon
    GeometryCollection geometryCollection
    
    static mapping = {
        point geoIndex: '2dsphere'
    }
}

class PlacePartialTest extends MongoDatastoreSpec {
    void setupSpec() {
        manager.addAllDomainClasses([PlacePartial])
    }
    
    void "test place with only one field"() {
        when:
        def col = new GeometryCollection()
        col << Point.valueOf(5, 10)
        def p = new PlacePartial(geometryCollection: col)
        println "Saving with only geometryCollection set..."
        p.save(flush: true, validate: false)
        println "Saved with id: ${p.id}"
        manager.session.clear()
        
        then:
        p.id != null
        
        when:
        println "Retrieving..."
        p = PlacePartial.get(p.id)
        println "Retrieved: ${p}"
        
        then:
        p != null
    }
}

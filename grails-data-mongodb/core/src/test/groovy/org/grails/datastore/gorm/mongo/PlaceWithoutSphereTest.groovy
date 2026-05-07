package org.grails.datastore.gorm.mongo

import org.apache.grails.data.mongo.core.MongoDatastoreSpec
import grails.mongodb.geo.*
import grails.persistence.Entity

@Entity
class PlaceNS {
    Long id
    String name
    Point point
    Polygon polygon
    LineString lineString
    Box box
    Circle circle
    // NO Sphere!
    MultiPoint multiPoint
    MultiLineString multiLineString
    MultiPolygon multiPolygon
    GeometryCollection geometryCollection
    
    static mapping = {
        point geoIndex: '2dsphere'
    }
}

class PlaceWithoutSphereTest extends MongoDatastoreSpec {
    void setupSpec() {
        manager.addAllDomainClasses([PlaceNS])
    }
    
    void "test place without sphere"() {
        given:
        def point = new Point(5, 10)
        def poly = Polygon.valueOf([[100.0, 0.0], [101.0, 0.0], [101.0, 1.0], [100.0, 1.0], [100.0, 0.0]])
        def line = LineString.valueOf([[100.0, 0.0], [101.0, 1.0]])
        def box = Box.valueOf([[0, 0], [10, 10]])
        def circle = Circle.valueOf([[5, 5], 3])
        
        when:
        def p = new PlaceNS(point: point, polygon: poly, lineString: line, box: box, circle: circle)
        p.save(flush: true, validate: false)
        manager.session.clear()
        p = PlaceNS.get(p.id)
        
        then:
        p != null
        p.point == point
    }
}

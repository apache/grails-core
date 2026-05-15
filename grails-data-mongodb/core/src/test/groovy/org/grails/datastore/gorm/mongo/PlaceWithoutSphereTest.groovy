/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  'License'); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  'AS IS' BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
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
    
    void 'test place without sphere'() {
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

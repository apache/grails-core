/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
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

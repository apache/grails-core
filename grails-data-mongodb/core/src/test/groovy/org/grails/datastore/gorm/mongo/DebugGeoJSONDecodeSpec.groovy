/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.grails.datastore.gorm.mongo

import org.apache.grails.data.mongo.core.MongoDatastoreSpec
import grails.mongodb.geo.GeometryCollection
import grails.mongodb.geo.Point
import grails.persistence.Entity

class DebugGeoJSONDecodeSpec extends MongoDatastoreSpec {

    void setupSpec() {
        manager.addAllDomainClasses([PlaceWithGeoJSON])
    }

    void "test simple GeoJSON field"() {
        when: "A Place with a single GeoJSON field is saved"
        def p = new PlaceWithGeoJSON(point: Point.valueOf(5, 10))
        p.save(flush: true, validate: false)
        def savedId = p.id
        println "Saved Place with id: ${savedId}, point: ${p.point}"
        
        and: "The session is cleared"
        manager.session.clear()
        
        and: "Place.get() is called"
        println "Calling PlaceWithGeoJSON.get(${savedId})..."
        def retrieved = PlaceWithGeoJSON.get(savedId)
        println "Retrieved: ${retrieved}"
        
        then: "The Place should be retrieved"
        retrieved != null
        retrieved.id == savedId
        retrieved.point == Point.valueOf(5, 10)
    }
    
    void "test GeoJSON collection field"() {
        when: "A Place with a GeometryCollection is saved"
        def col = new GeometryCollection()
        col << Point.valueOf(5, 10)
        println "Created GeometryCollection: ${col}"
        
        def p = new PlaceWithGeoJSON(geometryCollection: col)
        p.save(flush: true, validate: false)
        def savedId = p.id
        println "Saved Place with id: ${savedId}, geomCollection: ${p.geometryCollection}"
        
        and: "The session is cleared"
        manager.session.clear()
        
        and: "Place.get() is called"
        println "Calling PlaceWithGeoJSON.get(${savedId})..."
        def retrieved = PlaceWithGeoJSON.get(savedId)
        println "Retrieved: ${retrieved}"
        
        then: "The Place should be retrieved"
        retrieved != null
        retrieved.id == savedId
        retrieved.geometryCollection == col
    }
}

@Entity
class PlaceWithGeoJSON {
    Long id
    Point point
    GeometryCollection geometryCollection
}

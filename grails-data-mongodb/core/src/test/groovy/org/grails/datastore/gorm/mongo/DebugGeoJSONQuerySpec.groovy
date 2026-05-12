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
import com.mongodb.client.MongoCollection
import org.bson.Document

class DebugGeoJSONQuerySpec extends MongoDatastoreSpec {

    void setupSpec() {
        manager.addAllDomainClasses([PlaceWithGeoJSONQuery])
    }

    void "test raw query for GeoJSON collection field"() {
        when: "A Place with a GeometryCollection is saved"
        def col = new GeometryCollection()
        col << Point.valueOf(5, 10)
        
        def p = new PlaceWithGeoJSONQuery(geometryCollection: col)
        p.save(flush: true, validate: false)
        def savedId = p.id
        println "Saved Place with id: ${savedId}"
        
        and: "The session is cleared"
        manager.session.clear()
        
        and: "We do a raw query for the document"
        def entity = manager.mongoDatastore.mappingContext.getPersistentEntity(PlaceWithGeoJSONQuery.name)
        def collection = manager.session.getCollection(entity)
        println "Collection: ${collection}"
        
        def query = new Document('_id', savedId)
        println "Query: ${query}"
        
        def doc = collection.find(query).first()
        println "Raw document found: ${doc}"
        
        then: "The document should exist"
        doc != null
    }
}

@Entity
class PlaceWithGeoJSONQuery {
    Long id
    Point point
    GeometryCollection geometryCollection
}

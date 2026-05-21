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
import grails.mongodb.geo.Point
import grails.persistence.Entity
import com.mongodb.client.MongoCollection
import org.bson.Document

@Entity
class DebugPlace {
    Long id
    String name
    Point point
    
    static mapping = {
        point geoIndex: '2dsphere'
    }
}

class DebugGeoJSONSpec extends MongoDatastoreSpec {
    
    void setupSpec() {
        manager.addAllDomainClasses([DebugPlace])
    }
    
    void "test debug place save and retrieve"() {
        when: "save a place with a point"
        def point = new Point(5, 10)
        def p = new DebugPlace(name: "Test", point: point)
        println "Before save: id=${p.id}"
        p.save(flush: true, validate: false)
        println "After save: id=${p.id}, object=${p}"
        
        then: "id should be set"
        p.id != null
        
        when: "check mongodb directly"
        MongoCollection<Document> col = manager.mongoDatastore.mongoClient
                .getDatabase("test")
                .getCollection("debugPlace")
        def allDocs = col.find().into([])
        println "Documents in MongoDB: ${allDocs.size()}"
        allDocs.each { println "  $it" }
        
        then: "document should exist"
        allDocs.size() == 1
        
        when: "clear session and retrieve"
        manager.session.clear()
        def retrieved = DebugPlace.get(p.id)
        println "Retrieved: ${retrieved}"
        
        then: "should get the object back"
        retrieved != null
        retrieved.point == point
    }
}

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
import grails.mongodb.geo.Point
import grails.persistence.Entity
import com.mongodb.client.MongoCollection
import org.bson.Document

@Entity
class GeoPlace {
    Long id
    String name
    Point point
    
    static mapping = {
        point geoIndex: '2dsphere'
    }
}

class GeoPlaceTest extends MongoDatastoreSpec {
    void setupSpec() {
        manager.addAllDomainClasses([GeoPlace])
    }
    
    void "test geo save"() {
        when:
        def point = new Point(5, 10)
        def p = new GeoPlace(name: "Test", point: point)
        println "Before save: id=${p.id}"
        p.save(flush: true, validate: false)
        println "After save: id=${p.id}"
        
        then:
        p.id != null
        
        when:
        // Check MongoDB
        MongoCollection<Document> col = manager.mongoDatastore.mongoClient
                .getDatabase("test")
                .getCollection("geoPlace")
        def docs = col.find().into([])
        println "MongoDB has ${docs.size()} docs"
        docs.each { println "Doc: $it" }
        
        then:
        docs.size() == 1
    }
}

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

import grails.gorm.annotation.Entity
import grails.mongodb.MongoEntity
import grails.mongodb.geo.Point
import org.apache.grails.data.mongo.core.GrailsDataMongoTckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import org.bson.Document

import static grails.mongodb.mapping.MappingBuilder.document

/**
 * Created by graemerocher on 02/02/2017.
 */
class DocumentMappingSpec extends GrailsDataTckSpec<GrailsDataMongoTckManager> {
    void setupSpec() {
        manager.domainClasses.addAll([CustomMapping])
    }

    void "test custom document mapping"() {
        when: "A document is saved with a custom mapping"
        new CustomMapping(name: "test", loc: Point.valueOf(10, 15)).save(flush: true)
        Document doc = CustomMapping.collection.find().first()

        then:
        CustomMapping.collection.namespace.collectionName == 'mycoll'
        CustomMapping.collection.namespace.databaseName == 'myDb'
        doc.get("my_name") == "test"
        doc.get("loc").inspect() == '[\'type\':\'Point\', \'coordinates\':[10.0, 15.0]]'
    }
}

@Entity
class CustomMapping implements MongoEntity<CustomMapping> {

    String name
    Point loc

    static mapping = document {
        collection "mycoll"
        database "myDb"
        name property {
            reference false
            attr "my_name"
        }
        loc property {
            geoIndex "2dsphere"
        }
    }
}

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

import com.mongodb.client.MongoClient
import com.mongodb.client.MongoClients
import grails.gorm.annotation.Entity
import spock.lang.Shared

import org.apache.grails.testing.mongo.AutoStartedMongoSpec
import org.grails.datastore.mapping.mongo.MongoDatastore

/**
 * A {@code '*'} entry in {@code grails.gorm.default.constraints} applies to every property, and the
 * mapping and constraints closures that follow it configure the same property entries. A property
 * declared {@code index: true} in the mapping and named again in the constraints is still indexed.
 */
class BuildIndexesDefaultConstraintsSpec extends AutoStartedMongoSpec {

    @Shared
    MongoClient realClient

    @Override
    boolean shouldInitializeDatastore() {
        false
    }

    void setupSpec() {
        realClient = MongoClients.create(dbContainer.getReplicaSetUrl('defaultConstraintsDb'))
    }

    void cleanupSpec() {
        realClient?.close()
    }

    void "test a property indexed in the mapping and constrained under a '*' default is indexed"() {
        when:
        def datastore = new MongoDatastore([
                'grails.mongodb.url'             : dbContainer.getReplicaSetUrl('defaultConstraintsDb'),
                'grails.gorm.default.constraints': { '*'(nullable: true) }
        ], DefaultConstraintsIndexedThing)
        datastore.start()
        def indexes = realClient.getDatabase('defaultConstraintsDb')
                .getCollection('defaultConstraintsIndexedThing').listIndexes().toList()

        then: "both the plain declaration and the one carrying index attributes are built"
        indexes.find { it.key == [code: 1] }
        indexes.find { it.key == [name: 1] }?.sparse

        and: "the property the mapping alone names is built as before"
        indexes.find { it.key == [category: 1] }

        cleanup:
        datastore?.close()
    }
}

@Entity
class DefaultConstraintsIndexedThing {

    String code
    String name
    String category

    static mapping = {
        code index: true
        name index: true, indexAttributes: [sparse: true]
        category index: true
    }

    static constraints = {
        code maxSize: 20
        name blank: false
    }
}

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
package org.grails.datastore.gorm.mongo.connections

import grails.gorm.annotation.Entity
import spock.lang.AutoCleanup
import spock.lang.Shared

import org.apache.grails.testing.mongo.AutoStartedMongoSpec
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.mongo.MongoDatastore
import org.grails.datastore.mapping.mongo.config.MongoSettings

/**
 * Verifies that {@code buildIndexes} is resolved per connection: a connection inherits the top level
 * setting unless it declares its own, so index building can be switched off globally and left on for an
 * individual connection (or the other way round).
 */
class BuildIndexesPerConnectionSpec extends AutoStartedMongoSpec {

    @Shared
    @AutoCleanup
    MongoDatastore datastore

    @Override
    boolean shouldInitializeDatastore() {
        false
    }

    void setupSpec() {
        Map config = [
                'grails.mongodb.url'                 : "mongodb://${mongoHost}:${mongoPort}/skippedDb" as String,
                (MongoSettings.SETTING_BUILD_INDEXES): false,
                'grails.mongodb.connections'         : [
                        'indexed': [
                                'url'         : "mongodb://${mongoHost}:${mongoPort}/indexedDb" as String,
                                'buildIndexes': true
                        ],
                        'inherits': [
                                'url': "mongodb://${mongoHost}:${mongoPort}/inheritsDb" as String
                        ]
                ]
        ]
        datastore = new MongoDatastore(config, PerConnectionThing)
    }

    void "test a connection can override the global setting"() {
        expect: "the default connection is disabled by the top level setting"
        !datastore.isBuildIndexes()

        and: "the connection that declares its own setting builds indexes"
        datastore.getDatastoreForConnection('indexed').isBuildIndexes()

        and: "a connection that declares nothing inherits the top level setting"
        !datastore.getDatastoreForConnection('inherits').isBuildIndexes()
    }

    void "test only the connection with index building enabled has the declared index"() {
        when: "a document is written through each connection so every collection exists"
        PerConnectionThing.withNewSession {
            new PerConnectionThing(name: 'Fred').save(flush: true)
        }
        PerConnectionThing.indexed.withNewSession {
            new PerConnectionThing(name: 'Fred').save(flush: true)
        }

        then: "the disabled connection has only the implicit _id index"
        PerConnectionThing.collection.listIndexes()*.key == [[_id: 1]]

        and: "the enabled connection has the index declared in the mapping"
        [name: 1] in PerConnectionThing.indexed.collection.listIndexes()*.key
    }
}

@Entity
class PerConnectionThing {
    String name

    static mapping = {
        version false
        collection 'perConnectionThing'
        connection ConnectionSource.ALL
        name index: true
    }
}

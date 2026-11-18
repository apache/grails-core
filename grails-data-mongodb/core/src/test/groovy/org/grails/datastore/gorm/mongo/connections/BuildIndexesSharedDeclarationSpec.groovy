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

/**
 * Every connection builds its indexes from the same mapping declaration, so reading the declaration must
 * leave it as it was. A compound index's options live inside the declaration itself, under
 * {@code indexAttributes}, and a build that consumed them would hand the first connection a unique index
 * and every later one a plain index on the same keys.
 */
class BuildIndexesSharedDeclarationSpec extends AutoStartedMongoSpec {

    @Shared
    @AutoCleanup
    MongoDatastore datastore

    @Override
    boolean shouldInitializeDatastore() {
        false
    }

    void setupSpec() {
        datastore = new MongoDatastore([
                'grails.mongodb.url'        : "mongodb://${mongoHost}:${mongoPort}/sharedDefaultDb" as String,
                'grails.mongodb.connections': [
                        'second': ['url': "mongodb://${mongoHost}:${mongoPort}/sharedSecondDb" as String]
                ]
        ] as Map, SharedDeclarationThing)
    }

    private static Map compoundIndexOn(def staticApi) {
        staticApi.collection.listIndexes().find { it.key == [name: 1, age: -1] } as Map
    }

    void "test every connection gets the compound index options that were declared"() {
        expect: "the default connection's compound index is unique"
        compoundIndexOn(SharedDeclarationThing)?.unique == true

        and: "and so is the other connection's, built from the same declaration"
        compoundIndexOn(SharedDeclarationThing.second)?.unique == true
    }
}

@Entity
class SharedDeclarationThing {
    String name
    Integer age

    static mapping = {
        version false
        collection 'sharedDeclarationThing'
        connection ConnectionSource.ALL
        compoundIndex name: 1, age: -1, indexAttributes: [unique: true]
    }
}

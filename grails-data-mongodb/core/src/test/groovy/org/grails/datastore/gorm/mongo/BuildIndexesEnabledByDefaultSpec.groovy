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
import spock.lang.AutoCleanup
import spock.lang.Shared

import org.apache.grails.testing.mongo.AutoStartedMongoSpec
import org.grails.datastore.mapping.mongo.MongoDatastore

/**
 * The control for {@link BuildIndexesDisabledSpec}: with {@code grails.mongodb.buildIndexes} left at its
 * default the very same mapping declarations do produce indexes on startup, so the absence of indexes in
 * that specification is attributable to the setting and not to the mapping.
 */
class BuildIndexesEnabledByDefaultSpec extends AutoStartedMongoSpec {

    @Shared
    @AutoCleanup
    MongoDatastore datastore

    @Override
    boolean shouldInitializeDatastore() {
        false
    }

    void setupSpec() {
        // No grails.mongodb.buildIndexes => default true
        datastore = new MongoDatastore(['grails.mongodb.url': dbContainer.getReplicaSetUrl('myDb')] as Map, BuiltIndexThing)
    }

    void "test index building is enabled by default"() {
        expect:
        datastore.isBuildIndexes()
    }

    void "test the declared indexes are created on startup"() {
        when:
        List indexKeys = BuiltIndexThing.withNewSession {
            BuiltIndexThing.collection.listIndexes()*.key
        }

        then: "the property index and the compound index are both present alongside the implicit _id index"
        [name: 1] in indexKeys
        [name: 1, age: -1] in indexKeys
    }
}

@Entity
class BuiltIndexThing {
    String name
    Integer age

    static mapping = {
        version false
        collection 'builtIndexThing'
        name index: true
        compoundIndex name: 1, age: -1
    }
}

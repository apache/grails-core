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
import org.grails.datastore.mapping.mongo.config.MongoSettings

/**
 * Verifies that with {@code grails.mongodb.buildIndexes = false} GORM issues no index commands: the
 * indexes declared in the mapping block are neither created nor reconciled, so whatever indexes are
 * already on the server are left untouched. Persistence and querying are unaffected.
 *
 * @see BuildIndexesEnabledByDefaultSpec for the default behavior with the same mapping declarations
 */
class BuildIndexesDisabledSpec extends AutoStartedMongoSpec {

    @Shared
    @AutoCleanup
    MongoDatastore datastore

    @Override
    boolean shouldInitializeDatastore() {
        false
    }

    void setupSpec() {
        Map config = [
                'grails.mongodb.url'                  : dbContainer.getReplicaSetUrl('myDb'),
                (MongoSettings.SETTING_BUILD_INDEXES) : false
        ]
        datastore = new MongoDatastore(config, SkippedIndexThing)
    }

    private static List declaredIndexKeys() {
        SkippedIndexThing.withNewSession {
            SkippedIndexThing.collection.listIndexes()*.key
        }
    }

    void "test the datastore reports index building disabled"() {
        expect:
        !datastore.isBuildIndexes()
    }

    void "test no declared index is created on startup"() {
        when: "a document is written so the collection certainly exists"
        SkippedIndexThing.withNewSession {
            new SkippedIndexThing(name: 'Fred', age: 42).save(flush: true)
        }

        then: "only the implicit _id index is present - neither the property index nor the compound index was created"
        declaredIndexKeys() == [[_id: 1]]
    }

    void "test an explicit index build is a no-op while disabled"() {
        when: "index building is requested directly"
        datastore.buildIndex()

        then: "no index was created"
        declaredIndexKeys() == [[_id: 1]]
    }

    void "test registering an entity after startup does not create its indexes"() {
        given:
        def entity = datastore.mappingContext.getPersistentEntity(SkippedIndexThing.name)

        when: "the entity is re-registered, the path that indexes a domain class added after startup"
        datastore.persistentEntityAdded(entity)

        then: "no index was created"
        declaredIndexKeys() == [[_id: 1]]
    }

    void "test reads and writes are unaffected when index building is disabled"() {
        when:
        SkippedIndexThing.withNewSession {
            new SkippedIndexThing(name: 'Wilma', age: 41).save(flush: true)
        }

        then: "the unindexed property is still queryable"
        SkippedIndexThing.withNewSession { SkippedIndexThing.findByName('Wilma') }?.age == 41
    }
}

@Entity
class SkippedIndexThing {
    String name
    Integer age

    static mapping = {
        version false
        collection 'skippedIndexThing'
        name index: true
        compoundIndex name: 1, age: -1
    }
}

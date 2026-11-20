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

import grails.persistence.Entity
import org.apache.grails.data.mongo.core.GrailsDataMongoTckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import org.bson.Document
import org.bson.types.ObjectId

/**
 * Verifies that a single-field index declared with {@code indexAttributes: [expireAfterSeconds: N]}
 * is materialised as a MongoDB TTL index, and that a later change to the declared TTL is reconciled
 * in place (via {@code collMod}) rather than failing with IndexOptionsConflict.
 */
class TtlIndexSpec extends GrailsDataTckSpec<GrailsDataMongoTckManager> {

    void setupSpec() {
        manager.registerDomainClasses(TtlThing)
    }

    private Document ttlIndex() {
        TtlThing.collection.listIndexes().find { it.key == [created: 1] }
    }

    void "Test that expireAfterSeconds declared in the mapping creates a TTL index"() {
        expect: "no exception on startup and the index carries the declared TTL"
        TtlThing.count() == 0
        ttlIndex() != null
        ttlIndex().expireAfterSeconds == 100
    }

    void "Test that a changed TTL is reconciled in place on re-initialisation"() {
        given:
        def datastore = manager.mongoDatastore
        def entity = datastore.mappingContext.getPersistentEntity(TtlThing.name)
        def collName = datastore.getCollectionName(entity)

        when: "the live TTL is altered to a stale value (as if the configured retention changed)"
        manager.mongoClient.getDatabase('test').runCommand(new Document('collMod', collName)
                .append('index', new Document('name', 'created_1').append('expireAfterSeconds', 999)))

        then:
        ttlIndex().expireAfterSeconds == 999

        when: "indexes are re-initialised against the unchanged mapping declaration"
        datastore.persistentEntityAdded(entity)

        then: "the declared TTL is restored on the SAME index — no duplicate, no conflict error"
        def matches = TtlThing.collection.listIndexes().findAll { it.key == [created: 1] }
        matches.size() == 1
        matches[0].expireAfterSeconds == 100
    }
}


@Entity
class TtlThing {
    ObjectId id
    Long version
    Date created

    static mapping = {
        version false
        collection 'ttlThing'
        created index: true, indexAttributes: [expireAfterSeconds: 100]
    }
}

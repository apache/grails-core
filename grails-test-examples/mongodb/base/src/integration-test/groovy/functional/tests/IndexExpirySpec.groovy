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

package functional.tests

import com.mongodb.client.MongoCollection
import grails.testing.mixin.integration.Integration
import org.bson.Document
import org.grails.datastore.mapping.mongo.MongoDatastore
import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Specification

/**
 * Functional coverage, in a booted Grails application against a real MongoDB server, that
 * contrasts the existing behaviour with the new TTL support: {@link ExpiringEvent} declares
 * {@code indexAttributes: [expireAfterSeconds: N]} and gets a TTL index, while
 * {@link PersistentEvent} declares a plain index that never expires. A later change to the
 * declared TTL is reconciled in place rather than failing with an index conflict.
 */
@Integration(applicationClass = Application)
class IndexExpirySpec extends Specification {

    @Autowired
    MongoDatastore mongoDatastore

    void setup() {
        // The framework creates these indexes when the datastore initialises at startup, but
        // BootStrap drops the database afterwards. Re-run the same index initialisation here so
        // each feature starts from a known state and exercises the real create/reconcile path.
        reinitialiseIndexes(ExpiringEvent)
        reinitialiseIndexes(PersistentEvent)
    }

    private void reinitialiseIndexes(Class domainClass) {
        def entity = mongoDatastore.mappingContext.getPersistentEntity(domainClass.name)
        mongoDatastore.persistentEntityAdded(entity)
    }

    private static Document dateCreatedIndex(MongoCollection<Document> collection) {
        collection.listIndexes().find { it.key == [dateCreated: 1] }
    }

    void "new behaviour: expireAfterSeconds in the mapping creates a TTL index"() {
        when:
        Document index = dateCreatedIndex(ExpiringEvent.collection)

        then:
        index != null
        index.expireAfterSeconds == 3600
    }

    void "existing behaviour: a plain indexed property never expires"() {
        when:
        Document index = dateCreatedIndex(PersistentEvent.collection)

        then:
        index != null
        !index.containsKey('expireAfterSeconds')
    }

    void "a changed TTL is reconciled in place rather than failing with a conflict"() {
        given: "the live TTL is altered to a stale value, as if the configured retention had changed"
        Document index = dateCreatedIndex(ExpiringEvent.collection)
        ExpiringEvent.DB.runCommand(new Document('collMod', ExpiringEvent.collectionName)
                .append('index', new Document('name', index.getString('name')).append('expireAfterSeconds', 999)))

        expect:
        dateCreatedIndex(ExpiringEvent.collection).expireAfterSeconds == 999

        when: "indexes are re-initialised against the unchanged mapping declaration"
        reinitialiseIndexes(ExpiringEvent)

        then: "the declared TTL is restored on the same index — no duplicate, no conflict"
        def matches = ExpiringEvent.collection.listIndexes().findAll { it.key == [dateCreated: 1] }
        matches.size() == 1
        matches[0].expireAfterSeconds == 3600
    }
}

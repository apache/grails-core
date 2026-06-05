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
package org.grails.datastore.gorm.mongo.transactions

import grails.gorm.annotation.Entity

import org.apache.grails.testing.mongo.AutoStartedMongoSpec
import org.grails.datastore.mapping.mongo.MongoDatastore
import spock.lang.AutoCleanup
import spock.lang.Shared

/**
 * Verifies that with {@code grails.mongodb.transactional} left at its default (disabled), GORM keeps
 * the legacy client-side flush behavior: server-side transactions are not used, so writes already
 * flushed within a transaction are not rolled back. This is the non-breaking fallback contract.
 */
class MongoTransactionDisabledSpec extends AutoStartedMongoSpec {

    @Shared
    @AutoCleanup
    MongoDatastore datastore

    @Override
    boolean shouldInitializeDatastore() {
        false
    }

    void setupSpec() {
        // No grails.mongodb.transactional => default false
        datastore = new MongoDatastore(['grails.mongodb.url': dbContainer.getReplicaSetUrl('myDb')] as Map, LegacyThing)
    }

    void setup() {
        LegacyThing.withNewSession { LegacyThing.DB.drop() }
    }

    void "test transactions are disabled by default"() {
        expect:
        !datastore.isTransactionsEnabled()
    }

    void "test flushed writes are not rolled back when transactions are disabled (legacy behavior)"() {
        when: "a document is flushed inside a transaction that then fails"
        LegacyThing.withTransaction {
            new LegacyThing(name: "flushed").save(flush: true)
            throw new RuntimeException("boom")
        }

        then:
        thrown(RuntimeException)

        and: "the already-flushed write remains, because there was no server-side transaction to abort"
        LegacyThing.withNewSession { LegacyThing.count() } == 1
    }
}

@Entity
class LegacyThing {
    String name
}

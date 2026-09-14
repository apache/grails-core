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

import org.apache.grails.testing.mongo.EmbeddedReplicaSetSpec
import org.grails.datastore.mapping.mongo.MongoDatastore
import org.springframework.transaction.support.TransactionTemplate
import spock.lang.AutoCleanup
import spock.lang.Shared

/**
 * Verifies that with {@code grails.mongodb.transactional} left at its default (disabled), GORM keeps
 * the legacy client-side flush behavior: server-side transactions are not used, so writes already
 * flushed within a transaction are not rolled back. This is the non-breaking fallback contract.
 */
class MongoTransactionDisabledSpec extends EmbeddedReplicaSetSpec {

    @Shared
    @AutoCleanup
    MongoDatastore datastore

    void setupSpec() {
        // No grails.mongodb.transactional => default false
        datastore = new MongoDatastore(['grails.mongodb.url': mongoUrl] as Map, LegacyThing)
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

    void "a read-only transaction commits without flushing the surrounding session"() {
        when: "a read-only transaction commits while the session holds an unflushed write"
        int written = LegacyThing.withNewSession {
            new LegacyThing(name: "queued").save()
            TransactionTemplate txTemplate = new TransactionTemplate(datastore.transactionManager)
            txTemplate.readOnly = true
            txTemplate.execute {}
            LegacyThing.withNewSession { LegacyThing.count() }
        }

        then: "the queued write was left where it was, rather than committed by a read"
        written == 0
    }

    void "a read-write transaction still flushes the surrounding session"() {
        when: "the same sequence runs without the read-only flag"
        int written = LegacyThing.withNewSession {
            new LegacyThing(name: "queued").save()
            new TransactionTemplate(datastore.transactionManager).execute {}
            LegacyThing.withNewSession { LegacyThing.count() }
        }

        then: "the flush on commit is unchanged"
        written == 1
    }
}

@Entity
class LegacyThing {
    String name
}

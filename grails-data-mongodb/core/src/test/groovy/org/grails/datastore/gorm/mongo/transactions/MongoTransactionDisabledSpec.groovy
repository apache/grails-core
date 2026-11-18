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

import jakarta.persistence.FlushModeType
import org.apache.grails.testing.mongo.EmbeddedReplicaSetSpec
import org.grails.datastore.mapping.mongo.MongoDatastore
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
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

    void "a query in a transaction does not flush the session, so a rollback can still discard what is queued"() {
        given:
        long counted = -1

        when:
        LegacyThing.withTransaction {
            LegacyThing.withTransaction {
                new LegacyThing(name: "queued").save()
            }
            counted = LegacyThing.count()
            throw new RuntimeException("boom")
        }

        then: "the query did not see the joined transaction's queued write, and the rollback discarded it"
        thrown(RuntimeException)
        counted == 0
        names().empty
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

        then: "the read did not persist the queued write"
        written == 0

        and: "the write is dropped when its session closes, as it would be on Hibernate"
        LegacyThing.withNewSession { LegacyThing.count() } == 0
    }

    void "a transaction started inside another joins it, so the outer one's writes after it are committed"() {
        when:
        LegacyThing.withTransaction {
            new LegacyThing(name: "A").save()
            LegacyThing.withTransaction {
                new LegacyThing(name: "B").save()
            }
            new LegacyThing(name: "C").save()
        }

        then: "C was written by the outer commit, rather than dropped by a commit the inner one had already spent"
        names() == ["A", "B", "C"]
    }

    void "a read-only transaction inside a read-write one leaves the outer one's writes to be committed"() {
        when:
        LegacyThing.withTransaction {
            new LegacyThing(name: "A").save()
            LegacyThing.withTransaction([readOnly: true]) { LegacyThing.count() }
            new LegacyThing(name: "C").save()
        }

        then:
        names() == ["A", "C"]
    }

    void "a transaction in a new session inside a transaction is its own, and commits its writes"() {
        when:
        LegacyThing.withTransaction {
            new LegacyThing(name: "A").save()
            LegacyThing.withNewSession {
                LegacyThing.withTransaction {
                    new LegacyThing(name: "B").save()
                }
            }
            new LegacyThing(name: "C").save()
        }

        then:
        names() == ["A", "B", "C"]
    }

    void "transactional code run after a commit commits its own writes"() {
        when:
        LegacyThing.withTransaction {
            new LegacyThing(name: "A").save()
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                void afterCommit() {
                    LegacyThing.withTransaction {
                        new LegacyThing(name: "after commit").save()
                    }
                }
            })
        }

        then: "it did not join the transaction that had already committed"
        names() == ["A", "after commit"]
    }

    void "a joined transaction that fails discards what is queued, so a later explicit flush cannot write it"() {
        when:
        LegacyThing.withTransaction {
            new LegacyThing(name: "A").save()
            try {
                LegacyThing.withTransaction {
                    new LegacyThing(name: "B").save()
                    throw new IllegalStateException("inner")
                }
            }
            catch (IllegalStateException ignored) {
            }
            new LegacyThing(name: "C").save(flush: true)
        }

        then: "only C, flushed on its own, was written: without a server-side transaction a flush cannot be taken back"
        names() == ["C"]
    }

    void "a read-only transaction puts the session's flush mode back, so the session is flushed as before"() {
        when:
        FlushModeType mode = LegacyThing.withNewSession { session ->
            TransactionTemplate txTemplate = new TransactionTemplate(datastore.transactionManager)
            txTemplate.readOnly = true
            txTemplate.execute {}
            session.flushMode
        }

        then:
        mode == FlushModeType.AUTO
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

    private static List<String> names() {
        LegacyThing.withNewSession { LegacyThing.findAll()*.name.sort() }
    }
}

@Entity
class LegacyThing {
    String name
}

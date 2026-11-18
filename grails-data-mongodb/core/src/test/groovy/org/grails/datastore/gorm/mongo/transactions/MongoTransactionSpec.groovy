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

import com.mongodb.client.ClientSession
import com.mongodb.client.model.Filters
import org.apache.grails.testing.mongo.EmbeddedReplicaSetSpec
import org.grails.datastore.mapping.mongo.MongoDatastore
import org.springframework.transaction.CannotCreateTransactionException
import org.springframework.transaction.IllegalTransactionStateException
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.TransactionUsageException
import org.springframework.transaction.UnexpectedRollbackException
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.transaction.support.TransactionTemplate
import spock.lang.AutoCleanup
import spock.lang.Shared

/**
 * Tests that GORM uses real MongoDB multi-document transactions (a server-side ClientSession) when
 * {@code grails.mongodb.transactional} is enabled.
 */
class MongoTransactionSpec extends EmbeddedReplicaSetSpec {

    @Shared
    @AutoCleanup
    MongoDatastore datastore

    void setupSpec() {
        Map config = [
                'grails.mongodb.url'          : mongoUrl,
                'grails.mongodb.transactional': true
        ]
        datastore = new MongoDatastore(config, TxPerson, TxPet, TxCounter)
    }

    void setup() {
        TxPerson.withNewSession {
            TxPerson.DB.drop()
            TxPet.DB.drop()
            TxCounter.DB.drop()
            // Created up front: two open transactions that both create a collection by writing to it
            // conflict (WriteConflict), and a REQUIRES_NEW transaction runs beside its suspended outer one
            [TxPerson, TxPet, TxCounter].each { Class entity -> entity.DB.createCollection(entity.collectionName) }
        }
    }

    void "test the datastore reports transactions enabled against a replica set"() {
        expect:
        datastore.isTransactionsEnabled()
    }

    void "test a committed transaction persists all writes atomically"() {
        when: "two documents are saved in one transaction"
        TxPerson.withTransaction {
            new TxPerson(name: "Fred").save()
            new TxPerson(name: "Wilma").save()
        }

        then: "both are persisted after commit"
        TxPerson.withNewSession { TxPerson.count() } == 2
    }

    void "test a rolled back transaction discards all writes on the server"() {
        when: "documents are flushed inside a transaction that then fails"
        TxPerson.withTransaction {
            new TxPerson(name: "Fred").save(flush: true)
            new TxPerson(name: "Wilma").save(flush: true)
            throw new RuntimeException("boom")
        }

        then: "the exception propagates"
        thrown(RuntimeException)

        and: "nothing was persisted - the server-side transaction was aborted, not merely the session cleared"
        TxPerson.withNewSession { TxPerson.count() } == 0
    }

    void "test writes across multiple collections roll back together"() {
        when: "a person and a pet are written before the transaction fails"
        TxPerson.withTransaction {
            new TxPerson(name: "Fred").save(flush: true)
            new TxPet(name: "Dino").save(flush: true)
            throw new RuntimeException("boom")
        }

        then:
        thrown(RuntimeException)

        and: "neither collection retains the write"
        TxPerson.withNewSession { TxPerson.count() } == 0
        TxPet.withNewSession { TxPet.count() } == 0
    }

    void "test read-your-writes within an active transaction"() {
        expect: "a query inside the transaction sees its own uncommitted write"
        TxPerson.withTransaction {
            new TxPerson(name: "Fred").save(flush: true)
            TxPerson.count() == 1
        }

        and: "and it is visible to other sessions after commit"
        TxPerson.withNewSession { TxPerson.count() } == 1
    }

    void "a query sees a write a joined transaction left queued, as on Hibernate"() {
        when:
        long counted = TxPerson.withTransaction {
            TxPerson.withTransaction {
                new TxPerson(name: "Fred").save()
            }
            TxPerson.count()
        }

        then: "the session was flushed ahead of the query, inside the server-side transaction"
        counted == 1
        names() == ["Fred"]
    }

    void "a write flushed ahead of a query is rolled back with the transaction"() {
        given:
        long counted = -1

        when:
        TxPerson.withTransaction {
            new TxPerson(name: "Fred").save()
            counted = TxPerson.count()
            throw new RuntimeException("boom")
        }

        then:
        thrown(RuntimeException)
        counted == 1
        names().empty
    }

    void "a query in a read-only transaction does not flush the session"() {
        when:
        long counted = TxPerson.withNewSession {
            new TxPerson(name: "Queued").save()
            TxPerson.withTransaction([readOnly: true]) { TxPerson.count() }
        }

        then: "a read-only transaction has no server-side transaction to take a flushed write back"
        counted == 0
        names().empty
    }

    void "test a findOneAndDelete via the MongoEntity API participates in the transaction"() {
        given: "an existing committed document"
        TxPerson.withNewSession {
            new TxPerson(name: "Fred").save(flush: true)
        }

        when: "it is deleted inside a transaction that then fails"
        TxPerson.withTransaction {
            TxPerson.findOneAndDelete(Filters.eq("name", "Fred"))
            throw new RuntimeException("boom")
        }

        then:
        thrown(RuntimeException)

        and: "the delete was rolled back rather than auto-committing outside the transaction"
        TxPerson.withNewSession { TxPerson.count() } == 1
    }

    void "a transaction started inside another joins it, so the writes of both commit together"() {
        when:
        ClientSession outerSession = null
        ClientSession innerSession = null
        TxPerson.withTransaction {
            new TxPerson(name: "A").save()
            outerSession = clientSession()
            TxPerson.withTransaction {
                new TxPerson(name: "B").save()
                innerSession = clientSession()
            }
            new TxPerson(name: "C").save()
        }

        then: "all three are committed, the outer transaction's write after the inner one included"
        names() == ["A", "B", "C"]

        and: "the inner transaction ran in the outer one's server transaction"
        innerSession != null
        innerSession.is(outerSession)
    }

    void "a write the outer transaction flushed before a joined transaction commits with the rest"() {
        when:
        TxPerson.withTransaction {
            new TxPerson(name: "A").save(flush: true)
            TxPerson.withTransaction {
                new TxPerson(name: "B").save(flush: true)
            }
            new TxPerson(name: "C").save(flush: true)
        }

        then: "beginning the inner transaction did not abort the outer one's server transaction"
        names() == ["A", "B", "C"]
    }

    void "a joined transaction that fails rolls back the outer transaction's writes too"() {
        when:
        TxPerson.withTransaction {
            new TxPerson(name: "A").save(flush: true)
            TxPerson.withTransaction {
                new TxPerson(name: "B").save(flush: true)
                throw new RuntimeException("inner")
            }
        }

        then:
        thrown(RuntimeException)
        names().empty
    }

    void "an outer transaction that catches a joined transaction's failure is rolled back as a whole"() {
        when:
        TxPerson.withTransaction {
            new TxPerson(name: "A").save(flush: true)
            try {
                TxPerson.withTransaction {
                    new TxPerson(name: "B").save(flush: true)
                    throw new IllegalStateException("inner")
                }
            }
            catch (IllegalStateException ignored) {
            }
            new TxPerson(name: "C").save(flush: true)
        }

        then: "GORM marks the outer transaction rollback-only when a joined one fails, so nothing is committed"
        noExceptionThrown()
        names().empty
    }

    void "the same, driven by Spring's TransactionTemplate, reports the rollback"() {
        given:
        TransactionTemplate template = new TransactionTemplate(datastore.transactionManager)

        when:
        template.execute {
            new TxPerson(name: "A").save(flush: true)
            try {
                template.execute { throw new IllegalStateException("inner") }
            }
            catch (IllegalStateException ignored) {
            }
            new TxPerson(name: "C").save(flush: true)
        }

        then:
        thrown(UnexpectedRollbackException)
        names().empty
    }

    void "a read-only transaction inside a read-write one joins it and leaves the outer one's writes to commit"() {
        when:
        TxPerson.withTransaction {
            new TxPerson(name: "A").save()
            TxPerson.withTransaction([readOnly: true]) { TxPerson.count() }
            new TxPerson(name: "C").save()
        }

        then:
        names() == ["A", "C"]
    }

    void "a read-write transaction inside a read-only one joins it and is read-only, as on Hibernate"() {
        when:
        TxPerson.withTransaction([readOnly: true]) {
            TxPerson.withTransaction {
                new TxPerson(name: "flushed").save(flush: true)
                new TxPerson(name: "queued").save()
            }
        }

        then: "the read-only commit does not flush, so only the write flushed explicitly was saved"
        names() == ["flushed"]
    }

    void "a write flushed in a read-write transaction inside a read-only one is not part of any transaction"() {
        when: "the read-only transaction fails after the joined one flushed a write"
        TxPerson.withTransaction([readOnly: true]) {
            TxPerson.withTransaction {
                new TxPerson(name: "flushed").save(flush: true)
            }
            throw new RuntimeException("read-only work failed")
        }

        then: "the write stays: a read-only transaction has no server-side transaction to roll it back with"
        thrown(RuntimeException)
        names() == ["flushed"]
    }

    void "a synchronization registered in a joined transaction runs once, after the outer commit"() {
        given:
        List<Long> seen = []

        when:
        TxPerson.withTransaction {
            new TxPerson(name: "A").save()
            TxPerson.withTransaction {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    void afterCommit() {
                        seen << TxPerson.withNewSession { TxPerson.count() }
                    }
                })
            }
            new TxPerson(name: "C").save()
        }

        then: "it saw everything the outer transaction committed"
        seen == [2L]
    }

    void "a REQUIRES_NEW transaction commits on its own, and the outer one resumes and commits too"() {
        when:
        TxPerson.withTransaction {
            new TxPerson(name: "outer").save(flush: true)
            TxPerson.withTransaction([propagationBehavior: TransactionDefinition.PROPAGATION_REQUIRES_NEW]) {
                new TxPerson(name: "inner").save(flush: true)
            }
            new TxPerson(name: "after").save(flush: true)
        }

        then:
        names() == ["after", "inner", "outer"]
    }

    void "a transaction in a new session inside a transaction is its own, and commits its writes"() {
        when:
        TxPerson.withTransaction {
            new TxPerson(name: "A").save()
            TxPerson.withNewSession {
                TxPerson.withTransaction {
                    new TxPerson(name: "B").save()
                }
            }
            new TxPerson(name: "C").save()
        }

        then:
        names() == ["A", "B", "C"]
    }

    void "a transaction in a new session, run after a joined transaction failed, commits its writes and the outer one still rolls back"() {
        when:
        TxPerson.withTransaction {
            new TxPerson(name: "A").save(flush: true)
            try {
                TxPerson.withTransaction {
                    new TxPerson(name: "B").save(flush: true)
                    throw new IllegalStateException("inner")
                }
            }
            catch (IllegalStateException ignored) {
            }
            TxPerson.withNewSession {
                TxPerson.withTransaction {
                    new TxPerson(name: "audit").save(flush: true)
                }
            }
            new TxPerson(name: "C").save(flush: true)
        }

        then:
        noExceptionThrown()
        names() == ["audit"]
    }

    void "the same, driven by Spring's TransactionTemplate, commits the new session's transaction and reports the outer rollback"() {
        given:
        TransactionTemplate template = new TransactionTemplate(datastore.transactionManager)

        when:
        template.execute {
            new TxPerson(name: "A").save(flush: true)
            try {
                template.execute { throw new IllegalStateException("inner") }
            }
            catch (IllegalStateException ignored) {
            }
            TxPerson.withNewSession {
                template.execute { new TxPerson(name: "audit").save(flush: true) }
            }
            new TxPerson(name: "C").save(flush: true)
        }

        then:
        thrown(UnexpectedRollbackException)
        names() == ["audit"]
    }

    void "transactional code run after a commit commits its own writes"() {
        when:
        TxPerson.withTransaction {
            new TxPerson(name: "A").save()
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                void afterCommit() {
                    TxPerson.withTransaction {
                        new TxPerson(name: "after commit").save()
                    }
                }
            })
        }

        then: "it did not join the transaction that had already committed"
        names() == ["A", "after commit"]
    }

    void "a per-transaction timeout on a read-only transaction is refused too"() {
        when:
        new TransactionTemplate(datastore.transactionManager).tap {
            readOnly = true
            timeout = 5
        }.execute { TxPerson.count() }

        then:
        CannotCreateTransactionException e = thrown()
        e.cause instanceof TransactionUsageException
    }

    void "a read-only transaction reads without a server transaction"() {
        expect:
        TxPerson.withTransaction([readOnly: true]) {
            TxPerson.count()
            clientSession() == null
        }
    }

    void "a transaction cannot be begun on a session that has one active, and the active one carries on"() {
        when:
        TxPerson.withTransaction {
            new TxPerson(name: "A").save(flush: true)
            try {
                TxPerson.withSession { it.beginTransaction() }
                assert false: 'a second transaction was begun on the session'
            }
            catch (IllegalTransactionStateException expected) {
            }
            new TxPerson(name: "B").save(flush: true)
        }

        then: "beginning the second did not abort the first, which committed both writes"
        names() == ["A", "B"]
    }

    void "test a REQUIRES_NEW inner transaction commits independently of a rolled back outer transaction"() {
        when: "an inner REQUIRES_NEW transaction commits while the outer transaction rolls back"
        TxPerson.withTransaction {
            new TxPerson(name: "outer").save(flush: true)
            TxPerson.withTransaction([propagationBehavior: TransactionDefinition.PROPAGATION_REQUIRES_NEW]) {
                new TxPerson(name: "inner").save(flush: true)
            }
            throw new RuntimeException("rollback outer")
        }

        then:
        thrown(RuntimeException)

        and: "only the inner transaction's write survived - it ran in a session and server transaction of its own"
        TxPerson.withNewSession { TxPerson.findAll()*.name } == ["inner"]
    }

    void "test native Long identifier generation works for entities committed in a transaction"() {
        when: "two entities with a native Long id are saved in one transaction"
        TxCounter.withTransaction {
            new TxCounter(name: "a").save()
            new TxCounter(name: "b").save()
        }

        then: "both are persisted with generated, monotonically increasing Long ids"
        List<TxCounter> saved = TxCounter.withNewSession { TxCounter.list().sort { it.id } }
        saved*.name == ["a", "b"]
        saved.every { it.id instanceof Long }
        saved[1].id > saved[0].id
    }

    void "test a rolled back transaction discards a native Long id entity (id generation is non-transactional)"() {
        when: "a native Long id entity is flushed in a transaction that then fails"
        TxCounter.withTransaction {
            new TxCounter(name: "doomed").save(flush: true)
            throw new RuntimeException("boom")
        }

        then:
        thrown(RuntimeException)

        and: "the document was rolled back even though the id counter is not enrolled in the transaction"
        TxCounter.withNewSession { TxCounter.count() } == 0
    }

    void "a read-only transaction commits without flushing the surrounding session"() {
        when: "a read-only transaction commits while the session holds an unflushed write"
        int written = TxPerson.withNewSession {
            new TxPerson(name: "Queued").save()
            TransactionTemplate txTemplate = new TransactionTemplate(datastore.transactionManager)
            txTemplate.readOnly = true
            txTemplate.execute {}
            TxPerson.withNewSession { TxPerson.count() }
        }

        then: "the read did not persist the queued write"
        written == 0

        and: "the write is dropped when its session closes, as it would be on Hibernate"
        TxPerson.withNewSession { TxPerson.count() } == 0
    }

    void "test a per-transaction timeout is rejected rather than silently ignored"() {
        given: "a transaction template that requests an explicit timeout"
        def txTemplate = new TransactionTemplate(datastore.transactionManager)
        txTemplate.timeout = 5

        when: "a transactional operation is attempted"
        txTemplate.execute {
            new TxPerson(name: "Fred").save(flush: true)
        }

        then: "beginning the transaction is refused, wrapping the usage exception that explains why"
        def e = thrown(CannotCreateTransactionException)
        e.cause instanceof TransactionUsageException

        and: "nothing was persisted"
        TxPerson.withNewSession { TxPerson.count() } == 0

        and: "the datastore remains usable - the rejected transaction's session was cleaned up, not leaked"
        TxPerson.withTransaction {
            new TxPerson(name: "Wilma").save()
        }
        TxPerson.withNewSession { TxPerson.count() } == 1
    }

    void "a per-transaction timeout refused on a pre-bound session leaves no server transaction behind it"() {
        when: "the timeout is refused on a session that outlives the attempt"
        List<String> written = TxPerson.withNewSession {
            try {
                new TransactionTemplate(datastore.transactionManager).tap { timeout = 5 }.execute {}
            }
            catch (CannotCreateTransactionException ignored) {
            }
            new TxPerson(name: "Fred").save(flush: true)
            TxPerson.withNewSession { TxPerson.findAll()*.name }
        }

        then: "the next write on it was not swallowed by a transaction the refused begin left open"
        written == ["Fred"]
    }

    private static ClientSession clientSession() {
        TxPerson.withSession { it.clientSession } as ClientSession
    }

    private static List<String> names() {
        TxPerson.withNewSession { TxPerson.findAll()*.name.sort() }
    }
}

@Entity
class TxPerson {
    String name
}

@Entity
class TxPet {
    String name
}

@Entity
class TxCounter {
    Long id
    String name
}

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

import com.mongodb.client.model.Filters
import org.apache.grails.testing.mongo.AutoStartedMongoSpec
import org.grails.datastore.mapping.mongo.MongoDatastore
import org.springframework.transaction.CannotCreateTransactionException
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.TransactionUsageException
import org.springframework.transaction.support.TransactionTemplate
import spock.lang.AutoCleanup
import spock.lang.Shared

/**
 * Tests that GORM uses real MongoDB multi-document transactions (a server-side ClientSession) when
 * {@code grails.mongodb.transactional} is enabled.
 */
class MongoTransactionSpec extends AutoStartedMongoSpec {

    @Shared
    @AutoCleanup
    MongoDatastore datastore

    @Override
    boolean shouldInitializeDatastore() {
        false
    }

    void setupSpec() {
        Map config = [
                'grails.mongodb.url'          : dbContainer.getReplicaSetUrl('myDb'),
                'grails.mongodb.transactional': true
        ]
        datastore = new MongoDatastore(config, TxPerson, TxPet, TxCounter)
    }

    void setup() {
        TxPerson.withNewSession {
            TxPerson.DB.drop()
            TxPet.DB.drop()
            TxCounter.DB.drop()
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

        and: "only the inner transaction's write survived - suspend/resume kept the two sessions separate"
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

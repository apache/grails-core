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
package org.grails.datastore.gorm.mongodb.springdata

import com.mongodb.client.ClientSession
import org.springframework.data.mongodb.MongoDatabaseFactory
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.DefaultTransactionDefinition
import org.springframework.transaction.support.TransactionSynchronizationManager
import spock.lang.Specification

import org.grails.datastore.mapping.core.Session
import org.grails.datastore.mapping.mongo.AbstractMongoSession
import org.grails.datastore.mapping.mongo.MongoDatastore
import org.grails.datastore.mapping.mongo.MongoTransaction
import org.grails.datastore.mapping.transactions.SessionHolder
import org.grails.datastore.mapping.transactions.Transaction
import org.grails.datastore.mapping.transactions.TransactionObject

/**
 * The end-to-end contract of this class - a shared MongoDB transaction spanning a GORM save and a
 * Spring Data write, committed/rolled back together, with no resource leaked across transactions -
 * is already proven functionally by {@link UnifiedMongoTransactionSpec} against a real MongoDB
 * replica set. This spec covers the branches that spec cannot reach without a second real
 * datastore: doBegin/doCleanupAfterCompletion's and doSuspend/doResume's own conditional logic in
 * isolation, exercised directly since all are protected extension points of this class in the same
 * package.
 */
class GormSharedSessionMongoTransactionManagerSpec extends Specification {

    MongoDatastore datastore = Mock(MongoDatastore)
    MongoDatabaseFactory databaseFactory = Mock(MongoDatabaseFactory)
    GormSharedSessionMongoTransactionManager manager = new GormSharedSessionMongoTransactionManager(datastore, databaseFactory)

    void cleanup() {
        if (TransactionSynchronizationManager.hasResource(databaseFactory)) {
            TransactionSynchronizationManager.unbindResource(databaseFactory)
        }
        if (TransactionSynchronizationManager.hasResource(datastore)) {
            TransactionSynchronizationManager.unbindResource(datastore)
        }
    }

    private TransactionObject begin(Session session) {
        datastore.connect() >> session
        TransactionObject txObject = manager.doGetTransaction()
        manager.doBegin(txObject, new DefaultTransactionDefinition())
        txObject
    }

    /** A session whose begin starts a server-side transaction on the given ClientSession */
    private AbstractMongoSession sessionWithServerTransaction(ClientSession clientSession) {
        AbstractMongoSession session = Mock(AbstractMongoSession)
        session.beginTransaction(_ as TransactionDefinition) >> { new MongoTransaction(session, clientSession, false) }
        session
    }

    /** A session whose begin starts no server-side transaction, as with server-side transactions disabled */
    private AbstractMongoSession sessionWithoutServerTransaction() {
        AbstractMongoSession session = Mock(AbstractMongoSession)
        session.beginTransaction(_ as TransactionDefinition) >> Mock(Transaction)
        session
    }

    void "doBegin binds a Spring Data resource holder when it begins a server-side transaction"() {
        when:
        begin(sessionWithServerTransaction(Mock(ClientSession)))

        then:
        TransactionSynchronizationManager.hasResource(databaseFactory)
    }

    void "doBegin does not bind a Spring Data resource holder when it begins no server-side transaction"() {
        when:
        begin(sessionWithoutServerTransaction())

        then:
        !TransactionSynchronizationManager.hasResource(databaseFactory)
    }

    void "doCleanupAfterCompletion unbinds the Spring Data resource holder its doBegin bound"() {
        given:
        TransactionObject txObject = begin(sessionWithServerTransaction(Mock(ClientSession)))
        assert TransactionSynchronizationManager.hasResource(databaseFactory)

        when:
        manager.doCleanupAfterCompletion(txObject)

        then:
        !TransactionSynchronizationManager.hasResource(databaseFactory)
    }

    void "a transaction that begins no server-side transaction in a session bound on top sets the surrounding holder aside, and puts it back"() {
        given: "a transaction with a holder bound, and a session bound on top of it, as withNewSession binds one"
        begin(sessionWithServerTransaction(Mock(ClientSession)))
        Object surrounding = TransactionSynchronizationManager.getResource(databaseFactory)
        assert surrounding != null
        AbstractMongoSession onTop = sessionWithoutServerTransaction()
        (TransactionSynchronizationManager.getResource(datastore) as SessionHolder).addSession(onTop)
        TransactionObject inner = manager.doGetTransaction()

        when: "a transaction in that session begins no server-side transaction"
        manager.doBegin(inner, new DefaultTransactionDefinition())

        then: "its MongoTemplate calls run without a ClientSession, not in the surrounding one"
        !TransactionSynchronizationManager.hasResource(databaseFactory)

        when:
        manager.doCleanupAfterCompletion(inner)

        then:
        TransactionSynchronizationManager.getResource(databaseFactory).is(surrounding)
    }

    void "a transaction that begins a server-side transaction in a session bound on top binds its own holder, and puts the surrounding one back"() {
        given:
        begin(sessionWithServerTransaction(Mock(ClientSession)))
        Object surrounding = TransactionSynchronizationManager.getResource(databaseFactory)
        assert surrounding != null
        (TransactionSynchronizationManager.getResource(datastore) as SessionHolder).addSession(sessionWithServerTransaction(Mock(ClientSession)))
        TransactionObject inner = manager.doGetTransaction()

        when:
        manager.doBegin(inner, new DefaultTransactionDefinition())

        then:
        TransactionSynchronizationManager.hasResource(databaseFactory)
        !TransactionSynchronizationManager.getResource(databaseFactory).is(surrounding)

        when:
        manager.doCleanupAfterCompletion(inner)

        then:
        TransactionSynchronizationManager.getResource(databaseFactory).is(surrounding)
    }

    void "doSuspend un-binds the Spring Data holder with GORM's, and doResume binds both back"() {
        given:
        TransactionObject txObject = begin(sessionWithServerTransaction(Mock(ClientSession)))
        Object springData = TransactionSynchronizationManager.getResource(databaseFactory)
        Object gorm = TransactionSynchronizationManager.getResource(datastore)

        when:
        Object suspended = manager.doSuspend(txObject)

        then: "a transaction begun next binds its own"
        !TransactionSynchronizationManager.hasResource(databaseFactory)
        !TransactionSynchronizationManager.hasResource(datastore)

        when:
        manager.doResume(txObject, suspended)

        then:
        TransactionSynchronizationManager.getResource(databaseFactory).is(springData)
        TransactionSynchronizationManager.getResource(datastore).is(gorm)
    }

    void "doSuspend and doResume leave Spring Data unbound when the suspended transaction had no holder"() {
        given: "server-side transactions are disabled, so the suspended transaction bound no Spring Data holder"
        TransactionObject txObject = begin(sessionWithoutServerTransaction())

        when:
        manager.doResume(txObject, manager.doSuspend(txObject))

        then:
        !TransactionSynchronizationManager.hasResource(databaseFactory)
        TransactionSynchronizationManager.hasResource(datastore)
    }

    void "doCleanupAfterCompletion is a no-op for the Spring Data resource when nothing was bound"() {
        given:
        TransactionObject txObject = begin(sessionWithoutServerTransaction())
        assert !TransactionSynchronizationManager.hasResource(databaseFactory)

        when:
        manager.doCleanupAfterCompletion(txObject)

        then:
        noExceptionThrown()
        !TransactionSynchronizationManager.hasResource(databaseFactory)
    }
}

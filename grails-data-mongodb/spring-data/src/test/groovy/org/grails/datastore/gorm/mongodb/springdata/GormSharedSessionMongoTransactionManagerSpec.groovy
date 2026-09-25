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
import org.springframework.transaction.support.DefaultTransactionDefinition
import org.springframework.transaction.support.TransactionSynchronizationManager
import spock.lang.Specification

import org.grails.datastore.mapping.core.Session
import org.grails.datastore.mapping.mongo.AbstractMongoSession
import org.grails.datastore.mapping.mongo.MongoDatastore
import org.grails.datastore.mapping.transactions.Transaction
import org.grails.datastore.mapping.transactions.TransactionObject

/**
 * The end-to-end contract of this class - a shared MongoDB transaction spanning a GORM save and a
 * Spring Data write, committed/rolled back together, with no resource leaked across transactions -
 * is already proven functionally by {@link UnifiedMongoTransactionSpec} against a real MongoDB
 * replica set. This spec covers the two branches that spec cannot reach without a second real
 * datastore: doBegin/doCleanupAfterCompletion's own conditional logic in isolation, exercised
 * directly since both are protected extension points of this class in the same package.
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

    void "doBegin binds a Spring Data resource holder when GORM has an active client session"() {
        given:
        AbstractMongoSession session = Mock(AbstractMongoSession)
        session.beginTransaction() >> Mock(Transaction)
        ClientSession clientSession = Mock(ClientSession)
        session.clientSession >> clientSession
        datastore.getCurrentSession() >> session

        when:
        begin(session)

        then:
        TransactionSynchronizationManager.hasResource(databaseFactory)
    }

    void "doBegin does not bind a Spring Data resource holder when GORM has no active client session"() {
        given: "server-side transactions are disabled, so beginTransaction() starts no real MongoDB ClientSession"
        AbstractMongoSession session = Mock(AbstractMongoSession)
        session.beginTransaction() >> Mock(Transaction)
        session.clientSession >> null
        datastore.getCurrentSession() >> session

        when:
        begin(session)

        then:
        !TransactionSynchronizationManager.hasResource(databaseFactory)
    }

    void "doBegin does not bind a Spring Data resource holder when the current session is not a Mongo session"() {
        given: "a defensive branch - GORM's current session is some other Datastore's, not this Mongo one"
        Session session = Mock(Session)
        session.beginTransaction() >> Mock(Transaction)
        datastore.getCurrentSession() >> session

        when:
        begin(session)

        then:
        !TransactionSynchronizationManager.hasResource(databaseFactory)
    }

    void "doCleanupAfterCompletion unbinds a previously bound Spring Data resource holder"() {
        given:
        AbstractMongoSession session = Mock(AbstractMongoSession)
        session.beginTransaction() >> Mock(Transaction)
        session.clientSession >> Mock(ClientSession)
        datastore.getCurrentSession() >> session
        TransactionObject txObject = begin(session)
        assert TransactionSynchronizationManager.hasResource(databaseFactory)

        when:
        manager.doCleanupAfterCompletion(txObject)

        then:
        !TransactionSynchronizationManager.hasResource(databaseFactory)
    }

    void "doCleanupAfterCompletion is a no-op for the Spring Data resource when nothing was bound"() {
        given:
        AbstractMongoSession session = Mock(AbstractMongoSession)
        session.beginTransaction() >> Mock(Transaction)
        session.clientSession >> null
        datastore.getCurrentSession() >> session
        TransactionObject txObject = begin(session)
        assert !TransactionSynchronizationManager.hasResource(databaseFactory)

        when:
        manager.doCleanupAfterCompletion(txObject)

        then:
        noExceptionThrown()
        !TransactionSynchronizationManager.hasResource(databaseFactory)
    }
}

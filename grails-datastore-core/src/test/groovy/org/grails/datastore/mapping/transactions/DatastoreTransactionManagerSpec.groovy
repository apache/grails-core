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
package org.grails.datastore.mapping.transactions

import jakarta.persistence.FlushModeType
import org.springframework.dao.DataAccessResourceFailureException
import org.springframework.dao.InvalidDataAccessApiUsageException
import org.springframework.transaction.CannotCreateTransactionException
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.TransactionSystemException
import org.springframework.transaction.support.DefaultTransactionDefinition
import org.springframework.transaction.support.TransactionSynchronizationManager
import spock.lang.Specification

import org.grails.datastore.mapping.core.ConnectionNotFoundException
import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.core.Session

class DatastoreTransactionManagerSpec extends Specification {

    Datastore datastore = Mock(Datastore)
    Session session = Mock(Session)
    Transaction transaction = Mock(Transaction)
    DatastoreTransactionManager manager = new DatastoreTransactionManager(datastore: datastore)

    void cleanup() {
        if (TransactionSynchronizationManager.hasResource(datastore)) {
            TransactionSynchronizationManager.unbindResource(datastore)
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization()
        }
    }

    void "the manager requires a datastore"() {
        when:
        new DatastoreTransactionManager().datastore

        then:
        IllegalArgumentException e = thrown()
        e.message == 'Cannot use DatastoreTransactionManager without a datastore set!'

        expect:
        manager.datastore.is(datastore)
    }

    void "a new transaction connects a session, binds it and commits after flushing"() {
        given:
        session.hasTransaction() >> true
        session.getTransaction() >> transaction
        transaction.isActive() >> true

        when:
        TransactionStatus status = manager.getTransaction(new DefaultTransactionDefinition(timeout: 20))

        then:
        1 * datastore.connect() >> session
        1 * session.beginTransaction() >> transaction
        1 * transaction.setTimeout(20)
        0 * session.setFlushMode(_)
        1 * session.setSynchronizedWithTransaction(true)
        status.newTransaction
        TransactionSynchronizationManager.getResource(datastore) instanceof SessionHolder
        ((SessionHolder) TransactionSynchronizationManager.getResource(datastore)).session.is(session)
        ((SessionHolder) TransactionSynchronizationManager.getResource(datastore)).synchronizedWithTransaction

        when:
        manager.commit(status)

        then:
        1 * session.flush()
        1 * transaction.commit()
        1 * session.disconnect()
        1 * session.setSynchronizedWithTransaction(false)
        !((SessionHolder) TransactionSynchronizationManager.getResource(datastore)).synchronizedWithTransaction
    }

    void "read only transactions switch the flush mode and do not flush on commit"() {
        given:
        datastore.connect() >> session
        session.beginTransaction() >> transaction
        session.hasTransaction() >> true
        session.getTransaction() >> transaction
        transaction.isActive() >> true

        when:
        TransactionStatus status = manager.getTransaction(new DefaultTransactionDefinition(readOnly: true))

        then:
        1 * session.setFlushMode(FlushModeType.COMMIT)
        0 * transaction.setTimeout(_)

        when:
        manager.commit(status)

        then:
        0 * session.flush()
        1 * transaction.commit()
    }

    void "rollback rolls back the active transaction and clears the session"() {
        given:
        datastore.connect() >> session
        session.beginTransaction() >> transaction
        session.hasTransaction() >> true
        session.getTransaction() >> transaction
        transaction.isActive() >> true
        TransactionStatus status = manager.getTransaction(new DefaultTransactionDefinition())

        when:
        manager.rollback(status)

        then:
        1 * transaction.rollback()
        1 * session.clear()
        0 * transaction.commit()
        1 * session.disconnect()
        !((SessionHolder) TransactionSynchronizationManager.getResource(datastore)).synchronizedWithTransaction
    }

    void "inactive transactions are neither committed nor rolled back but the session is still cleared"() {
        given:
        datastore.connect() >> session
        session.beginTransaction() >> transaction
        session.hasTransaction() >> true
        session.getTransaction() >> transaction
        transaction.isActive() >> false

        when:
        manager.commit(manager.getTransaction(new DefaultTransactionDefinition()))

        then:
        0 * transaction.commit()
        0 * session.flush()

        when:
        manager.rollback(manager.getTransaction(new DefaultTransactionDefinition()))

        then:
        0 * transaction.rollback()
        1 * session.clear()
    }

    void "setting rollback only on the status rolls back on commit"() {
        given:
        datastore.connect() >> session
        session.beginTransaction() >> transaction
        session.hasTransaction() >> true
        session.getTransaction() >> transaction
        transaction.isActive() >> true
        TransactionStatus status = manager.getTransaction(new DefaultTransactionDefinition())

        when:
        status.setRollbackOnly()

        then:
        status.rollbackOnly

        when:
        manager.commit(status)

        then:
        1 * transaction.rollback()
        0 * transaction.commit()
    }

    void "commit failures are reported as transaction system exceptions"() {
        given:
        datastore.connect() >> session
        session.beginTransaction() >> transaction
        session.hasTransaction() >> true
        session.getTransaction() >> transaction
        transaction.isActive() >> true
        transaction.commit() >> { throw new InvalidDataAccessApiUsageException('commit failed') }
        TransactionStatus status = manager.getTransaction(new DefaultTransactionDefinition())

        when:
        manager.commit(status)

        then:
        TransactionSystemException e = thrown()
        e.message == 'Could not commit Datastore transaction'
        e.cause.message == 'commit failed'
        1 * session.disconnect()
    }

    void "rollback failures are reported as transaction system exceptions and still clear the session"() {
        given:
        datastore.connect() >> session
        session.beginTransaction() >> transaction
        session.hasTransaction() >> true
        session.getTransaction() >> transaction
        transaction.isActive() >> true
        transaction.rollback() >> { throw new InvalidDataAccessApiUsageException('rollback failed') }
        TransactionStatus status = manager.getTransaction(new DefaultTransactionDefinition())

        when:
        manager.rollback(status)

        then:
        TransactionSystemException e = thrown()
        e.message == 'Could not rollback Datastore transaction'
        1 * session.clear()
    }

    void "a failure to begin the transaction closes a newly connected session"() {
        given:
        datastore.connect() >> session
        session.beginTransaction() >> { throw new IllegalStateException('cannot begin') }

        when:
        manager.getTransaction(new DefaultTransactionDefinition())

        then:
        CannotCreateTransactionException e = thrown()
        e.message == 'Could not open Datastore Session for transaction'
        e.cause.message == 'cannot begin'
        1 * session.disconnect()
        0 * transaction.rollback()
        !TransactionSynchronizationManager.hasResource(datastore)
    }

    void "a failure to begin rolls back an already active transaction on the new session"() {
        given:
        datastore.connect() >> session
        session.beginTransaction() >> { throw new IllegalStateException('cannot begin') }
        session.getTransaction() >> transaction
        transaction.isActive() >> true

        when:
        manager.getTransaction(new DefaultTransactionDefinition())

        then:
        thrown(CannotCreateTransactionException)
        1 * transaction.rollback()
        1 * session.disconnect()
    }

    void "a thread bound session holder is reused and not closed after completion"() {
        given:
        SessionHolder holder = new SessionHolder(session)
        TransactionSynchronizationManager.bindResource(datastore, holder)
        session.hasTransaction() >> true
        session.getTransaction() >> transaction
        transaction.isActive() >> true

        when:
        TransactionStatus status = manager.getTransaction(new DefaultTransactionDefinition())

        then:
        0 * datastore.connect()
        1 * session.beginTransaction() >> transaction
        1 * session.setSynchronizedWithTransaction(true)
        TransactionSynchronizationManager.getResource(datastore).is(holder)

        when:
        manager.commit(status)

        then:
        1 * transaction.commit()
        0 * session.disconnect()
        1 * session.setSynchronizedWithTransaction(false)
        TransactionSynchronizationManager.getResource(datastore).is(holder)
    }

    void "datastore managed sessions use the current session and are not closed"() {
        given:
        manager.datastoreManagedSession = true
        session.hasTransaction() >> true
        session.getTransaction() >> transaction
        transaction.isActive() >> true

        when:
        TransactionStatus status = manager.getTransaction(new DefaultTransactionDefinition())

        then:
        1 * datastore.getCurrentSession() >> session
        0 * datastore.connect()
        1 * session.beginTransaction() >> transaction
        ((SessionHolder) TransactionSynchronizationManager.getResource(datastore)).session.is(session)

        when:
        manager.commit(status)

        then:
        1 * transaction.commit()
        1 * session.disconnect()
        !((SessionHolder) TransactionSynchronizationManager.getResource(datastore)).synchronizedWithTransaction
    }

    void "a missing datastore managed session is reported as a resource failure"() {
        given:
        manager.datastoreManagedSession = true
        datastore.getCurrentSession() >> { throw new ConnectionNotFoundException('none') }

        when:
        manager.getTransaction(new DefaultTransactionDefinition())

        then:
        DataAccessResourceFailureException e = thrown()
        e.message.startsWith('Could not obtain Datastore-managed Session for Spring-managed transaction')
        e.cause instanceof ConnectionNotFoundException
    }

}

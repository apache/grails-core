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

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import jakarta.persistence.FlushModeType
import org.slf4j.LoggerFactory
import org.springframework.dao.DataAccessResourceFailureException
import org.springframework.dao.InvalidDataAccessApiUsageException
import org.springframework.transaction.CannotCreateTransactionException
import org.springframework.transaction.IllegalTransactionStateException
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.TransactionSystemException
import org.springframework.transaction.TransactionUsageException
import org.springframework.transaction.UnexpectedRollbackException
import org.springframework.transaction.support.DefaultTransactionDefinition
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.transaction.support.TransactionTemplate
import spock.lang.Specification

import org.grails.datastore.mapping.core.ConnectionNotFoundException
import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.datastore.mapping.core.Session
import org.grails.datastore.mapping.core.VoidSessionCallback

/**
 * Drives {@link DatastoreTransactionManager} through {@link TransactionTemplate}, the way GORM's
 * {@code @Transactional} and {@code withTransaction} do, and checks what a commit does to the
 * session for read-write and read-only transactions, and what a transaction started inside another
 * does.
 */
class DatastoreTransactionManagerSpec extends Specification {

    Datastore datastore = Mock(Datastore)
    Session session = Mock(Session)
    Transaction transaction = Mock(Transaction)

    DatastoreTransactionManager transactionManager = new DatastoreTransactionManager(datastore: datastore)

    Logger managerLogger = LoggerFactory.getLogger(DatastoreTransactionManager) as Logger
    ListAppender<ILoggingEvent> appender = new ListAppender<>()

    void setup() {
        datastore.connect() >> session
        session.getDatastore() >> datastore
        session.beginTransaction(_ as TransactionDefinition) >> transaction
        session.hasTransaction() >> true
        session.getTransaction() >> transaction
        transaction.isActive() >> true
        // As a real session's does, disconnecting releases the holder the session was bound in
        session.disconnect() >> { TransactionSynchronizationManager.unbindResourceIfPossible(datastore) }

        appender.start()
        managerLogger.addAppender(appender)
    }

    void cleanup() {
        managerLogger.detachAppender(appender)
        // Pre-bound sessions are never disconnected by the manager
        TransactionSynchronizationManager.unbindResourceIfPossible(datastore)
    }

    void "a read-write commit flushes the session and does not warn"() {
        given:
        session.hasPendingOperations() >> true

        when:
        new TransactionTemplate(transactionManager).execute {}

        then:
        1 * session.flush()
        1 * transaction.commit()
        0 * session.setFlushMode(_)
        readOnlyWarnings.empty
    }

    void "a read-only commit with pending operations leaves them unflushed and warns"() {
        given:
        session.hasPendingOperations() >> true

        when:
        readOnlyTemplate.execute {}

        then:
        0 * session.flush()
        1 * session.setFlushMode(FlushModeType.COMMIT)
        1 * transaction.commit()

        and: "the warning names the session and says what to do instead"
        readOnlyWarnings.size() == 1
        with(readOnlyWarnings.first().formattedMessage) {
            it.contains(session.toString())
            it.contains('save(flush: true)')
            it.contains('read-write transaction')
        }
    }

    void "a read-only commit with nothing pending is silent"() {
        given:
        session.hasPendingOperations() >> false

        when:
        readOnlyTemplate.execute {}

        then:
        0 * session.flush()
        1 * transaction.commit()
        readOnlyWarnings.empty
    }

    void "a read-only rollback clears the session without warning about the operations it discards"() {
        given:
        session.hasPendingOperations() >> true

        when:
        readOnlyTemplate.execute { status -> status.setRollbackOnly() }

        then:
        0 * session.flush()
        1 * transaction.rollback()
        1 * session.clear()
        readOnlyWarnings.empty
    }

    void "a transaction started inside another joins it, and the outer one commits once"() {
        when:
        new TransactionTemplate(transactionManager).execute {
            new TransactionTemplate(transactionManager).execute {}
        }

        then: "one transaction was begun on the session, and completed by the outer commit"
        1 * session.beginTransaction(_ as TransactionDefinition) >> transaction
        1 * session.flush()
        1 * transaction.commit()
        0 * transaction.rollback()
    }

    void "a joined transaction that fails rolls back the whole transaction, even when the outer one catches it"() {
        when:
        new TransactionTemplate(transactionManager).execute {
            try {
                new TransactionTemplate(transactionManager).execute { throw new IllegalStateException('inner') }
            }
            catch (IllegalStateException ignored) {
            }
        }

        then: "the outer commit is refused rather than committing a transaction a participant failed"
        thrown(UnexpectedRollbackException)
        0 * transaction.commit()
        1 * transaction.rollback()
    }

    void "a synchronization registered in a joined transaction runs once, after the outer commit"() {
        given:
        List<String> events = []
        transaction.commit() >> { events << 'commit' }

        when:
        new TransactionTemplate(transactionManager).execute {
            new TransactionTemplate(transactionManager).execute {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    void afterCommit() {
                        events << 'afterCommit'
                    }
                })
            }
            events << 'outer resumes'
        }

        then:
        events == ['outer resumes', 'commit', 'afterCommit']
    }

    void "a read-only transaction inside a read-write one joins it and leaves the session's flush mode alone"() {
        when:
        new TransactionTemplate(transactionManager).execute {
            readOnlyTemplate.execute {}
        }

        then:
        1 * session.beginTransaction(_ as TransactionDefinition) >> transaction
        0 * session.setFlushMode(_)
        1 * session.flush()
        1 * transaction.commit()
    }

    void "a read-write transaction inside a read-only one joins it, as on Hibernate, and the commit warns about what it did not flush"() {
        given:
        session.hasPendingOperations() >> true

        when:
        readOnlyTemplate.execute {
            new TransactionTemplate(transactionManager).execute {}
        }

        then:
        1 * session.beginTransaction(_ as TransactionDefinition) >> transaction
        0 * session.flush()
        1 * transaction.commit()
        readOnlyWarnings.size() == 1
    }

    void "with validateExistingTransaction set, a read-write transaction inside a read-only one is refused"() {
        given:
        transactionManager.validateExistingTransaction = true

        when:
        readOnlyTemplate.execute {
            new TransactionTemplate(transactionManager).execute {}
        }

        then:
        thrown(IllegalTransactionStateException)
        0 * transaction.commit()
    }

    void "a REQUIRES_NEW transaction runs in a session of its own, and the outer one resumes on its session"() {
        given:
        Session innerSession = Mock(Session)
        Transaction innerTransaction = Mock(Transaction)
        innerSession.getDatastore() >> datastore
        innerSession.beginTransaction(_ as TransactionDefinition) >> innerTransaction
        innerTransaction.isActive() >> true
        TransactionTemplate requiresNew = new TransactionTemplate(transactionManager).tap {
            propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
        }
        Session boundAfterInner = null

        when:
        new TransactionTemplate(transactionManager).execute {
            requiresNew.execute {}
            boundAfterInner = (TransactionSynchronizationManager.getResource(datastore) as SessionHolder).session
        }

        then: "the inner transaction opened, used and closed a session of its own"
        2 * datastore.connect() >>> [session, innerSession]
        1 * innerSession.flush()
        1 * innerTransaction.commit()
        1 * innerSession.disconnect() >> { TransactionSynchronizationManager.unbindResourceIfPossible(datastore) }

        and: "the outer transaction carried on, and committed, on its own session"
        boundAfterInner.is(session)
        1 * session.flush()
        1 * transaction.commit()
    }

    void "a session left bound when a suspended transaction resumes is closed, and the transaction resumes on its own"() {
        given:
        Session leftover = Mock(Session)
        TransactionTemplate notSupported = new TransactionTemplate(transactionManager).tap {
            propagationBehavior = TransactionDefinition.PROPAGATION_NOT_SUPPORTED
        }
        Session boundAfter = null

        when: "code in the suspended scope binds a session and never releases it"
        new TransactionTemplate(transactionManager).execute {
            notSupported.execute { TransactionSynchronizationManager.bindResource(datastore, new SessionHolder(leftover)) }
            boundAfter = (TransactionSynchronizationManager.getResource(datastore) as SessionHolder).session
        }

        then:
        1 * leftover.disconnect()
        boundAfter.is(session)
        1 * transaction.commit()
    }

    void "a NOT_SUPPORTED scope suspends the transaction and resumes it on the same session"() {
        given:
        TransactionTemplate notSupported = new TransactionTemplate(transactionManager).tap {
            propagationBehavior = TransactionDefinition.PROPAGATION_NOT_SUPPORTED
        }
        boolean boundInside = true
        Session boundAfter = null

        when:
        new TransactionTemplate(transactionManager).execute {
            notSupported.execute { boundInside = TransactionSynchronizationManager.hasResource(datastore) }
            boundAfter = (TransactionSynchronizationManager.getResource(datastore) as SessionHolder).session
        }

        then:
        !boundInside
        boundAfter.is(session)
        1 * transaction.commit()
    }

    void "transactions run one after another on a pre-bound session each begin and commit their own, although the session's transaction always reports active"() {
        given: "a session bound to the thread, as open-session-in-view binds one; its transaction reports active even when none is in progress, as the simple map datastore's does"
        TransactionSynchronizationManager.bindResource(datastore, new SessionHolder(session))

        when:
        new TransactionTemplate(transactionManager).execute {}
        new TransactionTemplate(transactionManager).execute {}

        then: "the second is not mistaken for joining the first"
        2 * session.beginTransaction(_ as TransactionDefinition) >> transaction
        2 * transaction.commit()
    }

    void "transactional code run after a commit begins a transaction of its own, rather than joining the committed one"() {
        when:
        new TransactionTemplate(transactionManager).execute {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                void afterCommit() {
                    new TransactionTemplate(transactionManager).execute {}
                }
            })
        }

        then: "both began and committed, so the callback's writes are flushed by a commit of their own"
        2 * session.beginTransaction(_ as TransactionDefinition) >> transaction
        2 * session.flush()
        2 * transaction.commit()
    }

    void "a session that begins no transaction has nothing to commit"() {
        given:
        Session sessionWithoutTransactions = Mock(Session)
        sessionWithoutTransactions.getDatastore() >> datastore

        when:
        String result = new TransactionTemplate(transactionManager).execute { 'done' }

        then:
        1 * datastore.connect() >> sessionWithoutTransactions
        result == 'done'
    }

    void "a commit that fails reports its own failure, not the rollback of a transaction it already ended"() {
        given:
        boolean active = true

        when:
        new TransactionTemplate(transactionManager).execute {}

        then: "declared here, so they take precedence over setup()'s isActive() stub"
        _ * transaction.isActive() >> { active }
        1 * transaction.commit() >> {
            active = false
            throw new IllegalStateException('commit failed')
        }
        0 * transaction.rollback()
        IllegalStateException e = thrown()
        e.message == 'commit failed'
    }

    void "a failed joined transaction does not leave the next transaction on a pre-bound session rollback-only"() {
        given:
        SessionHolder holder = new SessionHolder(session)
        TransactionSynchronizationManager.bindResource(datastore, holder)

        when: "a joined transaction fails and the outer one catches it"
        new TransactionTemplate(transactionManager).execute {
            try {
                new TransactionTemplate(transactionManager).execute { throw new IllegalStateException('inner') }
            }
            catch (IllegalStateException ignored) {
            }
        }

        then:
        thrown(UnexpectedRollbackException)

        when: "the next transaction runs on the same session"
        new TransactionTemplate(transactionManager).execute {}

        then: "it commits"
        1 * transaction.commit()
    }

    void "a transaction in a new session, run after a joined transaction failed, commits on its own and leaves the outer one rollback-only"() {
        given:
        Session newSession = Mock(Session)
        Transaction newTransaction = Mock(Transaction)
        newSession.getDatastore() >> datastore
        newSession.beginTransaction(_ as TransactionDefinition) >> newTransaction
        newTransaction.isActive() >> true

        when: "the outer transaction catches a joined one's failure, then runs a transaction in a new session, as withNewSession does"
        new TransactionTemplate(transactionManager).execute {
            try {
                new TransactionTemplate(transactionManager).execute { throw new IllegalStateException('inner') }
            }
            catch (IllegalStateException ignored) {
            }
            DatastoreUtils.executeWithNewSession(datastore, { Session s ->
                new TransactionTemplate(transactionManager).execute {}
            } as VoidSessionCallback)
        }

        then: "the new session's transaction did not inherit the outer one's mark, and committed"
        2 * datastore.connect() >>> [session, newSession]
        1 * newSession.flush()
        1 * newTransaction.commit()
        0 * newTransaction.rollback()

        and: "its completion did not clear the outer one's mark, so the outer commit is refused"
        thrown(UnexpectedRollbackException)
        0 * transaction.commit()
        1 * transaction.rollback()
    }

    void "commit flushes, and rollback clears, the session the transaction began on, not one bound on top of it"() {
        given:
        Session onTop = Mock(Session)

        when:
        new TransactionTemplate(transactionManager).execute {
            (TransactionSynchronizationManager.getResource(datastore) as SessionHolder).addSession(onTop)
        }

        then:
        1 * session.flush()
        0 * onTop.flush()
        1 * transaction.commit()

        when:
        new TransactionTemplate(transactionManager).execute { status ->
            (TransactionSynchronizationManager.getResource(datastore) as SessionHolder).addSession(onTop)
            status.setRollbackOnly()
        }

        then:
        1 * session.clear()
        0 * onTop.clear()
        1 * transaction.rollback()
    }

    void "flushing the transaction status flushes the session the transaction began on, not one bound on top of it"() {
        given:
        Session onTop = Mock(Session)

        when:
        new TransactionTemplate(transactionManager).execute { status ->
            (TransactionSynchronizationManager.getResource(datastore) as SessionHolder).addSession(onTop)
            status.flush()
        }

        then: "once for status.flush(), once for the commit"
        2 * session.flush()
        0 * onTop.flush()
    }

    void "flushing a joined transaction's status flushes the session it joined"() {
        when:
        new TransactionTemplate(transactionManager).execute {
            new TransactionTemplate(transactionManager).execute { status -> status.flush() }
        }

        then: "once for status.flush(), once for the outer commit"
        2 * session.flush()
        1 * transaction.commit()
    }

    void "a read-only transaction puts back the flush mode it changed"() {
        given:
        session.getFlushMode() >> FlushModeType.AUTO

        when:
        readOnlyTemplate.execute {}

        then:
        1 * session.setFlushMode(FlushModeType.COMMIT)

        then: "restored once the transaction completes, so a pre-bound session is flushed as before"
        1 * session.setFlushMode(FlushModeType.AUTO)
    }

    void "a commit whose transaction is no longer active fails rather than dropping its writes"() {
        when:
        new TransactionTemplate(transactionManager).execute {}

        then:
        _ * transaction.isActive() >> false
        0 * transaction.commit()
        thrown(IllegalTransactionStateException)
    }

    void "a read-only begin that fails puts back the flush mode it changed"() {
        given:
        session.getFlushMode() >> FlushModeType.AUTO
        transaction.setTimeout(5) >> { throw new TransactionUsageException('no timeouts') }

        when:
        readOnlyTemplate.tap { timeout = 5 }.execute {}

        then:
        thrown(CannotCreateTransactionException)
        1 * session.setFlushMode(FlushModeType.COMMIT)

        then:
        1 * session.setFlushMode(FlushModeType.AUTO)
    }

    void "a begin that fails on a pre-bound session rolls back the transaction it started"() {
        given:
        TransactionSynchronizationManager.bindResource(datastore, new SessionHolder(session))
        transaction.setTimeout(5) >> { throw new TransactionUsageException('no timeouts') }

        when:
        new TransactionTemplate(transactionManager).tap { timeout = 5 }.execute {}

        then:
        thrown(CannotCreateTransactionException)
        1 * transaction.rollback()
    }

    void "the manager requires a datastore"() {
        when:
        new DatastoreTransactionManager().datastore

        then:
        IllegalArgumentException e = thrown()
        e.message == 'Cannot use DatastoreTransactionManager without a datastore set!'

        expect:
        transactionManager.datastore.is(datastore)
    }

    void "a new transaction connects a session, binds it and honours the timeout"() {
        when:
        TransactionStatus status = transactionManager.getTransaction(new DefaultTransactionDefinition(timeout: 20))

        then:
        1 * datastore.connect() >> session
        1 * session.beginTransaction(_ as TransactionDefinition) >> transaction
        1 * transaction.setTimeout(20)
        0 * session.setFlushMode(_)
        1 * session.setSynchronizedWithTransaction(true)
        status.newTransaction
        ((SessionHolder) TransactionSynchronizationManager.getResource(datastore)).session.is(session)
        ((SessionHolder) TransactionSynchronizationManager.getResource(datastore)).synchronizedWithTransaction

        when:
        transactionManager.commit(status)

        then:
        1 * session.flush()
        1 * transaction.commit()
        1 * session.disconnect()
        1 * session.setSynchronizedWithTransaction(false)
    }

    void "commit failures are reported as transaction system exceptions"() {
        given:
        transaction.commit() >> { throw new InvalidDataAccessApiUsageException('commit failed') }
        TransactionStatus status = transactionManager.getTransaction(new DefaultTransactionDefinition())

        when:
        transactionManager.commit(status)

        then:
        TransactionSystemException e = thrown()
        e.message == 'Could not commit Datastore transaction'
        e.cause.message == 'commit failed'
        1 * session.disconnect()
    }

    void "rollback failures are reported as transaction system exceptions and still clear the session"() {
        given:
        transaction.rollback() >> { throw new InvalidDataAccessApiUsageException('rollback failed') }
        TransactionStatus status = transactionManager.getTransaction(new DefaultTransactionDefinition())

        when:
        transactionManager.rollback(status)

        then:
        TransactionSystemException e = thrown()
        e.message == 'Could not rollback Datastore transaction'
        1 * session.clear()
    }

    void "setting rollback only on the status rolls back on commit"() {
        given:
        TransactionStatus status = transactionManager.getTransaction(new DefaultTransactionDefinition())

        when:
        status.setRollbackOnly()

        then:
        status.rollbackOnly

        when:
        transactionManager.commit(status)

        then:
        1 * transaction.rollback()
        0 * transaction.commit()
    }

    void "a failure to begin the transaction closes a newly connected session"() {
        when:
        transactionManager.getTransaction(new DefaultTransactionDefinition())

        then: 'declared here, as an interaction in a then block takes precedence over the one in setup'
        session.beginTransaction(_ as TransactionDefinition) >> { throw new IllegalStateException('cannot begin') }
        CannotCreateTransactionException e = thrown()
        e.message == 'Could not open Datastore Session for transaction'
        e.cause.message == 'cannot begin'
        1 * session.disconnect()
        0 * transaction.rollback()
        !TransactionSynchronizationManager.hasResource(datastore)
    }

    void "a thread bound session holder is reused and not closed after completion"() {
        given:
        SessionHolder holder = new SessionHolder(session)
        TransactionSynchronizationManager.bindResource(datastore, holder)

        when:
        TransactionStatus status = transactionManager.getTransaction(new DefaultTransactionDefinition())

        then:
        0 * datastore.connect()
        1 * session.setSynchronizedWithTransaction(true)
        TransactionSynchronizationManager.getResource(datastore).is(holder)

        when:
        transactionManager.commit(status)

        then:
        1 * transaction.commit()
        0 * session.disconnect()
        1 * session.setSynchronizedWithTransaction(false)
        TransactionSynchronizationManager.getResource(datastore).is(holder)
    }

    void "datastore managed sessions use the current session and are not closed"() {
        given:
        transactionManager.datastoreManagedSession = true

        when:
        TransactionStatus status = transactionManager.getTransaction(new DefaultTransactionDefinition())

        then:
        1 * datastore.getCurrentSession() >> session
        0 * datastore.connect()
        ((SessionHolder) TransactionSynchronizationManager.getResource(datastore)).session.is(session)

        when:
        transactionManager.commit(status)

        then:
        1 * transaction.commit()
        1 * session.disconnect()
        !((SessionHolder) TransactionSynchronizationManager.getResource(datastore)).synchronizedWithTransaction
    }

    void "a missing datastore managed session is reported as a resource failure"() {
        given:
        transactionManager.datastoreManagedSession = true
        datastore.getCurrentSession() >> { throw new ConnectionNotFoundException('none') }

        when:
        transactionManager.getTransaction(new DefaultTransactionDefinition())

        then:
        DataAccessResourceFailureException e = thrown()
        e.message.startsWith('Could not obtain Datastore-managed Session for Spring-managed transaction')
        e.cause instanceof ConnectionNotFoundException
    }

    private TransactionTemplate getReadOnlyTemplate() {
        new TransactionTemplate(transactionManager).tap { readOnly = true }
    }

    private List<ILoggingEvent> getReadOnlyWarnings() {
        appender.list.findAll { ILoggingEvent event ->
            event.level == Level.WARN && event.formattedMessage.contains('Read-only transaction')
        }
    }
}

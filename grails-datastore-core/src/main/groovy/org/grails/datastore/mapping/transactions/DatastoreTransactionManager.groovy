/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  'License'); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  'AS IS' BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.grails.datastore.mapping.transactions

import groovy.transform.CompileStatic
import jakarta.persistence.FlushModeType
import org.springframework.dao.DataAccessException
import org.springframework.dao.DataAccessResourceFailureException
import org.springframework.transaction.CannotCreateTransactionException
import org.springframework.transaction.IllegalTransactionStateException
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.TransactionException
import org.springframework.transaction.TransactionSystemException
import org.springframework.transaction.support.AbstractPlatformTransactionManager
import org.springframework.transaction.support.DefaultTransactionStatus
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.util.Assert

import org.grails.datastore.mapping.core.ConnectionNotFoundException
import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.datastore.mapping.core.Session

/**
 * A {@link org.springframework.transaction.PlatformTransactionManager} instance that
 * works with the Spring datastore abstraction.
 *
 * <p>A transaction started while another is in progress on the session the thread is using joins it
 * ({@code PROPAGATION_REQUIRED}, {@code SUPPORTS}, {@code MANDATORY}): the outer transaction commits
 * or rolls back everything once, and a joined transaction that fails marks the whole one
 * rollback-only. A session bound on top of it ({@code withNewSession}) runs transactions of its
 * own. {@code PROPAGATION_REQUIRES_NEW} suspends the outer transaction and runs in a session of its
 * own; {@code PROPAGATION_NESTED} is not supported. A read-write transaction started inside a read-only
 * one joins it and is read-only, as on Hibernate: the read-only commit does not flush, and warns when
 * that leaves writes unpersisted. Set {@code validateExistingTransaction} to refuse such a join
 * instead. Once a transaction has committed or rolled back it cannot be joined: transactional code
 * run from its {@code afterCommit} or {@code afterCompletion} callbacks begins a transaction of its
 * own.</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@SuppressWarnings('serial')
@CompileStatic
class DatastoreTransactionManager extends AbstractPlatformTransactionManager {

    private Datastore datastore
    private boolean datastoreManagedSession

    void setDatastore(Datastore datastore) {
        this.datastore = datastore
    }

    Datastore getDatastore() {
        Assert.notNull(datastore, 'Cannot use DatastoreTransactionManager without a datastore set!')
        return datastore
    }

    void setDatastoreManagedSession(boolean datastoreManagedSession) {
        this.datastoreManagedSession = datastoreManagedSession
    }

    @Override
    protected boolean isExistingTransaction(Object transaction) throws TransactionException {
        SessionHolder sessionHolder = ((TransactionObject) transaction).getSessionHolder()
        return sessionHolder != null && sessionHolder.isTransactionActive()
    }

    @Override
    protected Object doSuspend(Object transaction) throws TransactionException {
        TransactionObject txObject = (TransactionObject) transaction
        // Detached from the suspended session, so a transaction begun next (REQUIRES_NEW) opens its own
        txObject.setSessionHolder(null)
        return TransactionSynchronizationManager.unbindResource(getDatastore())
    }

    @Override
    protected void doResume(Object transaction, Object suspendedResources) throws TransactionException {
        // A session bound while the transaction was suspended (NOT_SUPPORTED) and never released would
        // otherwise leak, or stop the suspended transaction from being bound again
        SessionHolder leftover = (SessionHolder) TransactionSynchronizationManager.unbindResourceIfPossible(getDatastore())
        if (leftover != null) {
            logger.warn('A Datastore Session bound while a transaction was suspended was still bound when it ' +
                    'resumed; closing it. Its unflushed changes are discarded.')
            for (Session session in new ArrayList<Session>(leftover.getSessions())) {
                DatastoreUtils.closeSession(session)
            }
        }
        TransactionSynchronizationManager.bindResource(getDatastore(), suspendedResources)
    }

    @Override
    protected Object doGetTransaction() throws TransactionException {
        TransactionObject txObject = new TransactionObject()

        SessionHolder sessionHolder =
            (SessionHolder) TransactionSynchronizationManager.getResource(getDatastore())
        if (sessionHolder != null) {
            if (logger.isDebugEnabled()) {
                logger.debug('Found thread-bound Session [' +
                        sessionHolder.getSession() + '] for Datastore transaction')
            }
            txObject.setSessionHolder(sessionHolder)
        }
        else if (datastoreManagedSession) {
            try {
                Session session = getDatastore().getCurrentSession()
                if (logger.isDebugEnabled()) {
                    logger.debug('Found Datastore-managed Session [' +
                            session + '] for Spring-managed transaction')
                }
                txObject.setExistingSession(session)
            }
            catch (ConnectionNotFoundException ex) {
                throw new DataAccessResourceFailureException(
                        'Could not obtain Datastore-managed Session for Spring-managed transaction', ex)
            }
        }
        else {
            Session session = getDatastore().connect()
            txObject.setSession(session)
        }

        return txObject
    }

    @Override
    protected void doBegin(Object o, TransactionDefinition definition) throws TransactionException {
        TransactionObject txObject = (TransactionObject) o
        if (txObject.getSessionHolder() == null) {
            // The surrounding transaction was suspended (REQUIRES_NEW): this one gets a session of its own
            txObject.setSession(getDatastore().connect())
        }

        Session session = null
        Transaction<?> tx = null
        try {
            session = txObject.getSessionHolder().getSession()

            if (definition.isReadOnly()) {
                // FlushModeType has no MANUAL/NEVER equivalent, so this only keeps an AUTO session
                // from flushing ahead of queries. What stops a read-only transaction from writing is
                // the transaction itself declining to flush on commit, which is why the definition is
                // handed to the session below. The mode is put back when the transaction completes:
                // left at COMMIT, a pre-bound session is no longer flushed when its request ends.
                txObject.setPreviousFlushMode(session.getFlushMode())
                session.setFlushMode(FlushModeType.COMMIT)
            }

            tx = session.beginTransaction(definition)
            // Register transaction timeout.
            int timeout = determineTimeout(definition)
            if (timeout != TransactionDefinition.TIMEOUT_DEFAULT) {
                tx.setTimeout(timeout)
            }
            txObject.setTransaction(tx)
            txObject.setTransactionSession(session)

            // Bind the session holder to the thread.
            if (txObject.isNewSessionHolder()) {
                TransactionSynchronizationManager.bindResource(getDatastore(), txObject.getSessionHolder())
            }
            txObject.getSessionHolder().setSynchronizedWithTransaction(true)
            txObject.getSessionHolder().setTransactionActive(session, true)
        }
        catch (Exception ex) {
            try {
                // Undo whatever this begin started, on a pre-bound session as much as on a new one: a
                // pre-bound session outlives this failure, and would otherwise carry the transaction on
                if (tx != null && tx.isActive()) {
                    tx.rollback()
                }
            }
            catch (Throwable ex2) {
                logger.debug('Could not rollback Session after failed transaction begin', ex2)
            }
            finally {
                if (session != null && txObject.getPreviousFlushMode() != null) {
                    session.setFlushMode(txObject.getPreviousFlushMode())
                }
                if (txObject.isNewSession()) {
                    DatastoreUtils.closeSession(session)
                }
            }
            throw new CannotCreateTransactionException('Could not open Datastore Session for transaction', ex)
        }
    }

    @Override
    protected void doCommit(DefaultTransactionStatus status) throws TransactionException {
        TransactionObject txObject = (TransactionObject) status.getTransaction()
        Session session = txObject.getTransactionSession()
        Transaction<?> transaction = txObject.getTransaction()
        try {
            if (transaction == null) {
                // The session began no transaction, so there is nothing to commit
                return
            }
            if (!transaction.isActive()) {
                // Skipping the commit would drop every write this transaction made, and say nothing
                throw new IllegalTransactionStateException('The transaction begun on Session [' + session + '] is ' +
                        'no longer active, so its writes cannot be committed. It was completed or replaced outside ' +
                        'this transaction manager, for example by a transaction begun directly on the same session.')
            }
            if (!status.isReadOnly()) {
                if (session != null) {
                    if (status.isDebug()) {
                        logger.debug('Flushing Session prior to transaction commit [' + session + ']')
                    }
                    session.flush()
                }
            }
            else if (session != null && session.hasPendingOperations()) {
                logger.warn('Read-only transaction on Session [' + session + '] is committing without ' +
                        'flushing the session, which holds pending inserts, updates or deletes that this ' +
                        'transaction did not persist. Flush them explicitly with save(flush: true), or ' +
                        'perform them in a read-write transaction.')
            }
            if (status.isDebug()) {
                logger.debug('Committing Datastore transaction on Session [' + session + ']')
            }
            transaction.commit()
        }
        catch (DataAccessException ex) {
            throw new TransactionSystemException('Could not commit Datastore transaction', ex)
        }
        finally {
            ended(txObject)
        }
    }

    @Override
    protected void doRollback(DefaultTransactionStatus status) throws TransactionException {
        TransactionObject txObject = (TransactionObject) status.getTransaction()
        Session session = txObject.getTransactionSession()
        try {
            // Unlike a commit, a rollback of a transaction that has already ended has nothing to undo. It
            // happens after a failed commit, which ends the transaction first, and throwing here would
            // replace that failure with a less useful one.
            Transaction<?> transaction = txObject.getTransaction()
            if (transaction != null && transaction.isActive()) {
                if (status.isDebug()) {
                    logger.debug('Rolling back Datastore transaction on Session [' + session + ']')
                }
                transaction.rollback()
            }
        }
        catch (DataAccessException ex) {
            throw new TransactionSystemException('Could not rollback Datastore transaction', ex)
        }
        finally {
            // Clear all pending inserts/updates/deletes in the Session.
            // Necessary for pre-bound Sessions, to avoid inconsistent state.
            if (session != null) {
                session.clear()
            }
            ended(txObject)
        }
    }

    @Override
    protected void doSetRollbackOnly(DefaultTransactionStatus status) throws TransactionException {
        TransactionObject txObject = (TransactionObject) status.getTransaction()
        status.setRollbackOnly()
        // The mark goes on the session whose transaction this one joined, not on the holder: a
        // transaction begun in a session bound on top of it is its own, and must neither inherit the
        // mark nor clear it when it completes.
        Session session = txObject.getTransactionSession()
        txObject.getSessionHolder().setRollbackOnly(session)
        // A joined transaction failed, so the whole transaction will roll back. Discard what is queued
        // now: flushed explicitly before then, it would be written, and without a server-side
        // transaction nothing could take it back.
        if (session != null) {
            session.clear()
        }
    }

    /**
     * A committed or rolled back transaction can no longer be joined: code run from its
     * {@code afterCommit} or {@code afterCompletion} callbacks begins one of its own, rather than joining
     * a transaction that will not commit again and losing its writes.
     */
    private static void ended(TransactionObject txObject) {
        txObject.getSessionHolder().setTransactionActive(txObject.getTransactionSession(), false)
    }

    @Override
    protected void doCleanupAfterCompletion(Object transaction) {
        TransactionObject txObject = (TransactionObject) transaction
        SessionHolder sessionHolder = txObject.getSessionHolder()
        Session session = txObject.getTransactionSession()

        ended(txObject)
        // A joined transaction that failed marked this transaction's session rollback-only. A pre-bound
        // session outlives this transaction, and the next one begun on it must not inherit that mark.
        sessionHolder.resetRollbackOnly(session)
        if (session != null && txObject.getPreviousFlushMode() != null) {
            session.setFlushMode(txObject.getPreviousFlushMode())
        }

        // Closing the transaction's own session also un-binds the session holder from the thread.
        if (txObject.isNewSessionHolder()) {
            DatastoreUtils.closeSession(session)
        }
        sessionHolder.setSynchronizedWithTransaction(false)
    }

}

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
package org.grails.datastore.mapping.transactions;

import jakarta.persistence.FlushModeType;
import org.grails.datastore.mapping.core.ConnectionNotFoundException;
import org.grails.datastore.mapping.core.Datastore;
import org.grails.datastore.mapping.core.DatastoreUtils;
import org.grails.datastore.mapping.core.Session;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.Assert;

/**
 * A {@link org.springframework.transaction.PlatformTransactionManager} instance that
 * works with the Spring datastore abstraction
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@SuppressWarnings("serial")
public class DatastoreTransactionManager extends AbstractPlatformTransactionManager {

    private Datastore datastore;
    private boolean datastoreManagedSession;

    public void setDatastore(Datastore datastore) {
        this.datastore = datastore;
    }

    public Datastore getDatastore() {
        Assert.notNull(datastore, "Cannot use DatastoreTransactionManager without a datastore set!");
        return datastore;
    }

    public void setDatastoreManagedSession(boolean datastoreManagedSession) {
        this.datastoreManagedSession = datastoreManagedSession;
    }

    @Override
    protected Object doSuspend(Object transaction) throws TransactionException {
        TransactionObject txObject = (TransactionObject) transaction;
        TransactionSynchronizationManager.unbindResource(getDatastore());
        return txObject.getSessionHolder();
    }

    @Override
    protected void doResume(Object transaction, Object suspendedResources) throws TransactionException {
        SessionHolder conHolder = (SessionHolder) suspendedResources;
        TransactionSynchronizationManager.bindResource(getDatastore(), conHolder);
    }

    @Override
    protected Object doGetTransaction() throws TransactionException {
        TransactionObject txObject = new TransactionObject();

        SessionHolder sessionHolder =
                (SessionHolder) TransactionSynchronizationManager.getResource(getDatastore());
        if (sessionHolder != null) {
            if (logger.isDebugEnabled()) {
                logger.debug("Found thread-bound Session [" +
                        sessionHolder.getSession() + "] for Datastore transaction");
            }
            txObject.setSessionHolder(sessionHolder);
        } else if (datastoreManagedSession) {
            try {
                Session session = getDatastore().getCurrentSession();
                if (logger.isDebugEnabled()) {
                    logger.debug("Found Datastore-managed Session [" +
                            session + "] for Spring-managed transaction");
                }
                txObject.setExistingSession(session);
            } catch (ConnectionNotFoundException ex) {
                throw new DataAccessResourceFailureException(
                        "Could not obtain Datastore-managed Session for Spring-managed transaction", ex);
            }
        } else {
            Session session = getDatastore().connect();
            txObject.setSession(session);
        }

        return txObject;
    }

    @Override
    protected void doBegin(Object o, TransactionDefinition definition) throws TransactionException {
        TransactionObject txObject = (TransactionObject) o;

        Session session = null;
        try {
            session = txObject.getSessionHolder().getSession();

            if (definition.isReadOnly()) {
                // Just set to NEVER in case of a new Session for this transaction.
                session.setFlushMode(FlushModeType.COMMIT);
            }

            Transaction<?> tx = session.beginTransaction();
            // Register transaction timeout.
            int timeout = determineTimeout(definition);
            if (timeout != TransactionDefinition.TIMEOUT_DEFAULT) {
                tx.setTimeout(timeout);
            }

            // Bind the session holder to the thread.
            if (txObject.isNewSessionHolder()) {
                TransactionSynchronizationManager.bindResource(getDatastore(), txObject.getSessionHolder());
            }
            txObject.getSessionHolder().setSynchronizedWithTransaction(true);
        } catch (Exception ex) {
            if (txObject.isNewSession()) {
                try {
                    if (session != null) {
                        Transaction transaction = session.getTransaction();
                        if (transaction != null && transaction.isActive()) {
                            transaction.rollback();
                        }
                    }
                } catch (Throwable ex2) {
                    logger.debug("Could not rollback Session after failed transaction begin", ex);
                } finally {
                    DatastoreUtils.closeSession(session);
                }
            }
            throw new CannotCreateTransactionException("Could not open Datastore Session for transaction", ex);
        }
    }

    @Override
    protected void doCommit(DefaultTransactionStatus status) throws TransactionException {
        TransactionObject txObject = (TransactionObject) status.getTransaction();
        final SessionHolder sessionHolder = txObject.getSessionHolder();
        try {
            Transaction<?> transaction = txObject.getTransaction();
            if (transaction != null && transaction.isActive()) {
                Session session = sessionHolder.getSession();
                if (!status.isReadOnly()) {
                    if (session != null) {
                        if (status.isDebug()) {
                            logger.debug("Flushing Session prior to transaction commit [" + session + "]");
                        }
                        session.flush();
                    }
                }
                if (status.isDebug()) {
                    logger.debug("Committing Datastore transaction on Session [" + session + "]");
                }
                transaction.commit();
            }
        } catch (DataAccessException ex) {
            throw new TransactionSystemException("Could not commit Datastore transaction", ex);
        }
    }

    @Override
    protected void doRollback(DefaultTransactionStatus status) throws TransactionException {
        TransactionObject txObject = (TransactionObject) status.getTransaction();
        final SessionHolder sessionHolder = txObject.getSessionHolder();
        try {
            Transaction<?> transaction = txObject.getTransaction();
            if (transaction != null && transaction.isActive()) {
                if (status.isDebug()) {
                    logger.debug("Rolling back Datastore transaction on Session [" +
                            sessionHolder.getSession() + "]");
                }
                transaction.rollback();
            }
        } catch (DataAccessException ex) {
            throw new TransactionSystemException("Could not rollback Datastore transaction", ex);
        } finally {
            // Clear all pending inserts/updates/deletes in the Session.
            // Necessary for pre-bound Sessions, to avoid inconsistent state.
            if (sessionHolder.getSession() != null) {
                sessionHolder.getSession().clear();
            }
        }
    }

    @Override
    protected void doSetRollbackOnly(DefaultTransactionStatus status) throws TransactionException {
        TransactionObject txObject = (TransactionObject) status.getTransaction();
        status.setRollbackOnly();
        txObject.getSessionHolder().setRollbackOnly();
    }

    @Override
    protected void doCleanupAfterCompletion(Object transaction) {
        TransactionObject txObject = (TransactionObject) transaction;

        // Un-bind the session holder from the thread.
        if (txObject.isNewSessionHolder()) {
            DatastoreUtils.closeSession(txObject.getSessionHolder().getSession());
        }
        txObject.getSessionHolder().setSynchronizedWithTransaction(false);

    }
}

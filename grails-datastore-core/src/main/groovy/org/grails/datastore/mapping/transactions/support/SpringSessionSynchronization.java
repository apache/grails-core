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
package org.grails.datastore.mapping.transactions.support;

import org.springframework.dao.DataAccessException;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import org.grails.datastore.mapping.core.Datastore;
import org.grails.datastore.mapping.core.DatastoreUtils;
import org.grails.datastore.mapping.core.Session;
import org.grails.datastore.mapping.transactions.SessionHolder;

/**
 * An instance of {@link org.springframework.transaction.support.TransactionSynchronization}
 * for the Datastore abstraction. Based on similar work for Hibernate
 *
 * @author Juergen Hoeller
 * @author Graeme Rocher
 */
public class SpringSessionSynchronization implements TransactionSynchronization {

    private final SessionHolder sessionHolder;
    private final Datastore datastore;
    private final boolean newSession;
    private boolean holderActive = true;

    public SpringSessionSynchronization(SessionHolder sessionHolder,
            Datastore datastore, boolean newSession) {
        this.sessionHolder = sessionHolder;
        this.datastore = datastore;
        this.newSession = newSession;
    }

    /**
     * Check whether there is a Hibernate Session for the current JTA
     * transaction. Else, fall back to the default thread-bound Session.
     */
    private Session getCurrentSession() {
        return sessionHolder.getSession();
    }

    public void suspend() {
        // The session is only set aside while an inner transaction runs and is bound again by resume(), so it keeps
        // its pending changes: disconnecting it here cleared them, and a save not yet flushed was lost when the outer
        // transaction committed.
        if (holderActive) {
            TransactionSynchronizationManager.unbindResource(datastore);
        }
    }

    public void resume() {
        if (holderActive) {
            TransactionSynchronizationManager.bindResource(datastore, sessionHolder);
        }
    }

    public void flush() {
        // do nothing
    }

    public void beforeCommit(boolean readOnly) throws DataAccessException {
        // A session opened for the transaction, rather than bound before it, is flushed with it: the transaction's own
        // manager may belong to another datastore and would not flush it otherwise.
        if (newSession && !readOnly) {
            getCurrentSession().flush();
        }
    }

    public void beforeCompletion() {
        if (newSession) {
            // Default behavior: unbind and close the thread-bound Hibernate Session.
            TransactionSynchronizationManager.unbindResource(datastore);
            holderActive = false;
        }
    }

    public void afterCommit() {
    }

    public void afterCompletion(int status) {
        // No Hibernate TransactionManagerLookup: apply afterTransactionCompletion callback.
        // Always perform explicit afterTransactionCompletion callback for pre-bound Session,
        // even with Hibernate TransactionManagerLookup (which only applies to new Sessions).
        Session session = sessionHolder.getSession();
        // Close the Hibernate Session here if necessary
        // (closed in beforeCompletion in case of TransactionManagerLookup).
        if (newSession) {
            DatastoreUtils.closeSessionOrRegisterDeferredClose(session, datastore);
        }
        else {
            session.disconnect();
        }
        if (sessionHolder.doesNotHoldNonDefaultSession()) {
            sessionHolder.setSynchronizedWithTransaction(false);
        }
    }
}

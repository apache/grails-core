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
import groovy.transform.PackageScope
import jakarta.persistence.FlushModeType
import org.springframework.transaction.support.SmartTransactionObject

import org.grails.datastore.mapping.core.Session

/**
 * A transaction object returned when the transaction is created.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
class TransactionObject implements SmartTransactionObject {

    private SessionHolder sessionHolder
    private boolean newSessionHolder
    private boolean newSession
    private Transaction<?> transaction
    private Session transactionSession
    private FlushModeType previousFlushMode

    SessionHolder getSessionHolder() {
        return sessionHolder
    }

    /**
     * @return the transaction this object began, once it has begun one; otherwise the transaction of
     * the held session, if it has one
     */
    Transaction<?> getTransaction() {
        if (transaction != null) {
            return transaction
        }
        return sessionHolder != null ? sessionHolder.getTransaction() : null
    }

    /**
     * Records the transaction begun for this object, which its commit or rollback then completes.
     * Asking the session instead finds whichever transaction was begun on it last, which is not this
     * one when another transaction has been begun on the same session since.
     *
     * @param transaction the transaction begun for this object
     */
    void setTransaction(Transaction<?> transaction) {
        this.transaction = transaction
    }

    /**
     * The session the transaction began on, which its commit and {@link #flush()} flush, its rollback
     * clears and its rollback-only mark belongs to, even if another has been bound on top of it
     * since. For a transaction that joined another, the holder's current session, which is the one it
     * joined; the same for a transaction begun by a subclass that does not record its session.
     */
    @PackageScope
    Session getTransactionSession() {
        if (transactionSession != null) {
            return transactionSession
        }
        return sessionHolder != null ? sessionHolder.getSession() : null
    }

    @PackageScope
    void setTransactionSession(Session transactionSession) {
        this.transactionSession = transactionSession
    }

    /**
     * @return the flush mode the session had before a read-only transaction changed it, which is put
     * back when the transaction completes; {@code null} when the transaction did not change it
     */
    FlushModeType getPreviousFlushMode() {
        return previousFlushMode
    }

    /**
     * Records the flush mode a transaction manager changes on beginning a read-only transaction, so
     * that {@link DatastoreTransactionManager} puts it back when the transaction completes.
     *
     * @param previousFlushMode the session's flush mode before the change
     */
    void setPreviousFlushMode(FlushModeType previousFlushMode) {
        this.previousFlushMode = previousFlushMode
    }

    void setSession(Session session) {
        this.sessionHolder = new SessionHolder(session)
        this.newSessionHolder = true
        this.newSession = true
    }

    void setExistingSession(Session session) {
        this.sessionHolder = new SessionHolder(session)
        this.newSessionHolder = true
        this.newSession = false
    }

    void setSessionHolder(SessionHolder sessionHolder) {
        this.sessionHolder = sessionHolder
        this.newSessionHolder = false
        this.newSession = false
    }

    boolean isNewSessionHolder() {
        return newSessionHolder
    }

    boolean isNewSession() {
        return newSession
    }

    @Override
    boolean isRollbackOnly() {
        return sessionHolder != null && sessionHolder.isRollbackOnly(getTransactionSession())
    }

    @Override
    void flush() {
        getTransactionSession().flush()
    }

}

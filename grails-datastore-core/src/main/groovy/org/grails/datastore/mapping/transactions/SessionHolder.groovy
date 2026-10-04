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

import java.util.concurrent.LinkedBlockingDeque

import groovy.transform.CompileStatic
import org.springframework.transaction.support.ResourceHolderSupport

import org.grails.datastore.mapping.core.Session

/**
 * Holds a reference to one or more sessions.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
class SessionHolder extends ResourceHolderSupport {

    private Deque<Session> sessions = new LinkedBlockingDeque<>()
    private Object creator = null
    // Synchronized to match the session deque, which is concurrent
    private final Set<Session> transactionSessions = Collections.synchronizedSet(Collections.newSetFromMap(new IdentityHashMap<Session, Boolean>()))
    private final Set<Session> rollbackOnlySessions = Collections.synchronizedSet(Collections.newSetFromMap(new IdentityHashMap<Session, Boolean>()))

    SessionHolder(Session session) {
        sessions.add(session)
    }

    SessionHolder(Session session, Object creator) {
        this(session)
        this.creator = creator
    }

    Object getCreator() {
        return creator
    }

    Transaction<?> getTransaction() {
        final Session session = getSession()
        if (session != null && session.hasTransaction()) {
            return session.getTransaction()
        }
        return null
    }

    @Override
    void setSynchronizedWithTransaction(boolean synchronizedWithTransaction) {
        for (Session session in sessions) {
            session.setSynchronizedWithTransaction(synchronizedWithTransaction)
        }
        super.setSynchronizedWithTransaction(synchronizedWithTransaction)
    }

    @Deprecated
    void setTransaction(Transaction<?> transaction) {
        // no-op. here for compatibility
    }

    /**
     * Whether a {@link DatastoreTransactionManager} transaction is in progress on the session this
     * holder hands out ({@link #getSession()}, the most recently bound): from when the manager begins
     * one on it until that transaction commits or rolls back. A transaction started meanwhile joins it
     * rather than beginning another on the same session. A session bound on top of it, as
     * {@code withNewSession} binds one, has no transaction in progress until one is begun on it.
     *
     * <p>Not {@code getTransaction().isActive()}: a session's transaction object can outlive its
     * transaction, and some report themselves active whether or not one is in progress.</p>
     *
     * @return {@code true} while a transaction is in progress on the session this holder hands out
     */
    boolean isTransactionActive() {
        Session session = getSession()
        return session != null && transactionSessions.contains(session)
    }

    /**
     * Records that a transaction is, or is no longer, in progress on the given session.
     *
     * @param session the session the transaction was begun on
     * @param active whether it is in progress
     */
    void setTransactionActive(Session session, boolean active) {
        if (active) {
            transactionSessions.add(session)
        }
        else {
            transactionSessions.remove(session)
        }
    }

    /**
     * Marks the transaction in progress on the given session rollback-only, as a transaction that
     * joined it and failed does. The mark belongs to that session's transaction alone: a transaction
     * begun in a session bound on top of it, as {@code withNewSession} binds one, neither inherits it
     * nor clears it when it completes. {@link #setRollbackOnly()} still marks the holder as a whole.
     *
     * @param session the session the transaction was begun on
     */
    void setRollbackOnly(Session session) {
        rollbackOnlySessions.add(session)
    }

    /**
     * @param session the session the transaction was begun on
     * @return whether the transaction in progress on the given session has been marked rollback-only
     * with {@link #setRollbackOnly(Session)}
     */
    boolean isRollbackOnly(Session session) {
        return rollbackOnlySessions.contains(session)
    }

    /**
     * Clears the mark {@link #setRollbackOnly(Session)} made, once the session's transaction has
     * completed, so that the next transaction begun on the session does not inherit it.
     *
     * @param session the session the transaction was begun on
     */
    void resetRollbackOnly(Session session) {
        rollbackOnlySessions.remove(session)
    }

    @Override
    void clear() {
        super.clear()
        transactionSessions.clear()
        rollbackOnlySessions.clear()
    }

    Session getSession() {
        return sessions.peekLast()
    }

    /**
     * @return An unmodifiable view of every session held, in binding order
     */
    Collection<Session> getSessions() {
        return Collections.unmodifiableCollection(sessions)
    }

    boolean isEmpty() {
        return sessions.isEmpty()
    }

    boolean doesNotHoldNonDefaultSession() {
        return isEmpty()
    }

    void addSession(Session session) {
        sessions.add(session)
    }

    void removeSession(Session session) {
        sessions.remove(session)
    }

    boolean containsSession(Session session) {
        return sessions.contains(session)
    }

    int size() {
        return sessions.size()
    }

    Session getValidatedSession() {
        Session session = getSession()
        if (session != null && !session.isConnected()) {
            removeSession(session)
            session = null
        }
        return session
    }

}

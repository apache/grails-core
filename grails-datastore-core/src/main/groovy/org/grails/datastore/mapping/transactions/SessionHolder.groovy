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

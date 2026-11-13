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

package org.grails.datastore.mapping.core

import groovy.transform.CompileStatic

import org.springframework.transaction.support.TransactionSynchronizationManager

import org.grails.datastore.mapping.transactions.SessionHolder

/**
 * The default {@link SessionResolver}, backed by the same {@link SessionHolder}/
 * {@link TransactionSynchronizationManager} state as {@link DatastoreUtils}'s session binding -
 * a thin, stateless view over the one authoritative session stack for its owning datastore, rather
 * than an independent thread-local store that could disagree with it. Nested bindings are supported
 * because {@link SessionHolder} itself is a stack: {@link #bind(Session)} pushes, {@link #resolve()}
 * returns the topmost still-connected session, and {@link #unbind()} closes and pops only the top
 * session, restoring the outer binding rather than discarding the whole holder.
 * <p>
 * {@link #resolve()} performs the same housekeeping as {@code DatastoreUtils.doGetSession}:
 * disconnected sessions are evicted from the holder, and a holder left empty by eviction is
 * unbound (unless it is synchronized with an active transaction, in which case the transaction
 * manager owns its lifecycle) so that a stale binding can never block a later
 * {@code bindSession}.
 *
 * @author Walter Duque de Estrada
 * @since 8.0
 */
@CompileStatic
class TransactionSynchronizationSessionResolver implements SessionResolver {

    private final Datastore datastore

    TransactionSynchronizationSessionResolver(Datastore datastore) {
        this.datastore = datastore
    }

    @Override
    Session resolve() {
        SessionHolder holder = (SessionHolder) TransactionSynchronizationManager.getResource(datastore)
        if (holder == null) {
            return null
        }
        Session session = holder.getValidatedSession()
        while (session == null && !holder.isEmpty()) {
            session = holder.getValidatedSession()
        }
        if (session == null && !holder.isSynchronizedWithTransaction()) {
            TransactionSynchronizationManager.unbindResourceIfPossible(datastore)
        }
        return session
    }

    @Override
    boolean hasResolvedSession() {
        SessionHolder holder = (SessionHolder) TransactionSynchronizationManager.getResource(datastore)
        if (holder == null) {
            return false
        }
        for (Session session in holder.sessions) {
            if (session.isConnected()) {
                return true
            }
        }
        return false
    }

    @Override
    void bind(Session session) {
        if (session.getDatastore() != datastore) {
            throw new IllegalArgumentException(
                    "Cannot bind session [$session]: it belongs to datastore [${session.getDatastore()}], not [$datastore]")
        }
        DatastoreUtils.bindNewSession(session)
    }

    @Override
    void unbind() {
        Session session = resolve()
        if (session != null) {
            DatastoreUtils.unbindSession(session)
        }
    }
}

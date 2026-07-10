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
 * returns the top, and {@link #unbind()} clears the whole binding for the current thread.
 *
 * @author borinquenkid
 * @since 8.0
 */
@CompileStatic
class ThreadLocalSessionResolver<S extends Session> implements SessionResolver<S> {

    private final Datastore datastore

    ThreadLocalSessionResolver(Datastore datastore) {
        this.datastore = datastore
    }

    @Override
    S resolve() {
        SessionHolder holder = (SessionHolder) TransactionSynchronizationManager.getResource(datastore)
        return holder != null ? (S) holder.getSession() : null
    }

    @Override
    void bind(S session) {
        DatastoreUtils.bindNewSession(session)
    }

    @Override
    void unbind() {
        TransactionSynchronizationManager.unbindResourceIfPossible(datastore)
    }
}

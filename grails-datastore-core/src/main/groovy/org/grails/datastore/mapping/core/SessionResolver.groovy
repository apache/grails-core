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

/**
 * Resolver for the current session bound to a datastore in the current context (thread).
 * Implementations compose over {@link org.grails.datastore.mapping.transactions.SessionHolder}/
 * {@link org.springframework.transaction.support.TransactionSynchronizationManager} rather than
 * maintaining independent state, so this never disagrees with Spring's own transactional session
 * bookkeeping.
 *
 * @author Walter Duque de Estrada
 * @since 8.0
 */
@CompileStatic
interface SessionResolver {

    /**
     * Resolves the current valid session bound in the current context (thread), or {@code null}
     * if none is bound. Implementations must return only sessions that
     * {@link Session#isConnected()} - stale, disconnected sessions are evicted rather than
     * returned (and a binding left empty by eviction is cleaned up), matching
     * {@code DatastoreUtils.doGetSession}'s validation semantics.
     */
    Session resolve()

    /**
     * Non-mutating check for whether a connected session is currently bound - the same
     * determination {@link #resolve()} makes, but without evicting any stale, disconnected
     * session it finds along the way or unbinding a holder left empty by that eviction.
     * Safe to call from routing/discovery code that scans multiple datastores and must not have
     * side effects on bindings unrelated to the one it ultimately selects.
     */
    boolean hasResolvedSession()

    /**
     * Binds a session owned by this resolver's datastore to the current context, making it the
     * session {@link #resolve()} returns. Nested bindings stack: binding a second session pushes
     * it on top of the first.
     *
     * @throws IllegalArgumentException if the session belongs to a different datastore
     */
    void bind(Session session)

    /**
     * Unbinds <b>and closes</b> the current session, restoring the previously-bound session (if
     * any) as current. Equivalent to {@code DatastoreUtils.unbindSession(resolve())}: the popped
     * session is closed (or registered for deferred close), so callers must not continue using a
     * session after unbinding it. When no valid session is bound, no session is closed (though
     * the same stale-binding housekeeping as {@link #resolve()} may still occur).
     */
    void unbind()
}

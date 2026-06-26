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
 * A default thread-bound SessionResolver. Both the default session and all qualifier-keyed
 * sessions are stored in {@link ThreadLocal}s so they are naturally isolated per-thread
 * and cannot leak across concurrent test forks or request threads.
 *
 * @author borinquenkid
 * @since 8.0
 */
@CompileStatic
class ThreadLocalSessionResolver<S extends Session> implements SessionResolver<S> {

    private final ThreadLocal<S> currentSession = new ThreadLocal<>()
    private final ThreadLocal<Map<String, S>> qualifiedSessions = ThreadLocal.withInitial { [:] as Map<String, S> }

    @Override
    S resolve() {
        return currentSession.get()
    }

    @Override
    S resolve(String qualifier) {
        return qualifiedSessions.get().get(qualifier)
    }

    @Override
    void bind(S session) {
        currentSession.set(session)
    }

    void bind(String qualifier, S session) {
        qualifiedSessions.get().put(qualifier, session)
    }

    @Override
    void unbind() {
        currentSession.remove()
        qualifiedSessions.remove()
    }

    void unbind(String qualifier) {
        qualifiedSessions.get().remove(qualifier)
    }
}

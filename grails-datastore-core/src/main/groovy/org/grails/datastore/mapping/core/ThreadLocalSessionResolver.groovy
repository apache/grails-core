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

package org.grails.datastore.mapping.core;

import groovy.transform.CompileStatic;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A default thread-bound SessionResolver
 *
 * @author borinquenkid
 * @since 8.0
 */
@CompileStatic
public class ThreadLocalSessionResolver<S extends Session> implements SessionResolver<S> {

    private final ThreadLocal<S> currentSession = new ThreadLocal<>();
    private final Map<String, S> qualifiedSessions = new ConcurrentHashMap<>();

    @Override
    public S resolve() {
        return currentSession.get();
    }

    @Override
    public S resolve(String qualifier) {
        return qualifiedSessions.get(qualifier);
    }

    @Override
    public void bind(S session) {
        currentSession.set(session);
        // Note: In a production scenario, we'd need to link the session's datastore qualifier here.
    }

    public void bind(String qualifier, S session) {
        qualifiedSessions.put(qualifier, session);
    }

    @Override
    public void unbind() {
        currentSession.remove();
    }
    
    public void unbind(String qualifier) {
        qualifiedSessions.remove(qualifier);
    }
}

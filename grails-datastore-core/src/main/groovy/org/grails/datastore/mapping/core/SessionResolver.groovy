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

/**
 * Resolver for sessions in the current context (thread, tenant, etc)
 *
 * @author borinquenkid
 * @since 8.0
 */
@CompileStatic
public interface SessionResolver<S extends Session> {
    /** Resolves the current session based on current context (thread, tenant, etc) */
    S resolve();

    /** Resolves a session for a specific qualifier/tenant */
    S resolve(String qualifier);

    /** Binds a session to the current context */
    void bind(S session);

    /** Unbinds the current session */
    void unbind();
}

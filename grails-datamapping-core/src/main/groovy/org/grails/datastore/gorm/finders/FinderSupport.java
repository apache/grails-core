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
package org.grails.datastore.gorm.finders;

import org.grails.datastore.mapping.core.Datastore;
import org.grails.datastore.mapping.core.DatastoreUtils;
import org.grails.datastore.mapping.core.SessionCallback;
import org.grails.datastore.mapping.core.VoidSessionCallback;

/**
 * Shared session-execution helper for the synchronous finder implementations in this package.
 * Not a base class - each finder holds a {@link Datastore} field and calls these statically,
 * rather than inheriting an {@code execute} method.
 */
public final class FinderSupport {

    private FinderSupport() {
    }

    /**
     * Executes the given callback within a session bound to the given datastore.
     *
     * @param datastore The datastore, or null for stateless mode
     * @param callback The callback
     * @param <T> The callback's result type
     * @return The callback's result
     * @throws IllegalStateException if datastore is null (stateless mode)
     */
    public static <T> T execute(final Datastore datastore, final SessionCallback<T> callback) {
        if (datastore != null) {
            return DatastoreUtils.execute(datastore, callback);
        }
        throw new IllegalStateException("Cannot execute session query in stateless mode");
    }

    /**
     * Executes the given void callback within a session bound to the given datastore.
     *
     * @param datastore The datastore, or null for stateless mode
     * @param callback The callback
     * @throws IllegalStateException if datastore is null (stateless mode)
     */
    public static void execute(final Datastore datastore, final VoidSessionCallback callback) {
        if (datastore != null) {
            DatastoreUtils.execute(datastore, callback);
        }
        else {
            throw new IllegalStateException("Cannot execute session query in stateless mode");
        }
    }
}

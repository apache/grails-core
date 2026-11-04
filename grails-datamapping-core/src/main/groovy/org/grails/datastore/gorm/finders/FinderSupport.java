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

import org.grails.datastore.gorm.DatastoreResolver;
import org.grails.datastore.mapping.core.Datastore;
import org.grails.datastore.mapping.core.DatastoreUtils;
import org.grails.datastore.mapping.core.SessionCallback;
import org.grails.datastore.mapping.core.VoidSessionCallback;

/**
 * Shared session-execution helper for the synchronous finder implementations in this package.
 * Not a base class - each finder holds a {@link DatastoreResolver} field and calls these
 * statically, rather than inheriting an {@code execute} method. The datastore is resolved on
 * every call, so a finder built from a lazy resolver follows whatever datastore the resolver
 * returns at invocation time.
 */
public final class FinderSupport {

    private static final String NULL_DATASTORE_MESSAGE = "Cannot execute session query with null datastore";

    private FinderSupport() {
    }

    /**
     * Wraps a fixed datastore as a resolver.
     *
     * @param datastore The datastore, or null for stateless mode
     * @return A resolver that always returns the given datastore, or null when the datastore is null
     */
    public static DatastoreResolver resolverFor(final Datastore datastore) {
        return datastore == null ? null : () -> datastore;
    }

    /**
     * Executes the given callback within a session bound to the datastore the resolver returns.
     *
     * @param datastoreResolver The datastore resolver, or null for stateless mode
     * @param callback The callback
     * @param <T> The callback's result type
     * @return The callback's result
     * @throws IllegalStateException if no datastore can be resolved (stateless mode)
     */
    public static <T> T execute(final DatastoreResolver datastoreResolver, final SessionCallback<T> callback) {
        return DatastoreUtils.execute(resolve(datastoreResolver), callback);
    }

    /**
     * Executes the given void callback within a session bound to the datastore the resolver returns.
     *
     * @param datastoreResolver The datastore resolver, or null for stateless mode
     * @param callback The callback
     * @throws IllegalStateException if no datastore can be resolved (stateless mode)
     */
    public static void execute(final DatastoreResolver datastoreResolver, final VoidSessionCallback callback) {
        DatastoreUtils.execute(resolve(datastoreResolver), callback);
    }

    private static Datastore resolve(final DatastoreResolver datastoreResolver) {
        Datastore datastore = datastoreResolver == null ? null : datastoreResolver.resolve();
        if (datastore == null) {
            throw new IllegalStateException(NULL_DATASTORE_MESSAGE);
        }
        return datastore;
    }
}

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

package org.grails.datastore.mapping.core.connections;

import org.grails.datastore.mapping.core.Datastore;

/**
 * A {@link Datastore} capable of configuring multiple {@link Datastore} with individually named {@link ConnectionSource} instances
 *
 * @author Graeme Rocher
 * @since 6.1
 */
public interface MultipleConnectionSourceCapableDatastore extends Datastore {

    /**
     * Lookup a {@link Datastore} by {@link ConnectionSource} name
     *
     * @param connectionName The connection name
     * @return The {@link Datastore}
     */
    Datastore getDatastoreForConnection(String connectionName);

    /**
     * Whether an entity mapped only to non-default connection sources should route its unqualified
     * (no explicit connection) operations to its first mapped connection's datastore.
     *
     * Real datastores manage an independent session per connection, so such an entity's default
     * operations belong to that mapped connection's datastore. The in-memory mock used for unit
     * testing manages a single session on this (parent) datastore and only fabricates isolated
     * children for explicit connection access, so it overrides this to keep unqualified operations
     * on the parent — otherwise they would target a child whose session the test harness never
     * flushes.
     *
     * @return {@code true} to route unqualified operations to the mapped connection datastore
     */
    default boolean routesUnqualifiedToMappedConnection() {
        return true;
    }
}

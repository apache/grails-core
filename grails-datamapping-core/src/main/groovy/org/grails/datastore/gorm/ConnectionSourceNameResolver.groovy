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
package org.grails.datastore.gorm

import groovy.transform.CompileStatic
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.core.connections.ConnectionSources
import org.grails.datastore.mapping.core.connections.ConnectionSourcesProvider

/**
 * Resolves connection source names from a datastore.
 *
 * @author Graeme Rocher
 */
@CompileStatic
class ConnectionSourceNameResolver {

    /**
     * Resolve all connection source names from a datastore.
     * Returns a list of connection source names, or defaults to [ConnectionSource.DEFAULT] if none found.
     *
     * @param datastore The datastore to resolve names from
     * @return List of connection source names
     */
    static List<String> resolveConnectionSourceNames(Object datastore) {
        if (datastore instanceof ConnectionSourcesProvider) {
            ConnectionSources connectionSources = ((ConnectionSourcesProvider) datastore).connectionSources
            if (connectionSources != null) {
                Iterable<ConnectionSource> allConnections = connectionSources.allConnectionSources
                if (allConnections instanceof Collection) {
                    List<String> names = ((Collection<ConnectionSource>) allConnections).collect { it.name }
                    return names.isEmpty() ? [ConnectionSource.DEFAULT] : names
                } else {
                    return allConnections?.collect { it.name } ?: [ConnectionSource.DEFAULT]
                }
            }
        }
        return [ConnectionSource.DEFAULT]
    }

    /**
     * Resolve the default connection source name from a datastore.
     * Returns the default connection source name, or ConnectionSource.DEFAULT if none found.
     *
     * @param datastore The datastore to resolve the name from
     * @return The default connection source name
     */
    static String resolveDefaultConnectionSourceName(Object datastore) {
        if (datastore instanceof ConnectionSourcesProvider) {
            return ((ConnectionSourcesProvider) datastore).connectionSources?.defaultConnectionSource?.name ?: ConnectionSource.DEFAULT
        }
        return ConnectionSource.DEFAULT
    }

    /**
     * Whether the given name is one of the datastore's configured connection sources.
     *
     * This answers "is this qualifier a datasource name or a tenant id?" without probing
     * {@code getDatastoreForConnection}, which throws for an unknown name and would put stack-trace
     * construction on the per-operation path of every discriminator-mode query.
     *
     * @param datastore The datastore to inspect
     * @param name The candidate connection source name
     * @return {@code true} if the datastore declares a connection source with that name
     */
    static boolean isConnectionSourceName(Object datastore, String name) {
        if (name == null || !(datastore instanceof ConnectionSourcesProvider)) {
            return false
        }
        ConnectionSources connectionSources = ((ConnectionSourcesProvider) datastore).connectionSources
        if (connectionSources == null) {
            return false
        }
        // Check the default first: it is the common case, needs no iteration, and a child datastore
        // built for a single connection resolves its own name through it.
        if (name == connectionSources.defaultConnectionSource?.name) {
            return true
        }
        Iterable<ConnectionSource> allConnections = connectionSources.allConnectionSources
        if (allConnections == null) {
            return false
        }
        for (ConnectionSource connectionSource in allConnections) {
            if (name == connectionSource.name) {
                return true
            }
        }
        return false
    }
}

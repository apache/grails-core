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
}

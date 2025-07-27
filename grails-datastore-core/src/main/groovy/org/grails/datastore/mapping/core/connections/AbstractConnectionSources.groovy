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

package org.grails.datastore.mapping.core.connections

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic

import org.springframework.core.env.PropertyResolver

import org.grails.datastore.mapping.core.DatastoreUtils

/**
 * Abstract implementation of the {@link ConnectionSources} interface
 *
 * @author Graeme Rocher
 * @since 6.0
 */
@CompileStatic
abstract class AbstractConnectionSources <T, S extends ConnectionSourceSettings> implements ConnectionSources<T, S> {

    protected final ConnectionSource<T, S> defaultConnectionSource
    protected final ConnectionSourceFactory<T, S> connectionSourceFactory
    protected final PropertyResolver configuration
    protected final Collection<ConnectionSourcesListener<T,S>> listeners = []

    AbstractConnectionSources(ConnectionSource<T, S> defaultConnectionSource, ConnectionSourceFactory<T, S> connectionSourceFactory, PropertyResolver configuration) {
        if(connectionSourceFactory == null) {
            throw new IllegalArgumentException("Argument [connectionSourceFactory] cannot be null")
        }
        if(defaultConnectionSource == null) {
            throw new IllegalStateException("The default ConnectionSource cannot be null!")
        }
        if(configuration == null) {
            this.configuration = DatastoreUtils.createPropertyResolver(Collections.emptyMap())
        }
        else {
            this.configuration = configuration
        }
        this.defaultConnectionSource = defaultConnectionSource
        this.connectionSourceFactory = connectionSourceFactory
    }

    @Override
    PropertyResolver getBaseConfiguration() {
        return this.configuration
    }

    /**
     * Implementors should provide a method to lookup the data source names, which could be read from a database, configuration etc.
     *
     * @param connectionSourceFactory The factory class for construction {@link ConnectionSource} instances
     * @param configuration The root configuration
     * @return An iterable of connection source names. Should never return null.
     */
    protected Iterable<String> getConnectionSourceNames(ConnectionSourceFactory<T, S> connectionSourceFactory, PropertyResolver configuration) {
        Map<String, Object> allConnectionSources = configuration.getProperty(connectionSourceFactory.getConnectionSourcesConfigurationKey().toString(), Map.class, Collections.emptyMap())
        return toValidConnectionSourceNames(allConnectionSources)
    }

    static Set<String> toValidConnectionSourceNames(Map<String, Object> allConnectionSources) {
        Set<String> names = allConnectionSources.keySet()
        Set<String> newNames = new LinkedHashSet<>()
        for (String name : names) {
            int i = name.indexOf('.')
            if(i > -1) {
                newNames.add( name.substring(0,i) )
            }
            else {
                newNames.add(name)
            }
        }
        return newNames
    }

    @Override
    ConnectionSourceFactory<T, S> getFactory() {
        return this.connectionSourceFactory
    }

    @Override
    ConnectionSource<T, S> getDefaultConnectionSource() {
        return this.defaultConnectionSource
    }

    @Override
    void close() throws IOException {
        for(ConnectionSource connectionSource : allConnectionSources) {
            connectionSource.close()
        }
    }

    @Override
    Iterator<ConnectionSource<T, S>> iterator() {
        allConnectionSources.iterator()
    }

    @Override
    @CompileDynamic
    ConnectionSource<T, S> addConnectionSource(String name, Map<String, Object> configuration) {
        return addConnectionSource(name, DatastoreUtils.createPropertyResolver(configuration))
    }

    @Override
    ConnectionSources<T, S> addListener(ConnectionSourcesListener<T, S> listener) {
        listeners.add(listener)
        return this
    }
}

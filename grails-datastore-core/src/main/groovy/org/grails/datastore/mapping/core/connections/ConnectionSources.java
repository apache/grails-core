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

import org.springframework.core.env.PropertyResolver;

import java.io.Closeable;
import java.util.Map;

/**
 * Models multiple connection sources
 *
 * @author Graeme Rocher
 * @since 6.0
 *
 * @param <T> The underlying native type of the {@link ConnectionSource}, for example a SQL {@link javax.sql.DataSource}
 */
public interface ConnectionSources<T, S extends ConnectionSourceSettings> extends Iterable<ConnectionSource<T, S>>, Closeable {

    /**
     * @return Obtains the base configuration
     */
    PropertyResolver getBaseConfiguration();
    /**
     * @return The factory used to create new connections
     */
    ConnectionSourceFactory<T, S> getFactory();

    /**
     * @return An iterable containing all {@link ConnectionSource} instances
     */
    Iterable<ConnectionSource<T, S>> getAllConnectionSources();

    /**
     * Obtain a {@link ConnectionSource} by name
     *
     * @param name The name of the source
     *
     * @return A {@link ConnectionSource} or null if it doesn't exist
     */
    ConnectionSource<T, S> getConnectionSource(String name);

    /**
     * Obtains the default {@link ConnectionSource}
     *
     * @return The default {@link ConnectionSource}
     */
    ConnectionSource<T, S> getDefaultConnectionSource();

    /**
     * Adds a new {@link ConnectionSource}
     *
     * @param name The name of the connection source
     * @param configuration The configuration
     * @return The {@link ConnectionSource}
     *
     * @throws org.grails.datastore.mapping.core.exceptions.ConfigurationException if the configuration is invalid
     */
    ConnectionSource<T, S> addConnectionSource(String name, PropertyResolver configuration);


    /**
     * Adds a new {@link ConnectionSource}
     *
     * @param name The name of the connection source
     * @param configuration The configuration
     * @return The {@link ConnectionSource}
     *
     * @throws org.grails.datastore.mapping.core.exceptions.ConfigurationException if the configuration is invalid
     */
    ConnectionSource<T, S> addConnectionSource(String name, Map<String,Object> configuration);

    /**
     * Adds a listener
     *
     * @param listener The listener
     * @return This connection sources
     */
    ConnectionSources<T,S> addListener(ConnectionSourcesListener<T,S> listener);
}

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

import java.io.Serializable;

import org.springframework.core.env.PropertyResolver;

import org.grails.datastore.mapping.config.Settings;

/**
 * A factory for creating new {@link ConnectionSource} instances
 *
 * @author Graeme Rocher
 * @since 6.0
 */
public interface ConnectionSourceFactory<T, S extends ConnectionSourceSettings> extends Settings {

    /**
     * Creates a new {@link ConnectionSource} for the given name and configuration
     *
     * @param name The name
     * @param configuration The configuration
     * @return The {@link ConnectionSource}
     *
     * @throws org.grails.datastore.mapping.core.exceptions.ConfigurationException If there is an issue with the configuration
     */
    ConnectionSource<T, S> create(String name, PropertyResolver configuration);

    /**
     * Creates a new {@link ConnectionSource} for the given name and settings
     *
     * @param name The name
     * @param settings The settings
     * @return The {@link ConnectionSource}
     *
     * @throws org.grails.datastore.mapping.core.exceptions.ConfigurationException If there is an issue with the configuration
     */
    ConnectionSource<T, S> create(String name, S settings);

    /**
     * Creates a new {@link ConnectionSource} for the given name and configuration
     *
     * @param name The name
     * @param configuration The configuration
     * @param fallbackConnectionSource The fallback connection source
     *
     * @return The {@link ConnectionSource}
     *
     * @throws org.grails.datastore.mapping.core.exceptions.ConfigurationException If there is an issue with the configuration
     */
    ConnectionSource<T, S> create(String name, PropertyResolver configuration, ConnectionSource<T, S> fallbackConnectionSource);

    /**
     * Creates a new {@link ConnectionSource} for the given name and configuration
     *
     * @param name The name
     * @param configuration The configuration
     * @param fallbackSettings The settings to fallback to if none are specified
     *
     * @return The {@link ConnectionSource}
     *
     * @throws org.grails.datastore.mapping.core.exceptions.ConfigurationException If there is an issue with the configuration
     */
    <F extends ConnectionSourceSettings> ConnectionSource<T, S> create(String name, PropertyResolver configuration, F fallbackSettings);

    /**
     * @return Obtain the prefix used to obtain the default configuration. For example "grails.mongodb" or "grails.neo4j"
     */
    Serializable getConnectionSourcesConfigurationKey();

    /**
     * Creates a connection at runtime. This method differs from the `create` method is that it handles the runtime creation (as oppose to boot time) creation of connection sources
     * @param name The name of the connection source
     * @param configuration The configuration
     * @param fallbackSettings The fallback settings
     * @return The new connection source
     */
    ConnectionSource<T,S> createRuntime(String name, PropertyResolver configuration, S fallbackSettings);
}

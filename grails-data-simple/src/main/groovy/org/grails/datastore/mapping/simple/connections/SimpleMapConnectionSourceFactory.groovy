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

package org.grails.datastore.mapping.simple.connections

import groovy.transform.CompileStatic
import org.grails.datastore.mapping.core.connections.AbstractConnectionSourceFactory
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.core.connections.ConnectionSourceSettings
import org.grails.datastore.mapping.core.connections.ConnectionSourceSettingsBuilder
import org.grails.datastore.mapping.core.connections.DefaultConnectionSource
import org.springframework.core.env.PropertyResolver

import java.util.concurrent.ConcurrentHashMap

/**
 * Simple implementation that just builds {@link ConnectionSource} instances from Maps
 *
 * @author Graeme Rocher
 * @since 6.0
 */
@CompileStatic
class SimpleMapConnectionSourceFactory extends AbstractConnectionSourceFactory<Map<String, Map>, ConnectionSourceSettings> {

    @Override
    ConnectionSource<Map<String, Map>, ConnectionSourceSettings> create(String name, ConnectionSourceSettings settings) {
        return new DefaultConnectionSource<Map<String, Map>, ConnectionSourceSettings>(name, new ConcurrentHashMap<String, Map>(), settings)
    }

    @Override
    Serializable getConnectionSourcesConfigurationKey() {
        return PREFIX + ".connections"
    }

    @Override
    protected <F extends ConnectionSourceSettings> ConnectionSourceSettings buildSettings(String name, PropertyResolver configuration, F fallbackSettings, boolean isDefaultDataSource) {
        ConnectionSourceSettingsBuilder builder = new ConnectionSourceSettingsBuilder(configuration, PREFIX)
        return builder.build()
    }
}

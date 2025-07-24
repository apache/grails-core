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

package org.grails.datastore.gorm.jdbc.connections;

import java.util.LinkedHashMap;
import java.util.Map;

import javax.sql.DataSource;

import org.springframework.core.env.PropertyResolver;

import org.grails.datastore.mapping.core.connections.ConnectionSource;

/**
 * Extends {@link DataSourceConnectionSourceFactory} and caches the created {@link DataSourceConnectionSource} instances ensuring they are singletons
 *
 * @author Graeme Rocher
 * @since 6.1.7
 */
public class CachedDataSourceConnectionSourceFactory extends DataSourceConnectionSourceFactory {
    private final Map<String, ConnectionSource<DataSource, DataSourceSettings>> dataSources = new LinkedHashMap<>();

    @Override
    public ConnectionSource<DataSource, DataSourceSettings> create(String name, PropertyResolver configuration) {
        if(dataSources.containsKey(name)) {
            return dataSources.get(name);
        }
        else {
            ConnectionSource<DataSource, DataSourceSettings> connectionSource = super.create(name, configuration);
            dataSources.put(name, connectionSource);
            return connectionSource;
        }
    }

    @Override
    public ConnectionSource<DataSource, DataSourceSettings> create(String name, DataSourceSettings settings) {
        if(dataSources.containsKey(name)) {
            return dataSources.get(name);
        }
        else {
            ConnectionSource<DataSource, DataSourceSettings> connectionSource = super.create(name, settings);
            dataSources.put(name, connectionSource);
            return connectionSource;
        }
    }
}

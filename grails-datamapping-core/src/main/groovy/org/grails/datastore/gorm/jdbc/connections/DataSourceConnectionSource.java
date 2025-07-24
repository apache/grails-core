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

import java.io.IOException;
import java.lang.reflect.Method;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.util.ReflectionUtils;

import org.grails.datastore.mapping.core.connections.DefaultConnectionSource;

/**
 * A {@link org.grails.datastore.mapping.core.connections.ConnectionSource} for JDBC {@link DataSource} objects. Attempts to close the pool if a "close" method is provided.
 *
 * @author Graeme Rocher
 * @since 6.0
 */
public class DataSourceConnectionSource extends DefaultConnectionSource<DataSource, DataSourceSettings> {
    private static final Logger LOG = LoggerFactory.getLogger(DataSourceConnectionSource.class);

    public DataSourceConnectionSource(String name, DataSource source, DataSourceSettings settings) {
        super(name, source, settings);
    }

    @Override
    public void close() throws IOException {
        super.close();
        if(!closed) {

            DataSource source = getSource();
            Method closeMethod = ReflectionUtils.findMethod(source.getClass(), "close");

            while (closeMethod == null && source instanceof DelegatingDataSource) {
                source = ((DelegatingDataSource) source).getTargetDataSource();
                closeMethod = ReflectionUtils.findMethod(source.getClass(), "close");
            }

            if(closeMethod != null) {
                try {
                    ReflectionUtils.invokeMethod(closeMethod, source);
                    this.closed = true;
                } catch (Throwable e) {
                    LOG.warn("Error closing JDBC connection [{}]: {}", getName(), e.getMessage());
                }
            }
        }
    }
}

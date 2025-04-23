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

import org.grails.datastore.mapping.config.Entity;
import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.multitenancy.TenantDataSourceConfig;
import org.springframework.util.ClassUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Utility methods for {@link ConnectionSource} handling
 *
 * @author Graeme Rocher
 * @since 6.0
 */
public class ConnectionSourcesSupport {

    public static final List<String> DEFAULT_CONNECTION_SOURCE_NAMES = Collections.singletonList(ConnectionSource.DEFAULT);

    /**
     * If a domain class uses more than one datasource, we need to know which one to use
     * when calling a method without a namespace qualifier.
     *
     * @param entity the domain class
     * @return the default datasource name
     */
    public static String getDefaultConnectionSourceName(PersistentEntity entity) {
        List<String> names = getConnectionSourceNames(entity);
        if (names.size() == 1 && ConnectionSource.ALL.equals(names.get(0))) {
            return ConnectionSource.ALL;
        }
        return names.get(0);
    }

    /**
     * Obtain all of the {@link ConnectionSource} names for the given entity
     *
     * @param entity The entity
     * @return The {@link ConnectionSource} names
     */
    public static List<String> getConnectionSourceNames(PersistentEntity entity) {
        final Entity mappedForm = entity.getMapping().getMappedForm();
        if(mappedForm != null)  {
            return mappedForm.getDatasources();
        }
        return DEFAULT_CONNECTION_SOURCE_NAMES;
    }

    /**
     * Returns whether the given entity uses the give connection source name or not
     *
     * @param entity The name of the entity
     * @param connectionSourceName The connection source name
     * @return Whether the given connection source is used
     */
    public static boolean usesConnectionSource(PersistentEntity entity, String connectionSourceName) {
        Class[] interfaces = ClassUtils.getAllInterfacesForClass(entity.getJavaClass());
        if(isMultiTenant(interfaces)) {
            return !isMultiTenantExcludedDataSource( entity, connectionSourceName );
        }
        else {
            List<String> names = getConnectionSourceNames(entity);
            return names.contains(connectionSourceName) ||
                    names.contains(ConnectionSource.ALL);
        }
    }

    protected static boolean isMultiTenant(Class[] interfaces) {
        for (Class anInterface : interfaces) {
            String name = anInterface.getName();
            if(name.startsWith("grails.gorm") && name.endsWith(".MultiTenant")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns whether the given entity should be excluded from given connection source name or not.
     * It can be configured over {@link TenantDataSourceConfig} annotation
     *
     * @param entity The name of the multi tenant entity
     * @param connectionSourceName The connection source name to check
     * @return Whether the given connection should be excluded
     */
    protected static boolean isMultiTenantExcludedDataSource(PersistentEntity entity, String connectionSourceName) {
        TenantDataSourceConfig tdsc = (TenantDataSourceConfig) entity.getJavaClass().getAnnotation(TenantDataSourceConfig.class);
        boolean result = false;
        if ( null != tdsc ) {
            final String[] dataSourcesToExclude = tdsc.dataSourcesToExclude();
            result = Arrays.asList(dataSourcesToExclude).contains(connectionSourceName);
        }
        return result;
    }
}

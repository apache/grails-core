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
package org.grails.datastore.gorm.jdbc

import java.sql.Connection
import java.sql.SQLException

import javax.sql.DataSource

import groovy.transform.CompileStatic
import org.springframework.beans.BeanUtils
import org.springframework.beans.MutablePropertyValues
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.util.ClassUtils

import org.grails.datastore.mapping.core.exceptions.ConfigurationException

/**
 * NOTE: Forked from Spring Boot logic to avoid hard dependency on Boot.
 *
 * Convenience class for building a {@link DataSource} with common implementations and
 * properties. If Tomcat, HikariCP or Commons DBCP are on the classpath one of them will
 * be selected (in that order with Tomcat first). In the interest of a uniform interface,
 * and so that there can be a fallback to an embedded database if one can be detected on
 * the classpath, only a small set of common configuration properties are supported. To
 * inject additional properties into the result you can downcast it, or use
 * {@code @ConfigurationProperties}.
 *
 * @author Dave Syer
 * @author Graeme Rocher
 *
 * @since 1.1.0
 */
@CompileStatic
class DataSourceBuilder {

    private static final String[] DATA_SOURCE_TYPE_NAMES = [
        'org.apache.tomcat.jdbc.pool.DataSource',
        'com.zaxxer.hikari.HikariDataSource',
        'org.apache.commons.dbcp.BasicDataSource',
        'org.apache.commons.dbcp2.BasicDataSource',
        'org.springframework.jdbc.datasource.DriverManagerDataSource'
    ] as String[]

    private Class<? extends DataSource> type

    private ClassLoader classLoader

    private Map<String, String> properties = new HashMap<>()
    private boolean pooled = true
    private boolean readOnly = false

    static DataSourceBuilder create() {
        return new DataSourceBuilder(null)
    }

    static DataSourceBuilder create(ClassLoader classLoader) {
        return new DataSourceBuilder(classLoader)
    }

    DataSourceBuilder(ClassLoader classLoader) {
        this.classLoader = classLoader
    }

    DataSource build() {
        Class<? extends DataSource> type = getType()
        DataSource result = BeanUtils.instantiateClass(type)
        maybeGetDriverClassName()
        bind(result)
        return result
    }

    private void maybeGetDriverClassName() {
        if (!this.properties.containsKey('driverClassName') &&
                this.properties.containsKey('url')) {
            String url = this.properties.get('url')
            String driverClass = DatabaseDriver.fromJdbcUrl(url).getDriverClassName()
            this.properties.put('driverClassName', driverClass)
            this.properties.put('driver', driverClass)
        }
    }

    private void bind(DataSource result) {
        if (properties.containsKey('dbProperties') && properties.containsKey('dataSourceProperties')) {
            throw new ConfigurationException('Cannot specify both dbProperties and dataSourceProperties')
        }
        if (properties.containsKey('healthCheckProperties')) {
            coerceDbProperties('healthCheckProperties')
        }
        if (properties.containsKey('dbProperties')) {
            coerceDbProperties('dbProperties')
        }
        if (properties.containsKey('dataSourceProperties')) {
            coerceDbProperties('dataSourceProperties')
        }
        MutablePropertyValues properties = new MutablePropertyValues(this.properties)
        new RelaxedDataBinder(result).withAlias('url', 'jdbcUrl')
                .withAlias('username', 'user')
                // The HikariConfig's property name is dataSourceProperties, not dbProperties so support both or either way being defined in config
                .withAlias('dbProperties', 'dataSourceProperties')
                .withAlias('dataSourceProperties', 'dbProperties')
                .bind(properties)
    }

    DataSourceBuilder properties(Map<String, String> properties) {
        this.properties.putAll(properties)
        return this
    }

    /**
     * Coerces the dbProperties value into a flat {@link Properties} object suitable for
     * passing to the underlying DataSource (e.g. HikariCP's dataSourceProperties).
     *
     * <p>When dbProperties originate from a Groovy ConfigSlurper DSL, dotted keys like
     * {@code "oracle.jdbc.sendBooleanAsNativeBoolean"} are expanded into nested maps:
     * {@code {oracle: {jdbc: {sendBooleanAsNativeBoolean: false}}}}. This method
     * recursively flattens such nested maps back into dotted string keys.</p>
     *
     * <p>When dbProperties originate from YAML configuration, the keys are already flat
     * strings and are simply converted to a Properties object.</p>
     */
    private void coerceDbProperties(String keyname) {
        Map propertiesMap = (Map) this.properties
        Object dbPropertiesObject = propertiesMap.get(keyname)
        if (dbPropertiesObject instanceof Map) {
            Map dbProperties = (Map) dbPropertiesObject
            Properties properties = new Properties()
            flattenMap('', dbProperties, properties)
            propertiesMap.put(keyname, properties)
        }
    }

    @SuppressWarnings('unchecked')
    private void flattenMap(String prefix, Map<?, ?> map, Properties target) {
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String key = prefix.isEmpty() ? entry.getKey().toString() : "${prefix}.${entry.getKey()}"
            Object value = entry.getValue()
            if (value instanceof Map) {
                flattenMap(key, (Map<?, ?>) value, target)
            }
            else if (value != null) {
                target.put(key, value.toString())
            }
        }
    }

    DataSourceBuilder type(Class<? extends DataSource> type) {
        this.type = type
        return this
    }

    DataSourceBuilder url(String url) {
        this.properties.put('url', url)
        return this
    }

    DataSourceBuilder driverClassName(String driverClassName) {
        this.properties.put('driverClassName', driverClassName)
        return this
    }

    DataSourceBuilder username(String username) {
        this.properties.put('username', username)
        return this
    }

    DataSourceBuilder password(String password) {
        this.properties.put('password', password)
        return this
    }

    @SuppressWarnings('unchecked')
    Class<? extends DataSource> findType() {
        if (this.type != null) {
            return this.type
        }
        else if (!pooled) {
            if (this.readOnly) {
                return ReadOnlyDriverManagerDataSource
            }
            else {
                return org.springframework.jdbc.datasource.DriverManagerDataSource
            }
        }

        for (String name : DATA_SOURCE_TYPE_NAMES) {
            try {
                return (Class<? extends DataSource>) ClassUtils.forName(name, this.classLoader)
            }
            catch (Exception ignored) {
                // Swallow and continue
            }
        }
        throw new ConfigurationException('No connection pool implementation found on classpath (example commons-dbcp, tomcat-pool etc.)')
    }

    private Class<? extends DataSource> getType() {
        Class<? extends DataSource> type = findType()
        if (type != null) {
            return type
        }
        throw new IllegalStateException('No supported DataSource type found')
    }

    void setPooled(boolean pooled) {
        this.pooled = pooled
    }

    void setReadOnly(boolean readOnly) {
        this.readOnly = readOnly
    }

    protected static class ReadOnlyDriverManagerDataSource extends DriverManagerDataSource {

        @Override
        protected Connection getConnectionFromDriverManager(final String url, final Properties props) throws SQLException {
            Connection connection = super.getConnectionFromDriverManager(url, props)
            connection.setReadOnly(true)
            return connection
        }

    }

}

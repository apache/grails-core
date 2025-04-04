/*
 * Copyright 2012-2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.datastore.gorm.jdbc;


import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import javax.sql.DataSource;

import org.grails.datastore.mapping.core.exceptions.ConfigurationException;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.util.ClassUtils;

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
public class DataSourceBuilder {

    private static final String[] DATA_SOURCE_TYPE_NAMES = new String[] {
            "org.apache.tomcat.jdbc.pool.DataSource",
            "com.zaxxer.hikari.HikariDataSource",
            "org.apache.commons.dbcp.BasicDataSource",
            "org.apache.commons.dbcp2.BasicDataSource",
            "org.springframework.jdbc.datasource.DriverManagerDataSource"};

    private Class<? extends DataSource> type;

    private ClassLoader classLoader;

    private Map<String, String> properties = new HashMap<>();
    private boolean pooled = true;
    private boolean readOnly = false;

    public static DataSourceBuilder create() {
        return new DataSourceBuilder(null);
    }

    public static DataSourceBuilder create(ClassLoader classLoader) {
        return new DataSourceBuilder(classLoader);
    }

    public DataSourceBuilder(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    public DataSource build() {
        Class<? extends DataSource> type = getType();
        DataSource result = BeanUtils.instantiateClass(type);
        maybeGetDriverClassName();
        bind(result);
        return result;
    }

    private void maybeGetDriverClassName() {
        if (!this.properties.containsKey("driverClassName")
                && this.properties.containsKey("url")) {
            String url = this.properties.get("url");
            String driverClass = DatabaseDriver.fromJdbcUrl(url).getDriverClassName();
            this.properties.put("driverClassName", driverClass);
            this.properties.put("driver", driverClass);
        }
    }

    private void bind(DataSource result) {
        if(properties.containsKey("dbProperties")) {
            coerceDbProperties();
        }
        MutablePropertyValues properties = new MutablePropertyValues(this.properties);
        new RelaxedDataBinder(result).withAlias("url", "jdbcUrl")
                .withAlias("username", "user").bind(properties);
    }

    public DataSourceBuilder properties( Map<String, String> properties) {
        this.properties.putAll(properties);
        return this;
    }

    private void coerceDbProperties() {
        Map propertiesMap = this.properties;
        Object dbPropertiesObject = propertiesMap.get("dbProperties");
        if(dbPropertiesObject instanceof Map) {
            Map dbProperties = (Map) dbPropertiesObject;
            Properties properties = new Properties();
            for (Object key : dbProperties.keySet()) {
                Object value = dbProperties.get(key);
                if(value != null) {
                    properties.put(key.toString(), value.toString());
                }
            }
            propertiesMap.put("dbProperties", properties);
        }
    }

    public DataSourceBuilder type(Class<? extends DataSource> type) {
        this.type = type;
        return this;
    }

    public DataSourceBuilder url(String url) {
        this.properties.put("url", url);
        return this;
    }

    public DataSourceBuilder driverClassName(String driverClassName) {
        this.properties.put("driverClassName", driverClassName);
        return this;
    }

    public DataSourceBuilder username(String username) {
        this.properties.put("username", username);
        return this;
    }

    public DataSourceBuilder password(String password) {
        this.properties.put("password", password);
        return this;
    }

    @SuppressWarnings("unchecked")
    public Class<? extends DataSource> findType() {

        if (this.type != null) {
            return this.type;
        }
        else if(!pooled) {
            if(this.readOnly) {
                return ReadOnlyDriverManagerDataSource.class;
            }
            else {
                return org.springframework.jdbc.datasource.DriverManagerDataSource.class;
            }
        }

        for (String name : DATA_SOURCE_TYPE_NAMES) {
            try {
                return (Class<? extends DataSource>) ClassUtils.forName(name,
                        this.classLoader);
            }
            catch (Exception ex) {
                // Swallow and continue
            }
        }
        throw new ConfigurationException("No connection pool implementation found on classpath (example commons-dbcp, tomcat-pool etc.)");
    }

    private Class<? extends DataSource> getType() {
        Class<? extends DataSource> type = findType();
        if (type != null) {
            return type;
        }
        throw new IllegalStateException("No supported DataSource type found");
    }

    public void setPooled(boolean pooled) {
        this.pooled = pooled;
    }

    public void setReadOnly(boolean readOnly) {
        this.readOnly = readOnly;
    }

    protected static class ReadOnlyDriverManagerDataSource extends DriverManagerDataSource {

        @Override
        protected Connection getConnectionFromDriverManager(final String url, final Properties props) throws SQLException {
            Connection connection = super.getConnectionFromDriverManager(url, props);
            connection.setReadOnly(true);
            return connection;
        }
    }
}
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
package org.grails.orm.hibernate.sqltail;

import java.io.Serial;
import java.util.Properties;

import org.hibernate.cfg.AvailableSettings;
import org.hibernate.resource.jdbc.spi.StatementInspector;

import org.springframework.beans.BeanUtils;
import org.springframework.util.ClassUtils;

import org.grails.datastore.gorm.jdbc.sqltail.SqlTailCapture;
import org.grails.datastore.gorm.jdbc.sqltail.SqlTailSettings;
import org.grails.datastore.mapping.core.exceptions.ConfigurationException;

/**
 * The statement inspector GORM installs on a session factory when sql-tail capture is enabled. It
 * records every statement Hibernate is about to run to the {@link SqlTailCapture} of the
 * application, and leaves the statement as it found it. An inspector the application configured
 * itself through {@code hibernate.session_factory.statement_inspector} keeps running: it sees the
 * statement first, and the statement it returns is the one recorded.
 *
 * <p>Nothing of this is installed unless {@link SqlTailSettings#SETTING_ENABLED} is set, so an
 * application that does not capture pays nothing per statement.</p>
 *
 * @since 8.1
 */
public class SqlTailStatementInspector implements StatementInspector {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String dataSourceName;
    private final transient SqlTailCapture capture;
    private final StatementInspector delegate;

    /**
     * @param dataSourceName the connection source the inspected session factory belongs to
     * @param capture where statements are recorded
     * @param delegate the inspector the application configured, or null
     */
    public SqlTailStatementInspector(String dataSourceName, SqlTailCapture capture, StatementInspector delegate) {
        this.dataSourceName = dataSourceName;
        this.capture = capture;
        this.delegate = delegate;
    }

    /**
     * Installs the inspector on the Hibernate properties of a session factory when capture is enabled.
     *
     * @param dataSourceName the connection source the session factory is built for
     * @param settings the sql-tail settings of the connection source
     * @param properties the Hibernate properties the session factory is built from
     * @return the installed inspector, or null when capture is not enabled
     * @throws ConfigurationException when the inspector the application configured cannot be created
     */
    public static SqlTailStatementInspector install(String dataSourceName, SqlTailSettings settings, Properties properties) {
        if (settings == null || !settings.isEnabled()) {
            return null;
        }
        StatementInspector configured = resolve(properties.get(AvailableSettings.STATEMENT_INSPECTOR));
        SqlTailStatementInspector inspector = new SqlTailStatementInspector(
                dataSourceName, SqlTailCapture.open(settings), configured);
        properties.put(AvailableSettings.STATEMENT_INSPECTOR, inspector);
        return inspector;
    }

    /**
     * Resolves the inspector an application configured, which Hibernate accepts as an instance, a
     * class or a class name.
     *
     * @param configured the configured value, or null
     * @return the inspector, or null when nothing was configured
     * @throws ConfigurationException when the configured class cannot be loaded or created
     */
    static StatementInspector resolve(Object configured) {
        if (configured == null) {
            return null;
        }
        if (configured instanceof StatementInspector inspector) {
            return inspector;
        }
        Class<?> type;
        if (configured instanceof Class<?> clazz) {
            type = clazz;
        }
        else {
            String className = configured.toString().trim();
            if (className.isEmpty()) {
                return null;
            }
            try {
                type = ClassUtils.forName(className, ClassUtils.getDefaultClassLoader());
            }
            catch (ClassNotFoundException | LinkageError e) {
                throw new ConfigurationException(
                        "Cannot load the statement inspector [" + className + "] configured with " +
                                AvailableSettings.STATEMENT_INSPECTOR, e);
            }
        }
        if (!StatementInspector.class.isAssignableFrom(type)) {
            throw new ConfigurationException("The statement inspector [" + type.getName() + "] configured with " +
                    AvailableSettings.STATEMENT_INSPECTOR + " does not implement " + StatementInspector.class.getName());
        }
        return (StatementInspector) BeanUtils.instantiateClass(type);
    }

    @Override
    public String inspect(String sql) {
        String executed = sql;
        if (delegate != null) {
            String inspected = delegate.inspect(sql);
            if (inspected != null) {
                executed = inspected;
            }
        }
        if (capture != null) {
            capture.record(dataSourceName, executed);
        }
        return executed;
    }

    /**
     * @return the connection source name statements are recorded under
     */
    public String getDataSourceName() {
        return dataSourceName;
    }

    /**
     * @return where statements are recorded
     */
    public SqlTailCapture getCapture() {
        return capture;
    }

    /**
     * @return the inspector the application configured, or null
     */
    public StatementInspector getDelegate() {
        return delegate;
    }
}

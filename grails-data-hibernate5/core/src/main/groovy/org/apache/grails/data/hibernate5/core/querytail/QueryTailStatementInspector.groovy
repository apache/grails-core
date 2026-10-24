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
package org.apache.grails.data.hibernate5.core.querytail

import groovy.transform.CompileStatic
import groovy.transform.PackageScope
import org.hibernate.SessionFactory
import org.hibernate.SessionFactoryObserver
import org.hibernate.cfg.AvailableSettings
import org.hibernate.cfg.Configuration
import org.hibernate.dialect.AbstractHANADialect
import org.hibernate.dialect.CockroachDB192Dialect
import org.hibernate.dialect.DB2Dialect
import org.hibernate.dialect.Dialect
import org.hibernate.dialect.H2Dialect
import org.hibernate.dialect.HSQLDialect
import org.hibernate.dialect.InformixDialect
import org.hibernate.dialect.MySQLDialect
import org.hibernate.dialect.Oracle8iDialect
import org.hibernate.dialect.Oracle9Dialect
import org.hibernate.dialect.PostgreSQL81Dialect
import org.hibernate.dialect.SQLServerDialect
import org.hibernate.dialect.SybaseAnywhereDialect
import org.hibernate.dialect.SybaseDialect
import org.hibernate.engine.spi.SessionFactoryImplementor
import org.hibernate.resource.jdbc.spi.StatementInspector
import org.springframework.beans.BeanUtils
import org.springframework.util.ClassUtils

import org.apache.grails.data.core.querytail.QueryTailCapture
import org.apache.grails.data.core.querytail.QueryTailSettings
import org.apache.grails.data.core.querytail.QueryTailSyntax
import org.grails.datastore.mapping.core.exceptions.ConfigurationException

/**
 * The statement inspector GORM installs on a session factory when query-tail capture is enabled. It
 * records every statement Hibernate is about to run to the {@link QueryTailCapture} of the
 * application, without its values, and returns the statement as it found it: the database runs
 * it unchanged. An inspector the application configured itself through
 * {@code hibernate.session_factory.statement_inspector} keeps running: it sees the statement
 * first, and the statement it returns is the one run and recorded.
 *
 * <p>The session factory owns the capture. The inspector observes it: it opens the capture when
 * the session factory is created, takes from its dialect how the database quotes values, and
 * closes the capture when the session factory closes, so a session factory that fails to start
 * leaves no capture open either. An observer the application set on the configuration keeps
 * being called.</p>
 *
 * <p>Nothing of this is installed unless {@link QueryTailSettings#SETTING_ENABLED} is set, so an
 * application that does not capture pays nothing per statement.</p>
 *
 * @since 8.1
 */
@CompileStatic
class QueryTailStatementInspector implements StatementInspector, SessionFactoryObserver {

    @Serial
    private static final long serialVersionUID = 1L

    /** The connection source name statements are recorded under. */
    final String dataSourceName

    /** The file statements are recorded to. */
    final File file

    /** The inspector the application configured, or null. */
    final StatementInspector delegate

    /** The session factory observer the application configured, or null. */
    final SessionFactoryObserver observer

    private transient volatile QueryTailCapture capture
    private transient boolean closed

    /**
     * @param dataSourceName the connection source the inspected session factory belongs to
     * @param file the capture file statements are recorded to
     * @param delegate the inspector the application configured, or null
     * @param observer the session factory observer the application configured, or null
     */
    QueryTailStatementInspector(String dataSourceName, File file, StatementInspector delegate,
            SessionFactoryObserver observer) {
        this.dataSourceName = dataSourceName
        this.file = file
        this.delegate = delegate
        this.observer = observer
    }

    /**
     * Installs the inspector on the configuration of a session factory when capture is enabled, as
     * its statement inspector and as the observer of the session factory it builds.
     *
     * @param dataSourceName the connection source the session factory is built for
     * @param settings the query-tail settings of the connection source
     * @param configuration the configuration the session factory is built from
     * @return the installed inspector, or null when capture is not enabled
     * @throws ConfigurationException when the inspector the application configured cannot be created
     */
    static QueryTailStatementInspector install(String dataSourceName, QueryTailSettings settings,
            Configuration configuration) {
        if (settings == null || !settings.enabled) {
            return null
        }
        Properties properties = configuration.properties
        StatementInspector configured = resolve(properties.get(AvailableSettings.STATEMENT_INSPECTOR))
        QueryTailStatementInspector inspector = new QueryTailStatementInspector(
                dataSourceName, settings.resolveFile(), configured, configuration.sessionFactoryObserver)
        properties.put(AvailableSettings.STATEMENT_INSPECTOR, inspector)
        configuration.sessionFactoryObserver = inspector
        inspector
    }

    /**
     * Resolves the inspector an application configured, which Hibernate accepts as an instance, a
     * class or a class name.
     *
     * @param configured the configured value, or null
     * @return the inspector, or null when nothing was configured
     * @throws ConfigurationException when the configured class cannot be loaded or created
     */
    @PackageScope
    static StatementInspector resolve(Object configured) {
        if (configured == null) {
            return null
        }
        if (configured instanceof StatementInspector) {
            return configured
        }
        Class<?> type
        if (configured instanceof Class) {
            type = configured
        }
        else {
            String className = configured.toString().trim()
            if (className.isEmpty()) {
                return null
            }
            try {
                type = ClassUtils.forName(className, ClassUtils.getDefaultClassLoader())
            }
            catch (ClassNotFoundException | LinkageError e) {
                throw new ConfigurationException("Cannot load the statement inspector [${className}] configured with " +
                        AvailableSettings.STATEMENT_INSPECTOR, e)
            }
        }
        if (!StatementInspector.isAssignableFrom(type)) {
            throw new ConfigurationException("The statement inspector [${type.name}] configured with " +
                    "${AvailableSettings.STATEMENT_INSPECTOR} does not implement ${StatementInspector.name}")
        }
        (StatementInspector) BeanUtils.instantiateClass(type)
    }

    @Override
    String inspect(String sql) {
        String executed = sql
        if (delegate != null) {
            String inspected = delegate.inspect(sql)
            if (inspected != null) {
                executed = inspected
            }
        }
        QueryTailCapture current = openCapture()
        if (current != null) {
            current.record(dataSourceName, executed)
        }
        executed
    }

    /**
     * Opens the capture, and records the statements of the session factory in the syntax of its dialect.
     */
    @Override
    void sessionFactoryCreated(SessionFactory factory) {
        if (observer != null) {
            observer.sessionFactoryCreated(factory)
        }
        QueryTailCapture current = openCapture()
        if (current != null && factory instanceof SessionFactoryImplementor) {
            current.syntax = syntaxOf(factory.jdbcServices.dialect)
        }
    }

    /**
     * The syntax the statements of a dialect are redacted in: that of its database, where the
     * redactor knows the database's lexical rules. Derby's dialect extends Db2's and is redacted as
     * Db2, whose rules its database shares. SQL Anywhere's, a Transact-SQL dialect whose database
     * reads {@code //} as a comment, is not known. Any other dialect, such as Teradata's, Firebird's
     * or a dialect that is not Hibernate's, such as the Cloud Spanner dialect, is redacted as for a
     * database that is not known: a statement is then recorded only when every known syntax reads it
     * the same way.
     *
     * @param dialect the dialect of a session factory
     * @return the syntax its statements are redacted in
     */
    static QueryTailSyntax syntaxOf(Dialect dialect) {
        if (dialect instanceof SybaseAnywhereDialect) {
            return QueryTailSyntax.UNKNOWN
        }
        if (dialect instanceof SQLServerDialect || dialect instanceof SybaseDialect) {
            return QueryTailSyntax.TRANSACT_SQL
        }
        if (dialect instanceof MySQLDialect) {
            return QueryTailSyntax.MYSQL
        }
        if (dialect instanceof CockroachDB192Dialect) {
            return QueryTailSyntax.COCKROACH
        }
        if (dialect instanceof PostgreSQL81Dialect) {
            return QueryTailSyntax.POSTGRESQL
        }
        if (dialect instanceof H2Dialect) {
            return QueryTailSyntax.H2
        }
        if (dialect instanceof Oracle8iDialect || dialect instanceof Oracle9Dialect) {
            return QueryTailSyntax.ORACLE
        }
        if (dialect instanceof DB2Dialect) {
            return QueryTailSyntax.DB2
        }
        if (dialect instanceof HSQLDialect) {
            return QueryTailSyntax.HSQLDB
        }
        if (dialect instanceof AbstractHANADialect) {
            return QueryTailSyntax.HANA
        }
        if (dialect instanceof InformixDialect) {
            return QueryTailSyntax.INFORMIX
        }
        QueryTailSyntax.UNKNOWN
    }

    @Override
    void sessionFactoryClosing(SessionFactory factory) {
        if (observer != null) {
            observer.sessionFactoryClosing(factory)
        }
    }

    /**
     * Closes the capture.
     */
    @Override
    void sessionFactoryClosed(SessionFactory factory) {
        try {
            if (observer != null) {
                observer.sessionFactoryClosed(factory)
            }
        }
        finally {
            close()
        }
    }

    /**
     * Stops recording and closes the capture; a capture that was never opened stays unopened.
     */
    synchronized void close() {
        closed = true
        if (capture != null) {
            capture.close()
        }
    }

    /**
     * @return where statements are recorded, or null before the first statement or the creation of the session factory
     */
    QueryTailCapture getCapture() {
        capture
    }

    /**
     * @return the capture, opened by the first statement or by the creation of the session factory,
     * whichever comes first; null once the inspector is closed without one
     * @throws java.io.UncheckedIOException when the capture file cannot be created
     */
    private QueryTailCapture openCapture() {
        QueryTailCapture current = capture
        if (current != null) {
            return current
        }
        synchronized (this) {
            if (capture == null && !closed) {
                capture = QueryTailCapture.open(file)
            }
            return capture
        }
    }
}

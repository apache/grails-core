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
package org.apache.grails.data.hibernate7.core.querytail

import groovy.transform.CompileStatic
import org.hibernate.SessionFactory
import org.hibernate.SessionFactoryObserver
import org.hibernate.boot.SessionFactoryBuilder
import org.hibernate.boot.spi.MetadataImplementor
import org.hibernate.boot.spi.SessionFactoryBuilderFactory
import org.hibernate.boot.spi.SessionFactoryBuilderImplementor
import org.hibernate.engine.spi.SessionFactoryImplementor
import org.hibernate.service.spi.SessionFactoryServiceContributor

import org.apache.grails.datamapping.core.querytail.QueryTailCapture
import org.apache.grails.datamapping.core.querytail.QueryTailDialects
import org.apache.grails.datamapping.core.querytail.QueryTailRecorder
import org.apache.grails.datastore.core.querytail.QueryTailSettings
import org.grails.orm.hibernate.cfg.HibernateMappingContextConfiguration

/**
 * The session factory observer GORM installs on a session factory when query-tail capture is
 * enabled. It wraps the connections the connection provider of the session factory hands out, so
 * that each statement is recorded as it runs, once for each entry submitted in a batch, rather than
 * when the reusable statement is prepared. The statement runs unchanged, and so does the statement
 * inspector the application configured, if any: it sees the statement first, and the statement it
 * returns is the one run and recorded.
 *
 * <p>The session factory owns the capture. Each session factory a configuration builds has an
 * observer, and a capture, of its own, so a configuration that builds several leaves each of them
 * recording until it closes. Its {@link QueryTailRecorder} opens the capture when the session
 * factory is created, and takes from the dialect, as {@link QueryTailDialects} tells it, how the
 * database quotes values; it closes the capture as the session factory starts to close, so that the
 * schema Hibernate drops as it closes, as {@code create-drop} has it, is no more recorded than the
 * schema it created as it started, and a session factory that fails to start leaves no capture open
 * either.</p>
 *
 * <p>The observer is added to the session factory as Hibernate builds it, through the
 * {@linkplain #getSessionFactoryBuilderFactory() factory} the configuration hands Hibernate, next to
 * the observers of the configuration, which it leaves as they are, wherever the application sets
 * them; the connection provider is decorated in the service registry of the session factory, through
 * the {@linkplain #getSessionFactoryServiceContributor() contributor} the configuration hands
 * Hibernate, whatever provider the registry the session factory is built with has.</p>
 *
 * <p>Nothing of this is installed unless {@link QueryTailSettings#SETTING_ENABLED} is set, so an
 * application that does not capture pays nothing per statement.</p>
 *
 * @since 9.0
 */
@CompileStatic
class QueryTailSessionFactoryObserver implements SessionFactoryObserver {

    @Serial
    private static final long serialVersionUID = 1L

    private final QueryTailRecorder recorder

    /**
     * @param dataSourceName the connection source the observed session factory belongs to
     * @param file the capture file statements are recorded to
     */
    QueryTailSessionFactoryObserver(String dataSourceName, File file) {
        this.recorder = new QueryTailRecorder(dataSourceName, file)
    }

    /**
     * @return the connection source name statements are recorded under
     */
    String getDataSourceName() {
        recorder.dataSourceName
    }

    /**
     * @return the file statements are recorded to
     */
    File getFile() {
        recorder.file
    }

    /**
     * Enables capture on the configuration of a session factory when the settings enable it: every
     * session factory the configuration builds is then given an observer of its own, which records
     * the statements run on the connections of its service registry.
     *
     * @param settings the query-tail settings of the connection source
     * @param configuration the configuration the session factories are built from
     */
    static void install(QueryTailSettings settings, HibernateMappingContextConfiguration configuration) {
        if (settings != null && settings.enabled) {
            configuration.queryTailFile = settings.resolveFile()
        }
    }

    /**
     * @return what decorates the connection providers of the session factory Hibernate builds, as
     * Hibernate finds it through the class loader service of the service registry, so that the
     * statements run on the connections they hand out are recorded: whichever providers the registry
     * the session factory is built with has, which it leaves in place
     */
    SessionFactoryServiceContributor getSessionFactoryServiceContributor() {
        QueryTailConnectionProvider.contributor(recorder)
    }

    /**
     * @return what adds this observer to the session factory Hibernate builds, as Hibernate finds it
     * through the class loader service of the service registry: it adds the observer to the builder
     * Hibernate hands it and builds nothing itself, so that the session factory is built as it is
     * without it, with the observers of the configuration
     */
    SessionFactoryBuilderFactory getSessionFactoryBuilderFactory() {
        new Observing(this)
    }

    /**
     * Opens the capture, and records the statements of the session factory in the syntax of its dialect.
     */
    @Override
    void sessionFactoryCreated(SessionFactory factory) {
        Class<?> dialect = factory instanceof SessionFactoryImplementor ? factory.jdbcServices.dialect.getClass() : null
        recorder.open(QueryTailDialects.syntaxOf(dialect))
    }

    /**
     * Closes the capture as the session factory starts to close, so that what Hibernate runs as it
     * closes, such as the statements that drop the schema it created, is not recorded.
     */
    @Override
    void sessionFactoryClosing(SessionFactory factory) {
        close()
    }

    /**
     * Closes the capture, unless it closed as the session factory started to close.
     */
    @Override
    void sessionFactoryClosed(SessionFactory factory) {
        close()
    }

    /**
     * Stops recording and closes the capture; a capture that was never opened stays unopened.
     */
    void close() {
        recorder.close()
    }

    /**
     * @return where statements are recorded, or null before the first statement or the creation of the session factory
     */
    QueryTailCapture getCapture() {
        recorder.capture
    }

    /**
     * Adds an observer to the builder of the session factory Hibernate builds, and builds nothing of its own.
     */
    @CompileStatic
    private static final class Observing implements SessionFactoryBuilderFactory {

        private final SessionFactoryObserver observer

        Observing(SessionFactoryObserver observer) {
            this.observer = observer
        }

        @Override
        SessionFactoryBuilder getSessionFactoryBuilder(MetadataImplementor metadata, SessionFactoryBuilderImplementor defaultBuilder) {
            defaultBuilder.addSessionFactoryObservers(observer)
            // none, so that Hibernate builds with its own builder, or that of another factory, which adds to it
            null
        }
    }
}

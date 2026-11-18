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
package org.apache.grails.data.neo4j.core.querytail

import java.util.concurrent.CompletionStage
import java.util.function.BiConsumer

import groovy.transform.CompileStatic
import groovy.transform.PackageScope
import org.neo4j.driver.Driver
import org.neo4j.driver.Session
import org.neo4j.driver.SessionConfig

import org.apache.grails.datamapping.core.querytail.QueryTailCapture
import org.apache.grails.datamapping.core.querytail.QueryTailRecorder
import org.apache.grails.datamapping.core.querytail.QueryTailSyntax
import org.apache.grails.datastore.core.querytail.QueryTailSettings

/**
 * The driver a Neo4j connection source hands GORM when query-tail capture is enabled: the driver the
 * connection source built, with every session it opens recording the Cypher statements run on it,
 * in a transaction or outside one, to the {@link QueryTailCapture} of the application before they are
 * sent. A statement is recorded without its values, in the syntax of Cypher, and its parameters are
 * never recorded; the statement runs unchanged. The capture opens with the first statement and
 * closes when the driver closes, so a datastore that closes leaves nothing open, and one that fails
 * to start before it ran a statement leaves the capture file as it found it.
 *
 * <p>Statements run through the driver's asynchronous and reactive sessions are not recorded; GORM
 * runs none.</p>
 *
 * <p>Nothing of this is installed unless {@link QueryTailSettings#SETTING_ENABLED} is set, so an
 * application that does not capture pays nothing per statement.</p>
 *
 * @since 9.0
 */
@CompileStatic
class QueryTailDriver implements Driver {

    /** The connection source name statements are recorded under. */
    final String dataSourceName

    /** The file statements are recorded to. */
    final File file

    @Delegate(deprecated = true)
    private final Driver delegate

    private final QueryTailRecorder recorder

    /**
     * Records nothing until the first statement opens the capture, which has stopped at once when its file cannot be
     * created.
     *
     * @param dataSourceName the connection source the driver belongs to
     * @param file the capture file statements are recorded to
     * @param delegate the driver that runs the statements
     */
    QueryTailDriver(String dataSourceName, File file, Driver delegate) {
        this.dataSourceName = dataSourceName
        this.file = file
        this.delegate = delegate
        this.recorder = new QueryTailRecorder(dataSourceName, file, QueryTailSyntax.CYPHER)
    }

    /**
     * Wraps the driver of a connection source when capture is enabled, whether the connection source
     * built it or the application handed it over. A driver that records already, as the {@code neo4jDriver}
     * bean of a datastore that captures does, records each statement once: it is not wrapped again.
     *
     * @param dataSourceName the connection source the driver belongs to
     * @param settings the query-tail settings of the connection source
     * @param driver the driver of the connection source
     * @return the driver GORM should use: the recording one, or the driver itself when capture is not enabled
     * or it records already
     */
    static Driver install(String dataSourceName, QueryTailSettings settings, Driver driver) {
        if (settings == null || !settings.enabled || driver instanceof QueryTailDriver) {
            return driver
        }
        new QueryTailDriver(dataSourceName, settings.resolveFile(), driver)
    }

    /**
     * @return the driver the connection source built, which runs the statements
     */
    Driver getDelegate() {
        delegate
    }

    /**
     * @return where statements are recorded, or null before the first statement
     */
    QueryTailCapture getCapture() {
        recorder.capture
    }

    @Override
    Session session() {
        new QueryTailSession(delegate.session(), this)
    }

    @Override
    Session session(SessionConfig config) {
        new QueryTailSession(delegate.session(config), this)
    }

    /**
     * Closes the driver, then the capture.
     */
    @Override
    void close() {
        try {
            delegate.close()
        }
        finally {
            recorder.close()
        }
    }

    @Override
    CompletionStage<Void> closeAsync() {
        BiConsumer<Void, Throwable> closeCapture = { Void nothing, Throwable failure -> recorder.close() }
        delegate.closeAsync().whenComplete(closeCapture)
    }

    /**
     * Records a statement that is about to run on the current thread.
     */
    @PackageScope
    void record(String cypher) {
        recorder.record(cypher)
    }
}

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
package org.grails.plugins.datasource

import java.sql.Connection

import javax.sql.DataSource

import groovy.sql.Sql

import org.springframework.context.support.GenericApplicationContext
import org.springframework.jdbc.datasource.AbstractDataSource
import org.springframework.jdbc.datasource.DriverManagerDataSource

import org.grails.core.lifecycle.ShutdownOperations

import spock.lang.Specification

/**
 * Covers the hook that shuts an embedded database down once the application's shutdown operations run.
 */
class EmbeddedDatabaseShutdownHookSpec extends Specification {

    GenericApplicationContext context = new GenericApplicationContext()

    EmbeddedDatabaseShutdownHook hook = new EmbeddedDatabaseShutdownHook()

    void setup() {
        ShutdownOperations.resetOperations()
        hook.applicationContext = context
    }

    void cleanup() {
        ShutdownOperations.resetOperations()
        context.close()
    }

    void "an embedded database is shut down when the shutdown operations run, not when the hook stops"() {
        given: 'an in-memory database kept open until it is shut down, holding a table'
        def dataSource = new DriverManagerDataSource('jdbc:h2:mem:embeddedShutdownHook;DB_CLOSE_DELAY=-1')
        context.registerBean('dataSource', DataSource, { dataSource })
        context.refresh()
        new Sql(dataSource).execute('CREATE TABLE book (title VARCHAR(50))')

        when:
        hook.start()
        hook.stop()

        then: 'stopping only schedules the shutdown'
        tableExists(dataSource)

        when:
        ShutdownOperations.runOperations()

        then: 'the database was shut down, so the next connection opens an empty one'
        !tableExists(dataSource)
    }

    void "a database at #url is #treatment"() {
        given:
        def dataSource = new CountingDataSource(url: url)
        context.registerBean('dataSource', DataSource, { dataSource })
        context.refresh()

        when:
        hook.start()
        hook.stop()

        then: 'a connection is taken only to shut a database down'
        dataSource.connections == connections

        where:
        url                               | connections
        'jdbc:h2:mem:shutdownHook'        | 1
        'jdbc:h2:file:/tmp/shutdownHook'  | 1
        'jdbc:hsqldb:mem:shutdownHook'    | 1
        'jdbc:h2:tcp://localhost/db'      | 0
        'jdbc:h2:ssl://localhost/db'      | 0
        'jdbc:hsqldb:hsql://localhost/db' | 0
        'jdbc:hsqldb:http://localhost/db' | 0
        'jdbc:postgresql://localhost/db'  | 0
        null                              | 0

        treatment = connections ? 'shut down, being embedded' : 'left alone'
    }

    void "a data source without a url is left alone"() {
        given:
        def dataSource = new UrlLessDataSource()
        context.registerBean('dataSource', DataSource, { dataSource })
        context.refresh()

        when:
        hook.start()
        hook.stop()

        then:
        dataSource.connections == 0
    }

    void "it runs as a lifecycle bean stopped after every other"() {
        given:
        context.refresh()
        boolean calledBack = false

        expect:
        !hook.running
        hook.autoStartup
        hook.phase == Integer.MIN_VALUE

        when:
        hook.start()

        then:
        hook.running

        when:
        hook.stop { calledBack = true }

        then:
        !hook.running
        calledBack
    }

    private static boolean tableExists(DataSource dataSource) {
        new Sql(dataSource).firstRow(
                "SELECT COUNT(*) AS found FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = 'BOOK'").found > 0
    }

    /** A data source that counts the connections taken from it, and opens none. */
    static class CountingDataSource extends AbstractDataSource {

        String url

        int connections

        @Override
        Connection getConnection() {
            connections++
            null
        }

        @Override
        Connection getConnection(String username, String password) {
            getConnection()
        }
    }

    /** The same, without a url property. */
    static class UrlLessDataSource extends AbstractDataSource {

        int connections

        @Override
        Connection getConnection() {
            connections++
            null
        }

        @Override
        Connection getConnection(String username, String password) {
            getConnection()
        }
    }
}

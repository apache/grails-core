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
package grails.gorm.tests

import java.time.Duration

import groovy.transform.CompileStatic
import org.testcontainers.db2.Db2Container
import org.testcontainers.mariadb.MariaDBContainer
import org.testcontainers.mysql.MySQLContainer
import org.testcontainers.oracle.OracleContainer
import org.testcontainers.postgresql.PostgreSQLContainer

/**
 * One container per database for the specs that run a feature against each database, shared by all of them in a test
 * JVM. A spec starts a container the first time one of its rows needs it, and every later spec finds it running; none
 * stops it, and Testcontainers removes them all when the JVM exits. Before, each spec created containers of its own
 * that nothing stopped, so they piled up: after three specs three Oracle containers of about 2.5 GB each held the memory
 * Docker had, and the next Oracle container timed out waiting to start. Each spec creates its tables with
 * {@code create-drop}, so they are dropped when its datastore closes and the next spec starts from an empty schema.
 */
@CompileStatic
final class DatabaseContainers {

    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer('postgres:16')

    static final MySQLContainer MYSQL = new MySQLContainer('mysql:8.0')

    static final MariaDBContainer MARIADB = new MariaDBContainer('mariadb:10.11')

    /**
     * slim-faststart ships native arm64 and x86_64 images and usually reports ready in about 15 seconds, but the default
     * 60 second Testcontainers timeout has been too tight on loaded or emulated CI runners
     * (testcontainers/testcontainers-java#9057).
     */
    static final OracleContainer ORACLE = new OracleContainer('gvenzl/oracle-free:slim-faststart')
            .withStartupTimeout(Duration.ofMinutes(3))

    /**
     * Db2 for Linux, UNIX and Windows. IBM publishes the image for amd64 only, so the Db2 rows run on the amd64 CI
     * runners and are skipped on arm64 machines. It starts in one to three minutes.
     */
    static final Db2Container DB2 = new Db2Container('icr.io/db2_community/db2:11.5.9.0')
            .acceptLicense()
            .withStartupTimeout(Duration.ofMinutes(10))

    private DatabaseContainers() {
    }
}

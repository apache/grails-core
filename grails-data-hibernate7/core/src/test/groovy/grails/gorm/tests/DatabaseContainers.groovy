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
import org.opentest4j.TestAbortedException
import org.testcontainers.containers.ContainerFetchException
import org.testcontainers.containers.GenericContainer
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

    private static ContainerFetchException db2FetchFailure

    private DatabaseContainers() {
    }

    /**
     * Starts the container of a row unless it is running already. A row without a container runs on H2.
     * <p>
     * The Db2 image comes from IBM's registry, icr.io, which the Docker daemon of some GitHub-hosted runners cannot
     * reach at all, for as long as the job runs. There the Db2 rows are skipped, as they are on arm64, instead of
     * failing the job: the other jobs of the run still run them. The image is fetched once per test JVM, before the
     * container is started: {@code start()} retries a failing fetch for two minutes and then once more while it builds
     * its error message, and every later Db2 row would wait as long again.
     *
     * @param container the container of the row, or {@code null} for H2
     * @throws TestAbortedException if the container is Db2 and its image cannot be fetched
     */
    static void start(GenericContainer container) {
        if (container == null || container.running) {
            return
        }
        if (container.is(DB2)) {
            fetchDb2Image()
        }
        container.start()
    }

    private static synchronized void fetchDb2Image() {
        if (db2FetchFailure == null) {
            try {
                // Pulls the image unless Docker has it; the container keeps the name, so start() does not pull again
                DB2.dockerImageName
            } catch (ContainerFetchException e) {
                db2FetchFailure = e
            }
        }
        if (db2FetchFailure != null) {
            throw new TestAbortedException(
                    "The Db2 image could not be fetched, so the Db2 rows are skipped: ${db2FetchFailure.message}",
                    db2FetchFailure)
        }
    }
}

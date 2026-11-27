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
import groovy.transform.PackageScope
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

    static final String DB2_IMAGE = 'icr.io/db2_community/db2:11.5.9.0'

    /**
     * Db2 for Linux, UNIX and Windows. IBM publishes the image for amd64 only, so the Db2 rows run on the amd64 CI
     * runners and are skipped on arm64 machines. It starts in one to three minutes.
     */
    static final Db2Container DB2 = new Db2Container(DB2_IMAGE)
            .acceptLicense()
            .withStartupTimeout(Duration.ofMinutes(10))

    private static final ImageFetch DB2_IMAGE_FETCH = new ImageFetch(DB2, DB2_IMAGE)

    private DatabaseContainers() {
    }

    /**
     * Starts the container of a row unless it is running already. A row without a container runs on H2.
     * <p>
     * The Db2 image comes from IBM's registry, icr.io, which the Docker daemon of some GitHub-hosted runners cannot
     * reach at all, for as long as the job runs. {@code start()} retries a failing fetch for two minutes, and then once
     * more while it builds its error message, so every Db2 row waited that long before it failed, about 20 minutes for a
     * job. The image is therefore fetched once per test JVM, before the container is started, and when that fails, the
     * first Db2 row fails with the failure and every later one fails with it at once.
     *
     * @param container the container of the row, or {@code null} for H2
     * @throws ContainerFetchException if the container is Db2 and its image cannot be fetched
     */
    static void start(GenericContainer container) {
        start(container, DB2, DB2_IMAGE_FETCH)
    }

    /**
     * Starts the container of a row as {@link #start(GenericContainer)} does, with the Db2 container and the fetch of
     * its image given, so that a test can give its own.
     */
    @PackageScope
    static void start(GenericContainer container, GenericContainer db2, ImageFetch db2ImageFetch) {
        if (container == null || container.running) {
            return
        }
        if (container.is(db2)) {
            db2ImageFetch.fetch()
        }
        container.start()
    }

    /**
     * The image of a container, fetched once. A failure is remembered, so every later row that needs the image fails at
     * once instead of fetching it again.
     */
    @CompileStatic
    static final class ImageFetch {

        private final GenericContainer container
        private final String image
        private boolean attempted
        private ContainerFetchException failure

        ImageFetch(GenericContainer container, String image) {
            this.container = container
            this.image = image
        }

        /**
         * Fetches the image unless that was tried before.
         *
         * @throws ContainerFetchException if the image cannot be fetched
         */
        synchronized void fetch() {
            if (!attempted) {
                try {
                    // Pulls the image unless Docker has it; the container keeps the name, so start() does not pull again
                    container.dockerImageName
                } catch (ContainerFetchException e) {
                    failure = e
                }
                // Not set when an error ends the fetch, so the next row fetches the image again
                attempted = true
            }
            if (failure != null) {
                // The message of the exception only names the image; its cause says what went wrong
                throw new ContainerFetchException(
                        "Pulling ${image} failed: ${failure.cause?.message ?: failure.message}", failure)
            }
        }
    }
}

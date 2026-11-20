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

import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.SocketChannel
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

import groovy.transform.CompileStatic
import org.testcontainers.DockerClientFactory
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

    /**
     * Whether a Docker daemon is reachable on this machine; a yes means a daemon answered. A socket file at the
     * well-known path of Docker Desktop or Linux is asked with a {@code GET /_ping}, which avoids Testcontainers'
     * client on macOS, where it can fail with a 500 when the daemon API version does not match the docker-java version
     * it bundles. The file alone proves nothing: it stays behind when the daemon exits. Without a socket that answers
     * the daemon may still be there: on Windows it listens on a named pipe, and {@code DOCKER_HOST} can name a TCP
     * address or a socket elsewhere. None of those is a file, so then Testcontainers' client is asked, and an error
     * from it means the daemon is not reachable.
     */
    static boolean isDockerAvailable() {
        if ([System.getProperty('user.home') + '/.docker/run/docker.sock', '/var/run/docker.sock'].any { pings(Path.of(it)) }) {
            return true
        }
        try {
            return DockerClientFactory.instance().isDockerAvailable()
        } catch (Throwable ignored) {
            return false
        }
    }

    /**
     * Whether a Docker daemon answers {@code GET /_ping} with {@code OK} on the Unix socket at {@code socket}. A path
     * with no file, a file nothing listens on, a file that is not a socket, and a listener that does not speak HTTP all
     * answer no, within two seconds.
     */
    static boolean pings(Path socket) {
        if (!Files.exists(socket)) {
            return false
        }
        try (SocketChannel channel = SocketChannel.open(StandardProtocolFamily.UNIX)) {
            channel.configureBlocking(true)
            channel.connect(UnixDomainSocketAddress.of(socket))
            channel.write(ByteBuffer.wrap('GET /_ping HTTP/1.0\r\n\r\n'.getBytes(StandardCharsets.US_ASCII)))
            ByteBuffer response = ByteBuffer.allocate(512)
            long deadline = System.nanoTime() + PING_TIMEOUT.toNanos()
            ExecutorService reader = Executors.newSingleThreadExecutor()
            try {
                Future<Integer> read = reader.submit({ channel.read(response) } as Callable<Integer>)
                read.get(Math.max(1L, deadline - System.nanoTime()), TimeUnit.NANOSECONDS)
            } finally {
                reader.shutdownNow()
            }
            String text = new String(response.array(), 0, response.position(), StandardCharsets.US_ASCII)
            return text.startsWith('HTTP/1.') && text.contains(' 200 ') && text.trim().endsWith('OK')
        } catch (Throwable ignored) {
            return false
        }
    }

    private static final Duration PING_TIMEOUT = Duration.ofSeconds(2)

    private DatabaseContainers() {
    }
}

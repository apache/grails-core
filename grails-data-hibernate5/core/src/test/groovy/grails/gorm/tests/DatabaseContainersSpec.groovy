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
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

import spock.lang.Specification
import spock.lang.TempDir

/**
 * A yes from the Docker check has to mean a daemon answered, whatever is left on the file system.
 */
class DatabaseContainersSpec extends Specification {

    @TempDir
    Path dir

    private ServerSocketChannel server

    void cleanup() {
        server?.close()
    }

    private Path listen(String reply) {
        Path socket = Path.of('/tmp', "dc-${UUID.randomUUID().toString().take(8)}.sock")
        server = ServerSocketChannel.open(StandardProtocolFamily.UNIX)
        server.bind(UnixDomainSocketAddress.of(socket))
        Thread.start {
            try {
                SocketChannel client = server.accept()
                client.read(ByteBuffer.allocate(256))
                if (reply != null) {
                    client.write(ByteBuffer.wrap(reply.getBytes(StandardCharsets.US_ASCII)))
                }
                client.close()
            } catch (Throwable ignored) {
                // the spec closed the server
            }
        }
        socket
    }

    void 'a socket that a daemon answers on is reachable'() {
        given:
        Path socket = listen('HTTP/1.0 200 OK\r\nContent-Type: text/plain\r\n\r\nOK')

        expect:
        DatabaseContainers.pings(socket)

        cleanup:
        Files.deleteIfExists(socket)
    }

    void 'a socket file left behind with nothing listening is not reachable'() {
        given: 'a socket that was bound and then closed leaves its file'
        Path socket = listen(null)
        server.close()

        expect:
        Files.exists(socket)
        !DatabaseContainers.pings(socket)

        cleanup:
        Files.deleteIfExists(socket)
    }

    void 'a path with no file is not reachable'() {
        expect:
        !DatabaseContainers.pings(dir.resolve('absent.sock'))
    }

    void 'a regular file is not reachable'() {
        given:
        Path file = Files.writeString(dir.resolve('docker.sock'), 'not a socket')

        expect:
        !DatabaseContainers.pings(file)
    }

    void 'a listener that does not speak the Docker API is not reachable'() {
        given:
        Path socket = listen('SSH-2.0-banner\r\n')

        expect:
        !DatabaseContainers.pings(socket)

        cleanup:
        Files.deleteIfExists(socket)
    }

    void 'a listener that never answers is not reachable'() {
        given: 'it accepts and reads, then sends nothing and keeps the connection open'
        Path socket = Path.of('/tmp', "dc-${UUID.randomUUID().toString().take(8)}.sock")
        server = ServerSocketChannel.open(StandardProtocolFamily.UNIX)
        server.bind(UnixDomainSocketAddress.of(socket))
        Thread.start {
            try {
                SocketChannel client = server.accept()
                Thread.sleep(5000)
                client.close()
            } catch (Throwable ignored) {
                // the spec closed the server
            }
        }

        expect:
        !DatabaseContainers.pings(socket)

        cleanup:
        Files.deleteIfExists(socket)
    }
}

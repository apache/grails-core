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
package org.grails.cli.profile.commands.io

import com.sun.net.httpserver.HttpServer
import spock.lang.Specification
import spock.lang.Unroll

class ServerInteractionSpec extends Specification {

    ServerInteraction interaction = new ServerInteraction() {}

    HttpServer server

    void cleanup() {
        server?.stop(0)
    }

    void 'a port nothing listens on is not available'() {
        expect:
        !interaction.isServerAvailable('localhost', freePort())
    }

    @Unroll
    void 'a server answering #answer is #description'() {
        given:
        server = HttpServer.create(new InetSocketAddress('localhost', 0), 0)
        server.createContext('/') { exchange ->
            if (phase) {
                exchange.responseHeaders.set('Grails-Startup-Phase', phase)
            }
            exchange.sendResponseHeaders(status, -1)
            exchange.close()
        }
        server.start()

        expect:
        interaction.isServerAvailable('localhost', server.address.port) == available

        where:
        answer                                              | phase            | status || available
        'as the startup progress page'                      | 'CREATING_BEANS' | 503    || false
        'as the startup progress page, after a failed start' | 'FAILED'         | 503    || false
        'that the application is ready'                     | 'READY'          | 200    || true
        'as the application'                                | null             | 200    || true
        'as the application, with an error'                 | null             | 404    || true

        description = available ? 'available' : 'not available'
    }

    void 'a port that accepts connections without answering plain HTTP is available, as with SSL'() {
        given: 'a socket that hangs up on whatever is sent to it'
        ServerSocket socket = new ServerSocket(0)
        Thread.start {
            while (!socket.closed) {
                try {
                    socket.accept().close()
                }
                catch (IOException ignored) {
                    // closed
                }
            }
        }

        expect:
        interaction.isServerAvailable('localhost', socket.localPort)

        cleanup:
        socket.close()
    }

    private static int freePort() {
        new ServerSocket(0).withCloseable { it.localPort }
    }
}

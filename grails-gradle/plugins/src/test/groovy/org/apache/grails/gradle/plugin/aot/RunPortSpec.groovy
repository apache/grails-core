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
package org.apache.grails.gradle.plugin.aot

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.gradle.api.GradleException
import spock.lang.Specification
import spock.lang.Unroll

/**
 * Covers refusing a port something else is already answering on, and asking whether what answers on one is ready.
 */
class RunPortSpec extends Specification {

    @Unroll
    void 'an application answering #status is #description'() {
        given:
            HttpServer server = HttpServer.create(new InetSocketAddress('localhost', 0), 0)
            server.createContext('/') { HttpExchange exchange ->
                if (retryAfter) {
                    exchange.responseHeaders.add('Retry-After', '1')
                }
                exchange.sendResponseHeaders(status, -1)
                exchange.close()
            }
            server.start()

        expect:
            RunPort.answersNotReady(server.address.port) == notReady

        cleanup:
            server?.stop(0)

        where:
            status | retryAfter | notReady | description
            503    | true       | true     | 'not ready where it says to come back, as the Spring Security plugin does until the application has started'
            503    | false      | false    | 'ready where it does not say to come back, since that is the application\'s own answer'
            200    | false      | false    | 'ready'
            302    | false      | false    | 'ready, wherever it sends the request'
            404    | false      | false    | 'ready, whatever it has at its home page'
            500    | false      | false    | 'ready, since only 503 says to come back'
            500    | true       | false    | 'ready even with Retry-After, since only a 503 says it is not ready yet'
    }

    void 'a port nothing answers on is not said to be not ready'() {
        given:
            int free = new ServerSocket(0).withCloseable { ServerSocket socket -> socket.localPort }

        expect: 'whether anything listens is what the wait for the port itself is for'
            !RunPort.answersNotReady(free)
    }

    void 'a free port is not refused'() {
        given: 'a port nothing is listening on, found by letting the system choose one and closing it'
            int free = new ServerSocket(0).withCloseable { ServerSocket socket -> socket.localPort }

        when:
            RunPort.refuseWhereTaken(free, 'Training an AOT cache', 'grails.aotCache.port')

        then:
            noExceptionThrown()
    }

    void 'a port something is listening on is refused before anything is run'() {
        given: 'something already answering, as a second build on the same agent would be'
            ServerSocket taken = new ServerSocket(0, 1, InetAddress.getByName('localhost'))

        when:
            RunPort.refuseWhereTaken(taken.localPort, 'Training an AOT cache', 'grails.aotCache.port')

        then: 'rather than the run being started and whatever answers being recorded in its place'
            GradleException e = thrown()
            e.message.contains(taken.localPort as String)

        and: 'and the message says what to change'
            e.message.contains('grails.aotCache.port')

        cleanup:
            taken?.close()
    }

    void 'the message names whatever was being attempted'() {
        given:
            ServerSocket taken = new ServerSocket(0, 1, InetAddress.getByName('localhost'))

        when:
            RunPort.refuseWhereTaken(taken.localPort, 'Tracing what an image needs',
                    'grails.nativeMetadata.port')

        then:
            GradleException e = thrown()
            e.message.startsWith('Tracing what an image needs')
            e.message.contains('grails.nativeMetadata.port')

        cleanup:
            taken?.close()
    }
}

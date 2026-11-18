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

import org.gradle.api.GradleException
import spock.lang.Specification

/**
 * Covers refusing a port something else is already answering on.
 */
class RunPortSpec extends Specification {

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

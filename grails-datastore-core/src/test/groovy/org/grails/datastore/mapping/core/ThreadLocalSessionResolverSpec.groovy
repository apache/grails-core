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

package org.grails.datastore.mapping.core

import java.util.concurrent.Callable
import java.util.concurrent.Executors

import spock.lang.Specification

class ThreadLocalSessionResolverSpec extends Specification {

    ThreadLocalSessionResolver<Session> resolver = new ThreadLocalSessionResolver<>()

    def "should bind and resolve session"() {
        given:
        Session session = Mock(Session)

        when:
        resolver.bind(session)

        then:
        resolver.resolve() == session

        cleanup:
        resolver.unbind()
    }

    def "should bind and resolve qualified session"() {
        given:
        Session session = Mock(Session)
        String qualifier = "secondary"

        when:
        resolver.bind(qualifier, session)

        then:
        resolver.resolve(qualifier) == session

        cleanup:
        resolver.unbind(qualifier)
    }

    def "should unbind session"() {
        given:
        Session session = Mock(Session)
        resolver.bind(session)

        when:
        resolver.unbind()

        then:
        resolver.resolve() == null
    }

    def "a qualified session bound on one thread is not visible to another thread"() {
        given:
        Session session = Mock(Session)
        String qualifier = "secondary"
        resolver.bind(qualifier, session)

        when:
        def resolvedOnOtherThread = Executors.newVirtualThreadPerTaskExecutor().withCloseable { executor ->
            executor.submit({ resolver.resolve(qualifier) } as Callable<Session>).get()
        }

        then:
        resolvedOnOtherThread == null
        resolver.resolve(qualifier) == session

        cleanup:
        resolver.unbind(qualifier)
    }

    def "unbind clears both the current session and the qualified sessions for the thread"() {
        given:
        Session session = Mock(Session)
        Session qualifiedSession = Mock(Session)
        resolver.bind(session)
        resolver.bind("secondary", qualifiedSession)

        when:
        resolver.unbind()

        then:
        resolver.resolve() == null
        resolver.resolve("secondary") == null
    }
}

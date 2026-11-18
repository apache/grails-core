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
package org.grails.datastore.gorm.support

import grails.persistence.support.PersistenceContextInterceptor
import spock.lang.Specification

class AggregatePersistenceContextInterceptorSpec extends Specification {

    void "isOpen returns false when there are no interceptors"() {
        given:
        def interceptor = new AggregatePersistenceContextInterceptor([])

        expect:
        !interceptor.isOpen()
    }

    void "isOpen returns true if at least one interceptor is open"() {
        given:
        def closed = Mock(PersistenceContextInterceptor) {
            isOpen() >> false
        }
        def open = Mock(PersistenceContextInterceptor) {
            isOpen() >> true
        }
        def interceptor = new AggregatePersistenceContextInterceptor([closed, open])

        expect:
        interceptor.isOpen()
    }

    void "isOpen returns false when every interceptor is closed"() {
        given:
        def first = Mock(PersistenceContextInterceptor) {
            isOpen() >> false
        }
        def second = Mock(PersistenceContextInterceptor) {
            isOpen() >> false
        }
        def interceptor = new AggregatePersistenceContextInterceptor([first, second])

        expect:
        !interceptor.isOpen()
    }

    void "destroy only destroys interceptors that are open"() {
        given:
        def open = Mock(PersistenceContextInterceptor) {
            isOpen() >> true
        }
        def closed = Mock(PersistenceContextInterceptor) {
            isOpen() >> false
        }
        def interceptor = new AggregatePersistenceContextInterceptor([open, closed])

        when:
        interceptor.destroy()

        then:
        1 * open.destroy()
        0 * closed.destroy()
    }

    void "destroy swallows an exception from one interceptor and still destroys the rest"() {
        given:
        def failing = Mock(PersistenceContextInterceptor) {
            isOpen() >> true
            destroy() >> { throw new RuntimeException("boom") }
        }
        def healthy = Mock(PersistenceContextInterceptor) {
            isOpen() >> true
        }
        def interceptor = new AggregatePersistenceContextInterceptor([failing, healthy])

        when:
        interceptor.destroy()

        then:
        noExceptionThrown()
        1 * healthy.destroy()
    }

    void "reconnect delegates to every interceptor"() {
        given:
        def first = Mock(PersistenceContextInterceptor)
        def second = Mock(PersistenceContextInterceptor)
        def interceptor = new AggregatePersistenceContextInterceptor([first, second])

        when:
        interceptor.reconnect()

        then:
        1 * first.reconnect()
        1 * second.reconnect()
    }

    void "clear delegates to every interceptor"() {
        given:
        def first = Mock(PersistenceContextInterceptor)
        def second = Mock(PersistenceContextInterceptor)
        def interceptor = new AggregatePersistenceContextInterceptor([first, second])

        when:
        interceptor.clear()

        then:
        1 * first.clear()
        1 * second.clear()
    }

    void "disconnect delegates to every interceptor"() {
        given:
        def first = Mock(PersistenceContextInterceptor)
        def second = Mock(PersistenceContextInterceptor)
        def interceptor = new AggregatePersistenceContextInterceptor([first, second])

        when:
        interceptor.disconnect()

        then:
        1 * first.disconnect()
        1 * second.disconnect()
    }

    void "flush delegates to every interceptor"() {
        given:
        def first = Mock(PersistenceContextInterceptor)
        def second = Mock(PersistenceContextInterceptor)
        def interceptor = new AggregatePersistenceContextInterceptor([first, second])

        when:
        interceptor.flush()

        then:
        1 * first.flush()
        1 * second.flush()
    }

    void "init delegates to every interceptor"() {
        given:
        def first = Mock(PersistenceContextInterceptor)
        def second = Mock(PersistenceContextInterceptor)
        def interceptor = new AggregatePersistenceContextInterceptor([first, second])

        when:
        interceptor.init()

        then:
        1 * first.init()
        1 * second.init()
    }

    void "setReadOnly delegates to every interceptor"() {
        given:
        def first = Mock(PersistenceContextInterceptor)
        def second = Mock(PersistenceContextInterceptor)
        def interceptor = new AggregatePersistenceContextInterceptor([first, second])

        when:
        interceptor.setReadOnly()

        then:
        1 * first.setReadOnly()
        1 * second.setReadOnly()
    }

    void "setReadWrite delegates to every interceptor"() {
        given:
        def first = Mock(PersistenceContextInterceptor)
        def second = Mock(PersistenceContextInterceptor)
        def interceptor = new AggregatePersistenceContextInterceptor([first, second])

        when:
        interceptor.setReadWrite()

        then:
        1 * first.setReadWrite()
        1 * second.setReadWrite()
    }
}

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

import spock.lang.Specification

import grails.persistence.support.PersistenceContextInterceptor

class AggregatePersistenceContextInterceptorSpec extends Specification {

    PersistenceContextInterceptor first = Mock(PersistenceContextInterceptor)
    PersistenceContextInterceptor second = Mock(PersistenceContextInterceptor)
    AggregatePersistenceContextInterceptor aggregate = new AggregatePersistenceContextInterceptor([first, second])

    void "the aggregate is open when any interceptor is open"() {
        when:
        boolean open = aggregate.open

        then:
        1 * first.isOpen() >> false
        1 * second.isOpen() >> true
        open

        when:
        open = aggregate.open

        then:
        1 * first.isOpen() >> true
        0 * second.isOpen()
        open

        when:
        open = aggregate.open

        then:
        1 * first.isOpen() >> false
        1 * second.isOpen() >> false
        !open
        !new AggregatePersistenceContextInterceptor([]).open
    }

    void "lifecycle calls fan out to every interceptor"() {
        when:
        aggregate.init()
        aggregate.reconnect()
        aggregate.disconnect()
        aggregate.flush()
        aggregate.clear()
        aggregate.setReadOnly()
        aggregate.setReadWrite()

        then:
        1 * first.init()
        1 * second.init()
        1 * first.reconnect()
        1 * second.reconnect()
        1 * first.disconnect()
        1 * second.disconnect()
        1 * first.flush()
        1 * second.flush()
        1 * first.clear()
        1 * second.clear()
        1 * first.setReadOnly()
        1 * second.setReadOnly()
        1 * first.setReadWrite()
        1 * second.setReadWrite()
    }

    void "destroy only destroys open interceptors and ignores failures"() {
        when:
        aggregate.destroy()

        then:
        1 * first.isOpen() >> true
        1 * first.destroy() >> { throw new IllegalStateException('boom') }
        1 * second.isOpen() >> false
        0 * second.destroy()
        noExceptionThrown()

        when:
        aggregate.destroy()

        then:
        1 * first.isOpen() >> { throw new IllegalStateException('cannot check') }
        1 * second.isOpen() >> true
        1 * second.destroy()
        noExceptionThrown()
    }

}

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

import org.grails.datastore.mapping.simple.SimpleMapDatastore
import org.springframework.transaction.support.TransactionSynchronizationManager
import spock.lang.Specification

class AbstractDatastorePersistenceContextInterceptorSpec extends Specification {

    SimpleMapDatastore datastore = new SimpleMapDatastore()

    void cleanup() {
        if (TransactionSynchronizationManager.getResource(datastore) != null) {
            TransactionSynchronizationManager.unbindResource(datastore)
        }
    }

    void "test init binds a new session to the thread when none is bound"() {
        given:
        def interceptor = new TestInterceptor(datastore)

        expect:
        !interceptor.isOpen()

        when:
        interceptor.init()

        then:
        interceptor.isOpen()
        TransactionSynchronizationManager.getResource(datastore) != null
    }

    void "test init does not bind a second session when one is already bound"() {
        given:
        def first = new TestInterceptor(datastore)
        first.init()
        def existingHolder = TransactionSynchronizationManager.getResource(datastore)

        when:
        def second = new TestInterceptor(datastore)
        second.init()

        then:
        TransactionSynchronizationManager.getResource(datastore).is(existingHolder)
    }

    void "test destroy unbinds and closes the session it created"() {
        given:
        def interceptor = new TestInterceptor(datastore)
        interceptor.init()

        when:
        interceptor.destroy()

        then:
        TransactionSynchronizationManager.getResource(datastore) == null
        !interceptor.isOpen()
    }

    void "test destroy does nothing when the bound session was created by a different interceptor"() {
        given:
        def creator = new TestInterceptor(datastore)
        creator.init()
        def other = new TestInterceptor(datastore)

        when:
        other.destroy()

        then:
        TransactionSynchronizationManager.getResource(datastore) != null
    }

    void "test disconnect delegates to destroy"() {
        given:
        def interceptor = new TestInterceptor(datastore)
        interceptor.init()

        when:
        interceptor.disconnect()

        then:
        TransactionSynchronizationManager.getResource(datastore) == null
    }

    void "test reconnect delegates to init"() {
        given:
        def interceptor = new TestInterceptor(datastore)
        interceptor.init()
        interceptor.disconnect()

        when:
        interceptor.reconnect()

        then:
        interceptor.isOpen()
    }

    void "test clear delegates to the session"() {
        given:
        def interceptor = new TestInterceptor(datastore)
        interceptor.init()

        when:
        interceptor.clear()

        then:
        noExceptionThrown()
    }

    void "test flush is a no-op outside of a transaction"() {
        given:
        def interceptor = new TestInterceptor(datastore)
        interceptor.init()

        when:
        interceptor.flush()

        then:
        noExceptionThrown()
    }

    void "test setReadOnly and setReadWrite change the session flush mode without error"() {
        given:
        def interceptor = new TestInterceptor(datastore)
        interceptor.init()

        when:
        interceptor.setReadOnly()
        interceptor.setReadWrite()

        then:
        noExceptionThrown()
    }

    void "test isOpen returns false when no session is bound"() {
        given:
        def interceptor = new TestInterceptor(datastore)

        expect:
        !interceptor.isOpen()
    }

    static class TestInterceptor extends AbstractDatastorePersistenceContextInterceptor {

        TestInterceptor(SimpleMapDatastore datastore) {
            super(datastore)
        }
    }
}

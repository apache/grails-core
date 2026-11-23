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
package org.grails.datastore.mapping.web.support

import jakarta.persistence.FlushModeType

import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.datastore.mapping.core.Session
import org.grails.datastore.mapping.transactions.SessionHolder
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.transaction.support.TransactionSynchronizationUtils
import org.springframework.web.context.request.ServletWebRequest
import spock.lang.Specification

/**
 * The session of a request is opened by the first interceptor call that finds none bound, used by an include within
 * that request, and closed when the request completes or when it goes asynchronous.
 */
class OpenSessionInViewInterceptorSpec extends Specification {

    Datastore datastore = Mock()
    Session session = Mock()
    OpenSessionInViewInterceptor interceptor = new OpenSessionInViewInterceptor(datastore: datastore)
    ServletWebRequest request = new ServletWebRequest(new MockHttpServletRequest('GET', '/books'))

    void setup() {
        datastore.connect() >> session
        session.datastore >> datastore
        session.flushMode >> FlushModeType.AUTO
        session.hasTransaction() >> true
    }

    void cleanup() {
        if (TransactionSynchronizationManager.synchronizationActive) {
            TransactionSynchronizationManager.clearSynchronization()
        }
        TransactionSynchronizationManager.resourceMap.keySet().toList().each { key ->
            TransactionSynchronizationManager.unbindResource(key)
        }
    }

    private Session boundSession() {
        ((SessionHolder) TransactionSynchronizationManager.getResource(datastore))?.session
    }

    void 'a request without a session opens one, flushes it after the action and closes it when it completes'() {
        when:
        interceptor.preHandle(request)

        then:
        boundSession().is(session)

        when:
        interceptor.postHandle(request, null)

        then:
        1 * session.flush()

        when:
        interceptor.afterCompletion(request, null)

        then:
        1 * session.disconnect()
        boundSession() == null
    }

    void 'an include uses the session of the request and leaves it bound and open'() {
        given: 'the request has opened its session'
        interceptor.preHandle(request)

        when: 'an include within it starts and completes'
        interceptor.preHandle(request)
        interceptor.postHandle(request, null)
        interceptor.afterCompletion(request, null)

        then:
        0 * session.disconnect()
        boundSession().is(session)

        when: 'the request completes'
        interceptor.afterCompletion(request, null)

        then:
        1 * session.disconnect()
        boundSession() == null
    }

    void 'a request that goes asynchronous flushes and closes its session and leaves the thread without one'() {
        given:
        interceptor.preHandle(request)

        when:
        interceptor.afterConcurrentHandlingStarted(request)

        then:
        1 * session.flush()
        1 * session.disconnect()
        boundSession() == null
    }

    void 'the session is closed and unbound when its flush fails as the request goes asynchronous'() {
        given:
        interceptor.preHandle(request)

        when:
        interceptor.afterConcurrentHandlingStarted(request)

        then:
        1 * session.flush() >> { throw new IllegalStateException('flush failed') }
        thrown(IllegalStateException)
        1 * session.disconnect()
        boundSession() == null
    }

    void 'a session that was bound before the request stays bound and open when the request goes asynchronous'() {
        given: 'a session bound by the caller'
        DatastoreUtils.bindSession(session)

        when:
        interceptor.preHandle(request)
        interceptor.afterConcurrentHandlingStarted(request)

        then:
        0 * session.flush()
        0 * session.disconnect()
        boundSession().is(session)
    }

    void 'a session that transaction synchronization binds as it is opened is left for the transaction to close'() {
        given: 'synchronization is active, so opening the session binds it and registers its cleanup'
        TransactionSynchronizationManager.initSynchronization()

        when:
        interceptor.preHandle(request)
        interceptor.postHandle(request, null)
        interceptor.afterCompletion(request, null)

        then:
        0 * session.disconnect()
        boundSession().is(session)

        when: 'the transaction completes'
        List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.synchronizations
        TransactionSynchronizationUtils.triggerBeforeCompletion()
        TransactionSynchronizationUtils.invokeAfterCompletion(synchronizations, TransactionSynchronization.STATUS_COMMITTED)

        then:
        1 * session.disconnect()
        boundSession() == null
    }
}

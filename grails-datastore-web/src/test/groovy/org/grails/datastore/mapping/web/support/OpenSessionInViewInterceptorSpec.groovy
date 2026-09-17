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

import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.ui.ModelMap
import org.springframework.web.context.request.WebRequest
import spock.lang.Specification

import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.core.Session
import org.grails.datastore.mapping.transactions.SessionHolder

class OpenSessionInViewInterceptorSpec extends Specification {

    Datastore datastore = Mock(Datastore)
    Session session = Mock(Session) {
        getDatastore() >> datastore
        isConnected() >> true
    }
    WebRequest request = Stub(WebRequest)
    OpenSessionInViewInterceptor interceptor = new OpenSessionInViewInterceptor(datastore: datastore)

    void cleanup() {
        if (TransactionSynchronizationManager.hasResource(datastore)) {
            TransactionSynchronizationManager.unbindResource(datastore)
        }
    }

    void 'a session is opened, bound, flushed and closed around the request'() {
        when:
        interceptor.preHandle(request)

        then:
        interceptor.datastore.is(datastore)
        1 * datastore.connect() >> session
        1 * session.setFlushMode(FlushModeType.AUTO)
        ((SessionHolder) TransactionSynchronizationManager.getResource(datastore)).session.is(session)

        when:
        interceptor.postHandle(request, new ModelMap())

        then:
        1 * session.getFlushMode() >> FlushModeType.AUTO
        1 * session.hasTransaction() >> true
        1 * session.flush()

        when:
        interceptor.afterCompletion(request, null)

        then:
        1 * session.disconnect()
        !TransactionSynchronizationManager.hasResource(datastore)
    }

    void 'a session without a transaction or with manual flushing is not flushed'() {
        given:
        datastore.connect() >> session
        interceptor.preHandle(request)

        when:
        interceptor.postHandle(request, new ModelMap())

        then:
        1 * session.getFlushMode() >> FlushModeType.AUTO
        1 * session.hasTransaction() >> false
        0 * session.flush()

        when:
        interceptor.postHandle(request, new ModelMap())

        then:
        1 * session.getFlushMode() >> FlushModeType.COMMIT
        0 * session.hasTransaction()
        0 * session.flush()
    }

    void 'an already bound session is reused and nothing happens without one'() {
        given:
        TransactionSynchronizationManager.bindResource(datastore, new SessionHolder(session))

        when:
        interceptor.preHandle(request)

        then:
        0 * datastore.connect()
        0 * session.setFlushMode(_)

        when:
        TransactionSynchronizationManager.unbindResource(datastore)
        interceptor.postHandle(request, new ModelMap())
        interceptor.afterCompletion(request, null)

        then:
        0 * session._
    }

}

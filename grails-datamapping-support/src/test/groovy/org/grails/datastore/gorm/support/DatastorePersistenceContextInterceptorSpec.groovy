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

import jakarta.persistence.FlushModeType
import org.springframework.transaction.support.TransactionSynchronizationManager
import spock.lang.Specification

import grails.persistence.support.PersistenceContextInterceptor
import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.core.Session
import org.grails.datastore.mapping.transactions.SessionHolder

class DatastorePersistenceContextInterceptorSpec extends Specification {

    Datastore datastore = Mock(Datastore)
    Session session = Mock(Session) { getDatastore() >> datastore }

    void cleanup() {
        if (TransactionSynchronizationManager.hasResource(datastore)) {
            TransactionSynchronizationManager.unbindResource(datastore)
        }
    }

    void "the datastore interceptor is a grails persistence context interceptor bound to its datastore"() {
        given:
        DatastorePersistenceContextInterceptor interceptor = new DatastorePersistenceContextInterceptor(datastore)

        expect:
        interceptor instanceof PersistenceContextInterceptor
        !interceptor.open

        when:
        interceptor.init()

        then:
        1 * datastore.connect() >> session
        1 * session.setFlushMode(FlushModeType.AUTO)
        ((SessionHolder) TransactionSynchronizationManager.getResource(datastore)).session.is(session)
        ((SessionHolder) TransactionSynchronizationManager.getResource(datastore)).creator.is(interceptor)

        when:
        boolean open = interceptor.open

        then:
        (1.._) * session.isConnected() >> true
        open

        when:
        interceptor.destroy()

        then:
        1 * session.disconnect()
        !TransactionSynchronizationManager.hasResource(datastore)
    }

}

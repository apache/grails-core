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
package datasources

import java.util.concurrent.CountDownLatch

import grails.util.Holders
import org.grails.orm.hibernate.HibernateDatastore
import org.hibernate.Session
import org.springframework.orm.hibernate5.SessionHolder
import org.springframework.transaction.support.TransactionSynchronizationManager

/**
 * The sessions that the open session in view interceptor has bound to the current thread, and the ones an
 * asynchronous action of the scenario controllers ran with.
 */
class RequestSessions {

    static volatile CountDownLatch release = new CountDownLatch(1)
    static volatile Session asyncSecondarySession
    static volatile Session asyncDefaultSession

    static Session defaultSession() {
        final sessionFactory = Holders.applicationContext.getBean(HibernateDatastore).sessionFactory
        ((SessionHolder) TransactionSynchronizationManager.getResource(sessionFactory))?.session
    }

    static Session secondary() {
        final sessionFactory = Holders.applicationContext.getBean(HibernateDatastore)
                .getDatastoreForConnection('secondary').sessionFactory
        ((SessionHolder) TransactionSynchronizationManager.getResource(sessionFactory))?.session
    }

    static String describe(Session session) {
        session == null ? 'none' : "${System.identityHashCode(session)}/${session.open ? 'open' : 'closed'}"
    }
}

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
package functional.tests

import java.util.concurrent.CountDownLatch

import grails.util.Holders
import org.grails.datastore.mapping.core.Session
import org.grails.datastore.mapping.transactions.SessionHolder
import org.springframework.transaction.support.TransactionSynchronizationManager

/**
 * The session that the open session in view interceptor has bound to the current thread, and the session an
 * asynchronous action of the scenario controllers ran with.
 */
class RequestSessions {

    static volatile CountDownLatch release = new CountDownLatch(1)
    static volatile Session asyncSession

    static Session current() {
        final datastore = Holders.applicationContext.getBean('mongoOpenSessionInViewInterceptor').datastore
        ((SessionHolder) TransactionSynchronizationManager.getResource(datastore))?.session
    }

    static String describe(Session session) {
        session == null ? 'none' : "${System.identityHashCode(session)}/${session.connected ? 'open' : 'closed'}"
    }

    static String readPlayers() {
        try {
            Player.list().collect { it.team?.name }.size().toString()
        } catch (Exception e) {
            e.getClass().simpleName
        }
    }

    static void saveBeforeGoingAsynchronous() {
        asyncSession = current()
        new Player(name: 'Saved before going asynchronous', team: Team.first()).save()
    }
}

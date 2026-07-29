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

import org.springframework.transaction.support.TransactionSynchronizationManager

import org.grails.datastore.mapping.transactions.SessionHolder
import spock.lang.Specification

class TransactionSynchronizationSessionResolverSpec extends Specification {

    Datastore datastore = Mock()
    TransactionSynchronizationSessionResolver resolver = new TransactionSynchronizationSessionResolver(datastore)

    void cleanup() {
        TransactionSynchronizationManager.unbindResourceIfPossible(datastore)
    }

    private Session connectedSession() {
        Session session = Mock(Session)
        session.getDatastore() >> datastore
        session.isConnected() >> true
        return session
    }

    def "should bind and resolve session"() {
        given:
        Session session = connectedSession()

        when:
        resolver.bind(session)

        then:
        resolver.resolve() == session
    }

    def "should unbind session"() {
        given:
        Session session = connectedSession()
        resolver.bind(session)

        when:
        resolver.unbind()

        then:
        resolver.resolve() == null
    }

    def "bind() rejects a session that belongs to a different datastore"() {
        given: "a session owned by some other datastore"
        Datastore otherDatastore = Mock(Datastore)
        Session session = Mock(Session)
        session.getDatastore() >> otherDatastore

        when:
        resolver.bind(session)

        then: "the mismatch is rejected up front instead of binding under one key and resolving under another"
        thrown(IllegalArgumentException)
        resolver.resolve() == null
    }

    def "resolve() returns the same session DatastoreUtils.bindSession bound, not an independent copy"() {
        given: "a session bound the way GORM's own transaction/session machinery binds it"
        Session session = connectedSession()

        when:
        DatastoreUtils.bindSession(session)

        then: "the resolver sees it too - there is only one authoritative store"
        resolver.resolve() == session
    }

    def "resolve() evicts a disconnected session instead of returning it, matching doGetSession's validation"() {
        given: "a bound session that has since been disconnected"
        Session session = Mock(Session)
        session.getDatastore() >> datastore
        session.isConnected() >> false
        DatastoreUtils.bindSession(session)

        expect: "the stale session is not resolvable"
        resolver.resolve() == null

        and: "it was evicted from the holder, not just skipped"
        SessionHolder holder = (SessionHolder) TransactionSynchronizationManager.getResource(datastore)
        !holder.containsSession(session)
    }

    def "resolve() returns null for a bound but empty SessionHolder"() {
        given: "a holder left bound after its only session was removed"
        Session session = connectedSession()
        DatastoreUtils.bindSession(session)
        SessionHolder holder = (SessionHolder) TransactionSynchronizationManager.getResource(datastore)
        holder.removeSession(session)

        expect:
        resolver.resolve() == null
    }

    def "a session bound on one thread is not visible to another thread"() {
        given:
        Session session = connectedSession()
        resolver.bind(session)

        when:
        def resolvedOnOtherThread = Executors.newVirtualThreadPerTaskExecutor().withCloseable { executor ->
            executor.submit({ resolver.resolve() } as Callable<Session>).get()
        }

        then:
        resolvedOnOtherThread == null
        resolver.resolve() == session
    }

    def "bind() stacks onto an already-bound session rather than replacing it, mirroring SessionHolder's nested-scope support"() {
        given:
        Session first = Mock(Session)
        Session second = Mock(Session)
        first.getDatastore() >> datastore
        first.isConnected() >> true
        second.getDatastore() >> datastore
        second.isConnected() >> true

        when:
        resolver.bind(first)
        resolver.bind(second)

        then: "resolve() returns the most recently bound (top-of-stack) session"
        resolver.resolve() == second

        when:
        resolver.unbind()

        then: "unbind() closes and pops only the top session, restoring the outer binding"
        1 * second.disconnect()
        0 * first.disconnect()
        resolver.resolve() == first

        when:
        resolver.unbind()

        then: "unbinding the last remaining session closes it and clears the binding entirely"
        1 * first.disconnect()
        resolver.resolve() == null
    }

    def "unbind() is a no-op when nothing is bound"() {
        when:
        resolver.unbind()

        then:
        noExceptionThrown()
        resolver.resolve() == null
    }
}

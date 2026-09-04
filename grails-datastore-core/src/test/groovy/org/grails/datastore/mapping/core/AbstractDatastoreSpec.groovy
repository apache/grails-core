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

import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.support.GenericApplicationContext
import org.springframework.core.env.PropertyResolver
import org.springframework.transaction.support.TransactionSynchronizationManager

import spock.lang.Specification

import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.transactions.SessionHolder

class AbstractDatastoreSpec extends Specification {

    void "getApplicationEventPublisher returns the application context if set"() {
        given:
        def mappingContext = Mock(MappingContext)
        def ctx = new GenericApplicationContext()
        ctx.refresh()
        def datastore = new TestDatastore(mappingContext, (PropertyResolver) null, ctx)

        expect:
        datastore.applicationEventPublisher == ctx
        datastore.applicationContext == ctx
    }

    void "getApplicationEventPublisher returns null when constructed without an application context"() {
        given:
        def mappingContext = Mock(MappingContext)
        def datastore = new TestDatastore(mappingContext, (PropertyResolver) null, null)

        expect: "no default publisher is installed, so per-query publish guards stay off"
        datastore.getApplicationEventPublisher() == null
    }

    void "setApplicationContext(null) clears the publisher"() {
        given:
        def mappingContext = Mock(MappingContext)
        def ctx = new GenericApplicationContext()
        ctx.refresh()
        def datastore = new TestDatastore(mappingContext, (PropertyResolver) null, ctx)

        when:
        datastore.setApplicationContext(null)

        then:
        datastore.getApplicationEventPublisher() == null
        datastore.applicationContext == null
    }

    void "SessionCreationEvent is published when connect is called"() {
        given:
        def mappingContext = Mock(MappingContext)
        def events = []
        def ctx = new GenericApplicationContext()
        ctx.addApplicationListener({ event -> events << event })
        ctx.refresh()

        def datastore = new TestDatastore(mappingContext, (PropertyResolver) null, ctx)
        def mockSession = Mock(Session)
        mockSession.getDatastore() >> datastore
        datastore.sessionCreator = { mockSession }

        when:
        def session = datastore.connect()

        then:
        session == mockSession
        events.any { it instanceof SessionCreationEvent }
        ((SessionCreationEvent) events.find { it instanceof SessionCreationEvent }).session == session
    }

    void "getSessionResolver returns the same instance on every call"() {
        given:
        def mappingContext = Mock(MappingContext)
        def datastore = new TestDatastore(mappingContext, (PropertyResolver) null, null)

        expect: "callers can cache and compare resolvers"
        datastore.getSessionResolver().is(datastore.getSessionResolver())
    }

    void "hasCurrentSession and getCurrentSession agree when the bound session has been disconnected"() {
        given: "a bound session that has since been disconnected"
        def mappingContext = Mock(MappingContext)
        def datastore = new TestDatastore(mappingContext, (PropertyResolver) null, null)
        def session = Mock(Session)
        session.getDatastore() >> datastore
        session.isConnected() >> false
        DatastoreUtils.bindSession(session)

        expect: "hasCurrentSession does not claim a session getCurrentSession would refuse to return"
        !datastore.hasCurrentSession()

        when:
        datastore.getCurrentSession()

        then:
        thrown(IllegalStateException)

        cleanup:
        TransactionSynchronizationManager.unbindResourceIfPossible(datastore)
    }

    void "hasCurrentSession and getCurrentSession agree for a bound but empty SessionHolder"() {
        given: "a holder left bound after its only session was removed"
        def mappingContext = Mock(MappingContext)
        def datastore = new TestDatastore(mappingContext, (PropertyResolver) null, null)
        def session = Mock(Session)
        session.getDatastore() >> datastore
        session.isConnected() >> true
        DatastoreUtils.bindSession(session)
        SessionHolder holder = (SessionHolder) TransactionSynchronizationManager.getResource(datastore)
        holder.removeSession(session)

        expect:
        !datastore.hasCurrentSession()

        when:
        datastore.getCurrentSession()

        then:
        thrown(IllegalStateException)

        cleanup:
        TransactionSynchronizationManager.unbindResourceIfPossible(datastore)
    }

    void "hasCurrentSession probing one datastore does not evict or unbind an unrelated datastore's stale session"() {
        given: "two independently-bound datastores, as a routing scan (e.g. ActiveSessionDatastoreSelector) would encounter"
        def mappingContext = Mock(MappingContext)
        def datastoreA = new TestDatastore(mappingContext, (PropertyResolver) null, null)
        def datastoreB = new TestDatastore(mappingContext, (PropertyResolver) null, null)
        def staleSessionOnB = Mock(Session)
        staleSessionOnB.getDatastore() >> datastoreB
        staleSessionOnB.isConnected() >> false
        DatastoreUtils.bindSession(staleSessionOnB)

        when: "a probe scans datastore A - unrelated to B - the way a multi-datastore routing lookup would"
        boolean aHasSession = datastoreA.hasCurrentSession()
        boolean bHasSession = datastoreB.hasCurrentSession()

        then: "both probes report accurately"
        !aHasSession
        !bHasSession

        and: "B's stale binding is untouched by having been probed - not evicted, not unbound"
        TransactionSynchronizationManager.getResource(datastoreB) != null
        ((SessionHolder) TransactionSynchronizationManager.getResource(datastoreB)).containsSession(staleSessionOnB)

        cleanup:
        TransactionSynchronizationManager.unbindResourceIfPossible(datastoreB)
    }

    void "destroy() closes every session held on the current thread and clears the binding"() {
        given:
        def mappingContext = Mock(MappingContext) {
            getPersistentEntities() >> []
        }
        def datastore = new TestDatastore(mappingContext, (PropertyResolver) null, null)
        def session = Mock(Session)
        session.getDatastore() >> datastore
        DatastoreUtils.bindSession(session)

        when:
        datastore.destroy()

        then:
        1 * session.disconnect()
        !datastore.hasCurrentSession()
        datastore.sessionResolver.resolve() == null
    }

    void "destroy() logs and continues when a session's disconnect() throws"() {
        given:
        def mappingContext = Mock(MappingContext) {
            getPersistentEntities() >> []
        }
        def datastore = new TestDatastore(mappingContext, (PropertyResolver) null, null)
        def session = Mock(Session)
        session.getDatastore() >> datastore
        session.disconnect() >> { throw new IllegalStateException("boom") }
        DatastoreUtils.bindSession(session)

        when:
        datastore.destroy()

        then:
        noExceptionThrown()
        !datastore.hasCurrentSession()
    }

    void "destroy() leaves a holder that is synchronized with an active transaction alone"() {
        given: "a holder the transaction manager still owns"
        def mappingContext = Mock(MappingContext) {
            getPersistentEntities() >> []
        }
        def datastore = new TestDatastore(mappingContext, (PropertyResolver) null, null)
        def session = Mock(Session)
        session.getDatastore() >> datastore
        session.isConnected() >> true
        DatastoreUtils.bindSession(session)
        SessionHolder holder = (SessionHolder) TransactionSynchronizationManager.getResource(datastore)
        holder.setSynchronizedWithTransaction(true)

        when:
        datastore.destroy()

        then: "the transaction's session is not closed out from under commit/rollback"
        0 * session.disconnect()
        TransactionSynchronizationManager.getResource(datastore).is(holder)

        cleanup:
        TransactionSynchronizationManager.unbindResourceIfPossible(datastore)
    }

    static class TestDatastore extends AbstractDatastore {
        Closure<Session> sessionCreator = { null }

        TestDatastore(MappingContext mappingContext, PropertyResolver connectionDetails, ConfigurableApplicationContext ctx) {
            super(mappingContext, (PropertyResolver) connectionDetails, ctx)
        }

        @Override
        protected Session createSession(PropertyResolver connectionDetails) {
            return sessionCreator.call(connectionDetails)
        }
    }
}

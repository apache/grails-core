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

import org.springframework.core.env.MapPropertySource
import org.springframework.core.env.PropertyResolver
import org.springframework.core.env.StandardEnvironment
import org.springframework.transaction.support.TransactionSynchronizationManager

import spock.lang.Specification

import org.grails.datastore.mapping.transactions.SessionHolder

/**
 * Created by graemerocher on 01/03/2017.
 */
class DatastoreUtilsSpec extends Specification {

    void "test prepare property source from env"() {
        given:
        StandardEnvironment env = new StandardEnvironment()
        env.propertySources.addFirst(new MapPropertySource("test", ['grails.foo':'bar']))
        env.propertySources.addFirst(new MapPropertySource("test2", ['grails.foo':'baz']))

        PropertyResolver resolver = DatastoreUtils.preparePropertyResolver(env)
        expect:
        env != null
        resolver.getProperty('grails.foo', String) == 'baz'
    }

    void "deferred close lifecycle is isolated on a virtual thread"() {
        given:
        Datastore datastore = Mock()
        Session session = Mock()

        when:
        def repeatedCloseMessage = Executors.newVirtualThreadPerTaskExecutor().withCloseable { executor ->
            executor.submit({
                DatastoreUtils.initDeferredClose(datastore)
                DatastoreUtils.closeSessionOrRegisterDeferredClose(session, datastore)
                DatastoreUtils.processDeferredClose(datastore)
                try {
                    DatastoreUtils.processDeferredClose(datastore)
                    null
                }
                catch (IllegalStateException e) {
                    e.message
                }
            } as Callable<String>).get()
        }

        then:
        1 * session.disconnect()
        repeatedCloseMessage.contains('Deferred close not active')
    }

    void "soft thread local map does not expose parent entries to child threads"() {
        given:
        def threadLocal = new SoftThreadLocalMap()
        threadLocal.get().put('tenant', 'parent')

        when:
        def childTenant = Executors.newVirtualThreadPerTaskExecutor().withCloseable { executor ->
            executor.submit({
                threadLocal.get().get('tenant') as String
            } as Callable<String>).get()
        }

        then:
        childTenant == null
        threadLocal.get().get('tenant') == 'parent'

        cleanup:
        threadLocal.remove()
    }

    void "bindSession fails fast when a session is already bound for the datastore"() {
        given:
        Datastore datastore = Mock()
        Session first = Mock()
        Session second = Mock()
        first.getDatastore() >> datastore
        second.getDatastore() >> datastore

        when:
        DatastoreUtils.bindSession(first)
        DatastoreUtils.bindSession(second)

        then: "the second bind is rejected rather than silently stacked, so a leaked prior bind is surfaced immediately"
        thrown(IllegalStateException)

        cleanup:
        TransactionSynchronizationManager.unbindResourceIfPossible(datastore)
    }

    void "bindSession with a creator fails fast when a session is already bound for the datastore"() {
        given:
        Datastore datastore = Mock()
        Session first = Mock()
        Session second = Mock()
        first.getDatastore() >> datastore
        second.getDatastore() >> datastore

        when:
        DatastoreUtils.bindSession(first, new Object())
        DatastoreUtils.bindSession(second, new Object())

        then:
        thrown(IllegalStateException)

        cleanup:
        TransactionSynchronizationManager.unbindResourceIfPossible(datastore)
    }

    void "executeWithNewSession(SessionCallback) binds a new session, runs the callback, and unbinds/closes it afterwards"() {
        given:
        Datastore datastore = Mock()
        Session newSession = Mock()
        newSession.getDatastore() >> datastore
        datastore.connect() >> newSession

        when:
        def result = DatastoreUtils.executeWithNewSession(datastore, { Session session -> session } as SessionCallback)

        then:
        result.is(newSession)
        1 * newSession.disconnect()
        !TransactionSynchronizationManager.hasResource(datastore)
    }

    void "executeWithNewSession(VoidSessionCallback) delegates to the SessionCallback overload"() {
        given:
        Datastore datastore = Mock()
        Session newSession = Mock()
        newSession.getDatastore() >> datastore
        datastore.connect() >> newSession
        Session received = null

        when:
        DatastoreUtils.executeWithNewSession(datastore, { Session session -> received = session } as VoidSessionCallback)

        then:
        received.is(newSession)
        1 * newSession.disconnect()
        !TransactionSynchronizationManager.hasResource(datastore)
    }

    void "execute survives a bound but empty SessionHolder by stacking onto it instead of rebinding"() {
        given: "a holder left bound after its only session was removed, and a datastore with resolver-backed hasCurrentSession"
        Datastore datastore = new AbstractDatastoreSpec.TestDatastore(null, null, null)
        Session stale = Mock()
        stale.getDatastore() >> datastore
        stale.isConnected() >> true
        DatastoreUtils.bindSession(stale)
        SessionHolder holder = (SessionHolder) TransactionSynchronizationManager.getResource(datastore)
        holder.removeSession(stale)

        and: "a fresh session for the callback"
        Session fresh = Mock()
        fresh.getDatastore() >> datastore
        fresh.isConnected() >> true
        datastore.sessionCreator = { fresh }

        when:
        def result = DatastoreUtils.execute(datastore, { Session session -> session } as SessionCallback)

        then: "no 'already value bound' failure, and the temporary session is cleaned up"
        result.is(fresh)
        1 * fresh.disconnect()

        cleanup:
        TransactionSynchronizationManager.unbindResourceIfPossible(datastore)
    }

    void "executeWithNewSession cleans up correctly when connect() returns a session owned by a different datastore"() {
        given: "a parent datastore whose connect() hands out a child datastore's session, like ChildHibernateDatastore"
        Datastore parent = Mock()
        Datastore child = Mock()
        Session childSession = Mock()
        childSession.getDatastore() >> child
        parent.connect() >> childSession

        when:
        def result = DatastoreUtils.executeWithNewSession(parent, { Session session -> session } as SessionCallback)

        then: "the session is unbound from the key it was bound under, so nothing leaks"
        result.is(childSession)
        1 * childSession.disconnect()
        !TransactionSynchronizationManager.hasResource(child)
        !TransactionSynchronizationManager.hasResource(parent)
    }

    void "executeWithNewSession stacks onto an existing bound session rather than replacing it"() {
        given:
        Datastore datastore = Mock()
        Session existing = Mock()
        Session inner = Mock()
        existing.getDatastore() >> datastore
        inner.getDatastore() >> datastore
        datastore.connect() >> inner
        DatastoreUtils.bindSession(existing)

        when:
        DatastoreUtils.executeWithNewSession(datastore, { Session session -> session } as SessionCallback)
        SessionHolder holder = (SessionHolder) TransactionSynchronizationManager.getResource(datastore)

        then: "the callback's session is unbound again, leaving the pre-existing one intact"
        holder != null
        holder.containsSession(existing)
        !holder.containsSession(inner)

        cleanup:
        TransactionSynchronizationManager.unbindResourceIfPossible(datastore)
    }

    void "bindNewSession adds the session to the existing holder instead of failing when a session is already bound"() {
        given:
        Datastore datastore = Mock()
        Session first = Mock()
        Session second = Mock()
        first.getDatastore() >> datastore
        second.getDatastore() >> datastore

        when:
        DatastoreUtils.bindSession(first)
        DatastoreUtils.bindNewSession(second)
        SessionHolder holder = (SessionHolder) TransactionSynchronizationManager.getResource(datastore)

        then:
        holder.containsSession(first)
        holder.containsSession(second)
        holder.getSession() == second

        cleanup:
        TransactionSynchronizationManager.unbindResourceIfPossible(datastore)
    }
}

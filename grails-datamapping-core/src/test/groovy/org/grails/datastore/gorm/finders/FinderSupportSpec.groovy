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
package org.grails.datastore.gorm.finders

import org.grails.datastore.gorm.DatastoreResolver
import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.core.Session
import org.grails.datastore.mapping.core.SessionCallback
import org.grails.datastore.mapping.core.VoidSessionCallback
import spock.lang.Specification

/**
 * Exercises {@link FinderSupport} - the shared session-execution helper every synchronous finder
 * in this package calls with its own {@code DatastoreResolver} field, rather than inheriting an
 * {@code execute} method. The datastore is resolved on every call, so a lazy resolver is honoured.
 */
class FinderSupportSpec extends Specification {

    void "resolverFor wraps a datastore in a resolver that always returns it"() {
        given:
        Datastore datastore = Stub(Datastore)

        expect:
        FinderSupport.resolverFor(datastore).resolve().is(datastore)
    }

    void "resolverFor returns null for a null datastore, preserving stateless mode"() {
        expect:
        FinderSupport.resolverFor(null) == null
    }

    void "execute(SessionCallback) throws IllegalStateException when the resolver is null"() {
        given:
        SessionCallback callback = { Session session -> 'result' } as SessionCallback

        when:
        FinderSupport.execute(null, callback)

        then:
        IllegalStateException e = thrown()
        e.message == 'Cannot execute session query with null datastore'
    }

    void "execute(SessionCallback) throws IllegalStateException when the resolver resolves no datastore"() {
        given:
        DatastoreResolver resolver = Mock(DatastoreResolver)
        SessionCallback callback = { Session session -> 'result' } as SessionCallback

        when:
        FinderSupport.execute(resolver, callback)

        then:
        IllegalStateException e = thrown()
        e.message == 'Cannot execute session query with null datastore'
    }

    void "execute(VoidSessionCallback) throws IllegalStateException when the resolver is null"() {
        given:
        VoidSessionCallback callback = { Session session -> } as VoidSessionCallback

        when:
        FinderSupport.execute(null, callback)

        then:
        IllegalStateException e = thrown()
        e.message == 'Cannot execute session query with null datastore'
    }

    void "execute(SessionCallback) resolves the datastore on every call and runs the callback in its session"() {
        given:
        Session session = Stub(Session)
        Datastore datastore = Stub(Datastore) {
            hasCurrentSession() >> true
            getCurrentSession() >> session
        }
        DatastoreResolver resolver = Mock(DatastoreResolver)
        SessionCallback callback = { Session s -> s } as SessionCallback

        when:
        def first = FinderSupport.execute(resolver, callback)
        def second = FinderSupport.execute(resolver, callback)

        then:
        2 * resolver.resolve() >> datastore
        first.is(session)
        second.is(session)
    }

    void "execute(VoidSessionCallback) resolves the datastore and runs the callback in its session"() {
        given:
        Session session = Stub(Session)
        Datastore datastore = Stub(Datastore) {
            hasCurrentSession() >> true
            getCurrentSession() >> session
        }
        DatastoreResolver resolver = Mock(DatastoreResolver)
        List<Session> seen = []
        VoidSessionCallback callback = { Session s -> seen << s } as VoidSessionCallback

        when:
        FinderSupport.execute(resolver, callback)

        then:
        1 * resolver.resolve() >> datastore
        seen == [session]
    }
}

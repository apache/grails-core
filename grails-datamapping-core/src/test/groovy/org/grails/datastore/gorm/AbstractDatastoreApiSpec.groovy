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
package org.grails.datastore.gorm

import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.core.SessionCallback
import org.grails.datastore.mapping.core.VoidSessionCallback
import org.grails.datastore.mapping.simple.SimpleMapDatastore
import spock.lang.AutoCleanup
import spock.lang.Specification

/**
 * {@code AbstractDatastoreApi}'s own {@code execute(SessionCallback)}/{@code execute(VoidSessionCallback)}
 * are shadowed by its only real subclass, {@link AbstractGormApi} (which overrides both with its
 * own qualifier/tenant-aware implementation - see item 7's {@code AbstractGormApiSpec}) - so these
 * two methods are effectively dead code under real GORM usage. Tested here via a minimal,
 * purpose-built subclass (same technique as item 7's {@code MinimalGormApi}) so the base class's
 * own contract - lazy resolution via the new {@code DatastoreResolver}, and the null-datastore
 * guard - is verified directly rather than left entirely uncovered.
 */
class AbstractDatastoreApiSpec extends Specification {

    @AutoCleanup
    SimpleMapDatastore datastore = new SimpleMapDatastore()

    static class MinimalDatastoreApi extends AbstractDatastoreApi {
        MinimalDatastoreApi(Datastore datastore) { super(datastore) }
        MinimalDatastoreApi(DatastoreResolver resolver) { super(resolver) }
    }

    void "execute(SessionCallback) resolves the datastore lazily via a DatastoreResolver and runs the callback"() {
        given:
        def resolver = { datastore } as DatastoreResolver
        def api = new MinimalDatastoreApi(resolver)

        when:
        def result = api.execute(new SessionCallback<String>() {
            @Override
            String doInSession(org.grails.datastore.mapping.core.Session session) { 'callback ran' }
        })

        then:
        result == 'callback ran'
    }

    void "execute(SessionCallback) throws IllegalStateException when the resolver yields no datastore"() {
        given:
        def resolver = { null } as DatastoreResolver
        def api = new MinimalDatastoreApi(resolver)

        when:
        api.execute(new SessionCallback<String>() {
            @Override
            String doInSession(org.grails.datastore.mapping.core.Session session) { 'unreachable' }
        })

        then:
        def e = thrown(IllegalStateException)
        e.message == 'Cannot execute session callback with null datastore'
    }

    void "execute(VoidSessionCallback) resolves the datastore lazily via a DatastoreResolver and runs the callback"() {
        given:
        def api = new MinimalDatastoreApi(datastore)
        boolean called = false

        when:
        api.execute(new VoidSessionCallback() {
            @Override
            void doInSession(org.grails.datastore.mapping.core.Session session) { called = true }
        })

        then:
        called
    }

    void "execute(VoidSessionCallback) throws IllegalStateException when no datastore can be resolved"() {
        given:
        def api = new MinimalDatastoreApi((Datastore) null)

        when:
        api.execute(new VoidSessionCallback() {
            @Override
            void doInSession(org.grails.datastore.mapping.core.Session session) { }
        })

        then:
        def e = thrown(IllegalStateException)
        e.message == 'Cannot execute session callback with null datastore'
    }

    void "getDatastore returns null rather than throwing when no datastore is configured"() {
        given:
        def api = new MinimalDatastoreApi((Datastore) null)

        expect:
        api.getDatastore() == null
    }
}

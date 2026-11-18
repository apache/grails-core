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

import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.core.connections.ConnectionSources
import org.grails.datastore.mapping.core.connections.ConnectionSourcesProvider
import org.grails.datastore.mapping.simple.SimpleMapDatastore
import spock.lang.AutoCleanup
import spock.lang.Specification

/**
 * Brand-new file in this PR, already mostly covered incidentally (any {@code SimpleMapDatastore}
 * used elsewhere implements {@link ConnectionSourcesProvider}) - the only gap is the fallback for a
 * plain, non-{@code ConnectionSourcesProvider} datastore, which no other spec happens to exercise.
 */
class ConnectionSourceNameResolverSpec extends Specification {

    @AutoCleanup
    SimpleMapDatastore datastore = new SimpleMapDatastore()

    void "resolveConnectionSourceNames defaults to [DEFAULT] for a non-ConnectionSourcesProvider datastore"() {
        expect:
        ConnectionSourceNameResolver.resolveConnectionSourceNames(new Object()) == [ConnectionSource.DEFAULT]
    }

    void "resolveDefaultConnectionSourceName defaults to DEFAULT for a non-ConnectionSourcesProvider datastore"() {
        expect:
        ConnectionSourceNameResolver.resolveDefaultConnectionSourceName(new Object()) == ConnectionSource.DEFAULT
    }

    void "resolveConnectionSourceNames resolves real connection source names from a ConnectionSourcesProvider"() {
        expect:
        datastore instanceof ConnectionSourcesProvider
        ConnectionSourceNameResolver.resolveConnectionSourceNames(datastore) == [ConnectionSource.DEFAULT]
        ConnectionSourceNameResolver.resolveDefaultConnectionSourceName(datastore) == ConnectionSource.DEFAULT
    }

    void "isConnectionSourceName recognises a declared connection source"() {
        expect:
        ConnectionSourceNameResolver.isConnectionSourceName(datastore, ConnectionSource.DEFAULT)
    }

    void "isConnectionSourceName answers false for a tenant id without throwing"() {
        expect: "an unknown name is a plain false - no ConfigurationException, so no stack trace is built per operation"
        !ConnectionSourceNameResolver.isConnectionSourceName(datastore, 'someTenantId')
    }

    void "isConnectionSourceName answers false for a null name or a datastore with no connection sources"() {
        given:
        def noConnectionSources = Stub(ConnectionSourcesProvider) {
            getConnectionSources() >> null
        }

        expect:
        !ConnectionSourceNameResolver.isConnectionSourceName(datastore, null)
        !ConnectionSourceNameResolver.isConnectionSourceName(new Object(), 'anything')
        !ConnectionSourceNameResolver.isConnectionSourceName(noConnectionSources, 'anything')
    }

    void "isConnectionSourceName recognises a datastore's own default connection"() {
        given: "a child datastore built for a single connection, which enumerates only that connection"
        def leaf = Stub(ConnectionSourcesProvider) {
            getConnectionSources() >> Stub(ConnectionSources) {
                getDefaultConnectionSource() >> Stub(ConnectionSource) { getName() >> 'secondary' }
            }
        }

        expect: "resolving its own connection name is recognised, so it is not mistaken for a tenant id"
        ConnectionSourceNameResolver.isConnectionSourceName(leaf, 'secondary')
        !ConnectionSourceNameResolver.isConnectionSourceName(leaf, 'tenant1')
    }

    void "isConnectionSourceName recognises every declared connection, not just the default"() {
        given:
        def provider = Stub(ConnectionSourcesProvider) {
            getConnectionSources() >> Stub(ConnectionSources) {
                getAllConnectionSources() >> [
                        Stub(ConnectionSource) { getName() >> ConnectionSource.DEFAULT },
                        Stub(ConnectionSource) { getName() >> 'analytics' }
                ]
            }
        }

        expect:
        ConnectionSourceNameResolver.isConnectionSourceName(provider, 'analytics')
        !ConnectionSourceNameResolver.isConnectionSourceName(provider, 'reporting')
    }
}

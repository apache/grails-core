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
}

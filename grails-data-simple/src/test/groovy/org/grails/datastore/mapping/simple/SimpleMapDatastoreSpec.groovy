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
package org.grails.datastore.mapping.simple

import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.core.connections.SingleSessionCapableDatastore
import org.grails.datastore.mapping.core.exceptions.ConfigurationException
import spock.lang.AutoCleanup
import spock.lang.Specification

/**
 * First spec in this module (grails-data-simple previously had no {@code src/test} at all - added
 * {@code testImplementation 'org.spockframework:spock-core'} to this module's own build.gradle to
 * enable it). The diff here reworked {@code getDatastoreForConnection} to be idempotent for a leaf
 * (single-connection, no-children) datastore resolving its own connection name back to itself
 * (needed so an API already bound to a tenant's own datastore can still be wrapped in
 * {@code Tenants.withId(tenantId)} without failing), and marked this datastore as a
 * {@link SingleSessionCapableDatastore}.
 */
class SimpleMapDatastoreSpec extends Specification {

    @AutoCleanup
    SimpleMapDatastore multiDatastore = new SimpleMapDatastore(['secondary'])

    void "getDatastoreForConnection returns the registered child datastore when one exists"() {
        expect:
        multiDatastore.getDatastoreForConnection('secondary') != null
        multiDatastore.getDatastoreForConnection('secondary') instanceof SimpleMapDatastore
    }

    void "getDatastoreForConnection on a leaf (single-connection) datastore resolves its own connection name back to itself"() {
        given: "a leaf datastore with no children of its own - the secondary child from the multi-datastore above"
        Datastore leaf = multiDatastore.getDatastoreForConnection('secondary')

        expect: "resolving the leaf's own connection name is idempotent, not an error"
        leaf.getDatastoreForConnection('secondary').is(leaf)
    }

    void "getDatastoreForConnection throws ConfigurationException for a genuinely unknown connection name"() {
        when:
        multiDatastore.getDatastoreForConnection('nonexistent')

        then:
        thrown(ConfigurationException)
    }

    void "is marked as a single-session datastore for unqualified-connection routing"() {
        expect:
        multiDatastore instanceof SingleSessionCapableDatastore
    }
}

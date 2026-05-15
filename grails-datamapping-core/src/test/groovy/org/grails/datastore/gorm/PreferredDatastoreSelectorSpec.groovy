/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  'License'); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  'AS IS' BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.grails.datastore.gorm

import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.core.connections.MultipleConnectionSourceCapableDatastore
import spock.lang.Specification

class PreferredDatastoreSelectorSpec extends Specification {

    private final GormEnhancerRegistry stateRegistry = GormEnhancerRegistry.instance

    void setup() {
        GormRegistry.reset()
        stateRegistry.clearPreferredDatastore()
        stateRegistry.clearResolvingDatastoreDepth()
    }

    void cleanup() {
        stateRegistry.clearPreferredDatastore()
        stateRegistry.clearResolvingDatastoreDepth()
        GormRegistry.reset()
    }

    void 'select returns preferred datastore for default qualifier'() {
        given:
        def selector = new PreferredDatastoreSelector()
        GormRegistry registry = GormRegistry.instance
        Datastore preferredDatastore = Mock(Datastore)
        stateRegistry.setPreferredDatastore(preferredDatastore)

        expect:
        selector.select(registry, stateRegistry, null, ConnectionSource.DEFAULT, null, 0, null).is(preferredDatastore)
    }

    void 'select returns qualifier datastore from preferred multi-connection datastore'() {
        given:
        def selector = new PreferredDatastoreSelector()
        GormRegistry registry = GormRegistry.instance
        Datastore qualifierDatastore = Mock(Datastore)
        MultipleConnectionSourceCapableDatastore preferredDatastore = Mock(MultipleConnectionSourceCapableDatastore) {
            getDatastoreForConnection('secondary') >> qualifierDatastore
        }
        stateRegistry.setPreferredDatastore(preferredDatastore)

        expect:
        selector.select(registry, stateRegistry, null, 'secondary', null, 0, null).is(qualifierDatastore)
    }

    void 'select returns null when no preferred datastore is configured'() {
        given:
        def selector = new PreferredDatastoreSelector()
        GormRegistry registry = GormRegistry.instance

        expect:
        selector.select(registry, stateRegistry, null, null, null, 0, null) == null
    }
}

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
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.core.connections.MultipleConnectionSourceCapableDatastore
import org.grails.datastore.mapping.multitenancy.MultiTenantCapableDatastore
import org.springframework.transaction.support.TransactionSynchronizationManager
import spock.lang.Specification

class QualifiedDatastoreSelectorSpec extends Specification {

    private final GormEnhancerRegistry stateRegistry = GormEnhancerRegistry.instance

    void setup() {
        GormRegistry.reset()
        stateRegistry.clearPreferredDatastore()
        stateRegistry.clearResolvingDatastoreDepth()
    }

    void cleanup() {
        if (TransactionSynchronizationManager.hasResource('secondary')) {
            TransactionSynchronizationManager.unbindResource('secondary')
        }
        stateRegistry.clearPreferredDatastore()
        stateRegistry.clearResolvingDatastoreDepth()
        GormRegistry.reset()
    }

    void 'select returns transaction-bound datastore for qualifier'() {
        given:
        def selector = new QualifiedDatastoreSelector()
        GormRegistry registry = GormRegistry.instance
        Datastore boundDatastore = Mock(Datastore)
        TransactionSynchronizationManager.bindResource('secondary', boundDatastore)

        expect:
        selector.select(registry, stateRegistry, null, 'secondary', 0).is(boundDatastore)
    }

    void 'select returns registry datastore for qualifier'() {
        given:
        def selector = new QualifiedDatastoreSelector()
        GormRegistry registry = GormRegistry.instance
        Datastore secondaryDatastore = Mock(Datastore)
        registry.registerDatastore('secondary', secondaryDatastore)

        expect:
        selector.select(registry, stateRegistry, null, 'secondary', 0).is(secondaryDatastore)
    }

    void 'select returns datastore from default multiple-connection datastore'() {
        given:
        def selector = new QualifiedDatastoreSelector()
        GormRegistry registry = GormRegistry.instance
        Datastore secondaryDatastore = Mock(Datastore)
        MultipleConnectionSourceCapableDatastore defaultDatastore = Mock(MultipleConnectionSourceCapableDatastore) {
            getDatastoreForConnection('secondary') >> secondaryDatastore
        }
        registry.registerDatastore(ConnectionSource.DEFAULT, defaultDatastore)

        expect:
        selector.select(registry, stateRegistry, null, 'secondary', 0).is(secondaryDatastore)
    }

    void 'select returns datastore from default multi-tenant datastore'() {
        given:
        def selector = new QualifiedDatastoreSelector()
        GormRegistry registry = GormRegistry.instance
        Datastore secondaryDatastore = Mock(Datastore)
        MultiTenantCapableDatastore defaultDatastore = Mock(MultiTenantCapableDatastore) {
            getDatastoreForTenantId('secondary') >> secondaryDatastore
        }
        registry.registerDatastore(ConnectionSource.DEFAULT, defaultDatastore)

        expect:
        selector.select(registry, stateRegistry, null, 'secondary', 0).is(secondaryDatastore)
    }
}

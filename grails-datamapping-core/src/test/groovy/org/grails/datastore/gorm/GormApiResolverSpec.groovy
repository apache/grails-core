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
import org.springframework.transaction.support.TransactionSynchronizationManager
import spock.lang.Specification

class GormApiResolverSpec extends Specification {
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

    void 'resolver finds datastore by registered type'() {
        given:
        GormRegistry registry = GormRegistry.instance
        GormApiResolver resolver = registry.apiResolver
        Datastore datastore = Mock(Datastore)
        registry.registerDatastoreByType(datastore)

        expect:
        resolver.findDatastoreByType(datastore.getClass()).is(datastore)
    }

    void 'resolver finds the default datastore for a single configured datastore'() {
        given:
        GormRegistry registry = GormRegistry.instance
        GormApiResolver resolver = registry.apiResolver
        Datastore datastore = Mock(Datastore)
        registry.registerDatastore(ConnectionSource.DEFAULT, datastore)

        expect:
        resolver.findSingleDatastore().is(datastore)
    }

    void 'resolver resolves datastores by qualifier'() {
        given:
        GormRegistry registry = GormRegistry.instance
        GormApiResolver resolver = registry.apiResolver
        Datastore defaultDatastore = Mock(Datastore)
        Datastore secondaryDatastore = Mock(Datastore)
        registry.registerDatastore(ConnectionSource.DEFAULT, defaultDatastore)
        registry.registerDatastore('secondary', secondaryDatastore)

        expect:
        resolver.findDatastore(null, 'secondary').is(secondaryDatastore)
    }

    void 'resolver returns transaction-bound datastore for explicit qualifier'() {
        given:
        GormRegistry registry = GormRegistry.instance
        GormApiResolver resolver = registry.apiResolver
        Datastore boundDatastore = Mock(Datastore)
        TransactionSynchronizationManager.bindResource('secondary', boundDatastore)

        expect:
        resolver.findDatastore(null, 'secondary').is(boundDatastore)
    }

    void 'resolver honors preferred datastore for default qualifier'() {
        given:
        GormRegistry registry = GormRegistry.instance
        GormApiResolver resolver = registry.apiResolver
        Datastore preferredDatastore = Mock(Datastore) {
            getMappingContext() >> Mock(org.grails.datastore.mapping.model.MappingContext) {
                getPersistentEntity(TestEntity.name) >> Mock(org.grails.datastore.mapping.model.PersistentEntity)
            }
        }
        stateRegistry.setPreferredDatastore(preferredDatastore)

        expect:
        resolver.findDatastore(TestEntity, ConnectionSource.DEFAULT).is(preferredDatastore)
    }

    void 'resolver falls through preferred path to explicit qualifier resolution'() {
        given:
        GormRegistry registry = GormRegistry.instance
        GormApiResolver resolver = registry.apiResolver
        Datastore preferredDatastore = Mock(Datastore)
        Datastore secondaryDatastore = Mock(Datastore)
        stateRegistry.setPreferredDatastore(preferredDatastore)
        registry.registerDatastore('secondary', secondaryDatastore)

        expect:
        resolver.findDatastore(null, 'secondary').is(secondaryDatastore)
    }

    void 'resolver returns default datastore when recursion depth guard is exceeded'() {
        given:
        GormRegistry registry = GormRegistry.instance
        GormApiResolver resolver = registry.apiResolver
        Datastore defaultDatastore = Mock(Datastore)
        registry.registerDatastore(ConnectionSource.DEFAULT, defaultDatastore)
        stateRegistry.setResolvingDatastoreDepth(6)

        expect:
        resolver.findDatastore(TestEntity, null).is(defaultDatastore)
    }

    void 'resolver can resolve an active session datastore without a qualifier registration'() {
        given:
        GormRegistry registry = GormRegistry.instance
        GormApiResolver resolver = registry.apiResolver
        Datastore activeDatastore = Mock(Datastore) {
            hasCurrentSession() >> true
        }
        registry.registerDatastoreByType(activeDatastore)

        expect:
        resolver.findDatastore(null, null).is(activeDatastore)
    }

    void 'resolver fails when datastore type is missing'() {
        given:
        GormApiResolver resolver = GormRegistry.instance.apiResolver

        when:
        resolver.findDatastoreByType(Datastore)

        then:
        IllegalStateException e = thrown()
        e.message.contains('No GORM implementation configured for type')
    }

    private static class TestEntity {
    }
}

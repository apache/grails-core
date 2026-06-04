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
import org.grails.datastore.mapping.transactions.TransactionCapableDatastore
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.support.DefaultTransactionDefinition
import spock.lang.Specification

class GormStaticApiSpec extends Specification {

    void setup() {
        GormRegistry.reset()
    }

    void cleanup() {
        GormRegistry.reset()
    }

    void 'test createTransactionDefinition with basic properties'() {
        given:
        def registry = GormRegistry.instance
        def resolver = Stub(DatastoreResolver)
        def api = new GormStaticApi(TestEntity, null, [], resolver, ConnectionSource.DEFAULT, registry)

        when:
        def definition = api.createTransactionDefinition([
            readOnly: true,
            timeout: 30,
            name: "myTransaction"
        ])

        then:
        definition.isReadOnly()
        definition.getTimeout() == 30
        definition.getName() == "myTransaction"
    }

    void 'test createTransactionDefinition coerces CharSequence values'() {
        given:
        def registry = GormRegistry.instance
        def resolver = Stub(DatastoreResolver)
        def api = new GormStaticApi(TestEntity, null, [], resolver, ConnectionSource.DEFAULT, registry)
        def customName = "${'my'}Transaction" // GString (CharSequence)

        when:
        def definition = api.createTransactionDefinition([
            name: customName
        ])

        then:
        definition.getName() == "myTransaction"
        definition.getName() instanceof String
    }

    void 'test createTransactionDefinition throws exception for invalid property'() {
        given:
        def registry = GormRegistry.instance
        def resolver = Stub(DatastoreResolver)
        def api = new GormStaticApi(TestEntity, null, [], resolver, ConnectionSource.DEFAULT, registry)

        when:
        api.createTransactionDefinition([
            invalidProp: "value"
        ])

        then:
        thrown(IllegalArgumentException)
    }

    void 'test withTransaction executes callback with transaction definition'() {
        given:
        def registry = GormRegistry.instance
        def txManager = Mock(PlatformTransactionManager)
        def txStatus = Mock(TransactionStatus)
        def ds = Mock(TransactionCapableDatastore) {
            getTransactionManager() >> txManager
        }
        def resolver = Stub(DatastoreResolver) {
            resolve() >> ds
        }
        registry.registerDatastore(ConnectionSource.DEFAULT, ds)
        def api = new GormStaticApi(TestEntity, null, [], resolver, ConnectionSource.DEFAULT, registry)

        when:
        def result = api.withTransaction([readOnly: true]) { status ->
            assert status == txStatus
            return "success"
        }

        then:
        1 * txManager.getTransaction(_ as TransactionDefinition) >> { TransactionDefinition definition ->
            assert definition.isReadOnly()
            return txStatus
        }
        1 * txManager.commit(txStatus)
        result == "success"
    }

    void 'test withNewTransaction executes callback with propagation requires new'() {
        given:
        def registry = GormRegistry.instance
        def txManager = Mock(PlatformTransactionManager)
        def txStatus = Mock(TransactionStatus)
        def ds = Mock(TransactionCapableDatastore) {
            getTransactionManager() >> txManager
        }
        def resolver = Stub(DatastoreResolver) {
            resolve() >> ds
        }
        registry.registerDatastore(ConnectionSource.DEFAULT, ds)
        def api = new GormStaticApi(TestEntity, null, [], resolver, ConnectionSource.DEFAULT, registry)

        when:
        def result = api.withNewTransaction([readOnly: true]) { status ->
            assert status == txStatus
            return "success"
        }

        then:
        1 * txManager.getTransaction(_ as TransactionDefinition) >> { TransactionDefinition definition ->
            assert definition.isReadOnly()
            assert definition.getPropagationBehavior() == TransactionDefinition.PROPAGATION_REQUIRES_NEW
            return txStatus
        }
        1 * txManager.commit(txStatus)
        result == "success"
    }

    static class TestEntity {}
}

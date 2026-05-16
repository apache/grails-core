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
import org.grails.datastore.mapping.transactions.TransactionCapableDatastore
import org.springframework.transaction.PlatformTransactionManager
import spock.lang.Specification

class GormRegistrySpec extends Specification {

    void setup() {
        GormRegistry.reset()
    }

    void cleanup() {
        GormRegistry.reset()
    }

    void "reset clears all registries"() {
        given:
        def registry = GormRegistry.instance
        def datastore = Stub(Datastore)
        registry.registerDatastore(ConnectionSource.DEFAULT, datastore)
        registry.registerDatastoreByType(datastore)
        
        when:
        GormRegistry.reset()

        then:
        registry.datastoresByQualifier.isEmpty()
        registry.datastoresByType.isEmpty()
        registry.allDatastores.isEmpty()
        registry.staticApiRegistry.size() == 0
        registry.instanceApiRegistry.size() == 0
        registry.validationApiRegistry.size() == 0
    }

    void "findSingleTransactionManager returns null for non-transactional datastore"() {
        given:
        def registry = GormRegistry.instance
        def datastore = Stub(Datastore)
        registry.registerDatastore(ConnectionSource.DEFAULT, datastore)

        when:
        def tm = registry.findSingleTransactionManager()

        then:
        tm == null
    }

    void "findSingleTransactionManager returns transaction manager for TransactionCapableDatastore"() {
        given:
        def registry = GormRegistry.instance
        def txManager = Stub(PlatformTransactionManager)
        def datastore = Stub(TransactionCapableDatastore) {
            getTransactionManager() >> txManager
        }
        registry.registerDatastore(ConnectionSource.DEFAULT, datastore)

        when:
        def tm = registry.findSingleTransactionManager()

        then:
        tm == txManager
    }

    void "findSingleTransactionManager with connectionName returns transaction manager"() {
        given:
        def registry = GormRegistry.instance
        def txManager = Stub(PlatformTransactionManager)
        def datastore = Stub(TransactionCapableDatastore) {
            getTransactionManager() >> txManager
        }
        registry.registerDatastore("custom", datastore)

        when:
        def tm = registry.findSingleTransactionManager("custom")

        then:
        tm == txManager
    }

    void "findTransactionManager returns transaction manager for entity"() {
        given:
        def registry = GormRegistry.instance
        def txManager = Stub(PlatformTransactionManager)
        def datastore = Stub(TransactionCapableDatastore) {
            getTransactionManager() >> txManager
        }
        registry.registerEntityDatastore(TestEntity.name, ConnectionSource.DEFAULT, datastore)

        when:
        def tm = registry.findTransactionManager(TestEntity, null)

        then:
        tm == txManager
    }

    void "removeDatastoreByType removes from type registry but keeps in allDatastores"() {
        given:
        def registry = GormRegistry.instance
        def datastore = Stub(Datastore)
        registry.registerDatastoreByType(datastore)

        when:
        registry.removeDatastoreByType(datastore)

        then:
        !registry.datastoresByType.containsKey(datastore.getClass())
    }

    void "removeDatastoreFromDiscovery removes from type registry and allDatastores"() {
        given:
        def registry = GormRegistry.instance
        def datastore = Stub(Datastore)
        registry.registerDatastoreByType(datastore)

        when:
        registry.removeDatastoreFromDiscovery(datastore)

        then:
        !registry.datastoresByType.containsKey(datastore.getClass())
        !registry.allDatastores.contains(datastore)
    }

    void "removeDatastore removes from all registries"() {
        given:
        def registry = GormRegistry.instance
        def datastore = Stub(Datastore)
        registry.registerDatastore(ConnectionSource.DEFAULT, datastore)
        registry.registerDatastoreByType(datastore)
        registry.registerEntityDatastore(TestEntity.name, ConnectionSource.DEFAULT, datastore)

        when:
        registry.removeDatastore(datastore)

        then:
        !registry.datastoresByQualifier.containsValue(datastore)
        !registry.datastoresByType.containsValue(datastore)
        !registry.allDatastores.contains(datastore)
        registry.getDatastore(TestEntity.name, ConnectionSource.DEFAULT) == null
    }

    void "removeEntityDatastore removes datastore specifically for entity"() {
        given:
        def registry = GormRegistry.instance
        def datastore = Stub(Datastore)
        registry.registerEntityDatastore(TestEntity.name, ConnectionSource.DEFAULT, datastore)

        when:
        registry.removeEntityDatastore(TestEntity.name, datastore)

        then:
        registry.getDatastore(TestEntity.name, ConnectionSource.DEFAULT) == null
    }

    void "normalizeEntityKey properly normalizes class names"() {
        given:
        def registry = GormRegistry.instance

        expect:
        registry.normalizeEntityKey(" com.example.MyEntity ") == "com.example.MyEntity"
        registry.normalizeEntityKey(null) == null
        registry.normalizeEntityKey("   ") == null
        registry.normalizeEntityKeyFromClass(TestEntity) == TestEntity.name
    }

    void "normalizeQualifier properly normalizes qualifiers"() {
        given:
        def registry = GormRegistry.instance

        expect:
        registry.normalizeQualifier(null) == ConnectionSource.DEFAULT
        registry.normalizeQualifier("   ") == ConnectionSource.DEFAULT
        registry.normalizeQualifier(ConnectionSource.OLD_DEFAULT) == ConnectionSource.DEFAULT
        registry.normalizeQualifier(" myCustom  ") == "myCustom"
    }

    void "getDatastore falls back to available datastore if DEFAULT is requested but missing"() {
        given:
        def registry = GormRegistry.instance
        def datastore = Stub(Datastore)
        registry.registerEntityDatastore(TestEntity.name, "reporting", datastore)

        when:
        def resolved = registry.getDatastore(TestEntity.name, ConnectionSource.DEFAULT)

        then:
        resolved == datastore
    }

    void "getApiFactory falls back to parent type or default if specific type is not registered"() {
        given:
        def registry = GormRegistry.instance
        def customFactory = Stub(GormApiFactory)
        registry.registerApiFactory(Datastore, customFactory)
        
        def mockDatastore = Stub(TransactionCapableDatastore)

        when:
        def factory = registry.getApiFactory(mockDatastore)

        then:
        factory == customFactory
    }

    void "registerDatastoreByQualifier only registers by qualifier"() {
        given:
        def registry = GormRegistry.instance
        def datastore = Stub(Datastore)

        when:
        registry.registerDatastoreByQualifier("custom", datastore)

        then:
        registry.datastoresByQualifier.get("custom") == datastore
        !registry.allDatastores.contains(datastore)
    }

    void "initializeDatastore registers constraints, type and default qualifier"() {
        given:
        def registry = GormRegistry.instance
        def datastore = Stub(Datastore)

        when:
        registry.initializeDatastore(datastore, ConnectionSource.DEFAULT)

        then:
        registry.allDatastores.contains(datastore)
        registry.datastoresByType.get(datastore.getClass()) == datastore
        registry.datastoresByQualifier.get(ConnectionSource.DEFAULT) == datastore
    }

    static class TestEntity {
    }
}

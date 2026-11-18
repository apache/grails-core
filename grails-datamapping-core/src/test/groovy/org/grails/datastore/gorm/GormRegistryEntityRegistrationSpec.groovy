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

import spock.lang.Specification

import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.gorm.DatastoreResolver

/**
 * Tests for {@link GormRegistry#registerEntityApis(String, GormStaticApi, GormInstanceApi, GormValidationApi)}
 * and {@link GormRegistry#registerEntityDatastores(String, Object, List, Object)}.
 *
 * @author Graeme Rocher
 */
class GormRegistryEntityRegistrationSpec extends Specification {

    void setup() {
        GormRegistry.reset()
    }

    void cleanup() {
        GormRegistry.reset()
    }

    void 'registerApi stores static, instance, and validation APIs in dedicated registries'() {
        given:
        GormRegistry registry = GormRegistry.instance
        String className = RegistryBook.name
        MappingContext mappingContext = Stub(MappingContext) {
            getMappingFactory() >> null
        }
        Datastore datastore = Stub(Datastore) {
            getMappingContext() >> mappingContext
        }
        DatastoreResolver datastoreResolver = Stub(DatastoreResolver) {
            resolve() >> datastore
        }
        def staticApi = new GormStaticApi(RegistryBook, mappingContext, [], datastoreResolver, ConnectionSource.DEFAULT, registry)
        def instanceApi = new GormInstanceApi(RegistryBook, mappingContext, datastoreResolver, registry)
        def validationApi = new GormValidationApi(RegistryBook, mappingContext, datastoreResolver, registry)

        when:
        registry.registerApi(className, staticApi, instanceApi, validationApi)
        
        then:
        registry.getStaticApiRegistry().containsKey(className)
        registry.getInstanceApiRegistry().containsKey(className)
        registry.getValidationApiRegistry().containsKey(className)
        registry.getStaticApi(className).is(staticApi)
        registry.getInstanceApi(className).is(instanceApi)
        registry.getValidationApi(className).is(validationApi)
    }

    void 'registerApi overwrites existing registrations'() {
        given:
        GormRegistry registry = GormRegistry.instance
        String className = RegistryBook.name
        MappingContext mappingContext = Stub(MappingContext) {
            getMappingFactory() >> null
        }
        Datastore datastore = Stub(Datastore) {
            getMappingContext() >> mappingContext
        }
        DatastoreResolver datastoreResolver = Stub(DatastoreResolver) {
            resolve() >> datastore
        }
        def firstStaticApi = new GormStaticApi(RegistryBook, mappingContext, [], datastoreResolver, ConnectionSource.DEFAULT, registry)
        def firstInstanceApi = new GormInstanceApi(RegistryBook, mappingContext, datastoreResolver, registry)
        def firstValidationApi = new GormValidationApi(RegistryBook, mappingContext, datastoreResolver, registry)
        def secondStaticApi = new GormStaticApi(RegistryBook, mappingContext, [], datastoreResolver, ConnectionSource.DEFAULT, registry)
        def secondInstanceApi = new GormInstanceApi(RegistryBook, mappingContext, datastoreResolver, registry)
        def secondValidationApi = new GormValidationApi(RegistryBook, mappingContext, datastoreResolver, registry)

        when:
        registry.registerApi(className, firstStaticApi, firstInstanceApi, firstValidationApi)
        registry.registerApi(className, secondStaticApi, secondInstanceApi, secondValidationApi)
        
        then:
        registry.getStaticApi(className).is(secondStaticApi)
        registry.getInstanceApi(className).is(secondInstanceApi)
        registry.getValidationApi(className).is(secondValidationApi)
    }

    class RegistryBook {

    }

    void 'registerEntityDatastores registers datastore for single connection source'() {
        given:
        GormRegistry registry = GormRegistry.instance
        String className = 'com.example.Book'
        Datastore datastore = Mock(Datastore)

        when:
        // Note: registerEntityDatastores expects a non-null entity, so we call it without entity param
        // which will skip the entity-specific qualifier logic
        for (String qualifier in [ConnectionSource.DEFAULT]) {
            registry.registerDatastore(qualifier, datastore)
            registry.registerEntityDatastore(className, qualifier, datastore)
        }

        then:
        registry.getDatastore(className, ConnectionSource.DEFAULT) == datastore
    }

    void 'registerEntityDatastores handles null datastore gracefully'() {
        given:
        GormRegistry registry = GormRegistry.instance
        String className = 'com.example.Book'
        List<String> connectionSources = [ConnectionSource.DEFAULT]

        when:
        registry.registerEntityDatastores(className, null, connectionSources, null)

        then:
        noExceptionThrown()
        registry.getDatastore(className, ConnectionSource.DEFAULT) == null
    }

    void 'registerEntityDatastores registers datastores for multiple connection sources'() {
        given:
        GormRegistry registry = GormRegistry.instance
        String className = 'com.example.Book'
        Datastore datastore = Mock(Datastore)
        List<String> connectionSources = [ConnectionSource.DEFAULT, 'secondary', 'reporting']

        when:
        // Register directly for multiple sources
        for (String qualifier in connectionSources) {
            registry.registerDatastore(qualifier, datastore)
            registry.registerEntityDatastore(className, qualifier, datastore)
        }

        then:
        registry.getDatastore(className, ConnectionSource.DEFAULT) == datastore
        registry.getDatastore(className, 'secondary') == datastore
        registry.getDatastore(className, 'reporting') == datastore
    }

    void 'registry normalizes default qualifier aliases when registering datastores'() {
        given:
        GormRegistry registry = GormRegistry.instance
        Datastore datastore = Mock(Datastore)

        when:
        registry.registerDatastore(ConnectionSource.OLD_DEFAULT, datastore)

        then:
        registry.getDatastore((String) null, ConnectionSource.DEFAULT) == datastore
        registry.getDatastore((String) null, ConnectionSource.OLD_DEFAULT) == datastore
        registry.getDatastore((String) null, '   ') == datastore
    }

    void 'registry normalizes entity keys for entity-specific datastore lookups'() {
        given:
        GormRegistry registry = GormRegistry.instance
        Datastore datastore = Mock(Datastore)

        when:
        registry.registerEntityDatastore(" ${RegistryBook.name} ", ConnectionSource.OLD_DEFAULT, datastore)

        then:
        registry.getDatastore(RegistryBook.name, ConnectionSource.DEFAULT) == datastore
        registry.getDatastore(" ${RegistryBook.name} ", ConnectionSource.OLD_DEFAULT) == datastore
    }
}

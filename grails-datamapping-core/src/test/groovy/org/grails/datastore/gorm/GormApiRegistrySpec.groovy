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
import org.grails.datastore.mapping.model.MappingContext
import spock.lang.Specification

class GormApiRegistrySpec extends Specification {

    void setup() {
        GormRegistry.reset()
    }

    void cleanup() {
        GormRegistry.reset()
    }

    void 'static api registry stores and resolves APIs by qualifier'() {
        given:
        def registry = GormRegistry.instance
        def apiRegistry = registry.staticApiRegistry
        def (mappingContext, datastore, datastoreResolver) = createContext()
        def secondaryDatastore = Stub(Datastore)
        def api = new GormStaticApi(ApiRegistryEntity, mappingContext, [], datastoreResolver, ConnectionSource.DEFAULT, registry)

        when:
        apiRegistry.register(ApiRegistryEntity.name, api)
        registry.registerDatastore(ConnectionSource.DEFAULT, datastore)
        registry.registerDatastore('secondary', secondaryDatastore)
        registry.registerEntityDatastore(ApiRegistryEntity.name, ConnectionSource.DEFAULT, datastore)
        registry.registerEntityDatastore(ApiRegistryEntity.name, 'secondary', secondaryDatastore)

        then:
        apiRegistry.size() == 1
        apiRegistry.containsKey(ApiRegistryEntity.name)
        apiRegistry.get(ApiRegistryEntity.name).is(api)
        apiRegistry.get(ApiRegistryEntity.name, ConnectionSource.DEFAULT).is(api)
        apiRegistry.get(ApiRegistryEntity.name, 'secondary') instanceof GormStaticApi
        !apiRegistry.get(ApiRegistryEntity.name, 'secondary').is(api)

        when:
        apiRegistry.clear()

        then:
        apiRegistry.size() == 0
    }

    void 'instance api registry stores and resolves APIs by qualifier'() {
        given:
        def registry = GormRegistry.instance
        def apiRegistry = registry.instanceApiRegistry
        def (mappingContext, datastore, datastoreResolver) = createContext()
        def secondaryDatastore = Stub(Datastore)
        def api = new GormInstanceApi(ApiRegistryEntity, mappingContext, datastoreResolver, registry)

        when:
        apiRegistry.register(ApiRegistryEntity.name, api)
        registry.registerDatastore(ConnectionSource.DEFAULT, datastore)
        registry.registerDatastore('secondary', secondaryDatastore)
        registry.registerEntityDatastore(ApiRegistryEntity.name, ConnectionSource.DEFAULT, datastore)
        registry.registerEntityDatastore(ApiRegistryEntity.name, 'secondary', secondaryDatastore)

        then:
        apiRegistry.size() == 1
        apiRegistry.get(ApiRegistryEntity.name).is(api)
        apiRegistry.get(ApiRegistryEntity.name, ConnectionSource.DEFAULT).is(api)
        apiRegistry.get(ApiRegistryEntity.name, 'secondary') instanceof GormInstanceApi
    }

    void 'validation api registry stores and resolves APIs by qualifier'() {
        given:
        def registry = GormRegistry.instance
        def apiRegistry = registry.validationApiRegistry
        def (mappingContext, datastore, datastoreResolver) = createContext()
        def secondaryDatastore = Stub(Datastore)
        def api = new GormValidationApi(ApiRegistryEntity, mappingContext, datastoreResolver, registry)

        when:
        apiRegistry.register(ApiRegistryEntity.name, api)
        registry.registerDatastore(ConnectionSource.DEFAULT, datastore)
        registry.registerDatastore('secondary', secondaryDatastore)
        registry.registerEntityDatastore(ApiRegistryEntity.name, ConnectionSource.DEFAULT, datastore)
        registry.registerEntityDatastore(ApiRegistryEntity.name, 'secondary', secondaryDatastore)

        then:
        apiRegistry.size() == 1
        apiRegistry.get(ApiRegistryEntity.name).is(api)
        apiRegistry.get(ApiRegistryEntity.name, ConnectionSource.DEFAULT).is(api)
        apiRegistry.get(ApiRegistryEntity.name, 'secondary') instanceof GormValidationApi
    }

    void 'api registries normalize entity key and default qualifier aliases'() {
        given:
        def registry = GormRegistry.instance
        def apiRegistry = registry.staticApiRegistry
        def (mappingContext, datastore, datastoreResolver) = createContext()
        def api = new GormStaticApi(ApiRegistryEntity, mappingContext, [], datastoreResolver, ConnectionSource.DEFAULT, registry)

        when:
        apiRegistry.register(" ${ApiRegistryEntity.name} ", api)
        registry.registerDatastore(ConnectionSource.OLD_DEFAULT, datastore)
        registry.registerEntityDatastore(" ${ApiRegistryEntity.name} ", ConnectionSource.OLD_DEFAULT, datastore)

        then:
        apiRegistry.containsKey(ApiRegistryEntity.name)
        apiRegistry.get(ApiRegistryEntity.name, ConnectionSource.OLD_DEFAULT).is(api)
    }

    private List createContext() {
        MappingContext mappingContext = Stub(MappingContext) {
            getMappingFactory() >> null
        }
        Datastore datastore = Stub(Datastore) {
            getMappingContext() >> mappingContext
        }
        DatastoreResolver datastoreResolver = Stub(DatastoreResolver) {
            resolve() >> datastore
        }
        [mappingContext, datastore, datastoreResolver]
    }

    static class ApiRegistryEntity {

    }
}

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
import org.grails.datastore.mapping.model.MappingContext
import spock.lang.Specification

class GormInstanceApiRegistrySpec extends Specification {

    void setup() {
        GormRegistry.reset()
    }

    void cleanup() {
        GormRegistry.reset()
    }

    void 'findInstanceApi resolves by qualifier'() {
        given:
        def registry = GormRegistry.instance
        def apiRegistry = registry.instanceApiRegistry
        def context = Stub(MappingContext) {
            getMappingFactory() >> null
        }
        def defaultDatastore = Stub(Datastore) {
            getMappingContext() >> context
        }
        def secondaryDatastore = Stub(Datastore) {
            getMappingContext() >> context
        }
        def resolver = Stub(DatastoreResolver) {
            resolve() >> defaultDatastore
        }
        def api = new GormInstanceApi(ApiRegistryEntity, context, resolver, registry)
        apiRegistry.register(ApiRegistryEntity.name, api)
        registry.registerDatastore(ConnectionSource.DEFAULT, defaultDatastore)
        registry.registerDatastore('secondary', secondaryDatastore)
        registry.registerEntityDatastore(ApiRegistryEntity.name, ConnectionSource.DEFAULT, defaultDatastore)
        registry.registerEntityDatastore(ApiRegistryEntity.name, 'secondary', secondaryDatastore)

        expect:
        apiRegistry.findInstanceApi(ApiRegistryEntity).is(api)
        apiRegistry.findInstanceApi(ApiRegistryEntity, 'secondary') instanceof GormInstanceApi
        !apiRegistry.findInstanceApi(ApiRegistryEntity, 'secondary').is(api)
    }

    static class ApiRegistryEntity {
    }
}

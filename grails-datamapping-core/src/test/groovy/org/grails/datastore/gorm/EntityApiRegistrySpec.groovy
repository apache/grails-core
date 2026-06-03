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
import spock.lang.Specification

class EntityApiRegistrySpec extends Specification {

    void "test register and resolve entity APIs"() {
        given:
        def registry = GormRegistry.instance
        registry.reset()

        def apiRegistry = registry.entityApiRegistry
        def datastore = Stub(Datastore)

        def staticApi = new GormStaticApi(DummyEntity, null, [], new DatastoreResolver() {
            @Override Datastore resolve() { return datastore }
        }, ConnectionSource.DEFAULT, registry)
        def instanceApi = new GormInstanceApi(DummyEntity, datastore, registry)
        def validationApi = new GormValidationApi(DummyEntity, datastore, registry)

        when:
        apiRegistry.registerEntityApis(DummyEntity, staticApi, instanceApi, validationApi)

        then:
        apiRegistry.getStaticApi(DummyEntity) == staticApi
        apiRegistry.getInstanceApi(DummyEntity) == instanceApi
        apiRegistry.getValidationApi(DummyEntity) == validationApi

        cleanup:
        registry.reset()
    }

    static class DummyEntity {
        Long id
    }
}

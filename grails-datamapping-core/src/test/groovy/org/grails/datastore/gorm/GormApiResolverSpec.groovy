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

class GormApiResolverSpec extends Specification {

    void setup() {
        GormRegistry.reset()
    }

    void cleanup() {
        GormRegistry.reset()
    }

    void 'resolver returns registered APIs for default qualifier'() {
        given:
        GormRegistry registry = GormRegistry.instance
        GormApiResolver resolver = registry.apiResolver
        MappingContext mappingContext = Stub(MappingContext) {
            getMappingFactory() >> null
        }
        Datastore datastore = Stub(Datastore) {
            getMappingContext() >> mappingContext
        }
        DatastoreResolver datastoreResolver = Stub(DatastoreResolver) {
            resolve() >> datastore
        }
        GormStaticApi staticApi = new GormStaticApi(TestResolverEntity, mappingContext, [], datastoreResolver, ConnectionSource.DEFAULT, registry)
        GormInstanceApi instanceApi = new GormInstanceApi(TestResolverEntity, mappingContext, datastoreResolver, registry)
        GormValidationApi validationApi = new GormValidationApi(TestResolverEntity, mappingContext, datastoreResolver, registry)
        registry.registerApi(TestResolverEntity.name, staticApi, instanceApi, validationApi)

        expect:
        resolver.findStaticApi(TestResolverEntity).is(staticApi)
        resolver.findInstanceApi(TestResolverEntity).is(instanceApi)
        resolver.findValidationApi(TestResolverEntity).is(validationApi)
    }

    void 'resolver delegates qualifier lookups to forQualifier'() {
        given:
        GormRegistry registry = GormRegistry.instance
        GormApiResolver resolver = registry.apiResolver
        MappingContext mappingContext = Stub(MappingContext) {
            getMappingFactory() >> null
        }
        Datastore datastore = Stub(Datastore) {
            getMappingContext() >> mappingContext
        }
        DatastoreResolver datastoreResolver = Stub(DatastoreResolver) {
            resolve() >> datastore
        }
        GormStaticApi staticApi = new GormStaticApi(TestResolverEntity, mappingContext, [], datastoreResolver, ConnectionSource.DEFAULT, registry)
        GormInstanceApi instanceApi = new GormInstanceApi(TestResolverEntity, mappingContext, datastoreResolver, registry)
        GormValidationApi validationApi = new GormValidationApi(TestResolverEntity, mappingContext, datastoreResolver, registry)
        registry.registerApi(TestResolverEntity.name, staticApi, instanceApi, validationApi)

        when:
        def resolvedStatic = resolver.findStaticApi(TestResolverEntity, 'tenantA')
        def resolvedInstance = resolver.findInstanceApi(TestResolverEntity, 'tenantA')
        def resolvedValidation = resolver.findValidationApi(TestResolverEntity, 'tenantA')

        then:
        resolvedStatic instanceof GormStaticApi
        resolvedInstance instanceof GormInstanceApi
        resolvedValidation instanceof GormValidationApi
        !resolvedStatic.is(staticApi)
        !resolvedInstance.is(instanceApi)
        !resolvedValidation.is(validationApi)
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

    void 'resolver throws helpful exception when API is not registered'() {
        given:
        GormApiResolver resolver = GormRegistry.instance.apiResolver

        when:
        resolver.findStaticApi(TestResolverEntity)

        then:
        IllegalStateException e = thrown(IllegalStateException)
        e.message.contains('No GORM implementation configured')
    }

    static class TestResolverEntity {
    }
}

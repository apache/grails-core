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
package org.grails.gorm.rx.api

import grails.gorm.rx.MultiTenant
import org.grails.datastore.mapping.config.Entity
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.core.connections.ConnectionSources
import org.grails.datastore.mapping.model.ClassMapping
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.multitenancy.MultiTenancySettings
import org.grails.datastore.mapping.multitenancy.TenantResolver
import org.grails.datastore.rx.internal.RxDatastoreClientImplementor
import spock.lang.Specification

class RxGormEnhancerSpec extends Specification {

    private static class TestEntity {
    }

    private static class TestSecondEntity {
    }

    private interface TestMultiTenantEntity extends MultiTenant {
    }

    void cleanup() {
        RxGormEnhancer.close()
    }

    void "findStaticApi returns the static api registered for the default tenant"() {
        given:
        Map registered = registerEntity(TestEntity)

        expect:
        RxGormEnhancer.findStaticApi(TestEntity).is(registered.staticApi)
    }

    void "findStaticApi throws an IllegalStateException when the type was never registered"() {
        when:
        RxGormEnhancer.findStaticApi(TestEntity)

        then:
        IllegalStateException e = thrown(IllegalStateException)
        e.message.contains(TestEntity.name)
    }

    void "findInstanceApi returns the instance api registered for the default tenant"() {
        given:
        Map registered = registerEntity(TestEntity)

        expect:
        RxGormEnhancer.findInstanceApi(TestEntity).is(registered.instanceApi)
    }

    void "findInstanceApi throws an IllegalStateException when the type was never registered"() {
        when:
        RxGormEnhancer.findInstanceApi(TestEntity)

        then:
        IllegalStateException e = thrown(IllegalStateException)
        e.message.contains(TestEntity.name)
    }

    void "findValidationApi returns the validation api registered for the default tenant"() {
        given:
        Map registered = registerEntity(TestEntity)

        expect:
        RxGormEnhancer.findValidationApi(TestEntity).is(registered.validationApi)
    }

    void "findValidationApi throws an IllegalStateException when the type was never registered"() {
        when:
        RxGormEnhancer.findValidationApi(TestEntity)

        then:
        IllegalStateException e = thrown(IllegalStateException)
        e.message.contains(TestEntity.name)
    }

    void "findDatastoreClientByType returns the client registered for the type"() {
        given:
        Map registered = registerEntity(TestEntity)

        expect:
        RxGormEnhancer.findDatastoreClientByType(registered.client.getClass()).is(registered.client)
    }

    void "findDatastoreClientByType throws an IllegalStateException when no client is registered for the type"() {
        when:
        RxGormEnhancer.findDatastoreClientByType(RxDatastoreClientImplementor)

        then:
        IllegalStateException e = thrown(IllegalStateException)
        e.message.contains('No RxGORM implementation configured')
    }

    void "findSingleDatastoreClient returns the only registered client"() {
        given:
        Map registered = registerEntity(TestEntity)

        expect:
        RxGormEnhancer.findSingleDatastoreClient().is(registered.client)
    }

    void "findSingleDatastoreClient throws an IllegalStateException when no client is registered"() {
        when:
        RxGormEnhancer.findSingleDatastoreClient()

        then:
        IllegalStateException e = thrown(IllegalStateException)
        e.message.contains('No RxGORM implementations configured')
    }

    void "findSingleDatastoreClient throws an IllegalStateException when more than one client is registered"() {
        given:
        registerEntity(TestEntity)
        registerEntity(TestSecondEntity, MultiTenancySettings.MultiTenancyMode.NONE, Stub(TenantResolver), [Serializable])

        when:
        RxGormEnhancer.findSingleDatastoreClient()

        then:
        IllegalStateException e = thrown(IllegalStateException)
        e.message.contains('More than one RxGORM implementation is configured')
    }

    void "findTenantId returns the default connection source for a non multi tenant class"() {
        given:
        registerEntity(TestEntity)

        expect:
        RxGormEnhancer.findTenantId(TestEntity) == ConnectionSource.DEFAULT
    }

    void "findTenantId resolves the current tenant id when the datastore is in DATABASE mode"() {
        given:
        TenantResolver resolver = Stub(TenantResolver) {
            resolveTenantIdentifier() >> 'resolvedTenant'
        }
        registerEntity(TestMultiTenantEntity, MultiTenancySettings.MultiTenancyMode.DATABASE, resolver)

        expect:
        RxGormEnhancer.findTenantId(TestMultiTenantEntity) == 'resolvedTenant'
    }

    void "findTenantId returns the default connection source when a multi tenant class is not in DATABASE mode"() {
        given:
        registerEntity(TestMultiTenantEntity, MultiTenancySettings.MultiTenancyMode.SCHEMA)

        expect:
        RxGormEnhancer.findTenantId(TestMultiTenantEntity) == ConnectionSource.DEFAULT
    }

    void "registerEntity does not replace an already registered client for the same client class"() {
        given: "a first entity registered against a client of a given class"
        Map firstRegistration = registerEntity(TestEntity)
        RxDatastoreClientImplementor firstClient = (RxDatastoreClientImplementor) firstRegistration.client

        and: "a second, different client instance of the very same class registering another entity"
        RxDatastoreClientImplementor secondClient = Mock(RxDatastoreClientImplementor, additionalInterfaces: []) {
            getConnectionSources() >> Stub(ConnectionSources) {
                getAllConnectionSources() >> [Stub(ConnectionSource) { getName() >> ConnectionSource.DEFAULT }]
            }
            getMultiTenancyMode() >> MultiTenancySettings.MultiTenancyMode.NONE
            getTenantResolver() >> Stub(TenantResolver)
            createStaticApi(_, _) >> Stub(RxGormStaticApi)
            createInstanceApi(_, _) >> Stub(RxGormInstanceApi)
            createValidationApi(_, _) >> Stub(RxGormValidationApi)
        }
        PersistentEntity secondEntity = Stub(PersistentEntity) {
            getMapping() >> Stub(ClassMapping) { getMappedForm() >> null }
            getJavaClass() >> TestSecondEntity
            getName() >> TestSecondEntity.name
        }

        when:
        RxGormEnhancer.registerEntity(secondEntity, secondClient)

        then: "the first client registered for that class wins, the second is not swapped in"
        RxGormEnhancer.findDatastoreClientByType(firstClient.getClass()).is(firstClient)
    }

    void "registerEntity registers each additional named connection source for a non multi tenant entity"() {
        given:
        RxGormStaticApi staticApi = Stub(RxGormStaticApi)
        RxDatastoreClientImplementor client = Mock(RxDatastoreClientImplementor) {
            createStaticApi(_, _) >> staticApi
            createInstanceApi(_, _) >> Stub(RxGormInstanceApi)
            createValidationApi(_, _) >> Stub(RxGormValidationApi)
        }
        PersistentEntity entity = Stub(PersistentEntity) {
            getMapping() >> Stub(ClassMapping) {
                getMappedForm() >> new Entity(datasources: [ConnectionSource.DEFAULT, 'secondary'])
            }
            getJavaClass() >> TestEntity
            getName() >> TestEntity.name
        }

        when:
        RxGormEnhancer.registerEntity(entity, client)

        then:
        RxGormEnhancer.findStaticApi(TestEntity, ConnectionSource.DEFAULT).is(staticApi)
        RxGormEnhancer.findStaticApi(TestEntity, 'secondary').is(staticApi)
    }

    private Map<String, Object> registerEntity(Class type,
            MultiTenancySettings.MultiTenancyMode mode = MultiTenancySettings.MultiTenancyMode.NONE,
            TenantResolver tenantResolver = null,
            List<Class> additionalInterfaces = []) {
        TenantResolver resolver = tenantResolver ?: Stub(TenantResolver)
        RxGormStaticApi staticApi = Stub(RxGormStaticApi)
        RxGormInstanceApi instanceApi = Stub(RxGormInstanceApi)
        RxGormValidationApi validationApi = Stub(RxGormValidationApi)
        RxDatastoreClientImplementor client = Mock(RxDatastoreClientImplementor, additionalInterfaces: additionalInterfaces) {
            getConnectionSources() >> Stub(ConnectionSources) {
                getAllConnectionSources() >> [Stub(ConnectionSource) { getName() >> ConnectionSource.DEFAULT }]
            }
            getMultiTenancyMode() >> mode
            getTenantResolver() >> resolver
            createStaticApi(_, _) >> staticApi
            createInstanceApi(_, _) >> instanceApi
            createValidationApi(_, _) >> validationApi
        }
        staticApi.getDatastoreClient() >> client
        PersistentEntity entity = Stub(PersistentEntity) {
            getMapping() >> Stub(ClassMapping) { getMappedForm() >> null }
            getJavaClass() >> type
            getName() >> type.name
        }
        RxGormEnhancer.registerEntity(entity, client)
        [client: client, staticApi: staticApi, instanceApi: instanceApi, validationApi: validationApi]
    }
}

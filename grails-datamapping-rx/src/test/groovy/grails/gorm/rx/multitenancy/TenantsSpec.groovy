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
package grails.gorm.rx.multitenancy

import grails.gorm.multitenancy.CurrentTenantHolder
import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.core.connections.ConnectionSources
import org.grails.datastore.mapping.model.ClassMapping
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.multitenancy.AllTenantsResolver
import org.grails.datastore.mapping.multitenancy.MultiTenancySettings
import org.grails.datastore.mapping.multitenancy.TenantResolver
import org.grails.datastore.mapping.multitenancy.exceptions.TenantException
import org.grails.datastore.rx.RxDatastoreClient
import org.grails.datastore.rx.internal.RxDatastoreClientImplementor
import org.grails.gorm.rx.api.RxGormEnhancer
import org.grails.gorm.rx.api.RxGormInstanceApi
import org.grails.gorm.rx.api.RxGormStaticApi
import org.grails.gorm.rx.api.RxGormValidationApi
import spock.lang.Specification

class TenantsSpec extends Specification {

    void cleanup() {
        RxGormEnhancer.close()
    }

    void "eachTenant invokes the closure for every non default connection source in DATABASE mode"() {
        given:
        List<Serializable> visited = []
        ConnectionSources connectionSources = Mock(ConnectionSources) {
            getAllConnectionSources() >> [
                    Mock(ConnectionSource) { getName() >> ConnectionSource.DEFAULT },
                    Mock(ConnectionSource) { getName() >> 'tenantA' },
                    Mock(ConnectionSource) { getName() >> 'tenantB' }
            ]
        }
        registerClient(MultiTenancySettings.MultiTenancyMode.DATABASE, Mock(TenantResolver), connectionSources)

        when:
        Tenants.eachTenant { Serializable tenantId -> visited << tenantId }

        then:
        visited == ['tenantA', 'tenantB']
    }

    void "eachTenant for a specific datastore type resolves the client by class"() {
        given:
        List<Serializable> visited = []
        ConnectionSources connectionSources = Mock(ConnectionSources) {
            getAllConnectionSources() >> [Mock(ConnectionSource) { getName() >> 'tenantA' }]
        }
        RxDatastoreClient client = registerClient(MultiTenancySettings.MultiTenancyMode.DATABASE, Mock(TenantResolver), connectionSources)

        when:
        Tenants.eachTenant(client.getClass()) { Serializable tenantId -> visited << tenantId }

        then:
        visited == ['tenantA']
    }

    void "eachTenant resolves all tenants from an AllTenantsResolver under a shared connection mode"() {
        given:
        List<Serializable> visited = []
        AllTenantsResolver resolver = Mock(AllTenantsResolver) { resolveTenantIds() >> ['t1', 't2'] }
        registerClient(MultiTenancySettings.MultiTenancyMode.DISCRIMINATOR, resolver, null)

        when:
        Tenants.eachTenant { Serializable tenantId -> visited << tenantId }

        then:
        visited == ['t1', 't2']
    }

    void "eachTenant fails when a shared connection mode has no AllTenantsResolver configured"() {
        given:
        registerClient(MultiTenancySettings.MultiTenancyMode.SCHEMA, Mock(TenantResolver), null)

        when:
        Tenants.eachTenant { }

        then:
        UnsupportedOperationException e = thrown(UnsupportedOperationException)
        e.message.contains('AllTenantsResolver')
    }

    void "eachTenant fails for an unsupported multi tenancy mode"() {
        given:
        registerClient(MultiTenancySettings.MultiTenancyMode.NONE, Mock(TenantResolver), null)

        when:
        Tenants.eachTenant { }

        then:
        UnsupportedOperationException e = thrown(UnsupportedOperationException)
        e.message.contains('Method not supported')
    }

    void "currentId resolves via the tenant resolver when no tenant is bound to the thread"() {
        given:
        TenantResolver resolver = Mock(TenantResolver) { resolveTenantIdentifier() >> 'resolvedTenant' }
        registerClient(MultiTenancySettings.MultiTenancyMode.NONE, resolver, null)

        expect:
        Tenants.currentId() == 'resolvedTenant'
    }

    void "currentId returns the tenant id bound to the thread by withId"() {
        given:
        registerClient(MultiTenancySettings.MultiTenancyMode.NONE, Mock(TenantResolver), null)

        expect:
        Tenants.withId('boundTenant') { -> Tenants.currentId() } == 'boundTenant'
    }

    void "currentId falls back to the core tenant binding when no RX tenant is bound"() {
        given:
        registerClient(MultiTenancySettings.MultiTenancyMode.NONE, Mock(TenantResolver), null)
        Datastore coreDatastore = Mock(Datastore)

        expect:
        CurrentTenantHolder.withTenant(coreDatastore, 'coreTenant') {
            Tenants.currentId()
        } == 'coreTenant'
    }

    void "an RX tenant binding takes precedence over the core tenant binding"() {
        given:
        registerClient(MultiTenancySettings.MultiTenancyMode.NONE, Mock(TenantResolver), null)
        Datastore coreDatastore = Mock(Datastore)

        expect:
        CurrentTenantHolder.withTenant(coreDatastore, 'coreTenant') {
            Tenants.withId('rxTenant') { Tenants.currentId() }
        } == 'rxTenant'
    }

    void "currentId fails closed when different tenants are bound to core datastores"() {
        given:
        registerClient(MultiTenancySettings.MultiTenancyMode.NONE, Mock(TenantResolver), null)
        Datastore firstDatastore = Mock(Datastore)
        Datastore secondDatastore = Mock(Datastore)

        when:
        CurrentTenantHolder.withTenant(firstDatastore, 'firstTenant') {
            CurrentTenantHolder.withTenant(secondDatastore, 'secondTenant') {
                Tenants.currentId()
            }
        }

        then:
        thrown(TenantException)
    }

    void "withId restores nested tenant bindings and removes the binding after the outer scope"() {
        given:
        TenantResolver resolver = Mock(TenantResolver) { resolveTenantIdentifier() >> 'resolvedTenant' }
        registerClient(MultiTenancySettings.MultiTenancyMode.NONE, resolver, null)

        expect:
        Tenants.withId('outerTenant') {
            Tenants.withId('innerTenant') { Tenants.currentId() } == 'innerTenant'
            Tenants.currentId()
        } == 'outerTenant'
        Tenants.currentId() == 'resolvedTenant'
    }

    void "withId removes the tenant binding when the closure throws"() {
        given:
        TenantResolver resolver = Mock(TenantResolver) { resolveTenantIdentifier() >> 'resolvedTenant' }
        registerClient(MultiTenancySettings.MultiTenancyMode.NONE, resolver, null)

        when:
        Tenants.withId('failingTenant') {
            throw new IllegalStateException('boom')
        }

        then:
        thrown(IllegalStateException)

        when:
        Serializable tenantId = Tenants.currentId()

        then:
        tenantId == 'resolvedTenant'
    }

    void "tenant bindings are isolated by RX datastore client"() {
        given:
        RxDatastoreClient firstClient = registerClient(MultiTenancySettings.MultiTenancyMode.NONE, Mock(TenantResolver), null, FirstRxDatastoreClient)
        RxDatastoreClient secondClient = registerClient(MultiTenancySettings.MultiTenancyMode.NONE, Mock(TenantResolver), null, SecondRxDatastoreClient)

        expect:
        Tenants.withId(firstClient.getClass(), 'firstTenant') {
            Tenants.withId(secondClient.getClass(), 'secondTenant') {
                [Tenants.currentId(firstClient.getClass()), Tenants.currentId(secondClient.getClass())]
            }
        } == ['firstTenant', 'secondTenant']
    }

    void "currentId for a datastore type resolves via the tenant resolver when no tenant is bound"() {
        given:
        TenantResolver resolver = Mock(TenantResolver) { resolveTenantIdentifier() >> 'resolvedTenant' }
        RxDatastoreClient client = registerClient(MultiTenancySettings.MultiTenancyMode.NONE, resolver, null)

        expect:
        Tenants.currentId(client.getClass()) == 'resolvedTenant'
    }

    void "currentId for a datastore type returns the tenant id bound to the thread by withId"() {
        given:
        RxDatastoreClient client = registerClient(MultiTenancySettings.MultiTenancyMode.NONE, Mock(TenantResolver), null)

        expect:
        Tenants.withId(client.getClass(), 'boundTenant') { -> Tenants.currentId(client.getClass()) } == 'boundTenant'
    }

    void "withCurrent resolves the tenant id from the resolver and executes the closure"() {
        given:
        TenantResolver resolver = Mock(TenantResolver) { resolveTenantIdentifier() >> 'currentTenant' }
        registerClient(MultiTenancySettings.MultiTenancyMode.NONE, resolver, null)

        expect:
        Tenants.withId('boundTenant') {
            Tenants.withCurrent { Serializable tenantId -> tenantId }
        } == 'currentTenant'
    }

    void "withCurrent for a datastore type resolves the tenant id from the resolver"() {
        given:
        TenantResolver resolver = Mock(TenantResolver) { resolveTenantIdentifier() >> 'currentTenant' }
        RxDatastoreClient client = registerClient(MultiTenancySettings.MultiTenancyMode.NONE, resolver, null)

        expect:
        Tenants.withId(client.getClass(), 'boundTenant') {
            Tenants.withCurrent(client.getClass()) { Serializable tenantId -> tenantId }
        } == 'currentTenant'
    }

    void "withId executes a zero argument closure with the datastore client as delegate"() {
        given:
        RxDatastoreClient client = registerClient(MultiTenancySettings.MultiTenancyMode.NONE, Mock(TenantResolver), null)

        expect:
        Tenants.withId('tenantX') { -> delegate } == client
    }

    void "withId rejects a closure that accepts too many arguments"() {
        given:
        registerClient(MultiTenancySettings.MultiTenancyMode.NONE, Mock(TenantResolver), null)

        when:
        Tenants.withId('tenantX') { a, b -> }

        then:
        thrown(IllegalArgumentException)
    }

    void "withId for a datastore type executes the closure with the tenant id"() {
        given:
        RxDatastoreClient client = registerClient(MultiTenancySettings.MultiTenancyMode.NONE, Mock(TenantResolver), null)

        expect:
        Tenants.withId(client.getClass(), 'tenantY') { Serializable tenantId -> tenantId } == 'tenantY'
    }

    private RxDatastoreClient registerClient(MultiTenancySettings.MultiTenancyMode mode, TenantResolver resolver,
                                             ConnectionSources connectionSources,
                                             Class<? extends RxDatastoreClientImplementor> clientType = RxDatastoreClientImplementor) {
        RxDatastoreClientImplementor client = Mock(clientType) {
            getMultiTenancyMode() >> mode
            getTenantResolver() >> resolver
            getConnectionSources() >> connectionSources
            createStaticApi(_, _) >> Stub(RxGormStaticApi)
            createInstanceApi(_, _) >> Stub(RxGormInstanceApi)
            createValidationApi(_, _) >> Stub(RxGormValidationApi)
        }
        PersistentEntity entity = Mock(PersistentEntity) {
            getMapping() >> Mock(ClassMapping)
            getJavaClass() >> String
            getName() >> 'TestEntity'
        }
        RxGormEnhancer.registerEntity(entity, client)
        return client
    }

    private interface FirstRxDatastoreClient extends RxDatastoreClientImplementor {
    }

    private interface SecondRxDatastoreClient extends RxDatastoreClientImplementor {
    }
}

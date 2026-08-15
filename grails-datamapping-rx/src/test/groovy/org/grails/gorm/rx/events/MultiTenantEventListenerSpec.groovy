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

package org.grails.gorm.rx.events

import grails.gorm.multitenancy.Tenants
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.engine.EntityAccess
import org.grails.datastore.mapping.engine.event.PersistenceEventListener
import org.grails.datastore.mapping.engine.event.PostInsertEvent
import org.grails.datastore.mapping.engine.event.PreInsertEvent
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.types.TenantId
import org.grails.datastore.mapping.multitenancy.MultiTenancySettings
import org.grails.datastore.mapping.multitenancy.MultiTenantCapableDatastore
import org.grails.datastore.mapping.multitenancy.TenantResolver
import org.grails.datastore.mapping.multitenancy.exceptions.TenantException
import org.grails.datastore.mapping.query.Query
import org.grails.datastore.mapping.query.event.PostQueryEvent
import org.grails.datastore.mapping.query.event.PreQueryEvent
import org.grails.datastore.mapping.reflect.EntityReflector
import org.grails.datastore.rx.RxDatastoreClient
import org.grails.gorm.rx.api.RxGormEnhancer
import spock.lang.Specification
import spock.lang.Unroll

import java.lang.reflect.Field

class MultiTenantEventListenerSpec extends Specification {

    RxDatastoreClient datastoreClient = Mock(RxDatastoreClient)
    MultiTenantEventListener listener = new MultiTenantEventListener(datastoreClient)

    void setup() {
        datastoreClient.getTenantResolver() >> Stub(TenantResolver) {
            resolveTenantIdentifier() >> null
        }
        // Tenants.currentId() always consults RxGormEnhancer's static client registry
        // before checking the thread-bound tenant id, so it must know about this mock.
        datastoreClientRegistry()[datastoreClient.getClass()] = datastoreClient
    }

    void cleanup() {
        datastoreClientRegistry().remove(datastoreClient.getClass())
    }

    @Unroll
    void "supportsEventType returns true for #eventType.simpleName"() {
        expect:
        listener.supportsEventType(eventType)

        where:
        eventType << [PreQueryEvent, PostQueryEvent, PreInsertEvent]
    }

    void "supportsEventType returns false for an event type it does not handle"() {
        expect:
        !listener.supportsEventType(PostInsertEvent)
    }

    void "supportsSourceType returns true for RxDatastoreClient implementations"() {
        expect:
        listener.supportsSourceType(RxDatastoreClient)
        listener.supportsSourceType(datastoreClient.getClass())
    }

    void "supportsSourceType returns false for unrelated source types"() {
        expect:
        !listener.supportsSourceType(String)
    }

    void "getOrder returns the default listener order"() {
        expect:
        listener.getOrder() == PersistenceEventListener.DEFAULT_ORDER
    }

    void "onApplicationEvent ignores event types it does not support"() {
        given:
        PersistentEntity entity = Mock(PersistentEntity)
        PostInsertEvent event = new PostInsertEvent(datastoreClient, entity)

        when:
        listener.onApplicationEvent(event)

        then:
        0 * entity._
    }

    void "onApplicationEvent ignores query events with no matching branch such as PostQueryEvent"() {
        given:
        Query query = Mock(Query)
        PostQueryEvent event = new PostQueryEvent(datastoreClient, query, [])

        when:
        listener.onApplicationEvent(event)

        then:
        0 * query._
    }

    void "onApplicationEvent does nothing on PreQueryEvent when the entity is not multi tenant"() {
        given:
        PersistentEntity entity = Stub(PersistentEntity) {
            isMultiTenant() >> false
        }
        Query query = Mock(Query) {
            getEntity() >> entity
        }
        PreQueryEvent event = new PreQueryEvent(datastoreClient, query)

        when:
        listener.onApplicationEvent(event)

        then:
        0 * query.eq(_, _)
    }

    void "onApplicationEvent does nothing on PreQueryEvent when the entity has no tenant id property"() {
        given:
        PersistentEntity entity = Stub(PersistentEntity) {
            isMultiTenant() >> true
            getTenantId() >> null
        }
        Query query = Mock(Query) {
            getEntity() >> entity
        }
        PreQueryEvent event = new PreQueryEvent(datastoreClient, query)

        when:
        listener.onApplicationEvent(event)

        then:
        0 * query.eq(_, _)
    }

    void "onApplicationEvent does nothing on PreQueryEvent when the event source is a different datastore client"() {
        given:
        RxDatastoreClient otherClient = Mock(RxDatastoreClient)
        TenantId tenantId = Stub(TenantId) {
            getName() >> 'tenantId'
        }
        PersistentEntity entity = Stub(PersistentEntity) {
            isMultiTenant() >> true
            getTenantId() >> tenantId
        }
        Query query = Mock(Query) {
            getEntity() >> entity
        }
        PreQueryEvent event = new PreQueryEvent(otherClient, query)

        when:
        listener.onApplicationEvent(event)

        then:
        0 * query.eq(_, _)
    }

    void "onApplicationEvent applies the current tenant id as a query restriction on PreQueryEvent"() {
        given:
        TenantId tenantId = Stub(TenantId) {
            getName() >> 'tenantId'
        }
        PersistentEntity entity = Stub(PersistentEntity) {
            isMultiTenant() >> true
            getTenantId() >> tenantId
        }
        Query query = Mock(Query) {
            getEntity() >> entity
        }
        PreQueryEvent event = new PreQueryEvent(datastoreClient, query)
        MultiTenantCapableDatastore multiTenantCapableDatastore = sharedConnectionDatastore()

        when:
        Tenants.withId(multiTenantCapableDatastore, 'tenant-a') {
            listener.onApplicationEvent(event)
        }

        then:
        1 * query.eq('tenantId', 'tenant-a')
    }

    void "onApplicationEvent does nothing on PreInsertEvent when the entity is not multi tenant"() {
        given:
        PersistentEntity entity = Mock(PersistentEntity) {
            isMultiTenant() >> false
        }
        PreInsertEvent event = new PreInsertEvent(datastoreClient, entity)

        when:
        listener.onApplicationEvent(event)

        then:
        0 * entity.getReflector()
    }

    void "onApplicationEvent does nothing on PreInsertEvent when the event source is a different datastore client"() {
        given:
        RxDatastoreClient otherClient = Mock(RxDatastoreClient)
        TenantId tenantId = Stub(TenantId) {
            getName() >> 'tenantId'
        }
        EntityReflector reflector = Mock(EntityReflector)
        PersistentEntity entity = Stub(PersistentEntity) {
            isMultiTenant() >> true
            getTenantId() >> tenantId
            getReflector() >> reflector
        }
        EntityAccess entityAccess = Stub(EntityAccess) {
            getEntity() >> new Object()
        }
        PreInsertEvent event = new PreInsertEvent(otherClient, entity, entityAccess)

        when:
        listener.onApplicationEvent(event)

        then:
        0 * reflector.setProperty(*_)
    }

    void "onApplicationEvent does nothing on PreInsertEvent when there is no current tenant id"() {
        given:
        TenantId tenantId = Stub(TenantId) {
            getName() >> 'tenantId'
        }
        EntityReflector reflector = Mock(EntityReflector)
        PersistentEntity entity = Stub(PersistentEntity) {
            isMultiTenant() >> true
            getTenantId() >> tenantId
            getReflector() >> reflector
        }
        EntityAccess entityAccess = Stub(EntityAccess) {
            getEntity() >> new Object()
        }
        PreInsertEvent event = new PreInsertEvent(datastoreClient, entity, entityAccess)

        when:
        listener.onApplicationEvent(event)

        then:
        0 * reflector.setProperty(*_)
    }

    void "onApplicationEvent stamps the current tenant id onto the entity on PreInsertEvent"() {
        given:
        TenantId tenantId = Stub(TenantId) {
            getName() >> 'tenantId'
        }
        EntityReflector reflector = Mock(EntityReflector)
        PersistentEntity entity = Stub(PersistentEntity) {
            isMultiTenant() >> true
            getTenantId() >> tenantId
            getReflector() >> reflector
        }
        Object entityObject = new Object()
        EntityAccess entityAccess = Stub(EntityAccess) {
            getEntity() >> entityObject
        }
        PreInsertEvent event = new PreInsertEvent(datastoreClient, entity, entityAccess)
        MultiTenantCapableDatastore multiTenantCapableDatastore = sharedConnectionDatastore()

        when:
        Tenants.withId(multiTenantCapableDatastore, 'tenant-a') {
            listener.onApplicationEvent(event)
        }

        then:
        1 * reflector.setProperty(entityObject, 'tenantId', 'tenant-a')
    }

    void "onApplicationEvent resolves the tenant id from the entity when the current tenant is the default connection"() {
        given:
        TenantId tenantId = Stub(TenantId) {
            getName() >> 'tenantId'
        }
        EntityReflector reflector = Mock(EntityReflector)
        PersistentEntity entity = Stub(PersistentEntity) {
            isMultiTenant() >> true
            getTenantId() >> tenantId
            getReflector() >> reflector
        }
        Object entityObject = new Object()
        EntityAccess entityAccess = Stub(EntityAccess) {
            getEntity() >> entityObject
            getProperty('tenantId') >> 'tenant-from-entity'
        }
        PreInsertEvent event = new PreInsertEvent(datastoreClient, entity, entityAccess)
        MultiTenantCapableDatastore multiTenantCapableDatastore = sharedConnectionDatastore()

        when:
        Tenants.withId(multiTenantCapableDatastore, ConnectionSource.DEFAULT) {
            listener.onApplicationEvent(event)
        }

        then:
        1 * reflector.setProperty(entityObject, 'tenantId', 'tenant-from-entity')
    }

    void "onApplicationEvent wraps a property assignment failure in a TenantException on PreInsertEvent"() {
        given:
        TenantId tenantId = Stub(TenantId) {
            getName() >> 'tenantId'
        }
        EntityReflector reflector = Mock(EntityReflector)
        PersistentEntity entity = Stub(PersistentEntity) {
            isMultiTenant() >> true
            getTenantId() >> tenantId
            getReflector() >> reflector
        }
        Object entityObject = new Object()
        EntityAccess entityAccess = Stub(EntityAccess) {
            getEntity() >> entityObject
        }
        PreInsertEvent event = new PreInsertEvent(datastoreClient, entity, entityAccess)
        MultiTenantCapableDatastore multiTenantCapableDatastore = sharedConnectionDatastore()

        when:
        Tenants.withId(multiTenantCapableDatastore, 'tenant-a') {
            listener.onApplicationEvent(event)
        }

        then:
        1 * reflector.setProperty(entityObject, 'tenantId', 'tenant-a') >> { throw new IllegalStateException('type mismatch') }
        TenantException e = thrown(TenantException)
        e.message.contains('tenant-a')
        e.message.startsWith('Could not assign tenant id')
        e.cause instanceof IllegalStateException
    }

    private MultiTenantCapableDatastore sharedConnectionDatastore() {
        Stub(MultiTenantCapableDatastore) {
            getMultiTenancyMode() >> MultiTenancySettings.MultiTenancyMode.DISCRIMINATOR
        }
    }

    private static Map datastoreClientRegistry() {
        Field field = RxGormEnhancer.getDeclaredField('DATASTORE_CLIENTS')
        field.accessible = true
        (Map) field.get(null)
    }
}

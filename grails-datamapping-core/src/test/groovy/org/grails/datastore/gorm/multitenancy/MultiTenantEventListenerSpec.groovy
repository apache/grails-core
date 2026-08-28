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
package org.grails.datastore.gorm.multitenancy

import grails.gorm.multitenancy.CurrentTenantHolder
import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.engine.EntityAccess
import org.grails.datastore.mapping.engine.event.PersistenceEventListener
import org.grails.datastore.mapping.engine.event.PreInsertEvent
import org.grails.datastore.mapping.engine.event.PreUpdateEvent
import org.grails.datastore.mapping.engine.event.ValidationEvent
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.types.TenantId
import org.grails.datastore.mapping.multitenancy.MultiTenantCapableDatastore
import org.grails.datastore.mapping.multitenancy.exceptions.TenantException
import org.grails.datastore.mapping.query.Query
import org.grails.datastore.mapping.query.event.PreQueryEvent
import org.springframework.context.ApplicationEvent
import spock.lang.Specification
import spock.lang.Unroll

/**
 * No spec existed at all for this class before this coverage pass (0% patch coverage on the
 * GormRegistry rewrite - every line changed: {@code GormEnhancer.findDatastore} became
 * {@code GormRegistry.getInstance().getApiResolver().findDatastore}, {@code supportsSourceType}
 * now compares against the listener's own bound datastore class rather than a fixed type, a new
 * {@code isValidSource} instance-equality guard replaced the old {@code supportsEventType} check,
 * a {@code ConnectionSource.DEFAULT} + numeric-typed-tenant-id coercion to {@code 0L} was added to
 * both the query and insert/update paths, and inserts now prefer an already-set entity property
 * over the resolved tenant id). Modeled directly on the equivalent, already-established
 * {@code org.grails.orm.hibernate.multitenancy.MultiTenantEventListenerSpec} in grails-data-hibernate7,
 * which exercises the sibling class's public {@code onApplicationEvent}/{@code supportsEventType}/
 * {@code supportsSourceType} contract the same way.
 */
class MultiTenantEventListenerSpec extends Specification {

    Datastore boundDatastore = Mock(MultiTenantCapableDatastore)
    MultiTenantEventListener listener = new MultiTenantEventListener(boundDatastore)

    // ─── supportsEventType ────────────────────────────────────────────────────

    @Unroll
    void "supportsEventType returns true for #type.simpleName"() {
        expect:
        listener.supportsEventType(type)

        where:
        type << [PreQueryEvent, ValidationEvent, PreInsertEvent, PreUpdateEvent]
    }

    void "supportsEventType returns false for an unrelated event type"() {
        expect:
        !listener.supportsEventType(ApplicationEvent)
    }

    // ─── supportsSourceType ───────────────────────────────────────────────────

    void "supportsSourceType returns true for the bound datastore's own class"() {
        expect:
        listener.supportsSourceType(boundDatastore.class)
    }

    void "supportsSourceType returns false for an unrelated type"() {
        expect:
        !listener.supportsSourceType(String)
        !listener.supportsSourceType(Object)
    }

    // ─── getOrder ─────────────────────────────────────────────────────────────

    void "getOrder returns DEFAULT_ORDER from PersistenceEventListener"() {
        expect:
        listener.getOrder() == PersistenceEventListener.DEFAULT_ORDER
    }

    // ─── onApplicationEvent: isValidSource guard (new in the GormRegistry rewrite) ────

    void "onApplicationEvent ignores events whose source is not a Datastore at all"() {
        given:
        def event = new ApplicationEvent("not a datastore") {}

        when:
        listener.onApplicationEvent(event)

        then:
        noExceptionThrown()
    }

    void "onApplicationEvent ignores events from a different Datastore instance than the one it is bound to"() {
        given:
        def otherDatastore = Mock(MultiTenantCapableDatastore)
        def entity = Mock(PersistentEntity) { isMultiTenant() >> true }
        def query = Mock(Query) { getEntity() >> entity }
        def event = new PreQueryEvent(otherDatastore, query)

        when:
        listener.onApplicationEvent(event)

        then: "the query is never touched because the event's source isn't this listener's own datastore"
        0 * query.eq(_, _)
    }

    // ─── onApplicationEvent: PreQueryEvent ────────────────────────────────────

    void "onApplicationEvent PreQueryEvent on a non-multi-tenant entity does not filter the query"() {
        given:
        def entity = Mock(PersistentEntity) { isMultiTenant() >> false }
        def query = Mock(Query) { getEntity() >> entity }
        def event = new PreQueryEvent(boundDatastore, query)

        when:
        listener.onApplicationEvent(event)

        then:
        0 * query.eq(_, _)
    }

    void "onApplicationEvent PreQueryEvent filters by the current tenant id on a multi-tenant entity"() {
        given:
        def tenantId = Mock(TenantId) { getName() >> 'tenantId'; getType() >> String }
        def entity = Mock(PersistentEntity) { isMultiTenant() >> true; getTenantId() >> tenantId }
        def query = Mock(Query) { getEntity() >> entity }
        def event = new PreQueryEvent(boundDatastore, query)

        when:
        CurrentTenantHolder.withTenant(boundDatastore, 'tenant1') {
            listener.onApplicationEvent(event)
        }

        then:
        1 * query.eq('tenantId', 'tenant1')
    }

    void "onApplicationEvent PreQueryEvent coerces a DEFAULT connection source id to 0L for a numeric tenant id"() {
        given:
        def tenantId = Mock(TenantId) { getName() >> 'tenantId'; getType() >> Long }
        def entity = Mock(PersistentEntity) { isMultiTenant() >> true; getTenantId() >> tenantId }
        def query = Mock(Query) { getEntity() >> entity }
        def event = new PreQueryEvent(boundDatastore, query)

        when:
        CurrentTenantHolder.withTenant(boundDatastore, ConnectionSource.DEFAULT) {
            listener.onApplicationEvent(event)
        }

        then:
        1 * query.eq('tenantId', 0L)
    }

    void "onApplicationEvent PreQueryEvent does not coerce a DEFAULT connection source id for a non-numeric tenant id"() {
        given:
        def tenantId = Mock(TenantId) { getName() >> 'tenantId'; getType() >> String }
        def entity = Mock(PersistentEntity) { isMultiTenant() >> true; getTenantId() >> tenantId }
        def query = Mock(Query) { getEntity() >> entity }
        def event = new PreQueryEvent(boundDatastore, query)

        when:
        CurrentTenantHolder.withTenant(boundDatastore, ConnectionSource.DEFAULT) {
            listener.onApplicationEvent(event)
        }

        then:
        1 * query.eq('tenantId', ConnectionSource.DEFAULT)
    }

    void "onApplicationEvent PreQueryEvent does not filter when there is no current tenant"() {
        given:
        def tenantId = Mock(TenantId) { getName() >> 'tenantId'; getType() >> String }
        def entity = Mock(PersistentEntity) { isMultiTenant() >> true; getTenantId() >> tenantId }
        def query = Mock(Query) { getEntity() >> entity }
        def event = new PreQueryEvent(boundDatastore, query)
        boundDatastore.getTenantResolver() >> Mock(org.grails.datastore.mapping.multitenancy.TenantResolver) {
            resolveTenantIdentifier() >> null
        }

        when:
        listener.onApplicationEvent(event)

        then:
        0 * query.eq(_, _)
    }

    void "onApplicationEvent PreQueryEvent does nothing when the multi-tenant entity has no tenant id mapping"() {
        given:
        def entity = Mock(PersistentEntity) { isMultiTenant() >> true; getTenantId() >> null }
        def query = Mock(Query) { getEntity() >> entity }
        def event = new PreQueryEvent(boundDatastore, query)

        when:
        CurrentTenantHolder.withTenant(boundDatastore, 'tenant1') {
            listener.onApplicationEvent(event)
        }

        then:
        noExceptionThrown()
        0 * query.eq(_, _)
    }

    // ─── onApplicationEvent: PreInsertEvent / PreUpdateEvent / ValidationEvent ────

    void "onApplicationEvent PreInsertEvent on a non-multi-tenant entity sets no tenant property"() {
        given:
        def entity = Mock(PersistentEntity) { isMultiTenant() >> false }
        def entityAccess = Mock(EntityAccess)
        def event = new PreInsertEvent(boundDatastore, entity, entityAccess)

        when:
        listener.onApplicationEvent(event)

        then:
        0 * entityAccess.setProperty(_, _)
    }

    @Unroll
    void "onApplicationEvent #eventType.simpleName sets the resolved tenant id on the entity"() {
        given:
        def tenantId = Mock(TenantId) { getName() >> 'tenantId'; getType() >> String }
        def entity = Mock(PersistentEntity) { isMultiTenant() >> true; getTenantId() >> tenantId }
        def entityAccess = Mock(EntityAccess) { getProperty('tenantId') >> null }
        def event = eventType.getConstructor(Datastore, PersistentEntity, EntityAccess)
                .newInstance(boundDatastore, entity, entityAccess)

        when:
        CurrentTenantHolder.withTenant(boundDatastore, 'tenant1') {
            listener.onApplicationEvent(event)
        }

        then:
        1 * entityAccess.setProperty('tenantId', 'tenant1')

        where:
        eventType << [ValidationEvent, PreInsertEvent, PreUpdateEvent]
    }

    void "onApplicationEvent PreInsertEvent prefers an already-set entity property over the resolved tenant id"() {
        given:
        def tenantId = Mock(TenantId) { getName() >> 'tenantId'; getType() >> String }
        def entity = Mock(PersistentEntity) { isMultiTenant() >> true; getTenantId() >> tenantId }
        def entityAccess = Mock(EntityAccess) { getProperty('tenantId') >> 'already_set_tenant' }
        def event = new PreInsertEvent(boundDatastore, entity, entityAccess)

        when:
        CurrentTenantHolder.withTenant(boundDatastore, 'resolved_tenant') {
            listener.onApplicationEvent(event)
        }

        then: "the pre-existing property value wins over the resolved current tenant id"
        1 * entityAccess.setProperty('tenantId', 'already_set_tenant')
    }

    void "onApplicationEvent PreInsertEvent coerces a DEFAULT connection source id to 0L for a numeric tenant id"() {
        given:
        def tenantId = Mock(TenantId) { getName() >> 'tenantId'; getType() >> Long }
        def entity = Mock(PersistentEntity) { isMultiTenant() >> true; getTenantId() >> tenantId }
        def entityAccess = Mock(EntityAccess) { getProperty('tenantId') >> null }
        def event = new PreInsertEvent(boundDatastore, entity, entityAccess)

        when:
        CurrentTenantHolder.withTenant(boundDatastore, ConnectionSource.DEFAULT) {
            listener.onApplicationEvent(event)
        }

        then:
        1 * entityAccess.setProperty('tenantId', 0L)
    }

    void "onApplicationEvent PreInsertEvent does not set a property when there is no current tenant"() {
        given:
        def tenantId = Mock(TenantId) { getName() >> 'tenantId'; getType() >> String }
        def entity = Mock(PersistentEntity) { isMultiTenant() >> true; getTenantId() >> tenantId }
        def entityAccess = Mock(EntityAccess)
        def event = new PreInsertEvent(boundDatastore, entity, entityAccess)
        boundDatastore.getTenantResolver() >> Mock(org.grails.datastore.mapping.multitenancy.TenantResolver) {
            resolveTenantIdentifier() >> null
        }

        when:
        listener.onApplicationEvent(event)

        then:
        0 * entityAccess.setProperty(_, _)
    }

    void "onApplicationEvent PreInsertEvent wraps a setProperty failure in a TenantException"() {
        given:
        def tenantId = Mock(TenantId) { getName() >> 'tenantId'; getType() >> String }
        def entity = Mock(PersistentEntity) { isMultiTenant() >> true; getTenantId() >> tenantId }
        def entityAccess = Mock(EntityAccess) {
            getProperty('tenantId') >> null
            setProperty(_, _) >> { throw new IllegalArgumentException('type mismatch') }
        }
        def event = new PreInsertEvent(boundDatastore, entity, entityAccess)

        when:
        CurrentTenantHolder.withTenant(boundDatastore, 'tenant1') {
            listener.onApplicationEvent(event)
        }

        then:
        def e = thrown(TenantException)
        e.message.contains('Could not assigned tenant id')
        e.cause instanceof IllegalArgumentException
    }
}

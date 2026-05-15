/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the 'License')
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.grails.datastore.gorm.multitenancy

import grails.gorm.multitenancy.Tenants
import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.engine.EntityAccess
import org.grails.datastore.mapping.engine.event.PreInsertEvent
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.types.TenantId
import org.grails.datastore.mapping.multitenancy.MultiTenantCapableDatastore
import spock.lang.Specification

class MultiTenantEventListenerSpec extends Specification {

    static class DummyTenantId extends TenantId {

        DummyTenantId() { super(null, null, 'tenantId', Long) }
        org.grails.datastore.mapping.model.PropertyMapping getMapping() { null }
    }

    void 'test tenantId is not overridden if it already exists'() {
        given: 'A mock datastore and entity'
        MultiTenantCapableDatastore datastore = Mock(MultiTenantCapableDatastore)
        PersistentEntity entity = Mock(PersistentEntity)
        TenantId tenantId = new DummyTenantId()
        EntityAccess entityAccess = Mock(EntityAccess)

        and: 'Setup entity multi-tenant mocks'
        entity.isMultiTenant() >> true
        entity.getTenantId() >> tenantId
        entity.getJavaClass() >> MultiTenantEventListenerSpec

        and: 'A listener'
        def listener = new MultiTenantEventListener(datastore)

        when: 'A PreInsertEvent is triggered with an existing tenantId'
        def preInsertEvent = new PreInsertEvent(datastore, entity, entityAccess)
        // Stub the Tenants.currentId call
        Tenants.datastoreLocator = new Tenants.DatastoreLocator() {
            Datastore getDatastore() { return datastore }
        }
        datastore.getMultiTenancyMode() >> org.grails.datastore.mapping.multitenancy.MultiTenancySettings.MultiTenancyMode.DATABASE
        datastore.withNewSession(_ as Serializable, _ as Closure) >> { Serializable tId, Closure callable -> callable.call(null) }

        Tenants.withId(datastore, 'SystemTenant') {
            listener.onApplicationEvent(preInsertEvent)
        }

        then: 'The listener checks for existing tenantId'
        1 * entityAccess.getProperty('tenantId') >> 'ManualTenant'
        
        and: 'It sets it to the existing tenantId instead of the current context tenant'
        1 * entityAccess.setProperty('tenantId', 'ManualTenant')
        0 * entityAccess.setProperty('tenantId', 'SystemTenant')

        cleanup:
        Tenants.datastoreLocator = new Tenants.DatastoreLocator()
    }

    void 'test tenantId is set to current tenant if it does not exist'() {
        given: 'A mock datastore and entity'
        MultiTenantCapableDatastore datastore = Mock(MultiTenantCapableDatastore)
        PersistentEntity entity = Mock(PersistentEntity)
        TenantId tenantId = new DummyTenantId()
        EntityAccess entityAccess = Mock(EntityAccess)

        and: 'Setup entity multi-tenant mocks'
        entity.isMultiTenant() >> true
        entity.getTenantId() >> tenantId
        entity.getJavaClass() >> MultiTenantEventListenerSpec

        and: 'A listener'
        def listener = new MultiTenantEventListener(datastore)

        when: 'A PreInsertEvent is triggered with no existing tenantId'
        def preInsertEvent = new PreInsertEvent(datastore, entity, entityAccess)
        // Stub the Tenants.currentId call
        Tenants.datastoreLocator = new Tenants.DatastoreLocator() {
            Datastore getDatastore() { return datastore }
        }
        datastore.getMultiTenancyMode() >> org.grails.datastore.mapping.multitenancy.MultiTenancySettings.MultiTenancyMode.DATABASE
        datastore.withNewSession(_ as Serializable, _ as Closure) >> { Serializable tId, Closure callable -> callable.call(null) }

        Tenants.withId(datastore, 'SystemTenant') {
            listener.onApplicationEvent(preInsertEvent)
        }

        then: 'The listener checks for existing tenantId and finds null'
        1 * entityAccess.getProperty('tenantId') >> null
        
        and: 'It sets it to the system tenantId'
        1 * entityAccess.setProperty('tenantId', 'SystemTenant')

        cleanup:
        Tenants.datastoreLocator = new Tenants.DatastoreLocator()
    }
}

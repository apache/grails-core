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
package org.grails.orm.hibernate.multitenancy

import org.grails.datastore.mapping.engine.event.PreInsertEvent
import org.grails.datastore.mapping.engine.event.PreUpdateEvent
import org.grails.datastore.mapping.engine.event.ValidationEvent
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.types.TenantId
import org.grails.datastore.mapping.multitenancy.MultiTenantCapableDatastore
import org.grails.datastore.mapping.query.Query
import org.grails.datastore.mapping.query.event.PreQueryEvent
import org.grails.datastore.mapping.multitenancy.exceptions.TenantException
import org.grails.orm.hibernate.HibernateDatastore
import org.springframework.context.ApplicationEvent
import spock.lang.Specification
import spock.lang.Unroll

class MultiTenantEventListenerSpec extends Specification {

    MultiTenantEventListener listener = new MultiTenantEventListener()

    // ─── supportsEventType ────────────────────────────────────────────────────

    @Unroll
    void "supportsEventType returns true for #type.simpleName"() {
        expect:
        listener.supportsEventType(type)

        where:
        type << [PreQueryEvent, ValidationEvent, PreInsertEvent, PreUpdateEvent]
    }

    void "supportsEventType returns false for generic ApplicationEvent"() {
        expect:
        !listener.supportsEventType(ApplicationEvent)
    }

    void "supportsEventType returns false for unrelated event type"() {
        expect:
        !listener.supportsEventType(Object)
    }

    // ─── supportsSourceType ───────────────────────────────────────────────────

    void "supportsSourceType returns true for HibernateDatastore itself"() {
        expect:
        listener.supportsSourceType(HibernateDatastore)
    }

    void "supportsSourceType returns true for a subclass of HibernateDatastore"() {
        given:
        // anonymous subclass simulates a concrete HibernateDatastore
        def subclass = Mock(HibernateDatastore).class

        expect:
        listener.supportsSourceType(HibernateDatastore)
    }

    void "supportsSourceType returns false for plain Datastore"() {
        expect:
        !listener.supportsSourceType(Object)
    }

    void "supportsSourceType returns false for String"() {
        expect:
        !listener.supportsSourceType(String)
    }

    // ─── getOrder ─────────────────────────────────────────────────────────────

    void "getOrder returns DEFAULT_ORDER from PersistenceEventListener"() {
        expect:
        listener.getOrder() == org.grails.datastore.mapping.engine.event.PersistenceEventListener.DEFAULT_ORDER
    }

    // ─── onApplicationEvent: unsupported event type is silently ignored ───────

    void "onApplicationEvent with unsupported event type does nothing"() {
        given:
        def unsupportedEvent = new ApplicationEvent("source") {}

        when:
        listener.onApplicationEvent(unsupportedEvent)

        then:
        noExceptionThrown()
    }

    // ─── onApplicationEvent: PreQueryEvent — non-multi-tenant entity ──────────

    void "onApplicationEvent PreQueryEvent on non-multi-tenant entity does not call enableMultiTenancyFilter"() {
        given:
        def datastore = Mock(HibernateDatastore)
        def entity    = Mock(PersistentEntity) { isMultiTenant() >> false }
        def query     = Mock(Query) { getEntity() >> entity }
        def event     = new PreQueryEvent(datastore, query)

        when:
        listener.onApplicationEvent(event)

        then:
        0 * datastore.enableMultiTenancyFilter()
    }

    // ─── onApplicationEvent: PreQueryEvent — multi-tenant entity ─────────────

    void "onApplicationEvent PreQueryEvent on multi-tenant entity calls enableMultiTenancyFilter"() {
        given:
        def datastore = Mock(HibernateDatastore)
        def entity    = Mock(PersistentEntity) { isMultiTenant() >> true }
        def query     = Mock(Query) { getEntity() >> entity }
        def event     = new PreQueryEvent(datastore, query)

        when:
        listener.onApplicationEvent(event)

        then:
        1 * datastore.enableMultiTenancyFilter()
    }

    void "onApplicationEvent PreQueryEvent with non-Hibernate source does not call enableMultiTenancyFilter"() {
        given: "source is not an HibernateDatastore"
        def nonHibernateDatastore = Mock(org.grails.datastore.mapping.core.Datastore)
        def entity = Mock(PersistentEntity) { isMultiTenant() >> true }
        def query  = Mock(Query) { getEntity() >> entity }
        def event  = new PreQueryEvent(nonHibernateDatastore, query)

        when:
        listener.onApplicationEvent(event)

        then:
        noExceptionThrown()
    }

    // ─── onApplicationEvent: PreInsertEvent — non-multi-tenant entity ─────────

    void "onApplicationEvent PreInsertEvent on non-multi-tenant entity sets no tenant"() {
        given:
        def datastore    = Mock(HibernateDatastore)
        def entity       = Mock(PersistentEntity) { isMultiTenant() >> false }
        def entityAccess = Mock(org.grails.datastore.mapping.engine.EntityAccess)
        def event        = new PreInsertEvent(datastore, entity, entityAccess)

        when:
        listener.onApplicationEvent(event)

        then:
        0 * entityAccess.setProperty(_, _)
    }

    // ─── onApplicationEvent: PreInsertEvent — multi-tenant, no resolver → no-op ─

    void "onApplicationEvent PreInsertEvent on multi-tenant entity does not set tenantId when resolver returns null"() {
        given: "resolver returns null tenant — no-op path"
        def resolver     = Mock(org.grails.datastore.mapping.multitenancy.TenantResolver) { resolveTenantIdentifier() >> null }
        def tenantId     = Mock(TenantId) { getName() >> "tenantId" }
        def entity       = Mock(PersistentEntity) {
            isMultiTenant() >> true
            getTenantId()   >> tenantId
        }
        def entityAccess = Mock(org.grails.datastore.mapping.engine.EntityAccess)
        def datastore    = Mock(HibernateDatastore) { getTenantResolver() >> resolver }
        def event        = new PreInsertEvent(datastore, entity, entityAccess)

        when:
        listener.onApplicationEvent(event)

        then: "setProperty never called because currentId is null"
        0 * entityAccess.setProperty(_, _)
    }

    // ─── onApplicationEvent: PreUpdateEvent — multi-tenant, no resolver → no-op ─

    void "onApplicationEvent PreUpdateEvent on multi-tenant entity does not set tenantId when resolver returns null"() {
        given:
        def resolver     = Mock(org.grails.datastore.mapping.multitenancy.TenantResolver) { resolveTenantIdentifier() >> null }
        def tenantId     = Mock(TenantId) { getName() >> "tenantId" }
        def entity       = Mock(PersistentEntity) {
            isMultiTenant() >> true
            getTenantId()   >> tenantId
        }
        def entityAccess = Mock(org.grails.datastore.mapping.engine.EntityAccess)
        def datastore    = Mock(HibernateDatastore) { getTenantResolver() >> resolver }
        def event        = new PreUpdateEvent(datastore, entity, entityAccess)

        when:
        listener.onApplicationEvent(event)

        then:
        0 * entityAccess.setProperty(_, _)
    }

    // ─── onApplicationEvent: PreInsertEvent — multi-tenant, resolver returns non-null ─

    void "onApplicationEvent PreInsertEvent on multi-tenant entity sets tenantId when resolver returns non-null"() {
        given: "resolver returns a valid tenant id"
        def resolver     = Mock(org.grails.datastore.mapping.multitenancy.TenantResolver) { resolveTenantIdentifier() >> "tenant1" }
        def tenantId     = Mock(TenantId) { getName() >> "tenantId" }
        def entity       = Mock(PersistentEntity) {
            isMultiTenant() >> true
            getTenantId()   >> tenantId
        }
        def entityAccess = Mock(org.grails.datastore.mapping.engine.EntityAccess)
        def datastore    = Mock(HibernateDatastore) { getTenantResolver() >> resolver }
        def event        = new PreInsertEvent(datastore, entity, entityAccess)

        when:
        listener.onApplicationEvent(event)

        then:
        1 * entityAccess.setProperty("tenantId", "tenant1")
    }

    void "onApplicationEvent PreInsertEvent throws TenantException when setProperty fails"() {
        given: "resolver returns a valid tenant id but setProperty throws"
        def resolver     = Mock(org.grails.datastore.mapping.multitenancy.TenantResolver) { resolveTenantIdentifier() >> "tenant1" }
        def tenantId     = Mock(TenantId) { getName() >> "tenantId" }
        def entity       = Mock(PersistentEntity) {
            isMultiTenant() >> true
            getTenantId()   >> tenantId
        }
        def entityAccess = Mock(org.grails.datastore.mapping.engine.EntityAccess) {
            setProperty(_, _) >> { throw new IllegalArgumentException("type mismatch") }
        }
        def datastore    = Mock(HibernateDatastore) { getTenantResolver() >> resolver }
        def event        = new PreInsertEvent(datastore, entity, entityAccess)

        when:
        listener.onApplicationEvent(event)

        then:
        thrown(TenantException)
    }

    void "onApplicationEvent PreInsertEvent reads tenantId from entity property when currentId is DEFAULT"() {
        given: "resolver returns DEFAULT connection source id"
        def resolver     = Mock(org.grails.datastore.mapping.multitenancy.TenantResolver) {
            resolveTenantIdentifier() >> org.grails.datastore.mapping.core.connections.ConnectionSource.DEFAULT
        }
        def tenantId     = Mock(TenantId) { getName() >> "tenantId" }
        def entity       = Mock(PersistentEntity) {
            isMultiTenant() >> true
            getTenantId()   >> tenantId
        }
        def entityAccess = Mock(org.grails.datastore.mapping.engine.EntityAccess) {
            getProperty("tenantId") >> "entity_tenant"
        }
        def datastore    = Mock(HibernateDatastore) { getTenantResolver() >> resolver }
        def event        = new PreInsertEvent(datastore, entity, entityAccess)

        when:
        listener.onApplicationEvent(event)

        then:
        1 * entityAccess.setProperty("tenantId", "entity_tenant")
    }
}


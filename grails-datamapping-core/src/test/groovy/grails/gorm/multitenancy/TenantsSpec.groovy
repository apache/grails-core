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
package grails.gorm.multitenancy

import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.core.Session
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.core.connections.ConnectionSources
import org.grails.datastore.mapping.core.exceptions.ConfigurationException
import org.grails.datastore.mapping.multitenancy.AllTenantsResolver
import org.grails.datastore.mapping.multitenancy.MultiTenancySettings
import org.grails.datastore.mapping.multitenancy.MultiTenantCapableDatastore
import org.grails.datastore.mapping.multitenancy.TenantResolver
import org.grails.datastore.mapping.multitenancy.exceptions.TenantNotFoundException
import spock.lang.Specification

/**
 * No spec existed for this class at all. It is a pile of thin static wrapper methods around
 * {@code Tenants.datastoreLocator} (a swappable static field, restored after every test here)
 * plus two large methods with real dispatch logic: {@code withId(MultiTenantCapableDatastore, ...)}
 * (shared-connection vs new-session, keyed by closure arity) and
 * {@code eachTenant(MultiTenantCapableDatastore, ...)} (DATABASE vs shared-connection modes).
 */
class TenantsSpec extends Specification {

    private Tenants.DatastoreLocator originalLocator

    void setup() {
        originalLocator = Tenants.datastoreLocator
    }

    void cleanup() {
        Tenants.datastoreLocator = originalLocator
    }

    private void useLocator(Datastore datastore, Datastore byType = null, Datastore forDomain = null) {
        Tenants.datastoreLocator = new Tenants.DatastoreLocator() {
            @Override Datastore getDatastore() { datastore }
            @Override Datastore getDatastore(Class<? extends Datastore> cls) { byType }
            @Override Datastore getDatastoreForDomain(Class domainClass) { forDomain ?: datastore }
        }
    }

    void "withTenant(tenantId, callable) binds the tenant on the datastore instance for the duration of the call"() {
        given:
        def ds = Stub(Datastore)
        useLocator(ds)
        def capturedDuringCall = null

        when:
        def result = Tenants.withTenant('tenant1') {
            capturedDuringCall = CurrentTenantHolder.get(ds)
            'ran'
        }

        then:
        result == 'ran'
        capturedDuringCall == 'tenant1'
        CurrentTenantHolder.get(ds) == null
    }

    void "eachTenant(callable) delegates to the located datastore, requiring multi-tenancy support"() {
        given:
        def mtds = Stub(MixedDatastore) {
            getMultiTenancyMode() >> MultiTenancySettings.MultiTenancyMode.DATABASE
            getTenantResolver() >> Stub(AllTenantsResolver) { resolveTenantIds() >> [] }
        }
        useLocator(mtds)

        when:
        Tenants.eachTenant { }

        then:
        notThrown(Exception)
    }

    void "eachTenant(callable) throws for a datastore that does not support multi-tenancy"() {
        given:
        useLocator(Stub(Datastore))

        when:
        Tenants.eachTenant { }

        then:
        thrown(UnsupportedOperationException)
    }

    void "eachTenant(datastoreClass, callable) resolves the datastore by type before delegating"() {
        given:
        def mtds = Stub(MixedDatastore) {
            getMultiTenancyMode() >> MultiTenancySettings.MultiTenancyMode.DATABASE
            getTenantResolver() >> Stub(AllTenantsResolver) { resolveTenantIds() >> [] }
        }
        useLocator(Stub(Datastore), mtds)

        when:
        Tenants.eachTenant(MixedDatastore, { })

        then:
        notThrown(Exception)
    }

    void "currentId() delegates to the multi-tenant-capable located datastore"() {
        given:
        def mtds = Stub(MixedDatastore)
        useLocator(mtds)
        CurrentTenantHolder.set(mtds, 'tenant1')

        expect:
        Tenants.currentId() == 'tenant1'

        cleanup:
        CurrentTenantHolder.remove(mtds)
    }

    void "currentId() throws for a datastore that does not support multi-tenancy"() {
        given:
        useLocator(Stub(Datastore))

        when:
        Tenants.currentId()

        then:
        thrown(UnsupportedOperationException)
    }

    void "currentId(MultiTenantCapableDatastore) returns the bound tenant when present"() {
        given:
        def mtds = Stub(MixedDatastore)
        CurrentTenantHolder.set(mtds, 'bound-tenant')

        expect:
        Tenants.currentId(mtds) == 'bound-tenant'

        cleanup:
        CurrentTenantHolder.remove(mtds)
    }

    void "currentId(MultiTenantCapableDatastore) resolves via the TenantResolver when no tenant is bound"() {
        given:
        def mtds = Stub(MixedDatastore) {
            getTenantResolver() >> Stub(TenantResolver) { resolveTenantIdentifier() >> 'resolved-tenant' }
        }

        expect:
        Tenants.currentId(mtds) == 'resolved-tenant'
    }

    void "currentId(datastoreClass) resolves the datastore by type before delegating"() {
        given:
        def mtds = Stub(MixedDatastore)
        useLocator(Stub(Datastore), mtds)
        CurrentTenantHolder.set(mtds, 'tenant1')

        expect:
        Tenants.currentId(MixedDatastore) == 'tenant1'

        cleanup:
        CurrentTenantHolder.remove(mtds)
    }

    void "currentId(datastoreClass) throws for a datastore that does not support multi-tenancy"() {
        given:
        useLocator(Stub(Datastore), Stub(Datastore))

        when:
        Tenants.currentId(Datastore)

        then:
        thrown(UnsupportedOperationException)
    }

    void "withoutId(callable) delegates to the located datastore, requiring multi-tenancy support"() {
        given:
        def mtds = Stub(MixedDatastore) {
            getMultiTenancyMode() >> MultiTenancySettings.MultiTenancyMode.DISCRIMINATOR
            withSession(_) >> { Closure c -> c.call(Stub(Session)) }
        }
        useLocator(mtds)

        expect:
        Tenants.withoutId { -> 'ran' } == 'ran'
    }

    void "withoutId(callable) throws for a datastore that does not support multi-tenancy"() {
        given:
        useLocator(Stub(Datastore))

        when:
        Tenants.withoutId { }

        then:
        thrown(UnsupportedOperationException)
    }

    void "withCurrent(callable) resolves the current tenant then delegates to withId, requiring multi-tenancy support"() {
        given:
        def mtds = Stub(MixedDatastore) {
            getMultiTenancyMode() >> MultiTenancySettings.MultiTenancyMode.DATABASE
            withNewSession(_, _) >> { Serializable tid, Closure c -> c.call(Stub(Session)) }
        }
        useLocator(mtds)
        CurrentTenantHolder.set(mtds, 'tenant1')

        expect:
        Tenants.withCurrent { -> 'ran' } == 'ran'

        cleanup:
        CurrentTenantHolder.remove(mtds)
    }

    void "withCurrent(callable) throws for a datastore that does not support multi-tenancy"() {
        given:
        useLocator(Stub(Datastore))

        when:
        Tenants.withCurrent { }

        then:
        thrown(UnsupportedOperationException)
    }

    void "withCurrent(datastoreClass, callable) resolves the datastore by type before delegating"() {
        given:
        def mtds = Stub(MixedDatastore) {
            getMultiTenancyMode() >> MultiTenancySettings.MultiTenancyMode.DATABASE
            withNewSession(_, _) >> { Serializable tid, Closure c -> c.call(Stub(Session)) }
        }
        useLocator(Stub(Datastore), mtds)
        CurrentTenantHolder.set(mtds, 'tenant1')

        expect:
        Tenants.withCurrent(MixedDatastore) { -> 'ran' } == 'ran'

        cleanup:
        CurrentTenantHolder.remove(mtds)
    }

    void "withCurrent(datastoreClass, callable) throws for a datastore that does not support multi-tenancy"() {
        given:
        useLocator(Stub(Datastore), Stub(Datastore))

        when:
        Tenants.withCurrent(Datastore) { }

        then:
        thrown(UnsupportedOperationException)
    }

    void "withId(tenantId, callable) delegates to the located datastore, requiring multi-tenancy support"() {
        given:
        def mtds = Stub(MixedDatastore) {
            getMultiTenancyMode() >> MultiTenancySettings.MultiTenancyMode.DATABASE
            withNewSession(_, _) >> { Serializable tid, Closure c -> c.call(Stub(Session)) }
        }
        useLocator(mtds)

        expect:
        Tenants.withId('tenant1') { -> 'ran' } == 'ran'
    }

    void "withId(tenantId, callable) throws for a datastore that does not support multi-tenancy"() {
        given:
        useLocator(Stub(Datastore))

        when:
        Tenants.withId('tenant1') { }

        then:
        thrown(UnsupportedOperationException)
    }

    void "withId(domainClass, tenantId, callable) resolves the datastore for that domain class before delegating"() {
        given:
        def mtds = Stub(MixedDatastore) {
            getMultiTenancyMode() >> MultiTenancySettings.MultiTenancyMode.DATABASE
            withNewSession(_, _) >> { Serializable tid, Closure c -> c.call(Stub(Session)) }
        }
        useLocator(Stub(Datastore), null, mtds)

        expect:
        Tenants.withId(String, 'tenant1') { -> 'ran' } == 'ran'
    }

    void "withId(domainClass, tenantId, callable) throws for a datastore that does not support multi-tenancy"() {
        given:
        useLocator(Stub(Datastore), null, Stub(Datastore))

        when:
        Tenants.withId(String, 'tenant1') { }

        then:
        thrown(UnsupportedOperationException)
    }

    void "withTenant(domainClass, tenantId, callable) binds the tenant for the datastore resolved for that domain class"() {
        given:
        def ds = Stub(Datastore)
        useLocator(Stub(Datastore), null, ds)

        expect:
        Tenants.withTenant(String, 'tenant1') { CurrentTenantHolder.get(ds) } == 'tenant1'
    }

    void "withoutId(MultiTenantCapableDatastore, callable) runs a 0-arg closure directly for a shared-connection mode"() {
        given:
        def mtds = Stub(MixedDatastore) {
            getMultiTenancyMode() >> MultiTenancySettings.MultiTenancyMode.SCHEMA
        }

        expect:
        Tenants.withoutId(mtds) { -> 'ran' } == 'ran'
    }

    void "withoutId(MultiTenantCapableDatastore, callable) runs a >0-arg closure via withSession for a shared-connection mode"() {
        given:
        def session = Stub(Session)
        def mtds = Stub(MixedDatastore) {
            getMultiTenancyMode() >> MultiTenancySettings.MultiTenancyMode.DISCRIMINATOR
            withSession(_) >> { Closure c -> c.call(session) }
        }

        expect:
        Tenants.withoutId(mtds) { Session s -> s.is(session) }
    }

    void "withoutId(MultiTenantCapableDatastore, callable) dispatches by closure arity for a non-shared-connection mode"() {
        given:
        def session = Stub(Session)
        def mtds = Stub(MixedDatastore) {
            getMultiTenancyMode() >> MultiTenancySettings.MultiTenancyMode.DATABASE
            withNewSession(ConnectionSource.DEFAULT, _) >> { String c, Closure body -> body.call(session) }
        }

        expect:
        Tenants.withoutId(mtds) { -> 'zero' } == 'zero'
        Tenants.withoutId(mtds) { String tid -> tid } == ConnectionSource.DEFAULT
        Tenants.withoutId(mtds) { String tid, Session s -> [tid, s] } == [ConnectionSource.DEFAULT, session]
    }

    void "withoutId(MultiTenantCapableDatastore, callable) rejects a closure that accepts too many arguments"() {
        given:
        def mtds = Stub(MixedDatastore) {
            getMultiTenancyMode() >> MultiTenancySettings.MultiTenancyMode.DATABASE
            withNewSession(ConnectionSource.DEFAULT, _) >> { String c, Closure body -> body.call(Stub(Session)) }
        }

        when:
        Tenants.withoutId(mtds) { a, b, c -> }

        then:
        thrown(IllegalArgumentException)
    }

    void "withId(MultiTenantCapableDatastore, tenantId, callable) reuses an already-bound child session, dispatching by closure arity"() {
        given:
        def childSession = Stub(Session)
        def childDs = Stub(Datastore) {
            hasCurrentSession() >> true
            getCurrentSession() >> childSession
        }
        def mtds = Stub(MixedDatastore) {
            getMultiTenancyMode() >> MultiTenancySettings.MultiTenancyMode.DATABASE
            getDatastoreForTenantId('tenant1') >> childDs
        }

        expect:
        Tenants.withId(mtds, 'tenant1') { -> 'zero' } == 'zero'
        Tenants.withId(mtds, 'tenant1') { String tid -> tid } == 'tenant1'
        Tenants.withId(mtds, 'tenant1') { String tid, Session s -> [tid, s] } == ['tenant1', childSession]
    }

    void "withId(MultiTenantCapableDatastore, tenantId, callable) rejects too many arguments on the already-bound-session fast path"() {
        given:
        def childDs = Stub(Datastore) {
            hasCurrentSession() >> true
            getCurrentSession() >> Stub(Session)
        }
        def mtds = Stub(MixedDatastore) {
            getMultiTenancyMode() >> MultiTenancySettings.MultiTenancyMode.DATABASE
            getDatastoreForTenantId('tenant1') >> childDs
        }

        when:
        Tenants.withId(mtds, 'tenant1') { a, b, c -> }

        then:
        thrown(IllegalArgumentException)
    }

    void "withId(MultiTenantCapableDatastore, tenantId, callable) swallows a ConfigurationException from getDatastoreForTenantId and falls through"() {
        given: "an unknown tenant/connection name, matching what H5/H7/Mongo's getDatastoreForConnection actually throws"
        def session = Stub(Session)
        def mtds = Stub(MixedDatastore) {
            getMultiTenancyMode() >> MultiTenancySettings.MultiTenancyMode.DATABASE
            getDatastoreForTenantId('tenant1') >> { throw new ConfigurationException('boom') }
            withNewSession('tenant1', _) >> { String tid, Closure body -> body.call(session) }
        }

        expect:
        Tenants.withId(mtds, 'tenant1') { -> 'ran' } == 'ran'
    }

    void "withId(MultiTenantCapableDatastore, tenantId, callable) swallows a TenantException from getDatastoreForTenantId and falls through"() {
        given:
        def session = Stub(Session)
        def mtds = Stub(MixedDatastore) {
            getMultiTenancyMode() >> MultiTenancySettings.MultiTenancyMode.DATABASE
            getDatastoreForTenantId('tenant1') >> { throw new TenantNotFoundException('boom') }
            withNewSession('tenant1', _) >> { String tid, Closure body -> body.call(session) }
        }

        expect:
        Tenants.withId(mtds, 'tenant1') { -> 'ran' } == 'ran'
    }

    void "withId(MultiTenantCapableDatastore, tenantId, callable) does not swallow an unrelated exception from getDatastoreForTenantId"() {
        given: "a failure that is not a known tenant-resolution error should not be silently hidden"
        def mtds = Stub(MixedDatastore) {
            getMultiTenancyMode() >> MultiTenancySettings.MultiTenancyMode.DATABASE
            getDatastoreForTenantId('tenant1') >> { throw new IllegalStateException('boom') }
        }

        when:
        Tenants.withId(mtds, 'tenant1') { -> 'ran' }

        then:
        thrown(IllegalStateException)
    }

    void "withId(MultiTenantCapableDatastore, tenantId, callable) uses the shared-connection withSession path, dispatching by closure arity"() {
        given:
        def session = Stub(Session)
        def mtds = Stub(MixedDatastore) {
            getMultiTenancyMode() >> MultiTenancySettings.MultiTenancyMode.SCHEMA
            withSession(_) >> { Closure c -> c.call(session) }
        }

        expect:
        Tenants.withId(mtds, 'tenant1') { -> 'zero' } == 'zero'
        Tenants.withId(mtds, 'tenant1') { String tid -> tid } == 'tenant1'
        Tenants.withId(mtds, 'tenant1') { String tid, Session s -> [tid, s] } == ['tenant1', session]
    }

    void "withId(MultiTenantCapableDatastore, tenantId, callable) rejects too many arguments on the shared-connection non-2-arg path"() {
        given:
        def mtds = Stub(MixedDatastore) {
            getMultiTenancyMode() >> MultiTenancySettings.MultiTenancyMode.SCHEMA
        }

        when:
        Tenants.withId(mtds, 'tenant1') { a, b, c -> }

        then:
        thrown(IllegalArgumentException)
    }

    void "withId(MultiTenantCapableDatastore, tenantId, callable) uses the non-shared withNewSession path, dispatching by closure arity"() {
        given:
        def session = Stub(Session)
        def mtds = Stub(MixedDatastore) {
            getMultiTenancyMode() >> MultiTenancySettings.MultiTenancyMode.DATABASE
            withNewSession('tenant1', _) >> { String tid, Closure body -> body.call(session) }
        }

        expect:
        Tenants.withId(mtds, 'tenant1') { -> 'zero' } == 'zero'
        Tenants.withId(mtds, 'tenant1') { String tid -> tid } == 'tenant1'
        Tenants.withId(mtds, 'tenant1') { String tid, Session s -> [tid, s] } == ['tenant1', session]
    }

    void "withId(MultiTenantCapableDatastore, tenantId, callable) rejects too many arguments on the non-shared withNewSession path"() {
        given:
        def mtds = Stub(MixedDatastore) {
            getMultiTenancyMode() >> MultiTenancySettings.MultiTenancyMode.DATABASE
            withNewSession('tenant1', _) >> { String tid, Closure body -> body.call(Stub(Session)) }
        }

        when:
        Tenants.withId(mtds, 'tenant1') { a, b, c -> }

        then:
        thrown(IllegalArgumentException)
    }

    void "eachTenant(MultiTenantCapableDatastore, callable) iterates AllTenantsResolver's ids in DATABASE mode"() {
        given:
        def visited = []
        def childDs = Stub(Datastore)
        def mtds = Stub(MixedDatastore) {
            getMultiTenancyMode() >> MultiTenancySettings.MultiTenancyMode.DATABASE
            getTenantResolver() >> Stub(AllTenantsResolver) { resolveTenantIds() >> ['t1', 't2'] }
            getDatastoreForTenantId(_) >> childDs
            withNewSession(_, _) >> { String tid, Closure body -> body.call(Stub(Session)) }
        }

        when:
        Tenants.eachTenant(mtds) { String tid -> visited << tid }

        then:
        visited == ['t1', 't2']
    }

    void "eachTenant(MultiTenantCapableDatastore, callable) iterates non-DEFAULT connection sources in DATABASE mode without an AllTenantsResolver"() {
        given:
        def visited = []
        def connectionSources = Stub(ConnectionSources) {
            getAllConnectionSources() >> [
                    Stub(ConnectionSource) { getName() >> ConnectionSource.DEFAULT },
                    Stub(ConnectionSource) { getName() >> 'secondary' },
            ]
        }
        def mtds = Stub(MixedDatastore) {
            getMultiTenancyMode() >> MultiTenancySettings.MultiTenancyMode.DATABASE
            getTenantResolver() >> Stub(TenantResolver)
            getConnectionSources() >> connectionSources
            withNewSession(_, _) >> { String tid, Closure body -> body.call(Stub(Session)) }
        }

        when:
        Tenants.eachTenant(mtds) { String tid -> visited << tid }

        then:
        visited == ['secondary']
    }

    void "eachTenant(MultiTenantCapableDatastore, callable) iterates AllTenantsResolver's ids for a shared-connection mode"() {
        given:
        def visited = []
        def mtds = Stub(MixedDatastore) {
            getMultiTenancyMode() >> MultiTenancySettings.MultiTenancyMode.SCHEMA
            getTenantResolver() >> Stub(AllTenantsResolver) { resolveTenantIds() >> ['t1'] }
            withSession(_) >> { Closure c -> c.call(Stub(Session)) }
        }

        when:
        Tenants.eachTenant(mtds) { String tid -> visited << tid }

        then:
        visited == ['t1']
    }

    void "eachTenant(MultiTenantCapableDatastore, callable) throws for a shared-connection mode without an AllTenantsResolver"() {
        given:
        def mtds = Stub(MixedDatastore) {
            getMultiTenancyMode() >> MultiTenancySettings.MultiTenancyMode.SCHEMA
            getTenantResolver() >> Stub(TenantResolver)
        }

        when:
        Tenants.eachTenant(mtds) { }

        then:
        thrown(UnsupportedOperationException)
    }

    void "eachTenant(MultiTenantCapableDatastore, callable) throws for an unsupported multi tenancy mode"() {
        given:
        def mtds = Stub(MixedDatastore) {
            getMultiTenancyMode() >> MultiTenancySettings.MultiTenancyMode.NONE
        }

        when:
        Tenants.eachTenant(mtds) { }

        then:
        thrown(UnsupportedOperationException)
    }

    interface MixedDatastore extends MultiTenantCapableDatastore, Datastore {}
}

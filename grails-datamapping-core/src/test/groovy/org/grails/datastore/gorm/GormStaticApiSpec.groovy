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

import grails.gorm.annotation.Entity
import grails.gorm.multitenancy.Tenants
import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.core.Session
import org.grails.datastore.mapping.core.SessionCallback
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.core.connections.ConnectionSources
import org.grails.datastore.mapping.core.connections.ConnectionSourcesProvider
import org.grails.datastore.mapping.core.connections.MultipleConnectionSourceCapableDatastore
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.multitenancy.MultiTenancySettings
import org.grails.datastore.mapping.multitenancy.MultiTenantCapableDatastore
import org.grails.datastore.mapping.simple.SimpleMapDatastore
import org.grails.datastore.mapping.transactions.TransactionCapableDatastore
import spock.lang.AutoCleanup
import spock.lang.Specification

class GormStaticApiSpec extends Specification {

    @AutoCleanup
    SimpleMapDatastore datastore = new SimpleMapDatastore(GormStaticApiThing)

    void "the deprecated 2-arg Datastore constructor wires the mapping context and default qualifier"() {
        when:
        def api = new GormStaticApi(GormStaticApiThing, datastore, [])

        then:
        api.qualifier == ConnectionSource.DEFAULT
        api.getGormPersistentEntity() != null
    }

    void "the deprecated 3-arg Datastore+transactionManager constructor wires the mapping context"() {
        when:
        def api = new GormStaticApi(GormStaticApiThing, datastore, [], datastore.transactionManager)

        then:
        api.getGormPersistentEntity() != null
    }

    void "the MappingContext-only constructor defaults the qualifier to DEFAULT"() {
        when:
        def api = new GormStaticApi(GormStaticApiThing, datastore.mappingContext, [])

        then:
        api.qualifier == ConnectionSource.DEFAULT
    }

    void "the qualifier constructor stores the given qualifier"() {
        when:
        def api = new GormStaticApi(GormStaticApiThing, datastore.mappingContext, [], 'secondary')

        then:
        api.qualifier == 'secondary'
    }

    void "getTransactionManager returns the datastore's transaction manager when it is transaction-capable"() {
        given:
        def api = new GormStaticApi(GormStaticApiThing, datastore, [])

        expect:
        api.getTransactionManager() == datastore.transactionManager
    }

    void "getTransactionManager returns null when the datastore is not transaction-capable"() {
        given:
        def nonTransactionalDs = Stub(Datastore) {
            getMappingContext() >> datastore.mappingContext
        }
        def api = new GormStaticApi(GormStaticApiThing, nonTransactionalDs, [])

        expect:
        api.getTransactionManager() == null
    }

    void "executeQualified runs directly on this datastore when no distinct qualified api is registered"() {
        given:
        def api = new GormStaticApi(GormStaticApiThing, datastore, [])

        expect:
        api.executeQualified(ConnectionSource.DEFAULT, { Session session -> 'ran' }) == 'ran'
    }

    void "getGormDynamicFinders returns the finders the api was constructed with"() {
        given:
        def finder = Stub(org.grails.datastore.gorm.finders.FinderMethod)
        def api = new GormStaticApi(GormStaticApiThing, datastore, [finder])

        expect:
        api.getGormDynamicFinders() == [finder]
    }

    void "forQualifier creates a new static api bound to the requested qualifier"() {
        given:
        def api = new GormStaticApi(GormStaticApiThing, datastore, [])

        when:
        def qualified = api.forQualifier(ConnectionSource.DEFAULT)

        then:
        qualified instanceof GormStaticApi
        qualified.qualifier == ConnectionSource.DEFAULT
    }

    void "methodMissing invokes a matching dynamic finder"() {
        given:
        def finder = Mock(org.grails.datastore.gorm.finders.FinderMethod)
        finder.isMethodMatch('findByThing') >> true
        def api = new GormStaticApi(GormStaticApiThing, datastore, [finder])

        when:
        api.methodMissing('findByThing', ['x'] as Object[])

        then:
        1 * finder.invoke(GormStaticApiThing, 'findByThing', ['x'] as Object[]) >> null
    }

    void "methodMissing throws MissingMethodException when no finder matches"() {
        given:
        def api = new GormStaticApi(GormStaticApiThing, datastore, [])

        when:
        api.methodMissing('doesNotExist', [] as Object[])

        then:
        thrown(MissingMethodException)
    }

    void "propertyMissing getter returns a closure that invokes a matching dynamic finder"() {
        given:
        def finder = Mock(org.grails.datastore.gorm.finders.FinderMethod)
        finder.isMethodMatch('findByThing') >> true
        def api = new GormStaticApi(GormStaticApiThing, datastore, [finder])

        when:
        def result = api.propertyMissing('findByThing')

        then:
        result instanceof Closure

        when:
        result.call()

        then:
        1 * finder.invoke(GormStaticApiThing, 'findByThing', [] as Object[]) >> null
    }

    void "propertyMissing getter resolves a known connection source name to a qualified static api"() {
        given:
        def connectionSources = Stub(ConnectionSources) {
            getConnectionSource(ConnectionSource.DEFAULT) >> Stub(ConnectionSource)
        }
        def dsWithConnections = Stub(MultipleConnectionSourceDatastoreForTest) {
            getMappingContext() >> datastore.mappingContext
            getConnectionSources() >> connectionSources
        }
        def api = new GormStaticApi(GormStaticApiThing, dsWithConnections, [])

        when:
        def result = api.propertyMissing(ConnectionSource.DEFAULT)

        then:
        result instanceof GormStaticApi
        result.qualifier == ConnectionSource.DEFAULT
    }

    void "propertyMissing getter falls back to the registry when the datastore does not expose the qualifier directly"() {
        given:
        def api = new GormStaticApi(GormStaticApiThing, datastore, [])

        when:
        def result = api.propertyMissing(ConnectionSource.DEFAULT)

        then:
        result instanceof GormStaticApi
    }

    void "propertyMissing getter throws MissingPropertyException when nothing resolves the name"() {
        given:
        def api = new GormStaticApi(GormStaticApiThing, datastore, [])

        when:
        api.propertyMissing('doesNotExist')

        then:
        thrown(MissingPropertyException)
    }

    void "propertyMissing setter always throws MissingPropertyException"() {
        given:
        def api = new GormStaticApi(GormStaticApiThing, datastore, [])

        when:
        api.propertyMissing('name', (Object) 'value')

        then:
        thrown(MissingPropertyException)
    }

    void "instance-operation delegation methods route through the registry's instance api"() {
        given:
        def api = new GormStaticApi(GormStaticApiThing, datastore, [])
        def instance = new GormStaticApiThing(name: 'a')

        expect:
        api.save(instance) != null
        api.instanceOf(instance, GormStaticApiThing)
        api.ident(instance) != null

        when: "attachment is checked within the same session the instance was saved in"
        def attachedThenDiscarded = api.withSession { session ->
            session.persist(instance)
            boolean attachedBeforeDiscard = api.isAttached(instance)
            api.discard(instance)
            [attachedBeforeDiscard, api.isAttached(instance)]
        }

        then:
        attachedThenDiscarded == [true, false]
    }

    void "get/read/load/proxy/exists resolve a persisted instance by id"() {
        given:
        def api = new GormStaticApi(GormStaticApiThing, datastore, [])
        def saved = new GormStaticApiThing(name: 'a').save(flush: true)

        expect:
        api.get(saved.id) != null
        api.read(saved.id) != null
        api.load(saved.id) != null
        api.proxy(saved.id) != null
        api.exists(saved.id)
        !api.exists(-999L)
    }

    void "getAll resolves multiple persisted instances by varargs and iterable ids"() {
        given:
        def api = new GormStaticApi(GormStaticApiThing, datastore, [])
        def one = new GormStaticApiThing(name: 'one').save(flush: true)
        def two = new GormStaticApiThing(name: 'two').save(flush: true)

        expect:
        api.getAll(one.id, two.id)*.name.sort() == ['one', 'two']
        api.getAll([one.id, two.id])*.name.sort() == ['one', 'two']
        api.getAll()*.name.sort() == ['one', 'two']
    }

    void "list and list(Map) support the max parameter via a paged result list"() {
        given:
        def api = new GormStaticApi(GormStaticApiThing, datastore, [])
        (1..3).each { new GormStaticApiThing(name: "n$it").save(flush: true) }

        expect:
        api.list().size() == 3
        api.list(max: 2).size() <= 2
    }

    void "count and getCount return the number of persisted instances"() {
        given:
        def api = new GormStaticApi(GormStaticApiThing, datastore, [])
        new GormStaticApiThing(name: 'a').save(flush: true)
        new GormStaticApiThing(name: 'b').save(flush: true)

        expect:
        api.count() == 2
        api.getCount() == 2
    }

    void "first and last resolve the boundary instances by identity, with and without an explicit sort"() {
        given:
        def api = new GormStaticApi(GormStaticApiThing, datastore, [])
        def a = new GormStaticApiThing(name: 'a').save(flush: true)
        def b = new GormStaticApiThing(name: 'b').save(flush: true)

        expect:
        api.first() != null
        api.first('name') != null
        api.first(sort: 'name') != null
        api.last() != null
        api.last('name') != null
        api.last(sort: 'name') != null
    }

    void "createCriteria and withCriteria build and execute a criteria query"() {
        given:
        def api = new GormStaticApi(GormStaticApiThing, datastore, [])
        new GormStaticApiThing(name: 'findme').save(flush: true)

        expect:
        api.createCriteria() != null
        api.withCriteria { eq('name', 'findme') }.size() == 1
        api.withCriteria([:]) { eq('name', 'findme') }.size() == 1
    }

    void "where, whereLazy and whereAny build detached criteria against this api's entity"() {
        given:
        def api = new GormStaticApi(GormStaticApiThing, datastore, [])
        new GormStaticApiThing(name: 'a').save(flush: true)

        expect:
        api.where { eq('name', 'a') }.count() == 1
        api.whereLazy { eq('name', 'a') }.count() == 1
        api.whereAny { eq('name', 'a') }.count() == 1
    }

    void "saveAll persists a varargs and an iterable batch without forcing a flush of its own"() {
        given:
        def api = new GormStaticApi(GormStaticApiThing, datastore, [])

        when: "saveAll runs inside a bound session, like the baseline no-flush contract expects"
        def varargsIds = null
        def iterableIds = null
        api.withNewSession { Session session ->
            varargsIds = api.saveAll(new GormStaticApiThing(name: 'a'), new GormStaticApiThing(name: 'b'))
            iterableIds = api.saveAll([new GormStaticApiThing(name: 'c')])
            session.flush()
        }

        then: "the persisted ids are returned and the flushed batch is visible afterwards"
        varargsIds.size() == 2
        iterableIds.size() == 1
        api.count() == 3
    }

    void "deleteAll removes every persisted instance of the entity"() {
        given:
        def api = new GormStaticApi(GormStaticApiThing, datastore, [])
        new GormStaticApiThing(name: 'a').save(flush: true)
        new GormStaticApiThing(name: 'b').save(flush: true)

        when:
        api.deleteAll()

        then:
        api.count() == 0
    }

    void "deleteAll(Map) deletes every instance and reports how many"() {
        given:
        def api = new GormStaticApi(GormStaticApiThing, datastore, [])
        new GormStaticApiThing(name: 'a').save(flush: true)
        new GormStaticApiThing(name: 'b').save(flush: true)

        when:
        def deleted = api.deleteAll([:])

        then:
        deleted == 2
        api.count() == 0
    }

    void "deleteAll(Map) honours the flush argument"() {
        given:
        def session = Mock(Session)
        def api = new GormStaticApi(GormStaticApiThing, datastore, []) {
            @Override protected Object execute(SessionCallback callback) { callback.doInSession(session) }
        }

        when:
        api.deleteAll([flush: true])

        then:
        1 * session.deleteAll(_) >> 3
        1 * session.flush()

        when:
        api.deleteAll([:])

        then: "without flush: true the session is left to the caller's flush policy"
        1 * session.deleteAll(_) >> 3
        0 * session.flush()
    }

    void "deleteAll(Iterable) and deleteAll(varargs) remove exactly the given instances"() {
        given:
        def api = new GormStaticApi(GormStaticApiThing, datastore, [])
        def a = new GormStaticApiThing(name: 'a').save(flush: true)
        def b = new GormStaticApiThing(name: 'b').save(flush: true)
        def c = new GormStaticApiThing(name: 'c').save(flush: true)

        when:
        api.deleteAll([a])

        then:
        api.count() == 2

        when:
        api.deleteAll(b, c)

        then:
        api.count() == 0
    }

    void "deleteAll(Map, Iterable) flushes when requested and deleteAll(Map, varargs) delegates to it"() {
        given:
        def api = new GormStaticApi(GormStaticApiThing, datastore, [])
        def a = new GormStaticApiThing(name: 'a').save(flush: true)
        def b = new GormStaticApiThing(name: 'b').save(flush: true)

        when:
        api.deleteAll([flush: true], [a])

        then:
        api.count() == 1

        when:
        api.deleteAll([:], b)

        then:
        api.count() == 0
    }

    void "create builds a new unsaved instance"() {
        given:
        def api = new GormStaticApi(GormStaticApiThing, datastore, [])

        expect:
        api.create() instanceof GormStaticApiThing
    }

    void "findAll family resolves persisted instances by plain, example and closure criteria"() {
        given:
        def api = new GormStaticApi(GormStaticApiThing, datastore, [])
        new GormStaticApiThing(name: 'a').save(flush: true)
        new GormStaticApiThing(name: 'b').save(flush: true)

        expect:
        api.findAll().size() == 2
        api.findAll([:]).size() == 2
        api.findAll(new GormStaticApiThing(name: 'a')).size() == 1
        api.findAll(new GormStaticApiThing(name: 'a'), [:]).size() == 1
        api.findAll { eq('name', 'a') }.size() == 1
        api.findAll([:]) { eq('name', 'a') }.size() == 1
    }

    void "find family resolves a single instance by example and by closure"() {
        given:
        def api = new GormStaticApi(GormStaticApiThing, datastore, [])
        new GormStaticApiThing(name: 'a').save(flush: true)

        expect:
        api.find(new GormStaticApiThing(name: 'a')) != null
        api.find(new GormStaticApiThing(name: 'a'), [:]) != null
        api.find { eq('name', 'a') } != null
    }

    void "createQueryMapForExample prefers a non-null identity and falls back to simple persistent properties"() {
        given:
        def api = new GormStaticApi(GormStaticApiThing, datastore, [])
        def withId = new GormStaticApiThing(name: 'a').save(flush: true)

        expect:
        api.createQueryMapForExample(withId) == [id: withId.id]
        api.createQueryMapForExample(new GormStaticApiThing(name: 'unsaved')) == [name: 'unsaved']
    }

    void "findWhere and findAllWhere resolve instances via an equality query map"() {
        given:
        def api = new GormStaticApi(GormStaticApiThing, datastore, [])
        new GormStaticApiThing(name: 'a').save(flush: true)

        expect:
        api.findWhere([name: 'a']) != null
        api.findWhere([name: 'a'], [:]) != null
        api.findAllWhere([name: 'a']).size() == 1
        api.findAllWhere([name: 'a'], [:]).size() == 1
    }

    void "findOrCreateWhere returns the existing instance or builds (but does not persist) a new one"() {
        given:
        def api = new GormStaticApi(GormStaticApiThing, datastore, [])
        new GormStaticApiThing(name: 'existing').save(flush: true)

        expect:
        api.findOrCreateWhere([name: 'existing']).name == 'existing'

        when:
        def created = api.findOrCreateWhere([name: 'brand-new'])

        then:
        created.name == 'brand-new'
        created.id == null
    }

    void "findOrSaveWhere returns the existing instance or persists a new one"() {
        given:
        def api = new GormStaticApi(GormStaticApiThing, datastore, [])
        new GormStaticApiThing(name: 'existing').save(flush: true)

        expect:
        api.findOrSaveWhere([name: 'existing']).name == 'existing'

        when:
        def created = api.findOrSaveWhere([name: 'brand-new'])

        then:
        created.id != null
    }

    void "withTransaction and withNewTransaction execute the callback within a transaction template"() {
        given:
        def api = new GormStaticApi(GormStaticApiThing, datastore, [])

        expect:
        api.withTransaction { 'ran' } == 'ran'
        api.withNewTransaction { 'ran-new' } == 'ran-new'
    }

    void "withTransaction(Map) applies valid transaction properties and rejects unknown ones"() {
        given:
        def api = new GormStaticApi(GormStaticApiThing, datastore, [])

        expect:
        api.withTransaction([readOnly: true]) { 'ran' } == 'ran'
        api.withNewTransaction([readOnly: true]) { 'ran' } == 'ran'

        when:
        api.withTransaction([notARealProperty: true]) { 'ran' }

        then:
        thrown(IllegalArgumentException)
    }

    void "withTransaction(TransactionDefinition) executes the callback with an explicit definition"() {
        given:
        def api = new GormStaticApi(GormStaticApiThing, datastore, [])
        def definition = new org.springframework.transaction.support.DefaultTransactionDefinition()

        expect:
        api.withTransaction(definition) { 'ran' } == 'ran'
    }

    void "withNewSession and withStatelessSession execute the callback with a fresh session"() {
        given:
        def api = new GormStaticApi(GormStaticApiThing, datastore, [])

        expect:
        api.withNewSession { Session session -> session != null } == true
        api.withStatelessSession { Session session -> session != null } == true
    }

    void "executeQuery/executeUpdate/find/findAll collection and varargs overloads report unsupported"() {
        given:
        def api = new GormStaticApi(GormStaticApiThing, datastore, [])

        when:
        api.executeQuery('q', [])

        then:
        thrown(UnsupportedOperationException)

        when:
        api.executeQuery('q', 'p1')

        then:
        thrown(UnsupportedOperationException)

        when:
        api.executeUpdate('q', [])

        then:
        thrown(UnsupportedOperationException)

        when:
        api.executeUpdate('q', 'p1')

        then:
        thrown(UnsupportedOperationException)

        when:
        api.find('q', [])

        then:
        thrown(UnsupportedOperationException)

        when:
        api.find('q', ['p1'] as Object[])

        then:
        thrown(UnsupportedOperationException)

        when:
        api.findAll('q', [])

        then:
        thrown(UnsupportedOperationException)

        when:
        api.findAll('q', ['p1'] as Object[])

        then:
        thrown(UnsupportedOperationException)
    }

    void "withTenant(id) returns a qualified api bound to the given tenant"() {
        given:
        def mappingContext = datastore.mappingContext
        def defaultDs = Stub(MultiTenantDatastoreForTest) {
            getMappingContext() >> mappingContext
        }
        def registry = new GormRegistry()
        registry.registerEntityDatastore(GormStaticApiThing.name, ConnectionSource.DEFAULT, defaultDs)
        def resolver = new DatastoreResolver() {
            @Override Datastore resolve() { defaultDs }
        }
        def api = new GormStaticApi(GormStaticApiThing, mappingContext, [], resolver, ConnectionSource.DEFAULT, registry)

        when:
        def qualified = api.withTenant('tenant1')

        then:
        qualified instanceof GormStaticApi
        qualified.qualifier == 'tenant1'
    }

    void "eachTenant delegates to Tenants.eachTenant for a multi-tenant-capable datastore"() {
        given:
        def mappingContext = datastore.mappingContext
        def multiTenantDs = Stub(MultiTenantDatastoreForTest) {
            getMappingContext() >> mappingContext
            getMultiTenancyMode() >> MultiTenancySettings.MultiTenancyMode.DATABASE
            getTenantResolver() >> Stub(org.grails.datastore.mapping.multitenancy.AllTenantsResolver) {
                resolveTenantIds() >> []
            }
        }
        def registry = new GormRegistry()
        registry.registerEntityDatastore(GormStaticApiThing.name, ConnectionSource.DEFAULT, multiTenantDs)
        def api = new GormStaticApi(GormStaticApiThing, mappingContext, [], null, ConnectionSource.DEFAULT, registry)

        expect:
        api.eachTenant { }.is(api)
    }

    void "eachTenant throws UnsupportedOperationException for a non-multi-tenant datastore"() {
        given:
        def mappingContext = datastore.mappingContext
        def plainDs = Stub(Datastore) {
            getMappingContext() >> mappingContext
        }
        def registry = new GormRegistry()
        registry.registerEntityDatastore(GormStaticApiThing.name, ConnectionSource.DEFAULT, plainDs)
        def api = new GormStaticApi(GormStaticApiThing, mappingContext, [], null, ConnectionSource.DEFAULT, registry)

        when:
        api.eachTenant { }

        then:
        thrown(UnsupportedOperationException)
    }

    void "withId resolves via the multi-tenant-capable default datastore when available"() {
        given:
        def mappingContext = datastore.mappingContext
        def multiTenantDs = Stub(MultiTenantDatastoreForTest) {
            getMappingContext() >> mappingContext
            getMultiTenancyMode() >> MultiTenancySettings.MultiTenancyMode.DATABASE
            withNewSession(_, _) >> { Serializable tid, Closure body -> body.call(Stub(Session)) }
        }
        def registry = new GormRegistry()
        registry.registerEntityDatastore(GormStaticApiThing.name, ConnectionSource.DEFAULT, multiTenantDs)
        def api = new GormStaticApi(GormStaticApiThing, mappingContext, [], null, ConnectionSource.DEFAULT, registry)

        expect:
        api.withId('tenant1') { -> 'ran' } == 'ran'
    }

    void "withId falls back to resolving the specific tenant datastore for a non-multi-tenant default"() {
        given:
        def mappingContext = datastore.mappingContext
        def plainDs = Stub(Datastore) {
            getMappingContext() >> mappingContext
        }
        plainDs.connect() >> Stub(Session) {
            getDatastore() >> plainDs
        }
        def registry = new GormRegistry()
        registry.registerEntityDatastore(GormStaticApiThing.name, ConnectionSource.DEFAULT, plainDs)
        def api = new GormStaticApi(GormStaticApiThing, mappingContext, [], null, ConnectionSource.DEFAULT, registry)

        expect:
        api.withId(ConnectionSource.DEFAULT) { Session session -> 'ran' } == 'ran'
    }

    void "withoutId delegates to withId with the DEFAULT connection"() {
        given:
        def mappingContext = datastore.mappingContext
        def plainDs = Stub(Datastore) {
            getMappingContext() >> mappingContext
        }
        plainDs.connect() >> Stub(Session) {
            getDatastore() >> plainDs
        }
        def registry = new GormRegistry()
        registry.registerEntityDatastore(GormStaticApiThing.name, ConnectionSource.DEFAULT, plainDs)
        def api = new GormStaticApi(GormStaticApiThing, mappingContext, [], null, ConnectionSource.DEFAULT, registry)

        expect:
        api.withoutId { Session session -> 'ran' } == 'ran'
    }

    void "withNewSession(tenantId) resolves the tenant datastore and executes with a fresh session"() {
        given:
        def mappingContext = datastore.mappingContext
        def registry = new GormRegistry()
        registry.registerEntityDatastore(GormStaticApiThing.name, ConnectionSource.DEFAULT, datastore)
        def api = new GormStaticApi(GormStaticApiThing, mappingContext, [], null, ConnectionSource.DEFAULT, registry)

        expect:
        api.withNewSession(ConnectionSource.DEFAULT) { Session session -> session != null } == true
    }

    interface MultipleConnectionSourceDatastoreForTest extends Datastore, MultipleConnectionSourceCapableDatastore {}
    interface MultiTenantDatastoreForTest extends Datastore, MultiTenantCapableDatastore {}
}

@Entity
class GormStaticApiThing {

    String name
}

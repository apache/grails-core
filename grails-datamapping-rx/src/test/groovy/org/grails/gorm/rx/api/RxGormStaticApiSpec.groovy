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

import grails.gorm.rx.CriteriaBuilder
import grails.gorm.rx.DetachedCriteria
import grails.gorm.rx.RxEntity
import grails.gorm.rx.proxy.ObservableProxy
import org.grails.datastore.gorm.GormValidateable
import org.grails.datastore.gorm.finders.FinderMethod
import org.grails.datastore.mapping.config.Entity
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.core.connections.ConnectionSourceSettings
import org.grails.datastore.mapping.core.connections.ConnectionSources
import org.grails.datastore.mapping.model.ClassMapping
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.multitenancy.AllTenantsResolver
import org.grails.datastore.mapping.multitenancy.MultiTenancySettings
import org.grails.datastore.mapping.multitenancy.TenantResolver
import org.grails.datastore.mapping.query.Query
import org.grails.datastore.mapping.validation.ValidationException
import org.grails.datastore.rx.RxDatastoreClient
import org.grails.datastore.rx.internal.RxDatastoreClientImplementor
import org.grails.datastore.rx.query.RxQuery
import org.grails.gorm.rx.api.multitenancy.TenantDelegatingRxGormOperations
import org.springframework.core.convert.support.DefaultConversionService
import rx.Observable
import spock.lang.Specification

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class RxGormStaticApiSpec extends Specification {

    MappingContext mappingContext
    ConnectionSourceSettings connectionSourceSettings
    ConnectionSources connectionSources
    RxDatastoreClientImplementor datastoreClient
    PersistentEntity entity
    Query query

    // Mutable fixtures that back the stubs below, read lazily by the stubbed methods.
    // Spock matches interactions declared outside a 'then:' block in FIRST-declared order,
    // so re-stubbing e.g. "entity.getJavaClass() >> Other" from within a test would never
    // win over the stub declared in setup(); mutating these fields is what a test overrides.
    Class entityJavaClass
    Entity entityMappedForm
    List<ConnectionSource> allConnectionSources
    RxGormStaticApi registeredStaticApi
    RxGormInstanceApi registeredInstanceApi
    RxGormValidationApi registeredValidationApi
    MultiTenancySettings.MultiTenancyMode clientMultiTenancyMode
    TenantResolver clientTenantResolver

    void setup() {
        entityJavaClass = Person
        entityMappedForm = null
        registeredStaticApi = null
        registeredInstanceApi = Mock(RxGormInstanceApi)
        registeredValidationApi = Stub(RxGormValidationApi)
        clientMultiTenancyMode = MultiTenancySettings.MultiTenancyMode.NONE
        clientTenantResolver = Stub(TenantResolver)

        mappingContext = Stub(MappingContext) {
            getConversionService() >> new DefaultConversionService()
        }
        connectionSourceSettings = new ConnectionSourceSettings()
        ConnectionSource connectionSource = Stub(ConnectionSource) {
            getName() >> ConnectionSource.DEFAULT
            getSettings() >> connectionSourceSettings
        }
        allConnectionSources = [connectionSource]
        connectionSources = Stub(ConnectionSources) {
            getDefaultConnectionSource() >> connectionSource
            getAllConnectionSources() >> { allConnectionSources }
        }
        entity = Stub(PersistentEntity) {
            getJavaClass() >> { entityJavaClass }
            getName() >> { entityJavaClass.name }
            getMapping() >> { Stub(ClassMapping) { getMappedForm() >> entityMappedForm } }
            getIdentity() >> Stub(PersistentProperty) { getName() >> 'id' }
            getMappingContext() >> mappingContext
        }
        mappingContext.getPersistentEntity(_) >> entity

        query = Mock(Query, additionalInterfaces: [RxQuery])
        query.getEntity() >> entity

        datastoreClient = Mock(RxDatastoreClientImplementor) {
            getConnectionSources() >> connectionSources
            getMappingContext() >> mappingContext
            getMultiTenancyMode() >> { clientMultiTenancyMode }
            getTenantResolver() >> { clientTenantResolver }
            createQuery(_) >> query
            createQuery(_, _) >> query
            createStaticApi(_, _) >> { registeredStaticApi }
            createInstanceApi(_, _) >> { registeredInstanceApi }
            createValidationApi(_, _) >> { registeredValidationApi }
        }
    }

    void cleanup() {
        RxGormEnhancer.close()
        GroovySystem.metaClassRegistry.removeMetaClass(MethodMissingEntity)
    }

    private RxGormStaticApi<Person> newApi() {
        new RxGormStaticApi<Person>(entity, datastoreClient)
    }

    private RxGormStaticApi<Person> registerSelfAsStaticApi() {
        registeredStaticApi = new RxGormStaticApi<Person>(entity, datastoreClient)
        RxGormEnhancer.registerEntity(entity, datastoreClient)
        (RxGormStaticApi<Person>) registeredStaticApi
    }

    private RxGormInstanceApi<Person> registerInstanceApi() {
        registeredStaticApi = registeredStaticApi ?: new RxGormStaticApi<Person>(entity, datastoreClient)
        RxGormEnhancer.registerEntity(entity, datastoreClient)
        (RxGormInstanceApi<Person>) registeredInstanceApi
    }

    private PersistentEntity entityFor(Class javaClass) {
        Stub(PersistentEntity) {
            getJavaClass() >> javaClass
            getName() >> javaClass.name
            getMapping() >> Stub(ClassMapping) { getMappedForm() >> null }
            getIdentity() >> Stub(PersistentProperty) { getName() >> 'id' }
            getMappingContext() >> mappingContext
        }
    }

    void "the constructor captures the entity, persistent class and datastore client, and builds the dynamic finders"() {
        when:
        def api = newApi()

        then:
        api.entity.is(entity)
        api.persistentClass == Person
        api.datastoreClient.is(datastoreClient)
        api.gormDynamicFinders.size() == 7
    }

    void "create delegates to the entity's newInstance"() {
        given:
        Person created = new Person(name: 'fresh')
        entity.newInstance() >> created

        expect:
        newApi().create().is(created)
    }

    void "get(id) restricts the query by id, limits results to one and delegates to singleResult"() {
        given:
        def api = newApi()
        Observable<Person> observable = Observable.just(new Person(id: '1'))

        when:
        def result = api.get('1')

        then:
        1 * query.idEq('1')
        1 * query.max(1)
        1 * query.singleResult([:]) >> observable
        result.is(observable)
    }

    void "get(id, args) forwards the arguments map to singleResult"() {
        given:
        def api = newApi()
        Map args = [cache: true]
        Observable<Person> observable = Observable.empty()

        when:
        def result = api.get('1', args)

        then:
        1 * query.singleResult(args) >> observable
        result.is(observable)
    }

    void "first(String) sorts by the given property and limits results to one"() {
        given:
        def api = newApi()

        when:
        def result = api.first('name')

        then:
        1 * query.max(1)
        1 * query.singleResult([sort: 'name']) >> Observable.just(new Person(name: 'A'))
        result.toBlocking().single().name == 'A'
    }

    void "first(Map) strips any explicit order before querying"() {
        given:
        def api = newApi()

        when:
        api.first([sort: 'name', order: 'desc', max: 5])

        then:
        1 * query.singleResult([sort: 'name', max: 5]) >> Observable.empty()
    }

    void "last(String) sorts descending by the given property and limits results to one"() {
        given:
        def api = newApi()

        when:
        api.last('name')

        then:
        1 * query.max(1)
        1 * query.singleResult([sort: 'name', order: 'desc']) >> Observable.empty()
    }

    void "last(Map) defaults the sort to the entity's identifier when none is given"() {
        given:
        def api = newApi()

        when:
        api.last([:])

        then:
        1 * query.singleResult([order: 'desc', sort: 'id']) >> Observable.empty()
    }

    void "count applies a count projection and delegates to singleResult"() {
        given:
        def api = newApi()
        Query.ProjectionList projectionList = new Query.ProjectionList()

        when:
        def result = api.count()

        then:
        1 * query.projections() >> projectionList
        1 * query.singleResult() >> Observable.just(3)
        projectionList.projectionList[0] instanceof Query.CountProjection
        result.toBlocking().single() == 3
    }

    void "deleteAll(Iterable) delegates directly to the datastore client"() {
        given:
        def api = newApi()
        Observable<Number> observable = Observable.just(0)

        when:
        def result = api.deleteAll([])

        then:
        1 * datastoreClient.deleteAll([]) >> observable
        result.is(observable)
    }

    void "deleteAll(varargs) converts the array to a list and delegates to the datastore client"() {
        given:
        def api = newApi()
        Person a = new Person(id: '1')
        Person b = new Person(id: '2')
        Observable<Number> observable = Observable.just(2)

        when:
        def result = api.deleteAll(a, b)

        then:
        1 * datastoreClient.deleteAll([a, b]) >> observable
        result.is(observable)
    }

    void "saveAll persists every object when validation passes"() {
        given:
        def api = newApi()
        Person a = new Person(id: '1')
        Observable observable = Observable.just(['1'])

        when:
        def result = api.saveAll([a])

        then:
        1 * datastoreClient.persistAll([a], [:]) >> observable
        result.is(observable)
    }

    void "saveAll throws a ValidationException and never persists when a GormValidateable object is invalid"() {
        given:
        def api = newApi()
        ValidatableEntity invalid = new ValidatableEntity(valid: false)

        when:
        api.saveAll([invalid])

        then:
        ValidationException e = thrown(ValidationException)
        e.message.contains('ValidatableEntity')
        0 * datastoreClient.persistAll(_, _)
    }

    void "saveAll skips validation and still persists an invalid object when validate:false is passed"() {
        given:
        def api = newApi()
        ValidatableEntity invalid = new ValidatableEntity(valid: false)
        Observable observable = Observable.just(['1'])

        when:
        def result = api.saveAll([invalid], [validate: false])

        then:
        1 * datastoreClient.persistAll([invalid], [validate: false]) >> observable
        result.is(observable)
    }

    void "saveAll(varargs) delegates to the iterable overload with default arguments"() {
        given:
        def api = newApi()
        Person a = new Person(id: '1')
        Observable observable = Observable.just(['1'])

        when:
        def result = api.saveAll(a)

        then:
        1 * datastoreClient.persistAll([a], [:]) >> observable
        result.is(observable)
    }

    void "insertAll forces an insert of every object when validation passes"() {
        given:
        def api = newApi()
        Person a = new Person(id: '1')
        Observable observable = Observable.just(['1'])

        when:
        def result = api.insertAll([a])

        then:
        1 * datastoreClient.insertAll([a], [:]) >> observable
        result.is(observable)
    }

    void "insertAll throws a ValidationException and never inserts when a GormValidateable object is invalid"() {
        given:
        def api = newApi()
        ValidatableEntity invalid = new ValidatableEntity(valid: false)

        when:
        api.insertAll([invalid])

        then:
        thrown(ValidationException)
        0 * datastoreClient.insertAll(_, _)
    }

    void "insertAll skips validation and still inserts an invalid object when validate:false is passed"() {
        given:
        def api = newApi()
        ValidatableEntity invalid = new ValidatableEntity(valid: false)
        Observable observable = Observable.just(['1'])

        when:
        def result = api.insertAll([invalid], [validate: false])

        then:
        1 * datastoreClient.insertAll([invalid], [validate: false]) >> observable
        result.is(observable)
    }

    void "insertAll(varargs) delegates to the iterable overload with default arguments"() {
        given:
        def api = newApi()
        Person a = new Person(id: '1')
        Observable observable = Observable.just(['1'])

        when:
        def result = api.insertAll(a)

        then:
        1 * datastoreClient.insertAll([a], [:]) >> observable
        result.is(observable)
    }

    void "exists(id) emits true when get(id) resolves an instance"() {
        given:
        def api = newApi()

        when:
        def result = api.exists('1')

        then:
        1 * query.singleResult([:]) >> Observable.just(new Person(id: '1'))
        result.toBlocking().single()
    }

    void "exists(id) emits false when get(id) resolves no instance"() {
        given:
        def api = newApi()

        when:
        def result = api.exists('1')

        then:
        1 * query.singleResult([:]) >> Observable.empty()
        !result.toBlocking().single()
    }

    void "list(Map) converts all matching results into a list observable"() {
        given:
        def api = newApi()
        Person a = new Person(id: '1')
        Person b = new Person(id: '2')

        when:
        def result = api.list([:])

        then:
        1 * query.findAll([:]) >> Observable.just(a, b)
        result.toBlocking().single() == [a, b]
    }

    void "findAll(Map) streams each matching result individually rather than collecting a list"() {
        given:
        def api = newApi()
        Person a = new Person(id: '1')

        when:
        Observable<Person> result = api.findAll([:])

        then:
        1 * query.findAll([:]) >> Observable.just(a)
        result.toList().toBlocking().single() == [a]
    }

    void "findWhere(Map) applies equality constraints, limits to one result and delegates to singleResult"() {
        given:
        def api = newApi()
        Observable<Person> observable = Observable.empty()

        when:
        def result = api.findWhere([name: 'Fred'])

        then:
        1 * query.allEq([name: 'Fred'])
        1 * query.max(1)
        1 * query.singleResult([:]) >> observable
        result.is(observable)
    }

    void "findWhere(Map, Map) forwards the query arguments to singleResult"() {
        given:
        def api = newApi()
        Observable<Person> observable = Observable.empty()
        Map args = [cache: true]

        when:
        def result = api.findWhere([name: 'Fred'], args)

        then:
        1 * query.singleResult(args) >> observable
        result.is(observable)
    }

    void "findOrCreateWhere returns the existing match when the underlying query finds one"() {
        given:
        def api = newApi()
        Person existing = new Person(id: '1', name: 'Fred')

        when:
        def result = api.findOrCreateWhere([name: 'Fred']).toBlocking().single()

        then:
        1 * query.singleResult([:]) >> Observable.just(existing)
        result.is(existing)
    }

    void "findOrCreateWhere creates a new transient instance from the query map when no match exists"() {
        given:
        def api = newApi()

        when:
        def result = api.findOrCreateWhere([name: 'Fred']).toBlocking().single()

        then:
        1 * query.singleResult([:]) >> Observable.empty()
        result.name == 'Fred'
    }

    void "findOrSaveWhere returns the existing match when the underlying query finds one"() {
        given:
        entityJavaClass = SavablePerson
        def api = newApi()
        SavablePerson existing = new SavablePerson(id: '1', name: 'Fred')

        when:
        def result = api.findOrSaveWhere([name: 'Fred']).toBlocking().single()

        then:
        1 * query.singleResult([:]) >> Observable.just(existing)
        result.is(existing)
    }

    void "findOrSaveWhere creates, saves and returns a new instance when no match exists"() {
        given: "the creation path runs on a background thread, so the result is awaited with a bound instead of blocking forever"
        entityJavaClass = SavablePerson
        def api = newApi()
        CountDownLatch latch = new CountDownLatch(1)
        AtomicReference<SavablePerson> resultRef = new AtomicReference<>()
        AtomicReference<Throwable> errorRef = new AtomicReference<>()

        when:
        api.findOrSaveWhere([name: 'Fred']).subscribe(
                { SavablePerson p -> resultRef.set(p); latch.countDown() },
                { Throwable t -> errorRef.set(t); latch.countDown() })
        boolean completedInTime = latch.await(10, TimeUnit.SECONDS)

        then:
        1 * query.singleResult([:]) >> Observable.empty()
        completedInTime
        errorRef.get() == null
        resultRef.get()?.name == 'Fred'
    }

    void "findOrSaveWhere propagates an error from the underlying save() to the subscriber"() {
        given: "the creation path runs on a background thread, so the result is awaited with a bound instead of blocking forever"
        entityJavaClass = FailingSavablePerson
        def api = newApi()
        CountDownLatch latch = new CountDownLatch(1)
        AtomicReference<FailingSavablePerson> resultRef = new AtomicReference<>()
        AtomicReference<Throwable> errorRef = new AtomicReference<>()

        when:
        api.findOrSaveWhere([name: 'Fred']).subscribe(
                { FailingSavablePerson p -> resultRef.set(p); latch.countDown() },
                { Throwable t -> errorRef.set(t); latch.countDown() })
        boolean completedInTime = latch.await(10, TimeUnit.SECONDS)

        then:
        1 * query.singleResult([:]) >> Observable.empty()
        completedInTime
        resultRef.get() == null
        errorRef.get() instanceof IllegalStateException
        errorRef.get().message == 'save failed'
    }

    void "findAllWhere(Map) applies equality constraints and streams all matches"() {
        given:
        def api = newApi()
        Observable<Person> observable = Observable.empty()

        when:
        def result = api.findAllWhere([name: 'Fred'])

        then:
        1 * query.allEq([name: 'Fred'])
        1 * query.findAll([:]) >> observable
        result.is(observable)
    }

    void "findAllWhere(Map, Map) forwards the query arguments to findAll, not just allEq"() {
        given:
        def api = newApi()
        Observable<Person> observable = Observable.empty()
        Map args = [max: 10]

        when:
        def result = api.findAllWhere([name: 'Fred'], args)

        then:
        1 * query.allEq([name: 'Fred'])
        1 * query.findAll(args) >> observable
        result.is(observable)
    }

    void "where, whereLazy and whereAny build DetachedCriteria instances without touching the datastore client"() {
        given:
        def api = newApi()

        when:
        def whereResult = api.where { }
        def whereLazyResult = api.whereLazy { }
        def whereAnyResult = api.whereAny { }

        then:
        whereResult instanceof DetachedCriteria
        whereLazyResult instanceof DetachedCriteria
        whereAnyResult instanceof DetachedCriteria
        0 * datastoreClient.createQuery(_, _)
    }

    void "findAll(Closure) resolves the registered static api's query creator and streams all matches"() {
        given:
        registerSelfAsStaticApi()
        def api = newApi()
        Observable<Person> observable = Observable.empty()

        when:
        def result = api.findAll { }

        then:
        1 * query.findAll([:]) >> observable
        result.is(observable)
    }

    void "find(Closure) limits the resolved query to a single result before streaming"() {
        given:
        registerSelfAsStaticApi()
        def api = newApi()
        Observable<Person> observable = Observable.empty()

        when:
        def result = api.find { }

        then:
        1 * query.max(1)
        1 * query.findAll([:]) >> observable
        result.is(observable)
    }

    void "createCriteria builds a CriteriaBuilder bound to this entity's persistent class and datastore client"() {
        given:
        def api = newApi()
        Observable<Person> observable = Observable.empty()

        when:
        CriteriaBuilder<Person> criteria = api.createCriteria()
        def result = criteria.findAll { }

        then:
        criteria.targetClass == Person
        1 * query.findAll(Collections.emptyMap()) >> observable
        result.is(observable)
    }

    void "withCriteria(Closure) creates a criteria builder and finds all matching results"() {
        given:
        def api = newApi()
        Observable<Person> observable = Observable.empty()

        when:
        def result = api.withCriteria { }

        then:
        1 * query.findAll(Collections.emptyMap()) >> observable
        result.is(observable)
    }

    void "withCriteria(Map, Closure) invokes get() when uniqueResult is true"() {
        given:
        def api = newApi()
        Observable<Person> observable = Observable.empty()

        when:
        def result = api.withCriteria([uniqueResult: true]) { }

        then:
        1 * query.singleResult() >> observable
        result.is(observable)
    }

    void "withCriteria(Map, Closure) invokes findAll() when uniqueResult is not set"() {
        given:
        def api = newApi()
        Observable<Person> observable = Observable.empty()

        when:
        def result = api.withCriteria([:]) { }

        then:
        1 * query.findAll(Collections.emptyMap()) >> observable
        result.is(observable)
    }

    void "proxy(id) delegates to the datastore client using the entity's java class"() {
        given:
        def api = newApi()
        def observableProxy = Stub(ObservableProxy)

        when:
        def result = api.proxy('1')

        then:
        1 * datastoreClient.proxy(Person, '1') >> observableProxy
        result.is(observableProxy)
    }

    void "proxy(id, queryArgs) behaves like proxy(id) since the query arguments have nowhere to go"() {
        given:
        def api = newApi()
        def observableProxy = Stub(ObservableProxy)

        when:
        def result = api.proxy('1', [fetch: [:]])

        then:
        1 * datastoreClient.proxy(Person, '1') >> observableProxy
        result.is(observableProxy)
    }

    void "proxy(DetachedCriteria) resolves the query and asks the datastore client for a proxy"() {
        given:
        registerSelfAsStaticApi()
        def api = newApi()
        def criteria = new DetachedCriteria<Person>(Person)
        def observableProxy = Stub(ObservableProxy)

        when:
        def result = api.proxy(criteria)

        then:
        1 * datastoreClient.proxy(query) >> observableProxy
        result.is(observableProxy)
    }

    void "proxy(DetachedCriteria, Map) resolves the query with the given arguments and asks the datastore client for a proxy"() {
        given:
        registerSelfAsStaticApi()
        def api = newApi()
        def criteria = new DetachedCriteria<Person>(Person)
        def observableProxy = Stub(ObservableProxy)

        when:
        def result = api.proxy(criteria, [cache: true])

        then:
        1 * datastoreClient.proxy(query) >> observableProxy
        result.is(observableProxy)
    }

    void "staticMethodMissing delegates to methodMissing"() {
        given:
        def api = newApi()

        when:
        api.staticMethodMissing('totallyUnknownMethod', [] as Object[])

        then:
        MissingMethodException e = thrown(MissingMethodException)
        e.method == 'totallyUnknownMethod'
    }

    void "staticPropertyMissing delegates to propertyMissing"() {
        given:
        def api = newApi()

        when:
        api.staticPropertyMissing('bogus')

        then:
        MissingPropertyException e = thrown(MissingPropertyException)
        e.property == 'bogus'
    }

    void "propertyMissing throws a MissingPropertyException"() {
        given:
        def api = newApi()

        when:
        api.propertyMissing('bogus')

        then:
        MissingPropertyException e = thrown(MissingPropertyException)
        e.message.contains('bogus')
    }

    void "methodMissing throws a MissingMethodException when no dynamic finder matches"() {
        given:
        def api = newApi()

        when:
        api.methodMissing('totallyUnknownMethod', [] as Object[])

        then:
        thrown(MissingMethodException)
    }

    void "methodMissing delegates to the matching finder and returns its result"() {
        given:
        def isolatedEntity = entityFor(MethodMissingEntity)
        def api = new RxGormStaticApi<MethodMissingEntity>(isolatedEntity, datastoreClient)
        FinderMethod finder = Mock(FinderMethod) {
            isMethodMatch('countByName') >> true
        }
        api.gormDynamicFinders.clear()
        api.gormDynamicFinders.add(finder)
        Observable observable = Observable.just(5)

        when:
        def result = api.methodMissing('countByName', ['Fred'] as Object[])

        then:
        1 * finder.invoke(MethodMissingEntity, 'countByName', ['Fred'] as Object[]) >> observable
        result.is(observable)
    }

    void "methodMissing registers a callable dynamic finder that re-invokes the matching finder on the next static call"() {
        given:
        def isolatedEntity = entityFor(MethodMissingEntity)
        def api = new RxGormStaticApi<MethodMissingEntity>(isolatedEntity, datastoreClient)
        FinderMethod finder = Mock(FinderMethod) {
            isMethodMatch('countByName') >> true
        }
        api.gormDynamicFinders.clear()
        api.gormDynamicFinders.add(finder)
        Observable first = Observable.just(5)
        Observable second = Observable.just(7)

        when:
        def firstResult = api.methodMissing('countByName', ['Fred'] as Object[])

        then:
        1 * finder.invoke(MethodMissingEntity, 'countByName', ['Fred'] as Object[]) >> first
        firstResult.is(first)

        when: "the metaClass-registered static method is invoked directly on the persistent class"
        def secondResult = MethodMissingEntity.countByName('Fred')

        then:
        1 * finder.invoke(MethodMissingEntity, 'countByName', ['Fred'] as Object[]) >> second
        secondResult.is(second)
    }

    void "the registered dynamic finder wraps a null argument in a single element array"() {
        given:
        def isolatedEntity = entityFor(MethodMissingEntity)
        def api = new RxGormStaticApi<MethodMissingEntity>(isolatedEntity, datastoreClient)
        FinderMethod finder = Mock(FinderMethod) {
            isMethodMatch('countByName') >> true
        }
        api.gormDynamicFinders.clear()
        api.gormDynamicFinders.add(finder)
        api.methodMissing('countByName', ['Fred'] as Object[])
        Observable observable = Observable.just(0)

        when:
        def result = MethodMissingEntity.countByName(null)

        then:
        1 * finder.invoke(MethodMissingEntity, 'countByName', [null] as Object[]) >> observable
        result.is(observable)
    }

    void "the registered dynamic finder wraps a non-Object array argument in a single element array"() {
        given:
        def isolatedEntity = entityFor(MethodMissingEntity)
        def api = new RxGormStaticApi<MethodMissingEntity>(isolatedEntity, datastoreClient)
        FinderMethod finder = Mock(FinderMethod) {
            isMethodMatch('countByName') >> true
        }
        api.gormDynamicFinders.clear()
        api.gormDynamicFinders.add(finder)
        api.methodMissing('countByName', ['Fred'] as Object[])
        Observable observable = Observable.just(0)
        String[] names = ['Fred', 'Wilma']

        when:
        def result = MethodMissingEntity.countByName(names)

        then:
        1 * finder.invoke(MethodMissingEntity, 'countByName', [names] as Object[]) >> observable
        result.is(observable)
    }

    void "the registered dynamic finder unwraps a single argument that is itself an array"() {
        given:
        def isolatedEntity = entityFor(MethodMissingEntity)
        def api = new RxGormStaticApi<MethodMissingEntity>(isolatedEntity, datastoreClient)
        FinderMethod finder = Mock(FinderMethod) {
            isMethodMatch('countByName') >> true
        }
        api.gormDynamicFinders.clear()
        api.gormDynamicFinders.add(finder)
        api.methodMissing('countByName', ['Fred'] as Object[])
        Observable observable = Observable.just(0)
        String[] names = ['Fred', 'Wilma']
        Object[] wrapped = [names] as Object[]

        when:
        def result = MethodMissingEntity.countByName(wrapped)

        then:
        1 * finder.invoke(MethodMissingEntity, 'countByName', names) >> observable
        result.is(observable)
    }

    void "ident delegates to the registered instance api for the default connection source"() {
        given:
        def instanceApi = registerInstanceApi()
        def api = newApi()
        Person person = new Person(id: '1')

        when:
        def result = api.ident(person)

        then:
        1 * instanceApi.ident(person) >> '1'
        result == '1'
    }

    void "save(instance) delegates to the registered instance api"() {
        given:
        def instanceApi = registerInstanceApi()
        def api = newApi()
        Person person = new Person()
        Observable observable = Observable.just(person)

        when:
        def result = api.save(person)

        then:
        1 * instanceApi.save(person) >> observable
        result.is(observable)
    }

    void "save(instance, arguments) delegates to the registered instance api with the arguments"() {
        given:
        def instanceApi = registerInstanceApi()
        def api = newApi()
        Person person = new Person()
        Map args = [flush: true]
        Observable observable = Observable.just(person)

        when:
        def result = api.save(person, args)

        then:
        1 * instanceApi.save(person, args) >> observable
        result.is(observable)
    }

    void "insert(instance) delegates to the registered instance api's insert method"() {
        given:
        def instanceApi = registerInstanceApi()
        def api = newApi()
        Person person = new Person()
        Observable observable = Observable.just(person)

        when:
        def result = api.insert(person)

        then:
        1 * instanceApi.insert(person) >> observable
        0 * instanceApi.save(person)
        result.is(observable)
    }

    void "insert(instance, arguments) delegates to the registered instance api's insert method, not save"() {
        given:
        def instanceApi = registerInstanceApi()
        def api = newApi()
        Person person = new Person()
        Map args = [flush: true]
        Observable observable = Observable.just(person)

        when:
        def result = api.insert(person, args)

        then:
        1 * instanceApi.insert(person, args) >> observable
        0 * instanceApi.save(person, args)
        result.is(observable)
    }

    void "delete(instance) delegates to the registered instance api"() {
        given:
        def instanceApi = registerInstanceApi()
        def api = newApi()
        Person person = new Person(id: '1')
        Observable<Boolean> observable = Observable.just(true)

        when:
        def result = api.delete(person)

        then:
        1 * instanceApi.delete(person) >> observable
        result.is(observable)
    }

    void "delete(instance, arguments) delegates to the registered instance api with the arguments"() {
        given:
        def instanceApi = registerInstanceApi()
        def api = newApi()
        Person person = new Person(id: '1')
        Map args = [flush: true]
        Observable<Boolean> observable = Observable.just(true)

        when:
        def result = api.delete(person, args)

        then:
        1 * instanceApi.delete(person, args) >> observable
        result.is(observable)
    }

    void "withTenant(id, closure) throws UnsupportedOperationException outside DATABASE or shared connection modes"() {
        given:
        def api = newApi()

        when:
        api.withTenant('tenantX') { }

        then:
        thrown(UnsupportedOperationException)
    }

    void "withTenant(id, closure) in DATABASE mode delegates to the static api registered for that tenant with a zero arg closure"() {
        given:
        connectionSourceSettings.multiTenancy.mode = MultiTenancySettings.MultiTenancyMode.DATABASE
        entityMappedForm = new Entity(datasources: [ConnectionSource.DEFAULT, 'tenantX'])
        RxGormStaticApi<Person> registered = new RxGormStaticApi<Person>(entity, datastoreClient)
        registeredStaticApi = registered
        RxGormEnhancer.registerEntity(entity, datastoreClient)
        def api = newApi()

        when:
        def result = api.withTenant('tenantX') { -> delegate }

        then:
        result.is(registered)
    }

    void "withTenant(id, closure) in DATABASE mode passes the tenant id to a one arg closure"() {
        given:
        connectionSourceSettings.multiTenancy.mode = MultiTenancySettings.MultiTenancyMode.DATABASE
        entityMappedForm = new Entity(datasources: [ConnectionSource.DEFAULT, 'tenantX'])
        registeredStaticApi = new RxGormStaticApi<Person>(entity, datastoreClient)
        RxGormEnhancer.registerEntity(entity, datastoreClient)
        def api = newApi()

        expect:
        api.withTenant('tenantX') { tid -> tid } == 'tenantX'
    }

    void "withTenant(id, closure) rejects a closure accepting more than one argument"() {
        given:
        connectionSourceSettings.multiTenancy.mode = MultiTenancySettings.MultiTenancyMode.DATABASE
        entityMappedForm = new Entity(datasources: [ConnectionSource.DEFAULT, 'tenantX'])
        registeredStaticApi = new RxGormStaticApi<Person>(entity, datastoreClient)
        RxGormEnhancer.registerEntity(entity, datastoreClient)
        def api = newApi()

        when:
        api.withTenant('tenantX') { a, b -> }

        then:
        thrown(IllegalArgumentException)
    }

    void "withTenant(id, closure) in a shared connection mode binds the tenant id and delegates to the default qualifier's static api"() {
        given:
        connectionSourceSettings.multiTenancy.mode = MultiTenancySettings.MultiTenancyMode.DISCRIMINATOR
        RxGormStaticApi<Person> registered = new RxGormStaticApi<Person>(entity, datastoreClient)
        registeredStaticApi = registered
        RxGormEnhancer.registerEntity(entity, datastoreClient)
        def api = newApi()

        when:
        def result = api.withTenant('tenantX') { -> delegate }

        then:
        result.is(registered)
    }

    void "withTenant(id, closure) in a shared connection mode passes the tenant id to a one arg closure"() {
        given:
        connectionSourceSettings.multiTenancy.mode = MultiTenancySettings.MultiTenancyMode.SCHEMA
        registeredStaticApi = new RxGormStaticApi<Person>(entity, datastoreClient)
        RxGormEnhancer.registerEntity(entity, datastoreClient)
        def api = newApi()

        expect:
        api.withTenant('tenantX') { tid -> tid } == 'tenantX'
    }

    void "withTenant(id, closure) in a shared connection mode rejects a closure accepting more than one argument"() {
        given:
        connectionSourceSettings.multiTenancy.mode = MultiTenancySettings.MultiTenancyMode.DISCRIMINATOR
        registeredStaticApi = new RxGormStaticApi<Person>(entity, datastoreClient)
        RxGormEnhancer.registerEntity(entity, datastoreClient)
        def api = newApi()

        when:
        api.withTenant('tenantX') { a, b -> }

        then:
        thrown(IllegalArgumentException)
    }

    void "withTenant(id) throws UnsupportedOperationException outside DATABASE or shared connection modes"() {
        given:
        def api = newApi()

        when:
        api.withTenant('tenantX')

        then:
        thrown(UnsupportedOperationException)
    }

    void "withTenant(id) in DATABASE mode returns the static api registered for that tenant"() {
        given:
        connectionSourceSettings.multiTenancy.mode = MultiTenancySettings.MultiTenancyMode.DATABASE
        entityMappedForm = new Entity(datasources: [ConnectionSource.DEFAULT, 'tenantX'])
        RxGormStaticApi<Person> registered = new RxGormStaticApi<Person>(entity, datastoreClient)
        registeredStaticApi = registered
        RxGormEnhancer.registerEntity(entity, datastoreClient)
        def api = newApi()

        expect:
        api.withTenant('tenantX').is(registered)
    }

    void "withTenant(id) in a shared connection mode returns a TenantDelegatingRxGormOperations wrapping the default static api"() {
        given:
        connectionSourceSettings.multiTenancy.mode = MultiTenancySettings.MultiTenancyMode.SCHEMA
        registeredStaticApi = new RxGormStaticApi<Person>(entity, datastoreClient)
        RxGormEnhancer.registerEntity(entity, datastoreClient)
        def api = newApi()

        when:
        def result = api.withTenant('tenantX')

        then:
        result instanceof TenantDelegatingRxGormOperations
    }

    void "eachTenant invokes the closure for every non default connection source in DATABASE mode and returns this"() {
        given:
        connectionSourceSettings.multiTenancy.mode = MultiTenancySettings.MultiTenancyMode.DATABASE
        clientMultiTenancyMode = MultiTenancySettings.MultiTenancyMode.DATABASE
        ConnectionSource tenantSource = Stub(ConnectionSource) { getName() >> 'tenantX' }
        allConnectionSources = [connectionSources.getDefaultConnectionSource(), tenantSource]
        entityMappedForm = new Entity(datasources: [ConnectionSource.DEFAULT, 'tenantX'])
        registeredStaticApi = new RxGormStaticApi<Person>(entity, datastoreClient)
        RxGormEnhancer.registerEntity(entity, datastoreClient)
        def api = newApi()
        List<Serializable> visited = []

        when:
        def result = api.eachTenant { Serializable tenantId -> visited << tenantId }

        then:
        visited == ['tenantX']
        result.is(api)
    }

    void "eachTenant resolves tenant ids from an AllTenantsResolver in a shared connection mode"() {
        given:
        connectionSourceSettings.multiTenancy.mode = MultiTenancySettings.MultiTenancyMode.DISCRIMINATOR
        AllTenantsResolver resolver = Stub(AllTenantsResolver) { resolveTenantIds() >> ['t1', 't2'] }
        clientMultiTenancyMode = MultiTenancySettings.MultiTenancyMode.DISCRIMINATOR
        clientTenantResolver = resolver
        registeredStaticApi = new RxGormStaticApi<Person>(entity, datastoreClient)
        RxGormEnhancer.registerEntity(entity, datastoreClient)
        def api = newApi()
        List<Serializable> visited = []

        when:
        def result = api.eachTenant { Serializable tenantId -> visited << tenantId }

        then:
        visited == ['t1', 't2']
        result.is(api)
    }

    private static class Person {
        Serializable id
        String name
    }

    private static class SavablePerson implements RxEntity<SavablePerson> {
        Serializable id
        String name

        @Override
        Observable<SavablePerson> save(Map arguments) {
            Observable.just(this)
        }
    }

    private static class FailingSavablePerson implements RxEntity<FailingSavablePerson> {
        Serializable id
        String name

        @Override
        Observable<FailingSavablePerson> save(Map arguments) {
            Observable.error(new IllegalStateException('save failed'))
        }
    }

    private static class ValidatableEntity implements GormValidateable {
        Serializable id
        boolean valid = true

        @Override
        boolean validate() {
            valid
        }
    }

    private static class MethodMissingEntity {
    }
}

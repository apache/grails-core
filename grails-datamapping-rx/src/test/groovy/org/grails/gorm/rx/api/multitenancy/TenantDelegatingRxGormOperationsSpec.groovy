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
package org.grails.gorm.rx.api.multitenancy

import grails.gorm.rx.CriteriaBuilder
import grails.gorm.rx.DetachedCriteria
import grails.gorm.rx.api.RxGormAllOperations
import grails.gorm.rx.multitenancy.Tenants
import grails.gorm.rx.proxy.ObservableProxy
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.core.connections.ConnectionSources
import org.grails.datastore.mapping.model.ClassMapping
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.multitenancy.MultiTenancySettings
import org.grails.datastore.mapping.multitenancy.TenantResolver
import org.grails.datastore.rx.internal.RxDatastoreClientImplementor
import org.grails.gorm.rx.api.RxGormEnhancer
import org.grails.gorm.rx.api.RxGormInstanceApi
import org.grails.gorm.rx.api.RxGormStaticApi
import org.grails.gorm.rx.api.RxGormValidationApi
import rx.Observable
import spock.lang.Specification

class TenantDelegatingRxGormOperationsSpec extends Specification {

    private static final String TENANT_ID = 'tenantA'

    private static class TestEntity {
    }

    RxDatastoreClientImplementor client
    RxGormAllOperations delegateOperations
    TenantDelegatingRxGormOperations<TestEntity> operations

    void setup() {
        client = Mock(RxDatastoreClientImplementor) {
            getConnectionSources() >> Stub(ConnectionSources) {
                getAllConnectionSources() >> [Stub(ConnectionSource) { getName() >> ConnectionSource.DEFAULT }]
            }
            getMultiTenancyMode() >> MultiTenancySettings.MultiTenancyMode.NONE
            getTenantResolver() >> Stub(TenantResolver)
            createStaticApi(_, _) >> Stub(RxGormStaticApi)
            createInstanceApi(_, _) >> Stub(RxGormInstanceApi)
            createValidationApi(_, _) >> Stub(RxGormValidationApi)
        }
        PersistentEntity entity = Stub(PersistentEntity) {
            getMapping() >> Stub(ClassMapping) { getMappedForm() >> null }
            getJavaClass() >> TestEntity
            getName() >> TestEntity.name
        }
        RxGormEnhancer.registerEntity(entity, client)

        delegateOperations = Mock(RxGormAllOperations)
        operations = new TenantDelegatingRxGormOperations<TestEntity>(client, TENANT_ID, delegateOperations)
    }

    void cleanup() {
        RxGormEnhancer.close()
    }

    void "ident and create delegate directly without binding a tenant"() {
        given:
        TestEntity instance = new TestEntity()
        TestEntity created = new TestEntity()

        when:
        Serializable id = operations.ident(instance)

        then:
        1 * delegateOperations.ident(instance) >> 'id-1'
        id == 'id-1'

        when:
        TestEntity result = operations.create()

        then:
        1 * delegateOperations.create() >> created
        result.is(created)
    }

    void "save binds the tenant and forwards to the delegate for the simple and argument forms"() {
        given:
        TestEntity instance = new TestEntity()
        Map arguments = [flush: true]
        Observable<TestEntity> saved = Observable.just(instance)
        Observable<TestEntity> savedWithArgs = Observable.just(instance)

        when:
        Observable<TestEntity> result = operations.save(instance)

        then:
        1 * delegateOperations.save(instance) >> {
            assert Tenants.currentId() == TENANT_ID
            saved
        }
        result.is(saved)

        when:
        Observable<TestEntity> resultWithArgs = operations.save(instance, arguments)

        then:
        1 * delegateOperations.save(instance, arguments) >> savedWithArgs
        resultWithArgs.is(savedWithArgs)
    }

    void "insert forwards to the delegate for the simple and argument forms"() {
        given:
        TestEntity instance = new TestEntity()
        Map arguments = [flush: true]
        Observable<TestEntity> inserted = Observable.just(instance)
        Observable<TestEntity> insertedWithArgs = Observable.just(instance)

        when:
        Observable<TestEntity> result = operations.insert(instance)

        then:
        1 * delegateOperations.insert(instance) >> inserted
        result.is(inserted)

        when:
        Observable<TestEntity> resultWithArgs = operations.insert(instance, arguments)

        then:
        1 * delegateOperations.insert(instance, arguments) >> insertedWithArgs
        resultWithArgs.is(insertedWithArgs)
    }

    void "delete forwards to the delegate for the simple and argument forms"() {
        given:
        TestEntity instance = new TestEntity()
        Map arguments = [flush: true]
        Observable<Boolean> deleted = Observable.just(true)
        Observable<Boolean> deletedWithArgs = Observable.just(true)

        when:
        Observable<Boolean> result = operations.delete(instance)

        then:
        1 * delegateOperations.delete(instance) >> deleted
        result.is(deleted)

        when:
        Observable<Boolean> resultWithArgs = operations.delete(instance, arguments)

        then:
        1 * delegateOperations.delete(instance, arguments) >> deletedWithArgs
        resultWithArgs.is(deletedWithArgs)
    }

    void "get binds the tenant and forwards to the delegate for the simple and argument forms"() {
        given:
        TestEntity instance = new TestEntity()
        Map queryArgs = [lock: true]
        Observable<TestEntity> found = Observable.just(instance)
        Observable<TestEntity> foundWithArgs = Observable.just(instance)

        when:
        Observable<TestEntity> result = operations.get('1')

        then:
        1 * delegateOperations.get('1') >> {
            assert Tenants.currentId() == TENANT_ID
            found
        }
        result.is(found)

        when:
        Observable<TestEntity> resultWithArgs = operations.get('1', queryArgs)

        then:
        1 * delegateOperations.get('1', queryArgs) >> foundWithArgs
        resultWithArgs.is(foundWithArgs)
    }

    void "proxy forwards to the delegate for the id and detached criteria overloads"() {
        given:
        Map queryArgs = [lock: true]
        DetachedCriteria<TestEntity> criteria = Stub(DetachedCriteria)
        ObservableProxy<TestEntity> proxyById = Stub(ObservableProxy)
        ObservableProxy<TestEntity> proxyByIdWithArgs = Stub(ObservableProxy)
        ObservableProxy<TestEntity> proxyByQuery = Stub(ObservableProxy)
        ObservableProxy<TestEntity> proxyByQueryWithArgs = Stub(ObservableProxy)

        when:
        ObservableProxy<TestEntity> result = operations.proxy('1')

        then:
        1 * delegateOperations.proxy('1') >> proxyById
        result.is(proxyById)

        when:
        ObservableProxy<TestEntity> resultWithArgs = operations.proxy('1', queryArgs)

        then:
        1 * delegateOperations.proxy('1', queryArgs) >> proxyByIdWithArgs
        resultWithArgs.is(proxyByIdWithArgs)

        when:
        ObservableProxy<TestEntity> queryResult = operations.proxy(criteria)

        then:
        1 * delegateOperations.proxy(criteria) >> proxyByQuery
        queryResult.is(proxyByQuery)

        when:
        ObservableProxy<TestEntity> queryResultWithArgs = operations.proxy(criteria, queryArgs)

        then:
        1 * delegateOperations.proxy(criteria, queryArgs) >> proxyByQueryWithArgs
        queryResultWithArgs.is(proxyByQueryWithArgs)
    }

    void "count forwards to the tenant scoped delegate"() {
        given:
        Observable<Number> counted = Observable.just(5)

        when:
        Observable<Number> result = operations.count()

        then:
        1 * delegateOperations.count() >> counted
        result.is(counted)
    }

    void "deleteAll binds the tenant and forwards to the delegate for the varargs and iterable overloads"() {
        given:
        TestEntity first = new TestEntity()
        TestEntity second = new TestEntity()
        Iterable<TestEntity> iterableArgs = [first, second]
        Observable<Number> deletedVarargs = Observable.just(2)
        Observable<Number> deletedIterable = Observable.just(2)

        when:
        Observable<Number> result = operations.deleteAll(first, second)

        then:
        1 * delegateOperations.deleteAll(first, second) >> {
            assert Tenants.currentId() == TENANT_ID
            deletedVarargs
        }
        result.is(deletedVarargs)

        when:
        Observable<Number> resultIterable = operations.deleteAll(iterableArgs)

        then:
        1 * delegateOperations.deleteAll(iterableArgs) >> deletedIterable
        resultIterable.is(deletedIterable)
    }

    void "saveAll forwards to the delegate for the iterable, iterable with arguments and varargs overloads"() {
        given:
        TestEntity first = new TestEntity()
        TestEntity second = new TestEntity()
        Iterable<TestEntity> iterableArgs = [first, second]
        Map arguments = [flush: true]
        Observable<List<Serializable>> savedIterable = Observable.just(['1', '2'])
        Observable<List<Serializable>> savedIterableWithArgs = Observable.just(['1', '2'])
        Observable<List<Serializable>> savedVarargs = Observable.just(['1', '2'])

        when:
        Observable<List<Serializable>> result = operations.saveAll(iterableArgs)

        then:
        1 * delegateOperations.saveAll(iterableArgs) >> savedIterable
        result.is(savedIterable)

        when:
        Observable<List<Serializable>> resultWithArgs = operations.saveAll(iterableArgs, arguments)

        then:
        1 * delegateOperations.saveAll(iterableArgs, arguments) >> savedIterableWithArgs
        resultWithArgs.is(savedIterableWithArgs)

        when:
        Observable<List<Serializable>> resultVarargs = operations.saveAll(first, second)

        then:
        1 * delegateOperations.saveAll(first, second) >> savedVarargs
        resultVarargs.is(savedVarargs)
    }

    void "insertAll forwards to the delegate for the iterable, iterable with arguments and varargs overloads"() {
        given:
        TestEntity first = new TestEntity()
        TestEntity second = new TestEntity()
        Iterable<TestEntity> iterableArgs = [first, second]
        Map arguments = [flush: true]
        Observable<List<Serializable>> insertedIterable = Observable.just(['1', '2'])
        Observable<List<Serializable>> insertedIterableWithArgs = Observable.just(['1', '2'])
        Observable<List<Serializable>> insertedVarargs = Observable.just(['1', '2'])

        when:
        Observable<List<Serializable>> result = operations.insertAll(iterableArgs)

        then:
        1 * delegateOperations.insertAll(iterableArgs) >> insertedIterable
        result.is(insertedIterable)

        when:
        Observable<List<Serializable>> resultWithArgs = operations.insertAll(iterableArgs, arguments)

        then:
        1 * delegateOperations.insertAll(iterableArgs, arguments) >> insertedIterableWithArgs
        resultWithArgs.is(insertedIterableWithArgs)

        when:
        Observable<List<Serializable>> resultVarargs = operations.insertAll(first, second)

        then:
        1 * delegateOperations.insertAll(first, second) >> insertedVarargs
        resultVarargs.is(insertedVarargs)
    }

    void "exists forwards to the tenant scoped delegate"() {
        given:
        Observable<Boolean> exists = Observable.just(true)

        when:
        Observable<Boolean> result = operations.exists('1')

        then:
        1 * delegateOperations.exists('1') >> exists
        result.is(exists)
    }

    void "first forwards to the delegate for the no-arg, property and query map overloads"() {
        given:
        TestEntity instance = new TestEntity()
        Observable<TestEntity> plain = Observable.just(instance)
        Observable<TestEntity> byProperty = Observable.just(instance)
        Observable<TestEntity> byParams = Observable.just(instance)
        Map params = [sort: 'name']

        when:
        Observable<TestEntity> result = operations.first()

        then:
        1 * delegateOperations.first() >> plain
        result.is(plain)

        when:
        Observable<TestEntity> resultByProperty = operations.first('name')

        then:
        1 * delegateOperations.first('name') >> byProperty
        resultByProperty.is(byProperty)

        when:
        Observable<TestEntity> resultByParams = operations.first(params)

        then:
        1 * delegateOperations.first(params) >> byParams
        resultByParams.is(byParams)
    }

    void "last forwards to the delegate for the no-arg, property and query map overloads"() {
        given:
        TestEntity instance = new TestEntity()
        Observable<TestEntity> plain = Observable.just(instance)
        Observable<TestEntity> byProperty = Observable.just(instance)
        Observable<TestEntity> byParams = Observable.just(instance)
        Map params = [sort: 'name']

        when:
        Observable<TestEntity> result = operations.last()

        then:
        1 * delegateOperations.last() >> plain
        result.is(plain)

        when:
        Observable<TestEntity> resultByProperty = operations.last('name')

        then:
        1 * delegateOperations.last('name') >> byProperty
        resultByProperty.is(byProperty)

        when:
        Observable<TestEntity> resultByParams = operations.last(params)

        then:
        1 * delegateOperations.last(params) >> byParams
        resultByParams.is(byParams)
    }

    void "list forwards to the delegate for the no-arg and args overloads"() {
        given:
        List<TestEntity> all = [new TestEntity()]
        Map args = [max: 10]
        Observable<List<TestEntity>> plain = Observable.just(all)
        Observable<List<TestEntity>> withArgs = Observable.just(all)

        when:
        Observable<List<TestEntity>> result = operations.list()

        then:
        1 * delegateOperations.list() >> plain
        result.is(plain)

        when:
        Observable<List<TestEntity>> resultWithArgs = operations.list(args)

        then:
        1 * delegateOperations.list(args) >> withArgs
        resultWithArgs.is(withArgs)
    }

    void "findAll forwards to the delegate for the no-arg, args and closure overloads"() {
        given:
        TestEntity instance = new TestEntity()
        Map args = [max: 10]
        Closure callable = { eq('name', 'test') }
        Observable<TestEntity> plain = Observable.just(instance)
        Observable<TestEntity> withArgs = Observable.just(instance)
        Observable<TestEntity> withClosure = Observable.just(instance)

        when:
        Observable<TestEntity> result = operations.findAll()

        then:
        1 * delegateOperations.findAll() >> plain
        result.is(plain)

        when:
        Observable<TestEntity> resultWithArgs = operations.findAll(args)

        then:
        1 * delegateOperations.findAll(args) >> withArgs
        resultWithArgs.is(withArgs)

        when:
        Observable<TestEntity> resultWithClosure = operations.findAll(callable)

        then:
        1 * delegateOperations.findAll(callable) >> withClosure
        resultWithClosure.is(withClosure)
    }

    void "find forwards to the tenant scoped delegate"() {
        given:
        TestEntity instance = new TestEntity()
        Closure callable = { eq('name', 'test') }
        Observable<TestEntity> found = Observable.just(instance)

        when:
        Observable<TestEntity> result = operations.find(callable)

        then:
        1 * delegateOperations.find(callable) >> found
        result.is(found)
    }

    void "findWhere binds the tenant and forwards to the delegate for the simple and argument forms"() {
        given:
        TestEntity instance = new TestEntity()
        Map queryMap = [name: 'test']
        Map args = [max: 10]
        Observable<TestEntity> plain = Observable.just(instance)
        Observable<TestEntity> withArgs = Observable.just(instance)

        when:
        Observable<TestEntity> result = operations.findWhere(queryMap)

        then:
        1 * delegateOperations.findWhere(queryMap) >> {
            assert Tenants.currentId() == TENANT_ID
            plain
        }
        result.is(plain)

        when:
        Observable<TestEntity> resultWithArgs = operations.findWhere(queryMap, args)

        then:
        1 * delegateOperations.findWhere(queryMap, args) >> withArgs
        resultWithArgs.is(withArgs)
    }

    void "findOrCreateWhere and findOrSaveWhere forward to the tenant scoped delegate"() {
        given:
        TestEntity created = new TestEntity()
        TestEntity saved = new TestEntity()
        Map queryMap = [name: 'test']
        Observable<TestEntity> createdObservable = Observable.just(created)
        Observable<TestEntity> savedObservable = Observable.just(saved)

        when:
        Observable<TestEntity> result = operations.findOrCreateWhere(queryMap)

        then:
        1 * delegateOperations.findOrCreateWhere(queryMap) >> createdObservable
        result.is(createdObservable)

        when:
        Observable<TestEntity> resultSaved = operations.findOrSaveWhere(queryMap)

        then:
        1 * delegateOperations.findOrSaveWhere(queryMap) >> savedObservable
        resultSaved.is(savedObservable)
    }

    void "findAllWhere forwards the query and its arguments to the delegate for both overloads"() {
        given:
        TestEntity instance = new TestEntity()
        Map queryMap = [name: 'test']
        Map args = [max: 10]
        Observable<TestEntity> plain = Observable.just(instance)
        Observable<TestEntity> withArgs = Observable.just(instance)

        when:
        Observable<TestEntity> result = operations.findAllWhere(queryMap)

        then:
        1 * delegateOperations.findAllWhere(queryMap) >> plain
        result.is(plain)

        when:
        Observable<TestEntity> resultWithArgs = operations.findAllWhere(queryMap, args)

        then:
        1 * delegateOperations.findAllWhere(queryMap, args) >> withArgs
        resultWithArgs.is(withArgs)
    }

    void "where, whereLazy and whereAny bind the tenant and forward to the delegate"() {
        given:
        Closure callable = { eq('name', 'test') }
        DetachedCriteria<TestEntity> where = Stub(DetachedCriteria)
        DetachedCriteria<TestEntity> whereLazy = Stub(DetachedCriteria)
        DetachedCriteria<TestEntity> whereAny = Stub(DetachedCriteria)

        when:
        DetachedCriteria<TestEntity> result = operations.where(callable)

        then:
        1 * delegateOperations.where(callable) >> {
            assert Tenants.currentId() == TENANT_ID
            where
        }
        result.is(where)

        when:
        DetachedCriteria<TestEntity> resultLazy = operations.whereLazy(callable)

        then:
        1 * delegateOperations.whereLazy(callable) >> whereLazy
        resultLazy.is(whereLazy)

        when:
        DetachedCriteria<TestEntity> resultAny = operations.whereAny(callable)

        then:
        1 * delegateOperations.whereAny(callable) >> whereAny
        resultAny.is(whereAny)
    }

    void "createCriteria forwards to the tenant scoped delegate"() {
        given:
        CriteriaBuilder<TestEntity> criteriaBuilder = Stub(CriteriaBuilder)

        when:
        CriteriaBuilder<TestEntity> result = operations.createCriteria()

        then:
        1 * delegateOperations.createCriteria() >> criteriaBuilder
        result.is(criteriaBuilder)
    }

    void "withCriteria forwards to the delegate for the simple and builder-args overloads"() {
        given:
        Closure callable = { eq('name', 'test') }
        Map builderArgs = [uniqueResult: true]
        Observable plain = Observable.just(1)
        Observable withArgs = Observable.just(1)

        when:
        Observable result = operations.withCriteria(callable)

        then:
        1 * delegateOperations.withCriteria(callable) >> plain
        result.is(plain)

        when:
        Observable resultWithArgs = operations.withCriteria(builderArgs, callable)

        then:
        1 * delegateOperations.withCriteria(builderArgs, callable) >> withArgs
        resultWithArgs.is(withArgs)
    }

    void "staticMethodMissing and staticPropertyMissing forward to the tenant scoped delegate"() {
        given:
        TestEntity instance = new TestEntity()
        Observable<TestEntity> found = Observable.just(instance)

        when:
        Observable<TestEntity> result = operations.staticMethodMissing('findByName', 'test')

        then:
        1 * delegateOperations.staticMethodMissing('findByName', 'test') >> found
        result.is(found)

        when:
        Object property = operations.staticPropertyMissing('gormPersistentEntity')

        then:
        1 * delegateOperations.staticPropertyMissing('gormPersistentEntity') >> 'entity-value'
        property == 'entity-value'
    }

    void "withTenant and eachTenant delegate directly without binding the constructor tenant id"() {
        given:
        Closure callable = { }
        RxGormAllOperations<TestEntity> tenantScoped = Mock(RxGormAllOperations)

        when:
        String result = operations.withTenant('otherTenant', callable)

        then:
        1 * delegateOperations.withTenant('otherTenant', callable) >> 'closure-result'
        result == 'closure-result'

        when:
        RxGormAllOperations<TestEntity> eachResult = operations.eachTenant(callable)

        then:
        1 * delegateOperations.eachTenant(callable) >> tenantScoped
        eachResult.is(tenantScoped)

        when:
        RxGormAllOperations<TestEntity> withTenantResult = operations.withTenant('otherTenant')

        then:
        1 * delegateOperations.withTenant('otherTenant') >> tenantScoped
        withTenantResult.is(tenantScoped)
    }
}

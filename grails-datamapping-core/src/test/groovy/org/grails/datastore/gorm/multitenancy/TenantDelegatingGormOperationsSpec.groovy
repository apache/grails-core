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

import grails.gorm.api.GormAllOperations
import grails.gorm.multitenancy.Tenants
import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.multitenancy.MultiTenancySettings
import org.grails.datastore.mapping.multitenancy.MultiTenantCapableDatastore
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.DefaultTransactionDefinition
import spock.lang.Specification

/**
 * Verifies {@link TenantDelegatingGormOperations} delegates to the wrapped operations under the
 * bound tenant, rather than a different method with a compatible signature, and that the
 * {@code Map}-arg overloads of {@code deleteAll} forward the params map rather than dropping it.
 *
 * Swaps {@link Tenants#datastoreLocator} (the established, pluggable-for-testing static field - see
 * {@code TenantsSpec}/{@code DefaultTenantServiceSpec}) so {@code Tenants.withId(Class, ...)}
 * resolves to a controlled {@code Mock(MultiTenantCapableDatastore)} regardless of the (datastore,
 * not domain) class it's actually called with - sidestepping that unrelated, pre-existing semantic
 * question entirely and just verifying this class's own delegation behaviour.
 */
class TenantDelegatingGormOperationsSpec extends Specification {

    private final Tenants.DatastoreLocator originalLocator = Tenants.datastoreLocator

    void cleanup() {
        Tenants.datastoreLocator = originalLocator
    }

    private TenantDelegatingGormOperations<Object> buildOperations(GormAllOperations delegate) {
        def tenantDatastore = Mock(MultiTenantCapableDatastore) {
            getMultiTenancyMode() >> MultiTenancySettings.MultiTenancyMode.DISCRIMINATOR
        }
        Tenants.datastoreLocator = new Tenants.DatastoreLocator() {
            @Override
            Datastore getDatastoreForDomain(Class domainClass) {
                return tenantDatastore
            }
        }
        return new TenantDelegatingGormOperations<Object>(tenantDatastore, 'tenant1', delegate)
    }

    void "delete(instance, params) delegates to the wrapped delete, not save"() {
        given:
        def delegate = Mock(GormAllOperations)
        def ops = buildOperations(delegate)
        def instance = new Object()
        def params = [flush: true]

        when:
        ops.delete(instance, params)

        then:
        1 * delegate.delete(instance, params)
        0 * delegate.save(_, _)
    }

    void "deleteAll() delegates to the wrapped operations under the bound tenant"() {
        given:
        def delegate = Mock(GormAllOperations)
        def ops = buildOperations(delegate)

        when:
        def result = ops.deleteAll()

        then:
        1 * delegate.deleteAll() >> 5
        result == 5
    }

    void "deleteAll(Map) delegates to the wrapped operations under the bound tenant"() {
        given:
        def delegate = Mock(GormAllOperations)
        def ops = buildOperations(delegate)
        def params = [flush: true]

        when:
        def result = ops.deleteAll(params)

        then:
        1 * delegate.deleteAll(params) >> 3
        result == 3
    }

    void "deleteAll(Iterable) delegates to the wrapped operations under the bound tenant"() {
        given:
        def delegate = Mock(GormAllOperations)
        def ops = buildOperations(delegate)
        def toDelete = ['a', 'b']

        when:
        ops.deleteAll(toDelete)

        then:
        1 * delegate.deleteAll(toDelete)
    }

    void "deleteAll(Map, Object...) delegates to the wrapped operations under the bound tenant"() {
        given:
        def delegate = Mock(GormAllOperations)
        def ops = buildOperations(delegate)
        def params = [flush: true]
        def toDelete = ['a', 'b'] as Object[]

        when:
        ops.deleteAll(params, toDelete)

        then:
        1 * delegate.deleteAll(params, toDelete)
    }

    void "deleteAll(Map, Iterable) delegates to the wrapped operations under the bound tenant"() {
        given:
        def delegate = Mock(GormAllOperations)
        def ops = buildOperations(delegate)
        def params = [flush: true]
        def toDelete = ['a', 'b']

        when:
        ops.deleteAll(params, toDelete)

        then:
        1 * delegate.deleteAll(params, toDelete)
    }

    void "propertyMissing(instance, name) delegates to the wrapped operations under the bound tenant"() {
        given:
        def delegate = Mock(GormAllOperations)
        def ops = buildOperations(delegate)
        def instance = new Object()
        def name = 'propName'

        when:
        ops.propertyMissing(instance, name)

        then:
        1 * delegate.propertyMissing(instance, name)
    }

    void "instanceOf(instance, cls) delegates to the wrapped operations under the bound tenant"() {
        given:
        def delegate = Mock(GormAllOperations)
        def ops = buildOperations(delegate)
        def instance = new Object()
        def cls = String

        when:
        ops.instanceOf(instance, cls)

        then:
        1 * delegate.instanceOf(instance, cls)
    }

    void "lock(instance) delegates to the wrapped operations under the bound tenant"() {
        given:
        def delegate = Mock(GormAllOperations)
        def ops = buildOperations(delegate)
        def instance = new Object()

        when:
        ops.lock(instance)

        then:
        1 * delegate.lock(instance)
    }

    void "mutex(instance, callable) delegates to the wrapped operations under the bound tenant"() {
        given:
        def delegate = Mock(GormAllOperations)
        def ops = buildOperations(delegate)
        def instance = new Object()
        def callable = { -> 'mutex-result' }

        when:
        ops.mutex(instance, callable)

        then:
        1 * delegate.mutex(instance, callable)
    }

    void "refresh(instance) delegates to the wrapped operations under the bound tenant"() {
        given:
        def delegate = Mock(GormAllOperations)
        def ops = buildOperations(delegate)
        def instance = new Object()

        when:
        ops.refresh(instance)

        then:
        1 * delegate.refresh(instance)
    }

    void "save(instance) delegates to the wrapped operations under the bound tenant"() {
        given:
        def delegate = Mock(GormAllOperations)
        def ops = buildOperations(delegate)
        def instance = new Object()

        when:
        ops.save(instance)

        then:
        1 * delegate.save(instance)
    }

    void "insert(instance) delegates to the wrapped operations under the bound tenant"() {
        given:
        def delegate = Mock(GormAllOperations)
        def ops = buildOperations(delegate)
        def instance = new Object()

        when:
        ops.insert(instance)

        then:
        1 * delegate.insert(instance)
    }

    void "insert(instance, params) delegates to the wrapped operations under the bound tenant"() {
        given:
        def delegate = Mock(GormAllOperations)
        def ops = buildOperations(delegate)
        def instance = new Object()
        def params = [flush: true]

        when:
        ops.insert(instance, params)

        then:
        1 * delegate.insert(instance, params)
    }

    void "merge(instance, params) delegates to the wrapped operations under the bound tenant"() {
        given:
        def delegate = Mock(GormAllOperations)
        def ops = buildOperations(delegate)
        def instance = new Object()
        def params = [flush: true]

        when:
        ops.merge(instance, params)

        then:
        1 * delegate.merge(instance, params)
    }

    void "save(instance, validate) delegates to the wrapped operations under the bound tenant"() {
        given:
        def delegate = Mock(GormAllOperations)
        def ops = buildOperations(delegate)
        def instance = new Object()
        def validate = true

        when:
        ops.save(instance, validate)

        then:
        1 * delegate.save(instance, validate)
    }

    void "save(instance, params) delegates to the wrapped operations under the bound tenant"() {
        given:
        def delegate = Mock(GormAllOperations)
        def ops = buildOperations(delegate)
        def instance = new Object()
        def params = [flush: true]

        when:
        ops.save(instance, params)

        then:
        1 * delegate.save(instance, params)
    }

    void "ident(instance) delegates to the wrapped operations under the bound tenant"() {
        given:
        def delegate = Mock(GormAllOperations)
        def ops = buildOperations(delegate)
        def instance = new Object()

        when:
        ops.ident(instance)

        then:
        1 * delegate.ident(instance)
    }

    void "attach(instance) delegates to the wrapped operations under the bound tenant"() {
        given:
        def delegate = Mock(GormAllOperations)
        def ops = buildOperations(delegate)
        def instance = new Object()

        when:
        ops.attach(instance)

        then:
        1 * delegate.attach(instance)
    }

    void "isAttached(instance) delegates to the wrapped operations under the bound tenant"() {
        given:
        def delegate = Mock(GormAllOperations)
        def ops = buildOperations(delegate)
        def instance = new Object()

        when:
        ops.isAttached(instance)

        then:
        1 * delegate.isAttached(instance)
    }

    void "discard(instance) delegates to the wrapped operations under the bound tenant"() {
        given:
        def delegate = Mock(GormAllOperations)
        def ops = buildOperations(delegate)
        def instance = new Object()

        when:
        ops.discard(instance)

        then:
        1 * delegate.discard(instance)
    }

    void "delete(instance) delegates to the wrapped operations under the bound tenant"() {
        given:
        def delegate = Mock(GormAllOperations)
        def ops = buildOperations(delegate)
        def instance = new Object()

        when:
        ops.delete(instance)

        then:
        1 * delegate.delete(instance)
    }

    void "where(callable) delegates to the wrapped operations under the bound tenant"() {
        given:
        def delegate = Mock(GormAllOperations)
        def ops = buildOperations(delegate)
        def callable = { -> null }

        when:
        ops.where(callable)

        then:
        1 * delegate.where(callable)
    }

    void "whereLazy(callable) delegates to the wrapped operations under the bound tenant"() {
        given:
        def delegate = Mock(GormAllOperations)
        def ops = buildOperations(delegate)
        def callable = { -> null }

        when:
        ops.whereLazy(callable)

        then:
        1 * delegate.whereLazy(callable)
    }

    void "whereAny(callable) delegates to the wrapped operations under the bound tenant"() {
        given:
        def delegate = Mock(GormAllOperations)
        def ops = buildOperations(delegate)
        def callable = { -> null }

        when:
        ops.whereAny(callable)

        then:
        1 * delegate.whereAny(callable)
    }

    void "findAll(callable) delegates to the wrapped operations under the bound tenant"() {
        given:
        def delegate = Mock(GormAllOperations)
        def ops = buildOperations(delegate)
        def callable = { -> null }

        when:
        ops.findAll(callable)

        then:
        1 * delegate.findAll(callable)
    }

    void "findAll(args, callable) delegates to the wrapped operations under the bound tenant"() {
        given:
        def delegate = Mock(GormAllOperations)
        def ops = buildOperations(delegate)
        def args = [max: 10]
        def callable = { -> null }

        when:
        ops.findAll(args, callable)

        then:
        1 * delegate.findAll(args, callable)
    }

    void "find(callable) delegates to the wrapped operations under the bound tenant"() {
        given:
        def delegate = Mock(GormAllOperations)
        def ops = buildOperations(delegate)
        def callable = { -> null }

        when:
        ops.find(callable)

        then:
        1 * delegate.find(callable)
    }

    void "saveAll(toSave) varargs delegates to the wrapped operations under the bound tenant"() {
        given:
        def delegate = Mock(GormAllOperations)
        def ops = buildOperations(delegate)
        def toSave = ['a', 'b'] as Object[]

        when:
        ops.saveAll(toSave)

        then:
        1 * delegate.saveAll(toSave)
    }

    void "saveAll(toSave) iterable delegates to the wrapped operations under the bound tenant"() {
        given:
        def delegate = Mock(GormAllOperations)
        def ops = buildOperations(delegate)
        def toSave = ['a', 'b']

        when:
        ops.saveAll(toSave)

        then:
        1 * delegate.saveAll(toSave)
    }

    void "deleteAll(toDelete) varargs delegates to the wrapped operations under the bound tenant"() {
        given:
        def delegate = Mock(GormAllOperations)
        def ops = buildOperations(delegate)
        def toDelete = ['a', 'b'] as Object[]

        when:
        ops.deleteAll(toDelete)

        then:
        1 * delegate.deleteAll(toDelete)
    }

    void "get(id) delegates to the wrapped operations under the bound tenant"() {
        given:
        def delegate = Mock(GormAllOperations)
        def ops = buildOperations(delegate)
        def id = 'id1'

        when:
        ops.get(id)

        then:
        1 * delegate.get(id)
    }

    void "read(id) delegates to the wrapped operations under the bound tenant"() {
        given:
        def delegate = Mock(GormAllOperations)
        def ops = buildOperations(delegate)
        def id = 'id1'

        when:
        ops.read(id)

        then:
        1 * delegate.read(id)
    }

    void "load(id) delegates to the wrapped operations under the bound tenant"() {
        given:
        def delegate = Mock(GormAllOperations)
        def ops = buildOperations(delegate)
        def id = 'id1'

        when:
        ops.load(id)

        then:
        1 * delegate.load(id)
    }

    void "proxy(id) delegates to the wrapped operations under the bound tenant"() {
        given:
        def delegate = Mock(GormAllOperations)
        def ops = buildOperations(delegate)
        def id = 'id1'

        when:
        ops.proxy(id)

        then:
        1 * delegate.proxy(id)
    }

    void "getAll(ids) iterable delegates to the wrapped operations under the bound tenant"() {
        given:
        def delegate = Mock(GormAllOperations)
        def ops = buildOperations(delegate)
        def ids = ['id1', 'id2']

        when:
        ops.getAll(ids)

        then:
        1 * delegate.getAll(ids)
    }

    void "getAll(ids) varargs delegates to the wrapped operations under the bound tenant"() {
        given:
        def delegate = Mock(GormAllOperations)
        def ops = buildOperations(delegate)
        def ids = ['id1', 'id2'] as Serializable[]

        when:
        ops.getAll(ids)

        then:
        1 * delegate.getAll(ids)
    }

    void "getAll() delegates to the wrapped operations under the bound tenant"() {
        given:
        def delegate = Mock(GormAllOperations)
        def ops = buildOperations(delegate)

        when:
        ops.getAll()

        then:
        1 * delegate.getAll()
    }

    void "createCriteria() delegates to the wrapped operations under the bound tenant"() {
        given:
        def delegate = Mock(GormAllOperations)
        def ops = buildOperations(delegate)

        when:
        ops.createCriteria()

        then:
        1 * delegate.createCriteria()
    }

    void "withCriteria(callable) delegates to the wrapped operations under the bound tenant"() {
        given:
        def delegate = Mock(GormAllOperations)
        def ops = buildOperations(delegate)
        def callable = { -> null }

        when:
        ops.withCriteria(callable)

        then:
        1 * delegate.withCriteria(callable)
    }

    void "withCriteria(builderArgs, callable) delegates to the wrapped operations under the bound tenant"() {
        given:
        def delegate = Mock(GormAllOperations)
        def ops = buildOperations(delegate)
        def builderArgs = [uniqueResult: true]
        def callable = { -> null }

        when:
        ops.withCriteria(builderArgs, callable)

        then:
        1 * delegate.withCriteria(builderArgs, callable)
    }

    void "lock(id) delegates to the wrapped operations under the bound tenant"() {
        given:
        def delegate = Mock(GormAllOperations)
        def ops = buildOperations(delegate)
        def id = 'lockId'

        when:
        ops.lock(id)

        then:
        1 * delegate.lock(id)
    }

    void "merge(instance) single-arg delegates to the wrapped operations under the bound tenant"() {
        given:
        def delegate = Mock(GormAllOperations)
        def ops = buildOperations(delegate)
        def instance = new Object()

        when:
        ops.merge(instance)

        then:
        1 * delegate.merge(instance)
    }

    void "count() delegates to the wrapped operations under the bound tenant"() {
        given:
        def delegate = Mock(GormAllOperations)
        def ops = buildOperations(delegate)

        when:
        ops.count()

        then:
        1 * delegate.count()
    }

    void "getCount() delegates to the wrapped operations under the bound tenant"() {
        given:
        def delegate = Mock(GormAllOperations)
        def ops = buildOperations(delegate)

        when:
        ops.getCount()

        then:
        1 * delegate.getCount()
    }

    void "exists(id) delegates to the wrapped operations under the bound tenant"() {
        given:
        def delegate = Mock(GormAllOperations)
        def ops = buildOperations(delegate)
        def id = 'id1'

        when:
        ops.exists(id)

        then:
        1 * delegate.exists(id)
    }

    void "list(params) delegates to the wrapped operations under the bound tenant"() {
        given:
        def delegate = Mock(GormAllOperations)
        def ops = buildOperations(delegate)
        def params = [max: 10]

        when:
        ops.list(params)

        then:
        1 * delegate.list(params)
    }

    void "list() delegates to the wrapped operations under the bound tenant"() {
        given:
        def delegate = Mock(GormAllOperations)
        def ops = buildOperations(delegate)

        when:
        ops.list()

        then:
        1 * delegate.list()
    }

    void "findAll(params) delegates to the wrapped operations under the bound tenant"() {
        given:
        def delegate = Mock(GormAllOperations)
        def ops = buildOperations(delegate)
        def params = [max: 10]

        when:
        ops.findAll(params)

        then:
        1 * delegate.findAll(params)
    }

    void "findAll() delegates to the wrapped operations under the bound tenant"() {
        given:
        def delegate = Mock(GormAllOperations)
        def ops = buildOperations(delegate)

        when:
        ops.findAll()

        then:
        1 * delegate.findAll()
    }

    void "findAll(example) delegates to the wrapped operations under the bound tenant"() {
        given:
        def delegate = Mock(GormAllOperations)
        def ops = buildOperations(delegate)
        def example = new Object()

        when:
        ops.findAll(example)

        then:
        1 * delegate.findAll(example)
    }

    void "findAll(example, args) delegates to the wrapped operations under the bound tenant"() {
        given:
        def delegate = Mock(GormAllOperations)
        def ops = buildOperations(delegate)
        def example = new Object()
        def args = [max: 10]

        when:
        ops.findAll(example, args)

        then:
        1 * delegate.findAll(example, args)
    }

    void "first() delegates to the wrapped operations under the bound tenant"() {
        given:
        def delegate = Mock(GormAllOperations)
        def ops = buildOperations(delegate)

        when:
        ops.first()

        then:
        1 * delegate.first()
    }

    void "first(propertyName) delegates to the wrapped operations under the bound tenant"() {
        given:
        def delegate = Mock(GormAllOperations)
        def ops = buildOperations(delegate)
        def propertyName = 'name'

        when:
        ops.first(propertyName)

        then:
        1 * delegate.first(propertyName)
    }

    void "first(queryParams) delegates to the wrapped operations under the bound tenant"() {
        given:
        def delegate = Mock(GormAllOperations)
        def ops = buildOperations(delegate)
        def queryParams = [max: 10]

        when:
        ops.first(queryParams)

        then:
        1 * delegate.first(queryParams)
    }

    void "last() delegates to the wrapped operations under the bound tenant"() {
        given:
        def delegate = Mock(GormAllOperations)
        def ops = buildOperations(delegate)

        when:
        ops.last()

        then:
        1 * delegate.last()
    }

    void "last(propertyName) delegates to the wrapped operations under the bound tenant"() {
        given:
        def delegate = Mock(GormAllOperations)
        def ops = buildOperations(delegate)
        def propertyName = 'name'

        when:
        ops.last(propertyName)

        then:
        1 * delegate.last(propertyName)
    }

    void "methodMissing(methodName, arg) delegates to the wrapped operations under the bound tenant"() {
        given:
        def delegate = Mock(GormAllOperations)
        def ops = buildOperations(delegate)
        def methodName = 'dynamicFinder'
        def arg = 'value'

        when:
        ops.methodMissing(methodName, arg)

        then:
        1 * delegate.methodMissing(methodName, arg)
    }

    void "propertyMissing(property) getter delegates to the wrapped operations under the bound tenant"() {
        given:
        def delegate = Mock(GormAllOperations)
        def ops = buildOperations(delegate)
        def property = 'someProp'

        when:
        ops.propertyMissing(property)

        then:
        1 * delegate.propertyMissing(property)
    }

    void "propertyMissing(property, value) setter delegates to the wrapped operations under the bound tenant"() {
        given:
        def delegate = Mock(GormAllOperations)
        def ops = buildOperations(delegate)
        def property = 'someProp'
        // Not a String: propertyMissing(D, String) and propertyMissing(String, Object) both erase to
        // (Object, String)/(String, Object), so a String value here would make the overload ambiguous.
        def value = 42

        when:
        ops.propertyMissing(property, value)

        then:
        1 * delegate.propertyMissing(property, value)
    }

    void "last(queryParams) delegates to the wrapped operations under the bound tenant"() {
        given:
        def delegate = Mock(GormAllOperations)
        def ops = buildOperations(delegate)
        def queryParams = [max: 10]

        when:
        ops.last(queryParams)

        then:
        1 * delegate.last(queryParams)
    }

    void "findAllWhere(queryMap) delegates to the wrapped operations under the bound tenant"() {
        given:
        def delegate = Mock(GormAllOperations)
        def ops = buildOperations(delegate)
        def queryMap = [name: 'Fred']

        when:
        ops.findAllWhere(queryMap)

        then:
        1 * delegate.findAllWhere(queryMap)
    }

    void "findAllWhere(queryMap, args) delegates to the wrapped operations under the bound tenant"() {
        given:
        def delegate = Mock(GormAllOperations)
        def ops = buildOperations(delegate)
        def queryMap = [name: 'Fred']
        def args = [max: 10]

        when:
        ops.findAllWhere(queryMap, args)

        then:
        1 * delegate.findAllWhere(queryMap, args)
    }

    void "find(example) delegates to the wrapped operations under the bound tenant"() {
        given:
        def delegate = Mock(GormAllOperations)
        def ops = buildOperations(delegate)
        def example = new Object()

        when:
        ops.find(example)

        then:
        1 * delegate.find(example)
    }

    void "find(example, args) delegates to the wrapped operations under the bound tenant"() {
        given:
        def delegate = Mock(GormAllOperations)
        def ops = buildOperations(delegate)
        def example = new Object()
        def args = [max: 10]

        when:
        ops.find(example, args)

        then:
        1 * delegate.find(example, args)
    }

    void "findWhere(queryMap) delegates to the wrapped operations under the bound tenant"() {
        given:
        def delegate = Mock(GormAllOperations)
        def ops = buildOperations(delegate)
        def queryMap = [name: 'Fred']

        when:
        ops.findWhere(queryMap)

        then:
        1 * delegate.findWhere(queryMap)
    }

    void "findWhere(queryMap, args) delegates to the wrapped operations under the bound tenant"() {
        given:
        def delegate = Mock(GormAllOperations)
        def ops = buildOperations(delegate)
        def queryMap = [name: 'Fred']
        def args = [max: 10]

        when:
        ops.findWhere(queryMap, args)

        then:
        1 * delegate.findWhere(queryMap, args)
    }

    void "findOrCreateWhere(queryMap) delegates to the wrapped operations under the bound tenant"() {
        given:
        def delegate = Mock(GormAllOperations)
        def ops = buildOperations(delegate)
        def queryMap = [name: 'Fred']

        when:
        ops.findOrCreateWhere(queryMap)

        then:
        1 * delegate.findOrCreateWhere(queryMap)
    }

    void "findOrSaveWhere(queryMap) delegates to the wrapped operations under the bound tenant"() {
        given:
        def delegate = Mock(GormAllOperations)
        def ops = buildOperations(delegate)
        def queryMap = [name: 'Fred']

        when:
        ops.findOrSaveWhere(queryMap)

        then:
        1 * delegate.findOrSaveWhere(queryMap)
    }

    void "withSession(callable) delegates to the wrapped operations under the bound tenant"() {
        given:
        def delegate = Mock(GormAllOperations)
        def ops = buildOperations(delegate)
        def callable = { -> null }

        when:
        ops.withSession(callable)

        then:
        1 * delegate.withSession(callable)
    }

    void "withDatastoreSession(callable) delegates to the wrapped operations under the bound tenant"() {
        given:
        def delegate = Mock(GormAllOperations)
        def ops = buildOperations(delegate)
        def callable = { -> null }

        when:
        ops.withDatastoreSession(callable)

        then:
        1 * delegate.withDatastoreSession(callable)
    }

    void "withTransaction(callable) delegates to the wrapped operations under the bound tenant"() {
        given:
        def delegate = Mock(GormAllOperations)
        def ops = buildOperations(delegate)
        def callable = { -> null }

        when:
        ops.withTransaction(callable)

        then:
        1 * delegate.withTransaction(callable)
    }

    void "withNewTransaction(callable) delegates to the wrapped operations under the bound tenant"() {
        given:
        def delegate = Mock(GormAllOperations)
        def ops = buildOperations(delegate)
        def callable = { -> null }

        when:
        ops.withNewTransaction(callable)

        then:
        1 * delegate.withNewTransaction(callable)
    }

    void "withTransaction(transactionProperties, callable) delegates to the wrapped operations under the bound tenant"() {
        given:
        def delegate = Mock(GormAllOperations)
        def ops = buildOperations(delegate)
        def transactionProperties = [readOnly: true]
        def callable = { -> null }

        when:
        ops.withTransaction(transactionProperties, callable)

        then:
        1 * delegate.withTransaction(transactionProperties, callable)
    }

    void "withNewTransaction(transactionProperties, callable) delegates to the wrapped operations under the bound tenant"() {
        given:
        def delegate = Mock(GormAllOperations)
        def ops = buildOperations(delegate)
        def transactionProperties = [readOnly: true]
        def callable = { -> null }

        when:
        ops.withNewTransaction(transactionProperties, callable)

        then:
        1 * delegate.withNewTransaction(transactionProperties, callable)
    }

    void "withTransaction(definition, callable) delegates to the wrapped operations under the bound tenant"() {
        given:
        def delegate = Mock(GormAllOperations)
        def ops = buildOperations(delegate)
        def definition = new DefaultTransactionDefinition()
        def callable = { -> null }

        when:
        ops.withTransaction(definition, callable)

        then:
        1 * delegate.withTransaction(definition, callable)
    }

    void "withNewSession(callable) delegates to the wrapped operations under the bound tenant"() {
        given:
        def delegate = Mock(GormAllOperations)
        def ops = buildOperations(delegate)
        def callable = { -> null }

        when:
        ops.withNewSession(callable)

        then:
        1 * delegate.withNewSession(callable)
    }

    void "withStatelessSession(callable) delegates to the wrapped operations under the bound tenant"() {
        given:
        def delegate = Mock(GormAllOperations)
        def ops = buildOperations(delegate)
        def callable = { -> null }

        when:
        ops.withStatelessSession(callable)

        then:
        1 * delegate.withStatelessSession(callable)
    }

    void "executeQuery(query) delegates to the wrapped operations under the bound tenant"() {
        given:
        def delegate = Mock(GormAllOperations)
        def ops = buildOperations(delegate)
        def query = 'from Foo'

        when:
        ops.executeQuery(query)

        then:
        1 * delegate.executeQuery(query)
    }

    void "executeQuery(query, args) delegates to the wrapped operations under the bound tenant"() {
        given:
        def delegate = Mock(GormAllOperations)
        def ops = buildOperations(delegate)
        def query = 'from Foo'
        def args = [max: 10]

        when:
        ops.executeQuery(query, args)

        then:
        1 * delegate.executeQuery(query, args)
    }

    void "executeQuery(query, params, args) delegates to the wrapped operations under the bound tenant"() {
        given:
        def delegate = Mock(GormAllOperations)
        def ops = buildOperations(delegate)
        def query = 'from Foo'
        def params = [name: 'Fred']
        def args = [max: 10]

        when:
        ops.executeQuery(query, params, args)

        then:
        1 * delegate.executeQuery(query, params, args)
    }

    void "executeQuery(query, params) collection delegates to the wrapped operations under the bound tenant"() {
        given:
        def delegate = Mock(GormAllOperations)
        def ops = buildOperations(delegate)
        def query = 'from Foo'
        def params = ['Fred']

        when:
        ops.executeQuery(query, params)

        then:
        1 * delegate.executeQuery(query, params)
    }

    void "executeQuery(query, params) varargs delegates to the wrapped operations under the bound tenant"() {
        given:
        def delegate = Mock(GormAllOperations)
        def ops = buildOperations(delegate)
        def query = 'from Foo'
        def params = ['Fred'] as Object[]

        when:
        ops.executeQuery(query, params)

        then:
        1 * delegate.executeQuery(query, params)
    }

    void "executeQuery(query, params, args) collection delegates to the wrapped operations under the bound tenant"() {
        given:
        def delegate = Mock(GormAllOperations)
        def ops = buildOperations(delegate)
        def query = 'from Foo'
        def params = ['Fred']
        def args = [max: 10]

        when:
        ops.executeQuery(query, params, args)

        then:
        1 * delegate.executeQuery(query, params, args)
    }

    void "executeUpdate(query) delegates to the wrapped operations under the bound tenant"() {
        given:
        def delegate = Mock(GormAllOperations)
        def ops = buildOperations(delegate)
        def query = 'update Foo'

        when:
        ops.executeUpdate(query)

        then:
        1 * delegate.executeUpdate(query)
    }

    void "executeUpdate(query, args) delegates to the wrapped operations under the bound tenant"() {
        given:
        def delegate = Mock(GormAllOperations)
        def ops = buildOperations(delegate)
        def query = 'update Foo'
        def args = [flush: true]

        when:
        ops.executeUpdate(query, args)

        then:
        1 * delegate.executeUpdate(query, args)
    }

    void "executeUpdate(query, params, args) delegates to the wrapped operations under the bound tenant"() {
        given:
        def delegate = Mock(GormAllOperations)
        def ops = buildOperations(delegate)
        def query = 'update Foo'
        def params = [name: 'Fred']
        def args = [flush: true]

        when:
        ops.executeUpdate(query, params, args)

        then:
        1 * delegate.executeUpdate(query, params, args)
    }

    void "executeUpdate(query, params) collection delegates to the wrapped operations under the bound tenant"() {
        given:
        def delegate = Mock(GormAllOperations)
        def ops = buildOperations(delegate)
        def query = 'update Foo'
        def params = ['Fred']

        when:
        ops.executeUpdate(query, params)

        then:
        1 * delegate.executeUpdate(query, params)
    }

    void "executeUpdate(query, params) varargs delegates to the wrapped operations under the bound tenant"() {
        given:
        def delegate = Mock(GormAllOperations)
        def ops = buildOperations(delegate)
        def query = 'update Foo'
        def params = ['Fred'] as Object[]

        when:
        ops.executeUpdate(query, params)

        then:
        1 * delegate.executeUpdate(query, params)
    }

    void "executeUpdate(query, params, args) collection delegates to the wrapped operations under the bound tenant"() {
        given:
        def delegate = Mock(GormAllOperations)
        def ops = buildOperations(delegate)
        def query = 'update Foo'
        def params = ['Fred']
        def args = [flush: true]

        when:
        ops.executeUpdate(query, params, args)

        then:
        1 * delegate.executeUpdate(query, params, args)
    }

    void "find(query) CharSequence delegates to the wrapped operations under the bound tenant"() {
        given:
        def delegate = Mock(GormAllOperations)
        def ops = buildOperations(delegate)
        def query = 'from Foo'

        when:
        ops.find(query)

        then:
        1 * delegate.find(query)
    }

    void "find(query, params) CharSequence delegates to the wrapped operations under the bound tenant"() {
        given:
        def delegate = Mock(GormAllOperations)
        def ops = buildOperations(delegate)
        def query = 'from Foo'
        def params = [name: 'Fred']

        when:
        ops.find(query, params)

        then:
        1 * delegate.find(query, params)
    }

    void "find(query, params, args) CharSequence delegates to the wrapped operations under the bound tenant"() {
        given:
        def delegate = Mock(GormAllOperations)
        def ops = buildOperations(delegate)
        def query = 'from Foo'
        def params = [name: 'Fred']
        def args = [max: 10]

        when:
        ops.find(query, params, args)

        then:
        1 * delegate.find(query, params, args)
    }

    void "find(query, params) CharSequence collection delegates to the wrapped operations under the bound tenant"() {
        given:
        def delegate = Mock(GormAllOperations)
        def ops = buildOperations(delegate)
        def query = 'from Foo'
        def params = ['Fred']

        when:
        ops.find(query, params)

        then:
        1 * delegate.find(query, params)
    }

    void "find(query, params) CharSequence varargs delegates to the wrapped operations under the bound tenant"() {
        given:
        def delegate = Mock(GormAllOperations)
        def ops = buildOperations(delegate)
        def query = 'from Foo'
        def params = ['Fred'] as Object[]

        when:
        ops.find(query, params)

        then:
        1 * delegate.find(query, params)
    }

    void "find(query, params, args) CharSequence collection delegates to the wrapped operations under the bound tenant"() {
        given:
        def delegate = Mock(GormAllOperations)
        def ops = buildOperations(delegate)
        def query = 'from Foo'
        def params = ['Fred']
        def args = [max: 10]

        when:
        ops.find(query, params, args)

        then:
        1 * delegate.find(query, params, args)
    }

    void "findAll(query) CharSequence delegates to the wrapped operations under the bound tenant"() {
        given:
        def delegate = Mock(GormAllOperations)
        def ops = buildOperations(delegate)
        def query = 'from Foo'

        when:
        ops.findAll(query)

        then:
        1 * delegate.findAll(query)
    }

    void "findAll(query, params) CharSequence delegates to the wrapped operations under the bound tenant"() {
        given:
        def delegate = Mock(GormAllOperations)
        def ops = buildOperations(delegate)
        def query = 'from Foo'
        def params = [name: 'Fred']

        when:
        ops.findAll(query, params)

        then:
        1 * delegate.findAll(query, params)
    }

    void "findAll(query, params, args) CharSequence delegates to the wrapped operations under the bound tenant"() {
        given:
        def delegate = Mock(GormAllOperations)
        def ops = buildOperations(delegate)
        def query = 'from Foo'
        def params = [name: 'Fred']
        def args = [max: 10]

        when:
        ops.findAll(query, params, args)

        then:
        1 * delegate.findAll(query, params, args)
    }

    void "findAll(query, params) CharSequence collection delegates to the wrapped operations under the bound tenant"() {
        given:
        def delegate = Mock(GormAllOperations)
        def ops = buildOperations(delegate)
        def query = 'from Foo'
        def params = ['Fred']

        when:
        ops.findAll(query, params)

        then:
        1 * delegate.findAll(query, params)
    }

    void "findAll(query, params) CharSequence varargs delegates to the wrapped operations under the bound tenant"() {
        given:
        def delegate = Mock(GormAllOperations)
        def ops = buildOperations(delegate)
        def query = 'from Foo'
        def params = ['Fred'] as Object[]

        when:
        ops.findAll(query, params)

        then:
        1 * delegate.findAll(query, params)
    }

    void "findAll(query, params, args) CharSequence collection delegates to the wrapped operations under the bound tenant"() {
        given:
        def delegate = Mock(GormAllOperations)
        def ops = buildOperations(delegate)
        def query = 'from Foo'
        def params = ['Fred']
        def args = [max: 10]

        when:
        ops.findAll(query, params, args)

        then:
        1 * delegate.findAll(query, params, args)
    }
}

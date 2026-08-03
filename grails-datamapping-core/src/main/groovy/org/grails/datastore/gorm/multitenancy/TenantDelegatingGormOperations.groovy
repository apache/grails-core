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

import groovy.transform.CompileStatic

import org.springframework.transaction.TransactionDefinition

import grails.gorm.DetachedCriteria
import grails.gorm.api.GormAllOperations
import grails.gorm.multitenancy.Tenants
import org.grails.datastore.gorm.finders.FinderMethod
import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.multitenancy.MultiTenantCapableDatastore
import org.grails.datastore.mapping.query.api.BuildableCriteria
import org.grails.datastore.mapping.query.api.Criteria

/**
 * Wraps each method call in the the given tenant id
 *
 * @author Graeme Rocher
 * @since 6.0
 */
@CompileStatic
class TenantDelegatingGormOperations<D> implements GormAllOperations<D> {

    final Datastore datastore
    final Serializable tenantId
    final GormAllOperations<D> allOperations

    TenantDelegatingGormOperations(Datastore datastore, Serializable tenantId, GormAllOperations<D> allOperations) {
        this.datastore = datastore
        this.tenantId = tenantId
        this.allOperations = allOperations
    }

    @Override
    def propertyMissing(D instance, String name) {
        withTenantId() {
            allOperations.propertyMissing(instance, name)
        }
    }

    @Override
    boolean instanceOf(D instance, Class cls) {
        withTenantId() {
            allOperations.instanceOf(instance, cls)
        }
    }

    @Override
    D lock(D instance) {
        withTenantId() {
            allOperations.lock(instance)
        }
    }

    @Override
    def <T> T mutex(D instance, Closure<T> callable) {
        withTenantId() {
            allOperations.mutex(instance, callable)
        }
    }

    @Override
    D refresh(D instance) {
        withTenantId() {
            allOperations.refresh(instance)
        }
    }

    @Override
    D save(D instance) {
        withTenantId() {
            allOperations.save(instance)
        }
    }

    @Override
    D insert(D instance) {
        withTenantId() {
            allOperations.insert(instance)
        }
    }

    @Override
    D insert(D instance, Map params) {
        withTenantId() {
            allOperations.insert(instance, params)
        }
    }

    @Override
    D merge(D instance, Map params) {
        withTenantId() {
            allOperations.merge(instance, params)
        }
    }

    @Override
    D save(D instance, boolean validate) {
        withTenantId() {
            allOperations.save(instance, validate)
        }
    }

    @Override
    D save(D instance, Map params) {
        withTenantId() {
            allOperations.save(instance, params)
        }
    }

    @Override
    Serializable ident(D instance) {
        withTenantId() {
            allOperations.ident(instance)
        }
    }

    @Override
    D attach(D instance) {
        withTenantId() {
            allOperations.attach(instance)
        }
    }

    @Override
    boolean isAttached(D instance) {
        withTenantId() {
            allOperations.isAttached(instance)
        }
    }

    @Override
    void discard(D instance) {
        withTenantId() {
            allOperations.discard(instance)
        }
    }

    @Override
    void delete(D instance) {
        withTenantId() {
            allOperations.delete(instance)
        }
    }

    @Override
    void delete(D instance, Map params) {
        withTenantId() {
            allOperations.delete(instance, params)
        }
    }

    @Override
    PersistentEntity getGormPersistentEntity() {
        allOperations.gormPersistentEntity
    }

    @Override
    List<FinderMethod> getGormDynamicFinders() {
        return allOperations.gormDynamicFinders
    }

    @Override
    DetachedCriteria<D> where(Closure callable) {
        withTenantId() {
            allOperations.where(callable)
        }
    }

    @Override
    DetachedCriteria<D> whereLazy(Closure callable) {
        withTenantId() {
            allOperations.whereLazy(callable)
        }
    }

    @Override
    DetachedCriteria<D> whereAny(Closure callable) {
        withTenantId() {
            allOperations.whereAny(callable)
        }
    }

    @Override
    List<D> findAll(Closure callable) {
        withTenantId() {
            allOperations.findAll(callable)
        }
    }

    @Override
    List<D> findAll(Map args, Closure callable) {
        withTenantId() {
            allOperations.findAll(args, callable)
        }
    }

    @Override
    D find(Closure callable) {
        withTenantId() {
            allOperations.find(callable)
        }
    }

    @Override
    List<Serializable> saveAll(Object... objectsToSave) {
        withTenantId() {
            allOperations.saveAll(objectsToSave)
        }
    }

    @Override
    List<Serializable> saveAll(Iterable<?> objectsToSave) {
        withTenantId() {
            allOperations.saveAll(objectsToSave)
        }
    }

    @Override
    Number deleteAll() {
        (Number)withTenantId() {
            allOperations.deleteAll()
        }
    }

    @Override
    Number deleteAll(Map params) {
        (Number)withTenantId() {
            allOperations.deleteAll(params)
        }
    }

    @Override
    void deleteAll(Object... objectsToDelete) {
        withTenantId() {
            allOperations.deleteAll(objectsToDelete)
        }
    }

    @Override
    void deleteAll(Map params, Object... objectsToDelete) {
        withTenantId() {
            allOperations.deleteAll(params, objectsToDelete)
        }
    }

    @Override
    void deleteAll(Iterable objectsToDelete) {
        withTenantId() {
            allOperations.deleteAll(objectsToDelete)
        }
    }

    @Override
    void deleteAll(Map params, Iterable objectsToDelete) {
        withTenantId() {
            allOperations.deleteAll(params, objectsToDelete)
        }
    }

    @Override
    D create() {
        allOperations.create()
    }

    @Override
    D get(Serializable id) {
        withTenantId() {
            allOperations.get(id)
        }
    }

    @Override
    D read(Serializable id) {
        withTenantId() {
            allOperations.read(id)
        }
    }

    @Override
    D load(Serializable id) {
        withTenantId() {
            allOperations.load(id)
        }
    }

    @Override
    D proxy(Serializable id) {
        withTenantId() {
            allOperations.proxy(id)
        }
    }

    @Override
    List<D> getAll(Iterable<Serializable> ids) {
        withTenantId() {
            allOperations.getAll(ids)
        }
    }

    @Override
    List<D> getAll(Serializable... ids) {
        withTenantId() {
            allOperations.getAll(ids)
        }
    }

    @Override
    List<D> getAll() {
        withTenantId() {
            allOperations.getAll()
        }
    }

    @Override
    BuildableCriteria createCriteria() {
        withTenantId() {
            allOperations.createCriteria()
        }
    }

    @Override
    def <T> T withCriteria(@DelegatesTo(Criteria) Closure<T> callable) {
        withTenantId() {
            allOperations.withCriteria(callable)
        }
    }

    @Override
    def <T> T withCriteria(Map builderArgs, @DelegatesTo(Criteria) Closure callable) {
        withTenantId() {
            allOperations.withCriteria(builderArgs, callable)
        }
    }

    @Override
    D lock(Serializable id) {
        withTenantId() {
            allOperations.lock(id)
        }
    }

    @Override
    D merge(D d) {
        withTenantId() {
            allOperations.merge(d)
        }
    }

    @Override
    Integer count() {
        withTenantId() {
            allOperations.count()
        }
    }

    @Override
    Integer getCount() {
        withTenantId() {
            allOperations.getCount()
        }
    }

    @Override
    boolean exists(Serializable id) {
        withTenantId() {
            allOperations.exists(id)
        }
    }

    @Override
    List<D> list(Map params) {
        withTenantId() {
            allOperations.list(params)
        }
    }

    @Override
    List<D> list() {
        withTenantId() {
            allOperations.list()
        }
    }

    @Override
    List<D> findAll(Map params) {
        withTenantId() {
            allOperations.findAll(params)
        }
    }

    @Override
    List<D> findAll() {
        withTenantId() {
            allOperations.findAll()
        }
    }

    @Override
    List<D> findAll(D example) {
        withTenantId() {
            allOperations.findAll(example)
        }
    }

    @Override
    List<D> findAll(D example, Map args) {
        withTenantId() {
            allOperations.findAll(example, args)
        }
    }

    @Override
    D first() {
        withTenantId() {
            allOperations.first()
        }
    }

    @Override
    D first(String propertyName) {
        withTenantId() {
            allOperations.first(propertyName)
        }
    }

    @Override
    D first(Map queryParams) {
        withTenantId() {
            allOperations.first(queryParams)
        }
    }

    @Override
    D last() {
        withTenantId() {
            allOperations.last()
        }
    }

    @Override
    D last(String propertyName) {
        withTenantId() {
            allOperations.last(propertyName)
        }
    }

    @Override
    Object methodMissing(String methodName, Object arg) {
        withTenantId() {
            allOperations.methodMissing(methodName, arg)
        }
    }

    @Override
    Object propertyMissing(String property) {
        withTenantId() {
            allOperations.propertyMissing(property)
        }
    }

    @Override
    void propertyMissing(String property, Object value) {
        withTenantId() {
            allOperations.propertyMissing(property, value)
        }
    }

    @Override
    D last(Map queryParams) {
        withTenantId() {
            allOperations.last(queryParams)
        }
    }

    @Override
    List<D> findAllWhere(Map queryMap) {
        withTenantId() {
            allOperations.findAllWhere(queryMap)
        }
    }

    @Override
    List<D> findAllWhere(Map queryMap, Map args) {
        withTenantId() {
            allOperations.findAllWhere(queryMap, args)
        }
    }

    @Override
    D find(D example) {
        withTenantId() {
            allOperations.find(example)
        }
    }

    @Override
    D find(D example, Map args) {
        withTenantId() {
            allOperations.find(example, args)
        }
    }

    @Override
    D findWhere(Map queryMap) {
        withTenantId() {
            allOperations.findWhere(queryMap)
        }
    }

    @Override
    D findWhere(Map queryMap, Map args) {
        withTenantId() {
            allOperations.findWhere(queryMap, args)
        }
    }

    @Override
    D findOrCreateWhere(Map queryMap) {
        withTenantId() {
            allOperations.findOrCreateWhere(queryMap)
        }
    }

    @Override
    D findOrSaveWhere(Map queryMap) {
        withTenantId() {
            allOperations.findOrSaveWhere(queryMap)
        }
    }

    @Override
    def <T> T withSession(Closure<T> callable) {
        withTenantId() {
            allOperations.withSession(callable)
        }
    }

    @Override
    def <T> T withDatastoreSession(Closure<T> callable) {
        withTenantId() {
            allOperations.withDatastoreSession(callable)
        }
    }

    @Override
    def <T> T withTransaction(Closure<T> callable) {
        withTenantId() {
            allOperations.withTransaction(callable)
        }
    }

    @Override
    def <T> T withNewTransaction(Closure<T> callable) {
        withTenantId() {
            allOperations.withNewTransaction(callable)
        }
    }

    @Override
    def <T> T withTransaction(Map transactionProperties, Closure<T> callable) {
        withTenantId() {
            allOperations.withTransaction(transactionProperties, callable)
        }
    }

    @Override
    def <T> T withNewTransaction(Map transactionProperties, Closure<T> callable) {
        withTenantId() {
            allOperations.withNewTransaction(transactionProperties, callable)
        }
    }

    @Override
    def <T> T withTransaction(TransactionDefinition definition, Closure<T> callable) {
        withTenantId() {
            allOperations.withTransaction(definition, callable)
        }
    }

    @Override
    def <T> T withNewSession(Closure<T> callable) {
        withTenantId() {
            allOperations.withNewSession(callable)
        }
    }

    @Override
    def <T> T withStatelessSession(Closure<T> callable) {
        withTenantId() {
            allOperations.withStatelessSession(callable)
        }
    }

    @Override
    List executeQuery(CharSequence query) {
        withTenantId() {
            allOperations.executeQuery(query)
        }
    }

    @Override
    List executeQuery(CharSequence query, Map args) {
        withTenantId() {
            allOperations.executeQuery(query, args)
        }
    }

    @Override
    List executeQuery(CharSequence query, Map params, Map args) {
        withTenantId() {
            allOperations.executeQuery(query, params, args)
        }
    }

    @Override
    List executeQuery(CharSequence query, Collection params) {
        withTenantId() {
            allOperations.executeQuery(query, params)
        }
    }

    @Override
    List executeQuery(CharSequence query, Object... params) {
        withTenantId() {
            allOperations.executeQuery(query, params)
        }
    }

    @Override
    List executeQuery(CharSequence query, Collection params, Map args) {
        withTenantId() {
            allOperations.executeQuery(query, params, args)
        }
    }

    @Override
    Integer executeUpdate(CharSequence query) {
        withTenantId() {
            allOperations.executeUpdate(query)
        }
    }

    @Override
    Integer executeUpdate(CharSequence query, Map args) {
        withTenantId() {
            allOperations.executeUpdate(query, args)
        }
    }

    @Override
    Integer executeUpdate(CharSequence query, Map params, Map args) {
        withTenantId() {
            allOperations.executeUpdate(query, params, args)
        }
    }

    @Override
    Integer executeUpdate(CharSequence query, Collection params) {
        withTenantId() {
            allOperations.executeUpdate(query, params)
        }
    }

    @Override
    Integer executeUpdate(CharSequence query, Object... params) {
        withTenantId() {
            allOperations.executeUpdate(query, params)
        }
    }

    @Override
    Integer executeUpdate(CharSequence query, Collection params, Map args) {
        withTenantId() {
            allOperations.executeUpdate(query, params, args)
        }
    }

    @Override
    D find(CharSequence query) {
        withTenantId() {
            allOperations.find(query)
        }
    }

    @Override
    D find(CharSequence query, Map params) {
        withTenantId() {
            allOperations.find(query, params)
        }
    }

    @Override
    D find(CharSequence query, Map params, Map args) {
        withTenantId() {
            allOperations.find(query, params, args)
        }
    }

    @Override
    D find(CharSequence query, Collection params) {
        withTenantId() {
            allOperations.find(query, params)
        }
    }

    @Override
    D find(CharSequence query, Object[] params) {
        withTenantId() {
            allOperations.find(query, params)
        }
    }

    @Override
    D find(CharSequence query, Collection params, Map args) {
        withTenantId() {
            allOperations.find(query, params, args)
        }
    }

    @Override
    List<D> findAll(CharSequence query) {
        withTenantId() {
            allOperations.findAll(query)
        }
    }

    @Override
    List<D> findAll(CharSequence query, Map params) {
        withTenantId() {
            allOperations.findAll(query, params)
        }
    }

    @Override
    List<D> findAll(CharSequence query, Map params, Map args) {
        withTenantId() {
            allOperations.findAll(query, params, args)
        }
    }

    @Override
    List<D> findAll(CharSequence query, Collection params) {
        withTenantId() {
            allOperations.findAll(query, params)
        }
    }

    @Override
    List<D> findAll(CharSequence query, Object[] params) {
        withTenantId() {
            allOperations.findAll(query, params)
        }
    }

    @Override
    List<D> findAll(CharSequence query, Collection params, Map args) {
        withTenantId() {
            allOperations.findAll(query, params, args)
        }
    }

    @Override
    def <T> T withTenant(Serializable tenantId, Closure<T> callable) {
        allOperations.withTenant(tenantId, callable)
    }

    @Override
    GormAllOperations<D> eachTenant(Closure callable) {
        allOperations.eachTenant(callable)
    }

    @Override
    GormAllOperations<D> withTenant(Serializable tenantId) {
        allOperations.withTenant(tenantId)
    }

    /**
     * Runs the delegated call with this wrapper's tenant bound.
     *
     * Uses the datastore instance this wrapper was constructed with rather than looking one up by
     * its class: the instance is already known, and a type lookup would fail for a datastore that is
     * not registered by type, or resolve to a sibling when several share one type.
     */
    private <T> T withTenantId(Closure<T> callable) {
        if (datastore instanceof MultiTenantCapableDatastore) {
            return Tenants.withId((MultiTenantCapableDatastore) datastore, tenantId, callable)
        }
        return Tenants.withId((Class<? extends Datastore>) datastore.getClass(), tenantId, callable)
    }
}

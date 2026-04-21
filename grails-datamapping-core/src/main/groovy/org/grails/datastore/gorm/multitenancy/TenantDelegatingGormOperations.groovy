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
import grails.gorm.api.GormInstanceOperations
import grails.gorm.api.GormStaticOperations
import grails.gorm.api.GormValidationOperations
import grails.gorm.multitenancy.Tenants
import org.grails.datastore.gorm.finders.FinderMethod
import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.query.api.BuildableCriteria
import org.grails.datastore.mapping.query.api.Criteria
import org.springframework.validation.Errors

/**
 * Wraps each method call in the the given tenant id
 *
 * @author Graeme Rocher
 * @since 6.0
 */
@CompileStatic
class TenantDelegatingGormOperations<D> implements GormAllOperations<D>, GormValidationOperations<D> {

    final Datastore datastore
    final Serializable tenantId
    private GormStaticOperations<D> staticOperations
    private GormInstanceOperations<D> instanceOperations
    private GormValidationOperations<D> validationOperations

    TenantDelegatingGormOperations(Datastore datastore, Serializable tenantId, GormStaticOperations<D> staticOperations) {
        this.datastore = datastore
        this.tenantId = tenantId
        this.staticOperations = staticOperations
        if (staticOperations instanceof GormInstanceOperations) {
            this.instanceOperations = (GormInstanceOperations<D>) staticOperations
        }
        if (staticOperations instanceof GormValidationOperations) {
            this.validationOperations = (GormValidationOperations<D>) staticOperations
        }
    }

    static <D> TenantDelegatingGormOperations<D> createInstance(Datastore datastore, Serializable tenantId, GormInstanceOperations<D> instanceOperations) {
        def wrapper = new TenantDelegatingGormOperations<D>(datastore, tenantId, (GormStaticOperations<D>)null, true)
        wrapper.instanceOperations = instanceOperations
        if (instanceOperations instanceof GormStaticOperations) {
            wrapper.staticOperations = (GormStaticOperations<D>) instanceOperations
        }
        if (instanceOperations instanceof GormValidationOperations) {
            wrapper.validationOperations = (GormValidationOperations<D>) instanceOperations
        }
        return wrapper
    }

    static <D> TenantDelegatingGormOperations<D> createValidation(Datastore datastore, Serializable tenantId, GormValidationOperations<D> validationOperations) {
        def wrapper = new TenantDelegatingGormOperations<D>(datastore, tenantId, (GormStaticOperations<D>)null, true)
        wrapper.validationOperations = validationOperations
        if (validationOperations instanceof GormStaticOperations) {
            wrapper.staticOperations = (GormStaticOperations<D>) validationOperations
        }
        if (validationOperations instanceof GormInstanceOperations) {
            wrapper.instanceOperations = (GormInstanceOperations<D>) validationOperations
        }
        return wrapper
    }

    private TenantDelegatingGormOperations(Datastore datastore, Serializable tenantId, GormStaticOperations<D> staticOperations, boolean internal) {
        this.datastore = datastore
        this.tenantId = tenantId
        this.staticOperations = staticOperations
    }

    private GormStaticOperations<D> getStaticOps() {
        if (staticOperations == null) throw new UnsupportedOperationException("Static operations not supported by this wrapper")
        staticOperations
    }

    private GormInstanceOperations<D> getInstanceOps() {
        if (instanceOperations == null) throw new UnsupportedOperationException("Instance operations not supported by this wrapper")
        instanceOperations
    }

    private GormValidationOperations<D> getValidationOps() {
        if (validationOperations == null) throw new UnsupportedOperationException("Validation operations not supported by this wrapper")
        validationOperations
    }

    @Override
    boolean validate(D instance) {
        Tenants.withId((Class<Datastore>) datastore.getClass(), tenantId) {
            getValidationOps().validate(instance)
        }
    }

    @Override
    boolean validate(D instance, Map arguments) {
        Tenants.withId((Class<Datastore>) datastore.getClass(), tenantId) {
            getValidationOps().validate(instance, arguments)
        }
    }

    @Override
    boolean validate(D instance, List fields) {
        Tenants.withId((Class<Datastore>) datastore.getClass(), tenantId) {
            getValidationOps().validate(instance, fields)
        }
    }

    @Override
    Errors getErrors(D instance) {
        Tenants.withId((Class<Datastore>) datastore.getClass(), tenantId) {
            getValidationOps().getErrors(instance)
        }
    }

    @Override
    void setErrors(D instance, Errors errors) {
        Tenants.withId((Class<Datastore>) datastore.getClass(), tenantId) {
            getValidationOps().setErrors(instance, errors)
        }
    }

    @Override
    void clearErrors(D instance) {
        Tenants.withId((Class<Datastore>) datastore.getClass(), tenantId) {
            getValidationOps().clearErrors(instance)
        }
    }

    @Override
    boolean hasErrors(D instance) {
        Tenants.withId((Class<Datastore>) datastore.getClass(), tenantId) {
            getValidationOps().hasErrors(instance)
        }
    }

    @Override
    def propertyMissing(D instance, String name) {
        Tenants.withId((Class<Datastore>) datastore.getClass(), tenantId) {
            getInstanceOps().propertyMissing(instance, name)
        }
    }

    @Override
    boolean instanceOf(D instance, Class cls) {
        Tenants.withId((Class<Datastore>) datastore.getClass(), tenantId) {
            getInstanceOps().instanceOf(instance, cls)
        }
    }

    @Override
    D lock(D instance) {
        Tenants.withId((Class<Datastore>) datastore.getClass(), tenantId) {
            getInstanceOps().lock(instance)
        }
    }

    @Override
    def <T> T mutex(D instance, Closure<T> callable) {
        Tenants.withId((Class<Datastore>) datastore.getClass(), tenantId) {
            getInstanceOps().mutex(instance, callable)
        }
    }

    @Override
    D refresh(D instance) {
        Tenants.withId((Class<Datastore>) datastore.getClass(), tenantId) {
            getInstanceOps().refresh(instance)
        }
    }

    @Override
    D save(D instance) {
        Tenants.withId((Class<Datastore>) datastore.getClass(), tenantId) {
            getInstanceOps().save(instance)
        }
    }

    @Override
    D insert(D instance) {
        Tenants.withId((Class<Datastore>) datastore.getClass(), tenantId) {
            getInstanceOps().insert(instance)
        }
    }

    @Override
    D insert(D instance, Map params) {
        Tenants.withId((Class<Datastore>) datastore.getClass(), tenantId) {
            getInstanceOps().insert(instance, params)
        }
    }

    @Override
    D merge(D instance, Map params) {
        Tenants.withId((Class<Datastore>) datastore.getClass(), tenantId) {
            getInstanceOps().merge(instance, params)
        }
    }

    @Override
    D save(D instance, boolean validate) {
        Tenants.withId((Class<Datastore>) datastore.getClass(), tenantId) {
            getInstanceOps().save(instance, validate)
        }
    }

    @Override
    D save(D instance, Map params) {
        Tenants.withId((Class<Datastore>) datastore.getClass(), tenantId) {
            getInstanceOps().save(instance, params)
        }
    }

    @Override
    Serializable ident(D instance) {
        Tenants.withId((Class<Datastore>) datastore.getClass(), tenantId) {
            getInstanceOps().ident(instance)
        }
    }

    @Override
    D attach(D instance) {
        Tenants.withId((Class<Datastore>) datastore.getClass(), tenantId) {
            getInstanceOps().attach(instance)
        }
    }

    @Override
    boolean isAttached(D instance) {
        Tenants.withId((Class<Datastore>) datastore.getClass(), tenantId) {
            getInstanceOps().isAttached(instance)
        }
    }

    @Override
    void discard(D instance) {
        Tenants.withId((Class<Datastore>) datastore.getClass(), tenantId) {
            getInstanceOps().discard(instance)
        }
    }

    @Override
    void delete(D instance) {
        Tenants.withId((Class<Datastore>) datastore.getClass(), tenantId) {
            getInstanceOps().delete(instance)
        }
    }

    @Override
    void delete(D instance, Map params) {
        Tenants.withId((Class<Datastore>) datastore.getClass(), tenantId) {
            getInstanceOps().delete(instance, params)
        }
    }

    @Override
    boolean isDirty(D instance, String fieldName) {
        Tenants.withId((Class<Datastore>) datastore.getClass(), tenantId) {
            getInstanceOps().isDirty(instance, fieldName)
        }
    }

    @Override
    boolean isDirty(D instance) {
        Tenants.withId((Class<Datastore>) datastore.getClass(), tenantId) {
            getInstanceOps().isDirty(instance)
        }
    }

    @Override
    List getDirtyPropertyNames(D instance) {
        Tenants.withId((Class<Datastore>) datastore.getClass(), tenantId) {
            getInstanceOps().getDirtyPropertyNames(instance)
        }
    }

    @Override
    Object getPersistentValue(D instance, String fieldName) {
        Tenants.withId((Class<Datastore>) datastore.getClass(), tenantId) {
            getInstanceOps().getPersistentValue(instance, fieldName)
        }
    }

    @Override
    PersistentEntity getGormPersistentEntity() {
        getStaticOps().gormPersistentEntity
    }

    @Override
    List<FinderMethod> getGormDynamicFinders() {
        return getStaticOps().gormDynamicFinders
    }

    @Override
    DetachedCriteria<D> where(Closure callable) {
        Tenants.withId((Class<Datastore>) datastore.getClass(), tenantId) {
            getStaticOps().where(callable)
        }
    }

    @Override
    DetachedCriteria<D> whereLazy(Closure callable) {
        Tenants.withId((Class<Datastore>) datastore.getClass(), tenantId) {
            getStaticOps().whereLazy(callable)
        }
    }

    @Override
    DetachedCriteria<D> whereAny(Closure callable) {
        Tenants.withId((Class<Datastore>) datastore.getClass(), tenantId) {
            getStaticOps().whereAny(callable)
        }
    }

    @Override
    List<D> findAll(Closure callable) {
        Tenants.withId((Class<Datastore>) datastore.getClass(), tenantId) {
            getStaticOps().findAll(callable)
        }
    }

    @Override
    List<D> findAll(Map args, Closure callable) {
        Tenants.withId((Class<Datastore>) datastore.getClass(), tenantId) {
            getStaticOps().findAll(args, callable)
        }
    }

    @Override
    D find(Closure callable) {
        Tenants.withId((Class<Datastore>) datastore.getClass(), tenantId) {
            getStaticOps().find(callable)
        }
    }

    @Override
    List<Serializable> saveAll(Object... objectsToSave) {
        Tenants.withId((Class<Datastore>) datastore.getClass(), tenantId) {
            getStaticOps().saveAll(objectsToSave)
        }
    }

    @Override
    List<Serializable> saveAll(Iterable<?> objectsToSave) {
        Tenants.withId((Class<Datastore>) datastore.getClass(), tenantId) {
            getStaticOps().saveAll(objectsToSave)
        }
    }

    @Override
    void deleteAll(Object... objectsToDelete) {
        Tenants.withId((Class<Datastore>) datastore.getClass(), tenantId) {
            getStaticOps().deleteAll(objectsToDelete)
        }
    }

    @Override
    void deleteAll(Iterable objectsToDelete) {
        Tenants.withId((Class<Datastore>) datastore.getClass(), tenantId) {
            getStaticOps().deleteAll(objectsToDelete)
        }
    }

    @Override
    D create() {
        getStaticOps().create()
    }

    @Override
    D get(Serializable id) {
        Tenants.withId((Class<Datastore>) datastore.getClass(), tenantId) {
            getStaticOps().get(id)
        }
    }

    @Override
    D read(Serializable id) {
        Tenants.withId((Class<Datastore>) datastore.getClass(), tenantId) {
            getStaticOps().read(id)
        }
    }

    @Override
    D load(Serializable id) {
        Tenants.withId((Class<Datastore>) datastore.getClass(), tenantId) {
            getStaticOps().load(id)
        }
    }

    @Override
    D proxy(Serializable id) {
        Tenants.withId((Class<Datastore>) datastore.getClass(), tenantId) {
            getStaticOps().proxy(id)
        }
    }

    @Override
    List<D> getAll(Iterable<Serializable> ids) {
        Tenants.withId((Class<Datastore>) datastore.getClass(), tenantId) {
            getStaticOps().getAll(ids)
        }
    }

    @Override
    List<D> getAll(Serializable... ids) {
        Tenants.withId((Class<Datastore>) datastore.getClass(), tenantId) {
            getStaticOps().getAll(ids)
        }
    }

    @Override
    List<D> getAll() {
        Tenants.withId((Class<Datastore>) datastore.getClass(), tenantId) {
            getStaticOps().getAll()
        }
    }

    @Override
    BuildableCriteria createCriteria() {
        Tenants.withId((Class<Datastore>) datastore.getClass(), tenantId) {
            getStaticOps().createCriteria()
        }
    }

    @Override
    def <T> T withCriteria(@DelegatesTo(Criteria) Closure<T> callable) {
        Tenants.withId((Class<Datastore>) datastore.getClass(), tenantId) {
            getStaticOps().withCriteria(callable)
        }
    }

    @Override
    def <T> T withCriteria(Map builderArgs, @DelegatesTo(Criteria) Closure callable) {
        Tenants.withId((Class<Datastore>) datastore.getClass(), tenantId) {
            getStaticOps().withCriteria(builderArgs, callable)
        }
    }

    @Override
    D lock(Serializable id) {
        Tenants.withId((Class<Datastore>) datastore.getClass(), tenantId) {
            getStaticOps().lock(id)
        }
    }

    @Override
    D merge(D d) {
        Tenants.withId((Class<Datastore>) datastore.getClass(), tenantId) {
            getStaticOps().merge(d)
        }
    }

    @Override
    Integer count() {
        Tenants.withId((Class<Datastore>) datastore.getClass(), tenantId) {
            getStaticOps().count()
        }
    }

    @Override
    Integer getCount() {
        Tenants.withId((Class<Datastore>) datastore.getClass(), tenantId) {
            getStaticOps().getCount()
        }
    }

    @Override
    boolean exists(Serializable id) {
        Tenants.withId((Class<Datastore>) datastore.getClass(), tenantId) {
            getStaticOps().exists(id)
        }
    }

    @Override
    List<D> list(Map params) {
        Tenants.withId((Class<Datastore>) datastore.getClass(), tenantId) {
            getStaticOps().list(params)
        }
    }

    @Override
    List<D> list() {
        Tenants.withId((Class<Datastore>) datastore.getClass(), tenantId) {
            getStaticOps().list()
        }
    }

    @Override
    List<D> findAll(Map params) {
        Tenants.withId((Class<Datastore>) datastore.getClass(), tenantId) {
            getStaticOps().findAll(params)
        }
    }

    @Override
    List<D> findAll() {
        Tenants.withId((Class<Datastore>) datastore.getClass(), tenantId) {
            getStaticOps().findAll()
        }
    }

    @Override
    List<D> findAll(D example) {
        Tenants.withId((Class<Datastore>) datastore.getClass(), tenantId) {
            getStaticOps().findAll(example)
        }
    }

    @Override
    List<D> findAll(D example, Map args) {
        Tenants.withId((Class<Datastore>) datastore.getClass(), tenantId) {
            getStaticOps().findAll(example, args)
        }
    }

    @Override
    D first() {
        Tenants.withId((Class<Datastore>) datastore.getClass(), tenantId) {
            getStaticOps().first()
        }
    }

    @Override
    D first(String propertyName) {
        Tenants.withId((Class<Datastore>) datastore.getClass(), tenantId) {
            getStaticOps().first(propertyName)
        }
    }

    @Override
    D first(Map queryParams) {
        Tenants.withId((Class<Datastore>) datastore.getClass(), tenantId) {
            getStaticOps().first(queryParams)
        }
    }

    @Override
    D last() {
        Tenants.withId((Class<Datastore>) datastore.getClass(), tenantId) {
            getStaticOps().last()
        }
    }

    @Override
    D last(String propertyName) {
        Tenants.withId((Class<Datastore>) datastore.getClass(), tenantId) {
            getStaticOps().last(propertyName)
        }
    }

    @Override
    Object methodMissing(String methodName, Object args) {
        Tenants.withId((Class<Datastore>) datastore.getClass(), tenantId) {
            if (args instanceof Object[]) {
                return getStaticOps().methodMissing(methodName, (Object[])args)
            }
            return getStaticOps().methodMissing(methodName, args)
        }
    }

    @Override
    Object propertyMissing(String property) {
        Tenants.withId((Class<Datastore>) datastore.getClass(), tenantId) {
            getStaticOps().propertyMissing(property)
        }
    }

    @Override
    void propertyMissing(String property, Object value) {
        Tenants.withId((Class<Datastore>) datastore.getClass(), tenantId) {
            getStaticOps().propertyMissing(property, value)
        }
    }

    @Override
    D last(Map queryParams) {
        Tenants.withId((Class<Datastore>) datastore.getClass(), tenantId) {
            getStaticOps().last(queryParams)
        }
    }

    @Override
    List<D> findAllWhere(Map queryMap) {
        Tenants.withId((Class<Datastore>) datastore.getClass(), tenantId) {
            getStaticOps().findAllWhere(queryMap)
        }
    }

    @Override
    List<D> findAllWhere(Map queryMap, Map args) {
        Tenants.withId((Class<Datastore>) datastore.getClass(), tenantId) {
            getStaticOps().findAllWhere(queryMap, args)
        }
    }

    @Override
    D find(D example) {
        Tenants.withId((Class<Datastore>) datastore.getClass(), tenantId) {
            getStaticOps().find(example)
        }
    }

    @Override
    D find(D example, Map args) {
        Tenants.withId((Class<Datastore>) datastore.getClass(), tenantId) {
            getStaticOps().find(example, args)
        }
    }

    @Override
    D findWhere(Map queryMap) {
        Tenants.withId((Class<Datastore>) datastore.getClass(), tenantId) {
            getStaticOps().findWhere(queryMap)
        }
    }

    @Override
    D findWhere(Map queryMap, Map args) {
        Tenants.withId((Class<Datastore>) datastore.getClass(), tenantId) {
            getStaticOps().findWhere(queryMap, args)
        }
    }

    @Override
    D findOrCreateWhere(Map queryMap) {
        Tenants.withId((Class<Datastore>) datastore.getClass(), tenantId) {
            getStaticOps().findOrCreateWhere(queryMap)
        }
    }

    @Override
    D findOrSaveWhere(Map queryMap) {
        Tenants.withId((Class<Datastore>) datastore.getClass(), tenantId) {
            getStaticOps().findOrSaveWhere(queryMap)
        }
    }

    @Override
    def <T> T withSession(Closure<T> callable) {
        Tenants.withId((Class<Datastore>) datastore.getClass(), tenantId) {
            getStaticOps().withSession(callable)
        }
    }

    @Override
    def <T> T withDatastoreSession(Closure<T> callable) {
        Tenants.withId((Class<Datastore>) datastore.getClass(), tenantId) {
            getStaticOps().withDatastoreSession(callable)
        }
    }

    @Override
    def <T> T withTransaction(Closure<T> callable) {
        Tenants.withId((Class<Datastore>) datastore.getClass(), tenantId) {
            getStaticOps().withTransaction(callable)
        }
    }

    @Override
    def <T> T withNewTransaction(Closure<T> callable) {
        Tenants.withId((Class<Datastore>) datastore.getClass(), tenantId) {
            getStaticOps().withNewTransaction(callable)
        }
    }

    @Override
    def <T> T withTransaction(Map transactionProperties, Closure<T> callable) {
        Tenants.withId((Class<Datastore>) datastore.getClass(), tenantId) {
            getStaticOps().withTransaction(transactionProperties, callable)
        }
    }

    @Override
    def <T> T withNewTransaction(Map transactionProperties, Closure<T> callable) {
        Tenants.withId((Class<Datastore>) datastore.getClass(), tenantId) {
            getStaticOps().withNewTransaction(transactionProperties, callable)
        }
    }

    @Override
    def <T> T withTransaction(TransactionDefinition definition, Closure<T> callable) {
        Tenants.withId((Class<Datastore>) datastore.getClass(), tenantId) {
            getStaticOps().withTransaction(definition, callable)
        }
    }

    @Override
    def <T> T withNewSession(Closure<T> callable) {
        Tenants.withId((Class<Datastore>) datastore.getClass(), tenantId) {
            getStaticOps().withNewSession(callable)
        }
    }

    @Override
    def <T> T withStatelessSession(Closure<T> callable) {
        Tenants.withId((Class<Datastore>) datastore.getClass(), tenantId) {
            getStaticOps().withStatelessSession(callable)
        }
    }

    @Override
    List executeQuery(CharSequence query) {
        Tenants.withId((Class<Datastore>) datastore.getClass(), tenantId) {
            getStaticOps().executeQuery(query)
        }
    }

    @Override
    List executeQuery(CharSequence query, Map args) {
        Tenants.withId((Class<Datastore>) datastore.getClass(), tenantId) {
            getStaticOps().executeQuery(query, args)
        }
    }

    @Override
    List executeQuery(CharSequence query, Map params, Map args) {
        Tenants.withId((Class<Datastore>) datastore.getClass(), tenantId) {
            getStaticOps().executeQuery(query, params, args)
        }
    }

    @Override
    List executeQuery(CharSequence query, Collection params) {
        Tenants.withId((Class<Datastore>) datastore.getClass(), tenantId) {
            getStaticOps().executeQuery(query, params)
        }
    }

    @Override
    List executeQuery(CharSequence query, Object... params) {
        Tenants.withId((Class<Datastore>) datastore.getClass(), tenantId) {
            getStaticOps().executeQuery(query, params)
        }
    }

    @Override
    List executeQuery(CharSequence query, Collection params, Map args) {
        Tenants.withId((Class<Datastore>) datastore.getClass(), tenantId) {
            getStaticOps().executeQuery(query, params, args)
        }
    }

    @Override
    Integer executeUpdate(CharSequence query) {
        Tenants.withId((Class<Datastore>) datastore.getClass(), tenantId) {
            getStaticOps().executeUpdate(query)
        }
    }

    @Override
    Integer executeUpdate(CharSequence query, Map args) {
        Tenants.withId((Class<Datastore>) datastore.getClass(), tenantId) {
            getStaticOps().executeUpdate(query, args)
        }
    }

    @Override
    Integer executeUpdate(CharSequence query, Map params, Map args) {
        Tenants.withId((Class<Datastore>) datastore.getClass(), tenantId) {
            getStaticOps().executeUpdate(query, params, args)
        }
    }

    @Override
    Integer executeUpdate(CharSequence query, Collection params) {
        Tenants.withId((Class<Datastore>) datastore.getClass(), tenantId) {
            getStaticOps().executeUpdate(query, params)
        }
    }

    @Override
    Integer executeUpdate(CharSequence query, Object... params) {
        Tenants.withId((Class<Datastore>) datastore.getClass(), tenantId) {
            getStaticOps().executeUpdate(query, params)
        }
    }

    @Override
    Integer executeUpdate(CharSequence query, Collection params, Map args) {
        Tenants.withId((Class<Datastore>) datastore.getClass(), tenantId) {
            getStaticOps().executeUpdate(query, params, args)
        }
    }

    @Override
    D find(CharSequence query) {
        Tenants.withId((Class<Datastore>) datastore.getClass(), tenantId) {
            getStaticOps().find(query)
        }
    }

    @Override
    D find(CharSequence query, Map params) {
        Tenants.withId((Class<Datastore>) datastore.getClass(), tenantId) {
            getStaticOps().find(query, params)
        }
    }

    @Override
    D find(CharSequence query, Map params, Map args) {
        Tenants.withId((Class<Datastore>) datastore.getClass(), tenantId) {
            getStaticOps().find(query, params, args)
        }
    }

    @Override
    D find(CharSequence query, Collection params) {
        Tenants.withId((Class<Datastore>) datastore.getClass(), tenantId) {
            getStaticOps().find(query, params)
        }
    }

    @Override
    D find(CharSequence query, Object[] params) {
        Tenants.withId((Class<Datastore>) datastore.getClass(), tenantId) {
            getStaticOps().find(query, params)
        }
    }

    @Override
    D find(CharSequence query, Collection params, Map args) {
        Tenants.withId((Class<Datastore>) datastore.getClass(), tenantId) {
            getStaticOps().find(query, params, args)
        }
    }

    @Override
    List<D> findAll(CharSequence query) {
        Tenants.withId((Class<Datastore>) datastore.getClass(), tenantId) {
            getStaticOps().findAll(query)
        }
    }

    @Override
    List<D> findAll(CharSequence query, Map params) {
        Tenants.withId((Class<Datastore>) datastore.getClass(), tenantId) {
            getStaticOps().findAll(query, params)
        }
    }

    @Override
    List<D> findAll(CharSequence query, Map params, Map args) {
        Tenants.withId((Class<Datastore>) datastore.getClass(), tenantId) {
            getStaticOps().findAll(query, params, args)
        }
    }

    @Override
    List<D> findAll(CharSequence query, Collection params) {
        Tenants.withId((Class<Datastore>) datastore.getClass(), tenantId) {
            getStaticOps().findAll(query, params)
        }
    }

    @Override
    List<D> findAll(CharSequence query, Object[] params) {
        Tenants.withId((Class<Datastore>) datastore.getClass(), tenantId) {
            getStaticOps().findAll(query, params)
        }
    }

    @Override
    List<D> findAll(CharSequence query, Collection params, Map args) {
        Tenants.withId((Class<Datastore>) datastore.getClass(), tenantId) {
            getStaticOps().findAll(query, params, args)
        }
    }

    @Override
    def <T> T withTenant(Serializable tenantId, Closure<T> callable) {
        getStaticOps().withTenant(tenantId, callable)
    }

    @Override
    GormAllOperations<D> eachTenant(Closure callable) {
        getStaticOps().eachTenant(callable)
    }

    @Override
    GormAllOperations<D> withTenant(Serializable tenantId) {
        getStaticOps().withTenant(tenantId)
    }
}

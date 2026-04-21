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
/*
 * Copyright 2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.orm.hibernate

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import org.grails.datastore.mapping.query.Query as GormQuery

import org.hibernate.Session
import org.hibernate.SessionFactory
import org.hibernate.jpa.AvailableHints

import org.springframework.core.convert.ConversionService
import org.springframework.transaction.PlatformTransactionManager

import grails.orm.HibernateCriteriaBuilder
import grails.gorm.DetachedCriteria
import org.grails.datastore.gorm.GormStaticApi
import org.grails.datastore.gorm.finders.FinderMethod
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.core.connections.ConnectionSourcesProvider
import org.grails.datastore.mapping.proxy.ProxyHandler
import org.grails.datastore.mapping.query.api.BuildableCriteria as GrailsCriteria
import org.grails.datastore.mapping.query.event.PostQueryEvent
import org.grails.datastore.mapping.query.event.PreQueryEvent
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity
import org.grails.orm.hibernate.query.HibernateHqlQueryCreator
import org.grails.orm.hibernate.query.HibernatePagedResultList
import org.grails.orm.hibernate.query.MutationHqlQuery
import org.grails.orm.hibernate.query.HibernateQuery
import org.grails.orm.hibernate.query.HqlListQueryBuilder
import org.grails.orm.hibernate.query.HqlQueryContext
import org.grails.orm.hibernate.support.HibernateRuntimeUtils

/**
 * The implementation of the GORM static method contract for Hibernate
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Slf4j
@CompileStatic
//TODO Duplication!!
class HibernateGormStaticApi<D> extends GormStaticApi<D> {

    protected ClassLoader classLoader
    private HibernateGormInstanceApi<D> instanceApi

    HibernateGormStaticApi(Class<D> persistentClass, HibernateDatastore datastore, List<FinderMethod> finders,
                           ClassLoader classLoader, PlatformTransactionManager transactionManager, String qualifier = null) {
        super(persistentClass, (HibernateDatastore)null, finders, transactionManager)
        this.classLoader = classLoader
        this.instanceApi = new HibernateGormInstanceApi<>(persistentClass, (HibernateDatastore)null, classLoader)
    }

    protected ConversionService getConversionService() {
        getDatastore().mappingContext.conversionService
    }

    protected ProxyHandler getProxyHandler() {
        getDatastore().mappingContext.proxyHandler
    }

    protected Class getIdentityType() {
        getPersistentEntity().identity?.type
    }

    @Override
    HibernateDatastore getDatastore() {
        (HibernateDatastore) super.getDatastore()
    }

    GrailsHibernateTemplate getHibernateTemplate() {
        (GrailsHibernateTemplate) getDatastore().getHibernateTemplate()
    }

    HibernateSession getHibernateSession() {
        getDatastore().getHibernateSession()
    }

    SessionFactory getSessionFactory() {
        getDatastore().getSessionFactory()
    }

    String getQualifier() {
        def dsNames = getPersistentEntity().mapping.mappedForm.datasources
        if (dsNames) {
            String first = dsNames[0]
            if (first != ConnectionSource.DEFAULT && first != 'ALL') {
                return first
            }
        }
        null
    }

    GormStaticApi<D> getApi(String qualifier) {
        (GormStaticApi<D>) HibernateGormEnhancer.findStaticApi(persistentClass, qualifier)
    }

    @Override
    DetachedCriteria<D> where(Closure callable) {
        new HibernateDetachedCriteria<D>(persistentClass).build(callable)
    }

    @Override
    DetachedCriteria<D> whereLazy(Closure callable) {
        new HibernateDetachedCriteria<D>(persistentClass).buildLazy(callable)
    }

    @Override
    DetachedCriteria<D> whereAny(Closure callable) {
        (DetachedCriteria<D>) new HibernateDetachedCriteria<D>(persistentClass).or(callable)
    }

    @Override
    D merge(D d) {
        instanceApi.merge(d)
    }

    @Override
    <T> T withNewSession(Closure<T> callable) {
        if (persistentEntity.isMultiTenant()) {
            return ((HibernateDatastore) datastore).withNewSession(callable)
        }
        String q = getQualifier()
        if (q != null && q != ConnectionSource.DEFAULT) {
            return ((HibernateDatastore) datastore).withNewSession(q, callable)
        }
        ((HibernateDatastore) datastore).withNewSession(callable)
    }

    @Override
    <T> T withSession(Closure<T> callable) {
        if (persistentEntity.isMultiTenant()) {
            return ((HibernateDatastore) datastore).withSession(callable)
        }
        String q = getQualifier()
        if (q != null && q != ConnectionSource.DEFAULT) {
            return ((HibernateDatastore) datastore).withSession(q, callable)
        }
        ((HibernateDatastore) datastore).withSession(callable)
    }

    D get(Serializable id) {
        if (id == null) {
            return null
        }

        id = convertIdentifier(id)

        if (id == null) {
            return null
        }

        if (getPersistentEntity().isMultiTenant()) {
            // for multi-tenant entities we process get(..) via a query
            (D) getHibernateTemplate().execute { Session session ->
                new HibernateQuery(getHibernateSession(), (GrailsHibernatePersistentEntity) getPersistentEntity()).idEq(id).singleResult()
            }
        } else {
            // for non multi-tenant entities we process get(..) via the second level cache
            (D) getHibernateTemplate().execute { Session session -> session.find(getPersistentEntity().javaClass, id) }
        }
    }

    D read(Serializable id) {
        if (id == null) {
            return null
        }
        id = convertIdentifier(id)

        if (id == null) {
            return null
        }

        String hql = "from ${getPersistentEntity().name} where ${getPersistentEntity().identity.name} = :id"
        Map<String, Object> args = [(AvailableHints.HINT_READ_ONLY): (Object) true]
        proxyHandler.unwrap(doSingleInternal(hql, [id: id], [], args, false)) as D
    }

    @Override
    D load(Serializable id) {
        id = convertIdentifier(id)
        if (id != null) {
            return (D) getHibernateTemplate().load((Class) persistentClass, id)
        } else {
            return null
        }
    }

    @Override
    D proxy(Serializable id) {
        id = convertIdentifier(id)
        if (id != null) {
            // Use the configured MappingContext proxyFactory (e.g. GroovyProxyFactory) so proxies are created correctly
            def proxyFactory = getDatastore().getMappingContext().getProxyFactory()
            return (D) proxyFactory.createProxy(getDatastore().currentSession, (Class) persistentClass, id)
        } else {
            return null
        }
    }

    @Override
    List<D> getAll() {
        doListInternal("from ${getPersistentEntity().name}".toString(), [:], [], [:], false)
    }

    @Override
    Integer count() {
        String entity = getPersistentEntity().name
        doSingleInternal("select count(*) from $entity" as String, [:], [], [:], false) as Integer
    }

    @Override
    boolean exists(Serializable id) {
        def converted = convertIdentifier(id)
        if (converted == null) return false
        String entity = getPersistentEntity().name
        String idName = getPersistentEntity().identity.name
        (doSingleInternal("select count(*) from $entity where $idName = :id" as String, [id: converted], [], [:], false) as Long) > 0
    }

    @Override
    D first(Map m) {
        def list = list(m)
        list.isEmpty() ? null : list.first()
    }

    @Override
    D last(Map m) {
        def list = list(m)
        list.isEmpty() ? null : list.last()
    }

    @Override
    D find(CharSequence query, Map namedParams, Map args) {
        doSingleInternal(query, namedParams, [], args, false)
    }

    @Override
    D find(CharSequence query, Collection positionalParams, Map args) {
        doSingleInternal(query, [:], positionalParams, args, false)
    }

    @Override
    List<D> findAll(CharSequence query, Map namedParams, Map args) {
        doListInternal(query, namedParams, [], args, false)
    }

    D findWithNativeSql(CharSequence sql, Map args = Collections.emptyMap()) {
        doSingleInternal(sql, [:], [], args, true) as D
    }

    List<D> findAllWithNativeSql(CharSequence query, Map args = Collections.emptyMap()) {
        doListInternal(query, [:], [], args, true)
    }

    /** @deprecated Use {@link #findWithNativeSql(CharSequence, Map)} — the new name makes the native SQL risk surface explicit. */
    @Deprecated
    D findWithSql(CharSequence sql, Map args = Collections.emptyMap()) {
        findWithNativeSql(sql, args)
    }

    /** @deprecated Use {@link #findAllWithNativeSql(CharSequence, Map)} — the new name makes the native SQL risk surface explicit. */
    @Deprecated
    List<D> findAllWithSql(CharSequence query, Map args = Collections.emptyMap()) {
        findAllWithNativeSql(query, args)
    }

    @Override
    List<D> findAll(CharSequence query) {
        requireGString(query, 'findAll')
        doListInternal(query, [:], [], [:], false)
    }

    @Override
    List executeQuery(CharSequence query) {
        requireGString(query, 'executeQuery')
        doListInternal(query, [:], [], [:], false)
    }

    @Override
    Integer executeUpdate(CharSequence query) {
        requireGString(query, 'executeUpdate')
        doInternalExecuteUpdate(query, [:], [], [:])
    }

    @Override
    D find(CharSequence query) {
        requireGString(query, 'find')
        doSingleInternal(query, [:], [], [:], false)
    }

    private static void requireGString(CharSequence query, String method) {
        if (!(query instanceof GString)) {
            throw new UnsupportedOperationException(
                    "${method}(CharSequence) only accepts a Groovy GString with interpolated parameters " +
                            "(e.g. ${method}(\"from Foo where bar = \${value}\")). " +
                            "Use the parameterized overload ${method}(CharSequence, Map) or ${method}(CharSequence, Collection, Map) " +
                            'to pass a plain String query safely.'
            )
        }
    }

    @Override
    D find(CharSequence query, Map params) {
        doSingleInternal(query, params, [], params, false)
    }

    @Override
    List<D> findAll(CharSequence query, Map params) {
        doListInternal(query, params, [], params, false)
    }

    @Override
    List executeQuery(CharSequence query, Map args) {
        doListInternal(query, args, [], args, false)
    }

    @Override
    Integer executeUpdate(CharSequence query, Map args) {
        doInternalExecuteUpdate(query, args, [], args)
    }

    @Override
    D findWhere(Map queryMap, Map args) {
        if (!queryMap) return null
        Map coercedMap = queryMap.collectEntries { k, v -> [k.toString(), v] }
        String hql = buildWhereHql(coercedMap)
        doSingleInternal(hql, coercedMap, [], args, false)
    }

    @Override
    List<D> findAllWhere(Map queryMap, Map args) {
        if (!queryMap) return null
        Map coercedMap = queryMap.collectEntries { k, v -> [k.toString(), v] }
        String hql = buildWhereHql(coercedMap)
        doListInternal(hql, coercedMap, [], args, false)
    }

    private String buildWhereHql(Map queryMap) {
        String whereClause = queryMap.keySet().collect { Object key -> "$key = :$key" }.join(' and ')
        return "from ${persistentEntity.name} where $whereClause"
    }

    @Override
    List executeQuery(CharSequence query, Map namedParams, Map args) {
        doListInternal(query, namedParams, [], args, false)
    }

    @Override
    List executeQuery(CharSequence query, Collection positionalParams, Map args) {
        return doListInternal(query, [:], positionalParams, args, false)
    }

    @Override
    List<D> findAll(CharSequence query, Collection positionalParams, Map args) {
        doListInternal(query, [:], positionalParams, args, false)
    }

    private List<D> getAllInternal(List ids) {
        if (!ids) return []
        String idName = persistentEntity.identity.name
        String entity = persistentEntity.name
        Class<?> idType = persistentEntity.identity.type
        List convertedIds = ids.collect { HibernateRuntimeUtils.convertValueToType(it, idType, conversionService) }
        List<D> results = doListInternal("from $entity where $idName in (:ids)" as String, [ids: convertedIds], [], [:], false)
        Map<Object, D> byId = results.collectEntries { [(it[idName]): it] }
        ids.collect { byId[it] }
    }

    @Override
    List<D> getAll(Serializable... ids) {
        getAllInternal(ids as List)
    }

    protected List<D> doListInternal(CharSequence hql,
                                   Map namedParams,
                                   Collection positionalParams,
                                   Map args
                                   , boolean isNative) {
        def hqlQuery = prepareHqlQuery(hql, isNative, false, namedParams, positionalParams, args)
        firePreQueryEvent()
        def ds = (List<D>) hqlQuery.list()
        firePostQueryEvent(ds)
        return ds
    }

    @SuppressWarnings('GroovyAssignabilityCheck')
    private D doSingleInternal(CharSequence hql,
                               Map namedParams,
                               Collection positionalParams,
                               Map args, Map hints = [:], boolean isNative
    ) {
        def hqlQuery = prepareHqlQuery(hql, isNative, false, namedParams, positionalParams, args)
        firePreQueryEvent()
        def sm = hqlQuery.singleResult()
        firePostQueryEvent(sm)
        return (D) sm
    }

    @Override
    Integer executeUpdate(CharSequence query, Map params, Map args) {
        doInternalExecuteUpdate(query, params, [], args)
    }

    @Override
    Integer executeUpdate(CharSequence query, Collection indexedParams, Map args) {
        doInternalExecuteUpdate(query, [:], indexedParams, args)
    }

    private Integer doInternalExecuteUpdate(CharSequence hql,
                                            Map namedParams,
                                            Collection positionalParams,
                                            Map args) {
        def hqlQuery = prepareHqlQuery(hql, false, true, namedParams, positionalParams, args)
        firePreQueryEvent()
        def execute = ((MutationHqlQuery) hqlQuery).executeUpdate()
        firePostQueryEvent(execute)
        return (Integer) execute
    }

    @SuppressWarnings('GroovyAssignabilityCheck')
    protected GormQuery prepareHqlQuery(CharSequence hql
                                        , boolean isNative
                                        , boolean isUpdate
                                        , Map<String, Object> namedParams
                                        , Collection<Object> positionalParams
                                        , Map<String, Object> querySettings
                                        , Map<String, Object> hints = [:]) {
        if (hints.isEmpty() && querySettings != null) {
            Map<String, Object> h = new HashMap<String, Object>();
            Set<String> definedHints = AvailableHints.getDefinedHints();
            for (Map.Entry<String, Object> entry : querySettings.entrySet()) {
                if (definedHints.contains(entry.getKey())) {
                    h.put(entry.getKey(), entry.getValue());
                }
            }
            hints = h;
        }
        // TODO: Optimize using Java Streams after fixing Static Compilation issues
        Map<String, Object> coercedParams = new HashMap<String, Object>();
        if (namedParams != null) {
            for (Map.Entry<String, Object> entry : namedParams.entrySet()) {
                coercedParams.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        def ctx = HqlQueryContext.prepare(getPersistentEntity(), hql, coercedParams, positionalParams, querySettings, hints, isNative, isUpdate)
        return HibernateHqlQueryCreator.createHqlQuery(
                (HibernateDatastore) getDatastore(),
                getSessionFactory(),
                (GrailsHibernatePersistentEntity) getPersistentEntity(),
                ctx
        )
    }

    protected Serializable convertIdentifier(Serializable id) {
        def identity = getPersistentEntity().identity
        if (identity != null) {
            ConversionService conversionService = getDatastore().mappingContext.conversionService
            if (id != null) {
                Class identityType = identity.type
                Class idInstanceType = id.getClass()
                if (identityType.isAssignableFrom(idInstanceType)) {
                    return id
                } else if (conversionService.canConvert(idInstanceType, identityType)) {
                    try {
                        return (Serializable) conversionService.convert(id, identityType)
                    }
                    catch (Throwable ignored) {
                        return null
                    }
                } else {
                    return null
                }
            }
        }
        return id
    }

    @Override
    List<D> list(Map params = Collections.emptyMap()) {
        firePreQueryEvent()
        HqlListQueryBuilder builder = new HqlListQueryBuilder((GrailsHibernatePersistentEntity) getPersistentEntity(), params)
        String hql = builder.buildListHql()
        HqlQueryContext ctx = HqlQueryContext.prepare(getPersistentEntity(), hql, Collections.emptyMap(), Collections.emptyList(), params, new HashMap<String, Object>(), false, false)
        GormQuery hqlQuery = HibernateHqlQueryCreator.createHqlQuery(
                (HibernateDatastore) getDatastore(),
                getSessionFactory(),
                (GrailsHibernatePersistentEntity) getPersistentEntity(),
                ctx
        )
        if (params.containsKey('max')) {
            return new HibernatePagedResultList(getHibernateTemplate(), getPersistentEntity(), hqlQuery)
        }
        List<D> result = (List<D>) hqlQuery.list()
        firePostQueryEvent(result)
        result
    }

    @Override
    def propertyMissing(String name) {
        if (getDatastore() instanceof ConnectionSourcesProvider) {
            return HibernateGormEnhancer.findStaticApi(persistentClass, name)
        } else {
            throw new MissingPropertyException(name, persistentClass)
        }
    }

    @Override
    GrailsCriteria createCriteria() {
        return new HibernateCriteriaBuilder(persistentClass, getSessionFactory(), (HibernateDatastore) getDatastore())
    }

    protected void firePostQueryEvent(Object result) {
        def hibernateQuery = new HibernateQuery(getHibernateSession(), (GrailsHibernatePersistentEntity) getPersistentEntity())
        def list = result instanceof List ? (List) result : Collections.singletonList(result)
        getDatastore().applicationEventPublisher.publishEvent(new PostQueryEvent(getDatastore(), hibernateQuery, list))
    }

    protected void firePreQueryEvent() {
        def hibernateQuery = new HibernateQuery(getHibernateSession(), (GrailsHibernatePersistentEntity) getPersistentEntity())
        getDatastore().applicationEventPublisher.publishEvent(new PreQueryEvent(getDatastore(), hibernateQuery))
    }
}

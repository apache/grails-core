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

import org.hibernate.jpa.AvailableHints

import grails.orm.HibernateCriteriaBuilder
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.CriteriaQuery
import jakarta.persistence.criteria.Expression
import jakarta.persistence.criteria.Root
import org.grails.datastore.gorm.GormEnhancer
import org.grails.datastore.gorm.GormStaticApi
import org.grails.datastore.gorm.finders.FinderMethod
import org.grails.datastore.mapping.core.connections.ConnectionSourcesProvider
import org.grails.datastore.mapping.query.api.BuildableCriteria as GrailsCriteria
import org.grails.datastore.mapping.query.event.PostQueryEvent
import org.grails.datastore.mapping.query.event.PreQueryEvent
import org.grails.datastore.mapping.proxy.ProxyHandler
import org.grails.orm.hibernate.query.HibernateHqlQuery
import org.grails.orm.hibernate.query.HibernateQuery
import org.grails.orm.hibernate.query.HqlListQueryBuilder
import org.grails.orm.hibernate.query.HqlQueryContext
import org.grails.orm.hibernate.query.PagedResultList
import org.grails.orm.hibernate.support.HibernateRuntimeUtils

import org.hibernate.Session
import org.hibernate.SessionFactory
import org.hibernate.query.Query

import org.springframework.core.convert.ConversionService
import org.springframework.transaction.PlatformTransactionManager

/**
 * The implementation of the GORM static method contract for Hibernate
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Slf4j
@CompileStatic
class HibernateGormStaticApi<D> extends GormStaticApi<D> {

    protected GrailsHibernateTemplate hibernateTemplate
    protected ConversionService conversionService
    protected final HibernateSession hibernateSession
    protected ProxyHandler proxyHandler
    protected SessionFactory sessionFactory
    protected Class identityType
    protected ClassLoader classLoader
    private HibernateGormInstanceApi<D> instanceApi

    HibernateGormStaticApi(Class<D> persistentClass, HibernateDatastore datastore, List<FinderMethod> finders,
                           ClassLoader classLoader, PlatformTransactionManager transactionManager) {
        super(persistentClass, datastore, finders, transactionManager)
        this.hibernateTemplate = new GrailsHibernateTemplate(datastore.getSessionFactory(), datastore)
        this.conversionService = datastore.mappingContext.conversionService
        this.proxyHandler = datastore.mappingContext.proxyHandler
        this.hibernateSession = new HibernateSession(
                (HibernateDatastore) datastore,
                hibernateTemplate.getSessionFactory()
        )
        this.classLoader = classLoader
        this.sessionFactory = datastore.getSessionFactory()
        this.identityType = persistentEntity.identity?.type
        this.instanceApi = new HibernateGormInstanceApi<>(persistentClass, datastore, classLoader)
    }

    GrailsHibernateTemplate getHibernateTemplate() {
        return hibernateTemplate as GrailsHibernateTemplate
    }

    @Override
     <T> T withNewSession(Closure<T> callable) {
        HibernateDatastore hibernateDatastore = (HibernateDatastore) datastore
        hibernateDatastore.withNewSession(callable)
    }

    @Override
     <T> T withSession(Closure<T> callable) {
        HibernateDatastore hibernateDatastore = (HibernateDatastore) datastore
        hibernateDatastore.withSession(callable)
    }

    D get(Serializable id) {
        if (id == null) {
            return null
        }

        id = convertIdentifier(id)

        if (id == null) {
            return null
        }

        if (persistentEntity.isMultiTenant()) {
            // for multi-tenant entities we process get(..) via a query
            (D) hibernateTemplate.execute { Session session ->
                new HibernateQuery(hibernateSession, persistentEntity).idEq(id).singleResult()
            }
        }
        else {
            // for non multi-tenant entities we process get(..) via the second level cache
            (D) hibernateTemplate.execute { Session session -> session.find(persistentEntity.javaClass, id) }
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

        (D) hibernateTemplate.execute { Session session ->
            CriteriaBuilder criteriaBuilder = session.getCriteriaBuilder()
            CriteriaQuery criteriaQuery = criteriaBuilder.createQuery(persistentEntity.javaClass)

            Root queryRoot = criteriaQuery.from(persistentEntity.javaClass)
            criteriaQuery = criteriaQuery.where(
                    //TODO: Remove explicit type cast once GROOVY-9460
                    criteriaBuilder.equal((Expression<?>) queryRoot.get(persistentEntity.identity.name), id)
            )
            Query criteria = session.createQuery(criteriaQuery)
                    .setHint(AvailableHints.HINT_READ_ONLY, true)
            HibernateHqlQuery hibernateHqlQuery = new HibernateHqlQuery(
                    hibernateSession, persistentEntity, criteria)
            proxyHandler.unwrap(hibernateHqlQuery.singleResult())
        }
    }

    @Override
    D load(Serializable id) {
        id = convertIdentifier(id)
        if (id != null) {
            return (D) hibernateTemplate.load((Class) persistentClass, id)
        }
        else {
            return null
        }
    }

    @Override
    D proxy(Serializable id) {
        id = convertIdentifier(id)
        if (id != null) {
            // Use the configured MappingContext proxyFactory (e.g. GroovyProxyFactory) so proxies are created correctly
            def proxyFactory = datastore.getMappingContext().getProxyFactory()
            return (D) proxyFactory.createProxy(datastore.currentSession, (Class) persistentClass, id)
        }
        else {
            return null
        }
    }

    @Override
    List<D> getAll() {
        doListInternal("from ${persistentEntity.name}".toString(), [:], [], [:], false)
    }

    @Override
    Integer count() {
        String entity = persistentEntity.name
        doSingleInternal("select count(*) from $entity" as String, [:], [], [:], false) as Integer
    }

    @Override
    boolean exists(Serializable id) {
        def converted = convertIdentifier(id)
        if (converted == null) return false
        String entity = persistentEntity.name
        String idName = persistentEntity.identity.name
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
        String hql = buildWhereHql(queryMap)
        doSingleInternal(hql, queryMap, [], args, false)
    }

    @Override
    List<D> findAllWhere(Map queryMap, Map args) {
        if (!queryMap) return null
        String hql = buildWhereHql(queryMap)
        doListInternal(hql, queryMap, [], args, false)
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

    private List<D> doListInternal(CharSequence hql,
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
                               Map args
                               , boolean isNative) {
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
        def execute = hqlQuery.executeUpdate()
        firePostQueryEvent(execute)
        return (Integer) execute
    }

    @SuppressWarnings('GroovyAssignabilityCheck')
    private HibernateHqlQuery prepareHqlQuery(CharSequence hql, boolean isNative, boolean isUpdate,
                                              Map namedParams, Collection positionalParams, Map querySettings) {
        def ctx = HqlQueryContext.prepare(persistentEntity, hql, namedParams, positionalParams, querySettings, isNative, isUpdate)
        return HibernateHqlQuery.createHqlQuery(
                (HibernateDatastore) datastore,
                sessionFactory,
                persistentEntity,
                ctx
                ,
                getHibernateTemplate(),
                conversionService
        )
    }

    protected Serializable convertIdentifier(Serializable id) {
        def identity = persistentEntity.identity
        if (identity != null) {
            ConversionService conversionService = persistentEntity.mappingContext.conversionService
            if (id != null) {
                Class identityType = identity.type
                Class idInstanceType = id.getClass()
                if (identityType.isAssignableFrom(idInstanceType)) {
                    return id
                }
                else if (conversionService.canConvert(idInstanceType, identityType)) {
                    try {
                        return (Serializable) conversionService.convert(id, identityType)
                    }
                    catch (Throwable ignored) {
                        return null
                    }
                }
                else {
                    return null
                }
            }
        }
        return id
    }

    @Override
    List<D> list(Map params = Collections.emptyMap()) {
        firePreQueryEvent()
        HqlListQueryBuilder builder = new HqlListQueryBuilder(persistentEntity, params)
        String hql = builder.buildListHql()
        HqlQueryContext ctx = HqlQueryContext.prepare(persistentEntity, hql, Collections.emptyMap(), Collections.emptyList(), params, false, false)
        HibernateHqlQuery hqlQuery = HibernateHqlQuery.createHqlQuery(
                (HibernateDatastore) datastore,
                sessionFactory,
                persistentEntity,
                ctx,
                getHibernateTemplate(),
                datastore.mappingContext.conversionService
        )
        if (params.containsKey('max')) {
            return new PagedResultList(getHibernateTemplate(), persistentEntity, hqlQuery)
        }
        List<D> result = (List<D>) hqlQuery.list()
        firePostQueryEvent(result)
        result
    }

    @Override
    def propertyMissing(String name) {
        if (datastore instanceof ConnectionSourcesProvider) {
            return GormEnhancer.findStaticApi(persistentClass, name)
        }
        else {
            throw new MissingPropertyException(name, persistentClass)
        }
    }

    @Override
    GrailsCriteria createCriteria() {
        return new HibernateCriteriaBuilder(persistentClass, sessionFactory, (HibernateDatastore) datastore)
    }

    protected void firePostQueryEvent(Object result) {
        def hibernateQuery = new HibernateQuery(new HibernateSession((HibernateDatastore) datastore, sessionFactory), persistentEntity)
        def list = result instanceof List ? (List) result : Collections.singletonList(result)
        datastore.applicationEventPublisher.publishEvent(new PostQueryEvent(datastore, hibernateQuery, list))
    }

    protected void firePreQueryEvent() {
        def hibernateSession = new HibernateSession((HibernateDatastore) datastore, sessionFactory)
        def hibernateQuery = new HibernateQuery(hibernateSession, persistentEntity)
        datastore.applicationEventPublisher.publishEvent(new PreQueryEvent(datastore, hibernateQuery))
    }
}

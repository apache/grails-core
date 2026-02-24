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

import org.grails.datastore.mapping.model.PersistentProperty
import org.hibernate.query.criteria.JpaRoot

import grails.orm.HibernateCriteriaBuilder
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import jakarta.persistence.criteria.Expression
import jakarta.persistence.criteria.Root
import org.grails.datastore.gorm.GormEnhancer
import org.grails.datastore.gorm.GormStaticApi
import org.grails.datastore.gorm.finders.DynamicFinder
import org.grails.datastore.gorm.finders.FinderMethod
import org.grails.datastore.mapping.core.connections.ConnectionSourcesProvider
import org.grails.datastore.mapping.query.api.BuildableCriteria as GrailsCriteria
import org.grails.datastore.mapping.query.event.PostQueryEvent
import org.grails.datastore.mapping.query.event.PreQueryEvent
import org.grails.datastore.mapping.proxy.ProxyHandler
import org.grails.orm.hibernate.query.GrailsHibernateQueryUtils
import org.grails.orm.hibernate.query.HibernateHqlQuery
import org.grails.orm.hibernate.query.HibernateQuery
import org.grails.orm.hibernate.query.PagedResultList
import org.grails.orm.hibernate.support.HibernateRuntimeUtils

import org.hibernate.LockMode
import org.hibernate.Session
import org.hibernate.SessionFactory
import org.hibernate.jpa.QueryHints
import org.hibernate.query.Query

import org.springframework.core.convert.ConversionService
import org.springframework.transaction.PlatformTransactionManager
import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.CriteriaQuery

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
    private int defaultFlushMode

    HibernateGormStaticApi(Class<D> persistentClass, HibernateDatastore datastore, List<FinderMethod> finders,
                           ClassLoader classLoader, PlatformTransactionManager transactionManager) {
        super(persistentClass, datastore, finders, transactionManager)
        this.hibernateTemplate = new GrailsHibernateTemplate(datastore.getSessionFactory(), datastore)
        this.conversionService = datastore.mappingContext.conversionService
        this.proxyHandler = datastore.mappingContext.proxyHandler
        this.hibernateSession = new HibernateSession(
                (HibernateDatastore)datastore,
                hibernateTemplate.getSessionFactory(),
                hibernateTemplate.getFlushMode()
        )
        this.classLoader = classLoader
        this.sessionFactory = datastore.getSessionFactory()
        this.identityType = persistentEntity.identity?.type
        this.defaultFlushMode = datastore.getDefaultFlushMode()
        this.instanceApi = new HibernateGormInstanceApi<>(persistentClass, datastore, classLoader)
    }

    GrailsHibernateTemplate getHibernateTemplate() {
        return hibernateTemplate as GrailsHibernateTemplate
    }

    @Override
    public <T> T withNewSession(Closure<T> callable) {
        AbstractHibernateDatastore hibernateDatastore = (AbstractHibernateDatastore) datastore
        hibernateDatastore.withNewSession(callable)
    }

    @Override
    def <T> T withSession(Closure<T> callable) {
        AbstractHibernateDatastore hibernateDatastore = (AbstractHibernateDatastore) datastore
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

        if(persistentEntity.isMultiTenant()) {
            // for multi-tenant entities we process get(..) via a query
            (D)hibernateTemplate.execute(  { Session session ->
                return new HibernateQuery(hibernateSession,persistentEntity ).idEq(id).singleResult()
            } )
        }
        else {
            // for non multi-tenant entities we process get(..) via the second level cache
            hibernateTemplate.get(persistentEntity.javaClass, id)
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

        (D)hibernateTemplate.execute(  { Session session ->
            CriteriaBuilder criteriaBuilder = session.getCriteriaBuilder()
            CriteriaQuery criteriaQuery = criteriaBuilder.createQuery(persistentEntity.javaClass)

            Root queryRoot = criteriaQuery.from(persistentEntity.javaClass)
            criteriaQuery = criteriaQuery.where (
                    //TODO: Remove explicit type cast once GROOVY-9460
                    criteriaBuilder.equal((Expression<?>)  queryRoot.get(persistentEntity.identity.name), id)
            )
            Query criteria = session.createQuery(criteriaQuery)
                    .setHint(QueryHints.HINT_READONLY, true)
            HibernateHqlQuery hibernateHqlQuery = new HibernateHqlQuery(
                    hibernateSession, persistentEntity, criteria)
            return proxyHandler.unwrap( hibernateHqlQuery.singleResult() )
        } )
    }

    @Override
    D load(Serializable id) {
        id = convertIdentifier(id)
        if (id != null) {
            return (D) hibernateTemplate.load((Class)persistentClass, id)
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
            return (D) proxyFactory.createProxy(datastore.currentSession, (Class)persistentClass, id)
        }
        else {
            return null
        }
    }

    @Override
    List<D> getAll() {
        new HibernateQuery(hibernateSession, persistentEntity).list()
    }

    protected HibernateQuery createHibernateQuery() {
        new HibernateQuery(hibernateSession, persistentEntity)
    }

    @Override
    Integer count() {
        new HibernateQuery(hibernateSession,persistentEntity ).count().singleResult() as Integer
    }

    @Override
    boolean exists(Serializable id) {
        !new HibernateQuery(hibernateSession,persistentEntity ).idEq(convertIdentifier(id)).list().isEmpty()
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
        requireGString(query, "findAll")
        doListInternal(query, [:], [], [:], false)
    }

    @Override
    List executeQuery(CharSequence query) {
        requireGString(query, "executeQuery")
        doListInternal(query, [:], [], [:], false)
    }

    @Override
    Integer executeUpdate(CharSequence query) {
        requireGString(query, "executeUpdate")
        doInternalExecuteUpdate(query,[:],[],[:])
    }

    @Override
    D find(CharSequence query) {
        requireGString(query, "find")
        doSingleInternal(query, [:], [], [:], false)
    }

    private static void requireGString(CharSequence query, String method) {
        if (!(query instanceof GString)) {
            throw new UnsupportedOperationException(
                "${method}(CharSequence) only accepts a Groovy GString with interpolated parameters " +
                "(e.g. ${method}(\"from Foo where bar = \${value}\")). " +
                "Use the parameterized overload ${method}(CharSequence, Map) or ${method}(CharSequence, Collection, Map) " +
                "to pass a plain String query safely."
            )
        }
    }

    @Override
    D find(CharSequence query, Map params) {
        doSingleInternal(query, params, [], [:], false)
    }

    @Override
    List<D> findAll(CharSequence query, Map params) {
        doListInternal(query, params, [], [:], false)
    }

    @Override
    List executeQuery(CharSequence query, Map args) {
        doListInternal(query, [:], [], args, false)
    }



    @Override
    D find(D exampleObject, Map args) {
        throw new UnsupportedOperationException("not yet")
    }

    @Override
    List<D> findAll(D exampleObject, Map args) {
        throw new UnsupportedOperationException("Example is not supported but maybe in the future")
    }

    @Override
    List<D> findAllWhere(Map queryMap, Map args) {
        if (!queryMap) return null
        def hibernateQuery = new HibernateQuery(hibernateSession, persistentEntity)
        queryMap.each {key,value -> hibernateQuery.eq(key.toString(),value)}
        firePreQueryEvent()
        def result = hibernateQuery.list()
        firePostQueryEvent(result)
        result as List<D>
    }

    @Override
    List executeQuery(CharSequence query, Map namedParams, Map args) {
        doListInternal(query, namedParams, [], args, false)
    }

    @SuppressWarnings('GroovyAssignabilityCheck')
    private List<D> doListInternal(CharSequence hql,
                                   Map namedParams,
                                   Collection positionalParams,
                                   Map args
                                   , boolean isNative) {
        boolean isUpdate = false
        def hqlQuery = HibernateHqlQuery.createHqlQuery(
                (HibernateDatastore)  datastore,
                sessionFactory,
                persistentEntity,
                hql,
                isNative,
                isUpdate,
                args,
                namedParams,
                positionalParams
                ,getHibernateTemplate()
        )
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
        boolean isUpdate = false
        def hqlQuery = HibernateHqlQuery.createHqlQuery(
                (HibernateDatastore)  datastore,
                sessionFactory,
                persistentEntity,
                hql,
                isNative,
                isUpdate,
                args,
                namedParams,
                positionalParams
                ,getHibernateTemplate()
        )
        firePreQueryEvent()
        def sm = hqlQuery.singleResult()
        firePostQueryEvent(sm)
        return (D) sm
    }

    private Integer doInternalExecuteUpdate(CharSequence hql,
                                            Map namedParams,
                                            Collection positionalParams,
                                            Map args) {
        boolean isNative = false
        boolean isUpdate  = true
        def hqlQuery = HibernateHqlQuery.createHqlQuery(
                (HibernateDatastore)  datastore,
                sessionFactory,
                persistentEntity,
                hql,
                isNative,
                isUpdate,
                args,
                namedParams,
                positionalParams
                ,getHibernateTemplate()
        )
        firePreQueryEvent()
        def execute = hqlQuery.executeUpdate()
        firePostQueryEvent(execute)
        return (Integer) execute
    }





    @Override
    List executeQuery(CharSequence query, Collection positionalParams, Map args) {
        return doListInternal(query, [:], positionalParams, args, false)
    }

    @Override
    List<D> findAll(CharSequence query, Collection positionalParams, Map args) {
        doListInternal(query, [:], positionalParams, args, false)
    }

    @Override
    D findWhere(Map queryMap, Map args) {
        if (!queryMap) return null
        def hibernateQuery = new HibernateQuery(hibernateSession, persistentEntity)
        queryMap.each {key,value -> hibernateQuery.eq(key.toString(),value)}
        firePreQueryEvent()
        def result = hibernateQuery.singleResult()
        firePostQueryEvent(result)
        result as D
    }

    List<D> getAll(List ids) {
        getAllInternal(ids)
    }

    List<D> getAll(Long... ids) {
        getAllInternal(ids as List)
    }

    @Override
    List<D> getAll(Serializable... ids) {
        getAllInternal(ids as List)
    }

    private List<D> getAllInternal(List ids) {
        if (!ids) return []

        hibernateTemplate.execute { Session session ->
            PersistentProperty identity = persistentEntity.identity
            Class<?> identityType = identity.type
            String identityName = identity.name
            List<Object> convertedIds = ids.collect { HibernateRuntimeUtils.convertValueToType(it, identityType, conversionService) }
            CriteriaBuilder cb = session.getCriteriaBuilder()
            CriteriaQuery<D> cq = cb.createQuery((Class<D>)persistentEntity.javaClass)
            Root<D> root = cq.from((Class<D>)persistentEntity.javaClass)
            cq.select(root).where(root.get("id").in(convertedIds))
            firePreQueryEvent()

            List<D> results = session.createQuery(cq).resultList
            firePostQueryEvent(results)
            Map<Object, D> idsMap = results.collectEntries { [it[identityName], it] }
            ids.collect { idsMap[it] }
        } as List<D>
    }




    protected Serializable convertIdentifier(Serializable id) {
        def identity = persistentEntity.identity
        if(identity != null) {
            ConversionService conversionService = persistentEntity.mappingContext.conversionService
            if(id != null) {
                Class identityType = identity.type
                Class idInstanceType = id.getClass()
                if(identityType.isAssignableFrom(idInstanceType)) {
                    return id
                }
                else if(conversionService.canConvert(idInstanceType, identityType)) {
                    try {
                        return (Serializable)conversionService.convert(id, identityType)
                    } catch (Throwable e) {
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







    private String normalizeMultiLineQueryString(String query) {
        if (query?.indexOf('\n') != -1)
           return query?.trim().replace('\n', ' ')
        return query
    }

    @Override
    List<D> list(Map params = Collections.emptyMap()) {
        hibernateTemplate.execute { Session session ->
            CriteriaBuilder criteriaBuilder = session.getCriteriaBuilder()
            CriteriaQuery criteriaQuery = criteriaBuilder.createQuery(persistentEntity.javaClass)
            Root queryRoot = criteriaQuery.from(persistentEntity.javaClass)
            GrailsHibernateQueryUtils.populateArgumentsForCriteria(
                    persistentEntity,
                    criteriaQuery,
                    queryRoot,
                    criteriaBuilder,
                    params,
                    datastore.mappingContext.conversionService,
                    true
            )
            Query query = session.createQuery(criteriaQuery)

            GrailsHibernateQueryUtils.populateArgumentsForCriteria(
                    persistentEntity,
                    query,
                    params,
                    datastore.mappingContext.conversionService,
                    true
            )

            HibernateHqlQuery hibernateQuery = new HibernateHqlQuery(
                    new HibernateSession((HibernateDatastore)datastore, sessionFactory),
                    persistentEntity,
                    query
            )
            hibernateTemplate.applySettings(query)

            params = params ? new HashMap(params) : Collections.emptyMap()
            if(params.containsKey(DynamicFinder.ARGUMENT_MAX)) {
                criteriaQuery = criteriaBuilder.createQuery(Object.class)
                queryRoot = criteriaQuery.from(persistentEntity.javaClass)
                return new PagedResultList(
                        hibernateTemplate,
                        persistentEntity,
                        hibernateQuery,
                        criteriaQuery,
                        queryRoot,
                        criteriaBuilder
                )
            }
            else {
                return hibernateQuery.list()
            }
        }
    }

    @Override
    def propertyMissing(String name) {
        if(datastore instanceof ConnectionSourcesProvider) {
            return GormEnhancer.findStaticApi(persistentClass, name)
        }
        else {
            throw new MissingPropertyException(name, persistentClass)
        }
    }

    @Override
    GrailsCriteria createCriteria() {
        def builder = new HibernateCriteriaBuilder(persistentClass, sessionFactory,(AbstractHibernateDatastore)datastore)
        builder.conversionService = conversionService
        return builder
    }

    @Override
    D lock(Serializable id) {
        (D)hibernateTemplate.lock((Class)persistentClass, convertIdentifier(id), LockMode.PESSIMISTIC_WRITE)
    }

    @Override
    Integer executeUpdate(CharSequence query, Map params, Map args) {
        doInternalExecuteUpdate(query,params,[],args)
    }

    @Override
    Integer executeUpdate(CharSequence query, Collection indexedParams, Map args) {
        doInternalExecuteUpdate(query,[:],indexedParams,args)
    }



    protected void firePostQueryEvent(Object result) {
        def hibernateQuery = new HibernateQuery(new HibernateSession((HibernateDatastore) datastore, sessionFactory), persistentEntity)
        def list = result instanceof List ? (List)result :  Collections.singletonList(result)
        datastore.applicationEventPublisher.publishEvent( new PostQueryEvent(datastore, hibernateQuery, list))
    }

    protected void firePreQueryEvent() {
        def hibernateSession = new HibernateSession((HibernateDatastore) datastore, sessionFactory)
        def hibernateQuery = new HibernateQuery(hibernateSession, persistentEntity)
        datastore.applicationEventPublisher.publishEvent(new PreQueryEvent(datastore, hibernateQuery))
    }



}

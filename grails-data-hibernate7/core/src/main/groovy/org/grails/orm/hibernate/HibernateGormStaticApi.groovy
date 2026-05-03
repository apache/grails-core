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
package org.grails.orm.hibernate

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import org.grails.datastore.gorm.GormInstanceApi
import org.grails.datastore.gorm.GormStaticApi
import grails.orm.HibernateCriteriaBuilder
import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.core.Session
import org.grails.datastore.mapping.core.SessionCallback
import org.grails.datastore.gorm.proxy.GroovyProxyFactory
import org.grails.datastore.mapping.query.api.BuildableCriteria
import org.grails.datastore.mapping.engine.EntityPersister
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.types.Basic
import org.grails.datastore.mapping.model.types.Simple
import org.grails.datastore.mapping.query.Query
import org.grails.datastore.mapping.query.Restrictions
import org.grails.datastore.mapping.reflect.ClassUtils
import org.grails.orm.hibernate.query.HibernateHqlQuery
import org.grails.orm.hibernate.query.HibernateHqlQueryCreator
import org.grails.orm.hibernate.query.HqlQueryContext
import org.grails.orm.hibernate.query.SelectHqlQuery
import org.hibernate.FlushMode
import org.hibernate.query.QueryFlushMode
import org.hibernate.SessionFactory
import org.springframework.core.convert.ConversionService
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.grails.datastore.gorm.DatastoreResolver
import org.grails.datastore.gorm.finders.FinderMethod
import org.grails.datastore.gorm.finders.DynamicFinder
import org.grails.orm.hibernate.support.hibernate7.SessionHolder
import org.grails.datastore.mapping.query.event.PreQueryEvent
import org.grails.datastore.mapping.query.event.PostQueryEvent
import org.springframework.context.ApplicationEventPublisher

/**
 * Hibernate GORM static API.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
class HibernateGormStaticApi<D> extends GormStaticApi<D> {

    protected GrailsHibernateTemplate hibernateTemplate
    protected final ClassLoader classLoader

    private static final Set<String> PAGINATION_ARGS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            DynamicFinder.ARGUMENT_MAX,
            DynamicFinder.ARGUMENT_OFFSET,
            DynamicFinder.ARGUMENT_SORT,
            DynamicFinder.ARGUMENT_ORDER,
            DynamicFinder.ARGUMENT_FETCH,
            DynamicFinder.ARGUMENT_IGNORE_CASE,
            DynamicFinder.ARGUMENT_FETCH_SIZE,
            DynamicFinder.ARGUMENT_TIMEOUT,
            DynamicFinder.ARGUMENT_READ_ONLY,
            DynamicFinder.ARGUMENT_FLUSH_MODE,
            "cache"
    )))

    HibernateGormStaticApi(Class<D> persistentClass, HibernateDatastore datastore, List<FinderMethod> finders, DatastoreResolver datastoreResolver, String qualifier, ClassLoader classLoader) {
        super(persistentClass, datastore.mappingContext, finders, datastoreResolver, qualifier)
        this.hibernateTemplate = (GrailsHibernateTemplate) datastore.getHibernateTemplate()
        this.classLoader = classLoader
    }

    HibernateGormStaticApi(Class<D> persistentClass, HibernateDatastore datastore, List<FinderMethod> finders, ClassLoader classLoader, DatastoreResolver datastoreResolver, String qualifier) {
        this(persistentClass, datastore, finders, datastoreResolver, qualifier, classLoader)
    }

    HibernateGormStaticApi(Class<D> persistentClass, HibernateDatastore datastore, List<FinderMethod> finders, ClassLoader classLoader, PlatformTransactionManager transactionManager) {
        this(persistentClass, datastore, finders, new DatastoreResolver() {
            @Override Datastore resolve() { datastore }
        }, org.grails.datastore.mapping.core.connections.ConnectionSource.DEFAULT, classLoader)
    }

    HibernateGormStaticApi(Class<D> persistentClass, MappingContext mappingContext, List<FinderMethod> finders, DatastoreResolver datastoreResolver, String qualifier, ClassLoader classLoader) {
        super(persistentClass, mappingContext, finders, datastoreResolver, qualifier)
        this.classLoader = classLoader
    }

    protected HibernateDatastore getHibernateDatastore() {
        (HibernateDatastore) getDatastore()
    }

    String getQualifier() {
        return this.@qualifier
    }

    protected GrailsHibernateTemplate getHibernateTemplate() {
        if (this.hibernateTemplate == null) {
            HibernateDatastore datastore = getHibernateDatastore()
            this.hibernateTemplate = (GrailsHibernateTemplate) datastore.getHibernateTemplate()
        }
        return hibernateTemplate
    }

    @Override
    BuildableCriteria createCriteria() {
        HibernateDatastore ds = getHibernateDatastore()
        new HibernateCriteriaBuilder(persistentClass, ds.sessionFactory, ds)
    }

    @Override
    boolean exists(Serializable id) {
        if (id == null) return false
        id = convertIdentifier(id)
        if (id == null) return false
        def template = getHibernateTemplate()
        PersistentEntity pe = getGormPersistentEntity()
        String entityName = pe.getName()
        String identityName = pe.identity.name
        return (Boolean) template.execute { org.hibernate.Session session ->
            org.hibernate.query.Query<Long> q = session.createQuery(
                    "select count(e) from ${entityName} e where e.${identityName} = :id".toString(), Long)
            q.setParameter('id', id)
            q.uniqueResult() > 0L
        }
    }

    @Override
    HibernateGormStaticApi<D> forQualifier(String qualifier) {
        Datastore ds = getDatastore()
        if (ds == null) return this
        
        org.grails.datastore.gorm.DatastoreResolver resolver = new org.grails.datastore.gorm.DatastoreResolver() {
            @Override Datastore resolve() { org.grails.datastore.gorm.GormEnhancer.findDatastore(persistentClass, qualifier) }
        }
        HibernateGormStaticApi<D> newApi = new HibernateGormStaticApi<D>(persistentClass, ds.mappingContext, finders, resolver, qualifier, classLoader)
        return newApi
    }

    @Override
    def <T> T withNewSession(Closure<T> callable) {
        getHibernateDatastore().withNewSession { session ->
            callable.call(((HibernateSession)session).getNativeSession())
        }
    }

    @Override
    def <T> T withSession(Closure<T> callable) {
        getHibernateDatastore().withSession { session ->
            callable.call(((HibernateSession)session).getNativeSession())
        }
    }

    @Override
    def <T1> T1 withDatastoreSession(Closure<T1> callable) {
        getHibernateDatastore().withSession { session ->
            callable.call(session)
        }
    }

    @Override
    def <T> T withNewSession(Serializable tenantId, Closure<T> callable) {
        HibernateDatastore hibernateDatastore = (HibernateDatastore) ((org.grails.datastore.mapping.multitenancy.MultiTenantCapableDatastore)getHibernateDatastore()).getDatastoreForTenantId(tenantId)
        hibernateDatastore.withNewSession { session ->
            callable.call(((HibernateSession)session).getNativeSession())
        }
    }

    @Override
    List executeQuery(CharSequence query, Map params) {
        executeQuery(query, params, Collections.emptyMap())
    }

    @Override
    List executeQuery(CharSequence query, Collection params) {
        executeQuery(query, params, Collections.emptyMap())
    }

    @Override
    List executeQuery(CharSequence query, Map params, Map args) {
        def template = getHibernateTemplate()
        if (query instanceof GString) {
            Map allParams = new LinkedHashMap(params ?: [:])
            String hql = buildNamedParameterQueryFromGString((GString) query, allParams)
            return (List) template.execute { org.hibernate.Session session ->
                org.hibernate.query.Query q = session.createQuery(hql)
                template.applySettings(q)
                populateQueryArguments(q, args)
                populateQueryWithNamedArguments(q, allParams)
                populateQueryWithNamedArguments(q, args)
                new HibernateHqlQuery((HibernateSession)getDatastore().currentSession, getGormPersistentEntity(), q).list()
            }
        }
        return (List) template.execute { org.hibernate.Session session ->
            org.hibernate.query.Query q = session.createQuery(query.toString())
            template.applySettings(q)
            populateQueryArguments(q, params)
            populateQueryArguments(q, args)
            populateQueryWithNamedArguments(q, params)
            populateQueryWithNamedArguments(q, args)

            new HibernateHqlQuery((HibernateSession)getDatastore().currentSession, getGormPersistentEntity(), q).list()
        }
    }

    @Override
    List executeQuery(CharSequence query) {
        if (query instanceof GString) {
            Map params = new LinkedHashMap()
            String hql = buildNamedParameterQueryFromGString((GString) query, params)
            return executeQuery(hql, params, Collections.emptyMap())
        }
        return executeQuery(query.toString(), Collections.emptyMap(), Collections.emptyMap())
    }

    @Override
    List executeQuery(CharSequence query, Object... params) {
        executeQuery(query, Arrays.asList(params))
    }

    @Override
    D find(CharSequence query, Map params, Map args) {
        def template = getHibernateTemplate()
        if (query instanceof GString) {
            Map allParams = new LinkedHashMap(params ?: [:])
            String hql = buildNamedParameterQueryFromGString((GString) query, allParams)
            return (D) template.execute { org.hibernate.Session session ->
                org.hibernate.query.Query q = session.createQuery(hql)
                template.applySettings(q)
                populateQueryArguments(q, args)
                populateQueryWithNamedArguments(q, allParams)
                populateQueryWithNamedArguments(q, args)
                q.setMaxResults(1)
                List results = new HibernateHqlQuery((HibernateSession)getDatastore().currentSession, getGormPersistentEntity(), q).list()
                return results ? results[0] : null
            }
        }
        return (D) template.execute { org.hibernate.Session session ->
            org.hibernate.query.Query q = session.createQuery(query.toString())
            template.applySettings(q)
            populateQueryArguments(q, params)
            populateQueryArguments(q, args)
            populateQueryWithNamedArguments(q, params)
            populateQueryWithNamedArguments(q, args)
            q.setMaxResults(1)

            List results = new HibernateHqlQuery((HibernateSession)getDatastore().currentSession, getGormPersistentEntity(), q).list()
            return results ? results[0] : null
        }
    }

    @Override
    List<D> findAll(CharSequence query, Map params, Map args) {
        def template = getHibernateTemplate()
        if (query instanceof GString) {
            Map allParams = new LinkedHashMap(params ?: [:])
            String hql = buildNamedParameterQueryFromGString((GString) query, allParams)
            return (List<D>) template.execute { org.hibernate.Session session ->
                org.hibernate.query.Query q = session.createQuery(hql)
                template.applySettings(q)
                populateQueryArguments(q, args)
                populateQueryWithNamedArguments(q, allParams)
                populateQueryWithNamedArguments(q, args)
                new HibernateHqlQuery((HibernateSession)getDatastore().currentSession, getGormPersistentEntity(), q).list()
            }
        }
        return (List<D>) template.execute { org.hibernate.Session session ->
            org.hibernate.query.Query q = session.createQuery(query.toString())
            template.applySettings(q)
            populateQueryArguments(q, params)
            populateQueryArguments(q, args)
            populateQueryWithNamedArguments(q, params)
            populateQueryWithNamedArguments(q, args)

            new HibernateHqlQuery((HibernateSession)getDatastore().currentSession, getGormPersistentEntity(), q).list()
        }
    }

    @Override
    @CompileDynamic
    D read(Serializable id) {
        if (id == null) return null
        id = convertIdentifier(id)
        if (id == null) return null
        def template = getHibernateTemplate()
        return (D) template.execute { org.hibernate.Session session ->
            D entity = (D) session.get(persistentClass, id)
            if (entity != null) {
                session.setReadOnly(entity, true)
            }
            entity
        }
    }

    @Override
    @CompileDynamic
    D proxy(Serializable id) {
        if (id == null) return null
        id = convertIdentifier(id)
        if (id == null) return null
        def proxyFactory = getHibernateDatastore().mappingContext.getProxyFactory()
        if (proxyFactory instanceof GroovyProxyFactory) {
            return execute({ org.grails.datastore.mapping.core.Session session ->
                session.proxy(persistentClass, id)
            } as SessionCallback<D>)
        }
        return (D) getHibernateTemplate().load(persistentClass, id)
    }

    @Override
    D load(Serializable id) {
        if (id == null) return null
        id = convertIdentifier(id)
        if (id == null) return null
        return (D) getHibernateTemplate().load(persistentClass, id)
    }

    @Override
    @CompileDynamic
    D last(Map params) {
        PersistentEntity pe = getGormPersistentEntity()
        Map p = new LinkedHashMap(params ?: [:])
        if (!p.containsKey(DynamicFinder.ARGUMENT_SORT)) {
            p.put(DynamicFinder.ARGUMENT_SORT, pe.identity.name)
        }
        if (!p.containsKey(DynamicFinder.ARGUMENT_ORDER)) {
            p.put(DynamicFinder.ARGUMENT_ORDER, 'desc')
        }
        p.put(DynamicFinder.ARGUMENT_MAX, 1)
        List<D> results = list(p)
        results ? results.get(0) : null
    }

    @Override
    @CompileDynamic
    List<D> findAllWhere(Map queryMap, Map args) {
        if (!queryMap) return null
        super.findAllWhere(queryMap, args)
    }

    @Override
    @CompileDynamic
    D findWhere(Map queryMap, Map args) {
        if (!queryMap) return null
        super.findWhere(queryMap, args)
    }

    @CompileDynamic
    protected Serializable convertIdentifier(Serializable id) {
        try {
            PersistentEntity pe = getGormPersistentEntity()
            Class identityType = pe.identity.type
            if (!identityType.isInstance(id)) {
                ConversionService conversionService = pe.mappingContext.conversionService
                if (conversionService.canConvert(id.class, identityType)) {
                    return (Serializable) conversionService.convert(id, identityType)
                }
                return null
            }
            return id
        }
        catch (Throwable e) {
            return null
        }
    }

    @CompileDynamic
    protected String buildNamedParameterQueryFromGString(GString query, Map params) {
        StringBuilder hql = new StringBuilder()
        int i = 0
        Object[] values = query.values
        String[] strings = query.getStrings()
        for (String str in strings) {
            hql.append(str)
            if (i < values.length) {
                String paramName = "p${i}"
                hql.append(':').append(paramName)
                params.put(paramName, values[i])
                i++
            }
        }
        return hql.toString()
    }


    @Override
    List executeQuery(CharSequence query, Collection params, Map args) {
        def template = getHibernateTemplate()
        return (List) template.execute { org.hibernate.Session session ->
            org.hibernate.query.Query q = session.createQuery(query.toString())
            template.applySettings(q)
            populateQueryWithCollectionArguments(q, params)
            populateQueryArguments(q, args)

            new HibernateHqlQuery((HibernateSession)getDatastore().currentSession, getGormPersistentEntity(), q).list()
        }
    }

    @Override
    Integer executeUpdate(CharSequence query, Map params) {
        executeUpdate(query, params, Collections.emptyMap())
    }

    @Override
    Integer executeUpdate(CharSequence query, Collection params) {
        executeUpdate(query, params, Collections.emptyMap())
    }

    @Override
    Integer executeUpdate(CharSequence query, Map params, Map args) {
        def template = getHibernateTemplate()
        return (Integer) template.execute { org.hibernate.Session session ->
            org.hibernate.query.Query q = session.createQuery(query.toString())
            template.applySettings(q)
            populateQueryArguments(q, params)
            populateQueryArguments(q, args)
            populateQueryWithNamedArguments(q, params)
            populateQueryWithNamedArguments(q, args)

            q.executeUpdate()
        }
    }

    @Override
    Integer executeUpdate(CharSequence query, Collection params, Map args) {
        def template = getHibernateTemplate()
        return (Integer) template.execute { org.hibernate.Session session ->
            org.hibernate.query.Query q = session.createQuery(query.toString())
            template.applySettings(q)
            populateQueryWithCollectionArguments(q, params)
            populateQueryArguments(q, args)

            q.executeUpdate()
        }
    }

    SelectHqlQuery prepareHqlQuery(String hql, boolean readOnly, boolean cache, Map params, List positional, Map hints) {
        def entity = getGormPersistentEntity()
        Map<String, Object> querySettings = [:]
        if (readOnly) querySettings[DynamicFinder.ARGUMENT_READ_ONLY] = true
        if (cache) querySettings['cache'] = true
        def ctx = HqlQueryContext.prepare(entity, hql, params as Map<String, Object>, positional, querySettings, hints as Map<String, Object>, false, false)
        return (SelectHqlQuery) getHibernateTemplate().execute { org.hibernate.Session session ->
            HibernateHqlQueryCreator.createHqlQuery(getHibernateDatastore(), getHibernateDatastore().sessionFactory, entity, ctx)
        }
    }

    List doListInternal(String hql, Map params, List positional, Map hints, boolean readOnly) {
        prepareHqlQuery(hql, readOnly, false, params, positional, hints).list()
    }

    @Override
    D find(CharSequence query) {
        find(query, Collections.emptyMap(), Collections.emptyMap())
    }

    @Override
    D find(CharSequence query, Map params) {
        find(query, params, Collections.emptyMap())
    }

    @Override
    D find(CharSequence query, Collection params) {
        find(query, params, Collections.emptyMap())
    }

    @Override
    D find(CharSequence query, Collection params, Map args) {
        def template = getHibernateTemplate()
        return (D) template.execute { org.hibernate.Session session ->
            org.hibernate.query.Query q = session.createQuery(query.toString())
            template.applySettings(q)
            populateQueryWithCollectionArguments(q, params)
            populateQueryArguments(q, args)
            q.setMaxResults(1)

            List results = new HibernateHqlQuery((HibernateSession)getDatastore().currentSession, getGormPersistentEntity(), q).list()
            return results ? results[0] : null
        }
    }

    @Override
    List<D> findAll(CharSequence query) {
        findAll(query, Collections.emptyMap(), Collections.emptyMap())
    }

    @Override
    List<D> findAll(CharSequence query, Map params) {
        findAll(query, params, Collections.emptyMap())
    }

    @Override
    List<D> findAll(CharSequence query, Collection params) {
        findAll(query, params, Collections.emptyMap())
    }

    @Override
    List<D> findAll(CharSequence query, Object[] params) {
        findAll(query, Arrays.asList(params))
    }

    @Override
    List<D> findAll(CharSequence query, Collection params, Map args) {
        def template = getHibernateTemplate()
        return (List<D>) template.execute { org.hibernate.Session session ->
            org.hibernate.query.Query q = session.createQuery(query.toString())
            template.applySettings(q)
            populateQueryWithCollectionArguments(q, params)
            populateQueryArguments(q, args)

            new HibernateHqlQuery((HibernateSession)getDatastore().currentSession, getGormPersistentEntity(), q).list()
        }
    }

    @Override
    @CompileDynamic
    List<D> findAll(D example, Map args) {
        execute({ Session session ->
            def query = session.createQuery(persistentClass)
            populateQueryByExample(session, query, example)
            if (query.allCriteria.isEmpty()) {
                return null
            }
            Integer max = ClassUtils.getIntegerFromMap(DynamicFinder.ARGUMENT_MAX, args)
            Integer offset = ClassUtils.getIntegerFromMap(DynamicFinder.ARGUMENT_OFFSET, args)
            if (max != null) {
                query.max(max.intValue())
            }
            if (offset != null) {
                query.offset(offset.intValue())
            }
            query.list()
        } as SessionCallback<List<D>>)
    }

    @Override
    @CompileDynamic
    D find(D example, Map args) {
        execute({ Session session ->
            def query = session.createQuery(persistentClass)
            populateQueryByExample(session, query, example)
            if (query.allCriteria.isEmpty()) {
                return null
            }
            query.singleResult()
        } as SessionCallback<D>)
    }

    protected void populateQueryByExample(Session session, Query query, D example) {
        PersistentEntity pe = getGormPersistentEntity()
        MappingContext mappingContext = pe.mappingContext
        def ea = mappingContext.createEntityAccess(pe, example)
        def id = ea.getIdentifier()
        if (id != null) {
            query.add(Restrictions.eq(pe.identity.name, id))
        }
        else {
            for (prop in pe.persistentProperties) {
                if (prop instanceof Simple || prop instanceof Basic) {
                    def val = ea.getProperty(prop.name)
                    if (val != null) {
                        query.add(Restrictions.eq(prop.name, val))
                    }
                }
            }
        }
    }

    protected void populateQueryArguments(org.hibernate.query.Query q, Map args) {
        if (args?.containsKey(DynamicFinder.ARGUMENT_MAX)) {
            Integer val = ClassUtils.getIntegerFromMap(DynamicFinder.ARGUMENT_MAX, args)
            if (val != null) {
                q.setMaxResults(val.intValue())
            }
        }
        if (args?.containsKey(DynamicFinder.ARGUMENT_OFFSET)) {
            Integer val = ClassUtils.getIntegerFromMap(DynamicFinder.ARGUMENT_OFFSET, args)
            if (val != null) {
                q.setFirstResult(val.intValue())
            }
        }
        if (args?.containsKey(DynamicFinder.ARGUMENT_FETCH_SIZE)) {
            Integer val = ClassUtils.getIntegerFromMap(DynamicFinder.ARGUMENT_FETCH_SIZE, args)
            if (val != null) {
                q.setFetchSize(val.intValue())
            }
        }
        if (args?.containsKey(DynamicFinder.ARGUMENT_TIMEOUT)) {
            Integer val = ClassUtils.getIntegerFromMap(DynamicFinder.ARGUMENT_TIMEOUT, args)
            if (val != null) {
                q.setTimeout(val.intValue())
            }
        }
        if (args?.containsKey(DynamicFinder.ARGUMENT_READ_ONLY)) {
            q.setReadOnly(ClassUtils.getBooleanFromMap(DynamicFinder.ARGUMENT_READ_ONLY, args))
        }
    }

    protected void populateQueryWithCollectionArguments(org.hibernate.query.Query q, Collection params) {
        if (params == null || params.isEmpty()) return
        Set<String> namedParameters = q.getParameterMetadata().getNamedParameterNames()
        if (namedParameters.size() > 0 && params.size() == namedParameters.size()) {
            Iterator<String> it = namedParameters.iterator()
            for (param in params) {
                q.setParameter(it.next(), param)
            }
        }
        else {
            int i = 1
            for (param in params) {
                q.setParameter(i++, param)
            }
        }
    }

    protected void populateQueryWithNamedArguments(org.hibernate.query.Query q, Map params) {
        if (params == null) return
        Set<String> namedParameters = q.getParameterMetadata().getNamedParameterNames()
        for (entry in params.entrySet()) {
            String name = entry.key?.toString()
            if (name == null || PAGINATION_ARGS.contains(name)) continue

            def val = entry.value
            try {
                if (val instanceof Collection) {
                    q.setParameterList(name, (Collection) val)
                }
                else if (val?.getClass()?.isArray()) {
                    q.setParameterList(name, (Object[]) val)
                }
                else {
                    q.setParameter(name, val)
                }
            } catch (Throwable e) {
                // Ignore if the parameter is not defined in the query
            }
        }
    }

    @CompileDynamic
    List<D> findAllWithNativeSql(CharSequence sql, Map args) {
        def template = getHibernateTemplate()
        return (List<D>) template.execute { org.hibernate.Session session ->
            List params = []
            String sqlStr = sql instanceof GString ?
                buildOrdinalParameterQueryFromGString((GString) sql, params) :
                sql.toString()
            org.hibernate.query.NativeQuery<D> q = session.createNativeQuery(sqlStr, persistentClass)
            template.applySettings(q)
            params.eachWithIndex { val, int i ->
                if (val instanceof CharSequence) {
                    q.setParameter(i + 1, val.toString())
                } else {
                    q.setParameter(i + 1, val)
                }
            }
            populateQueryArguments(q, args)
            q.list()
        }
    }

    @CompileDynamic
    D findWithNativeSql(CharSequence sql, Map args) {
        def template = getHibernateTemplate()
        return (D) template.execute { org.hibernate.Session session ->
            List params = []
            String sqlStr = sql instanceof GString ?
                buildOrdinalParameterQueryFromGString((GString) sql, params) :
                sql.toString()
            org.hibernate.query.NativeQuery<D> q = session.createNativeQuery(sqlStr, persistentClass)
            template.applySettings(q)
            params.eachWithIndex { val, int i ->
                if (val instanceof CharSequence) {
                    q.setParameter(i + 1, val.toString())
                } else {
                    q.setParameter(i + 1, val)
                }
            }
            q.setMaxResults(1)
            populateQueryArguments(q, args)
            List results = q.list()
            results.isEmpty() ? null : results.get(0)
        }
    }

    protected String buildOrdinalParameterQueryFromGString(GString query, List params) {
        StringBuilder sqlString = new StringBuilder()
        int i = 0
        Object[] values = query.values
        String[] strings = query.getStrings()
        for (String str in strings) {
            sqlString.append(str)
            if (i < values.length) {
                sqlString.append('?')
                params.add(values[i++])
            }
        }
        return sqlString.toString()
    }
}

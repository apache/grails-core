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

import org.grails.datastore.mapping.query.Query as GormQuery

import org.hibernate.FlushMode

import org.springframework.core.convert.ConversionService
import org.springframework.transaction.PlatformTransactionManager

import grails.orm.HibernateCriteriaBuilder
import org.grails.datastore.gorm.DatastoreResolver
import org.grails.datastore.gorm.GormStaticApi
import org.grails.datastore.gorm.finders.DynamicFinder
import org.grails.datastore.gorm.finders.FinderMethod
import org.grails.datastore.gorm.proxy.GroovyProxyFactory
import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.core.Session
import org.grails.datastore.mapping.core.SessionCallback
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.model.config.GormProperties
import org.grails.datastore.mapping.model.types.Basic
import org.grails.datastore.mapping.model.types.Simple
import org.grails.datastore.mapping.query.Query
import org.grails.datastore.mapping.query.Restrictions
import org.grails.datastore.mapping.query.api.BuildableCriteria
import org.grails.datastore.mapping.query.event.PostQueryEvent
import org.grails.datastore.mapping.query.event.PreQueryEvent
import org.grails.datastore.mapping.reflect.ClassUtils
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity
import org.grails.orm.hibernate.query.HibernateHqlQueryCreator
import org.grails.orm.hibernate.query.HibernateQuery
import org.grails.orm.hibernate.query.HibernateQueryArgument
import org.grails.orm.hibernate.query.HqlListQueryBuilder
import org.grails.orm.hibernate.query.HqlQueryContext
import org.grails.orm.hibernate.query.MutationHqlQuery
import org.grails.orm.hibernate.query.PagedResultList
import org.grails.orm.hibernate.support.HibernateRuntimeUtils

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
            'cache'
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
        }, ConnectionSource.DEFAULT, classLoader)
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

    protected IHibernateTemplate getHibernateTemplate() {
        IHibernateTemplate template = (IHibernateTemplate) getHibernateDatastore().getHibernateTemplate()
        String connectionName = getHibernateDatastore().connectionSources.defaultConnectionSource.name
        if (qualifier != null && !connectionName.equals(qualifier) && !ConnectionSource.DEFAULT.equals(qualifier) && getHibernateDatastore().getMultiTenancyMode() == org.grails.datastore.mapping.multitenancy.MultiTenancySettings.MultiTenancyMode.DISCRIMINATOR) {
            return new TenantBoundHibernateTemplate(template, (Serializable)qualifier, getHibernateDatastore())
        }
        return template
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
        PersistentEntity pe = getGormPersistentEntity()

        return (Boolean) getHibernateTemplate().execute { org.hibernate.Session session ->
            StringBuilder hql = new StringBuilder('select count(e) from ').append(pe.name).append(' e where ')
            Map<String, Object> params = [:]

            PersistentProperty identity = pe.getIdentity()
            if (identity != null) {
                hql.append('e.').append(identity.name).append(' = :id')
                params.id = id
            } else {
                PersistentProperty[] compositeId = pe.getCompositeIdentity()
                if (compositeId != null && compositeId.length > 0) {
                    List<String> conditions = []
                    for (prop in compositeId) {
                        conditions << ("e.${prop.name} = :${prop.name}".toString())
                        params[prop.name] = id[prop.name]
                    }
                    hql.append(conditions.join(' and '))
                } else {
                    return false
                }
            }

            org.hibernate.query.Query<Long> q = session.createQuery(hql.toString(), Long)
            params.each { k, v -> q.setParameter(k, v) }
            return q.uniqueResult() > 0L
        }
    }

    @Override
    HibernateGormStaticApi<D> forQualifier(String qualifier) {
        Datastore ds = getDatastore()
        if (ds == null) return this

        org.grails.datastore.gorm.DatastoreResolver resolver = new org.grails.datastore.gorm.DatastoreResolver() {
            @Override Datastore resolve() { org.grails.datastore.gorm.GormRegistry.instance.apiResolver.findDatastore(persistentClass, qualifier) }
        }
        // Create new finders with the qualifier-specific resolver so dynamic finders (e.g. findByName)
        // execute against the correct (non-DEFAULT) datasource session factory.
        List<org.grails.datastore.gorm.finders.FinderMethod> qualifiedFinders =
                registry.createDynamicFinders(resolver, ds.mappingContext)
        HibernateGormStaticApi<D> newApi = new HibernateGormStaticApi<D>(persistentClass, ds.mappingContext, qualifiedFinders, resolver, qualifier, classLoader)
        return newApi
    }

    @Override
    def <T> T withSession(Closure<T> callable) {
        getHibernateDatastore().withSession { session ->
            callable.call(session)
        }
    }

    @Override
    def <T> T withNewSession(Closure<T> callable) {
        getHibernateDatastore().withNewSession { session ->
            callable.call(session)
        }
    }

    @Override
    def <T1> T1 withDatastoreSession(Closure<T1> callable) {
        getHibernateDatastore().withSession { session ->
            callable.call(new HibernateSession(getHibernateDatastore(), getHibernateDatastore().getSessionFactory(), (org.hibernate.Session)session))
        }
    }

    @Override
    def <T> T withNewSession(Serializable tenantId, Closure<T> callable) {
        HibernateDatastore primaryDatastore = getHibernateDatastore().getPrimaryDatastore()
        return (T) grails.gorm.multitenancy.Tenants.withId((org.grails.datastore.mapping.multitenancy.MultiTenantCapableDatastore)primaryDatastore, tenantId) { id, session ->
            callable.call(session)
        }
    }

    @Override
    List<D> list(Map params) {
        PersistentEntity entity = getGormPersistentEntity()
        HqlQueryContext ctx = HqlQueryContext.prepare(entity, null, null, null, params, new HashMap<>(), false, false)
        Query q = HibernateHqlQueryCreator.createHqlQuery(getHibernateDatastore(), getHibernateDatastore().getSessionFactory(), entity, ctx)
        if (HqlListQueryBuilder.isPaged(params)) {
            return (List<D>) new PagedResultList(q)
        }
        return (List<D>) q.list()
    }

    @Override
    List executeQuery(CharSequence query, Map params, Map args) {
        PersistentEntity entity = getGormPersistentEntity()
        HqlQueryContext ctx = HqlQueryContext.prepare(entity, query, params, null, args, new HashMap<>(), false, false)
        return (List) HibernateHqlQueryCreator.createHqlQuery(getHibernateDatastore(), getHibernateDatastore().getSessionFactory(), entity, ctx).list()
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

    // The single-argument CharSequence overloads accept a plain String (executed as written, as
    // on Hibernate 5) or a Groovy GString. A GString is never interpolated into the query text:
    // HqlQueryContext expands every ${value} into a bound named parameter (see
    // buildNamedParameterQueryFromGString), so the idiomatic Groovy form
    // executeQuery("from Foo where bar = ${userInput}") is injection-safe by binding, not escaping.
    @Override
    List<D> findAll(CharSequence query) {
        doListInternal(query, [:], [], [:], false)
    }

    @Override
    List executeQuery(CharSequence query) {
        doListInternal(query, [:], [], [:], false)
    }

    @Override
    Integer executeUpdate(CharSequence query) {
        doInternalExecuteUpdate(query, [:], [], [:])
    }

    @Override
    List executeQuery(CharSequence query, Map params) {
        return executeQuery(query, params, Collections.emptyMap())
    }

    @Override
    List executeQuery(CharSequence query, Collection params) {
        return executeQuery(query, params, Collections.emptyMap())
    }

    @Override
    List executeQuery(CharSequence query, Collection params, Map args) {
        PersistentEntity entity = getGormPersistentEntity()
        HqlQueryContext ctx = HqlQueryContext.prepare(entity, query, null, params, args, new HashMap<>(), false, false)
        return (List) HibernateHqlQueryCreator.createHqlQuery(getHibernateDatastore(), getHibernateDatastore().getSessionFactory(), entity, ctx).list()
    }

    @Override
    List executeQuery(CharSequence query, Object... params) {
        return executeQuery(query, Arrays.asList(params))
    }

    @Override
    grails.gorm.api.GormAllOperations<D> eachTenant(Closure callable) {
        grails.gorm.multitenancy.Tenants.eachTenant((Class<? extends Datastore>)getDatastore().getClass()) { Serializable tenantId ->
            withTenant(tenantId).withSession {
                callable.call(tenantId)
            }
        }
        return this
    }

    @Override
    grails.gorm.api.GormAllOperations<D> withTenant(Serializable tenantId) {
        HibernateDatastore hibernateDatastore = (HibernateDatastore) ((org.grails.datastore.mapping.multitenancy.MultiTenantCapableDatastore)getDatastore()).getDatastoreForTenantId(tenantId)
        final org.grails.datastore.gorm.DatastoreResolver resolver = new org.grails.datastore.gorm.DatastoreResolver() {
            @Override Datastore resolve() { hibernateDatastore }
        }
        return (grails.gorm.api.GormAllOperations<D>) new HibernateGormStaticApi<D>(persistentClass, hibernateDatastore, finders, resolver, tenantId.toString(), classLoader)
    }

    @Override
    def <T1> T1 withTenant(Serializable tenantId, Closure<T1> callable) {
        HibernateDatastore primaryDatastore = getHibernateDatastore().getPrimaryDatastore()
        return (T1) grails.gorm.multitenancy.Tenants.withId((org.grails.datastore.mapping.multitenancy.MultiTenantCapableDatastore)primaryDatastore, tenantId) { id, session ->
            if (callable.maximumNumberOfParameters == 2) {
                callable.call(tenantId, session)
            } else {
                callable.call(session)
            }
        }
    }

    @Override
    D find(CharSequence query, Map params, Map args) {
        PersistentEntity entity = getGormPersistentEntity()
        HqlQueryContext ctx = HqlQueryContext.prepare(entity, query, params, null, args, new HashMap<>(), false, false)
        ctx.querySettings().put(DynamicFinder.ARGUMENT_MAX, 1)
        List results = HibernateHqlQueryCreator.createHqlQuery(getHibernateDatastore(), getHibernateDatastore().getSessionFactory(), entity, ctx).list()
        return results ? (D) results[0] : null
    }

    @Override
    D find(CharSequence query) {
        doSingleInternal(query, [:], [], [:], false)
    }

    @Override
    D find(CharSequence query, Map params) {
        return find(query, params, Collections.emptyMap())
    }

    @Override
    D find(CharSequence query, Collection params) {
        return find(query, params, Collections.emptyMap())
    }

    @Override
    D find(CharSequence query, Collection params, Map args) {
        PersistentEntity entity = getGormPersistentEntity()
        HqlQueryContext ctx = HqlQueryContext.prepare(entity, query, null, params, args, new HashMap<>(), false, false)
        ctx.querySettings().put(DynamicFinder.ARGUMENT_MAX, 1)
        List results = HibernateHqlQueryCreator.createHqlQuery(getHibernateDatastore(), getHibernateDatastore().getSessionFactory(), entity, ctx).list()
        return results ? (D) results[0] : null
    }

    @Override
    List<D> findAll(CharSequence query, Map params) {
        return findAll(query, params, Collections.emptyMap())
    }

    @Override
    List<D> findAll(CharSequence query, Collection params) {
        return findAll(query, params, Collections.emptyMap())
    }

    @Override
    List<D> findAll(CharSequence query, Object[] params) {
        return findAll(query, Arrays.asList(params))
    }

    @Override
    D findWhere(Map queryMap, Map args) {
        if (!queryMap) return null
        executeSingleHqlQuery(prepareWhereHqlQuery(queryMap, buildFindWhereArgs(args)))
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
        Map p = new LinkedHashMap(params ?: [:])
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
        executeListHqlQuery(prepareWhereHqlQuery(queryMap, buildQuerySettings(args)))
    }

    private GormQuery prepareWhereHqlQuery(Map queryMap, Map<String, Object> querySettings) {
        Map<String, Object> coercedMap = buildQuerySettings(queryMap)
        return prepareHqlQuery(
                buildWhereHql(coercedMap),
                false,
                false,
                buildWhereParams(coercedMap),
                Collections.emptyList(),
                querySettings
        )
    }

    private String buildWhereHql(Map<String, Object> queryMap) {
        String whereClause = queryMap.collect { String key, Object value ->
            String propertyName = validateWherePropertyName(key)
            value == null ? "$propertyName is null" : "$propertyName = :$propertyName"
        }.join(' and ')
        return "from ${persistentEntity.name} where $whereClause"
    }

    private String validateWherePropertyName(String propertyName) {
        PersistentProperty property = persistentEntity.getPropertyByName(propertyName)
        if (property == null || property.name != propertyName) {
            throw new IllegalArgumentException("Property [$propertyName] is not a valid property of ${persistentEntity.name}")
        }
        return propertyName
    }

    private static Map<String, Object> buildWhereParams(Map<String, Object> queryMap) {
        queryMap.findAll { String key, Object value -> value != null } as Map<String, Object>
    }

    private static Map<String, Object> buildFindWhereArgs(Map args) {
        Map<String, Object> queryArgs = buildQuerySettings(args)
        queryArgs[HibernateQueryArgument.MAX.value()] = 1
        return queryArgs
    }

    private static Map<String, Object> buildQuerySettings(Map args) {
        Map<String, Object> queryArgs = new LinkedHashMap<>()
        args?.each { Object key, Object value -> queryArgs[key.toString()] = value }
        return queryArgs
    }

    @CompileDynamic
    protected Serializable convertIdentifier(Serializable id) {
        try {
            PersistentEntity pe = getGormPersistentEntity()
            PersistentProperty identity = pe.getIdentity()
            Class identityType = identity != null ? identity.type : id.getClass()
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

    @Override
    Integer executeUpdate(CharSequence query, Map params) {
        return executeUpdate(query, params, Collections.emptyMap())
    }

    @Override
    Integer executeUpdate(CharSequence query, Collection params) {
        return executeUpdate(query, params, Collections.emptyMap())
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
        ConversionService conversionService = persistentEntity.mappingContext.conversionService
        List convertedIds = ids.collect { HibernateRuntimeUtils.convertValueToType(it, idType, conversionService) }
        List<D> results = doListInternal("from $entity where $idName in (:ids)" as String, [ids: convertedIds], [], [:], false)
        Map<Object, D> byId = results.collectEntries { [(it[idName]): it] }
        convertedIds.collect { byId[it] }
    }

    @Override
    List<D> getAll(Serializable... ids) {
        getAllInternal(ids as List)
    }

    protected List<D> doListInternal(CharSequence hql,
                                     Map namedParams,
                                     Collection positionalParams,
                                     Map args,
                                     boolean isNative) {
        GormQuery hqlQuery = prepareHqlQuery(hql, isNative, false, namedParams, positionalParams, args)
        executeListHqlQuery(hqlQuery)
    }

    protected List<D> executeListHqlQuery(GormQuery hqlQuery) {
        firePreQueryEvent()
        def ds = (List<D>) hqlQuery.list()
        firePostQueryEvent(ds)
        return ds
    }

    @SuppressWarnings('GroovyAssignabilityCheck')
    private D doSingleInternal(CharSequence hql,
                               Map namedParams,
                               Collection positionalParams,
                               Map args,
                               boolean isNative) {
        GormQuery hqlQuery = prepareHqlQuery(hql, isNative, false, namedParams, positionalParams, args)
        executeSingleHqlQuery(hqlQuery)
    }

    @SuppressWarnings('GroovyAssignabilityCheck')
    private D executeSingleHqlQuery(GormQuery hqlQuery) {
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
                if (prop.name == GormProperties.VERSION) {
                    continue
                }
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
        if (args == null || args.isEmpty()) return

        Map argsToUse = new HashMap(args)
        Integer max = intValue(argsToUse, DynamicFinder.ARGUMENT_MAX)
        if (max != null) {
            q.setMaxResults(max)
        }
        Integer offset = intValue(argsToUse, DynamicFinder.ARGUMENT_OFFSET)
        if (offset != null) {
            q.setFirstResult(offset)
        }

        if (argsToUse.containsKey(DynamicFinder.ARGUMENT_CACHE)) {
            q.setCacheable(org.grails.datastore.mapping.reflect.ClassUtils.getBooleanFromMap(DynamicFinder.ARGUMENT_CACHE, argsToUse))
        }
        if (argsToUse.containsKey(DynamicFinder.ARGUMENT_FETCH_SIZE)) {
            Object fetchSize = argsToUse.remove(DynamicFinder.ARGUMENT_FETCH_SIZE)
            if (fetchSize instanceof Number) {
                q.setFetchSize(((Number) fetchSize).intValue())
            }
        }
        if (argsToUse.containsKey(DynamicFinder.ARGUMENT_TIMEOUT)) {
            Object timeout = argsToUse.remove(DynamicFinder.ARGUMENT_TIMEOUT)
            if (timeout instanceof Number) {
                q.setTimeout(((Number) timeout).intValue())
            }
        }
        if (argsToUse.containsKey(DynamicFinder.ARGUMENT_READ_ONLY)) {
            q.setReadOnly((Boolean) argsToUse.remove(DynamicFinder.ARGUMENT_READ_ONLY))
        }
        if (argsToUse.containsKey(DynamicFinder.ARGUMENT_FLUSH_MODE)) {
            Object flushMode = argsToUse.remove(DynamicFinder.ARGUMENT_FLUSH_MODE)
            if (flushMode instanceof FlushMode) {
                q.setHibernateFlushMode((FlushMode) flushMode)
            } else if (flushMode instanceof String) {
                q.setHibernateFlushMode(FlushMode.valueOf(flushMode.toString().toUpperCase()))
            }
        }
    }

    protected Integer intValue(Map args, String name) {
        Object val = args.get(name)
        if (val instanceof Number) {
            return ((Number) val).intValue()
        } else if (val != null) {
            try {
                return Integer.valueOf(val.toString())
            } catch (NumberFormatException e) {
                return null
            }
        }
        return null
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

    @SuppressWarnings('GroovyAssignabilityCheck')
    protected GormQuery prepareHqlQuery(CharSequence hql,
                                        boolean isNative,
                                        boolean isUpdate,
                                        Map namedParams,
                                        Collection positionalParams,
                                        Map args) {
        Map<String, Object> coercedParams = namedParams?.collectEntries { k, v -> [k.toString(), v] } ?: [:]
        Map<String, Object> coercedArgs = args?.collectEntries { k, v -> [k.toString(), v] } ?: [:]
        def ctx = HqlQueryContext.prepare(persistentEntity, hql, coercedParams, positionalParams, coercedArgs, new HashMap<>(), isNative, isUpdate)
        return HibernateHqlQueryCreator.createHqlQuery(
                getHibernateDatastore(),
                getHibernateDatastore().getSessionFactory(),
                persistentEntity,
                ctx
        )
    }

    protected void firePostQueryEvent(Object result) {
        def hibernateSession = new HibernateSession(getHibernateDatastore(), getHibernateDatastore().getSessionFactory())
        def hibernateQuery = new HibernateQuery(hibernateSession, (GrailsHibernatePersistentEntity) persistentEntity)
        def list = result instanceof List ? (List) result : Collections.singletonList(result)
        getHibernateDatastore().applicationEventPublisher.publishEvent(new PostQueryEvent(getHibernateDatastore(), hibernateQuery, list))
    }

    protected void firePreQueryEvent() {
        def hibernateSession = new HibernateSession(getHibernateDatastore(), getHibernateDatastore().getSessionFactory())
        def hibernateQuery = new HibernateQuery(hibernateSession, (GrailsHibernatePersistentEntity) persistentEntity)
        getHibernateDatastore().applicationEventPublisher.publishEvent(new PreQueryEvent(getHibernateDatastore(), hibernateQuery))
    }

}

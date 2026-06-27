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
package org.grails.datastore.gorm

import groovy.transform.CompileDynamic
import groovy.util.logging.Slf4j

import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.DefaultTransactionDefinition

import grails.gorm.CriteriaBuilder
import grails.gorm.DetachedCriteria
import grails.gorm.api.GormAllOperations
import grails.gorm.api.GormInstanceOperations
import grails.gorm.api.GormStaticOperations
import grails.gorm.multitenancy.Tenants
import grails.gorm.transactions.GrailsTransactionTemplate
import org.grails.datastore.gorm.finders.FinderMethod
import org.grails.datastore.gorm.transactions.DefaultTransactionTemplateFactory
import org.grails.datastore.gorm.transactions.TransactionTemplateFactory
import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.datastore.mapping.core.Session
import org.grails.datastore.mapping.core.SessionCallback
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.core.connections.ConnectionSources
import org.grails.datastore.mapping.core.connections.ConnectionSourcesProvider
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.multitenancy.MultiTenantCapableDatastore
import org.grails.datastore.mapping.query.api.BuildableCriteria
import org.grails.datastore.mapping.transactions.TransactionCapableDatastore

/**
 * Static methods for GORM
 *
 * @author Graeme Rocher
 */
@CompileDynamic
@Slf4j
class GormStaticApi<D> extends AbstractGormApi<D> implements GormAllOperations<D> {

    private static final TransactionTemplateFactory DEFAULT_TRANSACTION_TEMPLATE_FACTORY = new DefaultTransactionTemplateFactory()

    protected final List<FinderMethod> finders

    GormStaticApi(Class<D> persistentClass, MappingContext mappingContext, List<FinderMethod> finders) {
        this(persistentClass, mappingContext, finders, null, ConnectionSource.DEFAULT, null)
    }

    /** @deprecated Pass a {@link MappingContext} instead of a {@link Datastore}. */
    @Deprecated
    GormStaticApi(Class<D> persistentClass, Datastore datastore, List<FinderMethod> finders, PlatformTransactionManager transactionManager) {
        this(persistentClass, datastore?.mappingContext, finders,
                datastore != null ? ({ -> datastore } as DatastoreResolver) : null,
                ConnectionSource.DEFAULT, null)
    }

    GormStaticApi(Class<D> persistentClass, MappingContext mappingContext, List<FinderMethod> finders, DatastoreResolver resolver) {
        this(persistentClass, mappingContext, finders, resolver, ConnectionSource.DEFAULT, null)
    }

    GormStaticApi(Class<D> persistentClass, MappingContext mappingContext, List<FinderMethod> finders, String qualifier) {
        this(persistentClass, mappingContext, finders, null, qualifier, null)
    }

    GormStaticApi(Class<D> persistentClass, MappingContext mappingContext, List<FinderMethod> finders, DatastoreResolver resolver, String qualifier) {
        this(persistentClass, mappingContext, finders, resolver, qualifier, null)
    }

    GormStaticApi(Class<D> persistentClass, MappingContext mappingContext, List<FinderMethod> finders, DatastoreResolver resolver, String qualifier, GormRegistry registry) {
        super(persistentClass, mappingContext, resolver, qualifier, registry)
        this.finders = finders
    }

    @Override
    PlatformTransactionManager getTransactionManager() {
        Datastore ds = getDatastore()
        if (ds instanceof TransactionCapableDatastore) {
            return ((TransactionCapableDatastore)ds).getTransactionManager()
        }
        return null
    }

    @Override
    protected <T1> T1 executeQualified(String qualifier, SessionCallback<T1> callback) {
        GormStaticApi<D> qualifiedApi = registry.findStaticApi(persistentClass, qualifier)
        if (qualifiedApi != null && qualifiedApi != this) {
            return (T1) qualifiedApi.execute(callback)
        }
        return DatastoreUtils.execute(getDatastore(), callback)
    }

    @Override
    PersistentEntity getGormPersistentEntity() {
        PersistentEntity entity = qualifier != null ? registry.apiResolver.findEntity(persistentClass, qualifier) : null
        if (entity == null) {
            entity = super.getGormPersistentEntity()
        }
        if (entity == null) {
            entity = registry.apiResolver.findEntity(persistentClass)
        }
        // Final fallback: resolve from the mapping context captured when this API was constructed.
        // Entity metadata is identical across tenants/connections (only the datastore/session differs),
        // so this stable reference is the most reliable source and avoids a null entity when runtime
        // registry resolution is disturbed by cross-spec state — e.g. a qualified API created by
        // withTenant(tenantId) for DISCRIMINATOR/SCHEMA multi-tenancy.
        if (entity == null && mappingContext != null) {
            entity = mappingContext.getPersistentEntity(persistentClass.name)
        }
        entity
    }

    @Override
    List<FinderMethod> getGormDynamicFinders() {
        return finders
    }

    GormStaticApi<D> forQualifier(String qualifier) {
        Datastore ds = getDatastore()
        DatastoreResolver resolver = new DatastoreResolver() {
            @Override Datastore resolve() { registry.apiResolver.findDatastore(persistentClass, qualifier) }
        }
        List<FinderMethod> qualifiedFinders = registry.createDynamicFinders(resolver, ds.mappingContext)
        createStaticApi(persistentClass, ds.mappingContext, qualifiedFinders, resolver, qualifier)
    }

    protected GormStaticApi<D> createStaticApi(Class<D> persistentClass, MappingContext mappingContext, List<FinderMethod> finders, DatastoreResolver resolver, String qualifier) {
        new GormStaticApi<D>(persistentClass, mappingContext, finders, resolver, qualifier, registry)
    }

    @Override
    Object methodMissing(String name, Object args) {
        Object[] argsArray = (args instanceof Object[]) ? (Object[]) args : ([args] as Object[])
        for (FinderMethod fm : finders) {
            if (fm.isMethodMatch(name)) {
                return execute({ Session session ->
                    fm.invoke(persistentClass, name, argsArray)
                } as SessionCallback)
            }
        }
        throw new MissingMethodException(name, persistentClass, argsArray)
    }

    @Override
    Object propertyMissing(String name) {
        for (FinderMethod fm : finders) {
            if (fm.isMethodMatch(name)) {
                return { Object... args ->
                    Object[] finderArgs = args == null ? ([null] as Object[]) : args
                    execute({ Session session ->
                        fm.invoke(persistentClass, name, finderArgs)
                    } as SessionCallback)
                }
            }
        }
        
        Datastore ds = getDatastore()
        if (ds instanceof ConnectionSourcesProvider) {
            ConnectionSources sources = ((ConnectionSourcesProvider) ds).connectionSources
            if (sources != null) {
                if (sources.getConnectionSource(name) != null) {
                    return registry.findStaticApi(persistentClass, name)
                }
                if (name.equalsIgnoreCase(ConnectionSource.DEFAULT) || name.equalsIgnoreCase(ConnectionSource.OLD_DEFAULT)) {
                    return registry.findStaticApi(persistentClass, ConnectionSource.DEFAULT)
                }
            }
        }
        // Fallback: the preferred/transactional datastore may be a single-datasource datastore
        // that doesn't expose the named qualifier in its connectionSources. Check the registry
        // directly so that entities mapped to multiple datasources (e.g. datasource 'ALL') can
        // still be accessed via the qualifier even when a single-datasource transaction is active.
        if (registry.getDatastoreByString(persistentClass.name, name) != null) {
            return registry.findStaticApi(persistentClass, name)
        }
        throw new MissingPropertyException(name, persistentClass)
    }

    @Override
    void propertyMissing(String name, Object val) {
        throw new MissingPropertyException(name, persistentClass)
    }

    // GormInstanceOperations delegation
    @Override
    def propertyMissing(D instance, String name) {
        registry.findInstanceApi(persistentClass, null).propertyMissing(instance, name)
    }

    @Override
    boolean instanceOf(D instance, Class cls) {
        registry.findInstanceApi(persistentClass, null).instanceOf(instance, cls)
    }

    @Override
    D lock(D instance) {
        registry.findInstanceApi(persistentClass, null).lock(instance)
    }

    @Override
    def <T1> T1 mutex(D instance, Closure<T1> callable) {
        registry.findInstanceApi(persistentClass, null).mutex(instance, callable)
    }

    @Override
    D refresh(D instance) {
        registry.findInstanceApi(persistentClass, null).refresh(instance)
    }

    @Override
    D save(D instance) {
        registry.findInstanceApi(persistentClass, null).save(instance)
    }

    @Override
    D insert(D instance) {
        registry.findInstanceApi(persistentClass, null).insert(instance)
    }

    @Override
    D insert(D instance, Map params) {
        registry.findInstanceApi(persistentClass, null).insert(instance, params)
    }

    @Override
    D merge(D instance) {
        registry.findInstanceApi(persistentClass, null).merge(instance)
    }

    @Override
    D merge(D instance, Map params) {
        registry.findInstanceApi(persistentClass, null).merge(instance, params)
    }

    @Override
    D save(D instance, boolean validate) {
        registry.findInstanceApi(persistentClass, null).save(instance, validate)
    }

    @Override
    D save(D instance, Map params) {
        registry.findInstanceApi(persistentClass, null).save(instance, params)
    }

    @Override
    Serializable ident(D instance) {
        registry.findInstanceApi(persistentClass, null).ident(instance)
    }

    @Override
    D attach(D instance) {
        registry.findInstanceApi(persistentClass, null).attach(instance)
    }

    @Override
    boolean isAttached(D instance) {
        registry.findInstanceApi(persistentClass, null).isAttached(instance)
    }

    @Override
    void discard(D instance) {
        registry.findInstanceApi(persistentClass, null).discard(instance)
    }

    @Override
    void delete(D instance) {
        registry.findInstanceApi(persistentClass, null).delete(instance)
    }

    @Override
    void delete(D instance, Map params) {
        registry.findInstanceApi(persistentClass, null).delete(instance, params)
    }

    // GormStaticOperations
    @Override
    D get(Serializable id) {
        execute({ Session session ->
            session.retrieve(persistentClass, id)
        } as SessionCallback<D>)
    }

    @Override
    D read(Serializable id) {
        get(id)
    }

    @Override
    D load(Serializable id) {
        execute({ Session session ->
            session.proxy(persistentClass, id)
        } as SessionCallback<D>)
    }

    @Override
    D proxy(Serializable id) {
        load(id)
    }

    @Override
    List<D> getAll(Serializable... ids) {
        execute({ Session session ->
            session.retrieveAll(persistentClass, ids)
        } as SessionCallback<List<D>>)
    }

    @Override
    List<D> getAll(Iterable<Serializable> ids) {
        execute({ Session session ->
            session.retrieveAll(persistentClass, ids)
        } as SessionCallback<List<D>>)
    }

    @Override
    List<D> getAll() {
        list()
    }

    @Override
    List<D> list() {
        list(Collections.emptyMap())
    }

    @Override
    List<D> list(Map params) {
        execute({ Session session ->
            org.grails.datastore.mapping.query.Query q = session.createQuery(persistentClass)
            org.grails.datastore.gorm.finders.DynamicFinder.populateArgumentsForCriteria(persistentClass, q, params)
            if (params?.containsKey('max')) {
                return new grails.gorm.PagedResultList(q)
            }
            q.list()
        } as SessionCallback<List<D>>)
    }

    @Override
    Integer count() {
        log.debug('GormStaticApi.count() called for {}', persistentClass.name)
        Integer result = execute({ Session session ->
            def query = session.createQuery(persistentClass)
            query.projections().count()
            def res = query.singleResult()
            log.debug('Query singleResult returned {}', res)
            res instanceof Number ? ((Number)res).intValue() : 0
        } as SessionCallback<Integer>)
        log.debug('count() result is {}', result)
        return result
    }

    @Override
    Integer getCount() {
        count()
    }

    @Override
    boolean exists(Serializable id) {
        get(id) != null
    }

    @Override
    D first() {
        first([:])
    }

    @Override
    D first(String propertyName) {
        first(sort: propertyName)
    }

    @Override
    D first(Map params) {
        list(params + [max: 1])?.getAt(0)
    }

    @Override
    D last() {
        last([:])
    }

    @Override
    D last(String propertyName) {
        last(sort: propertyName, order: 'desc')
    }

    @Override
    D last(Map params) {
        list(params + [max: 1, order: 'desc'])?.getAt(0)
    }

    @Override
    BuildableCriteria createCriteria() {
        execute({ Session session ->
            new CriteriaBuilder(persistentClass, session)
        } as SessionCallback<BuildableCriteria>)
    }

    @Override
    def <T1> T1 withCriteria(Closure<T1> callable) {
        createCriteria().list(callable)
    }

    @Override
    def <T1> T1 withCriteria(Map builderArgs, Closure callable) {
        createCriteria().list(builderArgs, callable)
    }

    @Override
    D lock(Serializable id) {
        execute({ Session session ->
            session.lock(persistentClass, id)
        } as SessionCallback<D>)
    }

    @Override
    grails.gorm.DetachedCriteria<D> where(Closure callable) {
        new grails.gorm.DetachedCriteria<D>(persistentClass).withConnection(qualifier).where(callable)
    }

    @Override
    grails.gorm.DetachedCriteria<D> whereLazy(Closure callable) {
        where(callable)
    }

    @Override
    grails.gorm.DetachedCriteria<D> whereAny(Closure callable) {
        new grails.gorm.DetachedCriteria<D>(persistentClass).or(callable)
    }

    @Override
    List<Serializable> saveAll(Iterable<?> objectsToSave) {
        execute({ Session session ->
            session.persist(objectsToSave)
            session.flush()
        } as SessionCallback<List<Serializable>>)
    }

    @Override
    List<Serializable> saveAll(Object... objectsToSave) {
        saveAll(Arrays.asList(objectsToSave))
    }

    @Override
    Number deleteAll() {
        execute({ Session session ->
            session.deleteAll(new DetachedCriteria(persistentClass))
        } as SessionCallback<Number>)
    }

    @Override
    Number deleteAll(Map params) {
        deleteAll()
    }

    @Override
    void deleteAll(Iterable objectsToDelete) {
        execute({ Session session ->
            for (obj in objectsToDelete) {
                session.delete(obj)
            }
        } as SessionCallback<Void>)
    }

    @Override
    void deleteAll(Object... objectsToDelete) {
        deleteAll(Arrays.asList(objectsToDelete))
    }

    @Override
    void deleteAll(Map params, Iterable objectsToDelete) {
        execute({ Session session ->
            for (obj in objectsToDelete) {
                session.delete(obj)
            }
            if (params?.flush) {
                session.flush()
            }
        } as SessionCallback<Void>)
    }

    @Override
    void deleteAll(Map params, Object... objectsToDelete) {
        deleteAll(params, Arrays.asList(objectsToDelete))
    }

    @Override
    D create() {
        persistentClass.newInstance()
    }

    @Override
    List<D> findAll() {
        list()
    }

    @Override
    List<D> findAll(Map params) {
        list(params)
    }

    @Override
    List<D> findAll(D example) {
        findAll(example, Collections.emptyMap())
    }

    @Override
    List<D> findAll(D example, Map args) {
        def queryMap = createQueryMapForExample(getGormPersistentEntity(), example)
        findAllWhere(queryMap, args)
    }

    @Override
    List<D> findAll(Closure callable) {
        where(callable).list()
    }

    @Override
    List<D> findAll(Map args, Closure callable) {
        where(callable).list(args)
    }

    @Override
    D find(D example) {
        find(example, Collections.emptyMap())
    }

    @Override
    D find(D example, Map args) {
        def queryMap = createQueryMapForExample(getGormPersistentEntity(), example)
        findWhere(queryMap, args)
    }

    private Map createQueryMapForExample(org.grails.datastore.mapping.model.PersistentEntity pe, D example) {
        def props = pe.persistentProperties.findAll { org.grails.datastore.mapping.model.PersistentProperty prop ->
            !(prop instanceof org.grails.datastore.mapping.model.types.Association)
        }
        def queryMap = [:]
        for (org.grails.datastore.mapping.model.PersistentProperty prop in props) {
            def val = example[prop.name]
            if (val != null) {
                queryMap[prop.name] = val
            }
        }
        return queryMap
    }

    @Override
    D find(Closure callable) {
        where(callable).find()
    }

    @Override
    D findWhere(Map queryMap) {
        findWhere(queryMap, [:])
    }

    @Override
    D findWhere(Map queryMap, Map args) {
        execute({ Session session ->
            org.grails.datastore.mapping.query.Query q = session.createQuery(persistentClass)
            Map<String, Object> processedQueryMap = [:]
            queryMap.each { key, value -> processedQueryMap[key.toString()] = value }
            q.allEq(processedQueryMap)
            org.grails.datastore.gorm.finders.DynamicFinder.populateArgumentsForCriteria(persistentClass, q, args)
            q.singleResult()
        } as SessionCallback<D>)
    }

    @Override
    List<D> findAllWhere(Map queryMap) {
        findAllWhere(queryMap, [:])
    }

    @Override
    List<D> findAllWhere(Map queryMap, Map args) {
        (List<D>) execute({ Session session ->
            org.grails.datastore.mapping.query.Query q = session.createQuery(persistentClass)
            Map<String, Object> processedQueryMap = [:]
            queryMap.each { key, value -> processedQueryMap[key.toString()] = value }
            q.allEq(processedQueryMap)
            org.grails.datastore.gorm.finders.DynamicFinder.populateArgumentsForCriteria(persistentClass, q, args)
            q.list()
        } as SessionCallback<List>)
    }

    @Override
    D findOrCreateWhere(Map queryMap) {
        D instance = findWhere(queryMap)
        if (instance == null) {
            instance = persistentClass.newInstance(queryMap)
        }
        return instance
    }

    @Override
    D findOrSaveWhere(Map queryMap) {
        D instance = findWhere(queryMap)
        if (instance == null) {
            instance = persistentClass.newInstance(queryMap)
            ((GormEntity<D>)instance).save(flush: true)
        }
        return instance
    }

    @Override
    def <T1> T1 withSession(Closure<T1> callable) {
        execute({ Session session ->
            callable.call(session)
        } as SessionCallback<T1>)
    }

    @Override
    def <T1> T1 withDatastoreSession(Closure<T1> callable) {
        withSession(callable)
    }

    @Override
    def <T1> T1 withTransaction(Closure<T1> callable) {
        createTransactionTemplate().execute(callable)
    }

    @Override
    def <T1> T1 withNewTransaction(Closure<T1> callable) {
        DefaultTransactionDefinition definition = new DefaultTransactionDefinition()
        definition.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW)
        withTransaction(definition, callable)
    }

    @Override
    def <T1> T1 withTransaction(Map transactionProperties, Closure<T1> callable) {
        DefaultTransactionDefinition definition = new DefaultTransactionDefinition()
        transactionProperties.each { k, v ->
            if (v instanceof CharSequence && !(v instanceof String)) {
                v = v.toString()
            }
            try {
                definition[k as String] = v
            } catch (MissingPropertyException mpe) {
                throw new IllegalArgumentException("[${k}] is not a valid transaction property.")
            }
        }
        withTransaction(definition, callable)
    }

    @Override
    def <T1> T1 withNewTransaction(Map transactionProperties, Closure<T1> callable) {
        DefaultTransactionDefinition definition = new DefaultTransactionDefinition()
        transactionProperties.each { k, v ->
            if (v instanceof CharSequence && !(v instanceof String)) {
                v = v.toString()
            }
            try {
                definition[k as String] = v
            } catch (MissingPropertyException mpe) {
                throw new IllegalArgumentException("[${k}] is not a valid transaction property.")
            }
        }
        definition.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW)
        withTransaction(definition, callable)
    }

    @Override
    def <T1> T1 withTransaction(org.springframework.transaction.TransactionDefinition definition, Closure<T1> callable) {
        createTransactionTemplate(definition).execute(callable)
    }

    protected GrailsTransactionTemplate createTransactionTemplate() {
        getTransactionTemplateFactory().createTransactionTemplate(getTransactionManager())
    }

    protected GrailsTransactionTemplate createTransactionTemplate(org.springframework.transaction.TransactionDefinition definition) {
        getTransactionTemplateFactory().createTransactionTemplate(getTransactionManager(), definition)
    }

    protected TransactionTemplateFactory getTransactionTemplateFactory() {
        DEFAULT_TRANSACTION_TEMPLATE_FACTORY
    }

    @Override
    def <T1> T1 withNewSession(Closure<T1> callable) {
        Datastore ds = getDatastore()
        DatastoreUtils.executeWithNewSession(ds, { Session session ->
            callable.call(session)
        } as SessionCallback<T1>)
    }

    @Override
    def <T1> T1 withStatelessSession(Closure<T1> callable) {
        Datastore ds = getDatastore()
        DatastoreUtils.executeWithNewSession(ds, { Session session ->
            callable.call(session)
        } as SessionCallback<T1>)
    }

    @Override
    List executeQuery(CharSequence query) {
        throw new UnsupportedOperationException('String-based queries like [executeQuery] are currently not supported in this implementation of GORM. Use criteria instead.')
    }

    @Override
    List executeQuery(CharSequence query, Map args) {
        throw new UnsupportedOperationException('String-based queries like [executeQuery] are currently not supported in this implementation of GORM. Use criteria instead.')
    }

    @Override
    List executeQuery(CharSequence query, Map params, Map args) {
        throw new UnsupportedOperationException('String-based queries like [executeQuery] are currently not supported in this implementation of GORM. Use criteria instead.')
    }

    @Override
    List executeQuery(CharSequence query, Collection params) {
        throw new UnsupportedOperationException('String-based queries like [executeQuery] are currently not supported in this implementation of GORM. Use criteria instead.')
    }

    @Override
    List executeQuery(CharSequence query, Object... params) {
        throw new UnsupportedOperationException('String-based queries like [executeQuery] are currently not supported in this implementation of GORM. Use criteria instead.')
    }

    @Override
    List executeQuery(CharSequence query, Collection params, Map args) {
        throw new UnsupportedOperationException('String-based queries like [executeQuery] are currently not supported in this implementation of GORM. Use criteria instead.')
    }

    @Override
    Integer executeUpdate(CharSequence query) {
        throw new UnsupportedOperationException('String-based queries like [executeUpdate] are currently not supported in this implementation of GORM. Use criteria instead.')
    }

    @Override
    Integer executeUpdate(CharSequence query, Map args) {
        throw new UnsupportedOperationException('String-based queries like [executeUpdate] are currently not supported in this implementation of GORM. Use criteria instead.')
    }

    @Override
    Integer executeUpdate(CharSequence query, Map params, Map args) {
        throw new UnsupportedOperationException('String-based queries like [executeUpdate] are currently not supported in this implementation of GORM. Use criteria instead.')
    }

    @Override
    Integer executeUpdate(CharSequence query, Collection params) {
        throw new UnsupportedOperationException('String-based queries like [executeUpdate] are currently not supported in this implementation of GORM. Use criteria instead.')
    }

    @Override
    Integer executeUpdate(CharSequence query, Object... params) {
        throw new UnsupportedOperationException('String-based queries like [executeUpdate] are currently not supported in this implementation of GORM. Use criteria instead.')
    }

    @Override
    Integer executeUpdate(CharSequence query, Collection params, Map args) {
        throw new UnsupportedOperationException('String-based queries like [executeUpdate] are currently not supported in this implementation of GORM. Use criteria instead.')
    }

    @Override
    D find(CharSequence query) {
        throw new UnsupportedOperationException('String-based queries like [find] are currently not supported in this implementation of GORM. Use criteria instead.')
    }

    @Override
    D find(CharSequence query, Map params) {
        throw new UnsupportedOperationException('String-based queries like [find] are currently not supported in this implementation of GORM. Use criteria instead.')
    }

    @Override
    D find(CharSequence query, Map params, Map args) {
        throw new UnsupportedOperationException('String-based queries like [find] are currently not supported in this implementation of GORM. Use criteria instead.')
    }

    @Override
    D find(CharSequence query, Collection params) {
        throw new UnsupportedOperationException('String-based queries like [find] are currently not supported in this implementation of GORM. Use criteria instead.')
    }

    @Override
    D find(CharSequence query, Object[] params) {
        throw new UnsupportedOperationException('String-based queries like [find] are currently not supported in this implementation of GORM. Use criteria instead.')
    }

    @Override
    D find(CharSequence query, Collection params, Map args) {
        throw new UnsupportedOperationException('String-based queries like [find] are currently not supported in this implementation of GORM. Use criteria instead.')
    }

    @Override
    List<D> findAll(CharSequence query) {
        throw new UnsupportedOperationException('String-based queries like [findAll] are currently not supported in this implementation of GORM. Use criteria instead.')
    }

    @Override
    List<D> findAll(CharSequence query, Map params) {
        throw new UnsupportedOperationException('String-based queries like [findAll] are currently not supported in this implementation of GORM. Use criteria instead.')
    }

    @Override
    List<D> findAll(CharSequence query, Map params, Map args) {
        throw new UnsupportedOperationException('String-based queries like [findAll] are currently not supported in this implementation of GORM. Use criteria instead.')
    }

    @Override
    List<D> findAll(CharSequence query, Collection params) {
        throw new UnsupportedOperationException('String-based queries like [findAll] are currently not supported in this implementation of GORM. Use criteria instead.')
    }

    @Override
    List<D> findAll(CharSequence query, Object[] params) {
        throw new UnsupportedOperationException('String-based queries like [findAll] are currently not supported in this implementation of GORM. Use criteria instead.')
    }

    @Override
    List<D> findAll(CharSequence query, Collection params, Map args) {
        throw new UnsupportedOperationException('String-based queries like [findAll] are currently not supported in this implementation of GORM. Use criteria instead.')
    }

    @Override
    grails.gorm.api.GormAllOperations<D> withTenant(Serializable tenantId) {
        return (grails.gorm.api.GormAllOperations<D>) forQualifier(tenantId.toString())
    }

    @Override
    def <T1> T1 withTenant(Serializable tenantId, Closure<T1> callable) {
        withId(tenantId, callable)
    }

    @Override
    grails.gorm.api.GormAllOperations<D> eachTenant(Closure callable) {
        Datastore ds = registry.getDatastore(persistentClass.name, ConnectionSource.DEFAULT)
        if (ds instanceof MultiTenantCapableDatastore) {
            Tenants.eachTenant((MultiTenantCapableDatastore) ds, callable)
            return this
        }
        throw new UnsupportedOperationException("eachTenant not supported for datastore: ${ds?.class?.simpleName}")
    }

    def <T1> T1 withTenantTransaction(Serializable tenantId, Closure<T1> callable) {
        withId(tenantId, callable)
    }

    def <T1> T1 withTenantTransaction(Serializable tenantId, org.springframework.transaction.TransactionDefinition definition, Closure<T1> callable) {
        withId(tenantId, callable)
    }

    def <T1> T1 withId(Serializable tenantId, Closure<T1> callable) {
        // For multi-tenancy, always resolve via the DEFAULT (root/parent) datastore.
        // Resolving the tenant-specific datastore and then calling withNewSession() on it
        // would fail because child datastores have empty datastoresByConnectionSource maps.
        Datastore defaultDs = registry.getDatastore(persistentClass.name, ConnectionSource.DEFAULT)
        if (defaultDs instanceof MultiTenantCapableDatastore) {
            return (T1) Tenants.withId((MultiTenantCapableDatastore) defaultDs, tenantId, callable)
        }
        // Non-multi-tenant path: resolve the specific datastore for this connection/tenant key
        Datastore tenantDatastore = registry.apiResolver.findDatastore(persistentClass, tenantId.toString())
        return DatastoreUtils.execute(tenantDatastore, (Session session) -> {
            return (T1) callable.call(session)
        } as SessionCallback<T1>)
    }

    def <T1> T1 withoutId(Closure<T1> callable) {
        withId(ConnectionSource.DEFAULT, callable)
    }

    def <T1> T1 withNewSession(Serializable tenantId, Closure<T1> callable) {
        DatastoreResolver resolver = new DatastoreResolver() {
            @Override Datastore resolve() { registry.apiResolver.findDatastore(persistentClass, tenantId.toString()) }
        }
        Datastore tenantDatastore = resolver.resolve()
        DatastoreUtils.executeWithNewSession(tenantDatastore, { Session session ->
            return (T1) callable.call(session)
        } as SessionCallback<T1>)
    }
}

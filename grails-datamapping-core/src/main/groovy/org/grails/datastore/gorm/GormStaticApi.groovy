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

import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.DefaultTransactionDefinition

import grails.gorm.api.GormAllOperations
import grails.gorm.api.GormStaticOperations
import grails.gorm.api.GormInstanceOperations
import grails.gorm.CriteriaBuilder
import groovy.lang.Closure
import groovy.lang.MissingMethodException
import groovy.lang.MissingPropertyException
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic

import grails.gorm.transactions.GrailsTransactionTemplate
import org.grails.datastore.gorm.finders.FinderMethod
import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.core.Session
import org.grails.datastore.mapping.core.SessionCallback
import org.grails.datastore.mapping.core.connections.ConnectionSources
import org.grails.datastore.mapping.core.connections.ConnectionSourcesProvider
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.query.api.BuildableCriteria
import org.grails.datastore.mapping.reflect.NameUtils
import org.springframework.transaction.PlatformTransactionManager
import org.grails.datastore.mapping.transactions.TransactionCapableDatastore
import org.grails.datastore.mapping.core.DatastoreUtils
import grails.gorm.multitenancy.Tenants
import org.grails.datastore.mapping.multitenancy.MultiTenantCapableDatastore
import org.grails.datastore.mapping.multitenancy.exceptions.TenantNotFoundException

import groovy.util.logging.Slf4j

/**
 * Static methods for GORM
 *
 * @author Graeme Rocher
 */
@CompileDynamic
@Slf4j
class GormStaticApi<D> extends AbstractGormApi<D> implements GormAllOperations<D> {

    protected final List<FinderMethod> finders
    protected final String qualifier

    GormStaticApi(Class<D> persistentClass, MappingContext mappingContext, List<FinderMethod> finders) {
        this(persistentClass, mappingContext, finders, null, ConnectionSource.DEFAULT)
    }

    GormStaticApi(Class<D> persistentClass, MappingContext mappingContext, List<FinderMethod> finders, String qualifier) {
        this(persistentClass, mappingContext, finders, null, qualifier)
    }

    GormStaticApi(Class<D> persistentClass, MappingContext mappingContext, List<FinderMethod> finders, DatastoreResolver resolver, String qualifier) {
        super(persistentClass, mappingContext, resolver)
        this.finders = finders
        this.qualifier = qualifier ?: ConnectionSource.DEFAULT
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
    PersistentEntity getGormPersistentEntity() {
        super.getGormPersistentEntity()
    }

    @Override
    List<FinderMethod> getGormDynamicFinders() {
        return finders
    }

    GormStaticApi<D> forQualifier(String qualifier) {
        DatastoreResolver resolver = new DatastoreResolver() {
            @Override Datastore resolve() { GormEnhancer.findDatastore(persistentClass, qualifier) }
        }
        createStaticApi(persistentClass, getDatastore().mappingContext, finders, resolver, qualifier)
    }

    protected GormStaticApi<D> createStaticApi(Class<D> persistentClass, MappingContext mappingContext, List<FinderMethod> finders, DatastoreResolver resolver, String qualifier) {
        new GormStaticApi<D>(persistentClass, mappingContext, finders, resolver, qualifier)
    }

    @Override
    Object methodMissing(String name, Object args) {
        Object[] argsArray = (args instanceof Object[]) ? (Object[]) args : ([args] as Object[])
        for (FinderMethod fm : finders) {
            if (fm.isMethodMatch(name)) {
                return fm.invoke(persistentClass, name, argsArray)
            }
        }
        throw new MissingMethodException(name, persistentClass, argsArray)
    }

    @Override
    Object propertyMissing(String name) {
        for (FinderMethod fm : finders) {
            if (fm.isMethodMatch(name)) {
                return { Object... args ->
                    methodMissing(name, args)
                }
            }
        }
        
        Datastore ds = getDatastore()
        if (ds instanceof ConnectionSourcesProvider) {
            ConnectionSources sources = ((ConnectionSourcesProvider) ds).connectionSources
            if (sources != null) {
                if (sources.getConnectionSource(name) != null) {
                    return GormEnhancer.findStaticApi(persistentClass, name)
                }
                if (name.equalsIgnoreCase(ConnectionSource.DEFAULT) || name.equalsIgnoreCase(ConnectionSource.OLD_DEFAULT)) {
                    return GormEnhancer.findStaticApi(persistentClass, ConnectionSource.DEFAULT)
                }
            }
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
        GormEnhancer.findInstanceApi(persistentClass).propertyMissing(instance, name)
    }

    @Override
    boolean instanceOf(D instance, Class cls) {
        GormEnhancer.findInstanceApi(persistentClass).instanceOf(instance, cls)
    }

    @Override
    D lock(D instance) {
        GormEnhancer.findInstanceApi(persistentClass).lock(instance)
    }

    @Override
    def <T1> T1 mutex(D instance, Closure<T1> callable) {
        GormEnhancer.findInstanceApi(persistentClass).mutex(instance, callable)
    }

    @Override
    D refresh(D instance) {
        GormEnhancer.findInstanceApi(persistentClass).refresh(instance)
    }

    @Override
    D save(D instance) {
        GormEnhancer.findInstanceApi(persistentClass).save(instance)
    }

    @Override
    D insert(D instance) {
        GormEnhancer.findInstanceApi(persistentClass).insert(instance)
    }

    @Override
    D insert(D instance, Map params) {
        GormEnhancer.findInstanceApi(persistentClass).insert(instance, params)
    }

    @Override
    D merge(D instance) {
        GormEnhancer.findInstanceApi(persistentClass).merge(instance)
    }

    @Override
    D merge(D instance, Map params) {
        GormEnhancer.findInstanceApi(persistentClass).merge(instance, params)
    }

    @Override
    D save(D instance, boolean validate) {
        GormEnhancer.findInstanceApi(persistentClass).save(instance, validate)
    }

    @Override
    D save(D instance, Map params) {
        GormEnhancer.findInstanceApi(persistentClass).save(instance, params)
    }

    @Override
    Serializable ident(D instance) {
        GormEnhancer.findInstanceApi(persistentClass).ident(instance)
    }

    @Override
    D attach(D instance) {
        GormEnhancer.findInstanceApi(persistentClass).attach(instance)
    }

    @Override
    boolean isAttached(D instance) {
        GormEnhancer.findInstanceApi(persistentClass).isAttached(instance)
    }

    @Override
    void discard(D instance) {
        GormEnhancer.findInstanceApi(persistentClass).discard(instance)
    }

    @Override
    void delete(D instance) {
        GormEnhancer.findInstanceApi(persistentClass).delete(instance)
    }

    @Override
    void delete(D instance, Map params) {
        GormEnhancer.findInstanceApi(persistentClass).delete(instance, params)
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
            q.list()
        } as SessionCallback<List<D>>)
    }

    @Override
    Integer count() {
        log.debug("GormStaticApi.count() called for {}", persistentClass.name)
        Integer result = execute({ Session session ->
            def query = session.createQuery(persistentClass);
            query.projections().count();
            def res = query.singleResult();
            log.debug("Query singleResult returned {}", res)
            res instanceof Number ? ((Number)res).intValue() : 0
        } as SessionCallback<Integer>)
        log.debug("count() result is {}", result)
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
        new grails.gorm.DetachedCriteria<D>(persistentClass).where(callable)
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
        } as SessionCallback<List<Serializable>>)
    }

    @Override
    List<Serializable> saveAll(Object... objectsToSave) {
        saveAll(Arrays.asList(objectsToSave))
    }

    @Override
    Number deleteAll() {
        execute({ Session session ->
            session.createQuery(persistentClass).deleteAll()
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
        execute({ Session session ->
            def query = session.createQuery(persistentClass)
            populateQueryByExample(session, query, example)
            query.list(args)
        } as SessionCallback<List<D>>)
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
        execute({ Session session ->
            def query = session.createQuery(persistentClass)
            populateQueryByExample(session, query, example)
            query.singleResult()
        } as SessionCallback<D>)
    }

    private void populateQueryByExample(Session session, org.grails.datastore.mapping.query.Query query, D example) {
        def pe = getGormPersistentEntity()
        def persister = session.getPersister(example)
        if (persister != null) {
            def id = persister.getObjectIdentifier(example)
            if (id != null) {
                query.add(org.grails.datastore.mapping.query.Restrictions.eq(pe.identity.name, id))
            }
            else {
                def ea = pe.mappingContext.createEntityAccess(pe, example)
                for (prop in pe.persistentProperties) {
                    if (prop instanceof org.grails.datastore.mapping.model.types.Simple || prop instanceof org.grails.datastore.mapping.model.types.Basic) {
                        def val = ea.getProperty(prop.name)
                        if (val != null) {
                            query.add(org.grails.datastore.mapping.query.Restrictions.eq(prop.name, val))
                        }
                    }
                }
            }
        }
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
        where {
            for (entry in queryMap) {
                eq(entry.key.toString(), entry.value)
            }
        }.find(args)
    }

    @Override
    List<D> findAllWhere(Map queryMap) {
        findAllWhere(queryMap, [:])
    }

    @Override
    List<D> findAllWhere(Map queryMap, Map args) {
        where {
            for (entry in queryMap) {
                eq(entry.key.toString(), entry.value)
            }
        }.list(args)
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
            ((GormEntity<D>)instance).save(flush:true)
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
        new GrailsTransactionTemplate(getTransactionManager()).execute(callable)
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
        for (String key : transactionProperties.keySet()) {
            if (definition.metaClass.hasProperty(definition, key)) {
                definition.setProperty(key, transactionProperties.get(key))
            }
        }
        withTransaction(definition, callable)
    }

    @Override
    def <T1> T1 withNewTransaction(Map transactionProperties, Closure<T1> callable) {
        DefaultTransactionDefinition definition = new DefaultTransactionDefinition()
        for (String key : transactionProperties.keySet()) {
            if (definition.metaClass.hasProperty(definition, key)) {
                definition.setProperty(key, transactionProperties.get(key))
            }
        }
        definition.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW)
        withTransaction(definition, callable)
    }

    @Override
    def <T1> T1 withTransaction(org.springframework.transaction.TransactionDefinition definition, Closure<T1> callable) {
        new GrailsTransactionTemplate(getTransactionManager(), definition).execute(callable)
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
        throw new UnsupportedOperationException("String-based queries like [executeQuery] are currently not supported in this implementation of GORM. Use criteria instead.")
    }

    @Override
    List executeQuery(CharSequence query, Map args) {
        throw new UnsupportedOperationException("String-based queries like [executeQuery] are currently not supported in this implementation of GORM. Use criteria instead.")
    }

    @Override
    List executeQuery(CharSequence query, Map params, Map args) {
        throw new UnsupportedOperationException("String-based queries like [executeQuery] are currently not supported in this implementation of GORM. Use criteria instead.")
    }

    @Override
    List executeQuery(CharSequence query, Collection params) {
        throw new UnsupportedOperationException("String-based queries like [executeQuery] are currently not supported in this implementation of GORM. Use criteria instead.")
    }

    @Override
    List executeQuery(CharSequence query, Object... params) {
        throw new UnsupportedOperationException("String-based queries like [executeQuery] are currently not supported in this implementation of GORM. Use criteria instead.")
    }

    @Override
    List executeQuery(CharSequence query, Collection params, Map args) {
        throw new UnsupportedOperationException("String-based queries like [executeQuery] are currently not supported in this implementation of GORM. Use criteria instead.")
    }

    @Override
    Integer executeUpdate(CharSequence query) {
        throw new UnsupportedOperationException("String-based queries like [executeUpdate] are currently not supported in this implementation of GORM. Use criteria instead.")
    }

    @Override
    Integer executeUpdate(CharSequence query, Map args) {
        throw new UnsupportedOperationException("String-based queries like [executeUpdate] are currently not supported in this implementation of GORM. Use criteria instead.")
    }

    @Override
    Integer executeUpdate(CharSequence query, Map params, Map args) {
        throw new UnsupportedOperationException("String-based queries like [executeUpdate] are currently not supported in this implementation of GORM. Use criteria instead.")
    }

    @Override
    Integer executeUpdate(CharSequence query, Collection params) {
        throw new UnsupportedOperationException("String-based queries like [executeUpdate] are currently not supported in this implementation of GORM. Use criteria instead.")
    }

    @Override
    Integer executeUpdate(CharSequence query, Object... params) {
        throw new UnsupportedOperationException("String-based queries like [executeUpdate] are currently not supported in this implementation of GORM. Use criteria instead.")
    }

    @Override
    Integer executeUpdate(CharSequence query, Collection params, Map args) {
        throw new UnsupportedOperationException("String-based queries like [executeUpdate] are currently not supported in this implementation of GORM. Use criteria instead.")
    }

    @Override
    D find(CharSequence query) {
        throw new UnsupportedOperationException("String-based queries like [find] are currently not supported in this implementation of GORM. Use criteria instead.")
    }

    @Override
    D find(CharSequence query, Map params) {
        throw new UnsupportedOperationException("String-based queries like [find] are currently not supported in this implementation of GORM. Use criteria instead.")
    }

    @Override
    D find(CharSequence query, Map params, Map args) {
        throw new UnsupportedOperationException("String-based queries like [find] are currently not supported in this implementation of GORM. Use criteria instead.")
    }

    @Override
    D find(CharSequence query, Collection params) {
        throw new UnsupportedOperationException("String-based queries like [find] are currently not supported in this implementation of GORM. Use criteria instead.")
    }

    @Override
    D find(CharSequence query, Object[] params) {
        throw new UnsupportedOperationException("String-based queries like [find] are currently not supported in this implementation of GORM. Use criteria instead.")
    }

    @Override
    D find(CharSequence query, Collection params, Map args) {
        throw new UnsupportedOperationException("String-based queries like [find] are currently not supported in this implementation of GORM. Use criteria instead.")
    }

    @Override
    List<D> findAll(CharSequence query) {
        throw new UnsupportedOperationException("String-based queries like [findAll] are currently not supported in this implementation of GORM. Use criteria instead.")
    }

    @Override
    List<D> findAll(CharSequence query, Map params) {
        throw new UnsupportedOperationException("String-based queries like [findAll] are currently not supported in this implementation of GORM. Use criteria instead.")
    }

    @Override
    List<D> findAll(CharSequence query, Map params, Map args) {
        throw new UnsupportedOperationException("String-based queries like [findAll] are currently not supported in this implementation of GORM. Use criteria instead.")
    }

    @Override
    List<D> findAll(CharSequence query, Collection params) {
        throw new UnsupportedOperationException("String-based queries like [findAll] are currently not supported in this implementation of GORM. Use criteria instead.")
    }

    @Override
    List<D> findAll(CharSequence query, Object[] params) {
        throw new UnsupportedOperationException("String-based queries like [findAll] are currently not supported in this implementation of GORM. Use criteria instead.")
    }

    @Override
    List<D> findAll(CharSequence query, Collection params, Map args) {
        throw new UnsupportedOperationException("String-based queries like [findAll] are currently not supported in this implementation of GORM. Use criteria instead.")
    }

    @Override
    grails.gorm.api.GormAllOperations<D> withTenant(Serializable tenantId) {
        Datastore tenantDatastore = GormEnhancer.findDatastore(persistentClass, tenantId.toString())
        DatastoreResolver resolver = new DatastoreResolver() {
            @Override Datastore resolve() { tenantDatastore }
        }
        return (grails.gorm.api.GormAllOperations<D>) new GormStaticApi<D>(persistentClass, tenantDatastore.mappingContext, finders, resolver, tenantId.toString())
    }

    @Override
    def <T1> T1 withTenant(Serializable tenantId, Closure<T1> callable) {
        withId(tenantId, callable)
    }

    @Override
    grails.gorm.api.GormAllOperations<D> eachTenant(Closure callable) {
        throw new UnsupportedOperationException("eachTenant not supported")
    }

    def <T1> T1 withTenantTransaction(Serializable tenantId, Closure<T1> callable) {
        withId(tenantId, callable)
    }

    def <T1> T1 withTenantTransaction(Serializable tenantId, org.springframework.transaction.TransactionDefinition definition, Closure<T1> callable) {
        withId(tenantId, callable)
    }

    def <T1> T1 withId(Serializable tenantId, Closure<T1> callable) {
        DatastoreResolver resolver = new DatastoreResolver() {
            @Override Datastore resolve() { GormEnhancer.findDatastore(persistentClass, tenantId.toString()) }
        }
        Datastore tenantDatastore = resolver.resolve()
        if (tenantDatastore instanceof MultiTenantCapableDatastore) {
            return (T1) Tenants.withId((MultiTenantCapableDatastore)tenantDatastore, tenantId, callable)
        } else {
            return DatastoreUtils.execute(tenantDatastore, (Session session) -> {
                return (T1) callable.call(session)
            } as SessionCallback<T1>)
        }
    }

    def <T1> T1 withoutId(Closure<T1> callable) {
        withId(ConnectionSource.DEFAULT, callable)
    }

    def <T1> T1 withNewSession(Serializable tenantId, Closure<T1> callable) {
        DatastoreResolver resolver = new DatastoreResolver() {
            @Override Datastore resolve() { GormEnhancer.findDatastore(persistentClass, tenantId.toString()) }
        }
        Datastore tenantDatastore = resolver.resolve()
        DatastoreUtils.executeWithNewSession(tenantDatastore, { Session session ->
            return (T1) callable.call(session)
        } as SessionCallback<T1>)
    }
}

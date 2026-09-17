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
package org.grails.datastore.mapping.core

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executor

import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.RemovalCause
import com.github.benmanes.caffeine.cache.RemovalListener
import groovy.transform.CompileStatic
import groovy.transform.PackageScope
import jakarta.persistence.FlushModeType
import org.springframework.beans.BeanWrapper
import org.springframework.beans.BeanWrapperImpl
import org.springframework.context.ApplicationEventPublisher
import org.springframework.core.convert.ConversionFailedException
import org.springframework.core.convert.ConversionService
import org.springframework.dao.DataAccessResourceFailureException
import org.springframework.dao.InvalidDataAccessResourceUsageException
import org.springframework.transaction.NoTransactionException
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.DefaultTransactionDefinition
import org.springframework.util.Assert

import org.grails.datastore.mapping.cache.TPCacheAdapterRepository
import org.grails.datastore.mapping.config.Entity
import org.grails.datastore.mapping.core.impl.PendingDelete
import org.grails.datastore.mapping.core.impl.PendingInsert
import org.grails.datastore.mapping.core.impl.PendingOperation
import org.grails.datastore.mapping.core.impl.PendingOperationExecution
import org.grails.datastore.mapping.core.impl.PendingUpdate
import org.grails.datastore.mapping.dirty.checking.DirtyCheckable
import org.grails.datastore.mapping.dirty.checking.DirtyCheckingSupport
import org.grails.datastore.mapping.engine.EntityAccess
import org.grails.datastore.mapping.engine.EntityPersister
import org.grails.datastore.mapping.engine.NativeEntryEntityPersister
import org.grails.datastore.mapping.engine.NonPersistentTypeException
import org.grails.datastore.mapping.engine.Persister
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.query.Query
import org.grails.datastore.mapping.query.api.QueryableCriteria
import org.grails.datastore.mapping.transactions.Transaction

/**
 * Abstract implementation of the {@link org.grails.datastore.mapping.core.Session} interface that uses
 * a list of {@link org.grails.datastore.mapping.engine.Persister} instances
 * to save, update and delete instances
 *
 * @param <N>
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
@SuppressWarnings(['rawtypes', 'unchecked'])
abstract class AbstractSession<N> extends AbstractAttributeStoringSession implements SessionImplementor {

    public static final String ENTITY_ACCESS = 'org.grails.gorm.ENTITY_ACCESS'

    private static final RemovalListener<PersistentEntity, Collection<PendingInsert>> EXCEPTION_THROWING_INSERT_LISTENER =
            evictionGuard('Maximum number (5000) of insert operations to flush() exceeded. Flush the session periodically to avoid this error for batch operations.')

    private static final RemovalListener<PersistentEntity, Collection<PendingUpdate>> EXCEPTION_THROWING_UPDATE_LISTENER =
            evictionGuard('Maximum number (5000) of update operations to flush() exceeded. Flush the session periodically to avoid this error for batch operations.')

    private static final RemovalListener<PersistentEntity, Collection<PendingDelete>> EXCEPTION_THROWING_DELETE_LISTENER =
            evictionGuard('Maximum number (5000) of delete operations to flush() exceeded. Flush the session periodically to avoid this error for batch operations.')

    private static final Executor DIRECT_EXECUTOR = { Runnable runnable -> runnable.run() } as Executor
    private static final String NULL = 'null'
    private static final long MAX_PENDING_OPERATIONS = 5000

    protected Map<Class, Persister> persisters = new ConcurrentHashMap<>()
    protected boolean isSynchronizedWithTransaction = false
    private MappingContext mappingContext
    protected ConcurrentLinkedQueue lockedObjects = new ConcurrentLinkedQueue()
    protected Transaction transaction
    private Datastore datastore
    private FlushModeType flushMode = FlushModeType.AUTO
    protected Map<Class, Map<Serializable, Object>> firstLevelCache = new ConcurrentHashMap<>()
    protected Map<Class, Map<Serializable, Object>> firstLevelEntryCache = new ConcurrentHashMap<>()
    protected Map<Class, Map<Serializable, Object>> firstLevelEntryCacheDirtyCheck = new ConcurrentHashMap<>()
    protected Map<CollectionKey, Collection> firstLevelCollectionCache = new ConcurrentHashMap<>()

    protected TPCacheAdapterRepository cacheAdapterRepository

    private Collection<Serializable> objectsPendingOperations = new ConcurrentLinkedQueue<>()
    private Map<PersistentEntity, Collection<PendingInsert>> pendingInserts = boundedPendingMap(EXCEPTION_THROWING_INSERT_LISTENER)

    private Map<PersistentEntity, Collection<PendingUpdate>> pendingUpdates = boundedPendingMap(EXCEPTION_THROWING_UPDATE_LISTENER)

    private Map<PersistentEntity, Collection<PendingDelete>> pendingDeletes = boundedPendingMap(EXCEPTION_THROWING_DELETE_LISTENER)

    protected Collection<Runnable> postFlushOperations = new ConcurrentLinkedQueue<>()
    private boolean exceptionOccurred
    protected ApplicationEventPublisher publisher

    protected boolean stateless = false
    protected boolean flushActive = false

    AbstractSession(Datastore datastore, MappingContext mappingContext,
                    ApplicationEventPublisher publisher) {
        this(datastore, mappingContext, publisher, false)
    }

    AbstractSession(Datastore datastore, MappingContext mappingContext,
                    ApplicationEventPublisher publisher, boolean stateless) {
        this.mappingContext = mappingContext
        this.datastore = datastore
        this.publisher = publisher
        this.stateless = stateless
    }

    AbstractSession(Datastore datastore, MappingContext mappingContext,
                    ApplicationEventPublisher publisher, TPCacheAdapterRepository cacheAdapterRepository) {
        this(datastore, mappingContext, publisher, false)
        this.cacheAdapterRepository = cacheAdapterRepository
    }

    AbstractSession(Datastore datastore, MappingContext mappingContext,
                    ApplicationEventPublisher publisher, TPCacheAdapterRepository cacheAdapterRepository, boolean stateless) {
        this(datastore, mappingContext, publisher, stateless)
        this.cacheAdapterRepository = cacheAdapterRepository
    }

    private static <V> RemovalListener<PersistentEntity, V> evictionGuard(final String message) {
        return { PersistentEntity key, V value, RemovalCause cause ->
            if (cause.wasEvicted()) {
                throw new DataAccessResourceFailureException(message)
            }
        } as RemovalListener<PersistentEntity, V>
    }

    private static <K, V> Map<K, V> boundedPendingMap(RemovalListener<K, V> listener) {
        Caffeine builder = Caffeine.newBuilder()
        builder = builder.removalListener(listener).executor(DIRECT_EXECUTOR).maximumSize(MAX_PENDING_OPERATIONS)
        return (Map<K, V>) builder.build().asMap()
    }

    @Override
    boolean isSchemaless() {
        return this.datastore.isSchemaless()
    }

    @Override
    boolean isStateless() {
        return this.stateless
    }

    void addPostFlushOperation(Runnable runnable) {
        if (runnable != null && !postFlushOperations.contains(runnable)) {
            postFlushOperations.add(runnable)
        }
    }

    void addPendingInsert(PendingInsert insert) {
        final Object o = insert.getObject()
        if (o != null) {
            registerPending(o)
        }
        Collection<PendingInsert> inserts = pendingInserts.get(insert.getEntity())
        if (inserts == null) {
            inserts = new ConcurrentLinkedQueue<>()
            pendingInserts.put(insert.getEntity(), inserts)
        }

        inserts.add(insert)
    }

    @Override
    boolean isPendingAlready(Object obj) {
        Serializable id = getPersister(obj).getObjectIdentifier(obj)
        if (id != null) {
            return objectsPendingOperations.contains(id)
        }
        else {
            return objectsPendingOperations.contains(System.identityHashCode(obj))
        }
    }

    @Override
    void registerPending(Object obj) {
        if (obj != null) {
            Serializable id = getPersister(obj).getObjectIdentifier(obj)
            if (id != null) {
                if (!objectsPendingOperations.contains(id)) {
                    objectsPendingOperations.add(id)
                }
            }
            else {
                final int identityHashCode = System.identityHashCode(obj)
                if (!objectsPendingOperations.contains(identityHashCode)) {
                    objectsPendingOperations.add(identityHashCode)
                }
            }
        }
    }

    void addPendingUpdate(PendingUpdate update) {
        final Object o = update.getObject()
        if (o != null) {
            registerPending(o)
        }

        Collection<PendingUpdate> inserts = pendingUpdates.get(update.getEntity())
        if (inserts == null) {
            inserts = new ConcurrentLinkedQueue<>()
            pendingUpdates.put(update.getEntity(), inserts)
        }

        inserts.add(update)
    }

    void addPendingDelete(PendingDelete delete) {
        final Object o = delete.getObject()
        if (o != null) {
            registerPending(o)
        }

        Collection<PendingDelete> deletes = pendingDeletes.get(delete.getEntity())
        if (deletes == null) {
            deletes = new ConcurrentLinkedQueue<>()
            pendingDeletes.put(delete.getEntity(), deletes)
        }

        deletes.add(delete)
    }

    Object getCachedEntry(PersistentEntity entity, Serializable key) {
        if (isStateless(entity)) {
            return null
        }
        return getCachedEntry(entity, key, false)
    }

    Object getCachedEntry(PersistentEntity entity, Serializable key, boolean forDirtyCheck) {
        if (isStateless(entity)) {
            return null
        }
        if (key == null) {
            return null
        }

        return getEntryCache(entity.getJavaClass(), forDirtyCheck).get(key)
    }

    void cacheEntry(PersistentEntity entity, Serializable key, Object entry) {
        if (isStateless(entity)) {
            return
        }
        if (key == null || entry == null) {
            return
        }

        cacheEntry(key, entry, getEntryCache(entity.getJavaClass(), true), true)
        cacheEntry(key, entry, getEntryCache(entity.getJavaClass(), false), false)
    }

    boolean isStateless(PersistentEntity entity) {
        Entity mappedForm = entity != null ? entity.getMapping().getMappedForm() : null
        return isStateless() || (mappedForm != null && mappedForm.isStateless())
    }

    protected void cacheEntry(Serializable key, Object entry, Map<Serializable, Object> entryCache, boolean forDirtyCheck) {
        if (isStateless()) {
            return
        }
        entryCache.put(key, entry)
    }

    Collection getCachedCollection(PersistentEntity entity, Serializable key, String name) {
        if (isStateless(entity)) {
            return null
        }
        if (key == null || name == null) {
            return null
        }

        return firstLevelCollectionCache.get(
                new CollectionKey(entity.getJavaClass(), key, name))
    }

    void cacheCollection(PersistentEntity entity, Serializable key, Collection collection, String name) {
        if (isStateless(entity)) {
            return
        }
        if (key == null || collection == null || name == null) {
            return
        }

        firstLevelCollectionCache.put(
                new CollectionKey(entity.getJavaClass(), key, name),
                collection)
    }

    Map<PersistentEntity, Collection<PendingInsert>> getPendingInserts() {
        return pendingInserts
    }

    Map<PersistentEntity, Collection<PendingUpdate>> getPendingUpdates() {
        return pendingUpdates
    }

    Map<PersistentEntity, Collection<PendingDelete>> getPendingDeletes() {
        return pendingDeletes
    }

    FlushModeType getFlushMode() {
        return flushMode
    }

    void setFlushMode(FlushModeType flushMode) {
        this.flushMode = flushMode
    }

    Datastore getDatastore() {
        return datastore
    }

    MappingContext getMappingContext() {
        return mappingContext
    }

    void flush() {
        if (flushActive) {
            return
        }

        boolean hasInserts = false
        try {
            if (exceptionOccurred) {
                throw new InvalidDataAccessResourceUsageException(
                        'Do not flush() the Session after an exception occurs')
            }

            flushActive = true

            hasInserts = hasUpdates()
            if (hasInserts) {
                flushPendingInserts(pendingInserts)
                flushPendingUpdates(pendingUpdates)
                flushPendingDeletes(pendingDeletes)

                firstLevelCollectionCache.clear()

                executePendings(postFlushOperations)
            }
        }
        finally {
            clearPendingOperations()
            flushActive = false
        }
        postFlush(hasInserts)
    }

    protected void flushPendingDeletes(Map<PersistentEntity, Collection<PendingDelete>> pendingDeletes) {
        final Collection<Collection<PendingDelete>> deletes = pendingDeletes.values()
        for (Collection<PendingDelete> delete in deletes) {
            flushPendingOperations(delete)
        }
    }

    boolean isDirty(Object instance) {
        if (instance == null) {
            return false
        }

        EntityPersister persister = (EntityPersister) getPersister(instance)
        if (persister == null) {
            return false
        }

        if (instance instanceof DirtyCheckable) {
            return ((DirtyCheckable) instance).hasChanged() || DirtyCheckingSupport.areAssociationsDirty(persister.getPersistentEntity(), instance)
        }

        if (!(persister instanceof NativeEntryEntityPersister)) {
            return false
        }

        Serializable id = persister.getObjectIdentifier(instance)
        if (id == null) {
            // not persistent
            return false
        }

        Object entry = getCachedEntry(persister.getPersistentEntity(), id, false)
        Object instance2 = getCachedInstance(instance.getClass(), id)
        return !instance.is(instance2) || ((NativeEntryEntityPersister) persister).isDirty(instance, entry)
    }

    @Override
    Serializable getObjectIdentifier(Object instance) {
        Persister persister = getPersister(instance)
        if (persister != null) {
            return persister.getObjectIdentifier(instance)
        }
        return null
    }

    /**
     * The default implementation of flushPendingUpdates is to iterate over each update operation
     * and execute them one by one. This may be suboptimal for stores that support batch update
     * operations. Subclasses can override this method to implement batch update more efficiently.
     *
     * @param updates
     */
    protected void flushPendingUpdates(Map<PersistentEntity, Collection<PendingUpdate>> updates) {
        for (Collection<PendingUpdate> pending in updates.values()) {
            flushPendingOperations(pending)
        }
    }

    /**
     * The default implementation of flushPendingInserts is to iterate over each insert operations
     * and execute them one by one. This may be suboptimal for stores that support batch insert
     * operations. Subclasses can override this method to implement batch insert more efficiently.
     *
     * @param inserts The insert operations
     */
    protected void flushPendingInserts(Map<PersistentEntity, Collection<PendingInsert>> inserts) {
        for (Collection<PendingInsert> pending in inserts.values()) {
            flushPendingOperations(pending)
        }
    }

    private void flushPendingOperations(Collection operations) {
        for (Object o in operations) {
            PendingOperation pendingOperation = (PendingOperation) o
            try {
                PendingOperationExecution.executePendingOperation(pendingOperation)
            }
            catch (RuntimeException e) {
                setFlushMode(FlushModeType.COMMIT)
                exceptionOccurred = true
                throw e
            }
        }
    }

    private boolean hasUpdates() {
        return !pendingInserts.isEmpty() || !pendingUpdates.isEmpty() || !pendingDeletes.isEmpty() || !postFlushOperations.isEmpty()
    }

    protected void postFlush(boolean hasUpdates) {
        // do nothing
    }

    protected void executePendings(Collection<? extends Runnable> pendings) {
        try {
            for (Runnable pending in pendings) {
                pending.run()
            }
        }
        catch (RuntimeException e) {
            setFlushMode(FlushModeType.COMMIT)
            exceptionOccurred = true
            throw e
        }
    }

    void clear() {
        clearMaps(firstLevelCache)
        clearMaps(firstLevelEntryCache)
        clearMaps(firstLevelEntryCacheDirtyCheck)
        firstLevelCollectionCache.clear()
        clearPendingOperations()
        attributes.clear()
        exceptionOccurred = false
    }

    protected void clearPendingOperations() {
        objectsPendingOperations.clear()
        pendingInserts.clear()
        pendingUpdates.clear()
        pendingDeletes.clear()
        postFlushOperations.clear()
    }

    private void clearMaps(Map<Class, Map<Serializable, Object>> mapOfMaps) {
        for (Map<Serializable, Object> cache in mapOfMaps.values()) {
            cache.clear()
        }
    }

    final Persister getPersister(Object o) {
        if (o == null) {
            return null
        }
        Class cls
        if (o instanceof Class) {
            cls = (Class) o
        }
        else if (o instanceof PersistentEntity) {
            cls = ((PersistentEntity) o).getJavaClass()
        }
        else {
            cls = o.getClass()
        }
        Persister p = persisters.get(cls)
        if (p == null) {
            p = createPersister(cls, getMappingContext())
            if (p != null) {
                if (!isStateless(((EntityPersister) p).getPersistentEntity())) {
                    firstLevelCache.put(cls, new ConcurrentHashMap<>())
                }
                persisters.put(cls, p)
            }
        }
        return p
    }

    protected abstract Persister createPersister(Class cls, MappingContext mappingContext)

    boolean contains(Object o) {
        if (o == null || isStateless()) {
            return false
        }

        final Serializable identifier = getObjectIdentifier(o)
        if (identifier != null) {
            return getInstanceCache(o.getClass()).containsKey(identifier)
        }
        else {
            return getInstanceCache(o.getClass()).containsValue(o)
        }
    }

    boolean isCached(Class type, Serializable key) {
        PersistentEntity entity = getMappingContext().getPersistentEntity(type.getName())
        if (type == null || key == null || isStateless(entity)) {
            return false
        }

        return getInstanceCache(type).containsKey(key)
    }

    void cacheInstance(Class type, Serializable key, Object instance) {
        if (type == null || key == null || instance == null) {
            return
        }
        if (isStateless(getMappingContext().getPersistentEntity(type.getName()))) {
            return
        }
        getInstanceCache(type).put(key, instance)
    }

    Object getCachedInstance(Class type, Serializable key) {
        if (isStateless()) {
            return null
        }
        if (type == null || key == null) {
            return null
        }
        if (isStateless(getMappingContext().getPersistentEntity(type.getName()))) {
            return null
        }
        return getInstanceCache(type).get(key)
    }

    void clear(Object o) {
        if (o == null || isStateless()) {
            return
        }

        final Map<Serializable, Object> cache = firstLevelCache.get(o.getClass())
        if (cache != null) {
            Persister persister = getPersister(o)
            Serializable key = persister.getObjectIdentifier(o)
            if (key != null) {
                cache.remove(key)
            }
        }
        removeAttributesForEntity(o)
    }

    void attach(Object o) {
        if (o == null) {
            return
        }

        EntityPersister p = (EntityPersister) getPersister(o)
        if (p == null) {
            return
        }

        Serializable identifier = p.getObjectIdentifier(o)
        if (identifier != null) {
            cacheObject(identifier, o)
        }
    }

    protected void cacheObject(Serializable identifier, Object o) {
        if (identifier == null || o == null) {
            return
        }
        cacheInstance(o.getClass(), identifier, o)
    }

    Serializable persist(Object o) {
        Assert.notNull(o, 'Cannot persist null object')
        Persister persister = getPersister(o)
        if (persister == null) {
            throw new NonPersistentTypeException('Object [' + o +
                    '] cannot be persisted. It is not a known persistent type.')
        }

        final Serializable key = persister.persist(o)
        cacheObject(key, o)
        return key
    }

    @Override
    Serializable insert(Object o) {
        Assert.notNull(o, 'Cannot persist null object')
        Persister persister = getPersister(o)
        if (persister == null) {
            throw new NonPersistentTypeException('Object [' + o +
                    '] cannot be persisted. It is not a known persistent type.')
        }

        final Serializable key = persister.insert(o)
        cacheObject(key, o)
        return key
    }

    void refresh(Object o) {
        Assert.notNull(o, 'Cannot persist null object')
        Persister persister = getPersister(o)
        if (persister == null) {
            throw new NonPersistentTypeException('Object [' + o +
                    '] cannot be refreshed. It is not a known persistent type.')
        }

        final Serializable key = persister.refresh(o)
        cacheObject(key, o)
    }

    Object retrieve(Class type, Serializable key) {
        if (key == null || type == null || NULL.equals(key)) {
            return null
        }

        Persister persister = getPersister(type)
        if (persister == null) {
            throw new NonPersistentTypeException('Cannot retrieve object with key [' + key +
                    ']. The class [' + type.getName() + '] is not a known persistent type.')
        }

        Serializable resolvedKey = key
        final PersistentEntity entity = ((EntityPersister) persister).getPersistentEntity()
        if (entity != null) {
            final PersistentProperty identity = entity.getIdentity()
            if (!identity.getType().isAssignableFrom(key.getClass())) {
                resolvedKey = convertIdentityIfNecessasry(identity, key)
            }
        }

        if (resolvedKey == null) {
            return null
        }

        Object o = getInstanceCache(type).get(resolvedKey)
        if (o == null) {
            o = persister.retrieve(resolvedKey)
            if (o != null) {
                cacheObject(resolvedKey, o)
            }
        }
        return o
    }

    protected Serializable convertIdentityIfNecessasry(PersistentProperty identity, Serializable key) {
        ConversionService conversionService = getMappingContext().getConversionService()
        Serializable converted = key
        if (conversionService.canConvert(key.getClass(), identity.getType())) {
            try {
                converted = (Serializable) conversionService.convert(key, identity.getType())
            }
            catch (ConversionFailedException ignored) {
                // ignore
            }
        }
        return converted
    }

    Object proxy(Class type, Serializable key) {
        if (key == null || type == null) {
            return null
        }

        Persister persister = getPersister(type)
        if (persister == null) {
            throw new NonPersistentTypeException('Cannot retrieve object with key [' + key +
                    ']. The class [' + type.getName() + '] is not a known persistent type.')
        }

        // only return proxy if real instance is not available.
        Object o = getInstanceCache(type).get(key)
        if (o == null) {
            o = persister.proxy(key)
        }

        return o
    }

    void lock(Object o) {
        throw new UnsupportedOperationException('Datastore [' + getClass().getName() + '] does not support locking.')
    }

    Object lock(Class type, Serializable key) {
        throw new UnsupportedOperationException('Datastore [' + getClass().getName() + '] does not support locking.')
    }

    void unlock(Object o) {
        if (o != null) {
            lockedObjects.remove(o)
        }
    }

    /**
     * This default implementation of the deleteAll method is unlikely to be optimal as it iterates and deletes each object.
     * <p>
     * Subclasses should override to optimize for the batch operation capability of the underlying datastore
     *
     * @param criteria The criteria
     */
    long deleteAll(QueryableCriteria criteria) {
        List list = criteria.list()
        delete(list)
        return list.size()
    }

    /**
     * This default implementation of updateAll is unlikely to be optimal as it iterates and updates each object one by one.
     * <p>
     * Subclasses should override to optimize for the batch operation capability of the underlying datastore
     *
     * @param criteria   The criteria
     * @param properties The properties
     */
    long updateAll(QueryableCriteria criteria, Map<String, Object> properties) {
        List list = criteria.list()
        for (Object o in list) {
            BeanWrapper bean = new BeanWrapperImpl(o)
            for (String property in properties.keySet()) {
                bean.setPropertyValue(property, properties.get(property))
            }
        }
        persist(list)
        return list.size()
    }

    void delete(final Object obj) {
        if (obj == null) {
            return
        }

        final EntityPersister p = (EntityPersister) getPersister(obj)
        if (p == null) {
            return
        }

        p.delete(obj)
        clear(obj)
    }

    void delete(final Iterable objects) {
        if (objects == null) {
            return
        }

        // sort the objects into sets by Persister, in case the objects are of different types.
        Map<Persister, List> toDelete = new HashMap<>()
        for (Object object in objects) {
            if (object == null) {
                continue
            }
            final Persister p = getPersister(object)
            if (p == null) {
                continue
            }
            List listForPersister = toDelete.get(p)
            if (listForPersister == null) {
                listForPersister = new ArrayList()
                toDelete.put(p, listForPersister)
            }
            listForPersister.add(object)
        }
        // for each type (usually only 1 type), set up a pendingDelete of that type
        for (Map.Entry<Persister, List> entry in toDelete.entrySet()) {
            final EntityPersister p = (EntityPersister) entry.getKey()
            p.delete(entry.getValue())
        }
    }

    List<Serializable> persist(Iterable objects) {
        if (objects == null) {
            return Collections.emptyList()
        }

        final Iterator i = objects.iterator()
        if (!i.hasNext()) {
            return Collections.emptyList()
        }

        // peek at the first object to get the persister
        final Object obj = i.next()
        final Persister p = getPersister(obj)
        if (p == null) {
            throw new NonPersistentTypeException('Cannot persist objects. The class [' +
                    obj.getClass().getName() + '] is not a known persistent type.')
        }

        return p.persist(objects)
    }

    List retrieveAll(Class type, Iterable keys) {
        EntityPersister p = (EntityPersister) getPersister(type)
        if (p == null) {
            throw new NonPersistentTypeException('Cannot retrieve objects with keys [' + keys +
                    ']. The class [' + type.getName() + '] is not a known persistent type.')
        }

        List list = new ArrayList()
        List<Serializable> toRetrieve = new ArrayList<>()
        final Map<Serializable, Object> cache = getInstanceCache(type)
        for (Object key in keys) {
            Serializable serializable = (Serializable) key
            Object cached = cache.get(serializable)
            list.add(cached)
            if (cached == null) {
                toRetrieve.add(serializable)
            }
        }
        List<Object> retrieved = p.retrieveAll(toRetrieve)
        Iterator<Serializable> keyIterator = toRetrieve.iterator()
        Map<Serializable, Object> retrievedMap = new HashMap<>()
        for (Object o in retrieved) {
            final Serializable identifier = p.getObjectIdentifier(o)
            if (identifier != null) {
                retrievedMap.put(identifier, o)
            }
        }
        // now fill in the null entries (possibly with more nulls)
        for (int i = 0; i < list.size(); i++) {
            Object o = list.get(i)
            if (o == null) {
                if (keyIterator.hasNext()) {
                    Serializable key = keyIterator.next()
                    key = (Serializable) mappingContext.getConversionService().convert(key, p.getPersistentEntity().getIdentity().getType())
                    final Object next = retrievedMap.get(key)
                    list.set(i, next)
                    cacheInstance(type, key, next)
                }
            }
        }
        return list
    }

    List retrieveAll(Class type, Serializable... keys) {
        Persister p = getPersister(type)
        if (p == null) {
            throw new NonPersistentTypeException('Cannot retrieve objects with keys [' + keys +
                    ']. The class [' + type.getName() + '] is not a known persistent type.')
        }
        return retrieveAll(type, Arrays.asList(keys))
    }

    Query createQuery(Class type) {
        Persister p = getPersister(type)
        if (p == null) {
            throw new NonPersistentTypeException('Cannot create query. The class [' + type +
                    '] is not a known persistent type.')
        }

        return p.createQuery()
    }

    final Transaction beginTransaction() {
        return beginTransaction(new DefaultTransactionDefinition())
    }

    @Override
    Transaction beginTransaction(TransactionDefinition definition) {
        transaction = beginTransactionInternal()
        return transaction
    }

    protected abstract Transaction beginTransactionInternal()

    Transaction getTransaction() {
        if (transaction == null) {
            throw new NoTransactionException('Transaction not started. Call beginTransaction() first')
        }
        return transaction
    }

    @Override
    boolean hasTransaction() {
        return transaction != null
    }

    private Map<Serializable, Object> getInstanceCache(Class c) {
        Map<Serializable, Object> cache = firstLevelCache.get(c)
        if (cache == null) {
            cache = new ConcurrentHashMap<>()
            firstLevelCache.put(c, cache)
        }
        return cache
    }

    private Map<Serializable, Object> getEntryCache(Class c, boolean forDirtyCheck) {
        Map<Class, Map<Serializable, Object>> caches = forDirtyCheck ? firstLevelEntryCacheDirtyCheck : firstLevelEntryCache
        Map<Serializable, Object> cache = caches.get(c)
        if (cache == null) {
            cache = new ConcurrentHashMap<>()
            caches.put(c, cache)
        }
        return cache
    }

    @Override
    EntityAccess createEntityAccess(PersistentEntity entity, Object instance) {
        return getMappingContext().createEntityAccess(entity, instance)
    }

    /**
     * Whether the session is synchronized with an external transaction
     *
     * @param isSynchronizedWithTransaction True if it is
     */
    void setSynchronizedWithTransaction(boolean isSynchronizedWithTransaction) {
        this.isSynchronizedWithTransaction = isSynchronizedWithTransaction
    }

    @PackageScope
    static class CollectionKey {

        private final Class clazz
        private final Serializable key
        private final String collectionName

        @PackageScope
        CollectionKey(Class clazz, Serializable key, String collectionName) {
            this.clazz = clazz
            this.key = key
            this.collectionName = collectionName
        }

        @Override
        int hashCode() {
            int value = 17
            value = value * 37 + clazz.getName().hashCode()
            value = value * 37 + key.hashCode()
            value = value * 37 + collectionName.hashCode()
            return value
        }

        @Override
        boolean equals(Object obj) {
            CollectionKey other = (CollectionKey) obj
            return other.clazz.getName() == clazz.getName() &&
                    other.key.equals(key) &&
                    other.collectionName.equals(collectionName)
        }

        @Override
        String toString() {
            return clazz.getName() + ':' + key + ':' + collectionName
        }

    }

}

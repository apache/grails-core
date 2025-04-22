/* Copyright (C) 2010 SpringSource
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.datastore.mapping.core;

import java.io.Serializable;
import java.util.Collection;
import java.util.Map;

import org.grails.datastore.mapping.core.impl.PendingDelete;
import org.grails.datastore.mapping.core.impl.PendingInsert;
import org.grails.datastore.mapping.core.impl.PendingUpdate;
import org.grails.datastore.mapping.engine.EntityAccess;
import org.grails.datastore.mapping.model.PersistentEntity;

/**
 * Methods for the implementation of the {@link Session} interface to implement.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@SuppressWarnings("rawtypes")
public interface SessionImplementor<T> {

    /**
     * @return Whether the session is stateless
     */
    boolean isStateless();

    /**
     * @return Whether the session is stateless
     */
    boolean isStateless(PersistentEntity entity);

    /**
     * Adds a pending insert operation
     *
     * @param insert The pending insert operation
     */
    void addPendingInsert(PendingInsert insert);

    /**
     * Adds a pending update operation
     * @param update The pending update operation
     */
    void addPendingUpdate(PendingUpdate update);

    /**
     * @return The pending insert operations
     */
    Map<PersistentEntity, Collection<PendingInsert>> getPendingInserts();

    /**
     * @return The pending updates
     */
    Map<PersistentEntity, Collection<PendingUpdate>> getPendingUpdates();

    /**
     * @return The pending deletes
     */
    Map<PersistentEntity, Collection<PendingDelete>> getPendingDeletes();

    /**
     * Caches a native entry
     * @param entity The entity
     * @param key The key
     * @param entry The native entry
     */
    void cacheEntry(PersistentEntity entity, Serializable key, T entry);

    /**
     * Obtains a cached entry
     * @param entity The entity
     * @param key The key
     * @return The cached entry
     */
    T getCachedEntry(PersistentEntity entity, Serializable key);
    /**
     * Obtains a cached entry
     * @param entity The entity
     * @param key The key
     * @param forDirtyCheck Whether to obtain for purposes for dirty checking
     * @return The cached entry
     */
    T getCachedEntry(PersistentEntity entity, Serializable key, boolean forDirtyCheck);

    /**
     * Caches an instance
     * @param type The type
     * @param key The key
     * @param instance The instance
     */
    void cacheInstance(Class type, Serializable key, Object instance);

    /**
     * Get the cached instance if it exists.
     * @param type the object type
     * @param key the object key
     * @return the instance or <code>null</code>
     */
    Object getCachedInstance(Class type, Serializable key);

    /**
     * Whether an object with the specified key is contained within the first level cache.
     * @param type the object type
     * @param key The key to check
     * @return <code>true</code> if it is
     */
    boolean isCached(Class type, Serializable key);

    /**
     * Obtains a cached collection
     *
     * @param entity The entity
     * @param key The key
     * @param name The name
     * @return The cached collection or null
     */
    Collection getCachedCollection(PersistentEntity entity, Serializable key, String name);

    /**
     * Caches a collection
     *
     * @param entity The entity
     * @param key The key
     * @param collection The collection
     * @param name The name of the collection
     */
    void cacheCollection(PersistentEntity entity, Serializable key, Collection collection, String name);

    /**
     * Adds an operation to be executed after a flush
     * @param runnable The runnable
     */
    void addPostFlushOperation(Runnable runnable);

    /**
     * @param obj Whether the given object is already pending
     * @return True if it is
     */
    boolean isPendingAlready(T obj);

    /**
     * Register a pending object
     * @param obj The object to register
     */
    void registerPending(T obj);

    EntityAccess createEntityAccess(PersistentEntity entity, Object instance);
}

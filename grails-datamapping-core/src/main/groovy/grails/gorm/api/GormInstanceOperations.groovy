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

package grails.gorm.api

import groovy.transform.CompileStatic

import org.grails.datastore.mapping.reflect.ClassUtils

/**
 * Instance methods of the GORM API.
 *
 * @author Graeme Rocher
 * @param <D> the entity/domain class
 */
interface GormInstanceOperations<D> {

    /**
     * The message reported when a datastore does not support refreshing an instance under a pessimistic lock,
     * whether requested as {@code instance.refresh(lock: true)} or {@code DomainClass.lock(id, refresh: true)}.
     */
    String REFRESH_LOCK_UNSUPPORTED = 'Datastore implementation does not support refreshing under a pessimistic lock'

    /**
     * The {@link #refresh(java.lang.Object, java.util.Map)} argument that requests a pessimistic write lock.
     */
    String ARGUMENT_LOCK = 'lock'

    /**
     * Allow access to datasource by name
     *
     * @param instance The instance
     * @param name The property name
     * @return The property value
     */
    def propertyMissing(D instance, String name)

    /**
     * Proxy aware instanceOf implementation.
     */
    boolean instanceOf(D instance, Class cls)

    /**
     * Upgrades an existing persistence instance to a write lock
     * @return The instance
     */
    D lock(D instance)

    /**
     * Locks the instance for updates for the scope of the passed closure
     *
     * @param callable The closure
     * @return The result of the closure
     */
    <T> T mutex(D instance, Closure<T> callable)

    /**
     * Refreshes the state of the current instance
     * @return The instance
     */
    D refresh(D instance)

    /**
     * Refreshes the state of the given instance, with options.
     *
     * <p>Supported arguments:</p>
     * <ul>
     *   <li>{@code lock} - when {@code true}, reloads the instance's database state and version under a
     *   pessimistic write lock in a single operation, discarding unflushed changes. Requires an active
     *   transaction, which holds the lock until it commits or rolls back.</li>
     * </ul>
     *
     * <p>Without {@code lock: true} this behaves like {@link #refresh(java.lang.Object)}. The default
     * implementation rejects {@code lock: true}; datastores that support it override this method.</p>
     *
     * @param instance The instance
     * @param args The named arguments
     * @return The same instance
     * @throws jakarta.persistence.TransactionRequiredException if {@code lock: true} is requested without an active transaction
     * @throws UnsupportedOperationException if {@code lock: true} is requested and the datastore does not support it
     */
    @CompileStatic
    default D refresh(D instance, Map args) {
        if (ClassUtils.getBooleanFromMap(ARGUMENT_LOCK, args)) {
            throw new UnsupportedOperationException(REFRESH_LOCK_UNSUPPORTED)
        }
        refresh(instance)
    }

    /**
     * Saves an object the datastore
     * @return Returns the instance
     */
    D save(D instance)

    /**
     * Forces an insert of an object to the datastore
     * @return Returns the instance
     */
    D insert(D instance)

    /**
     * Forces an insert of an object to the datastore
     * @return Returns the instance
     */
    D insert(D instance, Map params)

    /**
     * Saves an object the datastore
     * @return Returns the instance
     */
    D merge(D instance)

    /**
     * Saves an object the datastore
     * @return Returns the instance
     */
    D merge(D instance, Map params)

    /**
     * Save method that takes a boolean which indicates whether to perform validation or not
     *
     * @param validate Whether to perform validation
     *
     * @return The instance or null if validation fails
     */
    D save(D instance, boolean validate)

    /**
     * Saves an object with the given parameters
     * @param instance The instance
     * @param params The parameters
     * @return The instance
     */
    D save(D instance, Map params)

    /**
     * Returns the objects identifier
     */
    Serializable ident(D instance)

    /**
     * Attaches an instance to an existing session. Requries a session-based model
     * @return
     */
    D attach(D instance)

    /**
     * No concept of session-based model so defaults to true
     */
    boolean isAttached(D instance)

    /**
     * Discards any pending changes. Requires a session-based model.
     */
    void discard(D instance)
    /**
     * Deletes an instance from the datastore
     */
    void delete(D instance)

    /**
     * Deletes an instance from the datastore
     */
    void delete(D instance, Map params)
}

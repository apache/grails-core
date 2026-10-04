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

import org.grails.datastore.gorm.internal.RefreshLockArguments

/**
 * Instance methods of the GORM API.
 *
 * @author Graeme Rocher
 * @param <D> the entity/domain class
 */
interface GormInstanceOperations<D> {

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
     * Locks the given instance for updates for the scope of the passed closure.
     *
     * <p>The lock is exclusive. A datastore that reports {@link #supportsLockedRefresh()} reloads the
     * instance's state and version under the lock, so that a competing writer is waited for and the closure
     * runs on the committed state rather than failing on the version loaded earlier. Reloading discards
     * unflushed changes to the instance, and requires an active transaction and an attached instance.</p>
     *
     * @param instance The instance
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
     *   <li>{@code lock} - {@code true} reloads the instance's database state and version under a pessimistic
     *   write lock; a {@link jakarta.persistence.LockModeType} reloads them under that lock mode instead. Either
     *   form acquires the lock before it reloads, discards unflushed changes, requires an attached
     *   instance and an active transaction, and holds the lock until that transaction commits or rolls back.
     *   {@code false} and {@link jakarta.persistence.LockModeType#NONE} request no lock.</li>
     * </ul>
     *
     * <p>Without a requested lock this behaves like {@link #refresh(java.lang.Object)}. The default
     * implementation rejects a requested lock; datastores that support it override this method.</p>
     *
     * @param instance The instance
     * @param args The named arguments
     * @return The same instance
     * @throws RuntimeException an implementation-specific exception if a lock is requested without an active
     * transaction, such as {@code jakarta.persistence.TransactionRequiredException} for Hibernate
     * @throws IllegalArgumentException if a lock is requested for an instance that is not attached to the
     * current session, or the {@code lock} argument is neither a boolean nor a lock mode
     * @throws UnsupportedOperationException if a lock is requested and the datastore does not support it
     */
    @CompileStatic
    default D refresh(D instance, Map args) {
        if (RefreshLockArguments.lockModeFrom(args) != null) {
            throw new UnsupportedOperationException(RefreshLockArguments.UNSUPPORTED)
        }
        refresh(instance)
    }

    /**
     * Whether {@link #refresh(java.lang.Object, java.util.Map)} reloads an instance under a requested lock.
     *
     * <p>A datastore that overrides {@code refresh(D, Map)} to honour the {@code lock} argument must report
     * {@code true} here; one that overrides it for unrelated arguments must not, so that callers are told the
     * lock is unavailable instead of receiving an instance that was never locked.</p>
     *
     * @return {@code false} for the default implementation, which rejects a requested lock
     */
    @CompileStatic
    default boolean supportsLockedRefresh() {
        false
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

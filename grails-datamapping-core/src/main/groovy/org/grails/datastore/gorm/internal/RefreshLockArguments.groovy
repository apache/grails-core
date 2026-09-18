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

package org.grails.datastore.gorm.internal

import groovy.transform.CompileStatic

import jakarta.persistence.LockModeType

/**
 * Resolves the named arguments shared by {@code refresh(Map)} and {@code lock(Map, Serializable)}.
 *
 * @since 8.1
 */
@CompileStatic
class RefreshLockArguments {

    /**
     * The {@code refresh(Map)} argument that requests a lock: {@code true} for a pessimistic write lock, or a
     * {@link LockModeType} naming the lock to acquire.
     */
    static final String LOCK = 'lock'

    /**
     * The {@code lock(Map, Serializable)} argument that requests a reload of the instance's state and version
     * under the lock.
     */
    static final String REFRESH = 'refresh'

    /**
     * The {@code lock(Map, Serializable)} argument that selects the {@link LockModeType} to acquire; defaults to
     * {@link LockModeType#PESSIMISTIC_WRITE}.
     */
    static final String TYPE = 'type'

    /**
     * The message reported when a datastore does not support refreshing an instance under a lock.
     */
    static final String UNSUPPORTED = 'Datastore implementation does not support refreshing under a lock'

    /**
     * The message reported when a datastore does not support lock modes other than a pessimistic write lock.
     */
    static final String UNSUPPORTED_TYPE = 'Datastore implementation does not support lock types other than PESSIMISTIC_WRITE'

    /**
     * The message reported when a lock or a locked refresh is requested outside of an active transaction.
     */
    static final String TRANSACTION_REQUIRED = 'An active transaction is required.'

    /**
     * Resolves the lock requested by the {@code lock} argument.
     *
     * @param args The named arguments, may be {@code null}
     * @return The requested lock mode, or {@code null} when no lock was requested
     * @throws IllegalArgumentException if the argument is neither a boolean, a {@link LockModeType}, nor the name of one
     */
    static LockModeType lockModeFrom(Map args) {
        Object value = args?.get(LOCK)
        if (value == null) {
            return null
        }
        if (value instanceof Boolean) {
            return value ? LockModeType.PESSIMISTIC_WRITE : null
        }
        if (value instanceof CharSequence) {
            String text = value.toString().trim()
            if (text.equalsIgnoreCase('true')) {
                return LockModeType.PESSIMISTIC_WRITE
            }
            if (text.equalsIgnoreCase('false')) {
                return null
            }
        }
        withoutNone(parseLockMode(LOCK, value, 'a boolean or a jakarta.persistence.LockModeType'))
    }

    /**
     * Resolves the lock mode selected by the {@code type} argument of {@code lock(Map, Serializable)}.
     *
     * @param args The named arguments, may be {@code null}
     * @return The selected lock mode, {@link LockModeType#PESSIMISTIC_WRITE} when the argument is absent
     * @throws IllegalArgumentException if the argument is neither a {@link LockModeType} nor the name of one,
     * or is {@link LockModeType#NONE}
     */
    static LockModeType lockTypeFrom(Map args) {
        Object value = args?.get(TYPE)
        if (value == null) {
            return LockModeType.PESSIMISTIC_WRITE
        }
        LockModeType lockMode = parseLockMode(TYPE, value, 'a jakarta.persistence.LockModeType')
        if (lockMode == LockModeType.NONE) {
            throw new IllegalArgumentException("The '${TYPE}' argument must name a lock but was NONE")
        }
        lockMode
    }

    /**
     * @param args The named arguments, may be {@code null}
     * @return Whether the {@code refresh} argument requests a reload under the lock
     * @throws IllegalArgumentException if the argument is neither a boolean nor the text of one
     */
    static boolean refreshRequested(Map args) {
        Object value = args?.get(REFRESH)
        if (value == null) {
            return false
        }
        if (value instanceof Boolean) {
            return (Boolean) value
        }
        if (value instanceof CharSequence) {
            String text = value.toString().trim()
            if (text.equalsIgnoreCase('true')) {
                return true
            }
            if (text.equalsIgnoreCase('false')) {
                return false
            }
            throw new IllegalArgumentException("The '${REFRESH}' argument must be a boolean but was '${text}'")
        }
        throw new IllegalArgumentException("The '${REFRESH}' argument must be a boolean but was an instance of " +
                value.getClass().name)
    }

    /**
     * @return whether the lock mode takes a database lock, as opposed to the optimistic modes that only
     * verify or increment the version when the transaction completes
     */
    static boolean pessimistic(LockModeType lockMode) {
        lockMode == LockModeType.PESSIMISTIC_READ ||
                lockMode == LockModeType.PESSIMISTIC_WRITE ||
                lockMode == LockModeType.PESSIMISTIC_FORCE_INCREMENT
    }

    private static LockModeType parseLockMode(String argument, Object value, String expected) {
        if (value instanceof LockModeType) {
            return value
        }
        if (value instanceof CharSequence) {
            String text = value.toString().trim()
            try {
                return LockModeType.valueOf(text.toUpperCase(Locale.ROOT))
            }
            catch (IllegalArgumentException ignored) {
                throw invalid(argument, expected, "'${text}'")
            }
        }
        throw invalid(argument, expected, "an instance of ${value.getClass().name}")
    }

    private static LockModeType withoutNone(LockModeType lockMode) {
        lockMode == LockModeType.NONE ? null : lockMode
    }

    private static IllegalArgumentException invalid(String argument, String expected, String actual) {
        new IllegalArgumentException("The '${argument}' argument must be ${expected} but was ${actual}")
    }
}

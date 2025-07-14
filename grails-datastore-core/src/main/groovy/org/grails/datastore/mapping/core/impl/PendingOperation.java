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
package org.grails.datastore.mapping.core.impl;

import org.grails.datastore.mapping.model.PersistentEntity;

import java.util.List;

/**
 * An operation that is pending execution.
 *
 * @param <E> The native entity type (examples could be Row, Document etc.)
 * @param <K> The native key
 * @author Graeme Rocher
 */
public interface PendingOperation<E, K> extends Runnable {

    /**
     * @return Whether it was already executed
     */
    boolean wasExecuted();

    /**
     * @return Whether the operation has been vetoed
     */
    boolean isVetoed();

    /**
     * @return The {@link PersistentEntity} being inserted
     */
    PersistentEntity getEntity();

    /**
     * @return The target object of the pending operation
     */
    Object getObject();

    /**
     * @return The native key to insert
     */
    K getNativeKey();

    /**
     * @return The native entry to persist
     */
    E getNativeEntry();

    /**
     * Operations to be executed directly prior to this operation
     *
     * @return The operations to execute prior
     */
    List<PendingOperation<E, K>> getPreOperations();

    /**
     * Adds an operation to executed prior to other operations
     *
     * @param preOperation The prior operation
     */
    void addPreOperation(PendingOperation<E, K> preOperation);

    /**
     * Operations to be executed directly following this operation
     *
     * @return The operations to cascade to
     */
    List<PendingOperation<E, K>> getCascadeOperations();

    /**
     * Adds an operation that should be executed after this operation
     *
     * @param pendingOperation The pending operation
     */
    void addCascadeOperation(PendingOperation<E, K> pendingOperation);
}

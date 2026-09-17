/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  'License'); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  'AS IS' BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.grails.datastore.mapping.core.impl

import groovy.transform.CompileStatic

import org.grails.datastore.mapping.model.PersistentEntity

/**
 * Base implementation of the {@link PendingOperation} interface.
 *
 * @author Graeme Rocher
 *
 * @param <E> The native entity type (examples could be Row, Document etc.)
 * @param <K> The native key
 */
@CompileStatic
abstract class PendingOperationAdapter<E, K> implements PendingOperation<E, K> {

    protected PersistentEntity entity
    protected K nativeKey
    protected E nativeEntry
    private List<PendingOperation<E, K>> pendingOperations = new LinkedList<>()
    private List<PendingOperation<E, K>> preOperations = new LinkedList<>()
    private boolean vetoed
    protected boolean executed

    PendingOperationAdapter(PersistentEntity entity, K nativeKey, E nativeEntry) {
        this.entity = entity
        this.nativeKey = nativeKey
        this.nativeEntry = nativeEntry
    }

    @Override
    boolean wasExecuted() {
        return this.executed
    }

    void setExecuted(boolean executed) {
        this.executed = executed
    }

    boolean isVetoed() {
        return vetoed
    }

    void setVetoed(boolean vetoed) {
        this.vetoed = vetoed
    }

    List<PendingOperation<E, K>> getPreOperations() {
        return Collections.unmodifiableList(preOperations)
    }

    void addPreOperation(PendingOperation<E, K> preOperation) {
        preOperations.add(preOperation)
    }

    List<PendingOperation<E, K>> getCascadeOperations() {
        return pendingOperations
    }

    void addCascadeOperation(PendingOperation<E, K> pendingOperation) {
        pendingOperations.add(pendingOperation)
    }

    K getNativeKey() {
        return nativeKey
    }

    PersistentEntity getEntity() {
        return entity
    }

    E getNativeEntry() {
        return nativeEntry
    }

    @Override
    Object getObject() {
        return nativeEntry
    }

}

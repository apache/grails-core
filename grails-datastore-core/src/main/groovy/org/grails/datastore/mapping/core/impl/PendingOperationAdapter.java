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
package org.grails.datastore.mapping.core.impl;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import org.grails.datastore.mapping.model.PersistentEntity;

/**
 * Base implementation of the {@link PendingOperation} interface.
 *
 * @author Graeme Rocher
 *
 * @param <E> The native entity type (examples could be Row, Document etc.)
 * @param <K> The native key
 */
public abstract class PendingOperationAdapter<E, K> implements PendingOperation<E, K> {

    protected PersistentEntity entity;
    protected K nativeKey;
    protected E nativeEntry;
    private List<PendingOperation<E, K>> pendingOperations = new LinkedList<PendingOperation<E, K>>();
    private List<PendingOperation<E, K>> preOperations = new LinkedList<PendingOperation<E, K>>();
    private boolean vetoed;
    protected boolean executed;

    public PendingOperationAdapter(PersistentEntity entity, K nativeKey, E nativeEntry) {
        this.entity = entity;
        this.nativeKey = nativeKey;
        this.nativeEntry = nativeEntry;
    }

    @Override
    public boolean wasExecuted() {
        return this.executed;
    }

    public void setExecuted(boolean executed) {
        this.executed = executed;
    }

    public boolean isVetoed() {
        return vetoed;
    }

    public void setVetoed(boolean vetoed) {
        this.vetoed = vetoed;
    }

    public List<PendingOperation<E, K>> getPreOperations() {
        return Collections.unmodifiableList(preOperations);
    }

    public void addPreOperation(PendingOperation<E, K> preOperation) {
        preOperations.add(preOperation);
    }

    public List<PendingOperation<E, K>> getCascadeOperations() {
        return pendingOperations;
    }

    public void addCascadeOperation(PendingOperation<E, K> pendingOperation) {
        pendingOperations.add(pendingOperation);
    }

    public K getNativeKey() {
        return nativeKey;
    }

    public PersistentEntity getEntity() {
        return entity;
    }

    public E getNativeEntry() {
        return nativeEntry;
    }

    @Override
    public Object getObject() {
        return nativeEntry;
    }
}

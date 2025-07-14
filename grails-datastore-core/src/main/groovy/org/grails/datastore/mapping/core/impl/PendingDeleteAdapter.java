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

/**
 * Adapter for the {@link org.grails.datastore.mapping.core.impl.PendingDelete} interface
 *
 * @author Graeme Rocher
 * @since 5.0.0
 */
public abstract class PendingDeleteAdapter<E, K> extends PendingOperationAdapter<E, K> implements PendingDelete<E, K> {

    private boolean vetoed;

    public PendingDeleteAdapter(PersistentEntity entity, K nativeKey, E nativeEntry) {
        super(entity, nativeKey, nativeEntry);
    }

    public boolean isVetoed() {
        return vetoed;
    }

    public void setVetoed(boolean vetoed) {
        this.vetoed = vetoed;
    }

    @Override
    public Object getObject() {
        return getNativeEntry();
    }
}

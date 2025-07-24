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
package org.grails.datastore.mapping.core;

import org.springframework.dao.OptimisticLockingFailureException;

import org.grails.datastore.mapping.model.PersistentEntity;

/**
 * Indicates an optimistic locking violation during an update.
 *
 * @author Burt Beckwith
 * @since 1.0
 */
public class OptimisticLockingException extends OptimisticLockingFailureException {

    private static final long serialVersionUID = 1;

    private final Object key;
    private final PersistentEntity persistentEntity;

    public OptimisticLockingException(final PersistentEntity persistentEntity, final Object key) {
        super("The instance was updated by another user while you were editing");
        this.key = key;
        this.persistentEntity = persistentEntity;
    }

    public PersistentEntity getPersistentEntity() {
        return persistentEntity;
    }

    public Object getKey() {
        return key;
    }
}

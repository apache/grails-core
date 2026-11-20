/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.grails.orm.hibernate.support.hibernate7;

import java.util.List;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Production implementation of {@link TransactionResources} that delegates every
 * call to the corresponding static method on
 * {@link org.springframework.transaction.support.TransactionSynchronizationManager}.
 */
public class DefaultTransactionResources implements TransactionResources {

    @Override
    public Object getResource(Object key) {
        return TransactionSynchronizationManager.getResource(key);
    }

    @Override
    public void bindResource(Object key, Object value) {
        TransactionSynchronizationManager.bindResource(key, value);
    }

    @Override
    public void unbindResource(Object key) {
        TransactionSynchronizationManager.unbindResource(key);
    }

    @Override
    public Object unbindResourceIfPossible(Object key) {
        return TransactionSynchronizationManager.unbindResourceIfPossible(key);
    }

    @Override
    public boolean hasResource(Object key) {
        return TransactionSynchronizationManager.hasResource(key);
    }

    @Override
    public boolean isSynchronizationActive() {
        return TransactionSynchronizationManager.isSynchronizationActive();
    }

    @Override
    public List<TransactionSynchronization> getSynchronizations() {
        return TransactionSynchronizationManager.getSynchronizations();
    }

    @Override
    public void clearSynchronization() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    @Override
    public void initSynchronization() {
        TransactionSynchronizationManager.initSynchronization();
    }

    @Override
    public void registerSynchronization(TransactionSynchronization synchronization) {
        TransactionSynchronizationManager.registerSynchronization(synchronization);
    }

    @Override
    public boolean isActualTransactionActive() {
        return TransactionSynchronizationManager.isActualTransactionActive();
    }

    @Override
    public boolean isCurrentTransactionReadOnly() {
        return TransactionSynchronizationManager.isCurrentTransactionReadOnly();
    }
}

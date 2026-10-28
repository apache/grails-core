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

/**
 * Abstraction over {@link org.springframework.transaction.support.TransactionSynchronizationManager}
 * static methods, allowing tests to supply a controllable implementation without
 * requiring an actual Spring transaction to be active.
 */
public interface TransactionResources {

    Object getResource(Object key);

    void bindResource(Object key, Object value);

    void unbindResource(Object key);

    Object unbindResourceIfPossible(Object key);

    boolean hasResource(Object key);

    boolean isSynchronizationActive();

    List<TransactionSynchronization> getSynchronizations();

    void clearSynchronization();

    void initSynchronization();

    void registerSynchronization(TransactionSynchronization synchronization);

    boolean isActualTransactionActive();

    boolean isCurrentTransactionReadOnly();
}

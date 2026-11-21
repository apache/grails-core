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
package org.grails.orm.hibernate;

import javax.sql.DataSource;

import org.hibernate.SessionFactory;

import org.springframework.orm.hibernate5.SessionFactoryUtils;
import org.springframework.orm.hibernate5.SessionHolder;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Strategy applied when an outer session exists within an active Spring synchronization scope
 * that is not an actual physical transaction (such as {@code PROPAGATION_SUPPORTS} or read-only scopes).
 *
 * <p>Leaves a disconnected outer session disconnected: it takes no connection of its own, so a pool of one
 * connection still serves a transaction in the nested session. Coordinates via {@link TransactionSynchronization}
 * to disconnect the outer session if it connects later, both on suspension (when a nested {@code REQUIRED}
 * transaction begins) and on beforeCompletion (before Spring's {@code ConnectionSynchronization} closes the physical connection),
 * preventing closed-connection failures on subsequent outer session access.</p>
 */
class NonTransactionalSynchronizationOuterSessionStrategy extends AbstractOuterSessionLifecycleStrategy {

    NonTransactionalSynchronizationOuterSessionStrategy(
            SessionFactory sessionFactory,
            DataSource dataSource,
            SessionHolder previousHolder,
            boolean lazyConnection) {
        super(sessionFactory, dataSource, previousHolder, lazyConnection);
    }

    @Override
    public void prepare() {
        super.prepare();
        disconnectBeforeInnerTransactionCompletes();
        if (lazyConnection && !wasPhysicallyConnectedAtPrepare) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public int getOrder() {
                    return SessionFactoryUtils.SESSION_SYNCHRONIZATION_ORDER;
                }

                @Override
                public void suspend() {
                    disconnectIfConnected();
                }

                @Override
                public void beforeCompletion() {
                    disconnectIfConnected();
                }
            });
        }
    }
}

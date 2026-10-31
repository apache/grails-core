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

import org.grails.orm.hibernate.support.hibernate7.SessionHolder;
import org.grails.orm.hibernate.support.hibernate7.TransactionResources;

/**
 * Strategy interface governing outer session lifecycle preparation, suspension, and restoration
 * across nested {@code withNewSession} invocations.
 */
interface OuterSessionLifecycleStrategy {

    /**
     * Prepares the outer session before unbinding and opening the new session.
     */
    void prepare();

    /**
     * Suspends any active Spring synchronizations and unbinds outer session and connection holders.
     */
    void suspendAndUnbind();

    /**
     * Restores previously suspended synchronizations and rebinds outer session and connection holders.
     */
    void restore();

    /**
     * Factory method creating the appropriate strategy based on the outer session, connection, and transaction context.
     *
     * @param previousHolder the session holder bound to the outer scope, or null
     * @param sessionFactory the current Hibernate SessionFactory
     * @param dataSource the underlying DataSource
     * @param txResources the transaction synchronization and resource manager
     * @param lazyConnection whether the datasource is configured with a lazy connection proxy
     * @return the appropriate {@link OuterSessionLifecycleStrategy}
     */
    static OuterSessionLifecycleStrategy from(
            SessionHolder previousHolder,
            SessionFactory sessionFactory,
            DataSource dataSource,
            TransactionResources txResources,
            boolean lazyConnection) {

        boolean hasOpenSession = previousHolder != null && previousHolder.getSession() != null && previousHolder.getSession().isOpen();
        boolean hasConnectionHolder = dataSource != null && txResources.getResource(dataSource) != null;
        boolean hasSynchronization = txResources.isSynchronizationActive();

        if (previousHolder == null && !hasConnectionHolder && !hasSynchronization) {
            return new NoOpOuterSessionStrategy();
        }

        if (txResources.isActualTransactionActive()) {
            return new ActiveTransactionOuterSessionStrategy(
                    sessionFactory, dataSource, txResources, previousHolder, lazyConnection);
        }

        if (hasSynchronization && hasOpenSession) {
            return new NonTransactionalSynchronizationOuterSessionStrategy(
                    sessionFactory, dataSource, txResources, previousHolder, lazyConnection);
        }

        return new DefaultOuterSessionStrategy(
                sessionFactory, dataSource, txResources, previousHolder, lazyConnection);
    }
}

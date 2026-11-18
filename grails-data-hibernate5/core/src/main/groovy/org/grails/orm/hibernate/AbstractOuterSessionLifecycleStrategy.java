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

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import javax.sql.DataSource;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.engine.jdbc.spi.JdbcCoordinator;
import org.hibernate.engine.spi.SessionImplementor;
import org.hibernate.resource.jdbc.spi.LogicalConnectionImplementor;

import org.springframework.jdbc.datasource.ConnectionHolder;
import org.springframework.orm.hibernate5.SessionHolder;
import org.springframework.transaction.support.TransactionSynchronization;

import org.grails.orm.hibernate.support.TransactionResources;

/**
 * Base strategy handling symmetric capture, unbinding, and restoration of session and connection holders
 * as well as Spring transaction synchronizations.
 */
abstract class AbstractOuterSessionLifecycleStrategy implements OuterSessionLifecycleStrategy {

    /**
     * The disconnect of each outer session whose scope is running on this thread, innermost last. A transaction that
     * begins inside the scope registers the innermost one, see {@link #pendingDisconnect()}.
     */
    private static final ThreadLocal<Deque<Runnable>> PENDING_DISCONNECTS = ThreadLocal.withInitial(ArrayDeque::new);

    protected final SessionFactory sessionFactory;
    protected final DataSource dataSource;
    protected final TransactionResources txResources;
    protected final SessionHolder previousHolder;
    protected final boolean lazyConnection;

    private boolean disconnectPending;

    protected boolean wasPhysicallyConnectedAtPrepare = false;
    protected ConnectionHolder previousConnectionHolder;
    protected boolean previousActiveSynchronization;
    protected List<TransactionSynchronization> transactionSynchronizations;

    AbstractOuterSessionLifecycleStrategy(
            SessionFactory sessionFactory,
            DataSource dataSource,
            TransactionResources txResources,
            SessionHolder previousHolder,
            boolean lazyConnection) {
        this.sessionFactory = sessionFactory;
        this.dataSource = dataSource;
        this.txResources = txResources;
        this.previousHolder = previousHolder;
        this.lazyConnection = lazyConnection;
    }

    protected LogicalConnectionImplementor getLogicalConnection() {
        if (previousHolder == null || previousHolder.getSession() == null || !previousHolder.getSession().isOpen()) {
            return null;
        }

        Session previousSession = previousHolder.getSession();
        if (!(previousSession instanceof SessionImplementor sessionImpl)) {
            return null;
        }

        JdbcCoordinator jdbcCoordinator = sessionImpl.getJdbcCoordinator();
        if (jdbcCoordinator == null) {
            return null;
        }

        return jdbcCoordinator.getLogicalConnection();
    }

    /**
     * The disconnect that a transaction beginning in the scope of a {@code withNewSession} call has to run before it
     * completes, or null when no such scope is running. A disconnected outer session that connects inside the scope
     * obtains the connection of that transaction, and the transaction closes it when it completes; disconnecting the
     * session first leaves it without a connection instead of with a closed one.
     *
     * @return the disconnect of the innermost outer session of this thread, or null
     */
    static Runnable pendingDisconnect() {
        return PENDING_DISCONNECTS.get().peekLast();
    }

    /**
     * Makes the disconnect of the outer session available to the transactions that begin before {@link #restore()}.
     * It applies only to an outer session that is disconnected now and a lazy data source, the case in which a
     * connection of the session is fetched from the inner transaction.
     */
    protected void disconnectBeforeInnerTransactionCompletes() {
        if (lazyConnection && !wasPhysicallyConnectedAtPrepare && getLogicalConnection() != null) {
            PENDING_DISCONNECTS.get().addLast(this::disconnectIfConnected);
            disconnectPending = true;
        }
    }

    protected void disconnectIfConnected() {
        LogicalConnectionImplementor logicalConnection = getLogicalConnection();
        if (logicalConnection != null && logicalConnection.isOpen() && logicalConnection.isPhysicallyConnected()) {
            logicalConnection.manualDisconnect();
        }
    }

    @Override
    public void prepare() {
        LogicalConnectionImplementor logicalConnection = getLogicalConnection();
        if (logicalConnection != null) {
            this.wasPhysicallyConnectedAtPrepare = logicalConnection.isPhysicallyConnected();
        }
    }

    @Override
    public void suspendAndUnbind() {
        previousConnectionHolder = dataSource != null ? (ConnectionHolder) txResources.getResource(dataSource) : null;
        previousActiveSynchronization = txResources.isSynchronizationActive();
        transactionSynchronizations = previousActiveSynchronization ? txResources.getSynchronizations() : null;

        if (previousActiveSynchronization) {
            txResources.clearSynchronization();
            txResources.initSynchronization();
        }

        if (previousHolder != null) {
            txResources.unbindResource(sessionFactory);
        }

        if (previousConnectionHolder != null && dataSource != null) {
            txResources.unbindResource(dataSource);
        }
    }

    @Override
    public void restore() {
        if (disconnectPending) {
            Deque<Runnable> pending = PENDING_DISCONNECTS.get();
            pending.removeLast();
            if (pending.isEmpty()) {
                PENDING_DISCONNECTS.remove();
            }
            disconnectPending = false;
        }
        if (previousActiveSynchronization) {
            txResources.initSynchronization();
            if (transactionSynchronizations != null) {
                for (TransactionSynchronization transactionSynchronization : transactionSynchronizations) {
                    txResources.registerSynchronization(transactionSynchronization);
                }
            }
        }

        if (previousHolder != null) {
            txResources.bindResource(sessionFactory, previousHolder);
            if (!wasPhysicallyConnectedAtPrepare && (!lazyConnection || !previousActiveSynchronization)) {
                disconnectIfConnected();
            }
        }

        if (previousConnectionHolder != null && dataSource != null) {
            txResources.bindResource(dataSource, previousConnectionHolder);
        }
    }
}

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

import java.sql.Connection;
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
import org.springframework.jdbc.datasource.ConnectionProxy;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.transaction.support.TransactionSynchronization;

import org.grails.orm.hibernate.support.hibernate7.SessionHolder;
import org.grails.orm.hibernate.support.hibernate7.TransactionResources;

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
    private boolean suspended;

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
     * @return the disconnect of every outer session of this thread, or null. Every session of the stack was
     * disconnected when its scope began and holds no transaction, so a transaction in the innermost scope can
     * close the connection that any of them took.
     */
    static Runnable pendingDisconnect() {
        Deque<Runnable> pending = PENDING_DISCONNECTS.get();
        if (pending.isEmpty()) {
            return null;
        }
        List<Runnable> disconnects = List.copyOf(pending);
        return () -> disconnects.forEach(Runnable::run);
    }

    /**
     * Makes the disconnect of the outer session available to the transactions that begin before {@link #restore()}.
     * It applies only to an outer session that is disconnected now: a connection that the session fetches in the scope
     * is the one of the inner transaction, whether or not the data source is lazy.
     */
    protected void disconnectBeforeInnerTransactionCompletes() {
        if (!wasPhysicallyConnectedAtPrepare && getLogicalConnection() != null) {
            PENDING_DISCONNECTS.get().addLast(this::disconnectIfHoldingTransactionConnection);
            disconnectPending = true;
        }
    }

    /**
     * Disconnects the outer session only when it holds the connection of the transaction that is completing. Every
     * transaction that begins in the scope runs this for every pending outer session, and a session that holds another
     * connection, such as its own transaction's, must keep it: disconnecting it hands that connection back to the pool
     * in the middle of that transaction. The session's connection comes from the transaction-aware data source, and its
     * target is the connection of the holder the transaction bound under the data source, so the check is exact. The
     * holder is looked up first, so the target connection is not fetched when no transaction has one.
     */
    protected void disconnectIfHoldingTransactionConnection() {
        LogicalConnectionImplementor logicalConnection = getLogicalConnection();
        if (dataSource == null || logicalConnection == null || !logicalConnection.isOpen() || !logicalConnection.isPhysicallyConnected()) {
            return;
        }
        if (txResources.getResource(dataSource) == null) {
            return;
        }
        Connection connection = logicalConnection.getPhysicalConnection();
        if (connection instanceof ConnectionProxy proxy) {
            connection = proxy.getTargetConnection();
        }
        if (DataSourceUtils.isConnectionTransactional(connection, dataSource)) {
            logicalConnection.manualDisconnect();
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
        suspended = true;
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
        if (!suspended) {
            // prepare() failed before the outer session was unbound: nothing was taken, so nothing is put back
            return;
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
            if (!wasPhysicallyConnectedAtPrepare) {
                disconnectIfConnected();
            }
        }

        if (previousConnectionHolder != null && dataSource != null) {
            txResources.bindResource(dataSource, previousConnectionHolder);
        }
    }
}

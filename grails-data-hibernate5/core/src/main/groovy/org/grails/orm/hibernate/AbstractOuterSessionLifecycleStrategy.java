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
import java.sql.SQLException;
import java.util.List;

import javax.sql.DataSource;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.engine.jdbc.spi.JdbcCoordinator;
import org.hibernate.engine.spi.SessionImplementor;
import org.hibernate.resource.jdbc.spi.LogicalConnectionImplementor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.jdbc.datasource.ConnectionHolder;
import org.springframework.orm.hibernate5.SessionHolder;
import org.springframework.transaction.support.TransactionSynchronization;

import org.grails.orm.hibernate.support.TransactionResources;

/**
 * Base strategy handling symmetric capture, unbinding, and restoration of session and connection holders
 * as well as Spring transaction synchronizations.
 */
abstract class AbstractOuterSessionLifecycleStrategy implements OuterSessionLifecycleStrategy {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractOuterSessionLifecycleStrategy.class);

    protected final SessionFactory sessionFactory;
    protected final DataSource dataSource;
    protected final TransactionResources txResources;
    protected final SessionHolder previousHolder;
    protected final boolean lazyConnection;

    protected boolean wasPhysicallyConnectedAtPrepare = false;
    protected ConnectionHolder previousConnectionHolder;
    protected boolean previousActiveSynchronization;
    protected List<TransactionSynchronization> transactionSynchronizations;

    AbstractOuterSessionLifecycleStrategy(
            SessionFactory sessionFactory,
            DataSource dataSource,
            TransactionResources txResources,
            SessionHolder previousHolder) {
        this(sessionFactory, dataSource, txResources, previousHolder, false);
    }

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

    protected void disconnectIfConnected() {
        LogicalConnectionImplementor logicalConnection = getLogicalConnection();
        if (logicalConnection != null && logicalConnection.isOpen() && logicalConnection.isPhysicallyConnected()) {
            logicalConnection.manualDisconnect();
        }
    }

    protected void primeIfDisconnected() {
        LogicalConnectionImplementor logicalConnection = getLogicalConnection();
        if (logicalConnection == null || logicalConnection.isPhysicallyConnected()) {
            return;
        }

        Connection con = logicalConnection.getPhysicalConnection();
        if (con != null) {
            try {
                con.getAutoCommit();
            } catch (SQLException e) {
                LOG.warn("Could not initialize physical connection: {}", e.getMessage(), e);
            }
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
        previousConnectionHolder = (ConnectionHolder) txResources.getResource(dataSource);
        previousActiveSynchronization = txResources.isSynchronizationActive();
        transactionSynchronizations = previousActiveSynchronization ? txResources.getSynchronizations() : null;

        if (previousActiveSynchronization) {
            txResources.clearSynchronization();
            txResources.initSynchronization();
        }

        if (previousHolder != null) {
            txResources.unbindResource(sessionFactory);
        }

        if (previousConnectionHolder != null) {
            txResources.unbindResource(dataSource);
        }
    }

    @Override
    public void restore() {
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

        if (previousConnectionHolder != null) {
            txResources.bindResource(dataSource, previousConnectionHolder);
        }
    }
}

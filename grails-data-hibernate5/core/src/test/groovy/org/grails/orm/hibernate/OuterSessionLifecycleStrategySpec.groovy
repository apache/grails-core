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
package org.grails.orm.hibernate

import java.sql.Connection
import java.sql.SQLException
import javax.sql.DataSource

import org.hibernate.Session
import org.hibernate.SessionFactory
import org.hibernate.engine.jdbc.spi.JdbcCoordinator
import org.hibernate.engine.spi.SessionImplementor
import org.hibernate.resource.jdbc.spi.LogicalConnectionImplementor
import org.springframework.jdbc.datasource.ConnectionHolder
import org.springframework.orm.hibernate5.SessionFactoryUtils
import org.springframework.orm.hibernate5.SessionHolder
import org.springframework.transaction.support.TransactionSynchronization
import spock.lang.Specification

import org.grails.orm.hibernate.support.TransactionResources

class OuterSessionLifecycleStrategySpec extends Specification {

    SessionFactory sessionFactory = Mock(SessionFactory)
    DataSource dataSource = Mock(DataSource)
    TransactionResources txResources = Mock(TransactionResources)
    Session session = Mock(Session)
    SessionHolder sessionHolder = new SessionHolder(session)

    // -------------------------------------------------------------------------
    // Strategy selection via factory
    // -------------------------------------------------------------------------

    void "from returns NoOpOuterSessionStrategy when previousHolder is null"() {
        when:
        OuterSessionLifecycleStrategy strategy = OuterSessionLifecycleStrategy.from(
                null, sessionFactory, dataSource, txResources)

        then:
        strategy instanceof NoOpOuterSessionStrategy
    }

    void "from returns DefaultOuterSessionStrategy when session is null with bound holder"() {
        given:
        SessionHolder emptyHolder = Mock(SessionHolder)
        emptyHolder.getSession() >> null

        when:
        OuterSessionLifecycleStrategy strategy = OuterSessionLifecycleStrategy.from(
                emptyHolder, sessionFactory, dataSource, txResources)

        then:
        strategy instanceof DefaultOuterSessionStrategy
    }

    void "from returns DefaultOuterSessionStrategy when session is closed with bound holder"() {
        given:
        session.isOpen() >> false

        when:
        OuterSessionLifecycleStrategy strategy = OuterSessionLifecycleStrategy.from(
                sessionHolder, sessionFactory, dataSource, txResources)

        then:
        strategy instanceof DefaultOuterSessionStrategy
    }

    void "from returns ActiveTransactionOuterSessionStrategy when previousHolder is null but transaction is active and DataSource holder is bound"() {
        given:
        ConnectionHolder connectionHolder = new ConnectionHolder(Mock(Connection))
        txResources.getResource(dataSource) >> connectionHolder
        txResources.isActualTransactionActive() >> true

        when:
        OuterSessionLifecycleStrategy strategy = OuterSessionLifecycleStrategy.from(
                null, sessionFactory, dataSource, txResources)

        then:
        strategy instanceof ActiveTransactionOuterSessionStrategy
    }

    void "from returns DefaultOuterSessionStrategy when previousHolder is null but DataSource holder is bound without synchronization"() {
        given:
        ConnectionHolder connectionHolder = new ConnectionHolder(Mock(Connection))
        txResources.getResource(dataSource) >> connectionHolder
        txResources.isActualTransactionActive() >> false
        txResources.isSynchronizationActive() >> false

        when:
        OuterSessionLifecycleStrategy strategy = OuterSessionLifecycleStrategy.from(
                null, sessionFactory, dataSource, txResources)

        then:
        strategy instanceof DefaultOuterSessionStrategy
    }

    void "from returns ActiveTransactionOuterSessionStrategy when actual transaction is active"() {
        given:
        session.isOpen() >> true
        txResources.isActualTransactionActive() >> true

        when:
        OuterSessionLifecycleStrategy strategy = OuterSessionLifecycleStrategy.from(
                sessionHolder, sessionFactory, dataSource, txResources)

        then:
        strategy instanceof ActiveTransactionOuterSessionStrategy
    }

    void "from returns NonTransactionalSynchronizationOuterSessionStrategy when synchronization is active but not actual transaction"() {
        given:
        session.isOpen() >> true
        txResources.isActualTransactionActive() >> false
        txResources.isSynchronizationActive() >> true

        when:
        OuterSessionLifecycleStrategy strategy = OuterSessionLifecycleStrategy.from(
                sessionHolder, sessionFactory, dataSource, txResources)

        then:
        strategy instanceof NonTransactionalSynchronizationOuterSessionStrategy
    }

    void "from returns DefaultOuterSessionStrategy when no synchronization is active"() {
        given:
        session.isOpen() >> true
        txResources.isActualTransactionActive() >> false
        txResources.isSynchronizationActive() >> false

        when:
        OuterSessionLifecycleStrategy strategy = OuterSessionLifecycleStrategy.from(
                sessionHolder, sessionFactory, dataSource, txResources)

        then:
        strategy instanceof DefaultOuterSessionStrategy
    }

    // -------------------------------------------------------------------------
    // NoOpOuterSessionStrategy
    // -------------------------------------------------------------------------

    void "NoOpOuterSessionStrategy performs no operations"() {
        given:
        NoOpOuterSessionStrategy strategy = new NoOpOuterSessionStrategy()

        when:
        strategy.prepare()
        strategy.suspendAndUnbind()
        strategy.restore()

        then:
        0 * txResources._
        0 * session._
    }

    // -------------------------------------------------------------------------
    // ActiveTransactionOuterSessionStrategy
    // -------------------------------------------------------------------------

    void "ActiveTransactionOuterSessionStrategy does not prime or disconnect connection during prepare"() {
        given:
        SessionImplementor sessionImpl = Mock(SessionImplementor)
        JdbcCoordinator coordinator = Mock(JdbcCoordinator)
        LogicalConnectionImplementor logicalConnection = Mock(LogicalConnectionImplementor)
        sessionImpl.getJdbcCoordinator() >> coordinator
        coordinator.getLogicalConnection() >> logicalConnection
        SessionHolder holder = new SessionHolder(sessionImpl)

        ActiveTransactionOuterSessionStrategy strategy = new ActiveTransactionOuterSessionStrategy(
                sessionFactory, dataSource, txResources, holder)

        when:
        strategy.prepare()

        then: "neither physical connection acquisition nor manualDisconnect is called"
        0 * logicalConnection.getPhysicalConnection()
        0 * logicalConnection.manualDisconnect()
        0 * txResources.registerSynchronization(_)
    }

    void "ActiveTransactionOuterSessionStrategy suspends and restores synchronizations and holders symmetrically"() {
        given:
        ConnectionHolder connectionHolder = new ConnectionHolder(Mock(Connection))
        TransactionSynchronization existingSync = Mock(TransactionSynchronization)
        List<TransactionSynchronization> syncList = [existingSync]

        txResources.getResource(dataSource) >> connectionHolder
        txResources.isSynchronizationActive() >> true
        txResources.getSynchronizations() >> syncList

        ActiveTransactionOuterSessionStrategy strategy = new ActiveTransactionOuterSessionStrategy(
                sessionFactory, dataSource, txResources, sessionHolder)

        when: "suspending and unbinding"
        strategy.suspendAndUnbind()

        then: "synchronizations are cleared and inited, and holders are unbound"
        1 * txResources.clearSynchronization()
        1 * txResources.initSynchronization()
        1 * txResources.unbindResource(sessionFactory)
        1 * txResources.unbindResource(dataSource)

        when: "restoring"
        strategy.restore()

        then: "synchronizations are restored and holders are rebound"
        1 * txResources.initSynchronization()
        1 * txResources.registerSynchronization(existingSync)
        1 * txResources.bindResource(sessionFactory, sessionHolder)
        1 * txResources.bindResource(dataSource, connectionHolder)
    }

    // -------------------------------------------------------------------------
    // NonTransactionalSynchronizationOuterSessionStrategy
    // -------------------------------------------------------------------------

    void "NonTransactionalSynchronizationOuterSessionStrategy primes disconnected session and registers synchronization when lazyConnection is true"() {
        given:
        SessionImplementor sessionImpl = Mock(SessionImplementor)
        JdbcCoordinator coordinator = Mock(JdbcCoordinator)
        LogicalConnectionImplementor logicalConnection = Mock(LogicalConnectionImplementor)
        Connection physicalCon = Mock(Connection)

        sessionImpl.isOpen() >> true
        sessionImpl.getJdbcCoordinator() >> coordinator
        coordinator.getLogicalConnection() >> logicalConnection
        logicalConnection.isPhysicallyConnected() >> false
        logicalConnection.getPhysicalConnection() >> physicalCon

        SessionHolder holder = new SessionHolder(sessionImpl)
        NonTransactionalSynchronizationOuterSessionStrategy strategy =
                new NonTransactionalSynchronizationOuterSessionStrategy(
                        sessionFactory, dataSource, txResources, holder, true)

        TransactionSynchronization registeredSync = null

        when:
        strategy.prepare()

        then: "connection autoCommit probe is executed"
        1 * physicalCon.getAutoCommit()

        and: "synchronization is registered at SESSION_SYNCHRONIZATION_ORDER"
        1 * txResources.registerSynchronization(_ as TransactionSynchronization) >> { TransactionSynchronization sync ->
            registeredSync = sync
        }
        registeredSync != null
        registeredSync.order == SessionFactoryUtils.SESSION_SYNCHRONIZATION_ORDER
    }

    void "NonTransactionalSynchronizationOuterSessionStrategy skips priming and synchronization when lazyConnection is false"() {
        given:
        SessionImplementor sessionImpl = Mock(SessionImplementor)
        JdbcCoordinator coordinator = Mock(JdbcCoordinator)
        LogicalConnectionImplementor logicalConnection = Mock(LogicalConnectionImplementor)

        sessionImpl.isOpen() >> true
        sessionImpl.getJdbcCoordinator() >> coordinator
        coordinator.getLogicalConnection() >> logicalConnection
        logicalConnection.isPhysicallyConnected() >> false

        SessionHolder holder = new SessionHolder(sessionImpl)
        NonTransactionalSynchronizationOuterSessionStrategy strategy =
                new NonTransactionalSynchronizationOuterSessionStrategy(
                        sessionFactory, dataSource, txResources, holder, false)

        when:
        strategy.prepare()

        then:
        0 * logicalConnection.getPhysicalConnection()
        0 * txResources.registerSynchronization(_)
    }

    void "NonTransactionalSynchronizationOuterSessionStrategy disconnects on restore when lazyConnection is false and connection became physically connected"() {
        given:
        SessionImplementor sessionImpl = Mock(SessionImplementor)
        JdbcCoordinator coordinator = Mock(JdbcCoordinator)
        LogicalConnectionImplementor logicalConnection = Mock(LogicalConnectionImplementor)

        sessionImpl.isOpen() >> true
        sessionImpl.getJdbcCoordinator() >> coordinator
        coordinator.getLogicalConnection() >> logicalConnection
        logicalConnection.isPhysicallyConnected() >>> [false, true]
        logicalConnection.isOpen() >> true

        SessionHolder holder = new SessionHolder(sessionImpl)
        NonTransactionalSynchronizationOuterSessionStrategy strategy =
                new NonTransactionalSynchronizationOuterSessionStrategy(
                        sessionFactory, dataSource, txResources, holder, false)

        strategy.prepare()

        when: "restoring after session became physically connected during scope"
        strategy.restore()

        then: "connection is manually disconnected to drop closed connection"
        1 * logicalConnection.manualDisconnect()
    }

    void "NonTransactionalSynchronizationOuterSessionStrategy synchronization disconnects on suspend"() {
        given:
        SessionImplementor sessionImpl = Mock(SessionImplementor)
        JdbcCoordinator coordinator = Mock(JdbcCoordinator)
        LogicalConnectionImplementor logicalConnection = Mock(LogicalConnectionImplementor)
        Connection physicalCon = Mock(Connection)

        sessionImpl.isOpen() >> true
        sessionImpl.getJdbcCoordinator() >> coordinator
        coordinator.getLogicalConnection() >> logicalConnection
        logicalConnection.isOpen() >> true
        logicalConnection.isPhysicallyConnected() >>> [false, true]
        logicalConnection.getPhysicalConnection() >> physicalCon

        SessionHolder holder = new SessionHolder(sessionImpl)
        NonTransactionalSynchronizationOuterSessionStrategy strategy =
                new NonTransactionalSynchronizationOuterSessionStrategy(
                        sessionFactory, dataSource, txResources, holder, true)

        TransactionSynchronization registeredSync = null
        txResources.registerSynchronization(_ as TransactionSynchronization) >> { TransactionSynchronization sync ->
            registeredSync = sync
        }

        strategy.prepare()
        assert registeredSync != null

        when: "a nested REQUIRED transaction starts, suspending outer synchronizations"
        registeredSync.suspend()

        then: "the primed logical connection is immediately disconnected"
        1 * logicalConnection.manualDisconnect()
    }

    void "NonTransactionalSynchronizationOuterSessionStrategy synchronization disconnects on beforeCompletion"() {
        given:
        SessionImplementor sessionImpl = Mock(SessionImplementor)
        JdbcCoordinator coordinator = Mock(JdbcCoordinator)
        LogicalConnectionImplementor logicalConnection = Mock(LogicalConnectionImplementor)
        Connection physicalCon = Mock(Connection)

        sessionImpl.isOpen() >> true
        sessionImpl.getJdbcCoordinator() >> coordinator
        coordinator.getLogicalConnection() >> logicalConnection
        logicalConnection.isOpen() >> true
        logicalConnection.isPhysicallyConnected() >>> [false, true]
        logicalConnection.getPhysicalConnection() >> physicalCon

        SessionHolder holder = new SessionHolder(sessionImpl)
        NonTransactionalSynchronizationOuterSessionStrategy strategy =
                new NonTransactionalSynchronizationOuterSessionStrategy(
                        sessionFactory, dataSource, txResources, holder, true)

        TransactionSynchronization registeredSync = null
        txResources.registerSynchronization(_ as TransactionSynchronization) >> { TransactionSynchronization sync ->
            registeredSync = sync
        }

        strategy.prepare()
        assert registeredSync != null

        when: "the scope finishes without suspension"
        registeredSync.beforeCompletion()

        then: "the logical connection is disconnected before Spring closes the physical connection"
        1 * logicalConnection.manualDisconnect()
    }

    void "NonTransactionalSynchronizationOuterSessionStrategy skips priming when logical connection is already physically connected"() {
        given:
        SessionImplementor sessionImpl = Mock(SessionImplementor)
        JdbcCoordinator coordinator = Mock(JdbcCoordinator)
        LogicalConnectionImplementor logicalConnection = Mock(LogicalConnectionImplementor)

        sessionImpl.isOpen() >> true
        sessionImpl.getJdbcCoordinator() >> coordinator
        coordinator.getLogicalConnection() >> logicalConnection
        logicalConnection.isPhysicallyConnected() >> true

        SessionHolder holder = new SessionHolder(sessionImpl)
        NonTransactionalSynchronizationOuterSessionStrategy strategy =
                new NonTransactionalSynchronizationOuterSessionStrategy(
                        sessionFactory, dataSource, txResources, holder, true)

        when:
        strategy.prepare()

        then:
        0 * logicalConnection.getPhysicalConnection()
        0 * txResources.registerSynchronization(_)
    }

    // -------------------------------------------------------------------------
    // DefaultOuterSessionStrategy
    // -------------------------------------------------------------------------

    void "DefaultOuterSessionStrategy unbinds and restores holders without synchronization"() {
        given:
        ConnectionHolder connectionHolder = new ConnectionHolder(Mock(Connection))
        txResources.getResource(dataSource) >> connectionHolder
        txResources.isSynchronizationActive() >> false

        DefaultOuterSessionStrategy strategy = new DefaultOuterSessionStrategy(
                sessionFactory, dataSource, txResources, sessionHolder)

        when:
        strategy.prepare()
        strategy.suspendAndUnbind()

        then:
        1 * txResources.unbindResource(sessionFactory)
        1 * txResources.unbindResource(dataSource)
        0 * txResources.clearSynchronization()

        when:
        strategy.restore()

        then:
        1 * txResources.bindResource(sessionFactory, sessionHolder)
        1 * txResources.bindResource(dataSource, connectionHolder)
        0 * txResources.initSynchronization()
    }

    void "DefaultOuterSessionStrategy does not prime disconnected session during prepare"() {
        given:
        SessionImplementor sessionImpl = Mock(SessionImplementor)
        JdbcCoordinator coordinator = Mock(JdbcCoordinator)
        LogicalConnectionImplementor logicalConnection = Mock(LogicalConnectionImplementor)

        sessionImpl.isOpen() >> true
        sessionImpl.getJdbcCoordinator() >> coordinator
        coordinator.getLogicalConnection() >> logicalConnection
        logicalConnection.isPhysicallyConnected() >> false

        SessionHolder holder = new SessionHolder(sessionImpl)
        DefaultOuterSessionStrategy strategy = new DefaultOuterSessionStrategy(
                sessionFactory, dataSource, txResources, holder)

        when:
        strategy.prepare()

        then: "no physical connection checkout occurs"
        0 * logicalConnection.getPhysicalConnection()
        0 * txResources.registerSynchronization(_)
    }

    void "DefaultOuterSessionStrategy disconnects on restore when session became physically connected during scope"() {
        given:
        SessionImplementor sessionImpl = Mock(SessionImplementor)
        JdbcCoordinator coordinator = Mock(JdbcCoordinator)
        LogicalConnectionImplementor logicalConnection = Mock(LogicalConnectionImplementor)

        sessionImpl.isOpen() >> true
        sessionImpl.getJdbcCoordinator() >> coordinator
        coordinator.getLogicalConnection() >> logicalConnection
        logicalConnection.isPhysicallyConnected() >>> [false, true]
        logicalConnection.isOpen() >> true

        SessionHolder holder = new SessionHolder(sessionImpl)
        DefaultOuterSessionStrategy strategy = new DefaultOuterSessionStrategy(
                sessionFactory, dataSource, txResources, holder)

        strategy.prepare()

        when: "restoring after session became physically connected during scope"
        strategy.restore()

        then: "connection is returned to pool"
        1 * logicalConnection.manualDisconnect()
    }
}

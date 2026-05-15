/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  'License'); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  'AS IS' BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.grails.orm.hibernate.support.hibernate7

import org.hibernate.FlushMode
import org.hibernate.Session
import org.hibernate.SessionFactory
import org.hibernate.Transaction
import org.hibernate.engine.spi.SessionImplementor
import org.hibernate.engine.jdbc.spi.JdbcCoordinator
import org.hibernate.resource.jdbc.spi.LogicalConnectionImplementor
import org.hibernate.resource.jdbc.spi.PhysicalConnectionHandlingMode
import org.hibernate.ConnectionReleaseMode
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.DefaultTransactionDefinition
import org.springframework.transaction.support.TransactionSynchronizationManager
import spock.lang.Specification
import java.sql.Connection

/**
 * Unit tests for HibernateTransactionManager.
 */
class HibernateTransactionManagerSpec extends Specification {

    SessionFactory sessionFactory = Mock(SessionFactory)
    SessionImplementor session = Mock(SessionImplementor)
    Transaction transaction = Mock(Transaction)
    JdbcCoordinator jdbcCoordinator = Mock(JdbcCoordinator)
    LogicalConnectionImplementor logicalConnection = Mock(LogicalConnectionImplementor)
    Connection connection = Mock(Connection)
    HibernateTransactionManager transactionManager

    def setup() {
        transactionManager = new HibernateTransactionManager(sessionFactory)
        session.unwrap(SessionImplementor) >> session
        session.getJdbcCoordinator() >> jdbcCoordinator
        jdbcCoordinator.getLogicalConnection() >> logicalConnection
        logicalConnection.getConnectionHandlingMode() >> PhysicalConnectionHandlingMode.DELAYED_ACQUISITION_AND_HOLD
        logicalConnection.getPhysicalConnection() >> connection
        session.getTransaction() >> transaction
    }

    def cleanup() {
        TransactionSynchronizationManager.unbindResourceIfPossible(sessionFactory)
        TransactionSynchronizationManager.clear()
    }

    def 'test begin new transaction'() {
        given:
        TransactionDefinition definition = new DefaultTransactionDefinition()

        when:
        def txStatus = transactionManager.getTransaction(definition)

        then:
        1 * sessionFactory.openSession() >> session
        1 * session.beginTransaction() >> transaction
        txStatus != null
        txStatus.newTransaction
        TransactionSynchronizationManager.hasResource(sessionFactory)
        def holder = (SessionHolder) TransactionSynchronizationManager.getResource(sessionFactory)
        holder.session == session
        holder.transaction == transaction
    }

    def 'test commit new transaction'() {
        given:
        TransactionDefinition definition = new DefaultTransactionDefinition()
        1 * sessionFactory.openSession() >> session
        1 * session.beginTransaction() >> transaction
        def txStatus = transactionManager.getTransaction(definition)

        when:
        transactionManager.commit(txStatus)

        then:
        1 * transaction.commit()
        1 * session.isOpen() >> true
        1 * session.close()
        !TransactionSynchronizationManager.hasResource(sessionFactory)
    }

    def 'test rollback new transaction'() {
        given:
        TransactionDefinition definition = new DefaultTransactionDefinition()
        1 * sessionFactory.openSession() >> session
        1 * session.beginTransaction() >> transaction
        def txStatus = transactionManager.getTransaction(definition)

        when:
        transactionManager.rollback(txStatus)

        then:
        1 * transaction.rollback()
        1 * session.isOpen() >> true
        1 * session.close()
        !TransactionSynchronizationManager.hasResource(sessionFactory)
    }

    def 'test read-only transaction sets flush mode to manual'() {
        given:
        DefaultTransactionDefinition definition = new DefaultTransactionDefinition()
        definition.setReadOnly(true)

        when:
        def txStatus = transactionManager.getTransaction(definition)

        then:
        1 * sessionFactory.openSession() >> session
        1 * session.setHibernateFlushMode(FlushMode.MANUAL)
        1 * session.setDefaultReadOnly(true)
        1 * session.beginTransaction() >> transaction
        txStatus.readOnly
    }

    def 'test participating in existing transaction'() {
        given:
        TransactionDefinition definition = new DefaultTransactionDefinition()
        
        // Setup first transaction
        1 * sessionFactory.openSession() >> session
        1 * session.beginTransaction() >> transaction
        def status1 = transactionManager.getTransaction(definition)

        when: 'Beginning a second transaction'
        def status2 = transactionManager.getTransaction(definition)

        then: 'It should participate in the existing one'
        !status2.newTransaction
        status2.transaction != null
        0 * sessionFactory.openSession()
        0 * session.beginTransaction()
        
        // participating transactions might check flush mode
        _ * session.getHibernateFlushMode() >> FlushMode.AUTO
    }

    def 'test suspend and resume transaction via REQUIRES_NEW'() {
        given:
        TransactionDefinition def1 = new DefaultTransactionDefinition()
        DefaultTransactionDefinition def2 = new DefaultTransactionDefinition()
        def2.propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW

        // Setup first transaction
        1 * sessionFactory.openSession() >> session
        1 * session.beginTransaction() >> transaction
        def status1 = transactionManager.getTransaction(def1)
        
        // Prepare second session for REQUIRES_NEW
        SessionImplementor session2 = Mock(SessionImplementor)
        Transaction transaction2 = Mock(Transaction)
        session2.unwrap(SessionImplementor) >> session2
        session2.getJdbcCoordinator() >> jdbcCoordinator
        session2.getTransaction() >> transaction2

        when: 'Beginning a REQUIRES_NEW transaction'
        def status2 = transactionManager.getTransaction(def2)

        then: 'The first one should be suspended and second one started'
        1 * sessionFactory.openSession() >> session2
        1 * session2.beginTransaction() >> transaction2
        status2.newTransaction
        def holder2 = (SessionHolder) TransactionSynchronizationManager.getResource(sessionFactory)
        holder2.session == session2

        when: 'Committing the second transaction'
        transactionManager.commit(status2)

        then: 'The second session should be closed and the first one resumed'
        1 * transaction2.commit()
        1 * session2.isOpen() >> true
        1 * session2.close()
        def resumedHolder = (SessionHolder) TransactionSynchronizationManager.getResource(sessionFactory)
        resumedHolder.session == session
    }
}

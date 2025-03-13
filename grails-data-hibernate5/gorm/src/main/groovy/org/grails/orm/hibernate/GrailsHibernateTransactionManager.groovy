/*
 * Copyright 2004-2010 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.orm.hibernate

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.hibernate.FlushMode
import org.hibernate.Session
import org.hibernate.SessionFactory
import org.hibernate.engine.jdbc.spi.JdbcCoordinator
import org.hibernate.engine.spi.SessionImplementor
import org.springframework.orm.hibernate5.HibernateTransactionManager
import org.springframework.orm.hibernate5.SessionHolder
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.DefaultTransactionStatus
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.util.Assert

import javax.sql.DataSource

/**
 * Extends the standard class to always set the flush mode to manual when in a read-only transaction.
 *
 * @author Burt Beckwith
 */
@CompileStatic
@Slf4j
class GrailsHibernateTransactionManager extends HibernateTransactionManager {

    final FlushMode defaultFlushMode
    boolean isJdbcBatchVersionedData

    GrailsHibernateTransactionManager(FlushMode defaultFlushMode = FlushMode.AUTO) {
        this.defaultFlushMode = defaultFlushMode
    }

    GrailsHibernateTransactionManager(SessionFactory sessionFactory, FlushMode defaultFlushMode = FlushMode.AUTO) {
        super(sessionFactory)
        this.defaultFlushMode = defaultFlushMode
        this.isJdbcBatchVersionedData = sessionFactory.getSessionFactoryOptions().isJdbcBatchVersionedData()
    }

    GrailsHibernateTransactionManager(SessionFactory sessionFactory, DataSource dataSource, FlushMode defaultFlushMode = FlushMode.AUTO) {
        super(sessionFactory)
        setDataSource(dataSource)
        this.defaultFlushMode = defaultFlushMode
        this.isJdbcBatchVersionedData = sessionFactory.getSessionFactoryOptions().isJdbcBatchVersionedData()
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
        super.doBegin transaction, definition

        if (definition.isReadOnly()) {
            // transaction is HibernateTransactionManager.HibernateTransactionObject private class instance
            // always set to manual; the base class doesn't because the OSIV has already registered a session

            SessionHolder holder = (SessionHolder)TransactionSynchronizationManager.getResource(sessionFactory)
            holder.session.setHibernateFlushMode(FlushMode.MANUAL)
        }
        else if(defaultFlushMode != FlushMode.AUTO) {
            SessionHolder holder = (SessionHolder)TransactionSynchronizationManager.getResource(sessionFactory)
            holder.session.setHibernateFlushMode(defaultFlushMode)
        }
    }

    @Override
    protected void doRollback(DefaultTransactionStatus status) {
        super.doRollback(status)
        if(isJdbcBatchVersionedData) {
            try {
                SessionHolder holder = (SessionHolder)TransactionSynchronizationManager.getResource(sessionFactory)
                if(holder != null) {
                    Session session = holder.getSession()
                    JdbcCoordinator jdbcCoordinator = ((SessionImplementor) session).getJdbcCoordinator()
                    jdbcCoordinator.abortBatch()
                }
            } catch (Throwable e) {
                log.warn("Error aborting batch during Transaction rollback: ${e.message}", e)
            }
        }
    }

    @Override
    void setSessionFactory(SessionFactory sessionFactory) {
        Assert.notNull(sessionFactory, "SessionFactory cannot be null")
        super.setSessionFactory(sessionFactory)
        this.isJdbcBatchVersionedData = sessionFactory.getSessionFactoryOptions().isJdbcBatchVersionedData()
    }
}

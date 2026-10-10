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

package org.grails.plugin.hibernate.support

import java.sql.Connection
import java.util.concurrent.ConcurrentLinkedDeque

import groovy.transform.CompileStatic
import org.hibernate.FlushMode
import org.hibernate.Session
import org.hibernate.SessionFactory
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import org.springframework.transaction.support.TransactionSynchronizationManager

import grails.persistence.support.PersistenceContextInterceptor
import grails.validation.DeferredBindingActions
import org.grails.core.lifecycle.ShutdownOperations
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.orm.hibernate.HibernateDatastore
import org.grails.orm.hibernate.support.HibernateRuntimeUtils
import org.grails.orm.hibernate.support.hibernate7.SessionFactoryUtils
import org.grails.orm.hibernate.support.hibernate7.SessionHolder

/**
 * @author Graeme Rocher
 * @since 0.4
 */
@CompileStatic
class HibernatePersistenceContextInterceptor
        implements PersistenceContextInterceptor, SessionFactoryAwarePersistenceContextInterceptor {

    private static final Logger LOG = LoggerFactory.getLogger(HibernatePersistenceContextInterceptor)
    private HibernateDatastore hibernateDatastore

    private static ThreadLocal<Map<String, Boolean>> participate = ThreadLocal.withInitial({ -> (Map<String, Boolean>) new HashMap<String, Boolean>() })

    private static ThreadLocal<Map<String, Integer>> nestingCount = ThreadLocal.withInitial({ -> (Map<String, Integer>) new HashMap<String, Integer>() })

    private String dataSourceName

    static {
        ShutdownOperations.addOperation({ ->
            participate.remove()
            nestingCount.remove()
        })
    }

    private Deque<Connection> disconnected = new ConcurrentLinkedDeque<>()
    private final boolean transactionRequired

    HibernatePersistenceContextInterceptor() {
        this(ConnectionSource.DEFAULT)
    }

    /**
     * @param dataSourceName a name of dataSource
     */
    HibernatePersistenceContextInterceptor(String dataSourceName) {
        this.dataSourceName = dataSourceName
        this.transactionRequired = true
    }

    /* (non-Javadoc)
     * @see org.apache.groovy.grails.support.PersistenceContextInterceptor#destroy()
     */
    void destroy() {
        DeferredBindingActions.clear()
        if (!disconnected.isEmpty()) {
            disconnected.pop()
        }
        if (getSessionFactory() == null || decNestingCount() > 0 || getParticipate()) {
            return
        }

        // single session mode
        SessionHolder holder = (SessionHolder) TransactionSynchronizationManager.unbindResource(getSessionFactory())
        LOG.debug('Closing single Hibernate session in GrailsDispatcherServlet')
        try {
            disconnected.clear()
            SessionFactoryUtils.closeSession(holder.session)
        } catch (RuntimeException ex) {
            LOG.error('Unexpected exception on closing Hibernate Session', ex)
        }
    }

    void disconnect() {
        throw new UnsupportedOperationException('disconnect is not supported by Hibernate 7')
    }

    void reconnect() {
        throw new UnsupportedOperationException('reconnect is not supported by Hibernate 7')
    }

    void flush() {
        if (getSessionFactory() == null) {
            return
        }
        if (!getParticipate()) {
            Session session = getSession()
            if (!transactionRequired) {
                session.flush()
            } else if (TransactionSynchronizationManager.isSynchronizationActive()) {
                session.flush()
            } else {
                // No active Spring transaction synchronization. This happens when the interceptor
                // opened the session itself - for example around a non-transactional BootStrap.
                // Flush and commit the owned session's pending changes in a short transaction so
                // they are persisted rather than discarded when the session is closed in destroy().
                org.hibernate.Transaction transaction = session.transaction
                boolean ownTransaction = transaction == null || !transaction.isActive()
                if (ownTransaction) {
                    transaction = session.beginTransaction()
                }
                try {
                    session.flush()
                    if (ownTransaction) {
                        transaction.commit()
                    }
                } catch (RuntimeException ex) {
                    if (ownTransaction && transaction.isActive()) {
                        transaction.rollback()
                    }
                    throw ex
                }
            }
        }
    }

    void clear() {
        if (getSessionFactory() == null) {
            return
        }
        getSession().clear()
    }

    void setReadOnly() {
        if (getSessionFactory() == null) {
            return
        }
        getSession().setHibernateFlushMode(FlushMode.MANUAL)
    }

    void setReadWrite() {
        if (getSessionFactory() == null) {
            return
        }
        getSession().setHibernateFlushMode(FlushMode.AUTO)
    }

    boolean isOpen() {
        if (getSessionFactory() == null) {
            return false
        }
        try {
            return getSession(false).isOpen()
        } catch (Exception e) {
            return false
        }
    }

    /* (non-Javadoc)
     * @see org.apache.groovy.grails.support.PersistenceContextInterceptor#init()
     */
    void init() {
        if (incNestingCount() > 1) {
            return
        }
        SessionFactory sf = getSessionFactory()
        if (sf == null) {
            return
        }
        if (TransactionSynchronizationManager.hasResource(sf)) {
            // Do not modify the Session: just set the participate flag.
            setParticipate(true)
        } else {
            setParticipate(false)
            LOG.debug('Opening single Hibernate session in HibernatePersistenceContextInterceptor')
            Session session = getSession()
            HibernateRuntimeUtils.enableDynamicFilterEnablerIfPresent(sf, session)
            TransactionSynchronizationManager.bindResource(sf, new SessionHolder(session))
        }
    }

    private Session getSession() {
        return getSession(true)
    }

    private Session getSession(boolean allowCreate) {

        Object value = TransactionSynchronizationManager.getResource(getSessionFactory())
        if (value instanceof Session) {
            return (Session) value
        }

        if (value instanceof SessionHolder) {
            SessionHolder sessionHolder = (SessionHolder) value
            return sessionHolder.session
        }

        if (allowCreate && hibernateDatastore != null) {
            return hibernateDatastore.openSession()
        }

        throw new IllegalStateException(
                'No Hibernate Session bound to thread, and configuration does not allow creation of non-transactional one here')
    }

    /**
     * @return the sessionFactory
     */
    SessionFactory getSessionFactory() {
        return hibernateDatastore.sessionFactory
    }

    void setHibernateDatastore(HibernateDatastore hibernateDatastore) {
        this.hibernateDatastore = hibernateDatastore
    }

    @Override
    void setSessionFactory(SessionFactory sessionFactory) {
        // ignore
    }

    private int incNestingCount() {
        Map<String, Integer> map = nestingCount.get()
        Integer current = map.get(dataSourceName)
        int value = (current != null) ? current + 1 : 1
        map.put(dataSourceName, value)
        return value
    }

    private int decNestingCount() {
        Map<String, Integer> map = nestingCount.get()
        Integer current = map.get(dataSourceName)
        int value = (current != null) ? current - 1 : 0
        if (value < 0) {
            value = 0
        }
        map.put(dataSourceName, value)
        return value
    }

    private void setParticipate(boolean flag) {
        Map<String, Boolean> map = participate.get()
        map.put(dataSourceName, flag)
    }

    private boolean getParticipate() {
        Map<String, Boolean> map = participate.get()
        Boolean ret = map.get(dataSourceName)
        return (ret != null) ? ret : false
    }

}

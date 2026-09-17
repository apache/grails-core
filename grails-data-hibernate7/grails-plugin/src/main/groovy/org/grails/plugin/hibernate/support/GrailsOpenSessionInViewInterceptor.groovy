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

import groovy.transform.CompileStatic
import org.hibernate.FlushMode
import org.hibernate.Session
import org.hibernate.SessionFactory

import org.springframework.dao.DataAccessException
import org.springframework.dao.DataAccessResourceFailureException
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.ui.ModelMap
import org.springframework.web.context.request.WebRequest

import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.orm.hibernate.HibernateDatastore
import org.grails.orm.hibernate.connections.HibernateConnectionSourceSettings
import org.grails.orm.hibernate.support.hibernate7.SessionFactoryUtils
import org.grails.orm.hibernate.support.hibernate7.SessionHolder
import org.grails.orm.hibernate.support.hibernate7.support.OpenSessionInViewInterceptor

/**
 * Extends the default Spring OSIV to support multiple datasources.
 * <p>
 * The default datasource's SessionFactory is managed by the parent class.
 * Additional (non-default) datasource SessionFactories are managed by this
 * subclass, which opens and closes sessions for each one alongside the
 * default session.
 *
 * @author Graeme Rocher
 * @since 0.5
 */
@CompileStatic
class GrailsOpenSessionInViewInterceptor extends OpenSessionInViewInterceptor {

    protected FlushMode hibernateFlushMode = FlushMode.MANUAL

    private final List<AdditionalSessionFactoryConfig> additionalSessionFactories = []

    private static class AdditionalSessionFactoryConfig {

        final String connectionName
        final SessionFactory sessionFactory
        final FlushMode flushMode

        AdditionalSessionFactoryConfig(String connectionName, SessionFactory sessionFactory, FlushMode flushMode) {
            this.connectionName = connectionName
            this.sessionFactory = sessionFactory
            this.flushMode = flushMode
        }

    }

    @Override
    protected Session openSession() throws DataAccessResourceFailureException {
        Session session = super.openSession()
        applyFlushMode(session)
        return session
    }

    protected void applyFlushMode(Session session) {
        session.setHibernateFlushMode(hibernateFlushMode)
    }

    @Override
    void preHandle(WebRequest request) throws DataAccessException {
        super.preHandle(request)

        for (AdditionalSessionFactoryConfig config : additionalSessionFactories) {
            SessionFactory sf = config.sessionFactory
            if (TransactionSynchronizationManager.hasResource(sf)) {
                continue
            }
            if (logger.isDebugEnabled()) {
                logger.debug("Opening additional Hibernate Session for datasource '${config.connectionName}' in OpenSessionInViewInterceptor".toString())
            }
            Session session = sf.openSession()
            session.setHibernateFlushMode(config.flushMode)
            SessionHolder sessionHolder = new SessionHolder(session)
            TransactionSynchronizationManager.bindResource(sf, sessionHolder)
        }
    }

    @Override
    void postHandle(WebRequest request, ModelMap model) throws DataAccessException {
        SessionHolder sessionHolder =
                (SessionHolder) TransactionSynchronizationManager.getResource(getSessionFactory())
        Session session = sessionHolder != null ? sessionHolder.session : null
        try {
            super.postHandle(request, model)
            FlushMode flushMode = session != null ? session.hibernateFlushMode : null
            boolean isNotManual = flushMode != FlushMode.MANUAL && flushMode != FlushMode.COMMIT
            if (session != null && isNotManual) {
                if (logger.isDebugEnabled()) {
                    logger.debug('Eagerly flushing Hibernate session')
                }
                session.flush()
            }
        } finally {
            if (session != null) {
                session.setHibernateFlushMode(FlushMode.MANUAL)
            }
        }

        RuntimeException firstFlushException = null
        for (AdditionalSessionFactoryConfig config : additionalSessionFactories) {
            SessionFactory sf = config.sessionFactory
            SessionHolder additionalHolder = (SessionHolder) TransactionSynchronizationManager.getResource(sf)
            if (additionalHolder != null) {
                Session additionalSession = additionalHolder.session
                try {
                    try {
                        FlushMode additionalFlushMode = additionalSession.hibernateFlushMode
                        boolean additionalIsNotManual = additionalFlushMode != FlushMode.MANUAL && additionalFlushMode != FlushMode.COMMIT
                        if (additionalIsNotManual) {
                            if (logger.isDebugEnabled()) {
                                logger.debug("Eagerly flushing additional Hibernate session for datasource '${config.connectionName}'".toString())
                            }
                            additionalSession.flush()
                        }
                    } catch (RuntimeException ex) {
                        if (firstFlushException == null) {
                            firstFlushException = ex
                        } else {
                            if (logger.isDebugEnabled()) {
                                logger.debug("Additional flush exception for datasource '${config.connectionName}'".toString(), ex)
                            }
                            firstFlushException.addSuppressed(ex)
                        }
                    }
                } finally {
                    additionalSession.setHibernateFlushMode(FlushMode.MANUAL)
                }
            }
        }
        if (firstFlushException != null) {
            throw firstFlushException
        }
    }

    @Override
    void afterCompletion(WebRequest request, Exception ex) throws DataAccessException {
        try {
            for (int i = additionalSessionFactories.size() - 1; i >= 0; i--) {
                AdditionalSessionFactoryConfig config = additionalSessionFactories.get(i)
                SessionFactory sf = config.sessionFactory
                SessionHolder sessionHolder = (SessionHolder) TransactionSynchronizationManager.getResource(sf)
                if (sessionHolder != null) {
                    Session session = sessionHolder.session
                    TransactionSynchronizationManager.unbindResource(sf)
                    if (logger.isDebugEnabled()) {
                        logger.debug("Closing additional Hibernate Session for datasource '${config.connectionName}' in OpenSessionInViewInterceptor".toString())
                    }
                    try {
                        SessionFactoryUtils.closeSession(session)
                    } catch (RuntimeException closeEx) {
                        logger.error("Unexpected exception on closing additional Hibernate Session for datasource '${config.connectionName}'".toString(), closeEx)
                    }
                }
            }
        } finally {
            super.afterCompletion(request, ex)
        }
    }

    void setHibernateDatastore(HibernateDatastore hibernateDatastore) {
        String defaultFlushModeName = hibernateDatastore.defaultFlushModeName
        if (hibernateDatastore.isOsivReadOnly()) {
            this.hibernateFlushMode = FlushMode.MANUAL
        } else {
            this.hibernateFlushMode = FlushMode.valueOf(defaultFlushModeName)
        }
        setSessionFactory(hibernateDatastore.sessionFactory)

        if (hibernateDatastore instanceof HibernateDatastore) {
            HibernateDatastore hibernateDs = (HibernateDatastore) hibernateDatastore
            for (ConnectionSource<SessionFactory, HibernateConnectionSourceSettings> connectionSource : hibernateDs.connectionSources.allConnectionSources) {
                String connectionName = connectionSource.name
                if (ConnectionSource.DEFAULT != connectionName) {
                    HibernateDatastore childDatastore = hibernateDs.getDatastoreForConnection(connectionName)
                    FlushMode childFlushMode
                    if (childDatastore.isOsivReadOnly()) {
                        childFlushMode = FlushMode.MANUAL
                    } else {
                        childFlushMode = FlushMode.valueOf(childDatastore.defaultFlushModeName)
                    }
                    additionalSessionFactories.add(
                            new AdditionalSessionFactoryConfig(connectionName, childDatastore.sessionFactory, childFlushMode)
                    )
                }
            }
        }
    }

}

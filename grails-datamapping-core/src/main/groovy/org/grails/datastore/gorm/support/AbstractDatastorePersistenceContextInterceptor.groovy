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
package org.grails.datastore.gorm.support

import groovy.transform.CompileStatic
import jakarta.persistence.FlushModeType
import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory
import org.springframework.transaction.support.TransactionSynchronizationManager

import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.datastore.mapping.core.Session
import org.grails.datastore.mapping.transactions.SessionHolder

/**
 * Abstract implementation of the persistence context interceptor
 *
 * @since 1.0
 * @author Graeme Rocher
 */
@CompileStatic
abstract class AbstractDatastorePersistenceContextInterceptor {

    private static final Log LOG = LogFactory.getLog(AbstractDatastorePersistenceContextInterceptor)

    protected Datastore datastore

    AbstractDatastorePersistenceContextInterceptor(Datastore datastore) {
        this.datastore = datastore
    }

    void init() {
        final SessionHolder sessionHolder = (SessionHolder) TransactionSynchronizationManager.getResource(datastore)
        if (sessionHolder == null) {
            LOG.debug('Opening single Datastore session in DatastorePersistenceContextInterceptor')
            Session session = getSession()
            session.setFlushMode(FlushModeType.AUTO)
            try {
                DatastoreUtils.bindSession(session, this)
            }
            catch (IllegalStateException ignored) {
            }
        }
    }

    protected Session getSession() {
        return DatastoreUtils.getSession(datastore, true)
    }

    void destroy() {
        // single session mode
        final SessionHolder sessionHolder = (SessionHolder) TransactionSynchronizationManager.getResource(datastore)
        if (sessionHolder != null && this.is(sessionHolder.getCreator())) {
            SessionHolder holder = (SessionHolder) TransactionSynchronizationManager.unbindResource(datastore)
            LOG.debug('Closing single Datastore session in DatastorePersistenceContextInterceptor')
            try {
                Session session = holder.getSession()
                DatastoreUtils.closeSession(session)
            }
            catch (RuntimeException ex) {
                LOG.error('Unexpected exception on closing Datastore Session', ex)
            }
        }
    }

    void disconnect() {
        destroy()
    }

    void reconnect() {
        init()
    }

    void flush() {
        Session session = getSession()
        if (session.hasTransaction()) {
            session.flush()
        }
    }

    void clear() {
        getSession().clear()
    }

    void setReadOnly() {
        getSession().setFlushMode(FlushModeType.COMMIT)
    }

    void setReadWrite() {
        getSession().setFlushMode(FlushModeType.AUTO)
    }

    boolean isOpen() {
        try {
            return DatastoreUtils.doGetSession(datastore, false).isConnected()
        }
        catch (IllegalStateException ignored) {
            return false
        }
    }
}

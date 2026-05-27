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

import groovy.transform.CompileStatic

import org.hibernate.SessionFactory

import org.springframework.transaction.support.TransactionSynchronizationManager

import org.grails.datastore.mapping.core.Session
import org.grails.datastore.mapping.core.SessionResolver
import org.grails.orm.hibernate.support.hibernate7.SessionHolder

/**
 * Hibernate 7 specific SessionResolver
 *
 * @author borinquenkid
 * @since 8.0
 */
@CompileStatic
class HibernateSessionResolver implements SessionResolver<Session> {

    private final SessionFactory sessionFactory
    private final HibernateDatastore datastore

    HibernateSessionResolver(HibernateDatastore datastore, SessionFactory sessionFactory) {
        this.datastore = datastore
        this.sessionFactory = sessionFactory
    }

    @Override
    Session resolve() {
        // 1. Try to find a GORM session bound to the datastore
        Object resource = TransactionSynchronizationManager.getResource(datastore)
        if (resource instanceof org.grails.datastore.mapping.transactions.SessionHolder) {
            return ((org.grails.datastore.mapping.transactions.SessionHolder) resource).getSession()
        }

        // 2. Fallback to native Hibernate session bound to the datastore (legacy Grails binding)
        if (resource instanceof SessionHolder) {
            return new HibernateSession(datastore, sessionFactory)
        }

        // 3. Fallback to native Hibernate session bound to the session factory
        resource = TransactionSynchronizationManager.getResource(sessionFactory)
        if (resource instanceof SessionHolder) {
            return new HibernateSession(datastore, sessionFactory)
        }
        
        return null
    }

    @Override
    Session resolve(String qualifier) {
        // Implementation for multi-datasource routing
        return datastore.getDatastoreForConnection(qualifier).getSessionResolver().resolve()
    }

    @Override
    void bind(Session session) {
        if (session instanceof HibernateSession) {
            TransactionSynchronizationManager.bindResource(sessionFactory, new SessionHolder(((HibernateSession) session).getNativeSession()))
        }
    }

    @Override
    void unbind() {
        TransactionSynchronizationManager.unbindResource(sessionFactory)
    }
}

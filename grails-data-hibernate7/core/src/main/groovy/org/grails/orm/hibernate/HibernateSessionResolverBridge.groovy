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

import org.grails.datastore.mapping.core.Session
import org.grails.datastore.mapping.core.SessionResolver
import org.hibernate.SessionFactory
import org.springframework.transaction.support.TransactionSynchronizationManager

/**
 * A bridge service that resolves sessions for Hibernate-backed datastores
 * by bridging between the GORM SessionResolver contract and Hibernate's 
 * internal session synchronization.
 *
 * @author borinquenkid
 * @since 8.0
 */
class HibernateSessionResolverBridge implements SessionResolver<Session> {

    private final HibernateDatastore datastore
    private final SessionFactory sessionFactory

    HibernateSessionResolverBridge(HibernateDatastore datastore, SessionFactory sessionFactory) {
        this.datastore = datastore
        this.sessionFactory = sessionFactory
    }

    @Override
    Session resolve() {
        Object resource = TransactionSynchronizationManager.getResource(sessionFactory)
        if (resource instanceof org.grails.orm.hibernate.support.hibernate7.SessionHolder) {
            return new HibernateSession(datastore, sessionFactory)
        }
        return null
    }

    @Override
    Session resolve(String qualifier) {
        return datastore.getDatastoreForConnection(qualifier).getSessionResolver().resolve()
    }

    @Override
    void bind(Session session) {
        if (session instanceof HibernateSession) {
            TransactionSynchronizationManager.bindResource(sessionFactory, 
                new org.grails.orm.hibernate.support.hibernate7.SessionHolder(session.getNativeSession()))
        }
    }

    @Override
    void unbind() {
        TransactionSynchronizationManager.unbindResource(sessionFactory)
    }
}

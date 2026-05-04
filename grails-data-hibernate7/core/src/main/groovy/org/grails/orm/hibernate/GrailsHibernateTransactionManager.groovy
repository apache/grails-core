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
import groovy.util.logging.Slf4j
import javax.sql.DataSource
import org.hibernate.FlushMode
import org.hibernate.SessionFactory

import org.grails.orm.hibernate.support.hibernate7.HibernateTransactionManager
import org.grails.orm.hibernate.support.hibernate7.SessionHolder
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.transaction.support.DefaultTransactionStatus
import org.grails.datastore.mapping.core.Datastore

/**
 * Extends the standard class to always set the flush mode to manual when in a read-only transaction.
 *
 * @author Burt Beckwith
 */
@CompileStatic
@Slf4j
class GrailsHibernateTransactionManager extends HibernateTransactionManager {

    final FlushMode defaultFlushMode
    Datastore datastore

    GrailsHibernateTransactionManager(SessionFactory sessionFactory, DataSource dataSource, FlushMode defaultFlushMode = FlushMode.AUTO) {
        super(sessionFactory)
        if (dataSource != null) {
            setDataSource(dataSource)
        }
        this.defaultFlushMode = defaultFlushMode
    }
    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
        super.doBegin transaction, definition
        
        SessionHolder holder = (SessionHolder) TransactionSynchronizationManager.getResource(sessionFactory)
        if (holder != null) {
            if (definition.readOnly) {
                holder.session.setHibernateFlushMode FlushMode.MANUAL
            }
            else {
                holder.session.setHibernateFlushMode(defaultFlushMode)
            }
            if (datastore != null && !TransactionSynchronizationManager.hasResource(datastore)) {
                TransactionSynchronizationManager.bindResource(datastore, new org.grails.datastore.mapping.transactions.SessionHolder(new HibernateSession((HibernateDatastore) datastore, sessionFactory, holder.session)))
            }
        }
    }

    @Override
    protected void doCleanupAfterCompletion(Object transaction) {
        super.doCleanupAfterCompletion(transaction)
        if (datastore != null) {
            HibernateTransactionManager.HibernateTransactionObject txObject = (HibernateTransactionManager.HibernateTransactionObject) transaction
            if (txObject.isNewSessionHolder()) {
                TransactionSynchronizationManager.unbindResourceIfPossible(datastore)
            }
        }
    }
}

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
package org.grails.datastore.gorm.mongodb.springdata

import groovy.transform.CompileStatic

import com.mongodb.client.ClientSession

import org.springframework.data.mongodb.GormSpringDataSessionSupport
import org.springframework.data.mongodb.MongoDatabaseFactory
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionSynchronizationManager

import org.grails.datastore.mapping.mongo.AbstractMongoSession
import org.grails.datastore.mapping.mongo.MongoDatastore
import org.grails.datastore.mapping.transactions.DatastoreTransactionManager

/**
 * A {@link org.springframework.transaction.PlatformTransactionManager} that drives a single GORM
 * MongoDB transaction and additionally exposes its {@link ClientSession} to Spring Data MongoDB, so
 * that GORM operations and {@code MongoTemplate}/repository operations executed within one
 * {@code @Transactional} method participate in the same MongoDB transaction.
 *
 * <p>It extends {@link DatastoreTransactionManager} — inheriting GORM's begin/commit/rollback,
 * session binding and flushing — and, once GORM has started its {@link ClientSession}, binds a
 * Spring Data {@link MongoResourceHolder} referencing that same session, keyed by the
 * {@link MongoDatabaseFactory}. Spring Data's {@code MongoTemplate} discovers that holder via the
 * thread-bound resources and runs inside the session, so a single commit (or abort) — driven by
 * GORM — applies to both stacks atomically.</p>
 *
 * <p>This requires GORM server-side transactions to be enabled
 * ({@code grails.mongodb.transactional = true}); without an active {@link ClientSession} there is
 * nothing to share and Spring Data operations run outside of a transaction as before.</p>
 *
 * <p><strong>Propagation:</strong> like GORM's {@link DatastoreTransactionManager}, this manager
 * supports a single flat transaction ({@code PROPAGATION_REQUIRED}); Spring-native suspension
 * propagations such as {@code REQUIRES_NEW} and {@code NESTED} are not supported and behave as
 * {@code REQUIRED} (they join the surrounding transaction rather than suspending it).</p>
 *
 * @since 8.0
 */
@CompileStatic
class GormSharedSessionMongoTransactionManager extends DatastoreTransactionManager {

    private final MongoDatabaseFactory databaseFactory

    GormSharedSessionMongoTransactionManager(MongoDatastore datastore, MongoDatabaseFactory databaseFactory) {
        this.databaseFactory = databaseFactory
        setDatastore(datastore)
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
        super.doBegin(transaction, definition)

        ClientSession clientSession = currentClientSession()
        if (clientSession != null) {
            GormSpringDataSessionSupport.bindClientSession(databaseFactory, clientSession)
        }
    }

    @Override
    protected void doCleanupAfterCompletion(Object transaction) {
        if (TransactionSynchronizationManager.hasResource(databaseFactory)) {
            TransactionSynchronizationManager.unbindResource(databaseFactory)
        }
        super.doCleanupAfterCompletion(transaction)
    }

    private ClientSession currentClientSession() {
        def session = getDatastore().getCurrentSession()
        return session instanceof AbstractMongoSession ? ((AbstractMongoSession) session).getClientSession() : null
    }
}

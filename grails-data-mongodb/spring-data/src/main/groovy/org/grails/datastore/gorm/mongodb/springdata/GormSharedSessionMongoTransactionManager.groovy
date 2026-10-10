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

import java.util.concurrent.ConcurrentHashMap

import groovy.transform.CompileStatic

import com.mongodb.client.ClientSession

import org.springframework.data.mongodb.GormSpringDataSessionSupport
import org.springframework.data.mongodb.MongoDatabaseFactory
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionSynchronizationManager

import org.grails.datastore.mapping.mongo.MongoDatastore
import org.grails.datastore.mapping.mongo.MongoTransaction
import org.grails.datastore.mapping.transactions.DatastoreTransactionManager
import org.grails.datastore.mapping.transactions.Transaction
import org.grails.datastore.mapping.transactions.TransactionObject

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
 * <p><strong>Propagation:</strong> as for GORM's {@link DatastoreTransactionManager}. A transaction
 * started inside another joins it, for Spring Data as for GORM. {@code REQUIRES_NEW} suspends both:
 * the new transaction runs in a GORM session and {@link ClientSession} of its own, which its
 * {@code MongoTemplate} calls use, and the outer transaction's are restored when it completes. A
 * transaction begun in a session of its own ({@code withNewSession}) likewise runs its
 * {@code MongoTemplate} calls in its own {@link ClientSession}, or, read-only, without one, and the
 * surrounding transaction's is put back when it completes. {@code NESTED} is not supported.</p>
 *
 * @since 8.0
 */
@CompileStatic
class GormSharedSessionMongoTransactionManager extends DatastoreTransactionManager {

    private final MongoDatabaseFactory databaseFactory
    // Spring Data holders set aside by transactions begun in a session of their own inside another,
    // keyed by transaction, and put back when each completes
    private final Map<Object, Object> setAside = new ConcurrentHashMap<>()

    GormSharedSessionMongoTransactionManager(MongoDatastore datastore, MongoDatabaseFactory databaseFactory) {
        this.databaseFactory = databaseFactory
        setDatastore(datastore)
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
        super.doBegin(transaction, definition)

        // A holder already bound names the ClientSession of a transaction this one runs inside, in a
        // session of its own (withNewSession). This one's MongoTemplate calls must not run in it,
        // whether this one begins a ClientSession of its own or, read-only, runs without one
        Object surrounding = TransactionSynchronizationManager.unbindResourceIfPossible(databaseFactory)
        if (surrounding != null) {
            setAside.put(transaction, surrounding)
        }
        ClientSession clientSession = clientSession(transaction)
        if (clientSession != null) {
            GormSpringDataSessionSupport.bindClientSession(databaseFactory, clientSession)
        }
    }

    @Override
    protected Object doSuspend(Object transaction) {
        // Spring Data's holder names the suspended transaction's ClientSession: left bound, the next
        // transaction's MongoTemplate calls would run in the suspended one
        Object springData = TransactionSynchronizationManager.hasResource(databaseFactory) ?
                TransactionSynchronizationManager.unbindResource(databaseFactory) : null
        new SuspendedSessions(super.doSuspend(transaction), springData)
    }

    @Override
    protected void doResume(Object transaction, Object suspendedResources) {
        SuspendedSessions suspended = (SuspendedSessions) suspendedResources
        super.doResume(transaction, suspended.gorm)
        if (TransactionSynchronizationManager.hasResource(databaseFactory)) {
            TransactionSynchronizationManager.unbindResource(databaseFactory)
        }
        if (suspended.springData != null) {
            TransactionSynchronizationManager.bindResource(databaseFactory, suspended.springData)
        }
    }

    @Override
    protected void doCleanupAfterCompletion(Object transaction) {
        Object surrounding = setAside.remove(transaction)
        // Leaves a holder alone that this transaction neither bound nor found bound when it began
        if (clientSession(transaction) != null || surrounding != null) {
            TransactionSynchronizationManager.unbindResourceIfPossible(databaseFactory)
        }
        if (surrounding != null) {
            TransactionSynchronizationManager.bindResource(databaseFactory, surrounding)
        }
        super.doCleanupAfterCompletion(transaction)
    }

    /** The ClientSession of the server-side transaction this one began; null when it began none (read-only) */
    private static ClientSession clientSession(Object transaction) {
        Transaction<?> tx = ((TransactionObject) transaction).getTransaction()
        return tx instanceof MongoTransaction ? ((MongoTransaction) tx).getNativeTransaction() : null
    }

    /** What a suspended transaction had bound: GORM's session holder, and Spring Data's, if any */
    private static final class SuspendedSessions {
        final Object gorm
        final Object springData

        SuspendedSessions(Object gorm, Object springData) {
            this.gorm = gorm
            this.springData = springData
        }
    }
}

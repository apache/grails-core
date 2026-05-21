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

package org.grails.datastore.gorm.mongo.transactions

import groovy.transform.CompileDynamic
import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType
import grails.gorm.transactions.GrailsTransactionTemplate
import org.springframework.transaction.TransactionException
import org.springframework.transaction.TransactionStatus
import org.grails.datastore.mapping.core.Session
import org.grails.datastore.mapping.mongo.MongoDatastore
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.interceptor.TransactionAttribute

/**
 * MongoDB-specific transaction template that properly handles rollback by clearing the session
 *
 * @author Graeme Rocher
 * @since 8.0
 */
class MongoGormTransactionTemplate extends GrailsTransactionTemplate {

    private final MongoDatastore mongoDatastore

    MongoGormTransactionTemplate(MongoDatastore mongoDatastore, PlatformTransactionManager transactionManager) {
        super(transactionManager)
        this.mongoDatastore = mongoDatastore
    }

    MongoGormTransactionTemplate(MongoDatastore mongoDatastore, PlatformTransactionManager transactionManager, TransactionDefinition definition) {
        super(transactionManager, definition)
        this.mongoDatastore = mongoDatastore
    }

    MongoGormTransactionTemplate(MongoDatastore mongoDatastore, PlatformTransactionManager transactionManager, TransactionAttribute attribute) {
        super(transactionManager, attribute)
        this.mongoDatastore = mongoDatastore
    }

    @Override
    @CompileDynamic
    <T> T executeAndRollback(@ClosureParams(value = SimpleType, options = 'org.springframework.transaction.TransactionStatus') Closure<T> action) throws TransactionException {
        return super.executeAndRollback(wrapRollbackAware(action))
    }

    @Override
    @CompileDynamic
    <T> T execute(@ClosureParams(value = SimpleType, options = 'org.springframework.transaction.TransactionStatus') Closure<T> action) throws TransactionException {
        return super.execute(wrapRollbackAware(action))
    }

    @CompileDynamic
    private <T> Closure<T> wrapRollbackAware(Closure<T> action) {
        return { TransactionStatus status ->
            MongoTransactionContext.withRollbackAware {
                try {
                    return action.call(status)
                } catch (Throwable e) {
                    status.setRollbackOnly()
                    throw e
                } finally {
                    if (status.isRollbackOnly()) {
                        clearMongoSession()
                    }
                }
            }
        } as Closure<T>
    }

    @CompileDynamic
    private void clearMongoSession() {
        try {
            Session currentSession = mongoDatastore.currentSession
            currentSession?.clear()
        } catch (IllegalStateException ignored) {
            // No current session bound
        }
    }
}

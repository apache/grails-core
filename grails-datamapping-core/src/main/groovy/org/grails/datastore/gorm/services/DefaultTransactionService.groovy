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
package org.grails.datastore.gorm.services

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType

import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.TransactionSystemException

import grails.gorm.transactions.GrailsTransactionTemplate
import grails.gorm.transactions.TransactionService
import org.grails.datastore.gorm.ConnectionSourceNameResolver
import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.services.Service
import org.grails.datastore.mapping.transactions.CustomizableRollbackTransactionAttribute
import org.grails.datastore.mapping.transactions.TransactionCapableDatastore

/**
 * The transaction service implementation
 *
 * @author Graeme Rocher
 * @since 6.1
 */
@CompileStatic
class DefaultTransactionService implements TransactionService, Service {

    private Datastore datastore

    @Override
    Datastore getDatastore() {
        return this.datastore
    }

    @Override
    void setDatastore(Datastore datastore) {
        this.datastore = datastore
    }

    @Override
    def <T> T withTransaction(
            @ClosureParams(value = SimpleType, options = 'org.springframework.transaction.TransactionStatus') Closure<T> callable) {
        if (datastore instanceof TransactionCapableDatastore) {
            GrailsTransactionTemplate template = newTemplate(((TransactionCapableDatastore) datastore).transactionManager)
            return template.execute(callable)
        }
        else {
            throw new TransactionSystemException("Datastore [$datastore] does not support transactions")
        }
    }

    @Override
    def <T> T withRollback(
            @ClosureParams(value = SimpleType, options = 'org.springframework.transaction.TransactionStatus') Closure<T> callable) {
        if (datastore instanceof TransactionCapableDatastore) {
            GrailsTransactionTemplate template = newTemplate(((TransactionCapableDatastore) datastore).transactionManager)
            return template.executeAndRollback(callable)
        }
        else {
            throw new TransactionSystemException("Datastore [$datastore] does not support transactions")
        }
    }

    @Override
    def <T> T withNewTransaction(
            @ClosureParams(value = SimpleType, options = 'org.springframework.transaction.TransactionStatus') Closure<T> callable) {
        if (datastore instanceof TransactionCapableDatastore) {
            PlatformTransactionManager transactionManager = ((TransactionCapableDatastore) datastore).transactionManager
            def txDef = new CustomizableRollbackTransactionAttribute(propagationBehavior: TransactionDefinition.PROPAGATION_REQUIRES_NEW)
            GrailsTransactionTemplate template = newTemplate(transactionManager, txDef)
            return template.execute(callable)
        }
        else {
            throw new TransactionSystemException("Datastore [$datastore] does not support transactions")
        }
    }

    @Override
    def <T> T withTransaction(TransactionDefinition definition,
                              @ClosureParams(value = SimpleType, options = 'org.springframework.transaction.TransactionStatus') Closure<T> callable) {
        if (datastore instanceof TransactionCapableDatastore) {
            PlatformTransactionManager transactionManager = ((TransactionCapableDatastore) datastore).transactionManager
            GrailsTransactionTemplate template = newTemplate(transactionManager, definition)
            return template.execute(callable)
        }
        else {
            throw new TransactionSystemException("Datastore [$datastore] does not support transactions")
        }
    }

    @Override
    def <T> T withTransaction(Map definition,
                              @ClosureParams(value = SimpleType, options = 'org.springframework.transaction.TransactionStatus') Closure<T> callable) {
        if (datastore instanceof TransactionCapableDatastore) {
            PlatformTransactionManager transactionManager = ((TransactionCapableDatastore) datastore).transactionManager
            def txDef = newDefinition(definition)
            GrailsTransactionTemplate template = newTemplate(transactionManager, txDef)
            return template.execute(callable)
        }
        else {
            throw new TransactionSystemException("Datastore [$datastore] does not support transactions")
        }
    }

    /**
     * A template for the datastore's transaction manager that routes, as the connection's own {@code withTransaction}
     * does, the calls on the domain classes of the datastore's connection to it. Only for a named connection: the
     * root datastore's service leaves the routing of an enclosing block alone, as an unqualified
     * {@code @Transactional} does.
     */
    private GrailsTransactionTemplate newTemplate(PlatformTransactionManager transactionManager, TransactionDefinition definition = null) {
        GrailsTransactionTemplate template = definition == null ?
                new GrailsTransactionTemplate(transactionManager) :
                new GrailsTransactionTemplate(transactionManager, definition)
        String connectionName = ConnectionSourceNameResolver.resolveDefaultConnectionSourceName(datastore)
        if (ConnectionSource.DEFAULT != connectionName) {
            template.connectionName = connectionName
        }
        return template
    }

    @CompileDynamic
    protected CustomizableRollbackTransactionAttribute newDefinition(Map definition) {
        new CustomizableRollbackTransactionAttribute(definition)
    }

    @Override
    def <T> T withRollback(TransactionDefinition definition,
                           @ClosureParams(value = SimpleType, options = 'org.springframework.transaction.TransactionStatus') Closure<T> callable) {
        if (datastore instanceof TransactionCapableDatastore) {
            PlatformTransactionManager transactionManager = ((TransactionCapableDatastore) datastore).transactionManager
            GrailsTransactionTemplate template = newTemplate(transactionManager, definition)
            return template.executeAndRollback(callable)
        }
        else {
            throw new TransactionSystemException("Datastore [$datastore] does not support transactions")
        }

    }

    @Override
    def <T> T withNewTransaction(TransactionDefinition definition,
                                 @ClosureParams(value = SimpleType, options = 'org.springframework.transaction.TransactionStatus') Closure<T> callable) {
        if (datastore instanceof TransactionCapableDatastore) {
            PlatformTransactionManager transactionManager = ((TransactionCapableDatastore) datastore).transactionManager
            def txDef = new CustomizableRollbackTransactionAttribute(definition)
            txDef.propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
            GrailsTransactionTemplate template = newTemplate(transactionManager, txDef)
            return template.execute(callable)
        }
        else {
            throw new TransactionSystemException("Datastore [$datastore] does not support transactions")
        }
    }
}

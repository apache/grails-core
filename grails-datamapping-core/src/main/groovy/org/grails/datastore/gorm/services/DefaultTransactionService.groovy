/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.datastore.gorm.services

import grails.gorm.transactions.TransactionService
import grails.gorm.transactions.GrailsTransactionTemplate
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType
import org.grails.datastore.mapping.services.Service
import org.grails.datastore.mapping.transactions.CustomizableRollbackTransactionAttribute
import org.grails.datastore.mapping.transactions.TransactionCapableDatastore
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.TransactionSystemException

/**
 * The transaction service implementation
 *
 * @author Graeme Rocher
 * @since 6.1
 */
@CompileStatic
class DefaultTransactionService implements TransactionService, Service {
    @Override
    def <T> T withTransaction(
            @ClosureParams(value = SimpleType.class, options = "org.springframework.transaction.TransactionStatus") Closure<T> callable) {
        if(datastore instanceof TransactionCapableDatastore) {
            GrailsTransactionTemplate template = new GrailsTransactionTemplate(((TransactionCapableDatastore)datastore).transactionManager)
            return template.execute(callable)
        }
        else {
            throw new TransactionSystemException("Datastore [$datastore] does not support transactions")
        }
    }

    @Override
    def <T> T withRollback(
            @ClosureParams(value = SimpleType.class, options = "org.springframework.transaction.TransactionStatus") Closure<T> callable) {
        if(datastore instanceof TransactionCapableDatastore) {
            GrailsTransactionTemplate template = new GrailsTransactionTemplate(((TransactionCapableDatastore)datastore).transactionManager)
            return template.executeAndRollback(callable)
        }
        else {
            throw new TransactionSystemException("Datastore [$datastore] does not support transactions")
        }
    }

    @Override
    def <T> T withNewTransaction(
            @ClosureParams(value = SimpleType.class, options = "org.springframework.transaction.TransactionStatus") Closure<T> callable) {
        if(datastore instanceof TransactionCapableDatastore) {
            PlatformTransactionManager transactionManager = ((TransactionCapableDatastore) datastore).transactionManager
            def txDef = new CustomizableRollbackTransactionAttribute(propagationBehavior: TransactionDefinition.PROPAGATION_REQUIRES_NEW)
            GrailsTransactionTemplate template = new GrailsTransactionTemplate(transactionManager, txDef)
            return template.execute(callable)
        }
        else {
            throw new TransactionSystemException("Datastore [$datastore] does not support transactions")
        }
    }

    @Override
    def <T> T withTransaction(TransactionDefinition definition,
                              @ClosureParams(value = SimpleType.class, options = "org.springframework.transaction.TransactionStatus") Closure<T> callable) {
        if(datastore instanceof TransactionCapableDatastore) {
            PlatformTransactionManager transactionManager = ((TransactionCapableDatastore) datastore).transactionManager
            GrailsTransactionTemplate template = new GrailsTransactionTemplate(transactionManager, definition)
            return template.execute(callable)
        }
        else {
            throw new TransactionSystemException("Datastore [$datastore] does not support transactions")
        }
    }

    @Override
    def <T> T withTransaction(Map definition,
                              @ClosureParams(value = SimpleType.class, options = "org.springframework.transaction.TransactionStatus") Closure<T> callable) {
        if(datastore instanceof TransactionCapableDatastore) {
            PlatformTransactionManager transactionManager = ((TransactionCapableDatastore) datastore).transactionManager
            def txDef = newDefinition(definition)
            GrailsTransactionTemplate template = new GrailsTransactionTemplate(transactionManager, txDef)
            return template.execute(callable)
        }
        else {
            throw new TransactionSystemException("Datastore [$datastore] does not support transactions")
        }
    }

    @CompileDynamic
    protected CustomizableRollbackTransactionAttribute newDefinition(Map definition) {
        new CustomizableRollbackTransactionAttribute(definition)
    }

    @Override
    def <T> T withRollback(TransactionDefinition definition,
                           @ClosureParams(value = SimpleType.class, options = "org.springframework.transaction.TransactionStatus") Closure<T> callable) {
        if(datastore instanceof TransactionCapableDatastore) {
            PlatformTransactionManager transactionManager = ((TransactionCapableDatastore) datastore).transactionManager
            GrailsTransactionTemplate template = new GrailsTransactionTemplate(transactionManager, definition)
            return template.executeAndRollback(callable)
        }
        else {
            throw new TransactionSystemException("Datastore [$datastore] does not support transactions")
        }

    }

    @Override
    def <T> T withNewTransaction(TransactionDefinition definition,
                                 @ClosureParams(value = SimpleType.class, options = "org.springframework.transaction.TransactionStatus") Closure<T> callable) {
        if(datastore instanceof TransactionCapableDatastore) {
            PlatformTransactionManager transactionManager = ((TransactionCapableDatastore) datastore).transactionManager
            def txDef = new CustomizableRollbackTransactionAttribute(definition)
            txDef.propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
            GrailsTransactionTemplate template = new GrailsTransactionTemplate(transactionManager, txDef)
            return template.execute(callable)
        }
        else {
            throw new TransactionSystemException("Datastore [$datastore] does not support transactions")
        }
    }
}

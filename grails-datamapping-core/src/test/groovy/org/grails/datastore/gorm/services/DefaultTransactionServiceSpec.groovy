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

import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.TransactionSystemException

import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.transactions.CustomizableRollbackTransactionAttribute
import org.grails.datastore.mapping.transactions.TransactionCapableDatastore
import spock.lang.Specification

class DefaultTransactionServiceSpec extends Specification {

    DefaultTransactionService transactionService = new DefaultTransactionService()

    PlatformTransactionManager transactionManager = Mock(PlatformTransactionManager) {
        getTransaction(_) >> Mock(TransactionStatus)
    }

    TransactionCapableDatastore transactionCapableDatastore = Mock(TransactionCapableDatastore) {
        getTransactionManager() >> transactionManager
    }

    void 'withTransaction(Closure) executes the callable within a transaction'() {
        given:
        transactionService.datastore = transactionCapableDatastore

        when:
        String result = transactionService.withTransaction { status -> 'done' }

        then:
        result == 'done'
        1 * transactionManager.commit(_)
    }

    void 'withTransaction(Closure) throws when the datastore does not support transactions'() {
        given:
        transactionService.datastore = Mock(Datastore)

        when:
        transactionService.withTransaction { status -> 'done' }

        then:
        TransactionSystemException e = thrown(TransactionSystemException)
        e.message.contains('does not support transactions')
    }

    void 'withRollback(Closure) executes the callable and rolls back'() {
        given:
        transactionService.datastore = transactionCapableDatastore

        expect:
        transactionService.withRollback { status -> 'done' } == 'done'
    }

    void 'withRollback(Closure) throws when the datastore does not support transactions'() {
        given:
        transactionService.datastore = Mock(Datastore)

        when:
        transactionService.withRollback { status -> 'done' }

        then:
        thrown(TransactionSystemException)
    }

    void 'withNewTransaction(Closure) executes the callable with PROPAGATION_REQUIRES_NEW'() {
        given:
        transactionService.datastore = transactionCapableDatastore

        expect:
        transactionService.withNewTransaction { status -> 'done' } == 'done'
    }

    void 'withNewTransaction(Closure) throws when the datastore does not support transactions'() {
        given:
        transactionService.datastore = Mock(Datastore)

        when:
        transactionService.withNewTransaction { status -> 'done' }

        then:
        thrown(TransactionSystemException)
    }

    void 'withTransaction(TransactionDefinition, Closure) executes the callable with the given definition'() {
        given:
        transactionService.datastore = transactionCapableDatastore
        TransactionDefinition definition = new CustomizableRollbackTransactionAttribute()

        expect:
        transactionService.withTransaction(definition) { status -> 'done' } == 'done'
    }

    void 'withTransaction(TransactionDefinition, Closure) throws when the datastore does not support transactions'() {
        given:
        transactionService.datastore = Mock(Datastore)
        TransactionDefinition definition = new CustomizableRollbackTransactionAttribute()

        when:
        transactionService.withTransaction(definition) { status -> 'done' }

        then:
        thrown(TransactionSystemException)
    }

    void 'withTransaction(Map, Closure) builds a transaction definition from the map'() {
        given:
        transactionService.datastore = transactionCapableDatastore

        expect:
        transactionService.withTransaction([readOnly: true]) { status -> 'done' } == 'done'
    }

    void 'withTransaction(Map, Closure) throws when the datastore does not support transactions'() {
        given:
        transactionService.datastore = Mock(Datastore)

        when:
        transactionService.withTransaction([readOnly: true]) { status -> 'done' }

        then:
        thrown(TransactionSystemException)
    }

    void 'withRollback(TransactionDefinition, Closure) executes the callable and rolls back'() {
        given:
        transactionService.datastore = transactionCapableDatastore
        TransactionDefinition definition = new CustomizableRollbackTransactionAttribute()

        expect:
        transactionService.withRollback(definition) { status -> 'done' } == 'done'
    }

    void 'withRollback(TransactionDefinition, Closure) throws when the datastore does not support transactions'() {
        given:
        transactionService.datastore = Mock(Datastore)
        TransactionDefinition definition = new CustomizableRollbackTransactionAttribute()

        when:
        transactionService.withRollback(definition) { status -> 'done' }

        then:
        thrown(TransactionSystemException)
    }

    void 'withNewTransaction(TransactionDefinition, Closure) forces PROPAGATION_REQUIRES_NEW'() {
        given:
        transactionService.datastore = transactionCapableDatastore
        TransactionDefinition definition = new CustomizableRollbackTransactionAttribute()

        expect:
        transactionService.withNewTransaction(definition) { status -> 'done' } == 'done'
    }

    void 'withNewTransaction(TransactionDefinition, Closure) throws when the datastore does not support transactions'() {
        given:
        transactionService.datastore = Mock(Datastore)
        TransactionDefinition definition = new CustomizableRollbackTransactionAttribute()

        when:
        transactionService.withNewTransaction(definition) { status -> 'done' }

        then:
        thrown(TransactionSystemException)
    }
}

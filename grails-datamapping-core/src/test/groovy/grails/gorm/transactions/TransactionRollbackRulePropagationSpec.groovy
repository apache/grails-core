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
package grails.gorm.transactions

import javax.sql.DataSource

import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.interceptor.NoRollbackRuleAttribute
import org.springframework.transaction.interceptor.RuleBasedTransactionAttribute
import org.springframework.transaction.support.DefaultTransactionStatus
import spock.lang.Specification

import org.grails.datastore.gorm.services.DefaultTransactionService
import org.grails.datastore.mapping.transactions.TransactionCapableDatastore

/**
 * Verifies that rollback rules configured on a transaction definition survive the conversion
 * to {@link org.grails.datastore.mapping.transactions.CustomizableRollbackTransactionAttribute}
 * performed by the public transaction APIs.
 */
class TransactionRollbackRulePropagationSpec extends Specification {

    RecordingTransactionManager transactionManager

    void setup() {
        def dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:${TransactionRollbackRulePropagationSpec.name};LOCK_TIMEOUT=10000", 'sa', '')
        dataSource.driverClassName = 'org.h2.Driver'
        transactionManager = new RecordingTransactionManager(dataSource)
    }

    void "GrailsTransactionTemplate honors a NoRollbackRuleAttribute on the supplied transaction attribute"() {
        given: "a rule based attribute that must not roll back on the business exception"
        def attribute = new RuleBasedTransactionAttribute()
        attribute.setRollbackRules([new NoRollbackRuleAttribute(TestBusinessException)])
        def template = new GrailsTransactionTemplate(transactionManager, attribute)

        when: "the transactional closure throws the matching exception"
        template.execute { TransactionStatus status ->
            throw new TestBusinessException()
        }

        then: "the exception propagates but the transaction is committed, not rolled back"
        thrown(TestBusinessException)
        transactionManager.committed
        !transactionManager.rolledBack
    }

    void "GrailsTransactionTemplate still rolls back when no rule matches the thrown exception"() {
        given:
        def attribute = new RuleBasedTransactionAttribute()
        attribute.setRollbackRules([new NoRollbackRuleAttribute(TestBusinessException)])
        def template = new GrailsTransactionTemplate(transactionManager, attribute)

        when:
        template.execute { TransactionStatus status ->
            throw new IllegalStateException('no rule matches this')
        }

        then:
        thrown(IllegalStateException)
        transactionManager.rolledBack
        !transactionManager.committed
    }

    void "withNewTransaction honors rollback rules from a RuleBasedTransactionAttribute definition"() {
        given:
        def service = new DefaultTransactionService()
        service.datastore = Stub(TransactionCapableDatastore) {
            getTransactionManager() >> transactionManager
        }
        def definition = new RuleBasedTransactionAttribute()
        definition.setRollbackRules([new NoRollbackRuleAttribute(TestBusinessException)])

        when: "the transactional closure throws the matching exception"
        service.withNewTransaction((TransactionDefinition) definition) { TransactionStatus status ->
            throw new TestBusinessException()
        }

        then: "the exception propagates but the transaction is committed, not rolled back"
        thrown(TestBusinessException)
        transactionManager.committed
        !transactionManager.rolledBack

        and: "the new transaction was started with REQUIRES_NEW propagation"
        transactionManager.definition.propagationBehavior == TransactionDefinition.PROPAGATION_REQUIRES_NEW
    }

    void "withNewTransaction still rolls back when no rule matches the thrown exception"() {
        given:
        def service = new DefaultTransactionService()
        service.datastore = Stub(TransactionCapableDatastore) {
            getTransactionManager() >> transactionManager
        }
        def definition = new RuleBasedTransactionAttribute()
        definition.setRollbackRules([new NoRollbackRuleAttribute(TestBusinessException)])

        when:
        service.withNewTransaction((TransactionDefinition) definition) { TransactionStatus status ->
            throw new IllegalStateException('no rule matches this')
        }

        then:
        thrown(IllegalStateException)
        transactionManager.rolledBack
        !transactionManager.committed
    }

    static class TestBusinessException extends RuntimeException {
    }

    static class RecordingTransactionManager extends DataSourceTransactionManager {

        boolean committed = false
        boolean rolledBack = false
        TransactionDefinition definition

        RecordingTransactionManager(DataSource dataSource) {
            super(dataSource)
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
            this.definition = definition
            super.doBegin(transaction, definition)
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            committed = true
            super.doCommit(status)
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            rolledBack = true
            super.doRollback(status)
        }
    }
}

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
package org.grails.datastore.gorm.transactions

import grails.gorm.transactions.GrailsTransactionTemplate
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.interceptor.TransactionAttribute
import spock.lang.Specification

/**
 * Brand-new file in this PR. {@code GormStaticApi} only calls the {@code (PlatformTransactionManager)}
 * and {@code (PlatformTransactionManager, TransactionDefinition)} overloads (already covered
 * incidentally via the many existing `withTransaction`/`withNewTransaction` tests across the
 * suite) - the {@code (PlatformTransactionManager, TransactionAttribute)} overload has no caller
 * anywhere in the codebase.
 */
class DefaultTransactionTemplateFactorySpec extends Specification {

    DefaultTransactionTemplateFactory factory = new DefaultTransactionTemplateFactory()
    PlatformTransactionManager transactionManager = Mock(PlatformTransactionManager)

    void "createTransactionTemplate(manager) builds a real GrailsTransactionTemplate"() {
        expect:
        factory.createTransactionTemplate(transactionManager) instanceof GrailsTransactionTemplate
    }

    void "createTransactionTemplate(manager, TransactionDefinition) builds a real GrailsTransactionTemplate"() {
        given:
        def definition = Mock(TransactionDefinition) {
            getIsolationLevel() >> TransactionDefinition.ISOLATION_DEFAULT
        }

        expect:
        factory.createTransactionTemplate(transactionManager, definition) instanceof GrailsTransactionTemplate
    }

    void "createTransactionTemplate(manager, TransactionAttribute) builds a real GrailsTransactionTemplate"() {
        given:
        def attribute = Mock(TransactionAttribute) {
            getIsolationLevel() >> TransactionDefinition.ISOLATION_DEFAULT
        }

        expect:
        factory.createTransactionTemplate(transactionManager, attribute) instanceof GrailsTransactionTemplate
    }
}

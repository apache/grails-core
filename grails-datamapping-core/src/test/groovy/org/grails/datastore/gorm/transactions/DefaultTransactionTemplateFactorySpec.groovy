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
import org.springframework.transaction.interceptor.DefaultTransactionAttribute
import org.springframework.transaction.support.SimpleTransactionStatus
import spock.lang.Specification

/**
 * Tests for DefaultTransactionTemplateFactory
 */
class DefaultTransactionTemplateFactorySpec extends Specification {

    DefaultTransactionTemplateFactory factory
    PlatformTransactionManager mockTransactionManager

    void setup() {
        factory = new DefaultTransactionTemplateFactory()
        mockTransactionManager = Mock(PlatformTransactionManager)
    }

    void "createTransactionTemplate creates GrailsTransactionTemplate with transaction manager"() {
        when:
        def template = factory.createTransactionTemplate(mockTransactionManager)

        then:
        template != null
        template instanceof GrailsTransactionTemplate
    }

    void "createTransactionTemplate with TransactionDefinition creates template with definition"() {
        given:
        def definition = Mock(TransactionDefinition) {
            getIsolationLevel() >> TransactionDefinition.ISOLATION_DEFAULT
            getPropagationBehavior() >> TransactionDefinition.PROPAGATION_REQUIRED
            getTimeout() >> -1
            isReadOnly() >> false
        }

        when:
        def template = factory.createTransactionTemplate(mockTransactionManager, definition)

        then:
        template != null
        template instanceof GrailsTransactionTemplate
    }

    void "createTransactionTemplate with TransactionAttribute creates template with attribute"() {
        given:
        def attribute = new DefaultTransactionAttribute()

        when:
        def template = factory.createTransactionTemplate(mockTransactionManager, attribute)

        then:
        template != null
        template instanceof GrailsTransactionTemplate
    }

    void "factory is consistent across multiple calls"() {
        when:
        def template1 = factory.createTransactionTemplate(mockTransactionManager)
        def template2 = factory.createTransactionTemplate(mockTransactionManager)

        then:
        template1 != null
        template2 != null
        template1.class == template2.class
    }
}

/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  'License'); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  'AS IS' BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

package org.grails.datastore.gorm.mongo.transactions

import grails.gorm.transactions.GrailsTransactionTemplate
import org.grails.datastore.mapping.mongo.MongoDatastore
import org.grails.datastore.mapping.mongo.config.MongoMappingContext
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.interceptor.DefaultTransactionAttribute
import spock.lang.Specification

/**
 * Specification for MongoTransactionTemplateFactory
 */
class MongoTransactionTemplateFactorySpec extends Specification {

    void 'MongoTransactionTemplateFactory creates MongoGormTransactionTemplate with default settings'() {
        given: 'a mock datastore and transaction manager'
        def datastore = new MongoDatastore(new MongoMappingContext('TxEntity'))
        def mockTxManager = Mock(PlatformTransactionManager)
        def factory = new MongoTransactionTemplateFactory(datastore)

        when: 'creating transaction template'
        def template = factory.createTransactionTemplate(mockTxManager)

        then: 'MongoGormTransactionTemplate is returned'
        template != null
        template instanceof MongoGormTransactionTemplate
        template instanceof GrailsTransactionTemplate

        cleanup:
        datastore.close()
    }

    void 'MongoTransactionTemplateFactory creates MongoGormTransactionTemplate with TransactionDefinition'() {
        given: 'mock objects'
        def datastore = new MongoDatastore(new MongoMappingContext('TxEntity'))
        def mockTxManager = Mock(PlatformTransactionManager)
        def mockDefinition = Mock(TransactionDefinition) {
            getIsolationLevel() >> TransactionDefinition.ISOLATION_DEFAULT
            getPropagationBehavior() >> TransactionDefinition.PROPAGATION_REQUIRED
            getTimeout() >> -1
            isReadOnly() >> false
        }
        def factory = new MongoTransactionTemplateFactory(datastore)

        when: 'creating transaction template with definition'
        def template = factory.createTransactionTemplate(mockTxManager, mockDefinition)

        then: 'MongoGormTransactionTemplate is returned'
        template != null
        template instanceof MongoGormTransactionTemplate

        cleanup:
        datastore.close()
    }

    void 'MongoTransactionTemplateFactory creates MongoGormTransactionTemplate with TransactionAttribute'() {
        given: 'mock objects'
        def datastore = new MongoDatastore(new MongoMappingContext('TxEntity'))
        def mockTxManager = Mock(PlatformTransactionManager)
        def attribute = new DefaultTransactionAttribute()
        def factory = new MongoTransactionTemplateFactory(datastore)

        when: 'creating transaction template with attribute'
        def template = factory.createTransactionTemplate(mockTxManager, attribute)

        then: 'MongoGormTransactionTemplate is returned'
        template != null
        template instanceof MongoGormTransactionTemplate

        cleanup:
        datastore.close()
    }

    void 'MongoTransactionTemplateFactory is consistent across calls'() {
        given: 'a factory and transaction manager'
        def datastore = new MongoDatastore(new MongoMappingContext('TxEntity'))
        def mockTxManager = Mock(PlatformTransactionManager)
        def factory = new MongoTransactionTemplateFactory(datastore)

        when: 'creating multiple templates'
        def template1 = factory.createTransactionTemplate(mockTxManager)
        def template2 = factory.createTransactionTemplate(mockTxManager)

        then: 'both are MongoGormTransactionTemplate instances'
        template1 instanceof MongoGormTransactionTemplate
        template2 instanceof MongoGormTransactionTemplate
        template1.class == template2.class

        cleanup:
        datastore.close()
    }
}

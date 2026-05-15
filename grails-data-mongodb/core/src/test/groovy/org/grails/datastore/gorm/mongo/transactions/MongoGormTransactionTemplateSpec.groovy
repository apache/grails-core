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

import org.grails.datastore.mapping.mongo.MongoDatastore
import org.grails.datastore.mapping.mongo.config.MongoMappingContext
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.interceptor.DefaultTransactionAttribute
import spock.lang.Specification

/**
 * Specification for MongoGormTransactionTemplate
 */
class MongoGormTransactionTemplateSpec extends Specification {

    void 'MongoTransactionContext enables and restores rollback-aware marker'() {
        expect:
        !MongoTransactionContext.isRollbackAwareActive()

        when:
        def inside = MongoTransactionContext.withRollbackAware {
            MongoTransactionContext.isRollbackAwareActive()
        }

        then:
        inside
        !MongoTransactionContext.isRollbackAwareActive()
    }

    void 'MongoTransactionContext supports nested scopes'() {
        when:
        def result = MongoTransactionContext.withRollbackAware {
            def nested = MongoTransactionContext.withRollbackAware {
                MongoTransactionContext.isRollbackAwareActive()
            }
            [MongoTransactionContext.isRollbackAwareActive(), nested]
        }

        then:
        result[0]
        result[1]
        !MongoTransactionContext.isRollbackAwareActive()
    }

    void 'MongoGormTransactionTemplate can be instantiated with TransactionManager'() {
        given: 'a mock datastore and transaction manager'
        def datastore = new MongoDatastore(new MongoMappingContext('TxEntity'))
        def mockTxManager = Mock(PlatformTransactionManager)

        when: 'creating MongoGormTransactionTemplate'
        def template = new MongoGormTransactionTemplate(datastore, mockTxManager)

        then: 'instance is created successfully'
        template != null
        template instanceof MongoGormTransactionTemplate

        cleanup:
        datastore.close()
    }

    void 'MongoGormTransactionTemplate can be instantiated with TransactionDefinition'() {
        given: 'mock objects'
        def datastore = new MongoDatastore(new MongoMappingContext('TxEntity'))
        def mockTxManager = Mock(PlatformTransactionManager)
        def mockDefinition = Mock(TransactionDefinition) {
            getIsolationLevel() >> TransactionDefinition.ISOLATION_DEFAULT
            getPropagationBehavior() >> TransactionDefinition.PROPAGATION_REQUIRED
            getTimeout() >> -1
            isReadOnly() >> false
        }

        when: 'creating MongoGormTransactionTemplate with definition'
        def template = new MongoGormTransactionTemplate(datastore, mockTxManager, mockDefinition)

        then: 'instance is created successfully'
        template != null
        template instanceof MongoGormTransactionTemplate

        cleanup:
        datastore.close()
    }

    void 'MongoGormTransactionTemplate can be instantiated with TransactionAttribute'() {
        given: 'mock objects'
        def datastore = new MongoDatastore(new MongoMappingContext('TxEntity'))
        def mockTxManager = Mock(PlatformTransactionManager)
        def attribute = new DefaultTransactionAttribute()

        when: 'creating MongoGormTransactionTemplate with attribute'
        def template = new MongoGormTransactionTemplate(datastore, mockTxManager, attribute)

        then: 'instance is created successfully'
        template != null
        template instanceof MongoGormTransactionTemplate

        cleanup:
        datastore.close()
    }
}

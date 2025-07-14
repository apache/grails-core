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
package org.grails.test.support

import grails.util.GrailsClassUtils
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.springframework.context.ApplicationContext
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.support.DefaultTransactionDefinition
import org.springframework.transaction.support.TransactionSynchronizationManager

/**
 * Establishes a rollback only transaction for running a test in.
 */
class GrailsTestTransactionInterceptor {


    static final String TRANSACTIONAL = "transactional"

    ApplicationContext applicationContext
    protected Map<String, TransactionStatus> transactionStatuses
    protected Map<String, PlatformTransactionManager> transactionManagers

    GrailsTestTransactionInterceptor(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext
        this.transactionManagers = [:]
        this.transactionStatuses = [:]

        def datasourceNames = []

        if (applicationContext.containsBean('dataSource')) {
            datasourceNames << ConnectionSource.DEFAULT
        }

        for (name in applicationContext.grailsApplication.config.keySet()) {
            if (name.startsWith('dataSource_')) {
                datasourceNames << name - 'dataSource_'
            }
        }

        for (datasourceName in datasourceNames) {
            boolean isDefault = datasourceName == ConnectionSource.DEFAULT
            String suffix = isDefault ? '' : '_' + datasourceName

            if (applicationContext.containsBean("transactionManager$suffix")) {
                transactionManagers[datasourceName] = applicationContext."transactionManager$suffix"
            }
        }
    }

    /**
     * Establishes a transaction.
     */
    void init() {
        TransactionSynchronizationManager.initSynchronization()
        transactionManagers.each { datasourceName, PlatformTransactionManager transactionManager ->
            if (transactionStatuses[datasourceName] == null) {
                transactionStatuses[datasourceName] = transactionManager.getTransaction(new DefaultTransactionDefinition())
            } else {
                throw new RuntimeException("init() called on test transaction interceptor during transaction for datasource $datasourceName")
            }
        }
    }

    /**
     * Rolls back the current transaction.
     */
    void destroy() {
        transactionManagers.each { datasourceName, PlatformTransactionManager transactionManager ->
            if (transactionStatuses[datasourceName]) {
                transactionManager.rollback(transactionStatuses[datasourceName])
                transactionStatuses[datasourceName] = null
            }
        }
        TransactionSynchronizationManager.clearSynchronization()
    }

    /**
     * A test is non transactional if it defines an instance or static property name 'transactional' with
     * a value of {@code false}.
     */
    boolean isTransactional(test) {
        def value = GrailsClassUtils.getPropertyOrStaticPropertyOrFieldValue(test, TRANSACTIONAL)
        !(value instanceof Boolean) || (Boolean) value
    }
}

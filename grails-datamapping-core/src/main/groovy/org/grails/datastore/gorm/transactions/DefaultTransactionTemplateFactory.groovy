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

import groovy.transform.CompileStatic
import grails.gorm.transactions.GrailsTransactionTemplate
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.interceptor.TransactionAttribute

/**
 * Default transaction template factory that uses standard GrailsTransactionTemplate.
 *
 * @since 8.0.0
 */
@CompileStatic
class DefaultTransactionTemplateFactory implements TransactionTemplateFactory {

    @Override
    GrailsTransactionTemplate createTransactionTemplate(PlatformTransactionManager transactionManager) {
        return new GrailsTransactionTemplate(transactionManager)
    }

    @Override
    GrailsTransactionTemplate createTransactionTemplate(PlatformTransactionManager transactionManager,
                                                       TransactionDefinition transactionDefinition) {
        return new GrailsTransactionTemplate(transactionManager, transactionDefinition)
    }

    @Override
    GrailsTransactionTemplate createTransactionTemplate(PlatformTransactionManager transactionManager,
                                                       TransactionAttribute transactionAttribute) {
        return new GrailsTransactionTemplate(transactionManager, transactionAttribute)
    }
}

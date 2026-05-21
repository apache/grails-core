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

package grails.async.services

import grails.async.DelegateAsync
import grails.transaction.TransactionManagerAware
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.TransactionException
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.SimpleTransactionStatus
import spock.lang.Specification

class AsyncTransactionalServiceSpec extends Specification {

    void 'Test that an async transactional service is transaction manager aware'() {

        when: 'a transactional service is used as a delegate'
            def asyncService = new AsyncRegularService()

        then: 'the async service is transactionManager aware'
            asyncService instanceof TransactionManagerAware

        when: 'the transaction manager is set'
            TransactionStatus txStatus
            TransactionDefinition txDef
            final txManager = new PlatformTransactionManager() {
                @Override
                TransactionStatus getTransaction(TransactionDefinition definition) throws TransactionException {
                    txDef = definition
                    txStatus = new SimpleTransactionStatus(true)
                    return txStatus
                }

                @Override
                void commit(TransactionStatus status) throws TransactionException {}

                @Override
                void rollback(TransactionStatus status) throws TransactionException {
                    status.setRollbackOnly()
                }
            }
            asyncService.transactionManager = txManager

        and: 'the async service is invoked'
            def result = asyncService.doWork().get()

        then: 'created promises are transactional'
            txStatus != null
            !txDef.readOnly

        when: 'custom transaction attributes are used'
            result = asyncService.readStuff().get()

        then: 'The custom tx attributes are used'
            txDef != null
            txDef.readOnly

    }
}

class RegularService {

    static transactional = true
    void doWork(String arg) {}

    @Transactional(readOnly = true)
    void readStuff(String arg) {}
}

class AsyncRegularService {

    @DelegateAsync RegularService regularService = new RegularService()
}

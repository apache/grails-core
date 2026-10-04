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
package org.grails.transaction

import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.TransactionStatus
import spock.lang.Specification

class MultiTransactionStatusSpec extends Specification {

    void 'delegates status queries to the main transaction manager status'() {
        given:
        PlatformTransactionManager main = Mock(PlatformTransactionManager)
        TransactionStatus mainStatus = Mock(TransactionStatus) {
            isRollbackOnly() >> true
            isCompleted() >> true
            isNewTransaction() >> true
            hasSavepoint() >> true
        }
        main.getTransaction(_ as TransactionDefinition) >> mainStatus

        MultiTransactionStatus multi = new MultiTransactionStatus(main)
        multi.registerTransactionManager(Mock(TransactionDefinition), main)

        expect:
        multi.isRollbackOnly()
        multi.isCompleted()
        multi.isNewTransaction()
        multi.hasSavepoint()
    }

    void 'setRollbackOnly and flush are applied to every registered transaction status'() {
        given:
        PlatformTransactionManager main = Mock(PlatformTransactionManager)
        PlatformTransactionManager other = Mock(PlatformTransactionManager)
        TransactionStatus mainStatus = Mock(TransactionStatus)
        TransactionStatus otherStatus = Mock(TransactionStatus)
        main.getTransaction(_ as TransactionDefinition) >> mainStatus
        other.getTransaction(_ as TransactionDefinition) >> otherStatus

        MultiTransactionStatus multi = new MultiTransactionStatus(main)
        multi.registerTransactionManager(Mock(TransactionDefinition), main)
        multi.registerTransactionManager(Mock(TransactionDefinition), other)

        when:
        multi.setRollbackOnly()
        multi.flush()

        then:
        1 * mainStatus.setRollbackOnly()
        1 * otherStatus.setRollbackOnly()
        1 * mainStatus.flush()
        1 * otherStatus.flush()
    }

    void 'commit and rollback delegate to the given transaction manager using its own registered status'() {
        given:
        PlatformTransactionManager tm = Mock(PlatformTransactionManager)
        TransactionStatus status = Mock(TransactionStatus)
        tm.getTransaction(_ as TransactionDefinition) >> status

        MultiTransactionStatus multi = new MultiTransactionStatus(tm)
        multi.registerTransactionManager(Mock(TransactionDefinition), tm)

        when:
        multi.commit(tm)

        then:
        1 * tm.commit(status)

        when:
        multi.rollback(tm)

        then:
        1 * tm.rollback(status)
    }

    void 'newSynchronization defaults to false and can be flipped on'() {
        given:
        MultiTransactionStatus multi = new MultiTransactionStatus(Mock(PlatformTransactionManager))

        expect:
        !multi.newSynchronization

        when:
        multi.setNewSynchronization()

        then:
        multi.newSynchronization
    }

    void 'savepoints are created, rolled back to, and released per transaction status'() {
        given:
        PlatformTransactionManager main = Mock(PlatformTransactionManager)
        PlatformTransactionManager other = Mock(PlatformTransactionManager)
        TransactionStatus mainStatus = Mock(TransactionStatus)
        TransactionStatus otherStatus = Mock(TransactionStatus)
        main.getTransaction(_ as TransactionDefinition) >> mainStatus
        other.getTransaction(_ as TransactionDefinition) >> otherStatus

        MultiTransactionStatus multi = new MultiTransactionStatus(main)
        multi.registerTransactionManager(Mock(TransactionDefinition), main)
        multi.registerTransactionManager(Mock(TransactionDefinition), other)

        when:
        Object savepoint = multi.createSavepoint()

        then:
        1 * mainStatus.createSavepoint() >> 'main-savepoint'
        1 * otherStatus.createSavepoint() >> 'other-savepoint'

        when:
        multi.rollbackToSavepoint(savepoint)

        then:
        1 * mainStatus.rollbackToSavepoint('main-savepoint')
        1 * otherStatus.rollbackToSavepoint('other-savepoint')

        when:
        multi.releaseSavepoint(savepoint)

        then:
        1 * mainStatus.releaseSavepoint('main-savepoint')
        1 * otherStatus.releaseSavepoint('other-savepoint')
    }
}

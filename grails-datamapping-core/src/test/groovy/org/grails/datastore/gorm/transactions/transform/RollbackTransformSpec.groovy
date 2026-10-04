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
package org.grails.datastore.gorm.transactions.transform

import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionStatus

import spock.lang.Specification

import grails.gorm.transactions.Rollback
import org.apache.grails.common.compiler.GroovyTransformOrder

/**
 * {@code RollbackTransform} only overrides two methods of {@link TransactionalTransform}, and
 * {@code TransactionalTransformSpec} already exercises {@code @Rollback} end-to-end against a real
 * datastore. This spec pins down, in isolation, what those two overrides change: a {@code @Rollback}
 * method drives its transaction through the rollback-forcing template method (observable as the
 * transaction always being marked rollback-only, where {@code @Transactional} leaves it alone), and
 * the transform ordering priority that sequences it after {@code TransactionalTransform}.
 */
class RollbackTransformSpec extends Specification {

    void "a #annotation method #description"() {
        given: 'a class woven by the real transform pipeline for the annotation under test'
        Class<?> serviceClass = new GroovyClassLoader().parseClass("""
            package org.grails.datastore.gorm.transactions.transform.fixture

            class ${annotation}Service {
                @grails.gorm.transactions.${annotation}
                String doIt() { 'done' }
            }
        """)
        TransactionStatus status = Mock()
        PlatformTransactionManager transactionManager = Mock {
            getTransaction(_) >> status
        }
        def service = serviceClass.getDeclaredConstructor().newInstance()
        service.transactionManager = transactionManager

        when:
        String result = service.doIt()

        then: 'the original body ran inside the transaction'
        result == 'done'
        1 * transactionManager.commit(status)

        and: 'only @Rollback routes through the rollback-forcing template method'
        rollbackOnlyCalls * status.setRollbackOnly()

        where:
        annotation      | rollbackOnlyCalls || description
        'Rollback'      | 1                 || 'always marks its transaction rollback-only'
        'Transactional' | 0                 || 'leaves the transaction alone'
    }

    void "priority orders RollbackTransform after TransactionalTransform"() {
        given:
        RollbackTransform transform = new RollbackTransform()

        expect:
        transform.priority() == GroovyTransformOrder.ROLLBACK_ORDER
        transform.priority() < GroovyTransformOrder.TRANSACTIONAL_ORDER
    }

    void "MY_TYPE identifies the Rollback annotation"() {
        expect:
        RollbackTransform.MY_TYPE.name == Rollback.name
    }
}

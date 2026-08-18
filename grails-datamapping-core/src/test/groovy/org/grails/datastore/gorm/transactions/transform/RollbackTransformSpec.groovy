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

import spock.lang.Specification

import grails.gorm.transactions.Rollback
import org.apache.grails.common.compiler.GroovyTransformOrder

/**
 * {@code RollbackTransform} only overrides two methods of {@link TransactionalTransform} and its
 * end-to-end weaving behavior is already exercised (via the {@code @Rollback} annotation) by
 * {@code TransactionalTransformSpec}. This spec covers what those behavioral tests can't: that the
 * overrides themselves - the transaction template method name and the transform ordering priority -
 * are the values that make {@code @Rollback} behave differently from plain {@code @Transactional}.
 */
class RollbackTransformSpec extends Specification {

    void "getTransactionTemplateMethodName overrides the parent to route through the rollback-forcing template method"() {
        given:
        RollbackTransform transform = new RollbackTransform()

        expect:
        transform.getTransactionTemplateMethodName() == 'executeAndRollback'
        new TransactionalTransform().getTransactionTemplateMethodName() == 'execute'
    }

    void "priority orders RollbackTransform after TransactionalTransform"() {
        given:
        RollbackTransform transform = new RollbackTransform()

        expect:
        transform.priority() == GroovyTransformOrder.ROLLBACK_ORDER
        transform.priority() < GroovyTransformOrder.TRANSACTIONAL_ORDER
    }

    void "MY_TYPE identifies the Rollback annotation and the class extends TransactionalTransform"() {
        expect:
        RollbackTransform.MY_TYPE.name == Rollback.name
        TransactionalTransform.isAssignableFrom(RollbackTransform)
    }
}

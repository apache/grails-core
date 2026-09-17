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

import org.springframework.transaction.interceptor.NoRollbackRuleAttribute
import org.springframework.transaction.interceptor.RollbackRuleAttribute
import org.springframework.transaction.interceptor.TransactionAttribute
import spock.lang.Specification

class GrailsTransactionAttributeSpec extends Specification {

    void 'rolls back on any exception when no rollback rules are configured'() {
        given:
        GrailsTransactionAttribute attribute = new GrailsTransactionAttribute()

        expect:
        attribute.rollbackOn(new RuntimeException())
        attribute.rollbackOn(new Exception())
        attribute.rollbackOn(new Error())
    }

    void 'rolls back when the closest matching rule is a RollbackRuleAttribute'() {
        given:
        GrailsTransactionAttribute attribute = new GrailsTransactionAttribute()
        attribute.rollbackRules = [new RollbackRuleAttribute(IllegalStateException)]

        expect:
        attribute.rollbackOn(new IllegalStateException())
    }

    void 'does not roll back when the closest matching rule is a NoRollbackRuleAttribute'() {
        given:
        GrailsTransactionAttribute attribute = new GrailsTransactionAttribute()
        attribute.rollbackRules = [new NoRollbackRuleAttribute(IllegalStateException)]

        expect:
        !attribute.rollbackOn(new IllegalStateException())
    }

    void 'the most specific (deepest) matching rule wins when rules conflict'() {
        given:
        GrailsTransactionAttribute attribute = new GrailsTransactionAttribute()
        attribute.rollbackRules = [
                new NoRollbackRuleAttribute(RuntimeException),
                new RollbackRuleAttribute(IllegalStateException)
        ]

        expect:
        attribute.rollbackOn(new IllegalStateException())
    }

    void 'copy constructor from a TransactionDefinition carries over propagation, isolation, timeout, readOnly and name'() {
        given:
        GrailsTransactionAttribute source = new GrailsTransactionAttribute()
        source.propagationBehavior = org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW
        source.timeout = 42
        source.readOnly = true
        source.name = 'someTx'

        when:
        GrailsTransactionAttribute copy = new GrailsTransactionAttribute((TransactionAttribute) source)

        then:
        copy.propagationBehavior == source.propagationBehavior
        copy.timeout == source.timeout
        copy.readOnly == source.readOnly
        copy.name == source.name
    }

    void 'inheritRollbackOnly is copied when constructing from another GrailsTransactionAttribute'() {
        given:
        GrailsTransactionAttribute source = new GrailsTransactionAttribute()
        source.inheritRollbackOnly = false

        when:
        GrailsTransactionAttribute copy = new GrailsTransactionAttribute(source)

        then:
        !copy.inheritRollbackOnly
    }

    void 'inheritRollbackOnly defaults to true'() {
        expect:
        new GrailsTransactionAttribute().inheritRollbackOnly
    }
}

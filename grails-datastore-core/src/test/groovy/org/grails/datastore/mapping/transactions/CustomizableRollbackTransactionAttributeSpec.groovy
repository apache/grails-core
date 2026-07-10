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
package org.grails.datastore.mapping.transactions

import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.interceptor.NoRollbackRuleAttribute
import org.springframework.transaction.interceptor.RollbackRuleAttribute
import org.springframework.transaction.interceptor.RuleBasedTransactionAttribute
import org.springframework.transaction.support.DefaultTransactionDefinition
import spock.lang.Specification

class CustomizableRollbackTransactionAttributeSpec extends Specification {

    void "copy constructor deep-copies the rollback rule list instead of aliasing it"() {
        given:
        def source = new CustomizableRollbackTransactionAttribute()
        source.setRollbackRules([new RollbackRuleAttribute(IllegalStateException)])

        when:
        def copy = new CustomizableRollbackTransactionAttribute((RuleBasedTransactionAttribute) source)
        copy.getRollbackRules().add(new NoRollbackRuleAttribute(IllegalArgumentException))

        then: "mutating the copy's rule list does not affect the source's list"
        source.getRollbackRules().size() == 1
        copy.getRollbackRules().size() == 2
    }

    void "copy constructor preserves qualifier, labels, and connection metadata"() {
        given:
        def source = new CustomizableRollbackTransactionAttribute()
        source.setQualifier('secondary')
        source.setLabels(['audited'])
        source.setConnection('secondary')
        source.setInheritRollbackOnly(false)

        when:
        def copy = new CustomizableRollbackTransactionAttribute(source)

        then:
        copy.getQualifier() == 'secondary'
        copy.getLabels() as Set == ['audited'] as Set
        copy.getConnection() == 'secondary'
        !copy.isInheritRollbackOnly()
    }

    void "copy constructor from a plain RuleBasedTransactionAttribute copies its rollback rules"() {
        given:
        def source = new RuleBasedTransactionAttribute()
        source.setRollbackRules([new RollbackRuleAttribute(RuntimeException)])

        when:
        def copy = new CustomizableRollbackTransactionAttribute(source)

        then:
        copy.getRollbackRules().size() == 1
        !copy.getRollbackRules().is(source.getRollbackRules())
    }

    void "copy constructor from a plain TransactionDefinition copies only definition-level properties"() {
        given: "a source that is neither a TransactionAttribute nor a RuleBasedTransactionAttribute"
        TransactionDefinition source = new DefaultTransactionDefinition().tap {
            propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
            isolationLevel = TransactionDefinition.ISOLATION_SERIALIZABLE
            timeout = 42
            readOnly = true
            name = 'plainDefinition'
        }

        when:
        def copy = new CustomizableRollbackTransactionAttribute(source)

        then: "the base TransactionDefinition properties are copied"
        copy.getPropagationBehavior() == TransactionDefinition.PROPAGATION_REQUIRES_NEW
        copy.getIsolationLevel() == TransactionDefinition.ISOLATION_SERIALIZABLE
        copy.getTimeout() == 42
        copy.isReadOnly()
        copy.getName() == 'plainDefinition'

        and: "attribute-only metadata that TransactionDefinition doesn't expose is left at its default"
        copy.getQualifier() == null
        !copy.getLabels()
        !copy.getRollbackRules()
        copy.getConnection() == null
        copy.isInheritRollbackOnly()
    }
}

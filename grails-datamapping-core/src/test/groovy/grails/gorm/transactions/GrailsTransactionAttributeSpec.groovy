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
package grails.gorm.transactions

import groovy.transform.CompileStatic

import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.interceptor.DefaultTransactionAttribute
import org.springframework.transaction.interceptor.NoRollbackRuleAttribute
import org.springframework.transaction.interceptor.RollbackRuleAttribute
import org.springframework.transaction.interceptor.RuleBasedTransactionAttribute
import org.springframework.transaction.interceptor.TransactionAttribute
import org.springframework.transaction.support.DefaultTransactionDefinition
import spock.lang.Specification

class GrailsTransactionAttributeSpec extends Specification {

    void "copy constructor deep-copies the rollback rule list instead of aliasing it"() {
        given:
        def sourceRules = [new RollbackRuleAttribute(IllegalStateException)]
        def source = new GrailsTransactionAttribute()
        source.setRollbackRules(sourceRules)

        when:
        def copy = new GrailsTransactionAttribute(source)
        copy.getRollbackRules().add(new NoRollbackRuleAttribute(IllegalArgumentException))

        then: "mutating the copy's rule list does not affect the source's list"
        source.getRollbackRules().size() == 1
        copy.getRollbackRules().size() == 2

        when: "the source's rule list is mutated"
        source.getRollbackRules().add(new RollbackRuleAttribute(UnsupportedOperationException))

        then: "the copy's rule list is unaffected"
        source.getRollbackRules().size() == 2
        copy.getRollbackRules().size() == 2

        and: "copying did not replace the source's internal rule list"
        source.getRollbackRules().is(sourceRules)
    }

    void "copy constructor deep-copies the labels collection instead of aliasing it"() {
        given:
        def sourceLabels = ['audited']
        def source = new GrailsTransactionAttribute()
        source.setLabels(sourceLabels)

        when:
        def copy = new GrailsTransactionAttribute(source)
        copy.getLabels().add('copy-only')

        then: "mutating the copy's labels does not affect the source's labels"
        source.getLabels() as List == ['audited']
        copy.getLabels() as List == ['audited', 'copy-only']

        when: "the source's labels are mutated"
        sourceLabels.add('source-only')

        then: "the copy's labels are unaffected"
        source.getLabels() as List == ['audited', 'source-only']
        copy.getLabels() as List == ['audited', 'copy-only']

        and: "copying did not replace the source's internal labels collection"
        source.getLabels().is(sourceLabels)
    }

    void "copy constructor preserves qualifier, labels and inheritRollbackOnly metadata"() {
        given:
        def source = new GrailsTransactionAttribute()
        source.setQualifier('secondary')
        source.setLabels(['audited'])
        source.setInheritRollbackOnly(false)

        when:
        def copy = new GrailsTransactionAttribute(source)

        then:
        copy.getQualifier() == 'secondary'
        copy.getLabels() as Set == ['audited'] as Set
        !copy.isInheritRollbackOnly()
    }

    void "copy constructor preserves all definition-level properties"() {
        given:
        def source = new GrailsTransactionAttribute()
        source.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW)
        source.setIsolationLevel(TransactionDefinition.ISOLATION_SERIALIZABLE)
        source.setTimeout(42)
        source.setReadOnly(true)
        source.setName('sourceTx')

        when:
        def copy = new GrailsTransactionAttribute(source)

        then:
        copy.getPropagationBehavior() == TransactionDefinition.PROPAGATION_REQUIRES_NEW
        copy.getIsolationLevel() == TransactionDefinition.ISOLATION_SERIALIZABLE
        copy.getTimeout() == 42
        copy.isReadOnly()
        copy.getName() == 'sourceTx'
    }

    void "copy constructor preserves descriptor and timeoutString"() {
        given:
        def source = new GrailsTransactionAttribute()
        source.setDescriptor('BookService.save')
        source.setTimeoutString('${tx.timeout}')

        when:
        def copy = new GrailsTransactionAttribute(source)

        then:
        copy.getDescriptor() == 'BookService.save'
        copy.getTimeoutString() == '${tx.timeout}'
    }

    void "copy constructor from a plain RuleBasedTransactionAttribute copies its rollback rules defensively"() {
        given:
        def source = new RuleBasedTransactionAttribute()
        source.setRollbackRules([new RollbackRuleAttribute(RuntimeException)])
        source.setQualifier('books')
        source.setLabels(['audited'])

        when:
        def copy = new GrailsTransactionAttribute(source)

        then:
        copy.getRollbackRules().size() == 1
        !copy.getRollbackRules().is(source.getRollbackRules())
        copy.getQualifier() == 'books'
        copy.getLabels() as List == ['audited']
        !copy.getLabels().is(source.getLabels())
        copy.isInheritRollbackOnly()
    }

    void "a NoRollbackRuleAttribute on the source is honored by the copy's rollbackOn"() {
        given:
        def source = new RuleBasedTransactionAttribute()
        source.setRollbackRules([new NoRollbackRuleAttribute(TestBusinessException)])

        when:
        def copy = new GrailsTransactionAttribute(source)

        then: "a matching exception does not trigger rollback"
        !copy.rollbackOn(new TestBusinessException())

        and: "a non-matching exception still triggers the default rollback-everything behavior"
        copy.rollbackOn(new IllegalStateException())
        copy.rollbackOn(new Exception())
    }

    void "rollbackOn applies the deepest matching rule"() {
        given:
        def attribute = new GrailsTransactionAttribute()
        attribute.setRollbackRules([
                new NoRollbackRuleAttribute(RuntimeException),
                new RollbackRuleAttribute(TestBusinessException)
        ])

        expect: "the rule closest to the thrown exception type wins"
        attribute.rollbackOn(new TestBusinessException())
        !attribute.rollbackOn(new IllegalStateException())
    }

    void "rollbackOn rolls back on any exception when no rules are configured"() {
        given:
        def attribute = new GrailsTransactionAttribute()

        expect: "unchecked and checked exceptions both roll back, unlike Spring's default"
        attribute.rollbackOn(new RuntimeException())
        attribute.rollbackOn(new Exception())
        attribute.rollbackOn(new Error())
    }

    void "statically dispatched TransactionDefinition copy still propagates rules and Grails state from the dynamic type"() {
        given: "a GrailsTransactionAttribute passed around as a plain TransactionDefinition"
        def rules = [new NoRollbackRuleAttribute(TestBusinessException)]
        def source = new GrailsTransactionAttribute()
        source.setRollbackRules(rules)
        source.setInheritRollbackOnly(false)
        source.setQualifier('books')
        source.setLabels(['audited'])

        when: "copied through the statically chosen TransactionDefinition constructor"
        def copy = copyAsTransactionDefinition(source)

        then:
        !copy.rollbackOn(new TestBusinessException())
        !copy.isInheritRollbackOnly()
        copy.getQualifier() == 'books'
        copy.getLabels() as List == ['audited']

        and: "the copy's rule list is independent of the source's"
        !copy.getRollbackRules().is(source.getRollbackRules())

        and: "the source's internal rule list was not replaced"
        source.getRollbackRules().is(rules)
    }

    void "statically dispatched TransactionAttribute copy still propagates rules from the dynamic type"() {
        given: "a RuleBasedTransactionAttribute passed around as a plain TransactionAttribute"
        def source = new RuleBasedTransactionAttribute()
        source.setRollbackRules([new NoRollbackRuleAttribute(TestBusinessException)])

        when: "copied through the statically chosen TransactionAttribute constructor"
        def copy = copyAsTransactionAttribute(source)

        then:
        !copy.rollbackOn(new TestBusinessException())
        !copy.getRollbackRules().is(source.getRollbackRules())
    }

    void "copy constructor from a non-rule-based DefaultTransactionAttribute copies qualifier and labels defensively"() {
        given:
        def sourceLabels = ['audited']
        def source = new DefaultTransactionAttribute()
        source.setQualifier('books')
        source.setLabels(sourceLabels)
        source.setTimeout(21)

        when:
        def copy = new GrailsTransactionAttribute((TransactionAttribute) source)
        copy.getLabels().add('copy-only')

        then:
        copy.getQualifier() == 'books'
        copy.getTimeout() == 21
        source.getLabels() as List == ['audited']
        source.getLabels().is(sourceLabels)
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
        def copy = new GrailsTransactionAttribute(source)

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
        copy.isInheritRollbackOnly()
    }

    @CompileStatic
    private static GrailsTransactionAttribute copyAsTransactionDefinition(TransactionDefinition source) {
        return new GrailsTransactionAttribute(source)
    }

    @CompileStatic
    private static GrailsTransactionAttribute copyAsTransactionAttribute(TransactionAttribute source) {
        return new GrailsTransactionAttribute(source)
    }

    static class TestBusinessException extends RuntimeException {
    }
}

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

package grails.orm

import groovy.lang.Closure
import groovy.lang.MissingMethodException
import org.grails.orm.hibernate.query.HibernateQuery
import org.hibernate.SessionFactory
import spock.lang.Specification

class CriteriaMethodInvokerSpec extends Specification {

    HibernateCriteriaBuilder builder = Mock(HibernateCriteriaBuilder)
    HibernateQuery query = Mock(HibernateQuery)
    CriteriaMethodInvoker invoker = new CriteriaMethodInvoker(builder)

    def setup() {
        builder.getHibernateQuery() >> query
        _ * builder.isPaginationEnabledList() >> false
    }

    void "test invokeMethod handles list call"() {
        given:
        def closure = { eq("foo", "bar") }

        when:
        invoker.invokeMethod("list", [closure] as Object[])

        then:
        1 * builder.isUniqueResult() >> false
        1 * builder.isDistinct() >> false
        1 * builder.isCount() >> false
        1 * query.list() >> []
        1 * builder.isParticipate() >> true
    }

    void "test invokeMethod handles get call"() {
        given:
        def closure = { eq("foo", "bar") }

        when:
        invoker.invokeMethod("get", [closure] as Object[])

        then:
        1 * builder.setUniqueResult(true)
        1 * builder.isUniqueResult() >> true
        1 * query.singleResult() >> null
        1 * builder.isParticipate() >> true
    }

    void "test invokeMethod handles count call"() {
        given:
        def closure = { eq("foo", "bar") }
        def projectionList = new org.grails.datastore.mapping.query.Query.ProjectionList()

        when:
        invoker.invokeMethod("count", [closure] as Object[])

        then:
        1 * builder.setCount(true)
        1 * builder.isUniqueResult() >> false
        1 * builder.isCount() >> true
        1 * query.projections() >> projectionList
        1 * query.singleResult() >> 0L
        1 * builder.isParticipate() >> true
    }

    void "test invokeMethod handles listDistinct call"() {
        given:
        def closure = { eq("foo", "bar") }

        when:
        invoker.invokeMethod("listDistinct", [closure] as Object[])

        then:
        1 * builder.setDistinct(true)
        1 * builder.isUniqueResult() >> false
        1 * builder.isDistinct() >> true
        1 * query.distinct()
        1 * query.list() >> []
        1 * builder.isParticipate() >> true
    }

    void "test invokeMethod handles pagination"() {
        given:
        def params = [max: 10, offset: 5]
        def closure = { }

        when:
        invoker.invokeMethod("list", [params, closure] as Object[])

        then:
        _ * builder.isPaginationEnabledList() >> false // initially false
        1 * builder.setPaginationEnabledList(true)
        1 * query.maxResults(10)
        1 * query.firstResult(5)
        1 * builder.isUniqueResult() >> false
        _ * builder.isPaginationEnabledList() >> true // then true
    }

    void "test invokeMethod handles criteria methods"() {
        when:
        invoker.invokeMethod("eq", ["prop", "value"] as Object[])

        then:
        _ * builder.isPaginationEnabledList() >> false
        _ * builder.getMetaClass() >> GroovySystem.metaClassRegistry.getMetaClass(HibernateCriteriaBuilder)
        1 * builder.eq("prop", "value")
    }

    void "test invokeMethod handles projections block"() {
        given:
        def closure = { sum("balance") }

        when:
        invoker.invokeMethod("projections", [closure] as Object[])

        then:
        _ * builder.isPaginationEnabledList() >> false
        1 * builder.getHibernateQuery() >> query
        // The projections block calls invokeClosureNode which delegates to the builder
    }

    void "test invokeMethod handles association query"() {
        given:
        def closure = { eq("amount", 10) }

        when:
        invoker.invokeMethod("transactions", [closure] as Object[])

        then:
        _ * builder.isPaginationEnabledList() >> false
        _ * builder.getTargetClass() >> InvokerAccount
        1 * builder.getSessionFactory() >> Mock(SessionFactory) {
            getMetamodel() >> Mock(jakarta.persistence.metamodel.Metamodel) {
                entity(InvokerAccount) >> Mock(jakarta.persistence.metamodel.EntityType) {
                    getAttribute("transactions") >> Mock(jakarta.persistence.metamodel.Attribute) {
                        isAssociation() >> true
                    }
                }
            }
        }
        1 * builder.getClassForAssociationType(_) >> InvokerTransaction
        1 * query.join("transactions", _)
        1 * query.in("transactions", _)
    }

    void "test invokeMethod handles and/or/not junctions"() {
        given:
        def closure = { eq("foo", "bar") }

        when:
        invoker.invokeMethod("and", [closure] as Object[])
        invoker.invokeMethod("or", [closure] as Object[])
        invoker.invokeMethod("not", [closure] as Object[])

        then:
        1 * builder.and(closure)
        1 * builder.or(closure)
        1 * builder.not(closure)
    }

    void "test invokeMethod throws MissingMethodException"() {
        when:
        invoker.invokeMethod("nonExistent", [] as Object[])

        then:
        _ * builder.isPaginationEnabledList() >> false
        _ * builder.getMetaClass() >> GroovySystem.metaClassRegistry.getMetaClass(HibernateCriteriaBuilder)
        thrown(MissingMethodException)
    }

    // ─── trySimpleCriteria ─────────────────────────────────────────────────
    // Both methods are protected, so the same-package spec calls them directly.

    void "trySimpleCriteria: idEq delegates to builder.eq('id', value)"() {
        when:
        invoker.trySimpleCriteria('idEq', CriteriaMethods.ID_EQUALS, [42L] as Object[])

        then:
        1 * builder.eq('id', 42L)
    }

    void "trySimpleCriteria: isNull with String delegates to hibernateQuery.isNull"() {
        when:
        invoker.trySimpleCriteria('isNull', CriteriaMethods.IS_NULL, ['branch'] as Object[])

        then:
        1 * builder.calculatePropertyName('branch') >> 'branch'
        1 * query.isNull('branch')
    }

    void "trySimpleCriteria: isNotNull with String delegates to hibernateQuery.isNotNull"() {
        when:
        invoker.trySimpleCriteria('isNotNull', CriteriaMethods.IS_NOT_NULL, ['branch'] as Object[])

        then:
        1 * builder.calculatePropertyName('branch') >> 'branch'
        1 * query.isNotNull('branch')
    }

    void "trySimpleCriteria: isEmpty with String delegates to hibernateQuery.isEmpty"() {
        when:
        invoker.trySimpleCriteria('isEmpty', CriteriaMethods.IS_EMPTY, ['transactions'] as Object[])

        then:
        1 * builder.calculatePropertyName('transactions') >> 'transactions'
        1 * query.isEmpty('transactions')
    }

    void "trySimpleCriteria: isNotEmpty with String delegates to hibernateQuery.isNotEmpty"() {
        when:
        invoker.trySimpleCriteria('isNotEmpty', CriteriaMethods.IS_NOT_EMPTY, ['transactions'] as Object[])

        then:
        1 * builder.calculatePropertyName('transactions') >> 'transactions'
        1 * query.isNotEmpty('transactions')
    }

    void "trySimpleCriteria: non-String arg to isNull calls throwRuntimeException"() {
        given:
        builder.throwRuntimeException(_ as RuntimeException) >> { throw it[0] }

        when:
        invoker.trySimpleCriteria('isNull', CriteriaMethods.IS_NULL, [42] as Object[])

        then:
        thrown(IllegalArgumentException)
    }

    void "trySimpleCriteria: null value returns UNHANDLED without touching builder"() {
        when:
        def result = invoker.trySimpleCriteria('isNull', CriteriaMethods.IS_NULL, [null] as Object[])

        then:
        result != null  // UNHANDLED sentinel object
        0 * builder.calculatePropertyName(_)
    }

    void "trySimpleCriteria: null method returns UNHANDLED"() {
        when:
        def result = invoker.trySimpleCriteria('unknown', null, ['x'] as Object[])

        then:
        result != null  // UNHANDLED sentinel
        0 * builder._
    }

    // ─── tryPropertyCriteria ───────────────────────────────────────────────

    void "tryPropertyCriteria: rlike delegates to builder.rlike"() {
        when:
        invoker.tryPropertyCriteria(CriteriaMethods.RLIKE, ['firstName', '^F.*'] as Object[])

        then:
        1 * builder.calculatePropertyName('firstName') >> 'firstName'
        1 * builder.rlike('firstName', '^F.*')
    }

    void "tryPropertyCriteria: between delegates to builder.between"() {
        when:
        invoker.tryPropertyCriteria(CriteriaMethods.BETWEEN, ['balance', 10, 100] as Object[])

        then:
        1 * builder.calculatePropertyName('balance') >> 'balance'
        1 * builder.between('balance', 10, 100)
    }

    void "tryPropertyCriteria: eq delegates to builder.eq"() {
        when:
        invoker.tryPropertyCriteria(CriteriaMethods.EQUALS, ['firstName', 'Fred'] as Object[])

        then:
        1 * builder.calculatePropertyName('firstName') >> 'firstName'
        1 * builder.eq('firstName', 'Fred')
    }

    void "tryPropertyCriteria: eq with Map params delegates to builder.eq(prop, val, map)"() {
        given:
        def params = [ignoreCase: true]

        when:
        invoker.tryPropertyCriteria(CriteriaMethods.EQUALS, ['firstName', 'Fred', params] as Object[])

        then:
        1 * builder.calculatePropertyName('firstName') >> 'firstName'
        1 * builder.eq('firstName', 'Fred', params)
    }

    void "tryPropertyCriteria: eqProperty delegates to builder.eqProperty"() {
        when:
        invoker.tryPropertyCriteria(CriteriaMethods.EQUALS_PROPERTY, ['firstName', 'lastName'] as Object[])

        then:
        1 * builder.calculatePropertyName('firstName') >> 'firstName'
        1 * builder.eqProperty('firstName', 'lastName')
    }

    void "tryPropertyCriteria: gt delegates to builder.gt"() {
        when:
        invoker.tryPropertyCriteria(CriteriaMethods.GREATER_THAN, ['balance', 100] as Object[])

        then:
        1 * builder.calculatePropertyName('balance') >> 'balance'
        1 * builder.gt('balance', 100)
    }

    void "tryPropertyCriteria: gtProperty delegates to builder.gtProperty"() {
        when:
        invoker.tryPropertyCriteria(CriteriaMethods.GREATER_THAN_PROPERTY, ['balance', 'balance'] as Object[])

        then:
        1 * builder.calculatePropertyName('balance') >> 'balance'
        1 * builder.gtProperty('balance', 'balance')
    }

    void "tryPropertyCriteria: ge delegates to builder.ge"() {
        when:
        invoker.tryPropertyCriteria(CriteriaMethods.GREATER_THAN_OR_EQUAL, ['balance', 100] as Object[])

        then:
        1 * builder.calculatePropertyName('balance') >> 'balance'
        1 * builder.ge('balance', 100)
    }

    void "tryPropertyCriteria: geProperty delegates to builder.geProperty"() {
        when:
        invoker.tryPropertyCriteria(CriteriaMethods.GREATER_THAN_OR_EQUAL_PROPERTY, ['balance', 'balance'] as Object[])

        then:
        1 * builder.calculatePropertyName('balance') >> 'balance'
        1 * builder.geProperty('balance', 'balance')
    }

    void "tryPropertyCriteria: ilike delegates to builder.ilike"() {
        when:
        invoker.tryPropertyCriteria(CriteriaMethods.ILIKE, ['firstName', 'fr%'] as Object[])

        then:
        1 * builder.calculatePropertyName('firstName') >> 'firstName'
        1 * builder.ilike('firstName', 'fr%')
    }

    void "tryPropertyCriteria: in with Collection delegates to builder.in"() {
        given:
        def names = ['Fred', 'Barney']

        when:
        invoker.tryPropertyCriteria(CriteriaMethods.IN, ['firstName', names] as Object[])

        then:
        1 * builder.calculatePropertyName('firstName') >> 'firstName'
        1 * builder.in('firstName', names)
    }

    void "tryPropertyCriteria: in with Object[] delegates to builder.in"() {
        given:
        def names = ['Fred', 'Barney'] as Object[]

        when:
        invoker.tryPropertyCriteria(CriteriaMethods.IN, ['firstName', names] as Object[])

        then:
        1 * builder.calculatePropertyName('firstName') >> 'firstName'
        1 * builder.in('firstName', names)
    }

    void "tryPropertyCriteria: lt delegates to builder.lt"() {
        when:
        invoker.tryPropertyCriteria(CriteriaMethods.LESS_THAN, ['balance', 500] as Object[])

        then:
        1 * builder.calculatePropertyName('balance') >> 'balance'
        1 * builder.lt('balance', 500)
    }

    void "tryPropertyCriteria: ltProperty delegates to builder.ltProperty"() {
        when:
        invoker.tryPropertyCriteria(CriteriaMethods.LESS_THAN_PROPERTY, ['balance', 'balance'] as Object[])

        then:
        1 * builder.calculatePropertyName('balance') >> 'balance'
        1 * builder.ltProperty('balance', 'balance')
    }

    void "tryPropertyCriteria: le delegates to builder.le"() {
        when:
        invoker.tryPropertyCriteria(CriteriaMethods.LESS_THAN_OR_EQUAL, ['balance', 500] as Object[])

        then:
        1 * builder.calculatePropertyName('balance') >> 'balance'
        1 * builder.le('balance', 500)
    }

    void "tryPropertyCriteria: leProperty delegates to builder.leProperty"() {
        when:
        invoker.tryPropertyCriteria(CriteriaMethods.LESS_THAN_OR_EQUAL_PROPERTY, ['balance', 'balance'] as Object[])

        then:
        1 * builder.calculatePropertyName('balance') >> 'balance'
        1 * builder.leProperty('balance', 'balance')
    }

    void "tryPropertyCriteria: like delegates to builder.like"() {
        when:
        invoker.tryPropertyCriteria(CriteriaMethods.LIKE, ['firstName', 'Fr%'] as Object[])

        then:
        1 * builder.calculatePropertyName('firstName') >> 'firstName'
        1 * builder.like('firstName', 'Fr%')
    }

    void "tryPropertyCriteria: ne delegates to builder.ne"() {
        when:
        invoker.tryPropertyCriteria(CriteriaMethods.NOT_EQUAL, ['firstName', 'Fred'] as Object[])

        then:
        1 * builder.calculatePropertyName('firstName') >> 'firstName'
        1 * builder.ne('firstName', 'Fred')
    }

    void "tryPropertyCriteria: neProperty delegates to builder.neProperty"() {
        when:
        invoker.tryPropertyCriteria(CriteriaMethods.NOT_EQUAL_PROPERTY, ['firstName', 'lastName'] as Object[])

        then:
        1 * builder.calculatePropertyName('firstName') >> 'firstName'
        1 * builder.neProperty('firstName', 'lastName')
    }

    void "tryPropertyCriteria: sizeEq delegates to builder.sizeEq"() {
        when:
        invoker.tryPropertyCriteria(CriteriaMethods.SIZE_EQUALS, ['transactions', 2] as Object[])

        then:
        1 * builder.calculatePropertyName('transactions') >> 'transactions'
        1 * builder.sizeEq('transactions', 2)
    }

    void "tryPropertyCriteria: null method returns UNHANDLED"() {
        when:
        def result = invoker.tryPropertyCriteria(null, ['x', 'y'] as Object[])

        then:
        result != null  // UNHANDLED sentinel
        0 * builder._
    }

    void "tryPropertyCriteria: non-String first arg returns UNHANDLED"() {
        when:
        def result = invoker.tryPropertyCriteria(CriteriaMethods.EQUALS, [42, 'Fred'] as Object[])

        then:
        result != null  // UNHANDLED sentinel
        0 * builder._
    }
}

class InvokerAccount {
    String firstName
    Set<InvokerTransaction> transactions
}
class InvokerTransaction {}


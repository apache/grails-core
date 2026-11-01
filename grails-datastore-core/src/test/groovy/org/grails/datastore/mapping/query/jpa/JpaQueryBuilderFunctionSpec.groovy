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
package org.grails.datastore.mapping.query.jpa

import jakarta.persistence.Entity
import jakarta.persistence.Id
import org.grails.datastore.mapping.keyvalue.mapping.config.KeyValueMappingContext
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.query.Projections
import org.grails.datastore.mapping.query.Query
import org.grails.datastore.mapping.query.api.QueryableCriteria
import org.grails.datastore.mapping.query.criteria.FunctionCallingCriterion
import org.springframework.dao.InvalidDataAccessResourceUsageException
import spock.lang.Specification
import spock.lang.Unroll

class JpaQueryBuilderFunctionSpec extends Specification {

    PersistentEntity entity

    def setup() {
        def context = new KeyValueMappingContext('test')
        entity = context.addPersistentEntity(JpaFunctionTestPerson)
    }

    @Unroll
    void "FunctionCallingCriterion renders #expectedSql for #criterion.class.simpleName"() {
        given:
        def criteria = [new FunctionCallingCriterion(functionName, criterion)]

        when:
        def info = new JpaQueryBuilder(entity, criteria).buildDelete()

        then:
        info.query == "DELETE FROM ${JpaFunctionTestPerson.name} jpaFunctionTestPerson WHERE (${expectedSql})"
        info.parameters == expectedParams

        where:
        functionName | criterion                                    || expectedSql                                                | expectedParams
        'upper'      | new Query.Equals('name', 'BOB')              || 'upper(jpaFunctionTestPerson.name) = :p1'                  | ['BOB']
        'upper'      | new Query.NotEquals('name', 'BOB')           || 'upper(jpaFunctionTestPerson.name) != :p1'                 | ['BOB']
        'year'       | new Query.GreaterThan('age', 2000)           || 'year(jpaFunctionTestPerson.age) > :p1'                    | [2000]
        'year'       | new Query.GreaterThanEquals('age', 2000)     || 'year(jpaFunctionTestPerson.age) >= :p1'                   | [2000]
        'year'       | new Query.LessThan('age', 2000)              || 'year(jpaFunctionTestPerson.age) < :p1'                    | [2000]
        'year'       | new Query.LessThanEquals('age', 2000)        || 'year(jpaFunctionTestPerson.age) <= :p1'                   | [2000]
        'lower'      | new Query.Like('name', 'bob%')               || 'lower(jpaFunctionTestPerson.name) LIKE :p1'               | ['bob%']
        'upper'      | new Query.ILike('name', 'bob%')              || 'lower(upper(jpaFunctionTestPerson.name)) like lower(:p1)' | ['bob%']
        'upper'      | new Query.Equals('name', null)               || 'upper(jpaFunctionTestPerson.name) IS NULL'                | []
        'upper'      | new Query.NotEquals('name', null)            || 'upper(jpaFunctionTestPerson.name) IS NOT NULL'            | []
        'upper'      | new Query.In('name', ['A'])                  || 'upper(jpaFunctionTestPerson.name) IN (:p1)'               | ['A']
        'upper'      | new Query.In('name', ['A', 'B', 'C'])        || 'upper(jpaFunctionTestPerson.name) IN (:p1,:p2,:p3)'       | ['A', 'B', 'C']
    }

    void "FunctionCallingCriterion with In subquery renders subselect and advances parameter positions"() {
        given:
        QueryableCriteria subquery = Stub(QueryableCriteria) {
            getPersistentEntity() >> entity
            getProjections() >> [Projections.property('name')]
            getCriteria() >> [new Query.Equals('name', 'Alice'), new Query.GreaterThan('age', 21)]
        }
        subquery.materializeLazyCriteria() >> subquery
        def criteria = [
            new FunctionCallingCriterion('upper', new Query.In('name', subquery)),
            new FunctionCallingCriterion('lower', new Query.Equals('name', 'bob'))
        ]

        when:
        def info = new JpaQueryBuilder(entity, criteria).buildUpdate(age: 30)

        then:
        info.query == "UPDATE ${JpaFunctionTestPerson.name} jpaFunctionTestPerson SET jpaFunctionTestPerson.age=:p1 WHERE (upper(jpaFunctionTestPerson.name) IN (SELECT jpaFunctionTestPerson1.name FROM ${JpaFunctionTestPerson.name} jpaFunctionTestPerson1 WHERE jpaFunctionTestPerson1.name=:p2 AND jpaFunctionTestPerson1.age > :p3) AND lower(jpaFunctionTestPerson.name) = :p4)"
        info.parameters == [30, 'Alice', 21, 'bob']
    }

    void "FunctionCallingCriterion throws when function is on value"() {
        given:
        def criteria = [new FunctionCallingCriterion('name', 'upper', new Query.Equals('name', 'BOB'), true)]

        when:
        new JpaQueryBuilder(entity, criteria).buildDelete()

        then:
        thrown(InvalidDataAccessResourceUsageException)
    }

    void "FunctionCallingCriterion throws on unsupported wrapped criterion"() {
        given:
        def criteria = [new FunctionCallingCriterion('upper', new Query.Between('age', 1, 10))]

        when:
        new JpaQueryBuilder(entity, criteria).buildDelete()

        then:
        thrown(InvalidDataAccessResourceUsageException)
    }

    void "FunctionCallingCriterion throws on non-existent property"() {
        given:
        def criteria = [new FunctionCallingCriterion('upper', new Query.Equals('nonExistent', 'BOB'))]

        when:
        new JpaQueryBuilder(entity, criteria).buildDelete()

        then:
        thrown(InvalidDataAccessResourceUsageException)
    }

    void "JpaQueryBuilder(QueryableCriteria) materializes deferred criteria on construction and in subqueries"() {
        given:
        QueryableCriteria materializedRoot = Stub(QueryableCriteria) {
            getPersistentEntity() >> entity
            getCriteria() >> [new Query.Equals('name', 'Alice')]
        }
        QueryableCriteria lazyRoot = Stub(QueryableCriteria) {
            getPersistentEntity() >> entity
            getCriteria() >> []
            materializeLazyCriteria() >> materializedRoot
        }

        when:
        def info = new JpaQueryBuilder(lazyRoot).buildDelete()

        then:
        info.query == "DELETE FROM ${JpaFunctionTestPerson.name} jpaFunctionTestPerson WHERE (jpaFunctionTestPerson.name=:p1)"
        info.parameters == ['Alice']
    }

    void "QueryableCriteria default methods return self and do nothing"() {
        given:
        QueryableCriteria criteria = [:] as QueryableCriteria

        when:
        criteria.applyLazyCriteria()
        QueryableCriteria materialized = criteria.materializeLazyCriteria()

        then:
        materialized.is(criteria)
    }
}

@Entity
class JpaFunctionTestPerson {
    @Id
    Long id
    String name
    Integer age
}

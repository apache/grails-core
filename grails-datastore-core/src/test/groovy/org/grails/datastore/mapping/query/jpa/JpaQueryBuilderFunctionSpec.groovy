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

    void "a subquery without criteria has no WHERE, so the statement parses"() {
        given:
        QueryableCriteria subquery = Stub(QueryableCriteria) {
            getPersistentEntity() >> entity
            getProjections() >> [Projections.property('name')]
            getCriteria() >> []
        }

        when:
        def info = new JpaQueryBuilder(entity, [new FunctionCallingCriterion('upper', new Query.In('name', subquery))]).buildDelete()

        then:
        info.query == "DELETE FROM ${JpaFunctionTestPerson.name} jpaFunctionTestPerson WHERE (upper(jpaFunctionTestPerson.name) IN (SELECT jpaFunctionTestPerson0.name FROM ${JpaFunctionTestPerson.name} jpaFunctionTestPerson0))"
        info.parameters == []
    }

    @Unroll
    void "a function with a one-row subquery as its value renders the subquery for operator #operator"() {
        given:
        QueryableCriteria subquery = Stub(QueryableCriteria) {
            getPersistentEntity() >> entity
            getProjections() >> [Projections.max('age')]
            getCriteria() >> [new Query.Equals('name', 'Alice')]
        }

        when:
        def info = new JpaQueryBuilder(entity, [new FunctionCallingCriterion('year', criterion(subquery))]).buildDelete()

        then: 'the subquery is part of the statement and its parameter is bound, not the criteria object'
        info.query == "DELETE FROM ${JpaFunctionTestPerson.name} jpaFunctionTestPerson WHERE (year(jpaFunctionTestPerson.age)${operator}(SELECT MAX(jpaFunctionTestPerson0.age) FROM ${JpaFunctionTestPerson.name} jpaFunctionTestPerson0 WHERE jpaFunctionTestPerson0.name=:p1))"
        info.parameters == ['Alice']

        where:
        criterion << [
            { new Query.Equals('age', it) },
            { new Query.NotEquals('age', it) },
            { new Query.GreaterThan('age', it) },
            { new Query.LessThanEquals('age', it) }
        ]
        operator << [' = ', ' != ', ' > ', ' <= ']
    }

    void "a function over an rlike is rejected in a bulk statement, as a plain rlike is, and not written as LIKE"() {
        when:
        new JpaQueryBuilder(entity, [new FunctionCallingCriterion('lower', new Query.RLike('name', '^r.x$'))]).buildDelete()

        then:
        thrown(InvalidDataAccessResourceUsageException)

        when:
        new JpaQueryBuilder(entity, [new Query.RLike('name', '^r.x$')]).buildDelete()

        then:
        thrown(InvalidDataAccessResourceUsageException)
    }

    @Unroll
    void "the function name '#functionName' is rejected before it reaches the statement"() {
        when:
        new JpaQueryBuilder(entity, [new FunctionCallingCriterion(functionName, new Query.Equals('name', 'x'))]).buildUpdate(age: 30)

        then:
        thrown(InvalidDataAccessResourceUsageException)

        where:
        functionName << ["upper(name) = name OR 1=1 OR upper", 'concat', 'LOWER', '', 'lower)', 'x y']
    }

    @Unroll
    void "the function #functionName that the where DSL supports is accepted"() {
        when:
        def info = new JpaQueryBuilder(entity, [new FunctionCallingCriterion(functionName, new Query.Equals('name', 'x'))]).buildDelete()

        then:
        info.query.contains("${functionName}(jpaFunctionTestPerson.name)")

        where:
        functionName << ['lower', 'upper', 'trim', 'length', 'second', 'hour', 'minute', 'day', 'month', 'year']
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
}

@Entity
class JpaFunctionTestPerson {
    @Id
    Long id
    String name
    Integer age
}

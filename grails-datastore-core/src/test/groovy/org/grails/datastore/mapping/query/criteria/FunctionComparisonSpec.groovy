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
package org.grails.datastore.mapping.query.criteria

import grails.gorm.DetachedCriteria
import org.grails.datastore.mapping.query.Query
import org.springframework.core.convert.support.DefaultConversionService
import org.springframework.dao.InvalidDataAccessResourceUsageException
import spock.lang.Specification
import spock.lang.Unroll

import org.grails.datastore.mapping.query.criteria.FunctionComparison.Kind
import org.grails.datastore.mapping.query.criteria.FunctionComparison.Operator

class FunctionComparisonSpec extends Specification {

    private static final conversion = new DefaultConversionService()

    private static FunctionComparison of(String function, Query.PropertyCriterion wrapped, boolean withService = true) {
        FunctionComparison.of(new FunctionCallingCriterion(function, wrapped), withService ? conversion : null)
    }

    @Unroll
    void "#function #wrapped.class.simpleName of #value is a #kind comparison on #operator with operand #expected"() {
        when:
        def comparison = of(function, wrapped)

        then:
        comparison.kind == kind
        comparison.operator == operator
        comparison.operand == expected
        comparison.operand?.getClass() == expected?.getClass()

        where:
        function | wrapped                                    | value || kind          | operator    | expected
        'length' | new Query.Equals('name', 3)                | 3     || Kind.COMPARE  | Operator.EQ | 3
        'length' | new Query.Equals('name', '3')              | '3'   || Kind.COMPARE  | Operator.EQ | 3
        'length' | new Query.Equals('name', 3L)               | 3L    || Kind.COMPARE  | Operator.EQ | 3
        'length' | new Query.Equals('name', 3.0G)             | 3.0G  || Kind.COMPARE  | Operator.EQ | 3
        'length' | new Query.GreaterThan('name', 3.5d)        | 3.5d  || Kind.COMPARE  | Operator.GT | 3.5d
        'length' | new Query.LessThanEquals('name', 3.5G)     | 3.5G  || Kind.COMPARE  | Operator.LE | 3.5G
        'length' | new Query.NotEquals('name', 5000000000L)   | 5000000000L || Kind.COMPARE | Operator.NE | 5000000000L
        'length' | new Query.GreaterThan('name', "${2 + 1}")  | '3'   || Kind.COMPARE  | Operator.GT | 3
        'upper'  | new Query.Equals('name', "A${'B'}")        | 'AB'  || Kind.COMPARE  | Operator.EQ | 'AB'
        'upper'  | new Query.Equals('name', 3.5d)             | 3.5d  || Kind.COMPARE  | Operator.EQ | '3.5'
        'lower'  | new Query.NotEquals('name', 7)             | 7     || Kind.COMPARE  | Operator.NE | '7'
        'trim'   | new Query.Like('name', 'a%')               | 'a%'  || Kind.COMPARE  | Operator.LIKE | 'a%'
        'upper'  | new Query.ILike('name', 'a%')              | 'a%'  || Kind.ILIKE    | null        | 'a%'
        'upper'  | new Query.RLike('name', '^a')              | '^a'  || Kind.RLIKE    | null        | '^a'
    }

    void "a string function takes a number as text even when no conversion service is given"() {
        expect:
        of('upper', new Query.Equals('name', 3.5d), false).operand == '3.5'
        of('length', new Query.Equals('name', "${3}"), false).operand == '3'
    }

    void "a text operand is the text, not the GString object"() {
        when:
        def operand = of('upper', new Query.Equals('name', "x${1}")).operand

        then:
        operand instanceof String
        operand == 'x1'
    }

    @Unroll
    void "an equality with #value is #kind"() {
        expect:
        of('upper', wrapped).kind == kind

        where:
        wrapped                         | value || kind
        new Query.Equals('name', null)    | null  || Kind.IS_NULL
        new Query.NotEquals('name', null) | null  || Kind.IS_NOT_NULL
    }

    void "a list is converted value by value and keeps a fraction"() {
        when:
        def comparison = of('length', new Query.In('name', [1, '2', 3L, 3.5d]))

        then:
        comparison.kind == Kind.IN_VALUES
        comparison.values == [1, 2, 3, 3.5d]
        comparison.values.collect { FunctionComparison.needsLiteral(it) } == [false, false, false, true]
    }

    void "a subquery stays a subquery whatever the function and the comparison"() {
        given:
        def subquery = new DetachedCriteria(Object)

        when:
        def inList = of('length', new Query.In('name', subquery))
        def compared = of('length', new Query.GreaterThan('name', subquery))

        then:
        inList.kind == Kind.IN_SUBQUERY
        inList.subquery.is(subquery)
        compared.kind == Kind.COMPARE_SUBQUERY
        compared.operator == Operator.GT
        compared.subquery.is(subquery)
        compared.operand == null
    }

    void "a criterion that cannot be compared with a function is rejected, and so is a function on the value"() {
        when:
        of('upper', new Query.Between('name', 'A', 'Z'))

        then:
        thrown(FunctionComparison.UnsupportedComparisonException)

        when:
        FunctionComparison.of(new FunctionCallingCriterion('name', 'upper', new Query.Equals('name', 'x'), true), conversion)

        then:
        thrown(FunctionComparison.UnsupportedComparisonException)
    }

    void "an unsupported function name is rejected before anything else"() {
        when:
        of('md5', new Query.Equals('name', 'x'))

        then:
        def e = thrown(InvalidDataAccessResourceUsageException)
        !(e instanceof FunctionComparison.UnsupportedComparisonException)
    }

    @Unroll
    void "#operator renders as #jpql"() {
        expect:
        operator.jpql() == jpql

        where:
        operator     | jpql
        Operator.EQ  | ' = '
        Operator.NE  | ' != '
        Operator.GT  | ' > '
        Operator.GE  | ' >= '
        Operator.LT  | ' < '
        Operator.LE  | ' <= '
        Operator.LIKE | ' LIKE '
    }
}

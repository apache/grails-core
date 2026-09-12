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
package org.grails.datastore.mapping.query

import spock.lang.Specification
import spock.lang.Unroll

import org.grails.datastore.mapping.query.api.QueryableCriteria

class RestrictionsAndProjectionsSpec extends Specification {

    @Unroll
    void "Restrictions.#method builds a #type.simpleName"() {
        when:
        Query.Criterion criterion = Restrictions."$method"(*args)

        then:
        type.isInstance(criterion)
        criterion.property == args[0]

        where:
        method       | args              | type
        'eq'         | ['a', 1]          | Query.Equals
        'ne'         | ['a', 1]          | Query.NotEquals
        'in'         | ['a', [1]]        | Query.In
        'like'       | ['a', 'x%']       | Query.Like
        'ilike'      | ['a', 'x%']       | Query.ILike
        'rlike'      | ['a', 'x.*']      | Query.RLike
        'between'    | ['a', 1, 2]       | Query.Between
        'gt'         | ['a', 1]          | Query.GreaterThan
        'lt'         | ['a', 1]          | Query.LessThan
        'gte'        | ['a', 1]          | Query.GreaterThanEquals
        'lte'        | ['a', 1]          | Query.LessThanEquals
        'isNull'     | ['a']             | Query.IsNull
        'isEmpty'    | ['a']             | Query.IsEmpty
        'isNotEmpty' | ['a']             | Query.IsNotEmpty
        'isNotNull'  | ['a']             | Query.IsNotNull
        'sizeEq'     | ['a', 1]          | Query.SizeEquals
        'sizeGt'     | ['a', 1]          | Query.SizeGreaterThan
        'sizeGe'     | ['a', 1]          | Query.SizeGreaterThanEquals
        'sizeLe'     | ['a', 1]          | Query.SizeLessThanEquals
        'sizeLt'     | ['a', 1]          | Query.SizeLessThan
        'sizeNe'     | ['a', 1]          | Query.SizeNotEquals
        'eqProperty' | ['a', 'b']        | Query.EqualsProperty
        'neProperty' | ['a', 'b']        | Query.NotEqualsProperty
        'gtProperty' | ['a', 'b']        | Query.GreaterThanProperty
        'geProperty' | ['a', 'b']        | Query.GreaterThanEqualsProperty
        'ltProperty' | ['a', 'b']        | Query.LessThanProperty
        'leProperty' | ['a', 'b']        | Query.LessThanEqualsProperty
    }

    void "subquery and identifier restrictions carry their arguments"() {
        given:
        QueryableCriteria subquery = Stub(QueryableCriteria)

        expect:
        Restrictions.idEq(5).value == 5
        Restrictions.idEq(5).property == 'id'
        Restrictions.in('a', subquery).subquery.is(subquery)
        Restrictions.notIn('a', subquery).subquery.is(subquery)
        Restrictions.eq('a', 1).value == 1
        Restrictions.between('a', 1, 2).from == 1
        Restrictions.between('a', 1, 2).to == 2
        Restrictions.eqProperty('a', 'b').otherProperty == 'b'
    }

    void "and and or combine two criteria into junctions"() {
        given:
        Query.Criterion a = Restrictions.eq('a', 1)
        Query.Criterion b = Restrictions.eq('b', 2)

        when:
        Query.Criterion and = Restrictions.and(a, b)
        Query.Criterion or = Restrictions.or(a, b)

        then:
        and instanceof Query.Conjunction
        and.criteria == [a, b]
        or instanceof Query.Disjunction
        or.criteria == [a, b]
    }

    void "projection factories return the projection types"() {
        expect:
        Projections.id().is(Projections.ID_PROJECTION)
        Projections.count().is(Projections.COUNT_PROJECTION)
        Projections.property('a').propertyName == 'a'
        Projections.sum('a') instanceof Query.SumProjection
        Projections.min('a') instanceof Query.MinProjection
        Projections.max('a') instanceof Query.MaxProjection
        Projections.avg('a') instanceof Query.AvgProjection
        Projections.distinct() instanceof Query.DistinctProjection
        Projections.distinct('a') instanceof Query.DistinctPropertyProjection
        Projections.distinct('a').propertyName == 'a'
        Projections.countDistinct('a') instanceof Query.CountDistinctProjection
        Projections.groupProperty('a') instanceof Query.GroupPropertyProjection
    }
}

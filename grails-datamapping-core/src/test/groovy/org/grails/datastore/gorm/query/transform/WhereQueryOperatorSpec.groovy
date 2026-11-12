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
package org.grails.datastore.gorm.query.transform

import org.grails.datastore.mapping.query.Query

import spock.lang.Specification

/**
 * {@link DetachedCriteriaTransformer} rewrites the body of a {@code where}/{@code find}/{@code findAll}
 * closure into calls on the enclosing {@link grails.gorm.DetachedCriteria}. A static field on a domain
 * class initialised from {@code Domain.where { ... }} is rewritten by the transform into
 * {@code new DetachedCriteria(Domain).build(closure)} directly (see
 * {@code DetachedCriteriaTransformer#visitField}), so the resulting static field can be built and
 * inspected entirely in memory - no live datastore is required because the criterion methods used here
 * (eq, gt, between, sizeEq, and, or, ...) only append plain {@link Query.Criterion} instances; they never
 * touch a {@code PersistentEntity} or a connected datastore. This lets these specs assert on the exact
 * criterion objects produced, rather than only on compilation succeeding.
 */
class WhereQueryOperatorSpec extends Specification {

    // The domain class name must be unique across the test JVM because
    // AstPropertyResolveUtils caches resolved properties statically by class name
    private static final String SOURCE = '''
import grails.gorm.DetachedCriteria
import grails.gorm.annotation.Entity

@Entity
class OpQueryBook {
    String title
    BigDecimal price
    Integer minStock
    Integer maxStock
    List<String> tags
    Date published

    static DetachedCriteria<OpQueryBook> eqQuery = OpQueryBook.where { title == "Effective Java" }
    static DetachedCriteria<OpQueryBook> neQuery = OpQueryBook.where { title != "Effective Java" }
    static DetachedCriteria<OpQueryBook> gtQuery = OpQueryBook.where { price > 10.0 }
    static DetachedCriteria<OpQueryBook> ltQuery = OpQueryBook.where { price < 10.0 }
    static DetachedCriteria<OpQueryBook> geQuery = OpQueryBook.where { price >= 10.0 }
    static DetachedCriteria<OpQueryBook> leQuery = OpQueryBook.where { price <= 10.0 }
    static DetachedCriteria<OpQueryBook> likeQuery = OpQueryBook.where { title ==~ "Effective%" }
    static DetachedCriteria<OpQueryBook> ilikeQuery = OpQueryBook.where { title =~ "effective%" }
    static DetachedCriteria<OpQueryBook> rlikeQuery = OpQueryBook.where { title ==~ ~/Effective.+/ }
    static DetachedCriteria<OpQueryBook> inListQuery = OpQueryBook.where { title in ["Foo", "Bar"] }
    static DetachedCriteria<OpQueryBook> betweenQuery = OpQueryBook.where { minStock in 1..100 }
    static DetachedCriteria<OpQueryBook> isNullQuery = OpQueryBook.where { title == null }
    static DetachedCriteria<OpQueryBook> isNotNullQuery = OpQueryBook.where { title != null }
    static DetachedCriteria<OpQueryBook> sizeEqQuery = OpQueryBook.where { tags.size() == 2 }
    static DetachedCriteria<OpQueryBook> sizeGtQuery = OpQueryBook.where { tags.size() > 1 }
    static DetachedCriteria<OpQueryBook> propertyComparisonQuery = OpQueryBook.where { minStock < maxStock }
    static DetachedCriteria<OpQueryBook> conjunctionQuery = OpQueryBook.where { title == "Foo" && price > 1 }
    static DetachedCriteria<OpQueryBook> disjunctionQuery = OpQueryBook.where { title == "Foo" || price > 1 }
    static DetachedCriteria<OpQueryBook> aggregateDirectQuery = OpQueryBook.where { price > avg(price) }
    static DetachedCriteria<OpQueryBook> aggregateOfQuery = OpQueryBook.where { price > avg(price).of { maxStock > 5 } }
}
'''

    private static Class<?> compile() {
        new GroovyClassLoader().parseClass(SOURCE)
    }

    void "== on a direct property produces an Equals criterion"() {
        given:
        Query.Equals criterion = compile().eqQuery.criteria[0]

        expect:
        criterion.property == 'title'
        criterion.value == 'Effective Java'
    }

    void "!= on a direct property produces a NotEquals criterion"() {
        given:
        Query.NotEquals criterion = compile().neQuery.criteria[0]

        expect:
        criterion.property == 'title'
        criterion.value == 'Effective Java'
    }

    void "> produces a GreaterThan criterion"() {
        given:
        Query.GreaterThan criterion = compile().gtQuery.criteria[0]

        expect:
        criterion.property == 'price'
        criterion.value == 10.0
    }

    void "< produces a LessThan criterion"() {
        given:
        Query.LessThan criterion = compile().ltQuery.criteria[0]

        expect:
        criterion.property == 'price'
        criterion.value == 10.0
    }

    void ">= produces a GreaterThanEquals criterion"() {
        given:
        Query.GreaterThanEquals criterion = compile().geQuery.criteria[0]

        expect:
        criterion.property == 'price'
        criterion.value == 10.0
    }

    void "<= produces a LessThanEquals criterion"() {
        given:
        Query.LessThanEquals criterion = compile().leQuery.criteria[0]

        expect:
        criterion.property == 'price'
        criterion.value == 10.0
    }

    void "==~ produces a Like criterion"() {
        given:
        Query.Like criterion = compile().likeQuery.criteria[0]

        expect:
        criterion.property == 'title'
        criterion.value == 'Effective%'
    }

    void "=~ produces an ILike criterion"() {
        given:
        Query.ILike criterion = compile().ilikeQuery.criteria[0]

        expect:
        criterion.property == 'title'
        criterion.value == 'effective%'
    }

    void "==~ with a regex pattern produces an RLike criterion"() {
        given:
        Query.RLike criterion = compile().rlikeQuery.criteria[0]

        expect:
        criterion.property == 'title'
        criterion.pattern == 'Effective.+'
    }

    void "in with a list literal produces an In criterion"() {
        given:
        Query.In criterion = compile().inListQuery.criteria[0]

        expect:
        criterion.property == 'title'
        criterion.value == ['Foo', 'Bar']
    }

    void "in with a range produces a Between criterion"() {
        given:
        Query.Between criterion = compile().betweenQuery.criteria[0]

        expect:
        criterion.property == 'minStock'
        criterion.from == 1
        criterion.to == 100
    }

    void "== null produces an IsNull criterion"() {
        given:
        Query.IsNull criterion = compile().isNullQuery.criteria[0]

        expect:
        criterion.property == 'title'
    }

    void "!= null produces an IsNotNull criterion"() {
        given:
        Query.IsNotNull criterion = compile().isNotNullQuery.criteria[0]

        expect:
        criterion.property == 'title'
    }

    void "collection.size() == produces a SizeEquals criterion"() {
        given:
        Query.SizeEquals criterion = compile().sizeEqQuery.criteria[0]

        expect:
        criterion.property == 'tags'
        criterion.value == 2
    }

    void "collection.size() > produces a SizeGreaterThan criterion"() {
        given:
        Query.SizeGreaterThan criterion = compile().sizeGtQuery.criteria[0]

        expect:
        criterion.property == 'tags'
        criterion.value == 1
    }

    void "comparing two properties of the same class produces a *Property criterion"() {
        given:
        Query.LessThanProperty criterion = compile().propertyComparisonQuery.criteria[0]

        expect:
        criterion.property == 'minStock'
        criterion.otherProperty == 'maxStock'
    }

    void "&& produces a Conjunction wrapping both criteria"() {
        given:
        Query.Conjunction junction = compile().conjunctionQuery.criteria[0]

        expect:
        junction.criteria.size() == 2
        junction.criteria[0] instanceof Query.Equals
        junction.criteria[1] instanceof Query.GreaterThan
    }

    void "|| produces a Disjunction wrapping both criteria"() {
        given:
        Query.Disjunction junction = compile().disjunctionQuery.criteria[0]

        expect:
        junction.criteria.size() == 2
        junction.criteria[0] instanceof Query.Equals
        junction.criteria[1] instanceof Query.GreaterThan
    }

    void "an aggregate function compared directly produces a criterion whose value is a projection-only DetachedCriteria"() {
        given: 'the projection closure that the transform generates is automatically built by add(), per AbstractDetachedCriteria#add'
        Query.GreaterThan criterion = compile().aggregateDirectQuery.criteria[0]

        expect:
        criterion.property == 'price'
        criterion.value instanceof grails.gorm.DetachedCriteria

        when:
        grails.gorm.DetachedCriteria projectionQuery = (grails.gorm.DetachedCriteria) criterion.value

        then: 'it applies an avg projection for the property and adds no further criteria'
        projectionQuery.projections.size() == 1
        projectionQuery.projections[0] instanceof Query.AvgProjection
        ((Query.PropertyProjection) projectionQuery.projections[0]).propertyName == 'price'
        projectionQuery.criteria.empty
    }

    void "an aggregate function subquery via .of() produces a criterion whose value is a built DetachedCriteria"() {
        given:
        Query.GreaterThan criterion = compile().aggregateOfQuery.criteria[0]

        expect:
        criterion.property == 'price'
        criterion.value instanceof grails.gorm.DetachedCriteria

        when:
        grails.gorm.DetachedCriteria subquery = (grails.gorm.DetachedCriteria) criterion.value

        then: 'the subquery has the avg projection applied'
        subquery.projections.size() == 1
        subquery.projections[0] instanceof Query.AvgProjection
        ((Query.PropertyProjection) subquery.projections[0]).propertyName == 'price'

        and: 'the subquery has its own additional criterion applied'
        subquery.criteria.size() == 1
        subquery.criteria[0] instanceof Query.GreaterThan
        ((Query.GreaterThan) subquery.criteria[0]).property == 'maxStock'
        ((Query.GreaterThan) subquery.criteria[0]).value == 5
    }
}

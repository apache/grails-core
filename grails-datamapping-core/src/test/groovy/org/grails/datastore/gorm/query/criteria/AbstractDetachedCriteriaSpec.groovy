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
package org.grails.datastore.gorm.query.criteria

import grails.gorm.DetachedCriteria
import grails.gorm.annotation.Entity

import jakarta.persistence.FetchType
import jakarta.persistence.criteria.JoinType

import org.grails.datastore.mapping.query.Query
import org.grails.datastore.mapping.query.api.QueryableCriteria
import org.grails.datastore.mapping.simple.SimpleMapDatastore
import spock.lang.AutoCleanup
import spock.lang.Specification

/**
 * Exercises {@link AbstractDetachedCriteria} through its concrete subclass {@link DetachedCriteria}.
 *
 * Criterion, projection, order and derivation methods only need a target class, so those features use the
 * plain {@link TestEntity}. Features that resolve properties or associations, or that execute the criteria,
 * run against real GORM entities registered with a {@link SimpleMapDatastore}.
 */
class AbstractDetachedCriteriaSpec extends Specification {

    @AutoCleanup
    SimpleMapDatastore datastore = new SimpleMapDatastore(['secondary'], DetachedCriteriaAuthor, DetachedCriteriaBook)

    void "eq adds an Equals criterion"() {
        given:
        def criteria = new DetachedCriteria(TestEntity)

        when:
        criteria.eq('name', 'Bob')

        then:
        criteria.criteria.size() == 1
        Query.Equals c = criteria.criteria[0]
        c.property == 'name'
        c.value == 'Bob'
    }

    void "add builds a QueryableCriteria when a PropertyCriterion's value is a bare closure"() {
        given:
        def criteria = new DetachedCriteria(TestEntity)

        when:
        criteria.eq('publisher', { eq('name', 'Apache') })

        then:
        Query.Equals c = criteria.criteria[0]
        c.property == 'publisher'
        c.value instanceof DetachedCriteria
        ((DetachedCriteria) c.value).criteria.size() == 1
    }

    void "ne adds a NotEquals criterion"() {
        given:
        def criteria = new DetachedCriteria(TestEntity)

        when:
        criteria.ne('name', 'Bob')

        then:
        Query.NotEquals c = criteria.criteria[0]
        c.property == 'name'
        c.value == 'Bob'
    }

    void "gt adds a GreaterThan criterion"() {
        given:
        def criteria = new DetachedCriteria(TestEntity)

        when:
        criteria.gt('age', 18)

        then:
        Query.GreaterThan c = criteria.criteria[0]
        c.property == 'age'
        c.value == 18
    }

    void "lt adds a LessThan criterion"() {
        given:
        def criteria = new DetachedCriteria(TestEntity)

        when:
        criteria.lt('age', 65)

        then:
        Query.LessThan c = criteria.criteria[0]
        c.property == 'age'
        c.value == 65
    }

    void "gte adds a GreaterThanEquals criterion"() {
        given:
        def criteria = new DetachedCriteria(TestEntity)

        when:
        criteria.gte('age', 18)

        then:
        Query.GreaterThanEquals c = criteria.criteria[0]
        c.property == 'age'
        c.value == 18
    }

    void "ge is an alias for gte"() {
        given:
        def criteria = new DetachedCriteria(TestEntity)

        when:
        criteria.ge('age', 18)

        then:
        Query.GreaterThanEquals c = criteria.criteria[0]
        c.property == 'age'
        c.value == 18
    }

    void "lte adds a LessThanEquals criterion"() {
        given:
        def criteria = new DetachedCriteria(TestEntity)

        when:
        criteria.lte('age', 65)

        then:
        Query.LessThanEquals c = criteria.criteria[0]
        c.property == 'age'
        c.value == 65
    }

    void "le is an alias for lte"() {
        given:
        def criteria = new DetachedCriteria(TestEntity)

        when:
        criteria.le('age', 65)

        then:
        Query.LessThanEquals c = criteria.criteria[0]
        c.property == 'age'
        c.value == 65
    }

    void "between adds a Between criterion"() {
        given:
        def criteria = new DetachedCriteria(TestEntity)

        when:
        criteria.between('age', 18, 65)

        then:
        Query.Between c = criteria.criteria[0]
        c.property == 'age'
        c.from == 18
        c.to == 65
    }

    void "like adds a Like criterion with a stringified value"() {
        given:
        def criteria = new DetachedCriteria(TestEntity)

        when:
        criteria.like('name', "B${'ob'}")

        then:
        Query.Like c = criteria.criteria[0]
        c.property == 'name'
        c.pattern == 'Bob'
    }

    void "ilike adds an ILike criterion"() {
        given:
        def criteria = new DetachedCriteria(TestEntity)

        when:
        criteria.ilike('name', 'bob')

        then:
        Query.ILike c = criteria.criteria[0]
        c.property == 'name'
        c.pattern == 'bob'
    }

    void "rlike adds an RLike criterion"() {
        given:
        def criteria = new DetachedCriteria(TestEntity)

        when:
        criteria.rlike('name', '^B.*')

        then:
        Query.RLike c = criteria.criteria[0]
        c.property == 'name'
        c.pattern == '^B.*'
    }

    void "isNull/isNotNull/isEmpty/isNotEmpty add the matching property-name criterion"() {
        given:
        def criteria = new DetachedCriteria(TestEntity)

        when:
        criteria.isNull('name')
        criteria.isNotNull('name')
        criteria.isEmpty('name')
        criteria.isNotEmpty('name')

        then:
        criteria.criteria.size() == 4
        criteria.criteria[0] instanceof Query.IsNull
        criteria.criteria[1] instanceof Query.IsNotNull
        criteria.criteria[2] instanceof Query.IsEmpty
        criteria.criteria[3] instanceof Query.IsNotEmpty
        criteria.criteria.every { it.property == 'name' }
    }

    void "idEq adds an IdEquals criterion"() {
        given:
        def criteria = new DetachedCriteria(TestEntity)

        when:
        criteria.idEq(42)

        then:
        Query.IdEquals c = criteria.criteria[0]
        c.property == 'id'
        c.value == 42
    }

    void "idEquals is an alias for idEq"() {
        given:
        def criteria = new DetachedCriteria(TestEntity)

        when:
        criteria.idEquals(42)

        then:
        Query.IdEquals c = criteria.criteria[0]
        c.value == 42
    }

    void "eqProperty/neProperty/gtProperty/geProperty/ltProperty/leProperty compare two properties"() {
        given:
        def criteria = new DetachedCriteria(TestEntity)

        when:
        criteria.eqProperty('name', 'nickName')
        criteria.neProperty('name', 'nickName')
        criteria.gtProperty('age', 'maxAge')
        criteria.geProperty('age', 'maxAge')
        criteria.ltProperty('age', 'minAge')
        criteria.leProperty('age', 'minAge')

        then:
        criteria.criteria.size() == 6
        criteria.criteria[0] instanceof Query.EqualsProperty
        criteria.criteria[1] instanceof Query.NotEqualsProperty
        criteria.criteria[2] instanceof Query.GreaterThanProperty
        criteria.criteria[3] instanceof Query.GreaterThanEqualsProperty
        criteria.criteria[4] instanceof Query.LessThanProperty
        criteria.criteria[5] instanceof Query.LessThanEqualsProperty
    }

    void "allEq adds a Conjunction of Equals criteria, one per map entry"() {
        given:
        def criteria = new DetachedCriteria(TestEntity)

        when:
        criteria.allEq(name: 'Bob', age: 42)

        then:
        criteria.criteria.size() == 1
        Query.Conjunction conjunction = criteria.criteria[0]
        conjunction.criteria.size() == 2
        conjunction.criteria.every { it instanceof Query.Equals }
        conjunction.criteria*.property.sort() == ['age', 'name']
    }

    void "sizeEq/sizeGt/sizeGe/sizeLe/sizeLt/sizeNe add size-comparison criteria"() {
        given:
        def criteria = new DetachedCriteria(TestEntity)

        when:
        criteria.sizeEq('books', 1)
        criteria.sizeGt('books', 1)
        criteria.sizeGe('books', 1)
        criteria.sizeLe('books', 1)
        criteria.sizeLt('books', 1)
        criteria.sizeNe('books', 1)

        then:
        criteria.criteria.size() == 6
        criteria.criteria[0] instanceof Query.SizeEquals
        criteria.criteria[1] instanceof Query.SizeGreaterThan
        criteria.criteria[2] instanceof Query.SizeGreaterThanEquals
        criteria.criteria[3] instanceof Query.SizeLessThanEquals
        criteria.criteria[4] instanceof Query.SizeLessThan
        criteria.criteria[5] instanceof Query.SizeNotEquals
        criteria.criteria.every { it.property == 'books' }
    }

    void "inList(Collection) converts CharSequence values to String"() {
        given:
        def criteria = new DetachedCriteria(TestEntity)
        def gstring = "${'Bob'}"

        when:
        criteria.inList('name', [gstring, 'Alice'])

        then:
        Query.In c = criteria.criteria[0]
        c.property == 'name'
        new ArrayList(c.values) == ['Bob', 'Alice']
        c.values.every { it instanceof String }
    }

    void "inList(Object array) delegates to inList(Collection)"() {
        given:
        def criteria = new DetachedCriteria(TestEntity)

        when:
        criteria.inList('name', ['Bob', 'Alice'] as Object[])

        then:
        Query.In c = criteria.criteria[0]
        new ArrayList(c.values) == ['Bob', 'Alice']
    }

    void "'in'(Collection) delegates to inList"() {
        given:
        def criteria = new DetachedCriteria(TestEntity)

        when:
        criteria."in"('name', ['Bob'])

        then:
        criteria.criteria[0] instanceof Query.In
    }

    void "'in'(Object array) delegates to inList"() {
        given:
        def criteria = new DetachedCriteria(TestEntity)

        when:
        criteria."in"('name', ['Bob'] as Object[])

        then:
        criteria.criteria[0] instanceof Query.In
    }

    void "inList(QueryableCriteria) adds an In criterion wrapping the subquery"() {
        given:
        def criteria = new DetachedCriteria(TestEntity)
        def subquery = Mock(QueryableCriteria)

        when:
        criteria.inList('name', subquery)

        then:
        Query.In c = criteria.criteria[0]
        c.property == 'name'
        c.subquery.is(subquery)
    }

    void "'in'(QueryableCriteria) delegates to inList(QueryableCriteria)"() {
        given:
        def criteria = new DetachedCriteria(TestEntity)
        def subquery = Mock(QueryableCriteria)

        when:
        criteria."in"('name', subquery)

        then:
        Query.In c = criteria.criteria[0]
        c.subquery.is(subquery)
    }

    void "'in'(Closure) builds a QueryableCriteria from the closure and delegates to inList"() {
        given:
        def criteria = new DetachedCriteria(TestEntity)

        when:
        criteria."in"('name') { eq('name', 'Bob') }

        then:
        Query.In c = criteria.criteria[0]
        c.property == 'name'
        c.subquery instanceof DetachedCriteria
        ((DetachedCriteria) c.subquery).criteria.size() == 1
    }

    void "inList(Closure) builds a QueryableCriteria from the closure"() {
        given:
        def criteria = new DetachedCriteria(TestEntity)

        when:
        criteria.inList('name') { eq('name', 'Bob') }

        then:
        Query.In c = criteria.criteria[0]
        c.subquery instanceof DetachedCriteria
    }

    void "notIn(QueryableCriteria) adds a NotIn criterion"() {
        given:
        def criteria = new DetachedCriteria(TestEntity)
        def subquery = Mock(QueryableCriteria)

        when:
        criteria.notIn('name', subquery)

        then:
        Query.NotIn c = criteria.criteria[0]
        c.property == 'name'
        c.value.is(subquery)
    }

    void "notIn(Closure) builds a QueryableCriteria and delegates"() {
        given:
        def criteria = new DetachedCriteria(TestEntity)

        when:
        criteria.notIn('name') { eq('name', 'Bob') }

        then:
        criteria.criteria[0] instanceof Query.NotIn
    }

    void "exists/notExists wrap a subquery"() {
        given:
        def criteria = new DetachedCriteria(TestEntity)
        def subquery = Mock(QueryableCriteria)

        when:
        criteria.exists(subquery)
        criteria.notExists(subquery)

        then:
        criteria.criteria.size() == 2
        criteria.criteria[0] instanceof Query.Exists
        criteria.criteria[1] instanceof Query.NotExists
    }

    void "eqAll/gtAll/ltAll/geAll/leAll wrap a subquery"() {
        given:
        def criteria = new DetachedCriteria(TestEntity)
        def subquery = Mock(QueryableCriteria)

        when:
        criteria.eqAll('age', subquery)
        criteria.gtAll('age', subquery)
        criteria.ltAll('age', subquery)
        criteria.geAll('age', subquery)
        criteria.leAll('age', subquery)

        then:
        criteria.criteria.size() == 5
        criteria.criteria[0] instanceof Query.EqualsAll
        criteria.criteria[1] instanceof Query.GreaterThanAll
        criteria.criteria[2] instanceof Query.LessThanAll
        criteria.criteria[3] instanceof Query.GreaterThanEqualsAll
        criteria.criteria[4] instanceof Query.LessThanEqualsAll
    }

    void "gtSome/geSome/ltSome/leSome wrap a subquery"() {
        given:
        def criteria = new DetachedCriteria(TestEntity)
        def subquery = Mock(QueryableCriteria)

        when:
        criteria.gtSome('age', subquery)
        criteria.geSome('age', subquery)
        criteria.ltSome('age', subquery)
        criteria.leSome('age', subquery)

        then:
        criteria.criteria.size() == 4
        criteria.criteria[0] instanceof Query.GreaterThanSome
        criteria.criteria[1] instanceof Query.GreaterThanEqualsSome
        criteria.criteria[2] instanceof Query.LessThanSome
        criteria.criteria[3] instanceof Query.LessThanEqualsSome
    }

    void "eqAll/gtAll/ltAll/geAll/leAll/gtSome/geSome/ltSome/leSome accept a closure and build the subquery from it"() {
        given:
        def criteria = new DetachedCriteria(TestEntity)

        when:
        criteria.eqAll('age') { eq('age', 42) }
        criteria.gtAll('age') { eq('age', 42) }
        criteria.ltAll('age') { eq('age', 42) }
        criteria.geAll('age') { eq('age', 42) }
        criteria.leAll('age') { eq('age', 42) }
        criteria.gtSome('age') { eq('age', 42) }
        criteria.geSome('age') { eq('age', 42) }
        criteria.ltSome('age') { eq('age', 42) }
        criteria.leSome('age') { eq('age', 42) }

        then:
        criteria.criteria.size() == 9
        criteria.criteria[0] instanceof Query.EqualsAll
        criteria.criteria[1] instanceof Query.GreaterThanAll
        criteria.criteria[2] instanceof Query.LessThanAll
        criteria.criteria[3] instanceof Query.GreaterThanEqualsAll
        criteria.criteria[4] instanceof Query.LessThanEqualsAll
        criteria.criteria[5] instanceof Query.GreaterThanSome
        criteria.criteria[6] instanceof Query.GreaterThanEqualsSome
        criteria.criteria[7] instanceof Query.LessThanSome
        criteria.criteria[8] instanceof Query.LessThanEqualsSome
        criteria.criteria.every { (it as Query.SubqueryCriterion).value instanceof DetachedCriteria }
    }

    void "order(String) adds an ascending order"() {
        given:
        def criteria = new DetachedCriteria(TestEntity)

        when:
        criteria.order('name')

        then:
        criteria.orders.size() == 1
        criteria.orders[0].property == 'name'
        criteria.orders[0].direction == Query.Order.Direction.ASC
    }

    void "order(String, String) sets the given direction"() {
        given:
        def criteria = new DetachedCriteria(TestEntity)

        when:
        criteria.order('name', 'desc')

        then:
        criteria.orders[0].direction == Query.Order.Direction.DESC
    }

    void "order(Query.Order) appends the given order instance"() {
        given:
        def criteria = new DetachedCriteria(TestEntity)
        def order = Query.Order.desc('name')

        when:
        criteria.order(order)

        then:
        criteria.orders[0].is(order)
    }

    void "and wraps nested criteria in a Conjunction"() {
        given:
        def criteria = new DetachedCriteria(TestEntity)

        when:
        criteria.and {
            eq('name', 'Bob')
            eq('age', 42)
        }

        then:
        criteria.criteria.size() == 1
        Query.Conjunction conjunction = criteria.criteria[0]
        conjunction.criteria.size() == 2
    }

    void "or wraps nested criteria in a Disjunction"() {
        given:
        def criteria = new DetachedCriteria(TestEntity)

        when:
        criteria.or {
            eq('name', 'Bob')
            eq('name', 'Alice')
        }

        then:
        criteria.criteria.size() == 1
        criteria.criteria[0] instanceof Query.Disjunction
        (criteria.criteria[0] as Query.Disjunction).criteria.size() == 2
    }

    void "not wraps nested criteria in a Negation"() {
        given:
        def criteria = new DetachedCriteria(TestEntity)

        when:
        criteria.not {
            eq('name', 'Bob')
        }

        then:
        criteria.criteria.size() == 1
        criteria.criteria[0] instanceof Query.Negation
        (criteria.criteria[0] as Query.Negation).criteria.size() == 1
    }

    void "junction is closed even when the closure throws"() {
        given:
        def criteria = new DetachedCriteria(TestEntity)

        when:
        criteria.and {
            eq('name', 'Bob')
            throw new IllegalStateException('boom')
        }

        then:
        thrown(IllegalStateException)
        criteria.criteria.size() == 1
        (criteria.criteria[0] as Query.Conjunction).criteria.size() == 1
    }

    void "projections closure populates the projection list"() {
        given:
        def criteria = new DetachedCriteria(TestEntity)

        when:
        criteria.projections {
            avg('age')
            max('age')
            min('age')
            sum('age')
            property('name')
            rowCount()
            count()
            id()
            distinct('name')
            distinct()
            countDistinct('name')
            groupProperty('name')
        }

        then:
        criteria.projections.size() == 12
    }

    void "join(property) marks the property for eager fetching"() {
        given:
        def criteria = new DetachedCriteria(TestEntity)

        when:
        criteria.join('books')

        then:
        criteria.fetchStrategies['books'] == FetchType.EAGER
        criteria.getFetchStrategies() == [books: FetchType.EAGER]
    }

    void "join(property, joinType) records the join type as well"() {
        given:
        def criteria = new DetachedCriteria(TestEntity)

        when:
        criteria.join('books', JoinType.LEFT)

        then:
        criteria.fetchStrategies['books'] == FetchType.EAGER
        criteria.joinTypes['books'] == JoinType.LEFT
        criteria.getJoinTypes() == [books: JoinType.LEFT]
    }

    void "select(property) marks the property for lazy fetching"() {
        given:
        def criteria = new DetachedCriteria(TestEntity)

        when:
        criteria.select('books')

        then:
        criteria.fetchStrategies['books'] == FetchType.LAZY
    }

    void "getFetchStrategies and getJoinTypes return unmodifiable views"() {
        given:
        def criteria = new DetachedCriteria(TestEntity)
        criteria.join('books')

        when:
        criteria.getFetchStrategies()['other'] = FetchType.LAZY

        then:
        thrown(UnsupportedOperationException)

        when:
        criteria.getJoinTypes()['other'] = JoinType.LEFT

        then:
        thrown(UnsupportedOperationException)
    }

    void "cache and readOnly are no-ops that return this"() {
        given:
        def criteria = new DetachedCriteria(TestEntity)

        expect:
        criteria.cache(true).is(criteria)
        criteria.readOnly(true).is(criteria)
    }

    void "setAlias/getAlias round-trip"() {
        given:
        def criteria = new DetachedCriteria(TestEntity)

        when:
        criteria.setAlias('t')

        then:
        criteria.getAlias() == 't'
    }

    void "where derives a new criteria instance without mutating the original"() {
        given:
        def criteria = new DetachedCriteria(TestEntity)
        criteria.eq('name', 'Bob')

        when:
        def derived = criteria.where { eq('age', 42) }

        then:
        !derived.is(criteria)
        criteria.criteria.size() == 1
        derived.criteria.size() == 2
    }

    void "build derives a new criteria instance without mutating the original"() {
        given:
        def criteria = new DetachedCriteria(TestEntity)
        criteria.eq('name', 'Bob')

        when:
        def derived = criteria.build { eq('age', 42) }

        then:
        !derived.is(criteria)
        criteria.criteria.size() == 1
        derived.criteria.size() == 2
    }

    void "buildLazy defers the closure until the criteria is next modified and applies it only once"() {
        given:
        def criteria = new DetachedCriteria(TestEntity)

        when:
        def derived = criteria.buildLazy { eq('age', 42) }

        then:
        derived.criteria.isEmpty()

        when: "a criterion is added, triggering the deferred closure"
        derived.eq('name', 'Bob')

        then:
        derived.criteria.size() == 2

        when: "another criterion is added"
        derived.eq('name', 'Alice')

        then: "the deferred closure is not applied a second time"
        derived.criteria.size() == 3
    }

    void "whereLazy applies the closure eagerly like where"() {
        given:
        def criteria = new DetachedCriteria(TestEntity)

        when:
        def derived = criteria.whereLazy { eq('age', 42) }

        then:
        derived.criteria.size() == 1
    }

    void "withConnection derives a new instance that executes against the named connection"() {
        given:
        new DetachedCriteriaAuthor(name: 'Default Only').save(flush: true)
        def criteria = new DetachedCriteria(DetachedCriteriaAuthor)

        when:
        def derived = criteria.withConnection('secondary')

        then:
        !derived.is(criteria)
        criteria.count() == 1
        derived.count() == 0
    }

    void "max(int)/offset(int) derive a new instance without mutating the original"() {
        given:
        def criteria = new DetachedCriteria(TestEntity)

        when:
        def maxed = criteria.max(10)
        def offset = criteria.offset(5)

        then:
        maxed.defaultMax == 10
        criteria.defaultMax == null
        offset.defaultOffset == 5
        criteria.defaultOffset == null
    }

    void "sort(property) and sort(property, direction) derive a new instance with an added order"() {
        given:
        def criteria = new DetachedCriteria(TestEntity)

        when:
        def ascSort = criteria.sort('name')
        def descSort = criteria.sort('name', 'desc')

        then:
        ascSort.orders.size() == 1
        ascSort.orders[0].direction == Query.Order.Direction.ASC
        descSort.orders[0].direction == Query.Order.Direction.DESC
        criteria.orders.isEmpty()
    }

    void "property/id/avg/sum/min/max(String)/distinct derive a new instance with the projection added"() {
        given:
        def criteria = new DetachedCriteria(TestEntity)

        when:
        def prop = criteria.property('name')
        def id = criteria.id()
        def avg = criteria.avg('age')
        def sum = criteria.sum('age')
        def min = criteria.min('age')
        def max = criteria.max('age')
        def distinct = criteria.distinct('name')

        then:
        [prop, id, avg, sum, min, max, distinct].every { it.projections.size() == 1 }
        criteria.projections.isEmpty()
    }

    void "clone produces a distinct DetachedCriteria with copied collections"() {
        given:
        def criteria = new DetachedCriteria(TestEntity)
        criteria.eq('name', 'Bob')
        criteria.order('name')
        criteria.projectionList.property('name')

        when:
        def cloned = criteria.clone()
        cloned.eq('age', 42)
        cloned.order('age')
        cloned.projectionList.property('age')

        then:
        cloned instanceof DetachedCriteria
        !cloned.is(criteria)
        criteria.criteria.size() == 1
        cloned.criteria.size() == 2
        criteria.orders.size() == 1
        cloned.orders.size() == 2
        criteria.projections.size() == 1
        cloned.projections.size() == 2
    }

    void "getPersistentEntity throws IllegalArgumentException for a class that is not GORM-enhanced"() {
        given:
        def criteria = new DetachedCriteria(TestEntity)

        when:
        criteria.getPersistentEntity()

        then:
        IllegalArgumentException e = thrown()
        e.message.contains('is not a domain class')
    }

    void "getPersistentClass and getPersistentEntity resolve the registered GORM entity"() {
        given:
        def criteria = new DetachedCriteria(DetachedCriteriaBook)

        expect:
        criteria.getPersistentClass() == DetachedCriteriaBook
        criteria.getPersistentEntity().javaClass == DetachedCriteriaBook
    }

    void "createAlias creates a DetachedAssociationCriteria for a top-level association"() {
        given:
        def criteria = new DetachedCriteria(DetachedCriteriaAuthor)

        when:
        def result = criteria.createAlias('books', 'b')

        then:
        result instanceof DetachedAssociationCriteria
        result.alias == 'b'
        result.associationPath == 'books'
        result.association.name == 'books'
        result.persistentClass == DetachedCriteriaBook
        criteria.criteria == [result]
    }

    void "createAlias reuses an existing association criteria and updates its alias"() {
        given:
        def criteria = new DetachedCriteria(DetachedCriteriaAuthor)

        when:
        def first = criteria.createAlias('books', 'b1')
        def second = criteria.createAlias('books', 'b2')

        then:
        first.is(second)
        second.alias == 'b2'
        criteria.criteria.size() == 1
    }

    void "createAlias resolves a dotted association path"() {
        given:
        def criteria = new DetachedCriteria(DetachedCriteriaAuthor)

        when:
        def result = criteria.createAlias('books.author', 'a')

        then:
        result instanceof DetachedAssociationCriteria
        result.alias == 'a'
        result.associationPath == 'books.author'
        result.association.name == 'author'
        result.persistentClass == DetachedCriteriaAuthor
    }

    void "createAlias throws IllegalArgumentException when the property is not an association"() {
        given:
        def criteria = new DetachedCriteria(DetachedCriteriaAuthor)

        when:
        criteria.createAlias('name', 'n')

        then:
        IllegalArgumentException e = thrown()
        e.message == 'Argument [name] is not an association'
    }

    void "createAlias throws IllegalArgumentException for a dotted path segment that is not an association"() {
        given:
        def criteria = new DetachedCriteria(DetachedCriteriaAuthor)

        when:
        criteria.createAlias(path, 'n')

        then:
        IllegalArgumentException e = thrown()
        e.message == "Argument [$path] is not an association"

        where:
        path << ['name.other', 'books.title']
    }

    void "reading an unknown property that maps to a persistent property returns a property projection"() {
        given:
        def criteria = new DetachedCriteria(DetachedCriteriaBook)

        when:
        def result = criteria.title

        then:
        result instanceof DetachedCriteria
        !result.is(criteria)
        result.projections.size() == 1
        result.projections[0] instanceof Query.PropertyProjection
        (result.projections[0] as Query.PropertyProjection).propertyName == 'title'
        criteria.projections.isEmpty()
    }

    void "reading an unknown property that is not a persistent property throws MissingPropertyException"() {
        given:
        def criteria = new DetachedCriteria(DetachedCriteriaBook)

        when:
        criteria.nope

        then:
        thrown(MissingPropertyException)
    }

    void "a dynamic finder called on the criteria is combined with the detached criteria"() {
        given:
        new DetachedCriteriaBook(title: 'Groovy', pages: 100).save(flush: true)
        new DetachedCriteriaBook(title: 'Groovy', pages: 300).save(flush: true)
        new DetachedCriteriaBook(title: 'Grails', pages: 100).save(flush: true)
        def criteria = new DetachedCriteria(DetachedCriteriaBook).build { eq('pages', 100) }

        when:
        List<DetachedCriteriaBook> found = criteria.findAllByTitle('Groovy')

        then:
        found*.title == ['Groovy']
        found*.pages == [100]
        criteria.countByTitle('Groovy') == 1
    }

    void "an unknown method with no arguments throws MissingMethodException"() {
        given:
        def criteria = new DetachedCriteria(DetachedCriteriaBook)

        when:
        criteria.notAMethod()

        then:
        thrown(MissingMethodException)
    }

    void "an unknown method named after a non-association property throws MissingMethodException"() {
        given:
        def criteria = new DetachedCriteria(DetachedCriteriaBook)

        when:
        criteria.title('Groovy')

        then:
        thrown(MissingMethodException)
    }

    void "an association method call without a closure adds an aliased association criteria"() {
        given:
        def criteria = new DetachedCriteria(DetachedCriteriaAuthor)

        when:
        criteria.books('b')

        then:
        criteria.criteria.size() == 1
        DetachedAssociationCriteria association = criteria.criteria[0]
        association.alias == 'b'
        association.associationPath == 'books'
        association.persistentClass == DetachedCriteriaBook
    }

    void "a repeated association method call reuses the existing alias when none is given"() {
        given:
        def criteria = new DetachedCriteria(DetachedCriteriaAuthor)

        when:
        criteria.books('b1') { eq('title', 'first') }
        criteria.books { eq('title', 'second') }

        then:
        criteria.criteria.size() == 2
        criteria.criteria.every { it instanceof DetachedAssociationCriteria }
        criteria.criteria*.alias == ['b1', 'b1']
    }

    void "an association method call builds an association criteria and delegates the closure to it"() {
        given:
        def criteria = new DetachedCriteria(DetachedCriteriaAuthor)
        boolean delegateWasAssociationCriteria = false

        when:
        criteria.books {
            delegateWasAssociationCriteria = delegate instanceof DetachedAssociationCriteria
            eq('title', 'Groovy in Action')
        }

        then:
        criteria.criteria.size() == 1
        criteria.criteria[0] instanceof DetachedAssociationCriteria
        delegateWasAssociationCriteria
        (criteria.criteria[0] as DetachedAssociationCriteria).criteria.size() == 1
    }

    void "an association method call restores the closure's previous delegate"() {
        given:
        def criteria = new DetachedCriteria(DetachedCriteriaAuthor)
        def previousDelegate = new Object()
        def closure = { eq('title', 'Groovy in Action') }
        closure.delegate = previousDelegate

        when:
        criteria.books(closure)

        then:
        closure.delegate.is(previousDelegate)
    }

    void "association criteria added through a nested closure filter the executed query"() {
        given:
        def groovyBook = new DetachedCriteriaBook(title: 'Groovy', pages: 100).save(flush: true)
        def grailsBook = new DetachedCriteriaBook(title: 'Grails', pages: 200).save(flush: true)
        new DetachedCriteriaAuthor(name: 'Groovy Author').addToBooks(groovyBook).save(flush: true)
        new DetachedCriteriaAuthor(name: 'Grails Author').addToBooks(grailsBook).save(flush: true)
        def criteria = new DetachedCriteria(DetachedCriteriaAuthor).build {
            books {
                eq('title', 'Groovy')
            }
        }

        expect:
        criteria.list()*.name == ['Groovy Author']
    }
}

@Entity
class DetachedCriteriaAuthor {

    Long id
    String name

    static hasMany = [books: DetachedCriteriaBook]
}

@Entity
class DetachedCriteriaBook {

    Long id
    String title
    Integer pages
    DetachedCriteriaAuthor author

    static belongsTo = [author: DetachedCriteriaAuthor]

    static constraints = {
        author nullable: true
    }
}

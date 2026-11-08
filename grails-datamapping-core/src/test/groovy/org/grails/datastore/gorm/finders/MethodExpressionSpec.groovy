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
package org.grails.datastore.gorm.finders

import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.query.Query
import org.springframework.core.convert.support.DefaultConversionService
import spock.lang.Specification
import spock.lang.Unroll

/**
 * Exercises every {@link MethodExpression} nested operator class - the DSL surface that dynamic
 * finder method-name suffixes (GreaterThan, Like, InList, Between, ...) get parsed into by
 * {@link DynamicFinder}. Collaborators (PersistentEntity/MappingContext) are stubbed with a real
 * Spring {@link DefaultConversionService} since {@code convertArguments}' coercion behavior
 * (GString -> String, collection/array element skip, delegated ConversionService conversion) is
 * itself part of what several of these tests verify.
 */
class MethodExpressionSpec extends Specification {

    PersistentProperty idProperty = Stub(PersistentProperty) {
        getName() >> 'id'
        getType() >> Long
    }
    PersistentProperty nameProperty = Stub(PersistentProperty) {
        getName() >> 'name'
        getType() >> String
    }
    PersistentProperty ageProperty = Stub(PersistentProperty) {
        getName() >> 'age'
        getType() >> Integer
    }
    PersistentProperty tagsProperty = Stub(PersistentProperty) {
        getName() >> 'tags'
        getType() >> List
    }
    MappingContext mappingContext = Stub(MappingContext) {
        getConversionService() >> new DefaultConversionService()
    }
    PersistentEntity persistentEntity = Stub(PersistentEntity) {
        getIdentity() >> idProperty
        getMappingContext() >> mappingContext
        getPropertyByName('name') >> nameProperty
        getPropertyByName('age') >> ageProperty
        getPropertyByName('tags') >> tagsProperty
        getPropertyByName('id') >> null
        getPropertyByName('missing') >> null
    }

    @Unroll
    void "#expressionType.simpleName createCriterion builds the matching Query.Criterion"() {
        given:
        MethodExpression expression = expressionType.getConstructor(String).newInstance('name')
        expression.setArguments(['Bob'] as Object[])

        when:
        Query.Criterion criterion = expression.createCriterion()

        then:
        criterionType.isInstance(criterion)
        criterion.property == 'name'
        criterion.value == 'Bob'

        where:
        expressionType             | criterionType
        MethodExpression.GreaterThan       | Query.GreaterThan
        MethodExpression.GreaterThanEquals | Query.GreaterThanEquals
        MethodExpression.LessThan          | Query.LessThan
        MethodExpression.LessThanEquals    | Query.LessThanEquals
    }

    @Unroll
    void "#expressionType.simpleName createCriterion builds the matching pattern criterion"() {
        given:
        MethodExpression expression = expressionType.getConstructor(String).newInstance('name')
        expression.setArguments(['Bo%'] as Object[])

        when:
        Query.Criterion criterion = expression.createCriterion()

        then:
        criterionType.isInstance(criterion)
        criterion.property == 'name'
        criterion.pattern == 'Bo%'

        where:
        expressionType     | criterionType
        MethodExpression.Like  | Query.Like
        MethodExpression.Ilike | Query.ILike
        MethodExpression.Rlike | Query.RLike
    }

    void "Like coerces a non-String argument via toString()"() {
        given:
        MethodExpression expression = new MethodExpression.Like('age')
        expression.setArguments([42] as Object[])

        expect:
        expression.createCriterion().pattern == '42'
    }

    void "Equal builds an Equals criterion when the argument is non-null"() {
        given:
        MethodExpression expression = new MethodExpression.Equal('name')
        expression.setArguments(['Bob'] as Object[])

        when:
        Query.Criterion criterion = expression.createCriterion()

        then:
        criterion instanceof Query.Equals
        criterion.property == 'name'
        criterion.value == 'Bob'
    }

    void "Equal falls back to an IsNull criterion when the argument is null"() {
        given:
        MethodExpression expression = new MethodExpression.Equal('name')
        expression.setArguments([null] as Object[])

        when:
        Query.Criterion criterion = expression.createCriterion()

        then:
        criterion instanceof Query.IsNull
        criterion.property == 'name'
    }

    void "NotEqual builds a NotEquals criterion when the argument is non-null"() {
        given:
        MethodExpression expression = new MethodExpression.NotEqual('name')
        expression.setArguments(['Bob'] as Object[])

        when:
        Query.Criterion criterion = expression.createCriterion()

        then:
        criterion instanceof Query.NotEquals
        criterion.property == 'name'
        criterion.value == 'Bob'
    }

    void "NotEqual falls back to an IsNotNull criterion when the argument is null"() {
        given:
        MethodExpression expression = new MethodExpression.NotEqual('name')
        expression.setArguments([null] as Object[])

        when:
        Query.Criterion criterion = expression.createCriterion()

        then:
        criterion instanceof Query.IsNotNull
        criterion.property == 'name'
    }

    @Unroll
    void "#expressionType.simpleName requires zero arguments and builds a bare property criterion"() {
        given:
        MethodExpression expression = expressionType.getConstructor(String).newInstance('name')

        expect:
        expression.argumentsRequired == 0

        when:
        Query.Criterion criterion = expression.createCriterion()

        then:
        criterionType.isInstance(criterion)
        criterion.property == 'name'

        where:
        expressionType             | criterionType
        MethodExpression.IsNull        | Query.IsNull
        MethodExpression.IsNotNull     | Query.IsNotNull
        MethodExpression.IsEmpty       | Query.IsEmpty
        MethodExpression.IsNotEmpty    | Query.IsNotEmpty
    }

    void "InList builds an In criterion from a Collection argument"() {
        given:
        MethodExpression expression = new MethodExpression.InList('name')
        expression.setArguments([['a', 'b']] as Object[])

        when:
        Query.Criterion criterion = expression.createCriterion()

        then:
        criterion instanceof Query.In
        criterion.property == 'name'
        // Query.In.getValues() returns Collections.unmodifiableCollection(), which - unlike
        // unmodifiableList()/unmodifiableSet() - does not delegate equals(), so it must be
        // materialized before comparing.
        criterion.values.toList() == ['a', 'b']
    }

    void "InList setArguments rejects a non-Collection, non-null argument"() {
        given:
        MethodExpression expression = new MethodExpression.InList('name')

        when:
        expression.setArguments(['not-a-collection'] as Object[])

        then:
        thrown(IllegalArgumentException)
    }

    void "InList setArguments accepts a null argument"() {
        given:
        MethodExpression expression = new MethodExpression.InList('name')

        when:
        expression.setArguments([null] as Object[])

        then:
        noExceptionThrown()
    }

    void "InList setArguments rejects an empty argument array"() {
        given:
        MethodExpression expression = new MethodExpression.InList('name')

        when:
        expression.setArguments([] as Object[])

        then:
        thrown(IllegalArgumentException)
    }

    void "InList convertArguments converts each collection element individually"() {
        given:
        MethodExpression expression = new MethodExpression.InList('age')
        expression.setArguments([['1', '2']] as Object[])

        when:
        expression.convertArguments(persistentEntity)

        then:
        expression.getArguments()[0] == [1, 2]
    }

    void "InList convertArguments converts a null collection into an empty list"() {
        given:
        MethodExpression expression = new MethodExpression.InList('age')
        expression.setArguments([null] as Object[])

        when:
        expression.convertArguments(persistentEntity)

        then:
        expression.getArguments()[0] == []
    }

    void "InList convertArguments is a no-op when the property cannot be resolved"() {
        given:
        MethodExpression expression = new MethodExpression.InList('missing')
        expression.setArguments([['1', '2']] as Object[])

        when:
        expression.convertArguments(persistentEntity)

        then:
        expression.getArguments()[0] == ['1', '2']
    }

    void "NotInList wraps an In criterion in a Negation"() {
        given:
        MethodExpression expression = new MethodExpression.NotInList('name')
        expression.setArguments([['a', 'b']] as Object[])

        when:
        Query.Criterion criterion = expression.createCriterion()

        then:
        criterion instanceof Query.Negation
        Query.Negation negation = (Query.Negation) criterion
        negation.criteria.size() == 1
        Query.In wrapped = negation.criteria[0]
        wrapped.property == 'name'
        wrapped.values.toList() == ['a', 'b']
    }

    void "NotInList setArguments applies the same Collection-or-null validation as InList"() {
        given:
        MethodExpression expression = new MethodExpression.NotInList('name')

        when:
        expression.setArguments(['not-a-collection'] as Object[])

        then:
        thrown(IllegalArgumentException)
    }

    void "NotInList convertArguments converts each collection element individually"() {
        given:
        MethodExpression expression = new MethodExpression.NotInList('age')
        expression.setArguments([['1', '2']] as Object[])

        when:
        expression.convertArguments(persistentEntity)

        then:
        expression.getArguments()[0] == [1, 2]
    }

    void "Between requires two arguments"() {
        given:
        MethodExpression expression = new MethodExpression.Between('age')

        expect:
        expression.argumentsRequired == 2
    }

    void "Between builds a Between criterion from two Comparable arguments"() {
        given:
        MethodExpression expression = new MethodExpression.Between('age')
        expression.setArguments([1, 10] as Object[])

        when:
        Query.Criterion criterion = expression.createCriterion()

        then:
        criterion instanceof Query.Between
        criterion.property == 'age'
        criterion.from == 1
        criterion.to == 10
    }

    void "Between setArguments rejects fewer than two arguments"() {
        given:
        MethodExpression expression = new MethodExpression.Between('age')

        when:
        expression.setArguments([1] as Object[])

        then:
        thrown(IllegalArgumentException)
    }

    @Unroll
    void "Between setArguments rejects a non-Comparable argument (#description)"() {
        given:
        MethodExpression expression = new MethodExpression.Between('age')

        when:
        expression.setArguments([first, second] as Object[])

        then:
        thrown(IllegalArgumentException)

        where:
        description       | first             | second
        'non-Comparable first argument'  | new Object() | 10
        'non-Comparable second argument' | 1            | new Object()
    }

    void "InRange requires exactly one argument"() {
        given:
        MethodExpression expression = new MethodExpression.InRange('age')

        expect:
        expression.argumentsRequired == 1
    }

    void "InRange builds a Between criterion from a Range's from/to bounds"() {
        given:
        MethodExpression expression = new MethodExpression.InRange('age')
        expression.setArguments([1..10] as Object[])

        when:
        Query.Criterion criterion = expression.createCriterion()

        then:
        criterion instanceof Query.Between
        criterion.property == 'age'
        criterion.from == 1
        criterion.to == 10
    }

    void "InRange setArguments rejects a non-Range argument"() {
        given:
        MethodExpression expression = new MethodExpression.InRange('age')

        when:
        expression.setArguments(['not-a-range'] as Object[])

        then:
        thrown(IllegalArgumentException)
    }

    void "InRange setArguments rejects more than one argument"() {
        given:
        MethodExpression expression = new MethodExpression.InRange('age')

        when:
        expression.setArguments([1..10, 'extra'] as Object[])

        then:
        thrown(IllegalArgumentException)
    }

    void "InRange convertArguments is a no-op, trusting setArguments already validated the Range"() {
        given:
        MethodExpression expression = new MethodExpression.InRange('age')
        def range = 1..10
        expression.setArguments([range] as Object[])

        when:
        expression.convertArguments(persistentEntity)

        then:
        expression.getArguments()[0].is(range)
    }

    void "convertArguments coerces a GString argument to a real String"() {
        given:
        MethodExpression expression = new MethodExpression.Equal('name')
        def suffix = 'Bo'
        expression.setArguments(["${suffix}b"] as Object[])

        when:
        expression.convertArguments(persistentEntity)

        then:
        expression.getArguments()[0] == 'Bob'
        expression.getArguments()[0] instanceof String
    }

    void "convertArguments delegates to the ConversionService for a convertible mismatched type"() {
        given:
        MethodExpression expression = new MethodExpression.Equal('age')
        expression.setArguments(['42'] as Object[])

        when:
        expression.convertArguments(persistentEntity)

        then:
        expression.getArguments()[0] == 42
    }

    void "convertArguments leaves an already-assignable argument untouched"() {
        given:
        MethodExpression expression = new MethodExpression.Equal('name')
        String value = 'Bob'
        expression.setArguments([value] as Object[])

        when:
        expression.convertArguments(persistentEntity)

        then:
        expression.getArguments()[0].is(value)
    }

    void "convertArguments skips a Collection argument whose elements already match the declared element type"() {
        given:
        MethodExpression expression = new MethodExpression.Equal('tags')
        List value = ['a', 'b']
        expression.setArguments([value] as Object[])

        when:
        expression.convertArguments(persistentEntity)

        then:
        expression.getArguments()[0].is(value)
    }

    void "convertArguments resolves the identity property when propertyName matches it"() {
        given:
        MethodExpression expression = new MethodExpression.Equal('id')
        expression.setArguments(['42'] as Object[])

        when:
        expression.convertArguments(persistentEntity)

        then:
        expression.getArguments()[0] == 42
    }

    void "InList convertArguments resolves the identity property when propertyName matches it"() {
        given:
        // InList/NotInList's convertArguments delegates to the private static
        // convertArgumentsForProp helper, which has its own COPY of the identity-property fallback
        // used by the base convertArguments above - a separate branch, separately worth covering.
        MethodExpression expression = new MethodExpression.InList('id')
        expression.setArguments([['42', '43']] as Object[])

        when:
        expression.convertArguments(persistentEntity)

        then:
        expression.getArguments()[0] == [42L, 43L]
    }

    void "convertArguments is a no-op when the property cannot be resolved at all"() {
        given:
        MethodExpression expression = new MethodExpression.Equal('missing')
        expression.setArguments(['unchanged'] as Object[])

        when:
        expression.convertArguments(persistentEntity)

        then:
        expression.getArguments()[0] == 'unchanged'
    }

    void "getArguments returns a defensive copy, not the live backing array"() {
        given:
        MethodExpression expression = new MethodExpression.Equal('name')
        expression.setArguments(['Bob'] as Object[])

        when:
        Object[] copy = expression.getArguments()
        copy[0] = 'Mutated'

        then:
        expression.getArguments()[0] == 'Bob'
    }

    void "getPropertyName returns the property this expression targets"() {
        expect:
        new MethodExpression.Equal('name').propertyName == 'name'
    }

    @Unroll
    void "#expressionType.simpleName's (Class, String) constructor delegates identically to its (String) constructor"() {
        given:
        // DynamicFinder always constructs operators via the (Class, String) overload (the Class
        // arg feeds the deprecated, unused `targetClass` field) - this confirms every concrete
        // operator's second constructor sets propertyName the same way the first one does, rather
        // than only ever exercising the (String)-only overload used elsewhere in this spec.
        MethodExpression expression = expressionType.getConstructor(Class, String).newInstance(FinderTestEntity, 'name')

        expect:
        expression.propertyName == 'name'
        expression.argumentsRequired == argumentsRequired

        where:
        expressionType                      | argumentsRequired
        MethodExpression.Equal              | 1
        MethodExpression.NotEqual           | 1
        MethodExpression.GreaterThan        | 1
        MethodExpression.GreaterThanEquals  | 1
        MethodExpression.LessThan           | 1
        MethodExpression.LessThanEquals     | 1
        MethodExpression.Like               | 1
        MethodExpression.Ilike              | 1
        MethodExpression.Rlike              | 1
        MethodExpression.InList             | 1
        MethodExpression.NotInList          | 1
        MethodExpression.Between            | 2
        MethodExpression.InRange            | 1
        MethodExpression.IsNull             | 0
        MethodExpression.IsNotNull          | 0
        MethodExpression.IsEmpty            | 0
        MethodExpression.IsNotEmpty         | 0
    }
}

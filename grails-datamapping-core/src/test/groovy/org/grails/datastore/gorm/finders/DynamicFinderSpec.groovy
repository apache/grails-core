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

import groovy.lang.MissingMethodException
import jakarta.persistence.FetchType
import jakarta.persistence.criteria.JoinType
import org.grails.datastore.gorm.query.criteria.AbstractDetachedCriteria
import org.grails.datastore.mapping.core.Session
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.model.types.Basic
import org.grails.datastore.mapping.query.Query
import org.grails.datastore.mapping.query.api.BuildableCriteria
import org.grails.datastore.mapping.query.api.QueryArgumentsAware
import org.springframework.core.convert.ConverterNotFoundException
import org.springframework.core.convert.TypeDescriptor
import org.springframework.core.convert.support.DefaultConversionService
import spock.lang.Specification
import spock.lang.Unroll

import java.util.regex.Pattern

/**
 * Exercises {@link DynamicFinder}'s method-name grammar and its static parsing/argument-handling/
 * junction-building surface. {@code DynamicFinder} is a plain, non-abstract, non-inheritable
 * collaborator - constructed directly here with the same patterns the real finder classes
 * ({@link SingleResultFinder}, {@link ListResultFinder}, {@link CountFinder}) use, rather than via
 * a concrete finder subclass (there are none any more). Collaborators (MappingContext/
 * PersistentEntity/Query) are mocked - this package's own responsibility is translating dynamic-
 * finder method calls into {@code Query} objects, not persistence itself.
 */
class DynamicFinderSpec extends Specification {

    private static final Pattern FIND_BY_PATTERN = Pattern.compile('(findBy)([A-Z]\\w*)')
    private static final Pattern FIND_BY_BOOLEAN_PATTERN = Pattern.compile('(find)((\\w+)(By)([A-Z]\\w*)|(\\w++))')
    private static final String[] OPERATORS = ['And', 'Or'] as String[]

    void "test build match spec"() {
        given:
        MatchSpec spec = DynamicFinder.buildMatchSpec(prefix, methodName, parameters)

        expect:
        spec.methodName == methodName
        spec.methodCallExpressions.size() == expressions
        spec.requiredArguments == parameters
        spec.prefix == prefix
        spec.queryExpression == queryExpression
        spec.propertyNames == propertyNames

        where:
        prefix   | methodName                    | parameters | expressions | queryExpression        | propertyNames
        "findBy" | "findByTitle"                 | 1          | 1           | "Title"                | ['title']
        "findBy" | "findByTitleBetween"          | 2          | 1           | "TitleBetween"         | ['title']
        "findBy" | "findByTitleAndAuthor"        | 2          | 2           | "TitleAndAuthor"       | ['title', 'author']
        "findBy" | "findByTitleOrAuthor"         | 2          | 2           | "TitleOrAuthor"        | ['title', 'author']
        "findBy" | "findByAgeGreaterThanEquals"  | 1          | 1           | "AgeGreaterThanEquals" | ['age']
        "findBy" | "findByAgeLessThanEquals"     | 1          | 1           | "AgeLessThanEquals"    | ['age']
        "findBy" | "findByActiveIsNull"          | 0          | 1           | "ActiveIsNull"         | ['active']
        "findBy" | "findByActiveIsNotNull"       | 0          | 1           | "ActiveIsNotNull"      | ['active']
        "findBy" | "findByAuthorNot"             | 1          | 1           | "AuthorNot"            | ['author']
    }

    void "buildMatchSpec returns null when there are fewer parameters than the expression requires"() {
        expect:
        DynamicFinder.buildMatchSpec("findBy", "findByTitleBetween", 1) == null
    }

    void "buildMatchSpec returns null when a lone expression's arguments exceed the given parameter count"() {
        expect:
        DynamicFinder.buildMatchSpec("findBy", "findByTitle", 0) == null
    }

    void "buildMatchSpec resolves a GreaterThanEquals suffix without truncating it to GreaterThan"() {
        given:
        MatchSpec spec = DynamicFinder.buildMatchSpec("findBy", "findByAgeGreaterThanEquals", 1)

        expect:
        spec.methodCallExpressions[0] instanceof MethodExpression.GreaterThanEquals
    }

    void "buildMatchSpec wraps a Not-suffixed clause's criterion in a Negation"() {
        given:
        MatchSpec spec = DynamicFinder.buildMatchSpec("findBy", "findByAuthorNot", 1)
        MethodExpression expression = spec.methodCallExpressions[0]
        expression.setArguments(['Bob'] as Object[])

        when:
        Query.Criterion criterion = expression.createCriterion()

        then:
        criterion instanceof Query.Negation
        Query.Negation negation = (Query.Negation) criterion
        negation.criteria.size() == 1
        negation.criteria[0] instanceof Query.Equals
        negation.criteria[0].property == 'author'
    }

    void "buildMatchSpec throws when the derived property name is empty because the property itself shares the operator's name"() {
        when:
        // "findByLikeLike" means "property 'Like', operator Like" - but calcPropertyName finds the
        // FIRST occurrence of "Like" in "LikeLike" (index 0, the property's own name), not the
        // occurrence the regex actually matched, so the derived property name comes out empty.
        DynamicFinder.buildMatchSpec("findBy", "findByLikeLike", 1)

        then:
        thrown(IllegalArgumentException)
    }

    void "buildMatchSpec's naive literal split on the And/Or operator can split inside an unrelated property name"() {
        when:
        // "findByAndroidVersionAndTitle" is meant to be "androidVersion And title", but
        // querySequence.split("And") splits on every literal occurrence of "And", including the
        // one inside "Android" itself, producing an empty leading segment - a real parsing bug in
        // both buildMatchSpec and createFinderInvocation's identical split logic, not a
        // hypothetical. Documented here as current behavior, not fixed as part of this pass.
        DynamicFinder.buildMatchSpec("findBy", "findByAndroidVersionAndTitle", 2)

        then:
        thrown(IllegalArgumentException)
    }

    void "registerNewMethodExpression rejects a class without a (Class, String) constructor"() {
        when:
        DynamicFinder.registerNewMethodExpression(String)

        then:
        thrown(IllegalArgumentException)
    }

    void "registerNewMethodExpression registers a custom operator that buildMatchSpec can then resolve"() {
        given:
        DynamicFinder.registerNewMethodExpression(ZzzCustomTest)

        when:
        // the registered alternative is matched against the CLASS'S OWN SIMPLE NAME, not some
        // separately-declared suffix string - same convention every built-in operator uses
        // (e.g. MethodExpression.GreaterThan's suffix is "GreaterThan", not a custom string).
        MatchSpec spec = DynamicFinder.buildMatchSpec("findBy", "findByTitleZzzCustomTest", 1)

        then:
        spec.methodCallExpressions[0] instanceof ZzzCustomTest
        spec.propertyNames == ['title']
    }

    static class ZzzCustomTest extends MethodExpression {
        ZzzCustomTest(Class targetClass, String propertyName) {
            super(targetClass, propertyName)
        }

        @Override
        Query.Criterion createCriterion() {
            return org.grails.datastore.mapping.query.Restrictions.eq(propertyName, arguments[0])
        }
    }

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
    PersistentProperty authorProperty = Stub(PersistentProperty) {
        getName() >> 'author'
        getType() >> String
    }
    PersistentProperty activeProperty = Stub(PersistentProperty) {
        getName() >> 'active'
        getType() >> Boolean
    }
    PersistentProperty tagsProperty = Stub(Basic) {
        getName() >> 'tags'
        getType() >> List
    }
    MappingContext mappingContext = Stub(MappingContext)
    PersistentEntity persistentEntity = Stub(PersistentEntity)

    // Wired in setup() rather than inline field initializers: mappingContext.getPersistentEntity()
    // needs to return `persistentEntity`, and persistentEntity.getMappingContext() needs to return
    // `mappingContext` right back - a genuine circular reference that inline field initializers
    // can't express (a Stub(...) { } closure captures the RHS field's value at the moment its own
    // field initializer runs, so whichever field is declared second would still be null).
    void setup() {
        mappingContext.getConversionService() >> new DefaultConversionService()
        mappingContext.getPersistentEntity(FinderTestEntity.name) >> persistentEntity
        persistentEntity.getIdentity() >> idProperty
        persistentEntity.getMappingContext() >> mappingContext
        persistentEntity.getPropertyByName('name') >> nameProperty
        persistentEntity.getPropertyByName('age') >> ageProperty
        persistentEntity.getPropertyByName('author') >> authorProperty
        persistentEntity.getPropertyByName('active') >> activeProperty
        persistentEntity.getPropertyByName('tags') >> tagsProperty
    }

    DynamicFinder findByGrammar = new DynamicFinder(FIND_BY_PATTERN, OPERATORS, mappingContext, false)
    DynamicFinder findByBooleanGrammar = new DynamicFinder(FIND_BY_BOOLEAN_PATTERN, OPERATORS, mappingContext, true)

    @Unroll
    void "isMethodMatch('#methodName') == #matches"() {
        expect:
        findByGrammar.isMethodMatch(methodName) == matches

        where:
        methodName            | matches
        'findByName'          | true
        'findByNameAndAge'    | true
        'somethingElse'       | false
    }

    void "firstExpressionIsRequiredBoolean reflects the constructor flag"() {
        expect:
        !findByGrammar.firstExpressionIsRequiredBoolean()
        findByBooleanGrammar.firstExpressionIsRequiredBoolean()
    }

    void "createFinderInvocation resolves a single equality expression"() {
        when:
        DynamicFinderInvocation invocation = findByGrammar.createFinderInvocation(FinderTestEntity, 'findByName', null, ['Bob'] as Object[])

        then:
        invocation.expressions.size() == 1
        invocation.expressions[0] instanceof MethodExpression.Equal
        invocation.expressions[0].propertyName == 'name'
        invocation.arguments.length == 0
    }

    void "createFinderInvocation splits an And junction into two expressions and records the operator"() {
        when:
        DynamicFinderInvocation invocation = findByGrammar.createFinderInvocation(
                FinderTestEntity, 'findByNameAndAge', null, ['Bob', 42] as Object[])

        then:
        invocation.operator == 'And'
        invocation.expressions.size() == 2
        invocation.expressions[0].propertyName == 'name'
        invocation.expressions[1].propertyName == 'age'
    }

    void "createFinderInvocation splits an Or junction and records the Or operator"() {
        when:
        DynamicFinderInvocation invocation = findByGrammar.createFinderInvocation(
                FinderTestEntity, 'findByNameOrAge', null, ['Bob', 42] as Object[])

        then:
        invocation.operator == 'Or'
        invocation.expressions.size() == 2
    }

    void "createFinderInvocation wraps a Not-suffixed clause's expression so its criterion negates"() {
        when:
        DynamicFinderInvocation invocation = findByGrammar.createFinderInvocation(
                FinderTestEntity, 'findByAuthorNot', null, ['Bob'] as Object[])
        Query.Criterion criterion = invocation.expressions[0].createCriterion()

        then:
        invocation.expressions.size() == 1
        criterion instanceof Query.Negation
    }

    void "createFinderInvocation throws MissingMethodException when too few arguments are supplied"() {
        when:
        findByGrammar.createFinderInvocation(FinderTestEntity, 'findByNameAndAge', null, ['Bob'] as Object[])

        then:
        thrown(MissingMethodException)
    }

    void "createFinderInvocation rethrows a ConversionException as MissingMethodException for a non-Basic property"() {
        given:
        // "age" (a plain Integer, non-Basic property) with an argument that genuinely needs
        // conversion - convertArguments only ever calls the ConversionService when the argument's
        // type isn't already assignable to the property's type, so an already-matching argument
        // (e.g. a String for a String property) would never reach the ConversionService at all.
        mappingContext.getConversionService() >> Stub(org.springframework.core.convert.ConversionService) {
            canConvert(_, _) >> true
            convert(_, _) >> { throw new ConverterNotFoundException(TypeDescriptor.valueOf(String), TypeDescriptor.valueOf(Integer)) }
        }

        when:
        findByGrammar.createFinderInvocation(FinderTestEntity, 'findByAge', null, ['not-a-number'] as Object[])

        then:
        thrown(MissingMethodException)
    }

    void "createFinderInvocation swallows a ConversionException for a Basic property"() {
        given:
        // Real Basic-typed properties always model a to-many/collection shape (Basic extends
        // ToMany), and MethodExpression's own conversion logic skips the ConversionService
        // entirely for a raw collection-typed property - so a scalar-typed Basic stub is used
        // here purely to exercise DynamicFinder's own "instanceof Basic" dispatch in isolation,
        // not to model a realistic Basic property.
        PersistentProperty basicFlagProperty = Stub(Basic) {
            getName() >> 'flag'
            getType() >> Boolean
        }
        persistentEntity.getPropertyByName('flag') >> basicFlagProperty
        mappingContext.getConversionService() >> Stub(org.springframework.core.convert.ConversionService) {
            canConvert(_, _) >> true
            convert(_, _) >> { throw new ConverterNotFoundException(TypeDescriptor.valueOf(String), TypeDescriptor.valueOf(Boolean)) }
        }

        when:
        DynamicFinderInvocation invocation = findByGrammar.createFinderInvocation(FinderTestEntity, 'findByFlag', null, ['not-a-boolean'] as Object[])

        then:
        noExceptionThrown()
        invocation.expressions.size() == 1
    }

    void "createFinderInvocation resolves the boolean-plus-By form, defaulting the boolean argument to TRUE"() {
        when:
        DynamicFinderInvocation invocation = findByBooleanGrammar.createFinderInvocation(
                FinderTestEntity, 'findActiveByName', null, ['Bob'] as Object[])

        then:
        invocation.expressions.size() == 2
        invocation.expressions[0].propertyName == 'active'
        invocation.expressions[0].getArguments()[0] == Boolean.TRUE
        invocation.expressions[1].propertyName == 'name'
    }

    void "createFinderInvocation resolves the lone-boolean form with no By clause and no consumed arguments"() {
        when:
        DynamicFinderInvocation invocation = findByBooleanGrammar.createFinderInvocation(
                FinderTestEntity, 'findActive', null, [] as Object[])

        then:
        invocation.expressions.size() == 1
        invocation.expressions[0].propertyName == 'active'
        invocation.expressions[0].getArguments()[0] == Boolean.TRUE
    }

    void "createFinderInvocation flips the boolean argument to FALSE for a Not-prefixed boolean clause"() {
        when:
        DynamicFinderInvocation invocation = findByBooleanGrammar.createFinderInvocation(
                FinderTestEntity, 'findNotActiveByName', null, ['Bob'] as Object[])

        then:
        invocation.expressions[0].propertyName == 'active'
        invocation.expressions[0].getArguments()[0] == Boolean.FALSE
    }

    void "getJunction builds a Conjunction for the And operator"() {
        given:
        MethodExpression name = new MethodExpression.Equal('name')
        name.setArguments(['Bob'] as Object[])
        MethodExpression age = new MethodExpression.Equal('age')
        age.setArguments([42] as Object[])
        DynamicFinderInvocation invocation = new DynamicFinderInvocation(FinderTestEntity, 'findByNameAndAge', [] as Object[], [name, age], null, 'And')

        when:
        Query.Junction junction = findByGrammar.getJunction(invocation)

        then:
        junction instanceof Query.Conjunction
        junction.criteria.size() == 2
    }

    void "getJunction builds a Disjunction for the Or operator when no boolean clause is required"() {
        given:
        MethodExpression name = new MethodExpression.Equal('name')
        name.setArguments(['Bob'] as Object[])
        MethodExpression age = new MethodExpression.Equal('age')
        age.setArguments([42] as Object[])
        DynamicFinderInvocation invocation = new DynamicFinderInvocation(FinderTestEntity, 'findByNameOrAge', [] as Object[], [name, age], null, 'Or')

        when:
        Query.Junction junction = findByGrammar.getJunction(invocation)

        then:
        junction instanceof Query.Disjunction
        junction.criteria.size() == 2
    }

    void "getJunction keeps a required boolean clause AND'd even when the rest of the clauses use Or"() {
        given:
        MethodExpression active = new MethodExpression.Equal('active')
        active.setArguments([Boolean.TRUE] as Object[])
        MethodExpression name = new MethodExpression.Equal('name')
        name.setArguments(['Bob'] as Object[])
        MethodExpression age = new MethodExpression.Equal('age')
        age.setArguments([42] as Object[])
        DynamicFinderInvocation invocation = new DynamicFinderInvocation(
                FinderTestEntity, 'findActiveByNameOrAge', [] as Object[], [active, name, age], null, 'Or')

        when:
        Query.Junction junction = findByBooleanGrammar.getJunction(invocation)

        then:
        junction instanceof Query.Conjunction
        junction.criteria.size() == 2
        junction.criteria[1] instanceof Query.Disjunction
        junction.criteria[1].criteria.size() == 2
    }

    void "populateArgumentsForCriteria(Query) is a no-op for a null argument map"() {
        given:
        Query query = Mock(Query)

        when:
        DynamicFinder.populateArgumentsForCriteria(FinderTestEntity, query, null)

        then:
        0 * query._
    }

    void "populateArgumentsForCriteria(Query) applies max and offset, converting String values"() {
        given:
        Query query = Mock(Query) {
            getEntity() >> Stub(PersistentEntity) { getMappingContext() >> mappingContext }
        }

        when:
        DynamicFinder.populateArgumentsForCriteria(FinderTestEntity, query, [max: '10', offset: '5'])

        then:
        1 * query.max(10)
        1 * query.offset(5)
    }

    void "populateArgumentsForCriteria(Query) applies a String sort, defaulting to ascending and ignoring case"() {
        given:
        Query query = Mock(Query) {
            getEntity() >> Stub(PersistentEntity) { getMappingContext() >> mappingContext }
        }

        when:
        DynamicFinder.populateArgumentsForCriteria(FinderTestEntity, query, [sort: 'age'])

        then:
        1 * query.order({ Query.Order order ->
            order.property == 'age' && order.direction == Query.Order.Direction.ASC && order.ignoreCase
        })
    }

    void "populateArgumentsForCriteria(Query) applies a descending order and honours ignoreCase: false"() {
        given:
        Query query = Mock(Query) {
            getEntity() >> Stub(PersistentEntity) { getMappingContext() >> mappingContext }
        }

        when:
        DynamicFinder.populateArgumentsForCriteria(FinderTestEntity, query, [sort: 'age', order: 'desc', ignoreCase: false])

        then:
        1 * query.order({ Query.Order order ->
            order.property == 'age' && order.direction == Query.Order.Direction.DESC && !order.ignoreCase
        })
    }

    void "populateArgumentsForCriteria(Query) applies a multi-field sort Map"() {
        given:
        Query query = Mock(Query) {
            getEntity() >> Stub(PersistentEntity) { getMappingContext() >> mappingContext }
        }

        when:
        DynamicFinder.populateArgumentsForCriteria(FinderTestEntity, query, [sort: [name: 'asc', age: 'desc']])

        then:
        1 * query.order({ Query.Order order -> order.property == 'name' && order.direction == Query.Order.Direction.ASC })
        1 * query.order({ Query.Order order -> order.property == 'age' && order.direction == Query.Order.Direction.DESC })
    }

    @Unroll
    void "populateArgumentsForCriteria(Query) applies fetch entry #fetchValue as #expectedMethod"() {
        given:
        Query query = Mock(Query) {
            getEntity() >> Stub(PersistentEntity) { getMappingContext() >> mappingContext }
        }

        when:
        DynamicFinder.populateArgumentsForCriteria(FinderTestEntity, query, [fetch: [author: fetchValue]])

        then:
        1 * query."$expectedMethod"('author')

        where:
        fetchValue          | expectedMethod
        FetchType.EAGER     | 'join'
        FetchType.LAZY      | 'select'
        'eager'             | 'join'
        'join'              | 'join'
        'lazy'              | 'select'
        'select'            | 'select'
        'unrecognised'      | 'select'
    }

    void "populateArgumentsForCriteria(Query) applies an explicit JoinType fetch entry via the two-arg join"() {
        given:
        Query query = Mock(Query) {
            getEntity() >> Stub(PersistentEntity) { getMappingContext() >> mappingContext }
        }

        when:
        DynamicFinder.populateArgumentsForCriteria(FinderTestEntity, query, [fetch: [author: JoinType.LEFT]])

        then:
        1 * query.join('author', JoinType.LEFT)
    }

    void "populateArgumentsForCriteria(Query) applies cache and lock flags"() {
        given:
        Query query = Mock(Query) {
            getEntity() >> Stub(PersistentEntity) { getMappingContext() >> mappingContext }
        }

        when:
        DynamicFinder.populateArgumentsForCriteria(FinderTestEntity, query, [cache: true, lock: true])

        then:
        1 * query.cache(true)
        1 * query.lock(true)
    }

    void "populateArgumentsForCriteria(Query) passes the raw argument map to a QueryArgumentsAware query"() {
        given:
        Query query = Mock(Query, additionalInterfaces: [QueryArgumentsAware]) {
            getEntity() >> Stub(PersistentEntity) { getMappingContext() >> mappingContext }
        }
        Map argMap = [sort: 'name']

        when:
        DynamicFinder.populateArgumentsForCriteria(FinderTestEntity, query, argMap)

        then:
        1 * ((QueryArgumentsAware) query).setArguments(argMap)
    }

    void "populateArgumentsForCriteria(BuildableCriteria) is a no-op for a null argument map"() {
        given:
        BuildableCriteria criteria = Mock(BuildableCriteria)

        when:
        DynamicFinder.populateArgumentsForCriteria(criteria, null)

        then:
        0 * criteria._
    }

    void "populateArgumentsForCriteria(BuildableCriteria) applies cache and sort, but has no lock/max/offset support"() {
        given:
        BuildableCriteria criteria = Mock(BuildableCriteria)

        when:
        DynamicFinder.populateArgumentsForCriteria(criteria, [cache: true, sort: 'name'])

        then:
        1 * criteria.cache(true)
        1 * criteria.order({ Query.Order order -> order.property == 'name' })
        0 * criteria.lock(_)
        0 * criteria.max(_)
        0 * criteria.offset(_)
    }

    void "populateArgumentsForCriteria(BuildableCriteria) applies fetch strategies for EAGER and LAZY associations"() {
        given:
        BuildableCriteria criteria = Mock(BuildableCriteria)

        when:
        DynamicFinder.populateArgumentsForCriteria(criteria, [fetch: [author: FetchType.EAGER, tags: FetchType.LAZY]])

        then:
        1 * criteria.join('author')
        1 * criteria.select('tags')
    }

    void "populateArgumentsForCriteria(BuildableCriteria) applies a multi-field sort Map, but ignores each entry's own direction"() {
        given:
        // Real, surprising inconsistency between the two populateArgumentsForCriteria overloads:
        // the Query-overload's multi-field sort (applySortForMap, tested above) reads EACH entry's
        // own value ('asc'/'desc') to pick that field's direction. This BuildableCriteria-overload
        // has its own separate inline loop that does NOT do that - it reads sortMap.get(key) into
        // `value` but never uses it, applying a single direction (from the top-level "order" arg,
        // defaulting to ascending) uniformly to every entry in the map instead. A per-field 'desc'
        // here is silently ignored.
        BuildableCriteria criteria = Mock(BuildableCriteria)

        when:
        DynamicFinder.populateArgumentsForCriteria(criteria, [sort: [name: 'asc', age: 'desc']])

        then:
        1 * criteria.order({ Query.Order order -> order.property == 'name' && order.direction == Query.Order.Direction.ASC })
        1 * criteria.order({ Query.Order order -> order.property == 'age' && order.direction == Query.Order.Direction.ASC })
    }

    void "populateArgumentsForCriteria(BuildableCriteria) applies the top-level order argument uniformly to every sort Map entry"() {
        given:
        BuildableCriteria criteria = Mock(BuildableCriteria)

        when:
        DynamicFinder.populateArgumentsForCriteria(criteria, [sort: [name: 'asc', age: 'desc'], order: 'desc'])

        then:
        1 * criteria.order({ Query.Order order -> order.property == 'name' && order.direction == Query.Order.Direction.DESC })
        1 * criteria.order({ Query.Order order -> order.property == 'age' && order.direction == Query.Order.Direction.DESC })
    }

    void "populateArgumentsForCriteria(BuildableCriteria) passes the raw argument map to a QueryArgumentsAware criteria"() {
        given:
        BuildableCriteria criteria = Mock(BuildableCriteria, additionalInterfaces: [QueryArgumentsAware])
        Map argMap = [sort: 'name']

        when:
        DynamicFinder.populateArgumentsForCriteria(criteria, argMap)

        then:
        1 * ((QueryArgumentsAware) criteria).setArguments(argMap)
    }

    void "getFetchMode resolves the FetchType for every recognised alias, defaulting to LAZY"() {
        expect:
        DynamicFinder.getFetchMode(input) == expected

        where:
        input           | expected
        FetchType.EAGER | FetchType.EAGER
        FetchType.LAZY  | FetchType.LAZY
        'eager'         | FetchType.EAGER
        'join'          | FetchType.EAGER
        'lazy'          | FetchType.LAZY
        'select'        | FetchType.LAZY
        'anything-else' | FetchType.LAZY
        null            | FetchType.LAZY
    }

    void "applySortForMap applies ascending/descending per-entry order values, defaulting to ascending"() {
        given:
        Query query = Mock(Query)

        when:
        DynamicFinder.applySortForMap(query, [name: 'asc', age: 'desc', title: null], true)

        then:
        1 * query.order({ Query.Order o -> o.property == 'name' && o.direction == Query.Order.Direction.ASC })
        1 * query.order({ Query.Order o -> o.property == 'age' && o.direction == Query.Order.Direction.DESC })
        1 * query.order({ Query.Order o -> o.property == 'title' && o.direction == Query.Order.Direction.ASC })
    }

    void "applyDetachedCriteria is a no-op for a null detached criteria"() {
        given:
        Query query = Mock(Query)

        when:
        DynamicFinder.applyDetachedCriteria(query, null)

        then:
        0 * query._
    }

    void "applyDetachedCriteria merges fetch strategies, criteria, projections and orders onto the live query"() {
        given:
        Query query = Mock(Query)
        MethodExpression.Equal expression = new MethodExpression.Equal('name')
        expression.setArguments(['Bob'] as Object[])
        Query.Criterion criterion = expression.createCriterion()
        Query.Projection projection = Mock(Query.Projection)
        Query.Order order = Query.Order.asc('age')
        AbstractDetachedCriteria detachedCriteria = Stub(AbstractDetachedCriteria) {
            getFetchStrategies() >> [author: FetchType.EAGER, tags: FetchType.LAZY]
            getJoinTypes() >> [author: JoinType.LEFT]
            getCriteria() >> [criterion]
            getProjections() >> [projection]
            getOrders() >> [order]
        }
        Query.ProjectionList projectionList = Mock(Query.ProjectionList)
        query.projections() >> projectionList

        when:
        DynamicFinder.applyDetachedCriteria(query, detachedCriteria)

        then:
        1 * query.join('author', JoinType.LEFT)
        1 * query.select('tags')
        1 * query.add(criterion)
        1 * projectionList.add(projection)
        1 * query.order(order)
    }

    void "applyDetachedCriteria joins without an explicit JoinType when none is configured for the association"() {
        given:
        Query query = Mock(Query)
        AbstractDetachedCriteria detachedCriteria = Stub(AbstractDetachedCriteria) {
            getFetchStrategies() >> [author: FetchType.EAGER]
            getJoinTypes() >> [:]
            getCriteria() >> []
            getProjections() >> []
            getOrders() >> []
        }

        when:
        DynamicFinder.applyDetachedCriteria(query, detachedCriteria)

        then:
        1 * query.join('author')
        0 * query.join('author', _)
    }

    void "applyAdditionalCriteria is a no-op for a null closure"() {
        given:
        Query query = Mock(Query)

        when:
        DynamicFinder.applyAdditionalCriteria(query, null)

        then:
        0 * query._
    }

    void "applyAdditionalCriteria builds a real CriteriaBuilder from the query and adds the closure's criterion"() {
        given:
        PersistentEntity entity = Stub(PersistentEntity) {
            getJavaClass() >> FinderTestEntity
            getPropertyByName('name') >> nameProperty
        }
        Session session = Stub(Session) {
            getMappingContext() >> mappingContext
        }
        Query query = Mock(Query) {
            getEntity() >> entity
            getSession() >> session
        }
        mappingContext.getPersistentEntity(FinderTestEntity.name) >> entity

        when:
        DynamicFinder.applyAdditionalCriteria(query, { eq('name', 'Bob') })

        then:
        1 * query.add({ Query.Criterion criterion ->
            criterion instanceof Query.Equals && criterion.property == 'name' && criterion.value == 'Bob'
        })
    }

    void "buildQuery creates a query from the session, applies additional criteria, detached criteria, arguments and the junction"() {
        given:
        Query query = Mock(Query) {
            getEntity() >> Stub(PersistentEntity) { getMappingContext() >> mappingContext }
        }
        Session session = Stub(Session) {
            createQuery(FinderTestEntity) >> query
            getMappingContext() >> mappingContext
        }
        DynamicFinderInvocation invocation = findByGrammar.createFinderInvocation(
                FinderTestEntity, 'findByName', null, ['Bob'] as Object[])

        when:
        Query result = findByGrammar.buildQuery(invocation, session)

        then:
        result.is(query)
        1 * query.add({ it instanceof Query.Conjunction })
    }

    void "buildQuery consumes the remaining argument map via configureQueryWithArguments"() {
        given:
        Query query = Mock(Query) {
            getEntity() >> Stub(PersistentEntity) { getMappingContext() >> mappingContext }
        }
        Session session = Stub(Session) {
            createQuery(FinderTestEntity) >> query
            getMappingContext() >> mappingContext
        }
        DynamicFinderInvocation invocation = findByGrammar.createFinderInvocation(
                FinderTestEntity, 'findByName', null, ['Bob', [max: 5]] as Object[])

        when:
        findByGrammar.buildQuery(invocation, session)

        then:
        1 * query.max(5)
    }
}

/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  'License'); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  'AS IS' BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.grails.datastore.gorm.finders

import grails.gorm.annotation.Entity
import jakarta.persistence.FetchType
import org.grails.datastore.mapping.query.Query
import org.grails.datastore.mapping.query.api.BuildableCriteria
import org.grails.datastore.mapping.simple.SimpleMapDatastore
import spock.lang.AutoCleanup
import spock.lang.Specification
import spock.lang.Unroll

/**
 * {@link DynamicFinderSpec} (pre-existing) covers only {@code buildMatchSpec}. This spec targets
 * the rest of {@code DynamicFinder} - the core method-name-parsing/expression-building pipeline
 * (exercised through real dynamic finder calls on a live {@code SimpleMapDatastore}-backed
 * entity, the same way GORM actually uses it) and the static
 * {@code populateArgumentsForCriteria}/{@code applyDetachedCriteria}/{@code getFetchMode} argument
 * handling helpers.
 */
class DynamicFinderCoverageSpec extends Specification {

    @AutoCleanup
    SimpleMapDatastore datastore = new SimpleMapDatastore(DynamicFinderThing, DynamicFinderOwner, DynamicFinderCompositeThing)

    def setup() {
        new DynamicFinderThing(name: 'Alice', age: 30, title: 'Engineer').save(flush: true)
        new DynamicFinderThing(name: 'Bob', age: 25, title: 'Manager').save(flush: true)
        new DynamicFinderThing(name: 'Charlie', age: 35, title: 'Engineer').save(flush: true)
    }

    void "a single Equal expression resolves via the default findMethodExpression clause"() {
        expect:
        DynamicFinderThing.findByName('Alice').name == 'Alice'
    }

    void "GreaterThan/LessThan/Between expressions parse their comparison clauses"() {
        expect:
        DynamicFinderThing.findAllByAgeGreaterThan(28)*.name.sort() == ['Alice', 'Charlie']
        DynamicFinderThing.findAllByAgeLessThan(28)*.name == ['Bob']
        DynamicFinderThing.findAllByAgeBetween(26, 34)*.name == ['Alice']
    }

    void "Like/InList/NotEqual expressions parse their clauses"() {
        expect:
        DynamicFinderThing.findAllByTitleLike('Eng%')*.name.sort() == ['Alice', 'Charlie']
        DynamicFinderThing.findAllByNameInList(['Alice', 'Bob'])*.name.sort() == ['Alice', 'Bob']
        DynamicFinderThing.findAllByNameNotEqual('Alice')*.name.sort() == ['Bob', 'Charlie']
    }

    void "IsNull/IsNotNull expressions parse their clauses"() {
        given:
        new DynamicFinderThing(name: 'NoTitle', age: 40).save(flush: true)

        expect:
        DynamicFinderThing.findAllByTitleIsNull()*.name == ['NoTitle']
        DynamicFinderThing.findAllByTitleIsNotNull().size() == 3
    }

    void "a Not-negated clause inverts the underlying expression's criterion"() {
        expect:
        DynamicFinderThing.findAllByNameNot('Alice')*.name.sort() == ['Bob', 'Charlie']
    }

    void "an And-combined multi-clause query requires every expression to match"() {
        expect:
        DynamicFinderThing.findAllByTitleAndAgeGreaterThan('Engineer', 32)*.name == ['Charlie']
    }

    void "an Or-combined multi-clause query requires any expression to match"() {
        expect:
        DynamicFinderThing.findAllByNameOrName('Alice', 'Bob')*.name.sort() == ['Alice', 'Bob']
    }

    void "invoking a dynamic finder with too few arguments throws MissingMethodException"() {
        when:
        DynamicFinderThing.findByNameAndAge('Alice')

        then:
        thrown(MissingMethodException)
    }

    void "invoking a dynamic finder with an argument that fails conversion throws MissingMethodException"() {
        when:
        DynamicFinderThing.findByAgeGreaterThan('not-a-number')

        then:
        thrown(MissingMethodException)
    }

    void "list(Map) sorts by a single property name, ascending or descending"() {
        expect:
        DynamicFinderThing.list(sort: 'age')*.name == ['Bob', 'Alice', 'Charlie']
        DynamicFinderThing.list(sort: 'age', order: 'desc')*.name == ['Charlie', 'Alice', 'Bob']
    }

    void "list(Map) trims surrounding whitespace on the order direction"() {
        expect:
        DynamicFinderThing.list(sort: 'age', order: ' DESC ')*.name == ['Charlie', 'Alice', 'Bob']
        DynamicFinderThing.list(sort: 'age', order: ' asc ')*.name == ['Bob', 'Alice', 'Charlie']
        DynamicFinderThing.list(sort: [age: ' DESC '])*.age == [35, 30, 25]
        DynamicFinderThing.findAllByTitle('Engineer', [sort: 'age', order: ' DESC '])*.name == ['Charlie', 'Alice']
    }

    @Unroll
    void "list(Map) rejects order direction #description without echoing it"() {
        when:
        DynamicFinderThing.list(sort: 'age', order: order)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == 'Invalid sort direction'

        where:
        order        | description
        'sideways'   | 'that is not asc or desc'
        'desc extra' | 'carrying extra tokens'
        'descending' | 'that is a longer word'
    }

    void "list(Map) sorts by a Map of property to direction"() {
        expect:
        DynamicFinderThing.list(sort: [age: 'asc'])*.name == ['Bob', 'Alice', 'Charlie']
    }

    void "list(Map) with no explicit sort falls back to ordering by identity"() {
        expect:
        DynamicFinderThing.list().size() == 3
    }

    void "list(Map) with an order but no explicit sort defaults the sort to the entity's identity property"() {
        expect: "no sort: given, but order: is - the identity property (id) becomes the sort key"
        DynamicFinderThing.list(order: 'asc')*.id == DynamicFinderThing.list(order: 'asc')*.id.sort()
        DynamicFinderThing.list(order: 'desc')*.id == DynamicFinderThing.list(order: 'asc')*.id.reverse()
    }

    void "list(Map) applies fetch strategy from a FetchType map without throwing"() {
        expect:
        DynamicFinderThing.list(fetch: [name: FetchType.EAGER]).size() == 3
        DynamicFinderThing.list(fetch: [name: FetchType.LAZY]).size() == 3
    }

    void "list(Map) applies fetch strategy from a plain string value via getFetchMode"() {
        expect:
        DynamicFinderThing.list(fetch: [name: 'join']).size() == 3
        DynamicFinderThing.list(fetch: [name: 'select']).size() == 3
    }

    void "getFetchMode maps known aliases to EAGER/LAZY and defaults unknown values to LAZY"() {
        expect:
        DynamicFinder.getFetchMode('EAGER') == FetchType.EAGER
        DynamicFinder.getFetchMode('join') == FetchType.EAGER
        DynamicFinder.getFetchMode('LAZY') == FetchType.LAZY
        DynamicFinder.getFetchMode('select') == FetchType.LAZY
        DynamicFinder.getFetchMode('anything-else') == FetchType.LAZY
        DynamicFinder.getFetchMode(null) == FetchType.LAZY
    }

    void "list(Map) applies the cache argument without throwing"() {
        expect:
        DynamicFinderThing.list(cache: true).size() == 3
    }

    void "where{}.list() applies detached criteria fetch strategies, criteria and orders"() {
        // operator-style where{} DSL (e.g. "age > 20") relies on a compile-time AST transform
        // this plain test module doesn't apply; the method-call criteria DSL (gt(...) etc.) works
        // without it, same as GormStaticApiSpec's where{} tests use.
        expect:
        DynamicFinderThing.where { gt('age', 20) }.join('name').order('age').list()*.name == ['Bob', 'Alice', 'Charlie']
    }

    void "registerNewMethodExpression makes a custom MethodExpression clause resolvable"() {
        given: "the finder clause keyword is the registered class's simple name"
        DynamicFinder.registerNewMethodExpression(AlwaysTrue)

        expect:
        DynamicFinderThing.findAllByNameAlwaysTrue('ignored').size() == 3
    }

    static class AlwaysTrue extends MethodExpression {
        AlwaysTrue(Class clazz, String propertyName) {
            super(clazz, propertyName)
            this.argumentsRequired = 1
        }

        @Override
        org.grails.datastore.mapping.query.Query.Criterion createCriterion() {
            new org.grails.datastore.mapping.query.Query.IsNotNull(propertyName)
        }
    }

    void "populateArgumentsForCriteria(BuildableCriteria, Map) trims and validates the order direction"() {
        given:
        def api = new org.grails.datastore.gorm.GormStaticApi(DynamicFinderThing, datastore, [])
        def criteria = api.createCriteria()

        when:
        DynamicFinder.populateArgumentsForCriteria(criteria, [sort: 'age', order: ' DESC '])

        then:
        criteria.list(null)*.age == [35, 30, 25]

        when:
        DynamicFinder.populateArgumentsForCriteria(api.createCriteria(), [sort: 'age', order: 'sideways'])

        then:
        def e = thrown(IllegalArgumentException)
        e.message == 'Invalid sort direction'
    }

    void "populateArgumentsForCriteria(BuildableCriteria, Map) applies sort and order to a real criteria query"() {
        given: "not called from any production code path in this repo (every real caller uses the Query overload), but still a public static API worth covering directly. fetch/cache require an already-active query the bare CriteriaBuilder here doesn't have."
        def api = new org.grails.datastore.gorm.GormStaticApi(DynamicFinderThing, datastore, [])
        def criteria = api.createCriteria()

        expect:
        DynamicFinder.populateArgumentsForCriteria(criteria, null) == null

        when:
        DynamicFinder.populateArgumentsForCriteria(criteria, [
                sort : 'age',
                order: 'desc',
        ])

        then:
        notThrown(Exception)
    }

    void "populateArgumentsForCriteria(BuildableCriteria, Map) with an order but no explicit sort defaults the sort to the entity's identity property"() {
        given:
        def api = new org.grails.datastore.gorm.GormStaticApi(DynamicFinderThing, datastore, [])
        def criteria = api.createCriteria()

        when:
        DynamicFinder.populateArgumentsForCriteria(criteria, [order: 'desc'])

        then: "no explicit sort: was given, so the entity's identity (id) property became the sort key"
        notThrown(Exception)
        criteria.list(null)*.id == DynamicFinderThing.list(order: 'asc')*.id.reverse()
    }

    void "populateArgumentsForCriteria(BuildableCriteria, Map) sorts by a Map of property to direction"() {
        given:
        def api = new org.grails.datastore.gorm.GormStaticApi(DynamicFinderThing, datastore, [])
        def criteria = api.createCriteria()

        when:
        DynamicFinder.populateArgumentsForCriteria(criteria, [sort: [age: 'asc']])

        then:
        notThrown(Exception)
    }

    void "the DatastoreResolver+MappingContext constructor wires a finder that is usable without a bound datastore"() {
        given:
        def finder = new FindByFinder(new org.grails.datastore.gorm.DatastoreResolver() {
            @Override org.grails.datastore.mapping.core.Datastore resolve() { datastore }
        }, datastore.mappingContext)

        expect:
        finder.isMethodMatch('findByName')
    }

    void "the MappingContext-only constructor wires a finder without any datastore or resolver"() {
        given:
        def finder = new FindByFinder(datastore.mappingContext)

        expect:
        finder.isMethodMatch('findByName')
    }

    void "invoke(Class, methodName, DetachedCriteria, arguments) merges the detached criteria into the finder invocation"() {
        given:
        def finder = new FindAllByFinder(datastore)
        def detached = new grails.gorm.DetachedCriteria(DynamicFinderThing).build { gt('age', 20) }

        when:
        def results = finder.invoke(DynamicFinderThing, 'findAllByTitle', detached, ['Engineer'] as Object[])

        then:
        results*.name.sort() == ['Alice', 'Charlie']
    }

    void "list(sort) still sorts by a mapped property"() {
        expect:
        DynamicFinderThing.list(sort: 'age')*.age == [25, 30, 35]
    }

    void "list(sort) accepts the identity and version properties"() {
        expect:
        DynamicFinderThing.list(sort: 'id')*.id == DynamicFinderThing.list()*.id.sort()
        DynamicFinderThing.list(sort: 'id', order: 'desc')*.id == DynamicFinderThing.list()*.id.sort().reverse()
        DynamicFinderThing.list(sort: 'version').size() == 3
    }

    @Unroll
    void "list(sort) rejects a sort key #description with a message that does not echo it"() {
        when:
        DynamicFinderThing.list(sort: sort)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == 'Invalid sort property'

        where:
        sort           | description
        'age, id'      | 'carrying a second expression'
        'age desc'     | 'carrying a direction'
        'upper(age)'   | 'wrapped in a function call'
        "age'"         | 'containing a quote'
        ''             | 'that is empty'
        ' '            | 'that is blank'
        'notAProperty' | 'naming an unknown property'
    }

    void "dynamic finders and where queries reject a bare name that is not a property"() {
        when:
        DynamicFinderThing.findAllByTitle('Engineer', [sort: 'notAProperty'])

        then:
        def finder = thrown(IllegalArgumentException)
        finder.message == 'Invalid sort property'

        when:
        DynamicFinderThing.where { eq('title', 'Engineer') }.list(sort: 'notAProperty')

        then:
        def where = thrown(IllegalArgumentException)
        where.message == 'Invalid sort property'
    }

    void "list(sort) accepts a path through an association, including the associated identity"() {
        // SimpleMapQuery cannot evaluate a nested sort itself, so no owners are persisted: the
        // empty result shows the key was accepted by every argument-handling entry point
        expect:
        DynamicFinderOwner.list(sort: 'thing.name') == []
        DynamicFinderOwner.list(sort: 'thing.id') == []
        DynamicFinderOwner.findAllByName('nobody', [sort: 'thing.age']) == []
        DynamicFinderOwner.where { eq('name', 'nobody') }.list(sort: 'thing.name') == []
    }

    void "list(sort) leaves a dotted key whose root is not a persistent property to the underlying query so aliases keep working"() {
        // c1 is what a where-query alias (def c1 = thing) or createAlias('thing', 'c1') looks like;
        // only a dotted key gets this treatment, a bare unknown name is rejected above
        expect:
        DynamicFinderOwner.list(sort: 'c1.name') == []
        DynamicFinderOwner.list(sort: 'c1.name', order: 'desc') == []
        DynamicFinderOwner.findAllByName('nobody', [sort: 'c1.name']) == []
        DynamicFinderOwner.where { eq('name', 'nobody') }.list(sort: 'c1.name') == []
    }

    @Unroll
    void "list(sort) rejects #description"() {
        when:
        DynamicFinderOwner.list(sort: sort)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == 'Invalid sort property'

        where:
        sort             | description
        'thing.notAProp' | 'an unknown property of the associated entity'
        'name.length'    | 'a segment beneath a property that is not an association'
        'tags.value'     | 'a segment beneath a basic collection, which has no associated entity'
        'thing.id.value' | 'a segment beneath an identity'
    }

    void "list(sort) accepts the members of a composite identity"() {
        expect:
        DynamicFinderCompositeThing.list(sort: 'a') == []
        DynamicFinderCompositeThing.list(sort: [a: 'asc', b: 'desc']) == []
        DynamicFinderCompositeThing.list(order: 'desc') == []
    }

    void "list(Map) takes each sort map entry's direction from its value rather than the order argument"() {
        expect:
        DynamicFinderThing.list(sort: [age: 'desc'])*.age == [35, 30, 25]
        DynamicFinderThing.list(sort: [age: 'desc'], order: 'asc')*.age == [35, 30, 25]
        DynamicFinderThing.list(sort: [age: 'asc'], order: 'desc')*.age == [25, 30, 35]
        DynamicFinderThing.list(sort: [age: null], order: 'desc')*.age == [25, 30, 35]
    }

    void "populateArgumentsForCriteria(BuildableCriteria, Map) takes each sort map entry's direction from its value"() {
        given:
        def api = new org.grails.datastore.gorm.GormStaticApi(DynamicFinderThing, datastore, [])
        def descending = api.createCriteria()
        def ascending = api.createCriteria()

        when:
        DynamicFinder.populateArgumentsForCriteria(descending, [sort: [age: 'desc'], order: 'asc'])
        DynamicFinder.populateArgumentsForCriteria(ascending, [sort: [age: null], order: 'desc'])

        then:
        descending.list(null)*.age == [35, 30, 25]
        ascending.list(null)*.age == [25, 30, 35]
    }

    void "populateArgumentsForCriteria(BuildableCriteria, Map) validates sort keys the same way as the Query overload"() {
        given:
        def api = new org.grails.datastore.gorm.GormStaticApi(DynamicFinderOwner, datastore, [])

        when: 'a key carrying a second expression'
        DynamicFinder.populateArgumentsForCriteria(api.createCriteria(), [sort: ['name, id': 'asc']])

        then:
        def injected = thrown(IllegalArgumentException)
        injected.message == 'Invalid sort property'

        when: 'an unknown property beneath a known association'
        DynamicFinder.populateArgumentsForCriteria(api.createCriteria(), [sort: 'thing.notAProp'])

        then:
        def unknown = thrown(IllegalArgumentException)
        unknown.message == 'Invalid sort property'

        when: 'a bare name that is not a persistent property'
        DynamicFinder.populateArgumentsForCriteria(api.createCriteria(), [sort: 'notAProperty'])

        then:
        def bare = thrown(IllegalArgumentException)
        bare.message == 'Invalid sort property'

        when: 'a dotted key whose root is not a persistent property'
        DynamicFinder.populateArgumentsForCriteria(api.createCriteria(), [sort: 'c1.name', order: 'desc'])

        then:
        notThrown(IllegalArgumentException)
    }

    void "populateArgumentsForCriteria(BuildableCriteria, Map) falls back to the shape check when the criteria does not expose its entity"() {
        given: 'a BuildableCriteria that is neither a CriteriaBuilder nor a DetachedCriteria, so no mapping can be consulted'
        def criteria = Mock(BuildableCriteria)

        when: 'the key is not a property of anything, but is shaped like one'
        DynamicFinder.populateArgumentsForCriteria(criteria, [sort: 'notAProperty.either', order: ' DESC '])

        then: 'it is accepted on shape alone and the direction is still normalized'
        1 * criteria.order({ Query.Order o ->
            o.property == 'notAProperty.either' && o.direction == Query.Order.Direction.DESC && o.ignoreCase
        })

        when: 'the key carries a second expression'
        DynamicFinder.populateArgumentsForCriteria(criteria, [sort: 'name, id'])

        then:
        def e = thrown(IllegalArgumentException)
        e.message == 'Invalid sort property'
        0 * criteria.order(_)
    }
}

@Entity
class DynamicFinderThing {
    Long id
    String name
    Integer age
    String title
}

@Entity
class DynamicFinderOwner {
    Long id
    String name
    DynamicFinderThing thing
    static hasMany = [tags: String]
}

@Entity
class DynamicFinderCompositeThing {
    String a
    String b
    static mapping = {
        id composite: ['a', 'b']
    }
}

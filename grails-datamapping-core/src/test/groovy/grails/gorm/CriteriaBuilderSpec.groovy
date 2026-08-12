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
package grails.gorm

import org.grails.datastore.mapping.core.Session
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.model.types.Association
import org.grails.datastore.mapping.query.AssociationQuery
import org.grails.datastore.mapping.query.Query
import org.grails.datastore.mapping.query.QueryCreator
import org.grails.datastore.mapping.query.api.BuildableCriteria
import org.grails.datastore.mapping.query.api.QueryableCriteria
import spock.lang.Specification
import spock.lang.Unroll

/**
 * Exercises {@link CriteriaBuilder}, and through it its abstract superclass
 * {@link org.grails.datastore.gorm.query.criteria.AbstractCriteriaBuilder}, which cannot be
 * instantiated directly. Collaborators (MappingContext/PersistentEntity/QueryCreator/Query) are
 * mocked since this class's own responsibility is translating DSL calls into Query.Criterion
 * objects and delegating to a Query, not persistence itself.
 */
class CriteriaBuilderSpec extends Specification {

    PersistentProperty idProperty = Stub(PersistentProperty) {
        getName() >> 'id'
    }
    PersistentProperty nameProperty = Stub(PersistentProperty) {
        getName() >> 'name'
    }
    PersistentEntity persistentEntity = Stub(PersistentEntity) {
        getIdentity() >> idProperty
        getPropertyByName(_) >> nameProperty
    }
    MappingContext mappingContext = Stub(MappingContext) {
        getPersistentEntity(CriteriaBuilderTestPerson.name) >> persistentEntity
    }
    Query query = Mock(Query)
    QueryCreator queryCreator = Stub(QueryCreator) {
        createQuery(CriteriaBuilderTestPerson) >> query
        isSchemaless() >> false
    }

    CriteriaBuilder<CriteriaBuilderTestPerson> newBuilder() {
        def criteria = new CriteriaBuilder<CriteriaBuilderTestPerson>(CriteriaBuilderTestPerson, queryCreator, mappingContext)
        criteria.@query = query
        criteria
    }

    void "constructor rejects a null target class"() {
        when:
        new CriteriaBuilder(null, queryCreator, mappingContext)

        then:
        thrown(IllegalArgumentException)
    }

    void "constructor rejects a null mapping context"() {
        when:
        new CriteriaBuilder(CriteriaBuilderTestPerson, queryCreator, null)

        then:
        thrown(IllegalArgumentException)
    }

    void "constructor rejects a class the mapping context does not recognise as persistent"() {
        given:
        MappingContext unknownContext = Stub(MappingContext) {
            getPersistentEntity(_) >> null
        }

        when:
        new CriteriaBuilder(CriteriaBuilderTestPerson, queryCreator, unknownContext)

        then:
        IllegalArgumentException e = thrown()
        e.message.contains(CriteriaBuilderTestPerson.name)
    }

    void "getTargetClass returns the class the criteria was built for"() {
        expect:
        newBuilder().targetClass == CriteriaBuilderTestPerson
    }

    void "constructing from a Session resolves the mapping context and query creator from it"() {
        given:
        Session session = Stub(Session) {
            getMappingContext() >> mappingContext
        }

        when:
        def criteria = new CriteriaBuilder<CriteriaBuilderTestPerson>(CriteriaBuilderTestPerson, session)

        then:
        criteria.targetClass == CriteriaBuilderTestPerson
        criteria.session.is(session)
    }

    void "constructing from a Session and an existing query reuses that query"() {
        given:
        Session session = Stub(Session) {
            getMappingContext() >> mappingContext
        }

        when:
        def criteria = new CriteriaBuilder<CriteriaBuilderTestPerson>(CriteriaBuilderTestPerson, session, query)

        then:
        criteria.query.is(query)
        criteria.session.is(session)
    }

    void "setUniqueResult flags a single-result query"() {
        given:
        def criteria = newBuilder()

        when:
        criteria.setUniqueResult(true)

        then:
        criteria.uniqueResult
    }

    void "getQuery returns null before the query has been initialized"() {
        expect:
        new CriteriaBuilder<CriteriaBuilderTestPerson>(CriteriaBuilderTestPerson, queryCreator, mappingContext).query == null
    }

    void "cache delegates to the query and preserves BuildableCriteria for chaining"() {
        given:
        def criteria = newBuilder()

        when:
        BuildableCriteria result = criteria.cache(true)

        then:
        1 * query.cache(true)
        result.is(criteria)
    }

    void "readOnly sets the readOnly flag and preserves BuildableCriteria for chaining"() {
        given:
        def criteria = newBuilder()

        when:
        BuildableCriteria result = criteria.readOnly(true)

        then:
        result.is(criteria)
        criteria.readOnly
    }

    void "join(String) delegates to the query and preserves BuildableCriteria for chaining"() {
        given:
        def criteria = newBuilder()

        when:
        BuildableCriteria result = criteria.join('books')

        then:
        1 * query.join('books')
        result.is(criteria)
    }

    void "join(String, JoinType) delegates to the query with the join type"() {
        given:
        def criteria = newBuilder()

        when:
        BuildableCriteria result = criteria.join('books', jakarta.persistence.criteria.JoinType.LEFT)

        then:
        1 * query.join('books', jakarta.persistence.criteria.JoinType.LEFT)
        result.is(criteria)
    }

    void "select delegates to the query and preserves BuildableCriteria for chaining"() {
        given:
        def criteria = newBuilder()

        when:
        BuildableCriteria result = criteria.select('name')

        then:
        1 * query.select('name')
        result.is(criteria)
    }

    @Unroll
    void "#method(propertyName, value) validates the property and adds a criterion to the query"() {
        given:
        def criteria = newBuilder()

        when:
        def result = criteria."$method"('name', 'value')

        then:
        1 * query.add(_)
        result.is(criteria)

        where:
        method << ['eq', 'ne', 'gt', 'ge', 'lt', 'le', 'gte', 'lte', 'like', 'ilike', 'rlike',
                    'eqProperty', 'neProperty', 'gtProperty', 'geProperty', 'ltProperty', 'leProperty']
    }

    void "between adds a range criterion to the query"() {
        given:
        def criteria = newBuilder()

        when:
        def result = criteria.between('name', 'a', 'z')

        then:
        1 * query.add(_)
        result.is(criteria)
    }

    @Unroll
    void "#method(propertyName) adds a criterion to the query with no value"() {
        given:
        def criteria = newBuilder()

        when:
        def result = criteria."$method"('name')

        then:
        1 * query.add(_)
        result.is(criteria)

        where:
        method << ['isEmpty', 'isNotEmpty', 'isNull', 'isNotNull']
    }

    @Unroll
    void "#method(propertyName, size) adds a size criterion to the query"() {
        given:
        def criteria = newBuilder()

        when:
        def result = criteria."$method"('name', 1)

        then:
        1 * query.add(_)
        result.is(criteria)

        where:
        method << ['sizeEq', 'sizeGt', 'sizeGe', 'sizeLe', 'sizeLt', 'sizeNe']
    }

    void "in(propertyName, Collection) adds an in criterion to the query"() {
        given:
        def criteria = newBuilder()

        when:
        def result = criteria.in('name', ['a', 'b'])

        then:
        1 * query.add(_)
        result.is(criteria)
    }

    void "inList(propertyName, Collection) delegates to in"() {
        given:
        def criteria = newBuilder()

        when:
        def result = criteria.inList('name', ['a', 'b'])

        then:
        1 * query.add(_)
        result.is(criteria)
    }

    void "in(propertyName, Object[]) adds an in criterion to the query"() {
        given:
        def criteria = newBuilder()

        when:
        def result = criteria.in('name', ['a', 'b'] as Object[])

        then:
        1 * query.add(_)
        result.is(criteria)
    }

    void "inList(propertyName, Object[]) delegates to in"() {
        given:
        def criteria = newBuilder()

        when:
        def result = criteria.inList('name', ['a', 'b'] as Object[])

        then:
        1 * query.add(_)
        result.is(criteria)
    }

    void "idEquals adds an id equality criterion"() {
        given:
        def criteria = newBuilder()

        when:
        def result = criteria.idEquals(1L)

        then:
        1 * query.add(_)
        result.is(criteria)
    }

    void "idEq adds an id equality criterion"() {
        given:
        def criteria = newBuilder()

        when:
        def result = criteria.idEq(1L)

        then:
        1 * query.add(_)
        result.is(criteria)
    }

    void "allEq adds an equality conjunction for every entry in the map"() {
        given:
        def criteria = newBuilder()

        when:
        def result = criteria.allEq([name: 'a', id: 1L])

        then:
        1 * query.add(_)
        result.is(criteria)
    }

    void "exists adds an exists subquery criterion"() {
        given:
        def criteria = newBuilder()
        QueryableCriteria subquery = Stub(QueryableCriteria)

        when:
        def result = criteria.exists(subquery)

        then:
        1 * query.add(_)
        result.is(criteria)
    }

    void "notExists adds a not-exists subquery criterion"() {
        given:
        def criteria = newBuilder()
        QueryableCriteria subquery = Stub(QueryableCriteria)

        when:
        def result = criteria.notExists(subquery)

        then:
        1 * query.add(_)
        result.is(criteria)
    }

    @Unroll
    void "#method(propertyName, QueryableCriteria) adds a subquery criterion"() {
        given:
        def criteria = newBuilder()
        QueryableCriteria subquery = Stub(QueryableCriteria)

        when:
        def result = criteria."$method"('name', subquery)

        then:
        1 * query.add(_)
        result.is(criteria)

        where:
        method << ['eqAll', 'gtAll', 'ltAll', 'geAll', 'leAll', 'gtSome', 'geSome', 'ltSome', 'leSome',
                    'in', 'inList', 'notIn']
    }

    @Unroll
    void "#method(propertyName, Closure) builds a detached criteria subquery"() {
        given:
        def criteria = newBuilder()

        when:
        def result = criteria."$method"('name', { eq('name', 'nested') })

        then:
        1 * query.add(_)
        result.is(criteria)

        where:
        method << ['eqAll', 'gtAll', 'ltAll', 'geAll', 'leAll', 'gtSome', 'geSome', 'ltSome', 'leSome',
                    'in', 'inList', 'notIn']
    }

    void "and combines the criteria built inside the closure into a single conjunction"() {
        given:
        def criteria = newBuilder()

        when:
        def result = criteria.and {
            eq('name', 'a')
            eq('name', 'b')
        }

        then:
        1 * query.add(_)
        result.is(criteria)
    }

    void "or combines the criteria built inside the closure into a single disjunction"() {
        given:
        def criteria = newBuilder()

        when:
        def result = criteria.or {
            eq('name', 'a')
            eq('name', 'b')
        }

        then:
        1 * query.add(_)
        result.is(criteria)
    }

    void "not negates the criteria built inside the closure"() {
        given:
        def criteria = newBuilder()

        when:
        def result = criteria.not {
            eq('name', 'a')
        }

        then:
        1 * query.add(_)
        result.is(criteria)
    }

    void "order(propertyName) orders ascending and applies immediately when pagination is disabled"() {
        given:
        def criteria = newBuilder()

        when:
        def result = criteria.order('name')

        then:
        1 * query.order(_)
        result.is(criteria)
    }

    void "order(Query.Order) applies immediately when pagination is disabled"() {
        given:
        def criteria = newBuilder()

        when:
        def result = criteria.order(Query.Order.asc('name'))

        then:
        1 * query.order(_)
        result.is(criteria)
    }

    void "order(propertyName, direction) orders descending when requested"() {
        given:
        def criteria = newBuilder()

        when:
        def result = criteria.order('name', CriteriaBuilder.ORDER_DESCENDING)

        then:
        1 * query.order({ Query.Order o -> o.direction == Query.Order.Direction.DESC })
        result.is(criteria)
    }

    void "order defers to orderEntries when pagination is enabled"() {
        given:
        def criteria = newBuilder()
        criteria.paginationEnabledList = true

        when:
        def result = criteria.order('name')

        then:
        0 * query.order(_)
        criteria.orderEntries.size() == 1
        result.is(criteria)
    }

    void "order(Query.Order) defers to orderEntries when pagination is enabled"() {
        given:
        def criteria = newBuilder()
        criteria.paginationEnabledList = true

        when:
        def result = criteria.order(Query.Order.asc('name'))

        then:
        0 * query.order(_)
        criteria.orderEntries.size() == 1
        result.is(criteria)
    }

    void "order(propertyName, direction) orders ascending by default"() {
        given:
        def criteria = newBuilder()

        when:
        def result = criteria.order('name', CriteriaBuilder.ORDER_ASCENDING)

        then:
        1 * query.order({ Query.Order o -> o.direction == Query.Order.Direction.ASC })
        result.is(criteria)
    }

    void "order(propertyName, direction) defers to orderEntries when pagination is enabled"() {
        given:
        def criteria = newBuilder()
        criteria.paginationEnabledList = true

        when:
        def result = criteria.order('name', CriteriaBuilder.ORDER_DESCENDING)

        then:
        0 * query.order(_)
        criteria.orderEntries.size() == 1
        result.is(criteria)
    }

    void "projections builds a projection list and evaluates the closure against it"() {
        given:
        def criteria = newBuilder()
        Query.ProjectionList projectionList = Mock(Query.ProjectionList)
        query.projections() >> projectionList

        when:
        def result = criteria.projections {
            id()
        }

        then:
        1 * projectionList.id()
        result.is(projectionList)
    }

    void "id delegates to the active projection list once projections have been initialized"() {
        given:
        def criteria = newBuilder()
        Query.ProjectionList projectionList = Mock(Query.ProjectionList)
        query.projections() >> projectionList
        criteria.projections {}

        when:
        criteria.id()

        then:
        1 * projectionList.id()
    }

    void "count delegates to the active projection list once projections have been initialized"() {
        given:
        def criteria = newBuilder()
        Query.ProjectionList projectionList = Mock(Query.ProjectionList)
        query.projections() >> projectionList
        criteria.projections {}

        when:
        criteria.count()

        then:
        1 * projectionList.count()
    }

    void "distinct() delegates to the active projection list once projections have been initialized"() {
        given:
        def criteria = newBuilder()
        Query.ProjectionList projectionList = Mock(Query.ProjectionList)
        query.projections() >> projectionList
        criteria.projections {}

        when:
        criteria.distinct()

        then:
        1 * projectionList.distinct()
    }

    @Unroll
    void "#method delegates to the active projection list once projections have been initialized"() {
        given:
        def criteria = newBuilder()
        Query.ProjectionList projectionList = Mock(Query.ProjectionList)
        query.projections() >> projectionList
        criteria.projections {}

        when:
        criteria."$method"(*args)

        then:
        1 * projectionList."$method"(*args)

        where:
        method           | args
        'countDistinct'  | ['name']
        'groupProperty'  | ['name']
        'distinct'       | ['name']
        'property'       | ['name']
        'sum'            | ['name']
        'min'            | ['name']
        'max'            | ['name']
        'avg'            | ['name']
    }

    void "rowCount delegates to the count projection"() {
        given:
        def criteria = newBuilder()
        Query.ProjectionList projectionList = Mock(Query.ProjectionList)
        query.projections() >> projectionList
        criteria.projections {}

        when:
        criteria.rowCount()

        then:
        1 * projectionList.count()
    }

    @Unroll
    void "#method projection accessor returns null before projections have been initialized"() {
        given:
        def criteria = newBuilder()

        expect:
        criteria."$method"(*args) == null

        where:
        method          | args
        'id'            | []
        'count'         | []
        'countDistinct' | ['name']
        'groupProperty' | ['name']
        'distinct'      | []
        'distinct'      | ['name']
        'property'      | ['name']
        'sum'           | ['name']
        'min'           | ['name']
        'max'           | ['name']
        'avg'           | ['name']
    }

    void "build invokes the closure against the criteria delegate"() {
        given:
        def criteria = newBuilder()
        boolean invoked = false

        when:
        criteria.build {
            invoked = true
            eq('name', 'a')
        }

        then:
        invoked
        1 * query.add(_)
    }

    void "build tolerates a null closure"() {
        given:
        def criteria = newBuilder()

        when:
        criteria.build(null)

        then:
        noExceptionThrown()
    }

    void "calling a criteria construction method executes the closure and returns a list by default"() {
        given:
        def criteria = newBuilder()
        query.list() >> ['result']

        when:
        def result = criteria.call { eq('name', 'a') }

        then:
        result == ['result']
    }

    void "calling a criteria construction method returns a single result when uniqueResult is set"() {
        given:
        def criteria = newBuilder()
        query.singleResult() >> 'single'

        when:
        def result = criteria.call {
            uniqueResult = true
            eq('name', 'a')
        }

        then:
        result == 'single'
    }

    void "an unrecognised property access on the criteria delegates to a matching query method"() {
        given:
        def criteria = newBuilder()

        when:
        def result = criteria.max(10)

        then:
        1 * query.max(10)
        result.is(query.max(10))
    }

    void "invoking an association name with a closure builds a nested association query"() {
        given:
        Association association = Stub(Association) {
            getName() >> 'books'
            getAssociatedEntity() >> persistentEntity
        }
        PersistentEntity ownerEntity = Stub(PersistentEntity) {
            getIdentity() >> idProperty
            getPropertyByName('books') >> association
        }
        MappingContext ownerMappingContext = Stub(MappingContext) {
            getPersistentEntity(CriteriaBuilderTestPerson.name) >> ownerEntity
        }
        AssociationQuery associationQuery = Mock(AssociationQuery)
        query.createQuery('books') >> associationQuery
        def criteria = new CriteriaBuilder<CriteriaBuilderTestPerson>(CriteriaBuilderTestPerson, queryCreator, ownerMappingContext)

        when:
        criteria.books {}

        then:
        1 * query.add(associationQuery)
    }

    void "an unresolvable method call throws a MissingMethodException"() {
        given:
        def criteria = newBuilder()

        when:
        criteria.thisMethodDoesNotExistAnywhere()

        then:
        thrown(MissingMethodException)
    }

    void "an unresolvable single-argument non-closure call throws a MissingMethodException"() {
        given:
        def criteria = newBuilder()

        when:
        criteria.thisMethodDoesNotExistAnywhere('not a closure')

        then:
        thrown(MissingMethodException)
    }

    void "invoking a name with a closure that is not an association throws a MissingMethodException"() {
        given:
        def criteria = newBuilder()

        when:
        criteria.notAnAssociation {}

        then:
        thrown(MissingMethodException)
    }

    void "validatePropertyName resolves the identity property when the property is not otherwise found"() {
        given:
        PersistentEntity entityWithoutNameLookup = Stub(PersistentEntity) {
            getIdentity() >> idProperty
            getPropertyByName('id') >> null
        }
        MappingContext idOnlyMappingContext = Stub(MappingContext) {
            getPersistentEntity(CriteriaBuilderTestPerson.name) >> entityWithoutNameLookup
        }
        def criteria = new CriteriaBuilder<CriteriaBuilderTestPerson>(CriteriaBuilderTestPerson, queryCreator, idOnlyMappingContext)
        criteria.@query = query

        when:
        def result = criteria.eq('id', 1L)

        then:
        1 * query.add(_)
        result.is(criteria)
    }

    void "validatePropertyName throws when the property cannot be resolved and the datastore is not schemaless"() {
        given:
        PersistentEntity entityWithNoProperties = Stub(PersistentEntity) {
            getIdentity() >> idProperty
            getPropertyByName(_) >> null
        }
        MappingContext emptyMappingContext = Stub(MappingContext) {
            getPersistentEntity(CriteriaBuilderTestPerson.name) >> entityWithNoProperties
        }
        def criteria = new CriteriaBuilder<CriteriaBuilderTestPerson>(CriteriaBuilderTestPerson, queryCreator, emptyMappingContext)
        criteria.@query = query

        when:
        criteria.eq('missing', 1L)

        then:
        thrown(IllegalArgumentException)
    }

    void "validatePropertyName rejects a null property name"() {
        given:
        def criteria = newBuilder()

        when:
        criteria.eq(null, 'value')

        then:
        thrown(IllegalArgumentException)
    }

    void "a closure passed as a criterion value is converted to a detached criteria subquery"() {
        given:
        def criteria = newBuilder()

        when:
        def result = criteria.eq('name', { eq('name', 'nested') })

        then:
        1 * query.add(_)
        result.is(criteria)
    }

    void "addToCriteria lazily initializes the query when it has not been set yet"() {
        given:
        def criteria = new CriteriaBuilder<CriteriaBuilderTestPerson>(CriteriaBuilderTestPerson, queryCreator, mappingContext)

        when:
        def result = criteria.eq('name', 'value')

        then:
        criteria.query.is(query)
        1 * query.add(_)
        result.is(criteria)
    }

    void "the doCall construction method is recognised in addition to call"() {
        given:
        def criteria = newBuilder()
        query.list() >> ['a']

        when:
        def result = criteria.doCall { eq('name', 'a') }

        then:
        result == ['a']
    }

    void "validatePropertyName tolerates an unresolvable property when the datastore is schemaless"() {
        given:
        PersistentEntity entityWithNoProperties = Stub(PersistentEntity) {
            getIdentity() >> idProperty
            getPropertyByName(_) >> null
        }
        MappingContext emptyMappingContext = Stub(MappingContext) {
            getPersistentEntity(CriteriaBuilderTestPerson.name) >> entityWithNoProperties
        }
        QueryCreator schemalessQueryCreator = Stub(QueryCreator) {
            createQuery(CriteriaBuilderTestPerson) >> query
            isSchemaless() >> true
        }
        def criteria = new CriteriaBuilder<CriteriaBuilderTestPerson>(CriteriaBuilderTestPerson, schemalessQueryCreator, emptyMappingContext)
        criteria.@query = query

        when:
        def result = criteria.eq('missing', 1L)

        then:
        1 * query.add(_)
        result.is(criteria)
    }

    void "list(Closure) evaluates the closure and returns the query's results"() {
        given:
        def criteria = newBuilder()
        query.list() >> ['a', 'b']

        when:
        def result = criteria.list { eq('name', 'a') }

        then:
        result == ['a', 'b']
        1 * query.add(_)
    }

    void "get(Closure) evaluates the closure and returns a single result"() {
        given:
        def criteria = newBuilder()
        query.singleResult() >> 'single'

        when:
        def result = criteria.get { eq('name', 'a') }

        then:
        result == 'single'
        criteria.uniqueResult
        1 * query.add(_)
    }

    void "listDistinct(Closure) applies a distinct projection before listing"() {
        given:
        def criteria = newBuilder()
        Query.ProjectionList projectionList = Mock(Query.ProjectionList)
        query.projections() >> projectionList
        query.list() >> ['a']

        when:
        def result = criteria.listDistinct { eq('name', 'a') }

        then:
        result == ['a']
        1 * projectionList.distinct()
        1 * query.add(_)
    }

    void "list(Map, Closure) enables pagination, applies ordering and returns a PagedResultList"() {
        given:
        def criteria = newBuilder()
        query.getEntity() >> persistentEntity
        persistentEntity.getMappingContext() >> mappingContext

        when:
        def result = criteria.list([:]) { order('name') }

        then:
        result instanceof PagedResultList
        criteria.paginationEnabledList
        1 * query.order(_)
    }

    void "count(Closure) applies a count projection and returns a single result"() {
        given:
        def criteria = newBuilder()
        Query.ProjectionList projectionList = Mock(Query.ProjectionList)
        query.projections() >> projectionList
        query.singleResult() >> 5

        when:
        def result = criteria.count { eq('name', 'a') }

        then:
        result == 5
        criteria.uniqueResult
        1 * projectionList.count()
        1 * query.add(_)
    }

    void "scroll executes the closure as a criteria construction call and returns the results"() {
        given:
        def criteria = newBuilder()
        query.list() >> ['a']

        when:
        def result = criteria.scroll { eq('name', 'a') }

        then:
        result == ['a']
        1 * query.add(_)
    }
}

class CriteriaBuilderTestPerson {
    Long id
    String name
}

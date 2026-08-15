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

import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.core.Session
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.query.Query
import org.springframework.core.convert.support.DefaultConversionService
import spock.lang.Specification
import spock.lang.Unroll

/**
 * Exercises {@link CountFinder}'s own independent {@code buildQuery} - unlike every other finder
 * in this package, it does NOT reuse {@code DynamicFinder.getJunction()} at all: for the And
 * operator (or no operator) each expression's criterion is added directly to the query, while for
 * Or each criterion is added to an explicit {@code disjunction()} via the two-arg
 * {@code Query.add(Junction, Criterion)} overload - and a {@code count()} projection is always
 * applied regardless of operator.
 */
class CountFinderSpec extends Specification {

    PersistentProperty nameProperty = Stub(PersistentProperty) {
        getName() >> 'name'
        getType() >> String
    }
    MappingContext mappingContext = Stub(MappingContext)
    PersistentEntity persistentEntity = Stub(PersistentEntity)
    Datastore datastore = Stub(Datastore) {
        getMappingContext() >> mappingContext
    }
    CountFinder countFinder = CountFinder.countBy(datastore)

    // See DynamicFinderSpec's setup() for why this circular wiring can't be done via inline field
    // initializers.
    void setup() {
        mappingContext.getConversionService() >> new DefaultConversionService()
        mappingContext.getPersistentEntity(FinderTestEntity.name) >> persistentEntity
        persistentEntity.getMappingContext() >> mappingContext
        persistentEntity.getPropertyByName('name') >> nameProperty
    }

    private static DynamicFinderInvocation twoEqualsInvocation(String operator) {
        MethodExpression.Equal nameExpression = new MethodExpression.Equal('name')
        nameExpression.setArguments(['Bob'] as Object[])
        MethodExpression.Equal ageExpression = new MethodExpression.Equal('age')
        ageExpression.setArguments([42] as Object[])
        new DynamicFinderInvocation(FinderTestEntity, 'countByNameAndAge', [] as Object[],
                [nameExpression, ageExpression], null, operator)
    }

    @Unroll
    void "buildQuery adds each expression's criterion directly to the query, without a disjunction, for operator #operator"() {
        given:
        Query.ProjectionList projectionList = Mock(Query.ProjectionList)
        Query query = Mock(Query) {
            getEntity() >> persistentEntity
            projections() >> projectionList
        }

        when:
        countFinder.buildQuery(twoEqualsInvocation(operator), Stub(Session) { createQuery(FinderTestEntity) >> query })

        then:
        2 * query.add(_)
        0 * query.disjunction()
        1 * projectionList.count()

        where:
        operator << [null, 'And']
    }

    void "buildQuery adds each expression's criterion to an explicit disjunction for the Or operator"() {
        given:
        Query.Junction disjunctionMock = Mock(Query.Junction)
        Query.ProjectionList projectionList = Mock(Query.ProjectionList)
        Query query = Mock(Query) {
            getEntity() >> persistentEntity
            projections() >> projectionList
        }
        Session session = Stub(Session) {
            createQuery(FinderTestEntity) >> query
        }

        when:
        countFinder.buildQuery(twoEqualsInvocation('Or'), session)

        then:
        // Return value is specified here, not via a separate stub, since a bare cardinality
        // interaction for the same method declared later would shadow an earlier stub's return
        // value and silently default to returning null.
        1 * query.disjunction() >> disjunctionMock
        2 * query.add(disjunctionMock, _)
        0 * query.add(_)
        1 * projectionList.count()
    }

    void "isMethodMatch matches countBy* method names"() {
        expect:
        countFinder.isMethodMatch('countByName')
        countFinder.isMethodMatch('countByNameAndAge')
        !countFinder.isMethodMatch('somethingElse')
    }

    void "invoke runs the full round trip through the real DatastoreUtils.execute seam and returns the count"() {
        given:
        Query.ProjectionList projectionList = Mock(Query.ProjectionList)
        Query query = Mock(Query) {
            getEntity() >> persistentEntity
            projections() >> projectionList
            singleResult() >> 2L
        }
        Session session = Stub(Session) {
            createQuery(FinderTestEntity) >> query
        }
        datastore.hasCurrentSession() >> true
        datastore.getCurrentSession() >> session

        when:
        Object result = countFinder.invoke(FinderTestEntity, 'countByName', ['Bob'] as Object[])

        then:
        result == 2L
        1 * projectionList.count()
    }

    void "invoke(Class, methodName, DetachedCriteria, Object[]) merges the detached criteria onto the built query"() {
        given:
        // Called reflectively (dynamic Groovy dispatch, not part of FinderMethod) by
        // AbstractDetachedCriteria#methodMissing.
        Query.ProjectionList projectionList = Mock(Query.ProjectionList)
        Query query = Mock(Query) {
            getEntity() >> persistentEntity
            projections() >> projectionList
            singleResult() >> 2L
        }
        Session session = Stub(Session) {
            createQuery(FinderTestEntity) >> query
        }
        datastore.hasCurrentSession() >> true
        datastore.getCurrentSession() >> session
        grails.gorm.DetachedCriteria detachedCriteria = Stub(grails.gorm.DetachedCriteria) {
            getFetchStrategies() >> [:]
            getCriteria() >> [org.grails.datastore.mapping.query.Restrictions.eq('age', 42)]
            getProjections() >> []
            getOrders() >> []
        }

        when:
        Object result = countFinder.invoke(FinderTestEntity, 'countByName', detachedCriteria, ['Bob'] as Object[])

        then:
        result == 2L
        // One from the detached criteria's merged criterion, one from the real "name" expression.
        2 * query.add({ it instanceof Query.PropertyCriterion })
        1 * projectionList.count()
    }

    void "invoke throws IllegalStateException when constructed in stateless mode"() {
        when:
        CountFinder.countBy(mappingContext).invoke(FinderTestEntity, 'countByName', ['Bob'] as Object[])

        then:
        thrown(IllegalStateException)
    }
}

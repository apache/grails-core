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

import org.grails.datastore.gorm.DatastoreResolver
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
 * Exercises ListOrderByFinder - unlike every other finder in this package, it never shared
 * {@link DynamicFinder}'s grammar, with its own regex matching and its own {@code invoke()}
 * method. Collaborators are wired using the same Datastore/Session/Query mock idiom as elsewhere
 * in this package: the "existing bound session" branch of {@code DatastoreUtils.execute}
 * (Datastore.hasCurrentSession() == true) is the simplest reliable seam for exercising the real
 * {@code invoke()} round trip.
 */
class ListOrderByFinderSpec extends Specification {

    MappingContext mappingContext = Stub(MappingContext) {
        getConversionService() >> new DefaultConversionService()
    }
    PersistentEntity persistentEntity = Stub(PersistentEntity) {
        getMappingContext() >> mappingContext
        getPropertyByName('age') >> Stub(PersistentProperty) { getName() >> 'age' }
    }
    Query query = Mock(Query) {
        getEntity() >> persistentEntity
    }
    Session session = Stub(Session) {
        createQuery(FinderTestEntity) >> query
    }
    Datastore datastore = Stub(Datastore) {
        hasCurrentSession() >> true
        getCurrentSession() >> session
    }
    ListOrderByFinder finder = new ListOrderByFinder(datastore)

    @Unroll
    void "isMethodMatch('#methodName') == #matches"() {
        expect:
        finder.isMethodMatch(methodName) == matches

        where:
        methodName               | matches
        'listOrderByName'        | true
        'listOrderByNameAndAge'  | true
        'somethingElse'          | false
    }

    void "setPattern replaces the method-name pattern"() {
        when:
        finder.setPattern('(customOrderBy)(\\w+)')

        then:
        finder.isMethodMatch('customOrderByName')
        !finder.isMethodMatch('listOrderByName')
    }

    void "invoke defaults to ascending order when no Map argument is supplied and lists the results without a distinct projection"() {
        when:
        finder.invoke(FinderTestEntity, 'listOrderByAge', [] as Object[])

        then:
        1 * query.order({ Query.Order order -> order.property == 'age' && order.direction == Query.Order.Direction.ASC })
        0 * query.projections()
        1 * query.list()
        0 * query.singleResult()
    }

    void "invoke orders by every And-separated property in the method name, in order"() {
        when:
        finder.invoke(FinderTestEntity, 'listOrderByNameAndAge', [] as Object[])

        then:
        1 * query.order({ Query.Order order -> order.property == 'name' && order.direction == Query.Order.Direction.ASC })

        then:
        1 * query.order({ Query.Order order -> order.property == 'age' && order.direction == Query.Order.Direction.ASC })
        1 * query.list()
    }

    void "invoke lower-cases only the first character of each property segment"() {
        when:
        finder.invoke(FinderTestEntity, 'listOrderByISize', [] as Object[])

        then:
        1 * query.order({ Query.Order order -> order.property == 'iSize' })
        1 * query.list()
    }

    void "invoke ignores a non-Map first argument, defaulting to ascending order without delegating to DynamicFinder.populateArgumentsForCriteria"() {
        when:
        finder.invoke(FinderTestEntity, 'listOrderByAge', ['not a map'] as Object[])

        then:
        1 * query.order({ Query.Order order -> order.property == 'age' && order.direction == Query.Order.Direction.ASC })
        0 * query.max(_)
        1 * query.list()
    }

    @Unroll
    void "invoke stays ascending when the Map's order entry is '#order'"() {
        when:
        finder.invoke(FinderTestEntity, 'listOrderByAge', [order != null ? [order: order] : [:]] as Object[])

        then:
        1 * query.order({ Query.Order o -> o.property == 'age' && o.direction == Query.Order.Direction.ASC })
        1 * query.list()

        where:
        order << [null, 'asc', 'ASC', ' Asc ', '']
    }

    @Unroll
    void "invoke flips to descending order for '#order' and delegates the remaining Map entries to DynamicFinder.populateArgumentsForCriteria"() {
        when:
        // "max: 5" alongside "order: desc" proves the remaining-arguments Map is actually
        // delegated to DynamicFinder.populateArgumentsForCriteria (which applies max()), not just
        // that the order flag itself is honoured.
        finder.invoke(FinderTestEntity, 'listOrderByAge', [[order: order, max: 5]] as Object[])

        then:
        1 * query.order({ Query.Order o -> o.property == 'age' && o.direction == Query.Order.Direction.DESC })
        1 * query.max(5)
        1 * query.list()

        where:
        order << ['desc', 'DESC', ' Desc ']
    }

    @Unroll
    void "invoke rejects the order direction '#order' without echoing it and before touching the query"() {
        when:
        finder.invoke(FinderTestEntity, 'listOrderByAge', [[order: order]] as Object[])

        then:
        IllegalArgumentException e = thrown()
        e.message == 'Invalid sort direction'
        0 * query.order(_)
        0 * query.list()

        where:
        order << ['sideways', 'desc extra']
    }

    void "invoke applies an additional criteria closure to the query"() {
        given:
        Closure additionalCriteria = { -> }
        query.getSession() >> session
        persistentEntity.getJavaClass() >> FinderTestEntity
        session.getMappingContext() >> mappingContext

        when:
        finder.invoke(FinderTestEntity, 'listOrderByAge', additionalCriteria, [] as Object[])

        then:
        1 * query.getSession() >> session
        1 * query.list()
    }

    void "invoke resolves its datastore through the DatastoreResolver on every invocation, not at construction"() {
        given:
        DatastoreResolver resolver = Mock(DatastoreResolver)

        when:
        ListOrderByFinder resolved = new ListOrderByFinder(resolver, mappingContext)

        then:
        0 * resolver.resolve()

        when:
        resolved.invoke(FinderTestEntity, 'listOrderByAge', [] as Object[])

        then:
        1 * resolver.resolve() >> datastore
        1 * query.list()
    }

    void "invoke throws IllegalStateException when constructed with a null datastore"() {
        when:
        new ListOrderByFinder((Datastore) null).invoke(FinderTestEntity, 'listOrderByAge', [] as Object[])

        then:
        IllegalStateException e = thrown()
        e.message == 'Cannot execute session query with null datastore'
    }
}

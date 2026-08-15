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
    Datastore datastore = Stub(Datastore)
    ListOrderByFinder finder = new ListOrderByFinder(datastore)

    @Unroll
    void "isMethodMatch('#methodName') == #matches"() {
        expect:
        finder.isMethodMatch(methodName) == matches

        where:
        methodName        | matches
        'listOrderByName' | true
        'somethingElse'   | false
    }

    void "invoke defaults to ascending order when no Map argument is supplied, and lists distinct results"() {
        given:
        Query.ProjectionList projectionList = Mock(Query.ProjectionList)
        Query query = Mock(Query) {
            projections() >> projectionList
        }
        Session session = Stub(Session) {
            createQuery(FinderTestEntity) >> query
        }
        datastore.hasCurrentSession() >> true
        datastore.getCurrentSession() >> session

        when:
        finder.invoke(FinderTestEntity, 'listOrderByAge', [] as Object[])

        then:
        1 * query.order({ Query.Order order -> order.property == 'age' && order.direction == Query.Order.Direction.ASC })
        1 * projectionList.distinct()
        1 * query.list()
        0 * query.singleResult()
    }

    void "invoke flips to descending order and delegates the remaining Map entries to DynamicFinder.populateArgumentsForCriteria"() {
        given:
        Query.ProjectionList projectionList = Mock(Query.ProjectionList)
        PersistentEntity persistentEntity = Stub(PersistentEntity) {
            getMappingContext() >> mappingContext
        }
        Query query = Mock(Query) {
            getEntity() >> persistentEntity
            projections() >> projectionList
        }
        Session session = Stub(Session) {
            createQuery(FinderTestEntity) >> query
        }
        datastore.hasCurrentSession() >> true
        datastore.getCurrentSession() >> session

        when:
        // "max: 5" alongside "order: desc" proves the remaining-arguments Map is actually
        // delegated to DynamicFinder.populateArgumentsForCriteria (which applies max()), not just
        // that the order flag itself is honoured.
        finder.invoke(FinderTestEntity, 'listOrderByAge', [[order: 'desc', max: 5]] as Object[])

        then:
        1 * query.order({ Query.Order order -> order.property == 'age' && order.direction == Query.Order.Direction.DESC })
        1 * query.max(5)
        1 * projectionList.distinct()
        1 * query.list()
    }
}

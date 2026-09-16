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

package org.grails.gorm.rx.finders

import org.grails.datastore.mapping.core.Session
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.query.Query
import org.grails.datastore.mapping.query.Restrictions
import org.grails.datastore.rx.RxDatastoreClient
import org.grails.datastore.rx.query.RxQuery
import org.springframework.core.convert.support.DefaultConversionService
import rx.Observable
import spock.lang.Specification

class CountByFinderSpec extends Specification {

    RxDatastoreClient datastoreClient = Mock()
    MappingContext mappingContext = Mock()
    PersistentEntity entity = Mock()
    Query query = Mock(Query, additionalInterfaces: [RxQuery])

    def setup() {
        PersistentProperty nameProperty = Stub(PersistentProperty) { getType() >> String }
        PersistentProperty activeProperty = Stub(PersistentProperty) { getType() >> Boolean }
        datastoreClient.getMappingContext() >> mappingContext
        entity.getMappingContext() >> mappingContext
        entity.getJavaClass() >> Person
        entity.getPropertyByName('name') >> nameProperty
        entity.getPropertyByName('active') >> activeProperty
        mappingContext.getPersistentEntity(Person.name) >> entity
        mappingContext.getConversionService() >> new DefaultConversionService()
        query.getEntity() >> entity
    }

    def "obtains its mapping context from the datastore client when constructed"() {
        when:
        def finder = new CountByFinder(datastoreClient)

        then:
        1 * datastoreClient.getMappingContext() >> mappingContext
        finder.datastoreClient.is(datastoreClient)
    }

    def "countBy parses an Or expression and executes a projected RX query"() {
        given:
        def finder = new CountByFinder(datastoreClient)
        Query.ProjectionList projectionList = new Query.ProjectionList()

        when:
        def result = finder.invoke(Person, 'countByNameOrActive', ['Fred', true] as Object[])

        then:
        1 * datastoreClient.createQuery(Person) >> query
        1 * query.add({ Query.Criterion it -> it instanceof Query.Disjunction })
        1 * query.projections() >> projectionList
        1 * query.singleResult() >> Observable.just(4L)
        result.toBlocking().single() == 4L
        projectionList.projectionList.size() == 1
        projectionList.projectionList[0] instanceof Query.CountProjection
    }

    def "applies detached criteria to the query when present"() {
        given:
        def finder = new CountByFinder(datastoreClient)
        def detachedCriteria = Stub(grails.gorm.DetachedCriteria) {
            getFetchStrategies() >> [:]
            getCriteria() >> [Restrictions.eq('active', true)]
            getProjections() >> []
            getOrders() >> []
        }
        when:
        finder.invoke(Person, 'countByName', detachedCriteria, ['Fred'] as Object[])

        then:
        1 * datastoreClient.createQuery(Person) >> query
        2 * query.add(_ as Query.Criterion)
        1 * query.projections() >> new Query.ProjectionList()
        1 * query.singleResult() >> Observable.just(1L)
    }

    def "applies additional criteria to the query when present"() {
        given:
        def finder = new CountByFinder(datastoreClient)
        def session = Stub(Session) {
            getMappingContext() >> mappingContext
        }
        mappingContext.getPersistentEntity(_) >> entity
        query.getSession() >> session

        when:
        finder.invoke(Person, 'countByName', { -> }, ['Fred'] as Object[])

        then:
        1 * datastoreClient.createQuery(Person) >> query
        1 * query.getSession() >> session
        1 * query.add(_ as Query.Criterion)
        1 * query.projections() >> new Query.ProjectionList()
        1 * query.singleResult() >> Observable.just(1L)
        noExceptionThrown()
    }

    def "applies query arguments to the query when present"() {
        given:
        def finder = new CountByFinder(datastoreClient)

        when:
        finder.invoke(Person, 'countByName', ['Fred', [max: 5]] as Object[])

        then:
        1 * datastoreClient.createQuery(Person) >> query
        1 * query.max(5)
        1 * query.add(_ as Query.Criterion)
        1 * query.projections() >> new Query.ProjectionList()
        1 * query.singleResult() >> Observable.just(1L)
    }

    private static class Person {
        String name
        boolean active
    }
}

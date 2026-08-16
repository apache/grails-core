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
import org.grails.datastore.rx.RxDatastoreClient
import org.springframework.core.convert.support.DefaultConversionService
import rx.Observable
import spock.lang.Specification

/**
 * Exercises {@link RxCountFinder} - the rx-module mirror of {@link
 * org.grails.datastore.gorm.finders.CountFinder}, reusing that class's {@code
 * applyCriteriaAndCount} helper rather than duplicating its independent And/Or criteria handling.
 */
class RxCountFinderSpec extends Specification {

    RxDatastoreClient datastoreClient = Mock()
    MappingContext mappingContext = Mock()
    PersistentEntity entity = Mock()
    Query query = Mock()

    PersistentProperty nameProperty = Stub(PersistentProperty) {
        getName() >> 'name'
        getType() >> String
    }

    def setup() {
        entity.getMappingContext() >> mappingContext
        mappingContext.getConversionService() >> new DefaultConversionService()
        mappingContext.getPersistentEntity(_) >> entity
        entity.getPropertyByName('name') >> nameProperty
        query.getEntity() >> entity
        query.projections() >> new Query.ProjectionList()
        datastoreClient.getMappingContext() >> mappingContext
    }

    void "isMethodMatch matches countBy* method names"() {
        expect:
        RxCountFinder.countBy(datastoreClient).isMethodMatch('countByName')
        !RxCountFinder.countBy(datastoreClient).isMethodMatch('somethingElse')
    }

    void "setPattern delegates to the grammar"() {
        given:
        def finder = RxCountFinder.countBy(datastoreClient)

        expect:
        finder.isMethodMatch('countByName')
        !finder.isMethodMatch('customPrefixName')

        when:
        finder.setPattern('(customPrefix)(\\w+)')

        then:
        finder.isMethodMatch('customPrefixName')
        !finder.isMethodMatch('countByName')
    }

    void "counts by building and executing a query via the RX datastore client instead of a session"() {
        given:
        def finder = RxCountFinder.countBy(datastoreClient)

        when:
        // query.singleResult() returns an Observable in production (RxQuery#singleResult) - countBy
        // passes it through unchanged rather than unwrapping it.
        Observable observable = finder.invoke(Person, 'countByName', ['Fred'] as Object[]) as Observable
        def result = observable.toBlocking().first()

        then:
        1 * datastoreClient.createQuery(Person) >> query
        1 * query.add({ Query.Criterion it -> it instanceof Query.PropertyCriterion })
        1 * query.singleResult() >> Observable.just(4L)
        result == 4L
    }

    void "invoke(Class, methodName, DetachedCriteria, Object[]) merges the detached criteria onto the built query"() {
        given:
        // Called reflectively (dynamic Groovy dispatch, not part of FinderMethod) by
        // AbstractDetachedCriteria#methodMissing.
        def finder = RxCountFinder.countBy(datastoreClient)
        def detachedCriteria = Stub(grails.gorm.DetachedCriteria) {
            getFetchStrategies() >> [:]
            getCriteria() >> [org.grails.datastore.mapping.query.Restrictions.eq('active', true)]
            getProjections() >> []
            getOrders() >> []
        }

        when:
        Observable observable = finder.invoke(Person, 'countByName', detachedCriteria, ['Fred'] as Object[]) as Observable
        def result = observable.toBlocking().first()

        then:
        1 * datastoreClient.createQuery(Person) >> query
        2 * query.add({ Query.Criterion it -> it instanceof Query.PropertyCriterion })
        1 * query.singleResult() >> Observable.just(4L)
        result == 4L
    }

    void "invoke(Class, methodName, DetachedCriteria, Object[]) with a null detachedCriteria never merges anything onto the built query"() {
        given:
        def finder = RxCountFinder.countBy(datastoreClient)

        when:
        Observable observable = finder.invoke(Person, 'countByName', (grails.gorm.DetachedCriteria) null, ['Fred'] as Object[]) as Observable
        def result = observable.toBlocking().first()

        then:
        1 * datastoreClient.createQuery(Person) >> query
        1 * query.add({ Query.Criterion it -> it instanceof Query.PropertyCriterion })
        1 * query.singleResult() >> Observable.just(4L)
        result == 4L
    }

    void "applies additional criteria to the query when present"() {
        given:
        def finder = RxCountFinder.countBy(datastoreClient)
        def session = Stub(Session) {
            getMappingContext() >> mappingContext
        }
        query.getSession() >> session
        entity.getJavaClass() >> Person

        when:
        finder.invoke(Person, 'countByName', { -> }, ['Fred'] as Object[])

        then:
        1 * datastoreClient.createQuery(Person) >> query
        1 * query.getSession() >> session
        1 * query.singleResult()
        noExceptionThrown()
    }

    void "applies query arguments to the query when present"() {
        given:
        def finder = RxCountFinder.countBy(datastoreClient)

        when:
        finder.invoke(Person, 'countByName', ['Fred', [max: 5]] as Object[])

        then:
        1 * datastoreClient.createQuery(Person) >> query
        1 * query.max(5)
        1 * query.singleResult()
    }

    private static class Person {
        String name
        boolean active
    }
}

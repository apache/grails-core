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
import org.grails.datastore.rx.query.RxQuery
import org.springframework.core.convert.support.DefaultConversionService
import rx.Observable
import spock.lang.Specification

/**
 * Exercises {@link RxListResultFinder} - the rx-module mirror of {@link
 * org.grails.datastore.gorm.finders.ListResultFinder}, composing {@link
 * org.grails.datastore.gorm.finders.DynamicFinder} rather than extending core's finder classes as
 * the previous per-type rx finder classes (FindAllByFinder/FindAllByBooleanFinder) did. Tests go
 * through the real public invoke() entry point with real method names.
 */
class RxListResultFinderSpec extends Specification {

    RxDatastoreClient datastoreClient = Mock()
    MappingContext mappingContext = Mock()
    PersistentEntity entity = Mock()
    RxCapableQuery query = Mock()

    PersistentProperty titleProperty = Stub(PersistentProperty) {
        getName() >> 'title'
        getType() >> String
    }
    PersistentProperty nameProperty = Stub(PersistentProperty) {
        getName() >> 'name'
        getType() >> String
    }
    PersistentProperty activeProperty = Stub(PersistentProperty) {
        getName() >> 'active'
        getType() >> Boolean
    }
    PersistentProperty cityProperty = Stub(PersistentProperty) {
        getName() >> 'city'
        getType() >> String
    }

    def setup() {
        entity.getMappingContext() >> mappingContext
        mappingContext.getConversionService() >> new DefaultConversionService()
        mappingContext.getPersistentEntity(_) >> entity
        entity.getPropertyByName('title') >> titleProperty
        entity.getPropertyByName('name') >> nameProperty
        entity.getPropertyByName('active') >> activeProperty
        entity.getPropertyByName('city') >> cityProperty
        query.getEntity() >> entity
        datastoreClient.getMappingContext() >> mappingContext
    }

    void "findAllBy isMethodMatch matches findAllBy* method names"() {
        expect:
        RxListResultFinder.findAllBy(datastoreClient).isMethodMatch('findAllByTitle')
        !RxListResultFinder.findAllBy(datastoreClient).isMethodMatch('somethingElse')
    }

    void "findAllBy finds all without remaining arguments by invoking findAll on the RX query"() {
        given:
        def finder = RxListResultFinder.findAllBy(datastoreClient)
        def observable = Observable.just(new Book(title: 'Shogun'))

        when:
        def result = finder.invoke(Book, 'findAllByTitle', ['Shogun'] as Object[])

        then:
        1 * datastoreClient.createQuery(Book) >> query
        1 * query.add({ Query.Criterion it -> it instanceof Query.Junction })
        1 * query.findAll() >> observable
        0 * query.findAll(_)
        result.is(observable)
    }

    void "findAllBy invoke(Class, methodName, DetachedCriteria, Object[]) merges the detached criteria onto the built query"() {
        given:
        // Called reflectively (dynamic Groovy dispatch, not part of FinderMethod) by
        // AbstractDetachedCriteria#methodMissing.
        def finder = RxListResultFinder.findAllBy(datastoreClient)
        def observable = Observable.just(new Book(title: 'Shogun'))
        def detachedCriteria = Stub(grails.gorm.DetachedCriteria) {
            getFetchStrategies() >> [:]
            getCriteria() >> [org.grails.datastore.mapping.query.Restrictions.eq('author', 'Clavell')]
            getProjections() >> []
            getOrders() >> []
        }

        when:
        def result = finder.invoke(Book, 'findAllByTitle', detachedCriteria, ['Shogun'] as Object[])

        then:
        1 * datastoreClient.createQuery(Book) >> query
        1 * query.add({ Query.Criterion it -> it instanceof Query.PropertyCriterion })
        1 * query.add({ Query.Criterion it -> it instanceof Query.Junction })
        1 * query.findAll() >> observable
        result.is(observable)
    }

    void "findAllBy finds all with remaining map arguments by invoking findAll(Map) on the RX query"() {
        given:
        def finder = RxListResultFinder.findAllBy(datastoreClient)
        def observable = Observable.just(new Book(title: 'Shogun'))

        when:
        def result = finder.invoke(Book, 'findAllByTitle', ['Shogun', [max: 5]] as Object[])

        then:
        1 * datastoreClient.createQuery(Book) >> query
        1 * query.max(5)
        1 * query.findAll([max: 5]) >> observable
        0 * query.findAll()
        result.is(observable)
    }

    void "findAllBy applies additional criteria to the query when present"() {
        given:
        def finder = RxListResultFinder.findAllBy(datastoreClient)
        def session = Stub(Session) {
            getMappingContext() >> mappingContext
        }
        query.getSession() >> session
        entity.getJavaClass() >> Book

        when:
        finder.invoke(Book, 'findAllByTitle', { -> }, ['Shogun'] as Object[])

        then:
        1 * datastoreClient.createQuery(Book) >> query
        1 * query.getSession() >> session
        1 * query.findAll() >> Observable.empty()
        noExceptionThrown()
    }

    void "findAllByBoolean isMethodMatch matches boolean style method names"() {
        expect:
        RxListResultFinder.findAllByBoolean(datastoreClient).isMethodMatch('findAllActive')
    }

    void "findAllByBoolean queries all results by the boolean property, combining it as a required expression with the rest via Or"() {
        given:
        def finder = RxListResultFinder.findAllByBoolean(datastoreClient)
        def observable = Observable.just(new Person(name: 'Fred', active: true))

        when:
        // The boolean clause's own argument is not consumed from the array - it's hardcoded
        // TRUE/FALSE by the grammar based on a Not-prefix, so only "Name"/"City" (Or'd, 1 arg each)
        // need real arguments here.
        def result = finder.invoke(Person, 'findAllActiveByNameOrCity', ['Fred', 'London'] as Object[])

        then:
        1 * datastoreClient.createQuery(Person) >> query
        1 * query.add({ Query.Criterion it -> it instanceof Query.Conjunction && ((Query.Junction) it).criteria.size() == 2 })
        1 * query.findAll() >> observable
        0 * query.findAll(_)
        result.is(observable)
    }

    private static class Book {
        String title
        String author
    }

    private static class Person {
        String name
        boolean active
    }

    private static abstract class RxCapableQuery extends Query implements RxQuery {
        protected RxCapableQuery() {
            super(null, null)
        }
    }
}

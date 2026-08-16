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

import grails.gorm.rx.RxEntity
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
 * Exercises {@link RxSingleResultFinder} - the rx-module mirror of {@link
 * org.grails.datastore.gorm.finders.SingleResultFinder}, composing {@link
 * org.grails.datastore.gorm.finders.DynamicFinder} rather than extending core's finder classes as
 * the previous per-type rx finder classes (FindByFinder/FindByBooleanFinder/FindOrCreateByFinder/
 * FindOrSaveByFinder) did. Unlike those classes' own specs, which hand-built a
 * DynamicFinderInvocation and called the package-private doInvokeInternal directly, these tests go
 * through the real public invoke() entry point with real method names - doInvokeInternal no longer
 * exists as a separate seam, and this also means the method-name parsing itself is now exercised,
 * not just the query-building/execution half.
 */
class RxSingleResultFinderSpec extends Specification {

    RxDatastoreClient datastoreClient = Mock()
    MappingContext mappingContext = Mock()
    PersistentEntity entity = Mock()
    Query query = Mock()

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
        entity.getPropertyByName('city') >> cityProperty
        entity.getPropertyByName('active') >> activeProperty
        query.getEntity() >> entity
        datastoreClient.getMappingContext() >> mappingContext
    }

    void "findBy obtains its mapping context from the datastore client when constructed"() {
        when:
        RxSingleResultFinder.findBy(datastoreClient)

        then:
        1 * datastoreClient.getMappingContext() >> mappingContext
    }

    void "findBy isMethodMatch matches findBy* method names"() {
        expect:
        RxSingleResultFinder.findBy(datastoreClient).isMethodMatch('findByTitle')
        !RxSingleResultFinder.findBy(datastoreClient).isMethodMatch('somethingElse')
    }

    void "setPattern delegates to the grammar"() {
        given:
        def finder = RxSingleResultFinder.findBy(datastoreClient)

        expect:
        finder.isMethodMatch('findByTitle')
        !finder.isMethodMatch('customPrefixTitle')

        when:
        finder.setPattern('(customPrefix)([A-Z]\\w*)')

        then:
        finder.isMethodMatch('customPrefixTitle')
        !finder.isMethodMatch('findByTitle')
    }

    void "findBy finds by building and executing a query via the RX datastore client instead of a session"() {
        given:
        def finder = RxSingleResultFinder.findBy(datastoreClient)

        when:
        // query.singleResult() returns an Observable in production (RxQuery#singleResult) - findBy
        // passes it through unchanged rather than unwrapping it, so the caller (e.g.
        // RxGormStaticApi.methodMissing) receives an Observable, not the raw entity.
        Observable observable = finder.invoke(Book, 'findByTitle', ['Shogun'] as Object[]) as Observable
        Book result = observable.toBlocking().first() as Book

        then:
        1 * datastoreClient.createQuery(Book) >> query
        1 * query.add({ Query.Criterion it -> it instanceof Query.Junction })
        1 * query.singleResult() >> Observable.just(new Book(title: 'Shogun'))
        result.title == 'Shogun'
    }

    void "findBy invoke(Class, methodName, DetachedCriteria, Object[]) merges the detached criteria onto the built query"() {
        given:
        // Called reflectively (dynamic Groovy dispatch, not part of FinderMethod) by
        // AbstractDetachedCriteria#methodMissing.
        def finder = RxSingleResultFinder.findBy(datastoreClient)
        def detachedCriteria = Stub(grails.gorm.DetachedCriteria) {
            getFetchStrategies() >> [:]
            getCriteria() >> [org.grails.datastore.mapping.query.Restrictions.eq('author', 'Clavell')]
            getProjections() >> []
            getOrders() >> []
        }

        when:
        Observable observable = finder.invoke(Book, 'findByTitle', detachedCriteria, ['Shogun'] as Object[]) as Observable
        Book result = observable.toBlocking().first() as Book

        then:
        1 * datastoreClient.createQuery(Book) >> query
        1 * query.add({ Query.Criterion it -> it instanceof Query.PropertyCriterion })
        1 * query.add({ Query.Criterion it -> it instanceof Query.Junction })
        1 * query.singleResult() >> Observable.just(new Book(title: 'Shogun'))
        result.title == 'Shogun'
    }

    void "findBy invoke(Class, methodName, DetachedCriteria, Object[]) with a null detachedCriteria never merges anything onto the built query"() {
        given:
        def finder = RxSingleResultFinder.findBy(datastoreClient)

        when:
        Observable observable = finder.invoke(Book, 'findByTitle', (grails.gorm.DetachedCriteria) null, ['Shogun'] as Object[]) as Observable
        Book result = observable.toBlocking().first() as Book

        then:
        1 * datastoreClient.createQuery(Book) >> query
        0 * query.add({ Query.Criterion it -> it instanceof Query.PropertyCriterion })
        1 * query.add({ Query.Criterion it -> it instanceof Query.Junction })
        1 * query.singleResult() >> Observable.just(new Book(title: 'Shogun'))
        result.title == 'Shogun'
    }

    void "findBy applies additional criteria to the query when present"() {
        given:
        def finder = RxSingleResultFinder.findBy(datastoreClient)
        def session = Stub(Session) {
            getMappingContext() >> mappingContext
        }
        query.getSession() >> session
        entity.getJavaClass() >> Book

        when:
        finder.invoke(Book, 'findByTitle', { -> }, ['Shogun'] as Object[])

        then:
        1 * datastoreClient.createQuery(Book) >> query
        1 * query.getSession() >> session
        1 * query.singleResult()
        noExceptionThrown()
    }

    void "findBy applies query arguments to the query when present"() {
        given:
        def finder = RxSingleResultFinder.findBy(datastoreClient)

        when:
        finder.invoke(Book, 'findByTitle', ['Shogun', [max: 5]] as Object[])

        then:
        1 * datastoreClient.createQuery(Book) >> query
        1 * query.max(5)
        1 * query.singleResult()
    }

    void "findByBoolean isMethodMatch matches boolean style method names"() {
        expect:
        RxSingleResultFinder.findByBoolean(datastoreClient).isMethodMatch('findActive')
    }

    void "findByBoolean queries by the boolean property, combining it as a required expression with the rest via Or"() {
        given:
        def finder = RxSingleResultFinder.findByBoolean(datastoreClient)

        when:
        Observable observable = finder.invoke(Person, 'findActiveByNameOrCity', ['Fred', 'London'] as Object[]) as Observable
        Person result = observable.toBlocking().first() as Person

        then:
        1 * datastoreClient.createQuery(Person) >> query
        1 * query.add({ Query.Criterion it -> it instanceof Query.Conjunction && ((Query.Junction) it).criteria.size() == 2 })
        1 * query.singleResult() >> Observable.just(new Person(name: 'Fred', active: true))
        result.name == 'Fred'
    }

    void "findOrCreateBy isMethodMatch matches findOrCreateBy method names only"() {
        expect:
        RxSingleResultFinder.findOrCreateBy(datastoreClient).isMethodMatch('findOrCreateByName')
        !RxSingleResultFinder.findOrCreateBy(datastoreClient).isMethodMatch('findByName')
    }

    void "findOrCreateBy returns the existing entity when the underlying query already finds a match"() {
        given:
        def finder = RxSingleResultFinder.findOrCreateBy(datastoreClient)
        def existing = new Person(name: 'Fred')

        when:
        Observable observable = finder.invoke(Person, 'findOrCreateByName', ['Fred'] as Object[]) as Observable
        Person result = observable.toBlocking().first() as Person

        then:
        1 * datastoreClient.createQuery(Person) >> query
        1 * query.singleResult() >> Observable.just(existing)
        result.is(existing)
    }

    void "findOrCreateBy creates a new instance from the query arguments when no match exists and saving is not required"() {
        given:
        def finder = RxSingleResultFinder.findOrCreateBy(datastoreClient)

        when:
        Observable observable = finder.invoke(Person, 'findOrCreateByName', ['Fred'] as Object[]) as Observable
        Person result = observable.toBlocking().first() as Person

        then:
        1 * datastoreClient.createQuery(Person) >> query
        1 * query.singleResult() >> Observable.empty()
        result.name == 'Fred'
    }

    void "findOrCreateBy signals completion after emitting the new instance when saving is not required"() {
        given:
        def finder = RxSingleResultFinder.findOrCreateBy(datastoreClient)

        when:
        Observable observable = finder.invoke(Person, 'findOrCreateByName', ['Fred'] as Object[]) as Observable
        def subscriber = new rx.observers.TestSubscriber()
        observable.subscribe(subscriber)
        subscriber.awaitTerminalEvent(5, java.util.concurrent.TimeUnit.SECONDS)

        then:
        1 * datastoreClient.createQuery(Person) >> query
        1 * query.singleResult() >> Observable.empty()
        subscriber.assertCompleted()
        subscriber.assertNoErrors()
        subscriber.onNextEvents.size() == 1
        (subscriber.onNextEvents[0] as Person).name == 'Fred'
    }

    void "findOrCreateBy signals an error rather than hanging when a non-Equal expression reaches the empty-result construction path"() {
        given:
        // The empty-result construction path runs inside a spawned Thread - unlike the
        // synchronous SingleResultFinder, a thrown exception there does not propagate as a normal
        // method-call exception, so it must be forwarded to the Observable's error channel
        // explicitly (via Subscriber#onError) or it would kill the thread silently and hang any
        // blocking call on the returned Observable forever.
        def finder = RxSingleResultFinder.findOrCreateBy(datastoreClient)

        when:
        Observable observable = finder.invoke(Person, 'findOrCreateByNameLike', ['Fre%'] as Object[]) as Observable
        def subscriber = new rx.observers.TestSubscriber()
        observable.subscribe(subscriber)
        subscriber.awaitTerminalEvent(5, java.util.concurrent.TimeUnit.SECONDS)

        then:
        1 * datastoreClient.createQuery(Person) >> query
        1 * query.singleResult() >> Observable.empty()
        subscriber.assertError(groovy.lang.MissingMethodException)
        subscriber.onNextEvents.isEmpty()
    }

    void "findOrSaveBy isMethodMatch matches findOrSaveBy method names only"() {
        expect:
        RxSingleResultFinder.findOrSaveBy(datastoreClient).isMethodMatch('findOrSaveByName')
        !RxSingleResultFinder.findOrSaveBy(datastoreClient).isMethodMatch('findOrCreateByName')
    }

    void "findOrSaveBy returns the existing entity when the underlying query already finds a match"() {
        given:
        def finder = RxSingleResultFinder.findOrSaveBy(datastoreClient)
        def existing = new Person(name: 'Fred')

        when:
        Observable observable = finder.invoke(Person, 'findOrSaveByName', ['Fred'] as Object[]) as Observable
        Person result = observable.toBlocking().first() as Person

        then:
        1 * datastoreClient.createQuery(Person) >> query
        1 * query.singleResult() >> Observable.just(existing)
        result.is(existing)
    }

    void "findOrSaveBy creates, saves and returns a new instance when no match exists"() {
        given:
        def finder = RxSingleResultFinder.findOrSaveBy(datastoreClient)
        entity.getPropertyByName('name') >> Stub(PersistentProperty) { getName() >> 'name'; getType() >> String }

        when:
        Observable observable = finder.invoke(PersonThatSavesSuccessfully, 'findOrSaveByName', ['Fred'] as Object[]) as Observable
        PersonThatSavesSuccessfully result = observable.toBlocking().first() as PersonThatSavesSuccessfully

        then:
        1 * datastoreClient.createQuery(PersonThatSavesSuccessfully) >> query
        1 * query.singleResult() >> Observable.empty()
        result.name == 'Fred'
    }

    void "findOrSaveBy propagates the error when saving the newly created instance fails"() {
        given:
        def finder = RxSingleResultFinder.findOrSaveBy(datastoreClient)

        when:
        Observable observable = finder.invoke(PersonThatFailsToSave, 'findOrSaveByName', ['Fred'] as Object[]) as Observable
        observable.toBlocking().first()

        then:
        1 * datastoreClient.createQuery(PersonThatFailsToSave) >> query
        1 * query.singleResult() >> Observable.empty()
        def ex = thrown(IllegalStateException)
        ex.message == 'save failed'
    }

    private static class Book {
        String title
        String author
    }

    private static class Person {
        String name
        boolean active
    }

    private static class PersonThatSavesSuccessfully implements RxEntity<PersonThatSavesSuccessfully> {
        String name

        @Override
        Observable<PersonThatSavesSuccessfully> save(Map<String, Object> arguments) {
            Observable.just(this)
        }
    }

    private static class PersonThatFailsToSave implements RxEntity<PersonThatFailsToSave> {
        String name

        @Override
        Observable<PersonThatFailsToSave> save(Map<String, Object> arguments) {
            Observable.error(new IllegalStateException('save failed'))
        }
    }
}

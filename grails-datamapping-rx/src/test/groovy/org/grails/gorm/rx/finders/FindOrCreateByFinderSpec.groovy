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
import org.grails.datastore.gorm.finders.DynamicFinderInvocation
import org.grails.datastore.gorm.finders.MethodExpression
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.query.Query
import org.grails.datastore.rx.RxDatastoreClient
import org.springframework.core.convert.support.DefaultConversionService
import rx.Observable
import spock.lang.Specification

class FindOrCreateByFinderSpec extends Specification {

    RxDatastoreClient datastoreClient = Mock()
    MappingContext mappingContext = Mock()
    PersistentEntity entity = Mock()
    Query query = Mock()

    def setup() {
        entity.getMappingContext() >> mappingContext
        entity.getJavaClass() >> Person
        mappingContext.getConversionService() >> new DefaultConversionService()
        query.getEntity() >> entity
    }

    def "overrides the inherited pattern to match findOrCreateBy method names"() {
        when:
        def finder = new FindOrCreateByFinder(datastoreClient)

        then:
        1 * datastoreClient.getMappingContext() >> mappingContext
        finder.isMethodMatch('findOrCreateByName')
        !finder.isMethodMatch('findByName')
    }

    def "shouldSaveOnCreate defaults to false"() {
        given:
        def finder = new FindOrCreateByFinder(datastoreClient)

        expect:
        !finder.shouldSaveOnCreate()
    }

    def "returns the existing entity when the underlying query already finds a match"() {
        given:
        def finder = new FindOrCreateByFinder(datastoreClient)
        def invocation = nameEqualsFredInvocation(Person)
        def existing = new Person(name: 'Fred')

        when:
        Observable observable = finder.doInvokeInternal(invocation) as Observable
        Person result = observable.toBlocking().first() as Person

        then:
        1 * datastoreClient.createQuery(Person) >> query
        1 * query.singleResult() >> Observable.just(existing)
        result.is(existing)
    }

    def "creates a new instance from the query arguments when no match exists and saving is not required"() {
        given:
        def finder = new FindOrCreateByFinder(datastoreClient)
        def invocation = nameEqualsFredInvocation(Person)

        when:
        Observable observable = finder.doInvokeInternal(invocation) as Observable
        Person result = observable.toBlocking().first() as Person

        then:
        1 * datastoreClient.createQuery(Person) >> query
        1 * query.singleResult() >> Observable.empty()
        result.name == 'Fred'
    }

    def "creates, saves and returns a new instance when shouldSaveOnCreate is overridden to return true"() {
        given:
        def finder = new SavingFindOrCreateByFinder(datastoreClient)
        def invocation = nameEqualsFredInvocation(PersonThatSavesSuccessfully)

        when:
        Observable observable = finder.doInvokeInternal(invocation) as Observable
        PersonThatSavesSuccessfully result = observable.toBlocking().first() as PersonThatSavesSuccessfully

        then:
        1 * datastoreClient.createQuery(PersonThatSavesSuccessfully) >> query
        1 * query.singleResult() >> Observable.empty()
        result.name == 'Fred'
    }

    def "completes without emitting a result when saving the newly created instance fails"() {
        given:
        def finder = new SavingFindOrCreateByFinder(datastoreClient)
        def invocation = nameEqualsFredInvocation(PersonThatFailsToSave)

        when:
        Observable observable = finder.doInvokeInternal(invocation) as Observable
        observable.toBlocking().first()

        then:
        1 * datastoreClient.createQuery(PersonThatFailsToSave) >> query
        1 * query.singleResult() >> Observable.empty()
        thrown(NoSuchElementException)
    }

    private static DynamicFinderInvocation nameEqualsFredInvocation(Class type) {
        def nameExpression = new MethodExpression.Equal(type, 'name')
        nameExpression.setArguments(['Fred'] as Object[])
        new DynamicFinderInvocation(type, 'findOrCreateByName', [] as Object[], [nameExpression], null, null)
    }

    private static class Person {
        String name
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

    private static class SavingFindOrCreateByFinder extends FindOrCreateByFinder {
        SavingFindOrCreateByFinder(RxDatastoreClient datastoreClient) {
            super(datastoreClient)
        }

        @Override
        protected boolean shouldSaveOnCreate() {
            true
        }
    }
}

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
package org.grails.gorm.rx.api

import grails.gorm.rx.DetachedCriteria
import jakarta.persistence.criteria.JoinType
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.query.Query
import org.grails.datastore.mapping.query.api.QueryArgumentsAware
import org.grails.datastore.rx.internal.RxDatastoreClientImplementor
import org.grails.datastore.rx.query.RxQuery
import org.springframework.core.convert.ConversionService
import rx.Observable
import rx.Subscriber
import spock.lang.Specification

/**
 * Exercises the reactive query-execution methods on {@link DetachedCriteria} (rx) - the ones
 * that go through {@code prepareQuery} and {@link RxGormEnhancer#findStaticApi}. This requires
 * a fake entity registered with {@link RxGormEnhancer}, since those static lookups can't be
 * intercepted from outside the {@code CompileStatic} call site with metaclass-based mocking.
 * Package-local so the test can call the {@code protected static}
 * {@code RxGormEnhancer.registerEntityWithConnectionSource} directly rather than driving the
 * full {@code registerEntity} multi-tenancy/connection-source resolution machinery.
 */
class DetachedCriteriaQuerySpec extends Specification {

    Query mockQuery
    RxDatastoreClientImplementor datastoreClient

    void setup() {
        def queryEntity = Mock(PersistentEntity) {
            getMappingContext() >> Mock(MappingContext) {
                getConversionService() >> Mock(ConversionService)
            }
        }
        mockQuery = Mock(Query, additionalInterfaces: [RxQuery, QueryArgumentsAware]) {
            getEntity() >> queryEntity
        }
        datastoreClient = Mock(RxDatastoreClientImplementor) {
            createQuery(QueryTestEntity, _ as Map) >> mockQuery
        }
        def persistentEntity = Mock(PersistentEntity) {
            getJavaClass() >> QueryTestEntity
            getName() >> QueryTestEntity.name
        }
        def staticApi = Mock(RxGormStaticApi) {
            getDatastoreClient() >> datastoreClient
        }
        datastoreClient.createStaticApi(persistentEntity, ConnectionSource.DEFAULT) >> staticApi
        datastoreClient.createInstanceApi(persistentEntity, ConnectionSource.DEFAULT) >> Mock(RxGormInstanceApi)
        datastoreClient.createValidationApi(persistentEntity, ConnectionSource.DEFAULT) >> Mock(RxGormValidationApi)
        RxGormEnhancer.registerEntityWithConnectionSource(persistentEntity, ConnectionSource.DEFAULT, ConnectionSource.DEFAULT, datastoreClient)
    }

    void cleanup() {
        RxGormEnhancer.close()
    }

    void "find applies a max of 1 and delegates to RxQuery#findAll"() {
        given:
        def criteria = new DetachedCriteria(QueryTestEntity)
        def observable = Observable.just(new QueryTestEntity())
        mockQuery.findAll(_ as Map) >> observable

        when:
        def result = criteria.find()

        then:
        1 * mockQuery.max(1)
        result.is(observable)
    }

    void "findAll delegates to RxQuery#findAll"() {
        given:
        def criteria = new DetachedCriteria(QueryTestEntity)
        def observable = Observable.just(new QueryTestEntity())
        mockQuery.findAll(_ as Map) >> observable

        when:
        def result = criteria.findAll()

        then:
        result.is(observable)
    }

    void "get(Map) applies a max of 1 and delegates to RxQuery#singleResult"() {
        given:
        def criteria = new DetachedCriteria(QueryTestEntity)
        def observable = Observable.just(new QueryTestEntity())
        mockQuery.singleResult(_ as Map) >> observable

        when:
        def result = criteria.get([:])

        then:
        1 * mockQuery.max(1)
        result.is(observable)
    }

    void "get(Closure) applies a max of 1 and delegates to the no-arg RxQuery#singleResult"() {
        given:
        def criteria = new DetachedCriteria(QueryTestEntity)
        def observable = Observable.just(new QueryTestEntity())
        mockQuery.singleResult() >> observable

        when:
        def result = criteria.get()

        then:
        1 * mockQuery.max(1)
        result.is(observable)
    }

    void "toList/list convert the RxQuery#findAll observable into an observable list"() {
        given:
        def criteria = new DetachedCriteria(QueryTestEntity)
        def one = new QueryTestEntity(name: 'one')
        def two = new QueryTestEntity(name: 'two')
        mockQuery.findAll(_ as Map) >> Observable.just(one, two)

        when:
        def toListResult = criteria.toList().toBlocking().first()
        def listResult = criteria.list().toBlocking().first()

        then:
        toListResult == [one, two]
        listResult == [one, two]
    }

    void "getCount/count apply a count projection and delegate to RxQuery#singleResult"() {
        given:
        def criteria = new DetachedCriteria(QueryTestEntity)
        def observable = Observable.just(2)
        mockQuery.singleResult(_ as Map) >> observable
        mockQuery.projections() >> Mock(Query.ProjectionList)

        when:
        def getCountResult = criteria.getCount()
        def countResult = criteria.count()

        then:
        getCountResult.is(observable)
        countResult.is(observable)
    }

    void "updateAll delegates to RxQuery#updateAll"() {
        given:
        def criteria = new DetachedCriteria(QueryTestEntity)
        def observable = Observable.just(1)
        mockQuery.updateAll(_ as Map) >> observable

        when:
        def result = criteria.updateAll(name: 'Bob')

        then:
        result.is(observable)
    }

    void "deleteAll delegates to RxQuery#deleteAll"() {
        given:
        def criteria = new DetachedCriteria(QueryTestEntity)
        def observable = Observable.just(1)
        mockQuery.deleteAll() >> observable

        when:
        def result = criteria.deleteAll()

        then:
        result.is(observable)
    }

    void "toQuery returns the prepared Query"() {
        given:
        def criteria = new DetachedCriteria(QueryTestEntity)

        when:
        def result = criteria.toQuery()

        then:
        result.is(mockQuery)
    }

    void "toObservable delegates to findAll"() {
        given:
        def criteria = new DetachedCriteria(QueryTestEntity)
        def observable = Observable.just(new QueryTestEntity())
        mockQuery.findAll(_ as Map) >> observable

        when:
        def result = criteria.toObservable()

        then:
        result.is(observable)
    }

    void "subscribe delegates to findAll().subscribe(subscriber)"() {
        given:
        def criteria = new DetachedCriteria(QueryTestEntity)
        def entity = new QueryTestEntity()
        mockQuery.findAll(_ as Map) >> Observable.just(entity)
        def received = null
        def subscriber = new Subscriber<QueryTestEntity>() {
            void onCompleted() { }
            void onError(Throwable e) { }
            void onNext(QueryTestEntity e) { received = e }
        }

        when:
        criteria.subscribe(subscriber)

        then:
        received.is(entity)
    }

    void "prepareQuery applies the default max and offset when set"() {
        given:
        def criteria = new DetachedCriteria(QueryTestEntity)
        def withMax = criteria.max(10)
        def withOffset = criteria.offset(5)

        when:
        withMax.findAll()

        then:
        1 * mockQuery.max(10)

        when:
        withOffset.findAll()

        then:
        1 * mockQuery.offset(5)
    }

    void "prepareQuery applies join and select fetch strategies"() {
        given:
        def criteria = new DetachedCriteria(QueryTestEntity)
        criteria.join('books')
        criteria.select('author')

        when:
        criteria.findAll()

        then:
        1 * mockQuery.join('books')
        1 * mockQuery.select('author')
    }

    void "prepareQuery passes a custom JoinType through to the query"() {
        given:
        def criteria = new DetachedCriteria(QueryTestEntity)
        criteria.join('books', JoinType.LEFT)

        when:
        criteria.findAll()

        then:
        1 * mockQuery.join('books', JoinType.LEFT)
        0 * mockQuery.join('books')
    }

    void "prepareQuery merges an additional criteria closure"() {
        given:
        def criteria = new DetachedCriteria(QueryTestEntity)

        when:
        criteria.findAll([:]) { eq('name', 'Bob') }

        then:
        1 * mockQuery.add({ it.property == 'name' && it.value == 'Bob' })
    }
}

class QueryTestEntity {
    Long id
    String name
}

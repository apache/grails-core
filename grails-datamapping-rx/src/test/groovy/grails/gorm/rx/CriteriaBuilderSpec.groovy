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
package grails.gorm.rx

import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.query.Query
import org.grails.datastore.mapping.query.QueryCreator
import org.grails.datastore.rx.query.RxQuery
import rx.Observable
import spock.lang.Specification

/**
 * Exercises {@link CriteriaBuilder}'s own reactive terminal operations. The shared query-DSL
 * methods it inherits from AbstractCriteriaBuilder (eq, gt, and/or/not, projections, etc.) are
 * already fully covered by grails.gorm.CriteriaBuilderSpec in grails-datamapping-core, against
 * the same base class -- coverage there applies regardless of which subclass exercises it, so
 * this spec only needs to cover the methods declared directly on this class.
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
    Query query = Mock(Query, additionalInterfaces: [RxQuery])
    QueryCreator queryCreator = Stub(QueryCreator) {
        createQuery(CriteriaBuilderTestPerson) >> query
        isSchemaless() >> false
    }

    def setup() {
        // Field initializers run top-to-bottom, so this mutual reference has to be wired up
        // after both fields exist rather than inside either Stub() block.
        persistentEntity.getMappingContext() >> mappingContext
        query.getEntity() >> persistentEntity
    }

    CriteriaBuilder<CriteriaBuilderTestPerson> newBuilder() {
        def criteria = new CriteriaBuilder<CriteriaBuilderTestPerson>(CriteriaBuilderTestPerson, queryCreator, mappingContext)
        criteria.@query = query
        criteria
    }

    void "get(Closure) evaluates the closure, flags a unique result and returns a single observable"() {
        given:
        def criteria = newBuilder()
        Observable<CriteriaBuilderTestPerson> observable = Observable.just(new CriteriaBuilderTestPerson())
        ((RxQuery) query).singleResult() >> observable

        when:
        def result = criteria.get { eq('name', 'a') }

        then:
        result.is(observable)
        criteria.uniqueResult
        1 * query.add(_)
    }

    void "get() flags a unique result and returns a single observable without evaluating a closure"() {
        given:
        def criteria = newBuilder()
        Observable<CriteriaBuilderTestPerson> observable = Observable.just(new CriteriaBuilderTestPerson())
        query.singleResult() >> observable

        when:
        def result = criteria.get()

        then:
        result.is(observable)
        criteria.uniqueResult
    }

    void "find(Closure) delegates to get(Closure)"() {
        given:
        def criteria = newBuilder()
        Observable<CriteriaBuilderTestPerson> observable = Observable.just(new CriteriaBuilderTestPerson())
        ((RxQuery) query).singleResult() >> observable

        when:
        def result = criteria.find { eq('name', 'a') }

        then:
        result.is(observable)
        1 * query.add(_)
    }

    void "find() with no closure delegates to get() with a null closure"() {
        given:
        def criteria = newBuilder()
        query.singleResult() >> Observable.empty()

        when:
        def result = criteria.find()

        then:
        result != null
        criteria.uniqueResult
    }

    void "findAll(Closure) delegates to findAll with an empty argument map"() {
        given:
        def criteria = newBuilder()
        Observable<CriteriaBuilderTestPerson> observable = Observable.just(new CriteriaBuilderTestPerson())
        ((RxQuery) query).findAll([:]) >> observable

        when:
        def result = criteria.findAll { eq('name', 'a') }

        then:
        result.is(observable)
        1 * query.add(_)
    }

    void "findAll(Map, Closure) prepares the query and returns the observable results"() {
        given:
        def criteria = newBuilder()
        Observable<CriteriaBuilderTestPerson> observable = Observable.just(new CriteriaBuilderTestPerson())
        ((RxQuery) query).findAll([:]) >> observable

        when:
        def result = criteria.findAll([:]) { eq('name', 'a') }

        then:
        result.is(observable)
        1 * query.add(_)
    }

    void "findAll applies any pre-populated order entries before executing"() {
        given:
        def criteria = newBuilder()
        criteria.orderEntries << Query.Order.asc('name')
        ((RxQuery) query).findAll([:]) >> Observable.empty()

        when:
        criteria.findAll()

        then:
        1 * query.order(_)
    }

    void "list(Map, Closure) collects findAll's results into a single observable list"() {
        given:
        def criteria = newBuilder()
        ((RxQuery) query).findAll([:]) >> Observable.from(['a', 'b'])

        when:
        Observable<List> result = criteria.list([:]) { eq('name', 'a') }

        then:
        result.toBlocking().first() == ['a', 'b']
    }

    void "list(Closure) collects findAll's results into a single observable list"() {
        given:
        def criteria = newBuilder()
        ((RxQuery) query).findAll([:]) >> Observable.from(['a', 'b'])

        when:
        Observable<List> result = criteria.list { eq('name', 'a') }

        then:
        result.toBlocking().first() == ['a', 'b']
    }

    void "count(Map, Closure) applies a count projection and returns a single observable result"() {
        given:
        def criteria = newBuilder()
        Query.ProjectionList projectionList = Mock(Query.ProjectionList)
        query.projections() >> projectionList
        ((RxQuery) query).singleResult([:]) >> Observable.just(5)

        when:
        Observable<Number> result = criteria.count([:]) { eq('name', 'a') }

        then:
        result.toBlocking().first() == 5
        1 * projectionList.count()
    }

    void "count(Closure) delegates to count with an empty argument map"() {
        given:
        def criteria = newBuilder()
        Query.ProjectionList projectionList = Mock(Query.ProjectionList)
        query.projections() >> projectionList
        ((RxQuery) query).singleResult([:]) >> Observable.just(5)

        when:
        Observable<Number> result = criteria.count { eq('name', 'a') }

        then:
        result.toBlocking().first() == 5
    }

    void "listDistinct(Map, Closure) applies a distinct projection and collects the results"() {
        given:
        def criteria = newBuilder()
        Query.ProjectionList projectionList = Mock(Query.ProjectionList)
        query.projections() >> projectionList
        ((RxQuery) query).findAll([:]) >> Observable.from(['a'])

        when:
        Observable<List> result = criteria.listDistinct([:]) { eq('name', 'a') }

        then:
        result.toBlocking().first() == ['a']
        1 * projectionList.distinct()
    }

    void "listDistinct(Closure) delegates to listDistinct with an empty argument map"() {
        given:
        def criteria = newBuilder()
        Query.ProjectionList projectionList = Mock(Query.ProjectionList)
        query.projections() >> projectionList
        ((RxQuery) query).findAll([:]) >> Observable.from(['a'])

        when:
        Observable<List> result = criteria.listDistinct { eq('name', 'a') }

        then:
        result.toBlocking().first() == ['a']
    }

    void "a call-style invocation resolves through the reactive invokeList override"() {
        given:
        def criteria = newBuilder()
        ((RxQuery) query).findAll() >> Observable.from(['a'])

        when:
        def result = criteria.call { eq('name', 'a') }

        then:
        result.toBlocking().first() == 'a'
    }
}

class CriteriaBuilderTestPerson {
    Long id
    String name
}

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
import org.grails.datastore.mapping.query.Query
import org.grails.datastore.mapping.query.QueryCreator
import org.grails.datastore.rx.query.RxQuery
import org.springframework.core.convert.support.DefaultConversionService
import rx.Observable
import spock.lang.Specification

class CriteriaBuilderSpec extends Specification {

    Query query
    QueryCreator queryCreator
    MappingContext mappingContext
    PersistentEntity persistentEntity

    void setup() {
        persistentEntity = Stub(PersistentEntity)
        mappingContext = Stub(MappingContext)
        queryCreator = Stub(QueryCreator)
        query = Mock(Query, additionalInterfaces: [RxQuery])

        mappingContext.getPersistentEntity(_) >> persistentEntity
        mappingContext.getConversionService() >> new DefaultConversionService()
        persistentEntity.getMappingContext() >> mappingContext
        queryCreator.createQuery(_) >> query
        queryCreator.isSchemaless() >> false
        query.getEntity() >> persistentEntity
    }

    private CriteriaBuilder<Book> newCriteria() {
        new CriteriaBuilder<Book>(Book, queryCreator, mappingContext)
    }

    void "get(Closure) initializes the query, applies the closure and returns a single reactive result"() {
        given:
        CriteriaBuilder<Book> criteria = newCriteria()
        Observable<Book> observable = Observable.just(new Book(title: 'Groovy in Action'))

        when:
        Observable<Book> result = criteria.get { eq('title', 'Groovy in Action') }

        then:
        1 * query.singleResult() >> observable
        result.is(observable)
        criteria.uniqueResult
    }

    void "get() marks the query as a unique result and delegates to the underlying query"() {
        given:
        CriteriaBuilder<Book> criteria = newCriteria()
        Observable<Book> observable = Observable.just(new Book(title: 'Effective Java'))

        when:
        Observable<Book> result = criteria.get()

        then:
        1 * query.singleResult() >> observable
        result.is(observable)
        criteria.uniqueResult
    }

    void "find(Closure) delegates to get(Closure)"() {
        given:
        CriteriaBuilder<Book> criteria = newCriteria()
        Observable<Book> observable = Observable.just(new Book(title: 'Domain Driven Design'))

        when:
        Observable<Book> result = criteria.find { eq('title', 'Domain Driven Design') }

        then:
        1 * query.singleResult() >> observable
        result.is(observable)
        criteria.uniqueResult
    }

    void "find() with no closure still delegates to get()"() {
        given:
        CriteriaBuilder<Book> criteria = newCriteria()
        Observable<Book> observable = Observable.just(new Book(title: 'Refactoring'))

        when:
        Observable<Book> result = criteria.find()

        then:
        1 * query.singleResult() >> observable
        result.is(observable)
        criteria.uniqueResult
    }

    void "findAll(Closure) delegates to findAll(Map, Closure) with an empty argument map"() {
        given:
        CriteriaBuilder<Book> criteria = newCriteria()
        Book book = new Book(title: 'A')
        Observable<Book> observable = Observable.just(book)

        when:
        Observable<Book> result = criteria.findAll { eq('title', 'A') }

        then:
        1 * query.findAll(Collections.emptyMap()) >> observable
        result.is(observable)
    }

    void "findAll(Map, Closure) populates query arguments and returns the reactive results for those arguments"() {
        given:
        CriteriaBuilder<Book> criteria = newCriteria()
        Book book = new Book(title: 'A')
        Observable<Book> observable = Observable.just(book)
        Map args = [max: 5]

        when:
        Observable<Book> result = criteria.findAll(args) { eq('title', 'A') }

        then:
        1 * query.findAll(args) >> observable
        result.is(observable)
    }

    void "findAll(Map, Closure) applies order clauses declared in the closure to the underlying query before executing"() {
        given:
        CriteriaBuilder<Book> criteria = newCriteria()
        Book book = new Book(title: 'A')
        Observable<Book> observable = Observable.just(book)

        when:
        Observable<Book> result = criteria.findAll([:]) { order('title') }

        then:
        1 * query.order(_ as Query.Order) >> query
        1 * query.findAll([:]) >> observable
        result.is(observable)
    }

    void "list(Map, Closure) converts the found results into a list observable"() {
        given:
        CriteriaBuilder<Book> criteria = newCriteria()
        Book first = new Book(title: 'A')
        Book second = new Book(title: 'B')

        when:
        Observable<List<Book>> result = criteria.list([:]) { eq('title', 'A') }

        then:
        1 * query.findAll([:]) >> Observable.just(first, second)
        result.toBlocking().single() == [first, second]
    }

    void "list(Closure) converts the found results into a list observable using an empty argument map"() {
        given:
        CriteriaBuilder<Book> criteria = newCriteria()
        Book book = new Book(title: 'A')

        when:
        Observable<List<Book>> result = criteria.list { eq('title', 'A') }

        then:
        1 * query.findAll(Collections.emptyMap()) >> Observable.just(book)
        result.toBlocking().single() == [book]
    }

    void "count(Map, Closure) applies a count projection and returns the reactive count without throwing"() {
        given:
        CriteriaBuilder<Book> criteria = newCriteria()
        Query.ProjectionList projectionList = new Query.ProjectionList()
        Map args = [:]

        when:
        Observable<Number> result = criteria.count(args) { eq('title', 'A') }

        then:
        1 * query.projections() >> projectionList
        1 * query.singleResult(args) >> Observable.just(3)
        result.toBlocking().single() == 3
        projectionList.projectionList.size() == 1
        projectionList.projectionList[0] instanceof Query.CountProjection
    }

    void "count(Closure) delegates to count(Map, Closure) with an empty argument map"() {
        given:
        CriteriaBuilder<Book> criteria = newCriteria()
        Query.ProjectionList projectionList = new Query.ProjectionList()

        when:
        Observable<Number> result = criteria.count { eq('title', 'A') }

        then:
        1 * query.projections() >> projectionList
        1 * query.singleResult(Collections.emptyMap()) >> Observable.just(7)
        result.toBlocking().single() == 7
        projectionList.projectionList[0] instanceof Query.CountProjection
    }

    void "listDistinct(Map, Closure) applies a distinct projection and returns the reactive results as a list"() {
        given:
        CriteriaBuilder<Book> criteria = newCriteria()
        Query.ProjectionList projectionList = new Query.ProjectionList()
        Book book = new Book(title: 'A')
        Map args = [:]

        when:
        Observable<List<Book>> result = criteria.listDistinct(args) { eq('title', 'A') }

        then:
        1 * query.projections() >> projectionList
        1 * query.findAll(args) >> Observable.just(book)
        result.toBlocking().single() == [book]
        projectionList.projectionList.size() == 1
        projectionList.projectionList[0] instanceof Query.DistinctProjection
    }

    void "listDistinct(Closure) delegates to listDistinct(Map, Closure) with an empty argument map"() {
        given:
        CriteriaBuilder<Book> criteria = newCriteria()
        Query.ProjectionList projectionList = new Query.ProjectionList()
        Book book = new Book(title: 'A')

        when:
        Observable<List<Book>> result = criteria.listDistinct { eq('title', 'A') }

        then:
        1 * query.projections() >> projectionList
        1 * query.findAll(Collections.emptyMap()) >> Observable.just(book)
        result.toBlocking().single() == [book]
        projectionList.projectionList[0] instanceof Query.DistinctProjection
    }

    void "call(Closure) applies the closure then executes the RX invokeList override for a non-unique result"() {
        given:
        CriteriaBuilder<Book> criteria = newCriteria()
        Book book = new Book(title: 'A')
        Observable<Book> observable = Observable.just(book)

        when:
        Object result = criteria.call { eq('title', 'A') }

        then:
        1 * query.findAll() >> observable
        result.is(observable)
        !criteria.uniqueResult
    }
}

class Book {
    String title
}

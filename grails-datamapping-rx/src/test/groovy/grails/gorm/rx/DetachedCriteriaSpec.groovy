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

import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.core.connections.ConnectionSourceSettings
import org.grails.datastore.mapping.core.connections.ConnectionSources
import org.grails.datastore.mapping.model.ClassMapping
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.query.Query
import org.grails.datastore.mapping.query.api.QueryArgumentsAware
import org.grails.datastore.mapping.query.api.QueryableCriteria
import org.grails.datastore.rx.internal.RxDatastoreClientImplementor
import org.grails.datastore.rx.query.RxQuery
import org.grails.gorm.rx.api.RxGormEnhancer
import org.grails.gorm.rx.api.RxGormInstanceApi
import org.grails.gorm.rx.api.RxGormStaticApi
import org.grails.gorm.rx.api.RxGormValidationApi
import org.springframework.core.convert.support.DefaultConversionService
import rx.Observable
import rx.Subscriber
import rx.Subscription
import spock.lang.Specification
import spock.lang.Unroll

class DetachedCriteriaSpec extends Specification {

    Query query
    MappingContext mappingContext
    PersistentEntity entity
    RxDatastoreClientImplementor datastoreClient
    RxGormStaticApi<Volume> registeredStaticApi

    void setup() {
        mappingContext = Stub(MappingContext) {
            getConversionService() >> new DefaultConversionService()
        }
        ConnectionSourceSettings connectionSourceSettings = new ConnectionSourceSettings()
        def connectionSource = Stub(ConnectionSource) {
            getName() >> ConnectionSource.DEFAULT
            getSettings() >> connectionSourceSettings
        }
        ConnectionSources connectionSources = Stub(ConnectionSources) {
            getDefaultConnectionSource() >> connectionSource
            getAllConnectionSources() >> [connectionSource]
        }
        entity = Stub(PersistentEntity) {
            getJavaClass() >> Volume
            getName() >> Volume.name
            getMapping() >> Stub(ClassMapping) { getMappedForm() >> null }
            getIdentity() >> Stub(PersistentProperty) { getName() >> 'id' }
            getMappingContext() >> mappingContext
        }
        mappingContext.getPersistentEntity(_) >> entity

        query = Mock(Query, additionalInterfaces: [RxQuery, QueryArgumentsAware])
        query.getEntity() >> entity

        datastoreClient = Mock(RxDatastoreClientImplementor) {
            getConnectionSources() >> connectionSources
            getMappingContext() >> mappingContext
            createQuery(_, _) >> query
            createStaticApi(_, _) >> { registeredStaticApi }
            createInstanceApi(_, _) >> Stub(RxGormInstanceApi)
            createValidationApi(_, _) >> Stub(RxGormValidationApi)
        }
        registeredStaticApi = new RxGormStaticApi<Volume>(entity, datastoreClient)
        RxGormEnhancer.registerEntity(entity, datastoreClient)
    }

    void cleanup() {
        RxGormEnhancer.close()
    }

    private DetachedCriteria<Volume> newCriteria() {
        new DetachedCriteria<Volume>(Volume)
    }

    void "the single argument constructor sets the target class and leaves the alias unset"() {
        when:
        DetachedCriteria<Volume> criteria = new DetachedCriteria<Volume>(Volume)

        then:
        criteria.targetClass == Volume
        criteria.alias == null
    }

    void "the two argument constructor sets the target class and the alias"() {
        when:
        DetachedCriteria<Volume> criteria = new DetachedCriteria<Volume>(Volume, 'b')

        then:
        criteria.targetClass == Volume
        criteria.alias == 'b'
    }

    void "find() with no arguments limits the query to one result and streams it via findAll"() {
        given:
        DetachedCriteria<Volume> criteria = newCriteria()
        Observable<Volume> observable = Observable.just(new Volume(title: 'A'))

        when:
        Observable<Volume> result = criteria.find()

        then:
        1 * query.max(1)
        1 * query.findAll([:]) >> observable
        result.is(observable)
    }

    void "find(Map) forwards the arguments map to findAll while still limiting to one result"() {
        given:
        DetachedCriteria<Volume> criteria = newCriteria()
        Map args = [cache: true]
        Observable<Volume> observable = Observable.empty()

        when:
        Observable<Volume> result = criteria.find(args)

        then:
        1 * query.max(1)
        1 * query.findAll(args) >> observable
        result.is(observable)
    }

    void "find(Map, Closure) applies the additional criteria to the query before executing"() {
        given:
        DetachedCriteria<Volume> criteria = newCriteria()
        Observable<Volume> observable = Observable.empty()

        when:
        Observable<Volume> result = criteria.find([:]) { eq('title', 'A') }

        then:
        1 * query.add(_)
        1 * query.max(1)
        1 * query.findAll([:]) >> observable
        result.is(observable)
    }

    void "findAll() with no arguments streams every matching result without limiting the query"() {
        given:
        DetachedCriteria<Volume> criteria = newCriteria()
        Observable<Volume> observable = Observable.empty()

        when:
        Observable<Volume> result = criteria.findAll()

        then:
        0 * query.max(_)
        1 * query.findAll([:]) >> observable
        result.is(observable)
    }

    void "findAll(Map, Closure) applies the additional criteria and forwards the arguments map"() {
        given:
        DetachedCriteria<Volume> criteria = newCriteria()
        Map args = [max: 10]
        Observable<Volume> observable = Observable.empty()

        when:
        Observable<Volume> result = criteria.findAll(args) { eq('title', 'A') }

        then:
        1 * query.add(_)
        1 * query.findAll(args) >> observable
        result.is(observable)
    }

    void "get(Map, Closure) limits the query to one result and forwards the arguments to singleResult"() {
        given:
        DetachedCriteria<Volume> criteria = newCriteria()
        Map args = [cache: true]
        Observable<Volume> observable = Observable.empty()

        when:
        Observable<Volume> result = criteria.get(args) { eq('title', 'A') }

        then:
        1 * query.add(_)
        1 * query.max(1)
        1 * query.singleResult(args) >> observable
        result.is(observable)
    }

    void "get(Closure) with no map still limits to one result but calls the no-arg singleResult"() {
        given:
        DetachedCriteria<Volume> criteria = newCriteria()
        Observable<Volume> observable = Observable.empty()

        when:
        Observable<Volume> result = criteria.get { eq('title', 'A') }

        then:
        1 * query.add(_)
        1 * query.max(1)
        1 * query.singleResult() >> observable
        result.is(observable)
    }

    void "toList(Map, Closure) collects every matching result into a single list observable"() {
        given:
        DetachedCriteria<Volume> criteria = newCriteria()
        Volume first = new Volume(title: 'A')
        Volume second = new Volume(title: 'B')

        when:
        Observable<List<Volume>> result = criteria.toList([:]) { eq('title', 'A') }

        then:
        1 * query.findAll([:]) >> Observable.just(first, second)
        result.toBlocking().single() == [first, second]
    }

    void "list(Map, Closure) behaves the same as toList(Map, Closure)"() {
        given:
        DetachedCriteria<Volume> criteria = newCriteria()
        Volume volume = new Volume(title: 'A')

        when:
        Observable<List<Volume>> result = criteria.list([:]) { eq('title', 'A') }

        then:
        1 * query.findAll([:]) >> Observable.just(volume)
        result.toBlocking().single() == [volume]
    }

    void "getCount(Map, Closure) applies a count projection and returns the reactive count"() {
        given:
        DetachedCriteria<Volume> criteria = newCriteria()
        Query.ProjectionList projectionList = new Query.ProjectionList()
        Map args = [:]

        when:
        Observable<Number> result = criteria.getCount(args) { eq('title', 'A') }

        then:
        1 * query.projections() >> projectionList
        1 * query.singleResult(args) >> Observable.just(3)
        result.toBlocking().single() == 3
        projectionList.projectionList.size() == 1
        projectionList.projectionList[0] instanceof Query.CountProjection
    }

    void "count(Map, Closure) delegates to getCount(Map, Closure) rather than preparing a second query"() {
        given:
        DetachedCriteria<Volume> criteria = newCriteria()
        Query.ProjectionList projectionList = new Query.ProjectionList()
        Map args = [:]

        when:
        Observable<Number> result = criteria.count(args) { eq('title', 'A') }

        then:
        1 * datastoreClient.createQuery(Volume, args) >> query
        1 * query.projections() >> projectionList
        1 * query.singleResult(args) >> Observable.just(7)
        result.toBlocking().single() == 7
        projectionList.projectionList[0] instanceof Query.CountProjection
    }

    void "updateAll updates all matching entities using an unfiltered default query"() {
        given:
        DetachedCriteria<Volume> criteria = newCriteria()
        Observable<Number> observable = Observable.just(2)

        when:
        Observable<Number> result = criteria.updateAll([title: 'Updated'])

        then:
        1 * datastoreClient.createQuery(Volume, [:]) >> query
        1 * query.updateAll([title: 'Updated']) >> observable
        result.is(observable)
    }

    void "deleteAll deletes all matching entities using an unfiltered default query"() {
        given:
        DetachedCriteria<Volume> criteria = newCriteria()
        Observable<Number> observable = Observable.just(1)

        when:
        Observable<Number> result = criteria.deleteAll()

        then:
        1 * datastoreClient.createQuery(Volume, [:]) >> query
        1 * query.deleteAll() >> observable
        result.is(observable)
    }

    void "toQuery(Map) prepares and returns the underlying query without executing it"() {
        given:
        DetachedCriteria<Volume> criteria = newCriteria().eq('title', 'A')
        Map args = [cache: true]

        when:
        Query result = criteria.toQuery(args)

        then:
        result.is(query)
        1 * query.add(_)
        0 * query.findAll(_)
        0 * query.singleResult(_)
    }

    void "toObservable() delegates to findAll() with no arguments"() {
        given:
        DetachedCriteria<Volume> criteria = newCriteria()
        Observable<Volume> observable = Observable.empty()

        when:
        Observable<Volume> result = criteria.toObservable()

        then:
        1 * query.findAll([:]) >> observable
        result.is(observable)
    }

    void "subscribe(Subscriber) subscribes the given subscriber to the observable returned by findAll()"() {
        given:
        DetachedCriteria<Volume> criteria = newCriteria()
        Volume volume = new Volume(title: 'A')
        List<Volume> received = []
        boolean completed = false
        Subscriber<Volume> subscriber = new Subscriber<Volume>() {
            @Override
            void onCompleted() {
                completed = true
            }

            @Override
            void onError(Throwable e) {
            }

            @Override
            void onNext(Volume v) {
                received << v
            }
        }

        when:
        Subscription subscription = criteria.subscribe(subscriber)

        then:
        1 * query.findAll([:]) >> Observable.just(volume)
        received == [volume]
        completed
        subscription != null
    }

    void "prepareQuery applies the default max only when it has been set via max(int)"() {
        given:
        DetachedCriteria<Volume> criteria = newCriteria().max(5)

        when:
        criteria.findAll()

        then:
        1 * query.max(5)
        1 * query.findAll([:]) >> Observable.empty()
    }

    void "prepareQuery applies the default offset only when it has been set via offset(int)"() {
        given:
        DetachedCriteria<Volume> criteria = newCriteria().offset(3)

        when:
        criteria.findAll()

        then:
        1 * query.offset(3)
        1 * query.findAll([:]) >> Observable.empty()
    }

    void "prepareQuery applies each fetch strategy exactly once rather than duplicating the join or select"() {
        given: "join and select apply fetch strategies that DynamicFinder.applyDetachedCriteria already applies once"
        DetachedCriteria<Volume> criteria = newCriteria().join('author').select('title')

        when:
        criteria.findAll()

        then:
        1 * query.join('author')
        1 * query.select('title')
        1 * query.findAll([:]) >> Observable.empty()
    }

    void "prepareQuery forwards the argument map to the query when it is argument aware"() {
        given: "prepareQuery sets the arguments directly and DynamicFinder.populateArgumentsForCriteria sets them again; this mirrors the equally redundant, but harmless, behaviour of the non-reactive grails.gorm.DetachedCriteria sibling"
        DetachedCriteria<Volume> criteria = newCriteria()
        Map args = [cache: true]

        when:
        criteria.findAll(args)

        then:
        2 * ((QueryArgumentsAware) query).setArguments(args)
        1 * query.findAll(args) >> Observable.empty()
    }

    void "clone() returns an independent DetachedCriteria with a copy of this criteria's state"() {
        given:
        DetachedCriteria<Volume> original = newCriteria().eq('title', 'A')

        when:
        DetachedCriteria<Volume> copy = original.clone()
        copy.eq('title', 'B')

        then:
        copy instanceof DetachedCriteria
        !copy.is(original)
        original.criteria.size() == 1
        copy.criteria.size() == 2
    }

    void "propertyMissing forwards to the base implementation, surfacing its exception for a non domain class"() {
        given:
        DetachedCriteria<Volume> criteria = newCriteria()

        when:
        criteria.propertyMissing('bogus')

        then:
        thrown(IllegalArgumentException)
    }

    @Unroll
    void "#method('title', Closure) builds the subquery instead of throwing ClassCastException"() {
        given: "buildQueryableCriteria used to cast a plain DetachedCriteria straight to QueryableCriteria, a cast that always failed because grails.gorm.rx.DetachedCriteria does not implement QueryableCriteria; it now wraps the built subquery in an adapter"
        DetachedCriteria<Volume> criteria = newCriteria()
        Closure subqueryClosure = { eq('title', 'A') }

        when:
        criteria."$method"('title', subqueryClosure)

        then:
        noExceptionThrown()
        criteria.criteria.size() == 1

        where:
        method << ['in', 'inList', 'notIn', 'eqAll', 'gtAll', 'ltAll', 'geAll', 'leAll', 'gtSome', 'geSome', 'ltSome', 'leSome']
    }

    void "in(String, Closure) adds a Query.In criterion whose subquery reflects the closure's constraints"() {
        given:
        DetachedCriteria<Volume> criteria = newCriteria()

        when:
        criteria.in('title', { eq('title', 'A') })

        then:
        criteria.criteria.size() == 1
        Query.In inCriterion = (Query.In) criteria.criteria[0]
        inCriterion.property == 'title'
        QueryableCriteria subquery = inCriterion.subquery
        subquery.criteria.size() == 1
        subquery.criteria[0] instanceof Query.Equals
        ((Query.Equals) subquery.criteria[0]).property == 'title'
        ((Query.Equals) subquery.criteria[0]).value == 'A'
        subquery.alias == null
    }

    void "notIn(String, Closure) adds a Query.NotIn criterion whose subquery reflects the closure's constraints"() {
        given:
        DetachedCriteria<Volume> criteria = newCriteria()

        when:
        criteria.notIn('title', { eq('title', 'B') })

        then:
        criteria.criteria.size() == 1
        Query.NotIn notInCriterion = (Query.NotIn) criteria.criteria[0]
        notInCriterion.property == 'title'
        QueryableCriteria subquery = notInCriterion.subquery
        subquery.criteria.size() == 1
        ((Query.Equals) subquery.criteria[0]).value == 'B'
    }

    void "eqAll(String, Closure) adds a Query.EqualsAll criterion whose value reflects the closure's constraints"() {
        given:
        DetachedCriteria<Volume> criteria = newCriteria()

        when:
        criteria.eqAll('price', { eq('price', 10) })

        then:
        criteria.criteria.size() == 1
        Query.EqualsAll equalsAll = (Query.EqualsAll) criteria.criteria[0]
        equalsAll.property == 'price'
        QueryableCriteria subquery = equalsAll.value
        subquery.criteria.size() == 1
        ((Query.Equals) subquery.criteria[0]).value == 10
    }

    void "the adapter built for a closure based subquery throws UnsupportedOperationException from find() and list() rather than executing eagerly"() {
        given: "query engines translate a subquery into a nested query by reading getCriteria()/getPersistentEntity()/getProjections()/getAlias() and never call find()/list() on it directly (see JpaQueryBuilder, PredicateGenerator); find()/list() would also require blocking a reactive Observable pipeline, so they are intentionally unsupported"
        DetachedCriteria<Volume> criteria = newCriteria()
        criteria.in('title', { eq('title', 'A') })
        QueryableCriteria subquery = ((Query.In) criteria.criteria[0]).subquery

        when:
        subquery.find()

        then:
        thrown(UnsupportedOperationException)

        when:
        subquery.list()

        then:
        thrown(UnsupportedOperationException)
    }

    void "the base class DSL overrides forward to the base implementation and preserve the DetachedCriteria type"() {
        given:
        DetachedCriteria<Volume> criteria = newCriteria()
        QueryableCriteria subquery = Stub(QueryableCriteria)

        expect:
        criteria.where { }.class == DetachedCriteria
        criteria.whereLazy { }.class == DetachedCriteria
        criteria.build { }.class == DetachedCriteria
        criteria.buildLazy { }.class == DetachedCriteria
        criteria.max(5).class == DetachedCriteria
        criteria.offset(5).class == DetachedCriteria
        criteria.sort('title').class == DetachedCriteria
        criteria.sort('title', 'desc').class == DetachedCriteria
        criteria.property('title').class == DetachedCriteria
        criteria.id().class == DetachedCriteria
        criteria.avg('price').class == DetachedCriteria
        criteria.sum('price').class == DetachedCriteria
        criteria.min('price').class == DetachedCriteria
        criteria.max('price').class == DetachedCriteria
        criteria.distinct('title').class == DetachedCriteria
        criteria.join('author').class == DetachedCriteria
        criteria.select('title').class == DetachedCriteria
        criteria.projections { count() }.class == DetachedCriteria
        criteria.and { eq('title', 'A') }.class == DetachedCriteria
        criteria.or { eq('title', 'A') }.class == DetachedCriteria
        criteria.not { eq('title', 'A') }.class == DetachedCriteria
        criteria.in('title', ['A', 'B']).class == DetachedCriteria
        criteria.in('title', subquery).class == DetachedCriteria
        criteria.inList('title', subquery).class == DetachedCriteria
        criteria.in('title', ['A', 'B'] as Object[]).class == DetachedCriteria
        criteria.notIn('title', subquery).class == DetachedCriteria
        criteria.order('title').class == DetachedCriteria
        criteria.order(new Query.Order('title')).class == DetachedCriteria
        criteria.order('title', 'desc').class == DetachedCriteria
        criteria.inList('title', ['A', 'B']).class == DetachedCriteria
        criteria.inList('title', ['A', 'B'] as Object[]).class == DetachedCriteria
        criteria.sizeEq('authors', 1).class == DetachedCriteria
        criteria.sizeGt('authors', 1).class == DetachedCriteria
        criteria.sizeGe('authors', 1).class == DetachedCriteria
        criteria.sizeLe('authors', 1).class == DetachedCriteria
        criteria.sizeLt('authors', 1).class == DetachedCriteria
        criteria.sizeNe('authors', 1).class == DetachedCriteria
        criteria.eqProperty('title', 'subtitle').class == DetachedCriteria
        criteria.neProperty('title', 'subtitle').class == DetachedCriteria
        criteria.allEq([title: 'A']).class == DetachedCriteria
        criteria.gtProperty('price', 'cost').class == DetachedCriteria
        criteria.geProperty('price', 'cost').class == DetachedCriteria
        criteria.ltProperty('price', 'cost').class == DetachedCriteria
        criteria.leProperty('price', 'cost').class == DetachedCriteria
        criteria.idEquals('1').class == DetachedCriteria
        criteria.exists(subquery).class == DetachedCriteria
        criteria.notExists(subquery).class == DetachedCriteria
        criteria.isEmpty('authors').class == DetachedCriteria
        criteria.isNotEmpty('authors').class == DetachedCriteria
        criteria.isNull('title').class == DetachedCriteria
        criteria.isNotNull('title').class == DetachedCriteria
        criteria.eq('title', 'A').class == DetachedCriteria
        criteria.idEq('1').class == DetachedCriteria
        criteria.ne('title', 'A').class == DetachedCriteria
        criteria.between('price', 1, 10).class == DetachedCriteria
        criteria.gte('price', 1).class == DetachedCriteria
        criteria.ge('price', 1).class == DetachedCriteria
        criteria.gt('price', 1).class == DetachedCriteria
        criteria.lte('price', 1).class == DetachedCriteria
        criteria.le('price', 1).class == DetachedCriteria
        criteria.lt('price', 1).class == DetachedCriteria
        criteria.like('title', 'A%').class == DetachedCriteria
        criteria.ilike('title', 'a%').class == DetachedCriteria
        criteria.rlike('title', '%a').class == DetachedCriteria
        criteria.eqAll('title', subquery).class == DetachedCriteria
        criteria.gtAll('price', subquery).class == DetachedCriteria
        criteria.gtSome('price', subquery).class == DetachedCriteria
        criteria.geSome('price', subquery).class == DetachedCriteria
        criteria.ltSome('price', subquery).class == DetachedCriteria
        criteria.leSome('price', subquery).class == DetachedCriteria
        criteria.ltAll('price', subquery).class == DetachedCriteria
        criteria.geAll('price', subquery).class == DetachedCriteria
        criteria.leAll('price', subquery).class == DetachedCriteria
    }
}

class Volume {
    Serializable id
    String title
    BigDecimal price
}

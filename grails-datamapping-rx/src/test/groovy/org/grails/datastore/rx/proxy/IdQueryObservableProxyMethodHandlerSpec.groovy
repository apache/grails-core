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
package org.grails.datastore.rx.proxy

import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.query.Query
import org.grails.datastore.rx.exceptions.BlockingOperationException
import org.grails.datastore.rx.internal.RxDatastoreClientImplementor
import org.grails.datastore.rx.query.QueryState
import org.grails.datastore.rx.query.RxQuery
import rx.Observable
import spock.lang.Specification

import java.lang.reflect.Method

class IdQueryObservableProxyMethodHandlerSpec extends Specification {

    private Query newQuery(Observable idResult) {
        PersistentEntity entity = Stub(PersistentEntity) {
            getJavaClass() >> Widget
        }
        Query query = Mock(Query, additionalInterfaces: [RxQuery])
        query.getEntity() >> entity
        query.projections() >> new Query.ProjectionList()
        query.singleResult() >> idResult
        query
    }

    private static Method methodNamed(String name, Class... paramTypes) {
        Signatures.getMethod(name, paramTypes)
    }

    void "toObservable() loads the entity from the client when the query yields an id not yet in the query state"() {
        given:
        Widget loaded = new Widget(name: 'loaded')
        RxDatastoreClientImplementor client = Mock(RxDatastoreClientImplementor)
        QueryState queryState = new QueryState()
        IdQueryObservableProxyMethodHandler handler = new IdQueryObservableProxyMethodHandler(
                Widget, newQuery(Observable.just(5L)), queryState, client)

        when:
        Observable result = handler.invoke(new Object(), methodNamed('toObservable'), null, [] as Object[]) as Observable
        Object resolved = result.toBlocking().first()

        then:
        1 * client.get(Widget, 5L, queryState) >> Observable.just(loaded)
        resolved.is(loaded)
        handler.invoke(new Object(), methodNamed('getProxyKey'), null, [] as Object[]) == 5L
    }

    void "toObservable() reuses an entity already present in the query state instead of asking the client"() {
        given:
        Widget cached = new Widget(name: 'cached')
        RxDatastoreClientImplementor client = Mock(RxDatastoreClientImplementor)
        QueryState queryState = new QueryState()
        queryState.addLoadedEntity(Widget, 9L, cached)
        IdQueryObservableProxyMethodHandler handler = new IdQueryObservableProxyMethodHandler(
                Widget, newQuery(Observable.just(9L)), queryState, client)

        when:
        Observable result = handler.invoke(new Object(), methodNamed('toObservable'), null, [] as Object[]) as Observable
        Object resolved = result.toBlocking().first()

        then:
        0 * client.get(_, _, _)
        resolved.is(cached)
    }

    void "toObservable() passes through the projected value directly when the query already yields an instance of the target type"() {
        given:
        Widget alreadyResolved = new Widget(name: 'already')
        RxDatastoreClientImplementor client = Mock(RxDatastoreClientImplementor)
        QueryState queryState = new QueryState()
        IdQueryObservableProxyMethodHandler handler = new IdQueryObservableProxyMethodHandler(
                Widget, newQuery(Observable.just(alreadyResolved)), queryState, client)

        when:
        Observable result = handler.invoke(new Object(), methodNamed('toObservable'), null, [] as Object[]) as Observable
        Object resolved = result.toBlocking().first()

        then:
        0 * client.get(_, _, _)
        resolved.is(alreadyResolved)
    }

    void "toObservable() completes with no result when the query finds no matching id"() {
        given:
        RxDatastoreClientImplementor client = Mock(RxDatastoreClientImplementor)
        QueryState queryState = new QueryState()
        IdQueryObservableProxyMethodHandler handler = new IdQueryObservableProxyMethodHandler(
                Widget, newQuery(Observable.empty()), queryState, client)

        when:
        Observable result = handler.invoke(new Object(), methodNamed('toObservable'), null, [] as Object[]) as Observable
        List received = result.toList().toBlocking().first()

        then:
        0 * client.get(_, _, _)
        received.isEmpty()
        handler.invoke(new Object(), methodNamed('isInitialized'), null, [] as Object[]) == false
    }

    void "getProxyKey() resolves the delegate via a blocking read when blocking operations are allowed"() {
        given:
        Widget loaded = new Widget(name: 'blocking')
        RxDatastoreClientImplementor client = Stub(RxDatastoreClientImplementor) {
            isAllowBlockingOperations() >> true
            get(Widget, 3L, _ as QueryState) >> Observable.just(loaded)
        }
        QueryState queryState = new QueryState()
        IdQueryObservableProxyMethodHandler handler = new IdQueryObservableProxyMethodHandler(
                Widget, newQuery(Observable.just(3L)), queryState, client)

        when:
        Object key = handler.invoke(new Object(), methodNamed('getProxyKey'), null, [] as Object[])

        then:
        key == 3L
        handler.invoke(new Object(), methodNamed('getTarget'), null, [] as Object[]).is(loaded)
    }

    void "getProxyKey() throws when blocking operations are not allowed by the client"() {
        given:
        RxDatastoreClientImplementor client = Stub(RxDatastoreClientImplementor) {
            isAllowBlockingOperations() >> false
        }
        QueryState queryState = new QueryState()
        IdQueryObservableProxyMethodHandler handler = new IdQueryObservableProxyMethodHandler(
                Widget, newQuery(Observable.just(3L)), queryState, client)

        when:
        handler.invoke(new Object(), methodNamed('getProxyKey'), null, [] as Object[])

        then:
        thrown(BlockingOperationException)
    }
}

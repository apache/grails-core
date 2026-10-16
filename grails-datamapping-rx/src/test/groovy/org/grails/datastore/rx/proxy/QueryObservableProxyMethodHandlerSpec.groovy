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

class QueryObservableProxyMethodHandlerSpec extends Specification {

    private Query newQuery(Observable singleResult) {
        PersistentEntity entity = Stub(PersistentEntity) {
            getJavaClass() >> Widget
        }
        Query query = Mock(Query, additionalInterfaces: [RxQuery])
        query.getEntity() >> entity
        query.projections() >> new Query.ProjectionList()
        query.singleResult() >> singleResult
        query
    }

    private static Method methodNamed(String name, Class... paramTypes) {
        Signatures.getMethod(name, paramTypes)
    }

    void "toObservable() resolves the entity yielded directly by the query's single result and caches it as the target"() {
        given:
        Widget loaded = new Widget(name: 'loaded')
        RxDatastoreClientImplementor client = Stub(RxDatastoreClientImplementor)
        QueryState queryState = new QueryState()
        QueryObservableProxyMethodHandler handler = new QueryObservableProxyMethodHandler(
                Widget, newQuery(Observable.just(loaded)), queryState, client)

        when:
        Observable result = handler.invoke(new Object(), methodNamed('toObservable'), null, [] as Object[]) as Observable
        Object resolved = result.toBlocking().first()

        then:
        resolved.is(loaded)
        handler.invoke(new Object(), methodNamed('isInitialized'), null, [] as Object[]) == true
        handler.invoke(new Object(), methodNamed('getTarget'), null, [] as Object[]).is(loaded)
    }

    void "toObservable() completes with no result when the query matches nothing"() {
        given:
        RxDatastoreClientImplementor client = Stub(RxDatastoreClientImplementor)
        QueryState queryState = new QueryState()
        QueryObservableProxyMethodHandler handler = new QueryObservableProxyMethodHandler(
                Widget, newQuery(Observable.empty()), queryState, client)

        when:
        Observable result = handler.invoke(new Object(), methodNamed('toObservable'), null, [] as Object[]) as Observable
        List received = result.toList().toBlocking().first()

        then:
        received.isEmpty()
        handler.invoke(new Object(), methodNamed('isInitialized'), null, [] as Object[]) == false
    }

    void "getTarget() performs a blocking read of the cached observable when blocking operations are allowed"() {
        given:
        Widget loaded = new Widget(name: 'blocking')
        RxDatastoreClientImplementor client = Stub(RxDatastoreClientImplementor) {
            isAllowBlockingOperations() >> true
        }
        QueryState queryState = new QueryState()
        QueryObservableProxyMethodHandler handler = new QueryObservableProxyMethodHandler(
                Widget, newQuery(Observable.just(loaded)), queryState, client)

        when:
        Object target = handler.invoke(new Object(), methodNamed('getTarget'), null, [] as Object[])

        then:
        target.is(loaded)
    }

    void "getTarget() throws when blocking operations are not allowed"() {
        given:
        RxDatastoreClientImplementor client = Stub(RxDatastoreClientImplementor) {
            isAllowBlockingOperations() >> false
        }
        QueryState queryState = new QueryState()
        QueryObservableProxyMethodHandler handler = new QueryObservableProxyMethodHandler(
                Widget, newQuery(Observable.empty()), queryState, client)

        when:
        handler.invoke(new Object(), methodNamed('getTarget'), null, [] as Object[])

        then:
        thrown(BlockingOperationException)
    }

    void "getProxyKey() always returns null since a query-resolved proxy has no identifier of its own to track"() {
        given:
        Widget loaded = new Widget(name: 'loaded')
        RxDatastoreClientImplementor client = Stub(RxDatastoreClientImplementor) {
            isAllowBlockingOperations() >> true
        }
        QueryState queryState = new QueryState()
        QueryObservableProxyMethodHandler handler = new QueryObservableProxyMethodHandler(
                Widget, newQuery(Observable.just(loaded)), queryState, client)

        expect:
        handler.invoke(new Object(), methodNamed('getProxyKey'), null, [] as Object[]) == null
    }
}

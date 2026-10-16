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

import org.grails.datastore.rx.exceptions.BlockingOperationException
import org.grails.datastore.rx.internal.RxDatastoreClientImplementor
import org.grails.datastore.rx.query.QueryState
import rx.Observable
import spock.lang.Specification

import java.lang.reflect.Method

class IdentifierObservableProxyMethodHandlerSpec extends Specification {

    private static Method methodNamed(String name, Class... paramTypes) {
        Signatures.getMethod(name, paramTypes)
    }

    void "toObservable() resolves the entity from the client using the given identifier and caches it as the target"() {
        given:
        Widget loaded = new Widget(name: 'loaded')
        RxDatastoreClientImplementor client = Stub(RxDatastoreClientImplementor) {
            get(Widget, 7L, _ as QueryState) >> Observable.just(loaded)
        }
        QueryState queryState = new QueryState()
        IdentifierObservableProxyMethodHandler handler = new IdentifierObservableProxyMethodHandler(
                Widget, Widget, 7L, client, queryState)

        when:
        Observable result = handler.invoke(new Object(), methodNamed('toObservable'), null, [] as Object[]) as Observable
        Object resolved = result.toBlocking().first()

        then:
        resolved.is(loaded)
        handler.invoke(new Object(), methodNamed('isInitialized'), null, [] as Object[]) == true
        handler.invoke(new Object(), methodNamed('getTarget'), null, [] as Object[]).is(loaded)
    }

    void "getTarget() prefers the entity already present in the query state over the client-resolved value"() {
        given:
        Widget cached = new Widget(name: 'cached')
        Widget fromClient = new Widget(name: 'fromClient')
        RxDatastoreClientImplementor client = Stub(RxDatastoreClientImplementor) {
            get(Widget, 11L, _ as QueryState) >> Observable.just(fromClient)
        }
        QueryState queryState = new QueryState()
        queryState.addLoadedEntity(Widget, 11L, cached)
        IdentifierObservableProxyMethodHandler handler = new IdentifierObservableProxyMethodHandler(
                Widget, Widget, 11L, client, queryState)

        when:
        Object target = handler.invoke(new Object(), methodNamed('getTarget'), null, [] as Object[])

        then:
        target.is(cached)
    }

    void "getTarget() performs a blocking read via the client when isAllowBlockingOperations is true and nothing is cached"() {
        given:
        Widget loaded = new Widget(name: 'blocking')
        RxDatastoreClientImplementor client = Stub(RxDatastoreClientImplementor) {
            isAllowBlockingOperations() >> true
            get(Widget, 13L, _ as QueryState) >> Observable.just(loaded)
        }
        QueryState queryState = new QueryState()
        IdentifierObservableProxyMethodHandler handler = new IdentifierObservableProxyMethodHandler(
                Widget, Widget, 13L, client, queryState)

        when:
        Object target = handler.invoke(new Object(), methodNamed('getTarget'), null, [] as Object[])

        then:
        target.is(loaded)
    }

    void "getTarget() throws when blocking operations are not allowed and nothing is cached"() {
        given:
        RxDatastoreClientImplementor client = Stub(RxDatastoreClientImplementor) {
            isAllowBlockingOperations() >> false
            get(Widget, 17L, _ as QueryState) >> Observable.empty()
        }
        QueryState queryState = new QueryState()
        IdentifierObservableProxyMethodHandler handler = new IdentifierObservableProxyMethodHandler(
                Widget, Widget, 17L, client, queryState)

        when:
        handler.invoke(new Object(), methodNamed('getTarget'), null, [] as Object[])

        then:
        thrown(BlockingOperationException)
    }

    void "getProxyKey() and getId() both report the identifier supplied at construction, whether or not the target has been resolved"() {
        given:
        RxDatastoreClientImplementor client = Stub(RxDatastoreClientImplementor) {
            get(_, _, _) >> Observable.empty()
        }
        QueryState queryState = new QueryState()
        IdentifierObservableProxyMethodHandler handler = new IdentifierObservableProxyMethodHandler(
                Widget, Widget, 21L, client, queryState)

        expect:
        handler.invoke(new Object(), methodNamed('getProxyKey'), null, [] as Object[]) == 21L
        handler.invoke(new Object(), methodNamed('getId'), null, [] as Object[]) == 21L
    }
}

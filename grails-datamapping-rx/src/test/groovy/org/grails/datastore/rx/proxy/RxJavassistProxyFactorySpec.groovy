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

import grails.gorm.rx.proxy.ObservableProxy
import javassist.util.proxy.ProxyObject
import org.grails.datastore.mapping.core.Session
import org.grails.datastore.mapping.engine.AssociationQueryExecutor
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.query.Query
import org.grails.datastore.rx.internal.RxDatastoreClientImplementor
import org.grails.datastore.rx.query.QueryState
import org.grails.datastore.rx.query.RxQuery
import rx.Observable
import spock.lang.Specification

class RxJavassistProxyFactorySpec extends Specification {

    RxJavassistProxyFactory factory = new RxJavassistProxyFactory()

    void "createProxy(client, queryState, type, key) builds a proxy implementing the target type and ObservableProxy, wired with an IdentifierObservableProxyMethodHandler"() {
        given:
        RxDatastoreClientImplementor client = Stub(RxDatastoreClientImplementor)
        client.get(Widget, 42L, _ as QueryState) >> Observable.empty()
        QueryState queryState = new QueryState()

        when:
        Widget proxy = factory.createProxy(client, queryState, Widget, 42L)

        then:
        proxy instanceof ObservableProxy
        ((ObservableProxy) proxy).getProxyKey() == 42L
        !((ObservableProxy) proxy).isInitialized()
        ((ProxyObject) proxy).getHandler() instanceof IdentifierObservableProxyMethodHandler
    }

    void "createProxy(client, queryState, type, key) reuses the same generated proxy class for repeated calls with the same type"() {
        given:
        RxDatastoreClientImplementor client = Stub(RxDatastoreClientImplementor)
        client.get(Widget, _ as Serializable, _ as QueryState) >> Observable.empty()
        QueryState queryState = new QueryState()

        when:
        Widget first = factory.createProxy(client, queryState, Widget, 1L)
        Widget second = factory.createProxy(client, queryState, Widget, 2L)

        then: 'getClass() is final on Object so Javassist cannot intercept it, avoiding proxy initialization'
        first.getClass().is(second.getClass())
    }

    void "createProxy(client, queryState, query) builds an ObservableProxy for the query's entity type, wired with an IdQueryObservableProxyMethodHandler"() {
        given:
        PersistentEntity entity = Stub(PersistentEntity) {
            getJavaClass() >> Widget
        }
        Query query = Mock(Query, additionalInterfaces: [RxQuery])
        query.getEntity() >> entity
        RxDatastoreClientImplementor client = Stub(RxDatastoreClientImplementor)
        QueryState queryState = new QueryState()

        when:
        ObservableProxy proxy = factory.createProxy(client, queryState, query)

        then:
        1 * query.projections() >> new Query.ProjectionList()
        1 * query.singleResult() >> Observable.empty()
        proxy instanceof Widget
        ((ProxyObject) proxy).getHandler() instanceof IdQueryObservableProxyMethodHandler
    }

    void "isProxy returns true only for ObservableProxy instances"() {
        expect:
        factory.isProxy(Stub(ObservableProxy))
        !factory.isProxy(new Widget())
    }

    void "isInitialized(Object) delegates to the proxy's own state, and is always true for non-proxies"() {
        given:
        ObservableProxy proxy = Stub(ObservableProxy) {
            isInitialized() >> initialized
        }

        expect:
        factory.isInitialized(proxy) == initialized
        factory.isInitialized(new Widget())

        where:
        initialized << [true, false]
    }

    void "isInitialized(Object, associationName) mirrors isInitialized(Object) regardless of the association name"() {
        given:
        ObservableProxy proxy = Stub(ObservableProxy) {
            isInitialized() >> false
        }

        expect:
        !factory.isInitialized(proxy, 'someAssociation')
        factory.isInitialized(new Widget(), 'someAssociation')
    }

    void "unwrap returns the proxy's target for a proxy, and the object itself otherwise"() {
        given:
        Widget target = new Widget(name: 'unwrapped')
        ObservableProxy proxy = Stub(ObservableProxy) {
            getTarget() >> target
        }
        Widget plain = new Widget(name: 'plain')

        expect:
        factory.unwrap(proxy).is(target)
        factory.unwrap(plain).is(plain)
    }

    void "getIdentifier returns the proxy key for a proxy, and null otherwise"() {
        given:
        ObservableProxy proxy = Stub(ObservableProxy) {
            getProxyKey() >> 99L
        }

        expect:
        factory.getIdentifier(proxy) == 99L
        factory.getIdentifier(new Widget()) == null
    }

    void "getProxiedClass returns the proxy's superclass for a real generated proxy, and the object's own class otherwise"() {
        given:
        RxDatastoreClientImplementor client = Stub(RxDatastoreClientImplementor)
        client.get(Widget, _ as Serializable, _ as QueryState) >> Observable.empty()
        Widget proxy = factory.createProxy(client, new QueryState(), Widget, 1L)

        expect:
        factory.getProxiedClass(proxy) == Widget
        factory.getProxiedClass(new Widget()) == Widget
    }

    void "initialize triggers initialization only when the object is a proxy"() {
        given:
        ObservableProxy proxy = Mock(ObservableProxy)

        when:
        factory.initialize(proxy)
        factory.initialize(new Widget())

        then:
        1 * proxy.initialize()
    }

    void "createProxy(Session, type, key) is unsupported in stateless RX mode"() {
        when:
        factory.createProxy(Stub(Session), Widget, 1L)

        then:
        thrown(UnsupportedOperationException)
    }

    void "createProxy(Session, executor, key) is unsupported in stateless RX mode"() {
        when:
        factory.createProxy(Stub(Session), Stub(AssociationQueryExecutor), 1L)

        then:
        thrown(UnsupportedOperationException)
    }
}

class Widget {
    String name
}

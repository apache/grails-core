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
package org.grails.orm.hibernate.proxy

import org.hibernate.collection.spi.PersistentCollection
import org.hibernate.proxy.HibernateProxy
import org.hibernate.proxy.LazyInitializer
import org.grails.datastore.mapping.engine.AssociationQueryExecutor
import org.grails.datastore.mapping.core.Session
import spock.lang.Specification

class SimpleHibernateProxyHandlerSpec extends Specification {

    void "test isInitialized respects PersistentCollections"() {
        given:
        def ph = new HibernateProxyHandler()

        when:
        def initialized = Mock(PersistentCollection) {
            1 * wasInitialized() >> true
        }
        def notInitialized = Mock(PersistentCollection) {
            1 * wasInitialized() >> false
        }

        then:
        ph.isInitialized(initialized)
        !ph.isInitialized(notInitialized)
    }

    void "test isInitialized respects HibernateProxy"() {
        given:
        def ph = new HibernateProxyHandler()

        when:
        def initialized = Mock(HibernateProxy) {
            1 * getHibernateLazyInitializer() >> Mock(LazyInitializer) {
                1 * isUninitialized() >> false
            }
        }
        def notInitialized = Mock(HibernateProxy) {
            1 * getHibernateLazyInitializer() >> Mock(LazyInitializer) {
                1 * isUninitialized() >> true
            }
        }

        then:
        ph.isInitialized(initialized)
        !ph.isInitialized(notInitialized)
    }

    void "test isInitialized returns false for null"() {
        given:
        def ph = new HibernateProxyHandler()

        expect:
        !ph.isInitialized(null)
    }

    void "test isInitialized returns true for plain object"() {
        given:
        def ph = new HibernateProxyHandler()

        expect:
        ph.isInitialized("a plain string")
    }

    void "test isInitialized(obj, associationName) returns false for unknown property"() {
        given:
        def ph = new HibernateProxyHandler()
        def obj = new Object()

        expect:
        !ph.isInitialized(obj, "nonExistentAssociation")
    }

    void "test isProxy returns false for plain object"() {
        given:
        def ph = new HibernateProxyHandler()

        expect:
        !ph.isProxy("a plain string")
    }

    void "test isProxy returns true for HibernateProxy"() {
        given:
        def ph = new HibernateProxyHandler()
        def proxy = Mock(HibernateProxy)

        expect:
        ph.isProxy(proxy)
    }

    void "test isProxy returns true for PersistentCollection"() {
        given:
        def ph = new HibernateProxyHandler()
        def coll = Mock(PersistentCollection)

        expect:
        ph.isProxy(coll)
    }

    void "test getProxiedClass returns the class of a plain object"() {
        given:
        def ph = new HibernateProxyHandler()
        def obj = "hello"

        expect:
        ph.getProxiedClass(obj) == String
    }

    void "test unwrap returns same object for plain (non-proxy) object"() {
        given:
        def ph = new HibernateProxyHandler()
        def obj = "plain object"

        expect:
        ph.unwrap(obj) == obj
    }

    void "test getIdentifier returns null for plain object"() {
        given:
        def ph = new HibernateProxyHandler()

        expect:
        ph.getIdentifier("plain") == null
    }

    void "test getIdentifier returns identifier for HibernateProxy"() {
        given:
        def ph = new HibernateProxyHandler()
        def proxy = Mock(HibernateProxy)
        def li = Mock(LazyInitializer)
        proxy.getHibernateLazyInitializer() >> li
        li.getIdentifier() >> 42L

        expect:
        ph.getIdentifier(proxy) == 42L
    }

    void "test initialize does not throw for plain object"() {
        given:
        def ph = new HibernateProxyHandler()

        when:
        ph.initialize("plain")

        then:
        noExceptionThrown()
    }

    void "test unwrapIfProxy delegates to unwrap"() {
        given:
        def ph = new HibernateProxyHandler()
        def obj = "plain"

        expect:
        ph.unwrapIfProxy(obj) == obj
    }

    void "test unwrapProxy delegates to unwrap"() {
        given:
        def ph = new HibernateProxyHandler()
        def obj = "plain"

        expect:
        ph.unwrapProxy(obj) == obj
    }

    void "test createProxy via AssociationQueryExecutor throws UnsupportedOperationException"() {
        given:
        def ph = new HibernateProxyHandler()
        def session = Mock(Session)
        def executor = Mock(AssociationQueryExecutor)

        when:
        ph.createProxy(session, executor, 1L)

        then:
        thrown(UnsupportedOperationException)
    }

    void "test getAssociationProxy returns null for unknown property"() {
        given:
        def ph = new HibernateProxyHandler()
        def obj = new Object()

        expect:
        ph.getAssociationProxy(obj, "nonExistentAssociation") == null
    }
}

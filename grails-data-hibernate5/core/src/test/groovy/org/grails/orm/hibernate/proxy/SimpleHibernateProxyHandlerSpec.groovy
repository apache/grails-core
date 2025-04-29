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
}

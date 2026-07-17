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

import groovy.transform.CompileStatic
import org.hibernate.proxy.HibernateProxy
import org.hibernate.proxy.LazyInitializer
import spock.lang.Specification

/**
 * Verifies that {@link HibernateProxyHandler} never dispatches methods through a
 * {@link HibernateProxy} while answering identifier and proxy-state questions. On a stock
 * (non-Groovy-aware) ByteBuddy proxy every dispatched method - including
 * {@code getMetaClass()} - is intercepted and initializes the proxy, which throws
 * {@code LazyInitializationException} when the proxy is detached. The handler must answer
 * from the {@link LazyInitializer} instead, which needs no session.
 *
 * <p>All interaction with the fake proxy happens in {@link StaticProbe} under
 * {@code @CompileStatic}: dynamic Groovy dispatch itself resolves methods through
 * {@code getMetaClass()}, so touching the proxy from dynamic spec code would trip the
 * trap before the handler is even involved.
 */
class HibernateProxyHandlerMetaClassProbeSpec extends Specification {

    void "getIdentifier reads the identifier from the LazyInitializer without dispatching through the proxy"() {
        given:
        LazyInitializer initializer = Stub(LazyInitializer) {
            getIdentifier() >> 42L
            isUninitialized() >> true
        }

        expect:
        StaticProbe.identifierOf(initializer) == 42L
    }

    void "isProxy and isInitialized do not dispatch through the proxy"() {
        given:
        LazyInitializer initializer = Stub(LazyInitializer) {
            isUninitialized() >> true
        }

        expect:
        StaticProbe.isProxy(initializer)
        !StaticProbe.isInitialized(initializer)
    }

    @CompileStatic
    static class StaticProbe {

        static Serializable identifierOf(LazyInitializer initializer) {
            new HibernateProxyHandler().getIdentifier(new MetaClassSensitiveProxy(initializer))
        }

        static boolean isProxy(LazyInitializer initializer) {
            new HibernateProxyHandler().isProxy(new MetaClassSensitiveProxy(initializer))
        }

        static boolean isInitialized(LazyInitializer initializer) {
            new HibernateProxyHandler().isInitialized(new MetaClassSensitiveProxy(initializer))
        }
    }

    /**
     * Simulates an uninitialized Hibernate proxy of a Groovy entity: any method dispatched
     * through the proxy instance - getMetaClass() included - is intercepted and would
     * initialize it, so this stand-in throws where a real detached proxy would throw
     * LazyInitializationException.
     */
    @CompileStatic
    static class MetaClassSensitiveProxy implements HibernateProxy, GroovyObject {

        private final LazyInitializer initializer

        MetaClassSensitiveProxy(LazyInitializer initializer) {
            this.initializer = initializer
        }

        @Override
        LazyInitializer getHibernateLazyInitializer() {
            initializer
        }

        @Override
        Object writeReplace() {
            this
        }

        @Override
        MetaClass getMetaClass() {
            throw new IllegalStateException('getMetaClass() was dispatched through the proxy - a real proxy would initialize here')
        }
    }
}

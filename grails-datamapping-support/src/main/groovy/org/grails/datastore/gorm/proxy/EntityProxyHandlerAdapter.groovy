/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  'License'); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  'AS IS' BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.grails.datastore.gorm.proxy

import groovy.transform.CompileStatic

import grails.core.support.proxy.EntityProxyHandler
import org.grails.datastore.mapping.core.Session
import org.grails.datastore.mapping.engine.AssociationQueryExecutor
import org.grails.datastore.mapping.proxy.ProxyFactory

/**
 * Adapts the proxy handler interface
 *
 * @author Graeme Rocher
 */
@CompileStatic
class EntityProxyHandlerAdapter implements ProxyFactory {

    final EntityProxyHandler proxyHandler

    EntityProxyHandlerAdapter(EntityProxyHandler proxyHandler) {
        this.proxyHandler = proxyHandler
    }

    @Override
    boolean isProxy(Object object) {
        return proxyHandler.isProxy(object)
    }

    @Override
    boolean isInitialized(Object object) {
        return proxyHandler.isInitialized(object)
    }

    @Override
    boolean isInitialized(Object object, String associationName) {
        return proxyHandler.isInitialized(object, associationName)
    }

    @Override
    Object unwrap(Object object) {
        return proxyHandler.unwrapIfProxy(object)
    }

    @Override
    Serializable getIdentifier(Object obj) {
        return (Serializable) proxyHandler.getProxyIdentifier(obj)
    }

    @Override
    Class<?> getProxiedClass(Object o) {
        return proxyHandler.getProxiedClass(o)
    }

    @Override
    void initialize(Object o) {
        proxyHandler.initialize(o)
    }

    @Override
    def <T> T createProxy(Session session, Class<T> type, Serializable key) {
        throw new UnsupportedOperationException('Method createProxy is not supported by this implementation')
    }

    @Override
    def <T, K extends Serializable> T createProxy(Session session, AssociationQueryExecutor<K, T> executor, K associationKey) {
        throw new UnsupportedOperationException('Method createProxy is not supported by this implementation')
    }

}

/*
 * Copyright 2014 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.datastore.gorm.proxy;

import grails.core.support.proxy.EntityProxyHandler;
import org.grails.datastore.mapping.core.Session;
import org.grails.datastore.mapping.engine.AssociationQueryExecutor;
import org.grails.datastore.mapping.proxy.ProxyFactory;

import java.io.Serializable;

/**
 * Adapts the proxy handler interface
 *
 * @author Graeme Rocher
 */
public class EntityProxyHandlerAdapter implements ProxyFactory {

    final EntityProxyHandler proxyHandler;

    public EntityProxyHandlerAdapter(EntityProxyHandler proxyHandler) {
        this.proxyHandler = proxyHandler;
    }


    @Override
    public boolean isProxy(Object object) {
        return proxyHandler.isProxy(object);
    }

    @Override
    public boolean isInitialized(Object object) {
        return proxyHandler.isInitialized(object);
    }

    @Override
    public boolean isInitialized(Object object, String associationName) {
        return proxyHandler.isInitialized(object,associationName);
    }

    @Override
    public Object unwrap(Object object) {
        return proxyHandler.unwrapIfProxy(object);
    }

    @Override
    public Serializable getIdentifier(Object obj) {
        return (Serializable) proxyHandler.getProxyIdentifier(obj);
    }

    @Override
    public Class<?> getProxiedClass(Object o) {
        return proxyHandler.getProxiedClass(o);
    }

    @Override
    public void initialize(Object o) {
        proxyHandler.initialize(o);
    }

    @Override
    public <T> T createProxy(Session session, Class<T> type, Serializable key) {
        throw new UnsupportedOperationException("Method createProxy is not supported by this implementation");
    }

    @Override
    public <T, K extends Serializable> T createProxy(Session session, AssociationQueryExecutor<K, T> executor, K associationKey) {
        throw new UnsupportedOperationException("Method createProxy is not supported by this implementation");
    }
}

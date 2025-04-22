/* Copyright (C) 2010 SpringSource
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.datastore.mapping.proxy;

import java.io.Serializable;

import org.grails.datastore.mapping.core.Session;
import org.grails.datastore.mapping.engine.AssociationQueryExecutor;

/**
 * The factory used to create proxies
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface ProxyFactory extends ProxyHandler {

    /**
     * Creates a proxy
     *
     * @param <T> The type of the proxy to create
     * @param session The session instance
     * @param type The type of the proxy to create
     * @param key The key to proxy
     * @return A proxy instance
     */
    <T> T createProxy(Session session, Class<T> type, Serializable key);

    /**
     * Creates a proxy
     *
     * @param <T> The type of the proxy to create
     * @param session The session instance
     * @param executor The query executor
     * @param associationKey The key to proxy
     * @return A proxy instance
     */
    <T,K extends Serializable> T createProxy(Session session, AssociationQueryExecutor<K, T> executor, K associationKey);

}

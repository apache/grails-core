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
package org.grails.datastore.rx.proxy;

import grails.gorm.rx.proxy.ObservableProxy;
import org.grails.datastore.mapping.proxy.ProxyHandler;
import org.grails.datastore.mapping.query.Query;
import org.grails.datastore.rx.RxDatastoreClient;
import org.grails.datastore.rx.query.QueryState;

import java.io.Serializable;

/**
 * @author Graeme Rocher
 * @since 6.0
 */
public interface ProxyFactory extends ProxyHandler {


    /**
     * Creates a proxy
     *
     * @param <T> The type of the proxy to create
     * @param client The datastore client
     * @param queryState Any prior query state
     * @param type The type of the proxy to create
     * @param key The key to proxy
     * @return A proxy instance
     */
    <T> T createProxy(RxDatastoreClient client, QueryState queryState, Class<T> type, Serializable key);

    /**
     * Creates a proxy
     *
     * @param client The datastore client
     * @param queryState Any prior query state
     * @param query The query to be executed to initialize the proxy
     *
     * @return A proxy instance
     */
    ObservableProxy createProxy(RxDatastoreClient client, QueryState queryState, Query query);
}

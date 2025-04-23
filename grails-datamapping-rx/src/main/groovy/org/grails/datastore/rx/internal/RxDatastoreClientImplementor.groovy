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

package org.grails.datastore.rx.internal

import grails.gorm.rx.proxy.ObservableProxy
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.query.Query
import org.grails.datastore.rx.RxDatastoreClient
import org.grails.datastore.rx.query.QueryState
import org.grails.gorm.rx.api.RxGormInstanceApi
import org.grails.gorm.rx.api.RxGormStaticApi
import org.grails.gorm.rx.api.RxGormValidationApi
import rx.Observable

/**
 * Internal non-client methods implemented by the implementor
 *
 * @author Graeme Rocher
 * @since 6.0
 */
interface RxDatastoreClientImplementor<T> extends RxDatastoreClient<T> {

    /**
     * @return Whether blocking operations are allowed by the implementation
     */
    boolean isAllowBlockingOperations()
    /**
     * Obtain an instance passing the query state
     *
     * @param type The type
     * @param id The id
     * @param queryState The query state
     * @return The observable
     */
    public <T1> Observable<T1> get(Class<T1> type, Serializable id, QueryState queryState)

    /**
     * Obtain an {@link grails.gorm.rx.proxy.ObservableProxy} for the given type and id
     *
     * @param type The type
     * @param id The id
     * @return An {@link grails.gorm.rx.proxy.ObservableProxy}
     */
    def <T1> ObservableProxy<T1> proxy(Class<T1> type, Serializable id, QueryState queryState)

    /**
     * Obtain an {@link ObservableProxy} that executes the given query to initialize
     *
     * @param query The query
     * @return An {@link ObservableProxy}
     */
    def ObservableProxy proxy(Query query, QueryState queryState)

    /**
     * Create a query, passing the entity state
     *
     * @param type The type
     * @param queryState The query state
     * @return The query
     */
    Query createQuery(Class type, QueryState queryState)

    /**
     * Create a query, passing the entity state
     *
     * @param type The type
     * @param queryState The query state
     * @return The query
     */
    Query createQuery(Class type, QueryState queryState, Map arguments)

    /**
     * Creates a static API (used for static methods)
     *
     * @param entity The entity
     * @return The static API
     */
    RxGormStaticApi createStaticApi(PersistentEntity entity)

    /**
     * Creates an instance API (used for instance methods)
     *
     * @param entity The entity
     * @return The instance API
     */
    RxGormInstanceApi createInstanceApi(PersistentEntity entity)

    /**
     * Creates a validation API (used for validation methods)
     *
     * @param entity The entity
     * @return The validation API
     */
    RxGormValidationApi createValidationApi(PersistentEntity entity)

    /**
     * Creates a static API (used for static methods)
     *
     * @param entity The entity
     * @return The static API
     */
    RxGormStaticApi createStaticApi(PersistentEntity entity, String connectionSourceName)

    /**
     * Creates an instance API (used for instance methods)
     *
     * @param entity The entity
     * @return The instance API
     */
    RxGormInstanceApi createInstanceApi(PersistentEntity entity, String connectionSourceName)

    /**
     * Creates a validation API (used for validation methods)
     *
     * @param entity The entity
     * @return The validation API
     */
    RxGormValidationApi createValidationApi(PersistentEntity entity, String connectionSourceName)

}
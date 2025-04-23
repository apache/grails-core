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

package org.grails.gorm.graphql.fetcher.manager

import graphql.schema.DataFetcher
import groovy.transform.CompileStatic
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.gorm.graphql.fetcher.BindingGormDataFetcher
import org.grails.gorm.graphql.fetcher.DeletingGormDataFetcher
import org.grails.gorm.graphql.fetcher.GraphQLDataFetcherType
import org.grails.gorm.graphql.fetcher.ReadingGormDataFetcher

/**
 * A default implementation of {@link GraphQLDataFetcherManager}.
 *
 * When retrieving fetcher instances, the exact class provided will be
 * searched for. If a parent class is registered and a subclass is searched,
 * the parent class fetcher will not be returned. If no fetchers are found,
 * the optional provided default fetchers will be searched. If no default
 * fetchers are provided, null will be returned.
 *
 * @author James Kleeh
 * @since 1.0.0
 */
@CompileStatic
class DefaultGraphQLDataFetcherManager implements GraphQLDataFetcherManager {

    protected final Map<Class, Map<GraphQLDataFetcherType, DataFetcher>> dataFetchers = [:]

    DefaultGraphQLDataFetcherManager() {
    }

    DefaultGraphQLDataFetcherManager(Map<GraphQLDataFetcherType, DataFetcher> defaultFetchers) {
        for (GraphQLDataFetcherType type: GraphQLDataFetcherType.values()) {
            verifyFetcher(defaultFetchers.get(type), type.requiredClass)
        }

        dataFetchers.put(Object, defaultFetchers)
    }

    protected void verifyFetcher(DataFetcher instance, Class requiredType) {
        if (instance != null && !(requiredType.isAssignableFrom(instance.class))) {
            throw new IllegalArgumentException("Data binder supplied ${instance.class.name} must be of returnType ${requiredType.name}")
        }
    }

    protected void registerFetcher(Class clazz, DataFetcher fetcher, GraphQLDataFetcherType type) {
        if (!dataFetchers.containsKey(clazz)) {
            dataFetchers.put(clazz, [:])
        }
        verifyFetcher(fetcher, type.requiredClass)
        dataFetchers.get(clazz).put(type, fetcher)
    }

    @Override
    void registerBindingDataFetcher(Class clazz, BindingGormDataFetcher fetcher) {
        for (GraphQLDataFetcherType type: GraphQLDataFetcherType.values()) {
            if (type.requiredClass == BindingGormDataFetcher) {
                if (fetcher.supports(type)) {
                    registerFetcher(clazz, fetcher, type)
                }
            }
        }
    }

    @Override
    void registerDeletingDataFetcher(Class clazz, DeletingGormDataFetcher fetcher) {
        registerFetcher(clazz, fetcher, GraphQLDataFetcherType.DELETE)
    }

    @Override
    void registerReadingDataFetcher(Class clazz, ReadingGormDataFetcher fetcher) {
        for (GraphQLDataFetcherType type: GraphQLDataFetcherType.values()) {
            if (type.requiredClass == ReadingGormDataFetcher) {
                if (fetcher.supports(type)) {
                    registerFetcher(clazz, fetcher, type)
                }
            }
        }
    }

    protected DataFetcher getCustomFetcher(Class clazz, GraphQLDataFetcherType type) {
        if (dataFetchers.containsKey(clazz)) {
            Map<GraphQLDataFetcherType, DataFetcher> fetchers = dataFetchers.get(clazz)
            if (fetchers.containsKey(type)) {
                return fetchers.get(type)
            }
        }
        null
    }

    protected Optional<DataFetcher> getCustomFetcher(PersistentEntity entity, GraphQLDataFetcherType type) {
        DataFetcher fetcher = getCustomFetcher(entity.javaClass, type) ?: getCustomFetcher(Object, type)
        if (fetcher != null) {
            Optional.of(fetcher)
        } else {
            Optional.empty()
        }
    }

    @Override
    Optional<BindingGormDataFetcher> getBindingFetcher(PersistentEntity entity, GraphQLDataFetcherType type) {
        if (type?.requiredClass != BindingGormDataFetcher) {
            throw new IllegalArgumentException("The type specified (${type}) is null or invalid")
        }
        getCustomFetcher(entity, type).map { DataFetcher d -> (BindingGormDataFetcher) d }
    }

    @Override
    Optional<DeletingGormDataFetcher> getDeletingFetcher(PersistentEntity entity) {
        getCustomFetcher(entity, GraphQLDataFetcherType.DELETE).map { DataFetcher d -> (DeletingGormDataFetcher) d }
    }

    @Override
    Optional<ReadingGormDataFetcher> getReadingFetcher(PersistentEntity entity, GraphQLDataFetcherType type) {
        if (type?.requiredClass != ReadingGormDataFetcher) {
            throw new IllegalArgumentException("The type specified (${type}) is null or invalid")
        }
        getCustomFetcher(entity, type).map { DataFetcher d -> (ReadingGormDataFetcher) d }
    }
}

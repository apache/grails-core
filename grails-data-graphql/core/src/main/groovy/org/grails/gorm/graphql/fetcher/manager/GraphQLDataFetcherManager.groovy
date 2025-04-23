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

import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.gorm.graphql.fetcher.BindingGormDataFetcher
import org.grails.gorm.graphql.fetcher.DeletingGormDataFetcher
import org.grails.gorm.graphql.fetcher.GraphQLDataFetcherType
import org.grails.gorm.graphql.fetcher.ReadingGormDataFetcher

/**
 * An interface to register and retrieve data fetcher instances
 *
 * @author James Kleeh
 * @since 1.0.0
 */
interface GraphQLDataFetcherManager {

    /**
     * Register a fetcher instance to be used for CREATE or UPDATE for the
     * provided class.
     *
     * @param clazz The class to be updated or deleted
     * @param fetcher The fetcher instance to be used
     */
    void registerBindingDataFetcher(Class clazz, BindingGormDataFetcher fetcher)

    /**
     * Register a fetcher instance to be used for DELETE for the
     * provided class.
     *
     * @param clazz The class to be deleted
     * @param fetcher The fetcher instance to be used
     */
    void registerDeletingDataFetcher(Class clazz, DeletingGormDataFetcher fetcher)

    /**
     * Register a fetcher instance to be used for GET or LIST for the
     * provided class.
     *
     * @param clazz The class to be retrieved
     * @param fetcher The fetcher instance to be used
     */
    void registerReadingDataFetcher(Class clazz, ReadingGormDataFetcher fetcher)

    /**
     * Returns a data fetcher instance to be used in CREATE or UPDATE
     *
     * @param entity The entity representing the domain used in the fetcher
     * @param type Which returnType of fetcher to return (CREATE or UPDATE)
     * @return An optional data fetcher
     */
    Optional<BindingGormDataFetcher> getBindingFetcher(PersistentEntity entity, GraphQLDataFetcherType type)

    /**
     * Returns a data fetcher instance to be used in DELETE
     *
     * @param entity The entity representing the domain used in the fetcher
     * @return An optional data fetcher
     */
    Optional<DeletingGormDataFetcher> getDeletingFetcher(PersistentEntity entity)

    /**
     * Returns a data fetcher instance to be used in GET or LIST
     *
     * @param entity The entity representing the domain used in the fetcher
     * @param type Which returnType of fetcher to return (GET or LIST)
     * @return An optional data fetcher
     */
    Optional<ReadingGormDataFetcher> getReadingFetcher(PersistentEntity entity, GraphQLDataFetcherType type)

}

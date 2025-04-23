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

package org.grails.gorm.graphql.interceptor

import graphql.schema.DataFetchingEnvironment
import org.grails.gorm.graphql.fetcher.GraphQLDataFetcherType

/**
 * Interface to describe a class that can intercept GraphQL data
 * fetchers and prevent the execution of their functionality.
 *
 * @author James Kleeh
 * @since 1.0.0
 */
interface GraphQLFetcherInterceptor {

    /**
     * This method will be executed before query operations provided by this library.
     *
     * @param environment The data fetching environment provided by GraphQL
     * @param type The data fetcher returnType. Either {@link GraphQLDataFetcherType#GET} or
     * {@link GraphQLDataFetcherType#LIST}
     * @return If FALSE, prevent execution of the interceptor
     */
    boolean onQuery(DataFetchingEnvironment environment, GraphQLDataFetcherType type)

    /**
     * This method will be executed before mutation operations provided by this library.
     *
     * @param environment The data fetching environment provided by GraphQL
     * @param type The data fetcher returnType. Either {@link GraphQLDataFetcherType#CREATE},
     * {@link GraphQLDataFetcherType#UPDATE}, or {@link GraphQLDataFetcherType#DELETE}
     * @return If FALSE, prevent execution of the interceptor
     */
    boolean onMutation(DataFetchingEnvironment environment, GraphQLDataFetcherType type)

    /**
     * This method will be executed before custom query operations provided by the user of
     * this library.
     *
     * @param name The name of the operation attempting to be executed
     * @param environment The data fetching environment provided by GraphQL
     * @return If FALSE, prevent execution of the interceptor
     */
    boolean onCustomQuery(String name, DataFetchingEnvironment environment)

    /**
     * This method will be executed before custom mutation operations provided by the user of
     * this library.
     *
     * @param name The name of the operation attempting to be executed
     * @param environment The data fetching environment provided by GraphQL
     * @return If FALSE, prevent execution of the interceptor
     */
    boolean onCustomMutation(String name, DataFetchingEnvironment environment)

}

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

package org.grails.gorm.graphql.interceptor.impl

import graphql.schema.DataFetchingEnvironment
import groovy.transform.CompileStatic
import org.grails.gorm.graphql.fetcher.GraphQLDataFetcherType
import org.grails.gorm.graphql.interceptor.GraphQLFetcherInterceptor

/**
 * Base class to extend from for custom data fetcher interceptors. Provides default
 * implementations of all methods.
 *
 * @author James Kleeh
 * @since 1.0.0
 */
@CompileStatic
class BaseGraphQLFetcherInterceptor implements GraphQLFetcherInterceptor {

    boolean onQuery(DataFetchingEnvironment environment, GraphQLDataFetcherType type) {
        true
    }

    boolean onMutation(DataFetchingEnvironment environment, GraphQLDataFetcherType type) {
        true
    }

    boolean onCustomQuery(String name, DataFetchingEnvironment environment) {
        true
    }

    boolean onCustomMutation(String name, DataFetchingEnvironment environment) {
        true
    }

}

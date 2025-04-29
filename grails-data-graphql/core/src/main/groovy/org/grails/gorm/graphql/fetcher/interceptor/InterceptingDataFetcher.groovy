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

package org.grails.gorm.graphql.fetcher.interceptor

import graphql.schema.DataFetcher
import graphql.schema.DataFetchingEnvironment
import groovy.transform.CompileStatic
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.gorm.graphql.GraphQLServiceManager
import org.grails.gorm.graphql.fetcher.DataFetcherNotFoundException
import org.grails.gorm.graphql.fetcher.GraphQLDataFetcherType
import org.grails.gorm.graphql.interceptor.GraphQLFetcherInterceptor
import org.grails.gorm.graphql.interceptor.manager.GraphQLInterceptorManager

/**
 * Data fetcher to wrap another data fetcher to apply
 * interceptor execution
 *
 * @author James Kleeh
 * @since 1.0.0
 */
@CompileStatic
class InterceptingDataFetcher<T> implements DataFetcher<T> {

    private Class clazz
    private GraphQLServiceManager serviceManager
    private DataFetcher wrappedFetcher
    private GraphQLDataFetcherType fetcherType
    private InterceptorInvoker interceptorInvoker

    protected List<GraphQLFetcherInterceptor> interceptors

    InterceptingDataFetcher(PersistentEntity entity,
                            GraphQLServiceManager serviceManager,
                            InterceptorInvoker interceptorInvoker,
                            GraphQLDataFetcherType fetcherType,
                            DataFetcher dataFetcher) {
        this(entity.javaClass, serviceManager, interceptorInvoker, fetcherType, dataFetcher)
    }

    InterceptingDataFetcher(Class clazz,
                            GraphQLServiceManager serviceManager,
                            InterceptorInvoker interceptorInvoker,
                            GraphQLDataFetcherType fetcherType,
                            DataFetcher dataFetcher) {
        this.clazz = clazz
        this.serviceManager = serviceManager
        this.wrappedFetcher = dataFetcher
        this.interceptorInvoker = interceptorInvoker
        this.fetcherType = fetcherType

        if (wrappedFetcher == null) {
            throw new DataFetcherNotFoundException(clazz, fetcherType)
        }
    }

    T get(DataFetchingEnvironment environment) {
        if (interceptors == null) {
            interceptors = serviceManager.getService(GraphQLInterceptorManager).getInterceptors(clazz) ?: (List<GraphQLFetcherInterceptor>) []
        }

        for (GraphQLFetcherInterceptor i: interceptors) {
            if (!interceptorInvoker.invoke(i, environment, fetcherType)) {
                return null
            }
        }

        wrappedFetcher.get(environment)
    }
}

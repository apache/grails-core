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

import graphql.schema.DataFetchingEnvironment
import org.grails.gorm.graphql.fetcher.GraphQLDataFetcherType
import org.grails.gorm.graphql.interceptor.GraphQLFetcherInterceptor

/**
 * A generic interface for custom operations to separate which event
 * will be called based on the returnType of the operation.
 *
 * @author James Kleeh
 * @since 1.0.0
 */
abstract class InterceptorInvoker {

    protected String getName(DataFetchingEnvironment environment) {
        environment.fields.empty ? 'UNKNOWN' : environment.fields[0].name
    }

    abstract boolean invoke(GraphQLFetcherInterceptor interceptor, DataFetchingEnvironment environment, GraphQLDataFetcherType type)
}

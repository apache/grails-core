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

package org.grails.gorm.graphql.interceptor.manager

import org.grails.gorm.graphql.interceptor.GraphQLFetcherInterceptor
import org.grails.gorm.graphql.interceptor.GraphQLSchemaInterceptor

/**
 * Describes a class that stores and retrieves fetcher interceptor
 * instances based on a class
 *
 * @author James Kleeh
 * @since 1.0.0
 */
interface GraphQLInterceptorManager {

    /**
     * Registers the interceptor
     *
     * @param clazz The class operations should be intercepted for
     * @param interceptor The interceptor to register
     */
    void registerInterceptor(Class clazz, GraphQLFetcherInterceptor interceptor)

    /**
     * Registers the interceptor
     *
     * @param interceptor The interceptor to register
     */
    void registerInterceptor(GraphQLSchemaInterceptor interceptor)

    /**
     * @param clazz The class to search for
     * @return Interceptors that support the class
     */
    List<GraphQLFetcherInterceptor> getInterceptors(Class clazz)

    /**
     * @param clazz The class to search for
     * @return Interceptors of the schema
     */
    List<GraphQLSchemaInterceptor> getInterceptors()
}

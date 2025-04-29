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

package org.grails.gorm.graphql.fetcher.impl

import graphql.schema.DataFetchingEnvironment
import groovy.transform.CompileStatic
import org.grails.datastore.gorm.GormEntity
import org.grails.gorm.graphql.entity.EntityFetchOptions

/**
 * Provides the data fetching environment for closures. Available
 * as the second parameter to dataFetcher closures provided by
 * custom properties. The main purpose of this class is to provide
 * the ability to get fetch arguments for custom properties with a
 * return type that is a domain class. This allows the same query
 * efficiency that is provided by the generic data fetchers by default.
 *
 * Usage:
 * <pre>
 * {@code
 * class Foo {
 *     static graphql = GraphQLMapping.build {
 *         add('bar', Bar) {
 *             dataFetcher { Foo foo, ClosureDataFetchingEnvironment env ->
 *                 //The fetchArguments will be populated based on what properties
 *                 //were requested from the 'bar'
 *                 Bar.where { }.list(env.fetchArguments)
 *             }
 *         }
 *     }
 * }
 * }
 * </pre>
 *
 * @author James Kleeh
 * @since 1.0.0
 */
@CompileStatic
class ClosureDataFetchingEnvironment {

    @Delegate
    DataFetchingEnvironment environment

    private EntityFetchOptions fetchOptions
    private Class domainType

    ClosureDataFetchingEnvironment(DataFetchingEnvironment environment, Class domainType) {
        this.environment = environment
        this.domainType = domainType
    }

    private void initializeFetchOptions(String propertyName = null) {
        if (fetchOptions == null && domainType != null && GormEntity.isAssignableFrom(domainType)) {
            fetchOptions = new EntityFetchOptions(domainType, propertyName)
        }
    }

    /**
     * For use with domain class return types only. All other
     * invocations will return null.
     *
     * @param projectedProperty The property name being projected on in the query
     * @return The fetch arguments to be used in your query.
     */
    Map getFetchArguments(String projectedProperty = null) {
        initializeFetchOptions(projectedProperty)
        fetchOptions?.getFetchArgument(environment)
    }

    /**
     * Which properties should be joined in the subsequent query.
     * Based upon which fields were requested to be returned by
     * the end user.
     *
     * @param projectedProperty The property name being projected on in the query
     * @return A set of strings representing properties to be joined
     */
    Set<String> getJoinProperties(String projectedProperty = null) {
        initializeFetchOptions(projectedProperty)
        fetchOptions?.getJoinProperties(environment)
    }
}

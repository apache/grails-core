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

import grails.gorm.DetachedCriteria
import graphql.schema.DataFetchingEnvironment
import groovy.transform.CompileStatic
import groovy.transform.InheritConstructors
import groovy.util.logging.Slf4j
import org.grails.gorm.graphql.fetcher.DefaultGormDataFetcher
import org.grails.gorm.graphql.fetcher.GraphQLDataFetcherType
import org.grails.gorm.graphql.fetcher.ReadingGormDataFetcher

/**
 * A class for retrieving a list of entities with GraphQL
 *
 * @param <T> The collection return type
 * @author James Kleeh
 * @since 1.0.0
 */
@InheritConstructors
@Slf4j
@CompileStatic
class EntityDataFetcher<T> extends DefaultGormDataFetcher<T> implements ReadingGormDataFetcher {

    //The new LinkedHasMap is to work around a static compilation bug
    static final Map<String, Class> ARGUMENTS = new LinkedHashMap<String, Class>([
       max: Integer,
       offset: Integer,
       sort: String,
       order: String,
       ignoreCase: Boolean
    ])

    static final String MAX = 'max'
    static final String OFFSET = 'offset'

    protected Map<String, Object> getArguments(DataFetchingEnvironment environment) {
        environment.arguments
    }

    @Override
    T get(DataFetchingEnvironment environment) {
        (T)withTransaction(true) {

            Map<String, Object> queryArgs = [:]

            for (Map.Entry<String, Object> entry: getArguments(environment)) {
                if (ARGUMENTS.containsKey(entry.key) && entry.value != null) {
                    queryArgs.put(entry.key, entry.value)
                }
            }

            boolean skipCollections = queryArgs.containsKey(MAX) || queryArgs.containsKey(OFFSET)

            queryArgs.putAll(getFetchArguments(environment, skipCollections))

            executeQuery(environment, queryArgs)
        }
    }

    protected DetachedCriteria buildCriteria(DataFetchingEnvironment environment) {
        new DetachedCriteria(entity.javaClass)
    }

    protected T executeQuery(DataFetchingEnvironment environment, Map queryArgs) {
        buildCriteria(environment).list(queryArgs)
    }

    @Override
    boolean supports(GraphQLDataFetcherType type) {
        type == GraphQLDataFetcherType.LIST
    }
}

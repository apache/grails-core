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
import org.grails.gorm.graphql.HibernateSpec
import org.grails.gorm.graphql.domain.general.toone.One
import org.grails.gorm.graphql.fetcher.GraphQLDataFetcherType
import org.grails.gorm.graphql.response.pagination.DefaultGraphQLPaginationResponseHandler

class PaginatedEntityDataFetcherSpec extends HibernateSpec {

    List<Class> getDomainClasses() { [One] }

    void setupSpec() {
        One.withNewTransaction {
            3.times { new One().save() }
        }
    }

    PaginatedEntityDataFetcher buildFetcher() {
        PaginatedEntityDataFetcher fetcher = new PaginatedEntityDataFetcher<>(mappingContext.getPersistentEntity(One.name))
        fetcher.responseHandler = new DefaultGraphQLPaginationResponseHandler()
        fetcher
    }

    void "test get applies default max and offset when none are supplied"() {
        given:
        DataFetchingEnvironment env = Mock(DataFetchingEnvironment) {
            1 * getArguments() >> [:]
            1 * getMergedField()
        }
        PaginatedEntityDataFetcher fetcher = buildFetcher()

        when:
        Map response = fetcher.get(env)

        then:
        response.results.size() == 3
        response.totalCount == 3L
    }

    void "test get honors an explicit max and offset"() {
        given:
        DataFetchingEnvironment env = Mock(DataFetchingEnvironment) {
            1 * getArguments() >> [max: 2, offset: 1]
            1 * getMergedField()
        }
        PaginatedEntityDataFetcher fetcher = buildFetcher()

        when:
        Map response = fetcher.get(env)

        then:
        response.results.size() == 2
        response.totalCount == 3L
    }

    void "test supports"() {
        when:
        PaginatedEntityDataFetcher fetcher = buildFetcher()

        then:
        !fetcher.supports(GraphQLDataFetcherType.CREATE)
        !fetcher.supports(GraphQLDataFetcherType.UPDATE)
        fetcher.supports(GraphQLDataFetcherType.LIST)
        !fetcher.supports(GraphQLDataFetcherType.GET)
        !fetcher.supports(GraphQLDataFetcherType.DELETE)
    }
}

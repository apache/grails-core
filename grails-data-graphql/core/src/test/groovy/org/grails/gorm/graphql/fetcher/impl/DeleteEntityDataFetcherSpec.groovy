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

import grails.gorm.transactions.Transactional
import graphql.schema.DataFetchingEnvironment
import org.grails.gorm.graphql.HibernateSpec
import org.grails.gorm.graphql.domain.general.toone.One
import org.grails.gorm.graphql.fetcher.GraphQLDataFetcherType
import org.grails.gorm.graphql.response.delete.GraphQLDeleteResponseHandler

class DeleteEntityDataFetcherSpec extends HibernateSpec {

    List<Class> getDomainClasses() { [One] }

    @Transactional
    One createInstance() {
        new One().save()
    }

    void "test get"() {
        given:
        One one = createInstance()
        GraphQLDeleteResponseHandler responseHandler = Mock(GraphQLDeleteResponseHandler)
        DataFetchingEnvironment env = Mock(DataFetchingEnvironment) {
            1 * getArgument('id') >> one.id
            1 * getMergedField()
        }
        DeleteEntityDataFetcher fetcher = new DeleteEntityDataFetcher<>(mappingContext.getPersistentEntity(One.name))
        fetcher.responseHandler = responseHandler

        when:
        fetcher.get(env)
        int count
        One.withNewSession {
            count = One.count
        }

        then:
        1 * responseHandler.createResponse(env, true, null)
        count == 0
    }

    void "test get invalid"() {
        given:
        One one = createInstance()
        GraphQLDeleteResponseHandler responseHandler = Mock(GraphQLDeleteResponseHandler)
        DataFetchingEnvironment env = Mock(DataFetchingEnvironment) {
            1 * getArgument('id') >> 95
            1 * getMergedField()
        }
        DeleteEntityDataFetcher fetcher = new DeleteEntityDataFetcher<>(mappingContext.getPersistentEntity(One.name))
        fetcher.responseHandler = responseHandler

        when:
        fetcher.get(env)

        then:
        1 * responseHandler.createResponse(env, false, _ as NullPointerException)
    }

    void "test supports"() {
        when:
        DeleteEntityDataFetcher fetcher = new DeleteEntityDataFetcher<>(mappingContext.getPersistentEntity(One.name))

        then:
        !fetcher.supports(GraphQLDataFetcherType.CREATE)
        !fetcher.supports(GraphQLDataFetcherType.UPDATE)
        !fetcher.supports(GraphQLDataFetcherType.LIST)
        !fetcher.supports(GraphQLDataFetcherType.GET)
        fetcher.supports(GraphQLDataFetcherType.DELETE)
    }
}

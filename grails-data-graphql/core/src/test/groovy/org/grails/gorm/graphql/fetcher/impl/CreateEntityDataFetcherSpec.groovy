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
import org.grails.gorm.graphql.binding.GraphQLDataBinder
import org.grails.gorm.graphql.domain.general.toone.One
import org.grails.gorm.graphql.fetcher.GraphQLDataFetcherType
import spock.lang.Subject

@Subject(CreateEntityDataFetcher)
class CreateEntityDataFetcherSpec extends HibernateSpec {

    List<Class> getDomainClasses() { [One] }

    void "test get"() {
        given:
        DataFetchingEnvironment env = Mock(DataFetchingEnvironment)
        GraphQLDataBinder binder = Mock(GraphQLDataBinder)
        CreateEntityDataFetcher fetcher = new CreateEntityDataFetcher<>(mappingContext.getPersistentEntity(One.name))
        fetcher.dataBinder = binder

        when:
        fetcher.get(env)
        int count
        One.withNewSession {
            count = One.count
        }

        then:
        1 * env.getArgument('one') >> ['bar': 1]
        1 * binder.bind(_ as One, ['bar': 1])
        count == 1
    }

    void "test supports"() {
        when:
        CreateEntityDataFetcher fetcher = new CreateEntityDataFetcher<>(mappingContext.getPersistentEntity(One.name))

        then:
        fetcher.supports(GraphQLDataFetcherType.CREATE)
        !fetcher.supports(GraphQLDataFetcherType.UPDATE)
        !fetcher.supports(GraphQLDataFetcherType.LIST)
        !fetcher.supports(GraphQLDataFetcherType.GET)
        !fetcher.supports(GraphQLDataFetcherType.DELETE)
    }

}

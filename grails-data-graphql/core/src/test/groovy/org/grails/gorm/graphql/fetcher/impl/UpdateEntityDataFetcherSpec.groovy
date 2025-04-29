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
import org.grails.gorm.graphql.binding.GraphQLDataBinder
import org.grails.gorm.graphql.binding.manager.DefaultGraphQLDataBinderManager
import org.grails.gorm.graphql.domain.general.custom.OtherDomain
import org.grails.gorm.graphql.fetcher.GraphQLDataFetcherType

class UpdateEntityDataFetcherSpec extends HibernateSpec {

    List<Class> getDomainClasses() { [OtherDomain] }

    @Transactional
    OtherDomain createInstance() {
        new OtherDomain(name: 'John').save()
    }

    void "test get"() {
        given:
        OtherDomain other = createInstance()
        DataFetchingEnvironment env = Mock(DataFetchingEnvironment)
        GraphQLDataBinder binder = new DefaultGraphQLDataBinderManager().getDataBinder(Object)
        UpdateEntityDataFetcher fetcher = new UpdateEntityDataFetcher<>(mappingContext.getPersistentEntity(OtherDomain.name))
        fetcher.dataBinder = binder

        when:
        fetcher.get(env)
        OtherDomain updated
        OtherDomain.withNewSession {
            updated = OtherDomain.get(other.id)
        }

        then:
        1 * env.getArgument('id') >> other.id
        1 * env.getArgument('otherDomain') >> ['name': 'Sally']
        updated.name == 'Sally'
    }

    void "test optimistic locking"() {
        given:
        OtherDomain other = createInstance()
        DataFetchingEnvironment env = Mock(DataFetchingEnvironment)
        GraphQLDataBinder binder = new DefaultGraphQLDataBinderManager().getDataBinder(Object)
        UpdateEntityDataFetcher fetcher = new UpdateEntityDataFetcher<>(mappingContext.getPersistentEntity(OtherDomain.name))
        fetcher.dataBinder = binder

        when:
        OtherDomain updated = fetcher.get(env)

        then:
        1 * env.getArgument('id') >> other.id
        1 * env.getArgument('otherDomain') >> ['name': 'Sally', 'version': -1L]
        updated.name == 'John'
        updated.errors.hasFieldErrors('version')
    }


    void "test optimistic locking with null version"() {
        given:
        OtherDomain other = createInstance()
        DataFetchingEnvironment env = Mock(DataFetchingEnvironment)
        GraphQLDataBinder binder = new DefaultGraphQLDataBinderManager().getDataBinder(Object)
        UpdateEntityDataFetcher fetcher = new UpdateEntityDataFetcher<>(mappingContext.getPersistentEntity(OtherDomain.name))
        fetcher.dataBinder = binder

        when:
        OtherDomain updated = fetcher.get(env)

        then:
        1 * env.getArgument('id') >> other.id
        1 * env.getArgument('otherDomain') >> ['name': 'Sally', 'version': null]
        updated.name == 'Sally'
        !updated.hasErrors()
    }

    void "test supports"() {
        when:
        UpdateEntityDataFetcher fetcher = new UpdateEntityDataFetcher<>(mappingContext.getPersistentEntity(OtherDomain.name))

        then:
        !fetcher.supports(GraphQLDataFetcherType.CREATE)
        fetcher.supports(GraphQLDataFetcherType.UPDATE)
        !fetcher.supports(GraphQLDataFetcherType.LIST)
        !fetcher.supports(GraphQLDataFetcherType.GET)
        !fetcher.supports(GraphQLDataFetcherType.DELETE)
    }

}

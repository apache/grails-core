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
import org.grails.gorm.graphql.domain.general.custom.OtherDomain
import org.grails.gorm.graphql.fetcher.GraphQLDataFetcherType
import org.grails.gorm.graphql.response.delete.DefaultGraphQLDeleteResponseHandler
import spock.lang.Shared

class SoftDeleteEntityDataFetcherSpec extends HibernateSpec {

    List<Class> getDomainClasses() { [OtherDomain] }

    @Shared Long id

    void setupSpec() {
        OtherDomain.withNewTransaction {
            id = new OtherDomain(name: "Sally").save(flush: true).id
        }
    }

    void "test get"() {
        given:
        DataFetchingEnvironment env = Mock(DataFetchingEnvironment) {
            1 * getArgument('id') >> id
            1 * getMergedField()
        }
        SoftDeleteEntityDataFetcher fetcher = new SoftDeleteEntityDataFetcher<>(mappingContext.getPersistentEntity(OtherDomain.name), 'name', 'John')
        fetcher.responseHandler = new DefaultGraphQLDeleteResponseHandler()

        when:
        Map result = fetcher.get(env)
        OtherDomain other
        OtherDomain.withNewSession {
            other = OtherDomain.get(id)
        }

        then:
        result.success
        other.name == 'John'
    }

    void "test supports"() {
        when:
        SoftDeleteEntityDataFetcher fetcher = new SoftDeleteEntityDataFetcher<>(mappingContext.getPersistentEntity(OtherDomain.name), 'name', 'John')

        then:
        !fetcher.supports(GraphQLDataFetcherType.CREATE)
        !fetcher.supports(GraphQLDataFetcherType.UPDATE)
        !fetcher.supports(GraphQLDataFetcherType.LIST)
        !fetcher.supports(GraphQLDataFetcherType.GET)
        fetcher.supports(GraphQLDataFetcherType.DELETE)
    }
}

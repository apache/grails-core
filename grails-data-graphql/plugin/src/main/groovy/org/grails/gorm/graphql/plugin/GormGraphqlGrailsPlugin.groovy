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

package org.grails.gorm.graphql.plugin

import grails.plugins.Plugin
import grails.web.mime.MimeType
import graphql.GraphQL
import graphql.schema.GraphQLCodeRegistry
import org.grails.gorm.graphql.GraphQLServiceManager
import org.grails.gorm.graphql.Schema
import org.grails.gorm.graphql.binding.manager.DefaultGraphQLDataBinderManager
import org.grails.gorm.graphql.entity.GraphQLEntityNamingConvention
import org.grails.gorm.graphql.entity.property.manager.DefaultGraphQLDomainPropertyManager
import org.grails.gorm.graphql.fetcher.manager.DefaultGraphQLDataFetcherManager
import org.grails.gorm.graphql.interceptor.manager.DefaultGraphQLInterceptorManager
import org.grails.gorm.graphql.plugin.binding.GrailsGraphQLDataBinder
import org.grails.gorm.graphql.response.delete.DefaultGraphQLDeleteResponseHandler
import org.grails.gorm.graphql.response.errors.DefaultGraphQLErrorsResponseHandler
import org.grails.gorm.graphql.response.pagination.DefaultGraphQLPaginationResponseHandler
import org.grails.gorm.graphql.types.DefaultGraphQLTypeManager

class GormGraphqlGrailsPlugin extends Plugin {

    def license = 'Apache 2.0 License'
    def organization = [name: 'Grails', url: 'https://grails.apache.org/']
    def issueManagement = [system: 'Github', url: 'https://github.com/apache/grails-core/issues']
    def scm = [url: 'https://github.com/apache/grails-core']
    def grailsVersion = '7.1.0 > *'
    def profiles = ['web']
    def title = 'GORM GraphQL'
    def description = 'Generates a GraphQL schema based on entities in GORM'
    def documentation = 'https://grails.apache.org/docs/latest/grails-data/graphql/manual/'

    public static final MimeType GRAPHQL_MIME = new MimeType('application/graphql')

    @Override
    Closure doWithSpring() {
        { ->
            grailsGraphQLConfiguration(GrailsGraphQLConfiguration)

            if (!config.getProperty('grails.gorm.graphql.enabled', Boolean, true)) {
                return
            }

            graphQLContextBuilder(DefaultGraphQLContextBuilder)

            graphQLDataBinder(GrailsGraphQLDataBinder)
            graphQLCodeRegistry(GraphQLCodeRegistry) { bean ->
                bean.factoryMethod = 'newCodeRegistry'
            }
            graphQLErrorsResponseHandler(DefaultGraphQLErrorsResponseHandler, ref('messageSource'), ref('graphQLCodeRegistry'))
            graphQLEntityNamingConvention(GraphQLEntityNamingConvention)
            graphQLDomainPropertyManager(DefaultGraphQLDomainPropertyManager)
            graphQLPaginationResponseHandler(DefaultGraphQLPaginationResponseHandler)

            graphQLTypeManager(DefaultGraphQLTypeManager,
                    ref('graphQLCodeRegistry'),
                    ref('graphQLEntityNamingConvention'),
                    ref('graphQLErrorsResponseHandler'),
                    ref('graphQLDomainPropertyManager'),
                    ref('graphQLPaginationResponseHandler'))
            graphQLDataBinderManager(DefaultGraphQLDataBinderManager, ref('graphQLDataBinder'))
            graphQLDeleteResponseHandler(DefaultGraphQLDeleteResponseHandler)
            graphQLDataFetcherManager(DefaultGraphQLDataFetcherManager)
            graphQLInterceptorManager(DefaultGraphQLInterceptorManager)
            graphQLServiceManager(GraphQLServiceManager)

            graphQLSchemaGenerator(Schema) {
                codeRegistry = ref('graphQLCodeRegistry')
                deleteResponseHandler = ref('graphQLDeleteResponseHandler')
                namingConvention = ref('graphQLEntityNamingConvention')
                typeManager = ref('graphQLTypeManager')
                dataBinderManager = ref('graphQLDataBinderManager')
                dataFetcherManager = ref('graphQLDataFetcherManager')
                interceptorManager = ref('graphQLInterceptorManager')
                paginationResponseHandler = ref('graphQLPaginationResponseHandler')
                serviceManager = ref('graphQLServiceManager')

                dateFormats = '#{grailsGraphQLConfiguration.getDateFormats()}'
                dateFormatLenient = '#{grailsGraphQLConfiguration.getDateFormatLenient()}'
                listArguments = '#{grailsGraphQLConfiguration.getListArguments()}'
            }

            graphQLSchema(graphQLSchemaGenerator: 'generate')
            graphQLBuilder(GraphQL.Builder, ref('graphQLSchema'))
            graphQL(GraphQL) { bean ->
                bean.factoryBean = 'graphQLBuilder'
                bean.factoryMethod = 'build'
            }
        }
    }
}

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

import org.springframework.beans.factory.support.BeanRegistryAdapter
import org.springframework.beans.factory.support.DefaultListableBeanFactory
import org.springframework.context.support.StaticMessageSource
import org.springframework.core.env.MapPropertySource
import org.springframework.core.env.StandardEnvironment

import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes

import graphql.ExecutionResult
import graphql.GraphQL
import graphql.Scalars
import graphql.schema.DataFetcher
import graphql.schema.FieldCoordinates
import graphql.schema.GraphQLCodeRegistry
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLSchema
import org.apache.grails.datamapping.core.querytail.QueryTailRecord
import org.apache.grails.datamapping.core.querytail.QueryTailRequestAttribution
import org.apache.grails.datastore.core.querytail.QueryTailSettings
import org.grails.gorm.graphql.types.DefaultGraphQLTypeManager

import spock.lang.Specification

class GormGraphqlGrailsPluginSpec extends Specification {

    void "beanRegistrar registers the graphql beans"() {
        given:
        def beanFactory = new DefaultListableBeanFactory()

        when:
        applyRegistrar(beanFactory, new StandardEnvironment())

        then:
        ['grailsGraphQLConfiguration', 'graphQLContextBuilder', 'graphQLDataBinder', 'graphQLCodeRegistry',
         'graphQLErrorsResponseHandler', 'graphQLEntityNamingConvention', 'graphQLDomainPropertyManager',
         'graphQLPaginationResponseHandler', 'graphQLTypeManager', 'graphQLDataBinderManager',
         'graphQLDeleteResponseHandler', 'graphQLDataFetcherManager', 'graphQLInterceptorManager',
         'graphQLServiceManager', 'graphQLSchemaGenerator', 'graphQLSchema', 'graphQLBuilder', 'graphQL']
                .every { String beanName -> beanFactory.containsBeanDefinition(beanName) }
    }

    void "only the configuration bean is registered when graphql is disabled"() {
        given:
        def beanFactory = new DefaultListableBeanFactory()
        def environment = new StandardEnvironment()
        environment.propertySources.addFirst(
                new MapPropertySource('test', ['grails.gorm.graphql.enabled': 'false']))

        when:
        applyRegistrar(beanFactory, environment)

        then:
        beanFactory.containsBeanDefinition('grailsGraphQLConfiguration')
        beanFactory.beanDefinitionCount == 1
    }

    void "the type manager is assembled from the registered collaborators"() {
        given:
        def beanFactory = new DefaultListableBeanFactory()
        applyRegistrar(beanFactory, new StandardEnvironment())
        beanFactory.registerSingleton('messageSource', new StaticMessageSource())

        when:
        def typeManager = beanFactory.getBean('graphQLTypeManager', DefaultGraphQLTypeManager)

        then:
        typeManager.codeRegistry.is(beanFactory.getBean('graphQLCodeRegistry', GraphQLCodeRegistry.Builder))
    }

    void "the GraphQL the plugin builds counts statements under each operation only while statements are captured: #setting"() {
        given: 'a schema whose one field answers with the page a statement its fetcher ran would count under'
        def beanFactory = new DefaultListableBeanFactory()
        def environment = new StandardEnvironment()
        environment.propertySources.addFirst(new MapPropertySource('test', settings))
        applyRegistrar(beanFactory, environment)
        beanFactory.removeBeanDefinition('graphQLSchema')
        beanFactory.registerSingleton('graphQLSchema', pageSchema())
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest('POST', '/graphql')))

        when:
        ExecutionResult result = beanFactory.getBean('graphQL', GraphQL).execute('query Probe { page }')

        then:
        result.errors.empty
        result.data.page == page

        cleanup:
        RequestContextHolder.resetRequestAttributes()

        where:
        setting    | settings                                         || page
        'enabled'  | [(QueryTailSettings.SETTING_ENABLED): 'true']  || 'POST /? (query Probe)'
        'disabled' | [(QueryTailSettings.SETTING_ENABLED): 'false'] || null
        'absent'   | [:]                                              || null
    }

    /**
     * @return a schema whose field {@code page} answers with the page of a statement run where its fetcher runs
     */
    private static GraphQLSchema pageSchema() {
        DataFetcher<String> page = { env -> new QueryTailRequestAttribution().attribute(new QueryTailRecord(sql: 'select 1')).page } as DataFetcher<String>
        GraphQLSchema.newSchema()
                .query(GraphQLObjectType.newObject().name('Query')
                        .field(GraphQLFieldDefinition.newFieldDefinition().name('page').type(Scalars.GraphQLString)))
                .codeRegistry(GraphQLCodeRegistry.newCodeRegistry().dataFetcher(FieldCoordinates.coordinates('Query', 'page'), page).build())
                .build()
    }

    private static void applyRegistrar(DefaultListableBeanFactory beanFactory, StandardEnvironment environment) {
        def registrar = new GormGraphqlGrailsPlugin().beanRegistrar()
        new BeanRegistryAdapter(beanFactory, environment, registrar.getClass()).register(registrar)
    }
}

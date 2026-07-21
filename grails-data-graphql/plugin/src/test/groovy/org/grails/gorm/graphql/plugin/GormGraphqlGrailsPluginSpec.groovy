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

import graphql.schema.GraphQLCodeRegistry
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

    private static void applyRegistrar(DefaultListableBeanFactory beanFactory, StandardEnvironment environment) {
        def registrar = new GormGraphqlGrailsPlugin().beanRegistrar()
        new BeanRegistryAdapter(beanFactory, environment, registrar.getClass()).register(registrar)
    }
}

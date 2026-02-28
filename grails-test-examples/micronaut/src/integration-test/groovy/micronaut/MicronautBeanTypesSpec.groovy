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
package micronaut

import bean.injection.AppConfig
import bean.injection.FactoryCreatedService
import bean.injection.JavaMessageProvider
import bean.injection.JavaSingletonService
import spock.lang.Specification

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext as SpringApplicationContext

import grails.testing.mixin.integration.Integration

/**
 * Integration tests for various Micronaut bean registration mechanisms in Grails context.
 *
 * Tests that:
 * 1. Java @Singleton beans (processed via annotation processor) are available in Spring context
 * 2. Groovy @Factory/@Bean beans (processed via AST transform) are available in Spring context
 * 3. @ConfigurationProperties beans reflect Grails application.yml config into Micronaut
 * 4. Beans registered by interface type are resolvable via the interface
 */
@Integration
class MicronautBeanTypesSpec extends Specification {

    @Autowired
    SpringApplicationContext springContext

    @Autowired
    JavaSingletonService javaSingletonService

    @Autowired
    FactoryCreatedService factoryCreatedService

    @Autowired
    AppConfig appConfig

    void "Java @Singleton bean is available via Spring autowiring"() {
        expect: 'bean is injected and functional'
        javaSingletonService?.message == 'from-java-singleton'
    }

    void "Groovy @Factory/@Bean created bean is available via Spring autowiring"() {
        expect: 'bean is injected with factory-configured values'
        factoryCreatedService?.name == 'factory-created'
    }

    void "@ConfigurationProperties bean reflects application.yml config"() {
        expect: 'config properties are bound from application.yml'
        appConfig?.name == 'test-micronaut-app'
    }

    void "Java @Singleton bean is a singleton instance"() {
        when: 'retrieving the bean twice from the application context'
        def first = springContext.getBean(JavaSingletonService)
        def second = springContext.getBean(JavaSingletonService)

        then: 'same instance is returned'
        first.is(second)
    }

    void "Factory-created bean is a singleton instance"() {
        when: 'retrieving the bean twice from the application context'
        def first = springContext.getBean(FactoryCreatedService)
        def second = springContext.getBean(FactoryCreatedService)

        then: 'same instance is returned (factory method annotated with @Singleton)'
        first.is(second)
    }

    void "Java @Singleton bean is resolvable by its interface type"() {
        when: 'looking up the bean by the JavaMessageProvider interface'
        def provider = springContext.getBean(JavaMessageProvider)

        then: 'the bean is found and is the same singleton instance'
        provider != null
        provider instanceof JavaSingletonService
        provider.message == 'from-java-singleton'
        provider.is(javaSingletonService)
    }
}

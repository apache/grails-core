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
import bean.injection.JavaSingletonService
import bean.injection.NamedService

import grails.testing.mixin.integration.Integration
import io.micronaut.context.ApplicationContext as MicronautApplicationContext
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext as SpringApplicationContext
import org.springframework.context.ApplicationContextAware
import spock.lang.Specification

/**
 * Integration tests verifying no bean duplication occurs between Spring and Micronaut contexts.
 *
 * When micronaut-spring bridges Micronaut beans into Spring, each bean should appear
 * exactly once in the Spring context (not duplicated). Similarly, Micronaut beans
 * should maintain correct counts in the Micronaut context.
 */
@Integration
class MicronautBeanDuplicationSpec extends Specification implements ApplicationContextAware {

    @Autowired
    MicronautApplicationContext micronautContext

    SpringApplicationContext springContext

    void setApplicationContext(SpringApplicationContext applicationContext) {
        this.springContext = applicationContext
    }

    void "Java @Singleton bean appears exactly once in Spring context"() {
        when:
        def beans = springContext.getBeansOfType(JavaSingletonService)

        then: "only one instance is registered, not duplicated by the bridge"
        beans.size() == 1
    }

    void "@Factory/@Bean created bean appears exactly once in Spring context"() {
        when:
        def beans = springContext.getBeansOfType(FactoryCreatedService)

        then: "only one instance is registered"
        beans.size() == 1
    }

    void "@ConfigurationProperties bean appears exactly once in Spring context"() {
        when:
        def beans = springContext.getBeansOfType(AppConfig)

        then: "only one instance is registered"
        beans.size() == 1
    }

    void "NamedService implementations appear exactly 4 times in Spring context"() {
        when:
        def beans = springContext.getBeansOfType(NamedService)

        then: "4 implementations, not 8 from duplication"
        beans.size() == 4
    }

    void "Micronaut context has exactly 4 NamedService implementations"() {
        when:
        def beans = micronautContext.getBeansOfType(NamedService)

        then:
        beans.size() == 4
    }

    void "Grails service appears exactly once in Spring context"() {
        when:
        def beans = springContext.getBeansOfType(BeanInjectionService)

        then: "Grails artefact not duplicated by Micronaut bridge"
        beans.size() == 1
    }

    void "bridged Micronaut bean in Spring is same instance as in Micronaut context"() {
        when:
        def fromSpring = springContext.getBean(JavaSingletonService)
        def fromMicronaut = micronautContext.getBean(JavaSingletonService)

        then: "bridge shares the same singleton, not a copy"
        fromSpring.is(fromMicronaut)
    }

    void "factory-created bean in Spring is same instance as in Micronaut context"() {
        when:
        def fromSpring = springContext.getBean(FactoryCreatedService)
        def fromMicronaut = micronautContext.getBean(FactoryCreatedService)

        then: "bridge shares the same singleton"
        fromSpring.is(fromMicronaut)
    }

    void "@ConfigurationProperties bean in Spring is same instance as in Micronaut context"() {
        when:
        def fromSpring = springContext.getBean(AppConfig)
        def fromMicronaut = micronautContext.getBean(AppConfig)

        then: "bridge shares the same singleton"
        fromSpring.is(fromMicronaut)
    }
}

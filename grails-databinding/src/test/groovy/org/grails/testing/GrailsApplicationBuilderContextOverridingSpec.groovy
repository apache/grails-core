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
package org.grails.testing

import org.springframework.beans.factory.support.BeanDefinitionOverrideException
import org.springframework.beans.factory.support.DefaultListableBeanFactory
import org.springframework.context.ConfigurableApplicationContext
import spock.lang.Specification
import spock.util.environment.RestoreSystemProperties

/**
 * Verifies that the unit test application context built by {@link GrailsApplicationBuilder} defaults to
 * bean definition overriding and circular references being enabled (the historical Grails behavior),
 * while honoring the standard {@code spring.main.*} configuration properties when they are set.
 */
@RestoreSystemProperties
class GrailsApplicationBuilderContextOverridingSpec extends Specification {

    ConfigurableApplicationContext context

    void cleanup() {
        context?.close()
    }

    void "bean definition overriding and circular references default to true"() {
        when:
        context = buildContext()

        then:
        DefaultListableBeanFactory beanFactory = (DefaultListableBeanFactory) context.beanFactory
        beanFactory.isAllowBeanDefinitionOverriding()
        beanFactory.isAllowCircularReferences()
    }

    void "circular references can be disabled via spring.main.allow-circular-references"() {
        given:
        System.setProperty('spring.main.allow-circular-references', 'false')

        when:
        context = buildContext()

        then:
        DefaultListableBeanFactory beanFactory = (DefaultListableBeanFactory) context.beanFactory
        beanFactory.isAllowBeanDefinitionOverriding()
        !beanFactory.isAllowCircularReferences()
    }

    void "disabling bean definition overriding is honored and the strict context rejects Grails' duplicate bean registrations"() {
        given: "bean definition overriding is disabled, which Grails enables by default because its own bootstrap re-registers beans"
        System.setProperty('spring.main.allow-bean-definition-overriding', 'false')

        when:
        context = buildContext()

        then: "the stricter bean factory rejects the duplicate registration, proving the setting was applied"
        thrown(BeanDefinitionOverrideException)
    }

    private static ConfigurableApplicationContext buildContext() {
        def builder = new GrailsApplicationBuilder().build()
        return (ConfigurableApplicationContext) builder.grailsApplication.mainContext
    }
}

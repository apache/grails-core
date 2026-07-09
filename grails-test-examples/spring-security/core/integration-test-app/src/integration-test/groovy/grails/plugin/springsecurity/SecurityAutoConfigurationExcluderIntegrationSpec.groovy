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
package grails.plugin.springsecurity

import spock.lang.Specification

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.web.SecurityFilterChain

import grails.testing.mixin.integration.Integration

@Integration
class SecurityAutoConfigurationExcluderIntegrationSpec extends Specification {

    @Autowired
    ConfigurableApplicationContext applicationContext

    void "SecurityAutoConfigurationExcluder class is on the classpath"() {
        expect:
        Class.forName(
                'grails.plugin.springsecurity.SecurityAutoConfigurationExcluder'
        )
    }

    void "no Spring Boot SecurityFilterChain bean is registered alongside the plugin"() {
        given: 'the application context bean factory'
        ConfigurableListableBeanFactory beanFactory = applicationContext.beanFactory

        and: 'all SecurityFilterChain beans visible to the application context'
        def filterChainBeans = applicationContext.getBeanNamesForType(SecurityFilterChain)

        expect: 'none come from Spring Boot security auto-configurations'
        filterChainBeans.every { name ->
            !beanFactory.getBeanDefinition(name).beanClassName?.startsWith('org.springframework.boot.security.')
        }

        and: 'none of the excluded auto-configuration class names are registered as beans'
        SecurityAutoConfigurationExcluder.excludedAutoConfigurations.each { className ->
            assert !beanFactory.containsBeanDefinition(className) :
                    "Spring Boot auto-configuration ${className} should be excluded by SecurityAutoConfigurationExcluder"
        }
    }

    void "no Spring Boot in-memory UserDetailsService is registered alongside the plugin"() {
        given: 'the application context bean factory'
        ConfigurableListableBeanFactory beanFactory = applicationContext.beanFactory

        and: 'all UserDetailsService beans visible to the application context'
        def udsBeans = applicationContext.getBeanNamesForType(UserDetailsService)

        expect: 'at least one (the plugin one) exists'
        udsBeans.length >= 1

        and: 'none come from Spring Boot security auto-configurations'
        udsBeans.every { name ->
            !beanFactory.getBeanDefinition(name).beanClassName?.startsWith('org.springframework.boot.security.')
        }

        and: "Boot's in-memory UserDetailsService is not present"
        !udsBeans.any { it == 'inMemoryUserDetailsManager' }
    }
}

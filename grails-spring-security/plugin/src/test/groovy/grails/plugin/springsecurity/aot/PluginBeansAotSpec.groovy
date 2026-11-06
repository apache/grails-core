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
package grails.plugin.springsecurity.aot

import org.springframework.aot.generate.DefaultGenerationContext
import org.springframework.aot.generate.InMemoryGeneratedFiles
import org.springframework.aot.generate.NameGenerator
import org.springframework.context.aot.ApplicationContextAotGenerator
import org.springframework.context.support.GenericApplicationContext
import org.springframework.javapoet.ClassName
import spock.lang.Specification

import grails.plugin.springsecurity.CustomClassesPluginBeansSpec
import grails.plugin.springsecurity.ReflectionUtils
import grails.plugin.springsecurity.SpringSecurityCoreGrailsPlugin
import grails.plugin.springsecurity.SpringSecurityUtils
import grails.spring.BeanBuilder
import grails.testing.web.GrailsWebUnitTest

/**
 * Runs Spring's own ahead-of-time generator over the beans the plugin declares, which an application's
 * {@code processAot} does with them, and which fails where a bean holds a value it cannot write out or
 * has constructors it cannot choose between.
 */
abstract class AbstractPluginBeansAotSpec extends Specification implements GrailsWebUnitTest {

    Set<String> getIncludePlugins() {
        ['core'] as Set
    }

    Closure doWithConfig() {
        { config ->
            configure(config.grails.plugin.springsecurity)
        }
    }

    protected abstract void configure(Object securityConfig)

    void cleanupSpec() {
        SpringSecurityUtils.resetSecurityConfig()
        ReflectionUtils.application = null
        SpringSecurityUtils.application = null
    }

    void 'what the plugin declares can be written out ahead of time'() {
        given:
            def plugin = new SpringSecurityCoreGrailsPlugin(grailsApplication: grailsApplication)
            def builder = new BeanBuilder(null, getClass().classLoader)
            builder.beans(plugin.doWithSpring())
            def context = new GenericApplicationContext()
            builder.registerBeans(context)
            def generationContext = new DefaultGenerationContext(
                    new NameGenerator(ClassName.get('generated', 'Application')), new InMemoryGeneratedFiles())

        when:
            new ApplicationContextAotGenerator().processAheadOfTime(context, generationContext)

        then:
            noExceptionThrown()
            context.getBeanDefinition('passwordEncoder')
            context.getBeanDefinition('authenticationManager')
            context.getBeanDefinition('springSecurityFilterChainProxy')
    }
}

class AnnotationConfigPluginBeansAotSpec extends AbstractPluginBeansAotSpec {

    protected void configure(Object securityConfig) {
        securityConfig.securityConfigType = 'Annotation'
    }
}

class RequestmapPluginBeansAotSpec extends AbstractPluginBeansAotSpec {

    protected void configure(Object securityConfig) {
        securityConfig.securityConfigType = 'Requestmap'
    }
}

class InterceptUrlMapPluginBeansAotSpec extends AbstractPluginBeansAotSpec {

    protected void configure(Object securityConfig) {
        securityConfig.securityConfigType = 'InterceptUrlMap'
    }
}

class OptionalFiltersPluginBeansAotSpec extends AbstractPluginBeansAotSpec {

    protected void configure(Object securityConfig) {
        securityConfig.securityConfigType = 'InterceptUrlMap'
        securityConfig.useBasicAuth = true
        securityConfig.useSwitchUserFilter = true
        securityConfig.useX509 = true
        securityConfig.useSecurityEventListener = true
        securityConfig.useHttpSessionEventPublisher = true
        securityConfig.registerLoggerListener = true
        securityConfig.debug = [useFilter: true]
        securityConfig.cacheUsers = true
        securityConfig.secureChannel = [definition: [[pattern: '/secure/**', access: 'REQUIRES_SECURE_CHANNEL']]]
        securityConfig.ipRestrictions = [[pattern: '/admin/**', access: ['127.0.0.1']]]
        securityConfig.afterInvocationManagerProviderNames = ['afterInvocationProvider']
    }
}

class PersistentRememberMePluginBeansAotSpec extends AbstractPluginBeansAotSpec {

    protected void configure(Object securityConfig) {
        securityConfig.securityConfigType = 'InterceptUrlMap'
        securityConfig.rememberMe = [persistent: true, persistentToken: [domainClassName: 'test.PersistentLogin']]
    }
}

class CustomClassesPluginBeansAotSpec extends AbstractPluginBeansAotSpec {

    protected void configure(Object securityConfig) {
        securityConfig.securityConfigType = 'InterceptUrlMap'
        securityConfig.passwordEncoderBeanClass = CustomClassesPluginBeansSpec.CustomPasswordEncoder
        securityConfig.authenticationManagerBeanClass = CustomClassesPluginBeansSpec.CustomProviderManager
        securityConfig.springSecurityFilterChainProxyBeanClass = CustomClassesPluginBeansSpec.CustomFilterChainProxy
    }
}

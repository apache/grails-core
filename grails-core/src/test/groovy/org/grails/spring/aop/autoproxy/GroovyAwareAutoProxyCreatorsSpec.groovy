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
package org.grails.spring.aop.autoproxy

import org.springframework.aop.aspectj.annotation.AnnotationAwareAspectJAutoProxyCreator
import org.springframework.aop.config.AopConfigUtils
import org.springframework.beans.factory.support.BeanDefinitionRegistry
import org.springframework.beans.factory.support.DefaultListableBeanFactory
import org.springframework.beans.factory.support.RootBeanDefinition

import spock.lang.Specification
import spock.lang.Unroll

/**
 * Covers what {@link GroovyAwareAutoProxyCreators} buys, through the {@link AopConfigUtils} entry
 * points Spring Boot's {@code AopAutoConfiguration}, {@code @EnableAspectJAutoProxy} and the
 * {@code aop} namespace all funnel into.
 */
class GroovyAwareAutoProxyCreatorsSpec extends Specification {

    @Unroll
    void 'a registered #creator.simpleName survives Spring registering an auto proxy creator of its own'() {
        given: 'the Grails creator registered under the bean name Spring reserves for it'
        GroovyAwareAutoProxyCreators.registerWithAopConfigUtils()
        BeanDefinitionRegistry registry = new DefaultListableBeanFactory()
        registry.registerBeanDefinition(AopConfigUtils.AUTO_PROXY_CREATOR_BEAN_NAME, new RootBeanDefinition(creator))

        when: 'Spring is asked for an auto proxy creator, as the AOP auto-configuration does'
        AopConfigUtils.registerAutoProxyCreatorIfNecessary(registry)
        AopConfigUtils.registerAspectJAnnotationAutoProxyCreatorIfNecessary(registry)

        then: 'the Grails creator is a known class and outranks Spring\'s own, so it is left in place'
        registry.getBeanDefinition(AopConfigUtils.AUTO_PROXY_CREATOR_BEAN_NAME).beanClassName == creator.name

        where:
        creator << [GroovyAwareInfrastructureAdvisorAutoProxyCreator, GroovyAwareAspectJAwareAdvisorAutoProxyCreator]
    }

    void 'Spring still escalates between its own creators'() {
        given: 'the infrastructure creator Spring registers by default'
        GroovyAwareAutoProxyCreators.registerWithAopConfigUtils()
        BeanDefinitionRegistry registry = new DefaultListableBeanFactory()
        AopConfigUtils.registerAutoProxyCreatorIfNecessary(registry)

        when: 'a creator of higher priority is asked for'
        AopConfigUtils.registerAspectJAnnotationAutoProxyCreatorIfNecessary(registry)

        then: 'appending the Grails creators has not disturbed the priorities Spring ships with'
        registry.getBeanDefinition(AopConfigUtils.AUTO_PROXY_CREATOR_BEAN_NAME).beanClassName ==
                AnnotationAwareAspectJAutoProxyCreator.name
    }

    void 'repeated registration leaves both resolutions unchanged'() {
        given: 'the registration applied more than once, as each registration site applies it'
        GroovyAwareAutoProxyCreators.registerWithAopConfigUtils()
        GroovyAwareAutoProxyCreators.registerWithAopConfigUtils()
        BeanDefinitionRegistry grailsRegistry = new DefaultListableBeanFactory()
        grailsRegistry.registerBeanDefinition(AopConfigUtils.AUTO_PROXY_CREATOR_BEAN_NAME,
                new RootBeanDefinition(GroovyAwareAspectJAwareAdvisorAutoProxyCreator))
        BeanDefinitionRegistry springRegistry = new DefaultListableBeanFactory()
        AopConfigUtils.registerAutoProxyCreatorIfNecessary(springRegistry)

        when:
        AopConfigUtils.registerAspectJAnnotationAutoProxyCreatorIfNecessary(grailsRegistry)
        AopConfigUtils.registerAspectJAnnotationAutoProxyCreatorIfNecessary(springRegistry)

        then: 'the Grails creator still wins, and Spring\'s own still escalate'
        grailsRegistry.getBeanDefinition(AopConfigUtils.AUTO_PROXY_CREATOR_BEAN_NAME).beanClassName ==
                GroovyAwareAspectJAwareAdvisorAutoProxyCreator.name
        springRegistry.getBeanDefinition(AopConfigUtils.AUTO_PROXY_CREATOR_BEAN_NAME).beanClassName ==
                AnnotationAwareAspectJAutoProxyCreator.name
    }

}

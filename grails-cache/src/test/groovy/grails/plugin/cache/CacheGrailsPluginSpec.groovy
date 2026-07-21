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
package grails.plugin.cache

import org.springframework.beans.factory.support.BeanRegistryAdapter
import org.springframework.beans.factory.support.DefaultListableBeanFactory
import org.springframework.core.env.MapPropertySource
import org.springframework.core.env.StandardEnvironment

import spock.lang.Specification

class CacheGrailsPluginSpec extends Specification {

    void "beanRegistrar registers the cache infrastructure beans"() {
        given:
        def beanFactory = new DefaultListableBeanFactory()

        when:
        applyRegistrar(beanFactory, new StandardEnvironment())

        then:
        beanFactory.containsBeanDefinition('grailsCacheAdminService')
        beanFactory.containsBeanDefinition('grailsCacheConfiguration')
    }

    void "the cache manager and key generator defaults are left to GrailsCacheAutoConfiguration"() {
        // Auto-configured with @ConditionalOnMissingBean so an application- or plugin-defined
        // bean backs the default off instead of triggering a definition override
        given:
        def beanFactory = new DefaultListableBeanFactory()

        when:
        applyRegistrar(beanFactory, new StandardEnvironment())

        then:
        !beanFactory.containsBeanDefinition('grailsCacheManager')
        !beanFactory.containsBeanDefinition('customCacheKeyGenerator')
    }

    void "no cache beans are registered when caching is disabled"() {
        given:
        def beanFactory = new DefaultListableBeanFactory()
        def environment = new StandardEnvironment()
        environment.propertySources.addFirst(new MapPropertySource('test', ['grails.cache.enabled': 'false']))

        when:
        applyRegistrar(beanFactory, environment)

        then:
        beanFactory.beanDefinitionCount == 0
    }

    private static void applyRegistrar(DefaultListableBeanFactory beanFactory, StandardEnvironment environment) {
        def registrar = new CacheGrailsPlugin().beanRegistrar()
        new BeanRegistryAdapter(beanFactory, environment, registrar.getClass()).register(registrar)
    }
}

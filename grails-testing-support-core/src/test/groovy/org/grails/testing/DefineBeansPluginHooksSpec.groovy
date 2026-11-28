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

import org.springframework.beans.factory.BeanRegistrar
import org.springframework.beans.factory.BeanRegistry
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory
import org.springframework.beans.factory.support.BeanDefinitionRegistry
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor
import org.springframework.beans.factory.support.GenericBeanDefinition
import org.springframework.core.Ordered
import org.springframework.core.PriorityOrdered
import org.springframework.core.env.Environment

import spock.lang.Specification

class DefineBeansPluginHooksSpec extends Specification implements GrailsUnitTest {

    void "plain unit tests do not load the converters' web beans"() {
        expect: 'grails-converters, which this module tests with, is the one module of them on its classpath'
        !applicationContext.containsBean('namedJsonConfigurationRegistry')
    }

    static class RegistrarPlugin {

        BeanRegistrar beanRegistrar() {
            return { BeanRegistry registry, Environment environment ->
                registry.registerBean('registrarOnlyBean', StringBuffer)
                registry.registerBean('sharedBean', StringBuffer)
                registry.registerBean('pluginDefinitionsPostProcessor', PluginDefinitionsPostProcessor)
            } as BeanRegistrar
        }
    }

    /** Registers what the {@code BeanRegistry} API cannot express, as a plugin's post-processor does. */
    static class PluginDefinitionsPostProcessor implements BeanDefinitionRegistryPostProcessor, PriorityOrdered {

        @Override
        void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) {
            registry.registerBeanDefinition('postProcessorBean', new GenericBeanDefinition(beanClass: StringBuilder, lazyInit: true))
            registry.registerAlias('postProcessorBean', 'postProcessorAlias')
        }

        @Override
        void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
        }

        @Override
        int getOrder() {
            Ordered.HIGHEST_PRECEDENCE
        }
    }

    static class PluginWithoutRegistrar {
    }

    void "defineBeans applies a plugin's beanRegistrar, and runs the post-processors it registers"() {
        when: 'a plugin is passed to defineBeans after the context has been refreshed'
        defineBeans(new RegistrarPlugin())

        then: 'the registrar beans are registered'
        applicationContext.getBean('registrarOnlyBean') instanceof StringBuffer
        applicationContext.getBean('sharedBean') instanceof StringBuffer

        and: 'the post-processor the registrar registered ran, as at boot, though Spring would not run it any more'
        applicationContext.getBean('postProcessorBean') instanceof StringBuilder
        applicationContext.getBean('postProcessorAlias').is(applicationContext.getBean('postProcessorBean'))
    }

    void "defineBeans of an object without beanRegistrar registers nothing"() {
        given:
        int before = applicationContext.beanDefinitionCount

        when:
        defineBeans(new PluginWithoutRegistrar())

        then:
        applicationContext.beanDefinitionCount == before
    }
}

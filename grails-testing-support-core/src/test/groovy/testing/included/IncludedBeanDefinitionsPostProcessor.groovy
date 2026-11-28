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
package testing.included

import org.springframework.beans.factory.config.ConfigurableListableBeanFactory
import org.springframework.beans.factory.support.BeanDefinitionRegistry
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor
import org.springframework.beans.factory.support.GenericBeanDefinition
import org.springframework.core.Ordered
import org.springframework.core.PriorityOrdered

import grails.core.GrailsApplication

/**
 * Registers the fixture plugin's bean definitions the way a plugin registers the ones the
 * {@code BeanRegistry} API cannot express: from a highest-precedence registry post-processor its
 * registrar registers, which Spring runs before the configuration classes are read.
 */
class IncludedBeanDefinitionsPostProcessor implements BeanDefinitionRegistryPostProcessor, PriorityOrdered {

    final GrailsApplication grailsApplication

    IncludedBeanDefinitionsPostProcessor(GrailsApplication grailsApplication) {
        this.grailsApplication = grailsApplication
    }

    @Override
    void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) {
        // Reads the test's doWithConfig, which has to be applied before plugin beans are registered
        def greeting = new GenericBeanDefinition(beanClass: String)
        greeting.constructorArgumentValues.addGenericArgumentValue(
                grailsApplication.config.getProperty('included.greeting', String, 'unset'))
        registry.registerBeanDefinition('configuredGreeting', greeting)

        // A name the harness has a default for, which the plugin's bean wins over (ReplacingBeansSpec)
        registry.registerBeanDefinition('messageSource', new GenericBeanDefinition(beanClass: IncludedMessageSource))
    }

    @Override
    void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
    }

    @Override
    int getOrder() {
        Ordered.HIGHEST_PRECEDENCE
    }
}

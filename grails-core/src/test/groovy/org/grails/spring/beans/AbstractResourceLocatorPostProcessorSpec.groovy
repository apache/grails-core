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
package org.grails.spring.beans

import org.springframework.beans.factory.support.BeanDefinitionRegistry
import org.springframework.beans.factory.support.DefaultListableBeanFactory
import org.springframework.beans.factory.support.RootBeanDefinition
import org.springframework.context.aot.AbstractAotProcessor
import org.grails.core.io.DefaultResourceLocator
import org.springframework.core.io.DefaultResourceLoader
import spock.lang.Specification

/**
 * Covers the search locations a resource locator inherits.
 *
 * <p>They are directories on the machine this runs on, and a child definition merges them in.
 * Generating code for that child writes them into it, so an application would carry the directory it
 * was built in and look there for its resources -- a path that says where it was built and, wherever
 * it runs, is not where its resources are.</p>
 */
class AbstractResourceLocatorPostProcessorSpec extends Specification {

    BeanDefinitionRegistry registry = new DefaultListableBeanFactory()

    void cleanup() {
        System.clearProperty(AbstractAotProcessor.AOT_PROCESSING)
    }

    private void whileGeneratingCode(boolean generating) {
        generating ? System.setProperty(AbstractAotProcessor.AOT_PROCESSING, 'true')
                : System.clearProperty(AbstractAotProcessor.AOT_PROCESSING)
    }

    private List<String> registeredSearchLocations() {
        registry.getBeanDefinition(AbstractResourceLocatorPostProcessor.BEAN_NAME)
                .propertyValues.getPropertyValue('searchLocations').value as List<String>
    }

    void 'the locations are inherited on an ordinary start'() {
        given:
            whileGeneratingCode(false)

        when:
            new AbstractResourceLocatorPostProcessor(['/base']).postProcessBeanDefinitionRegistry(registry)

        then:
            registeredSearchLocations() == ['/base']
    }

    void 'no location is inherited while code is being generated'() {
        given:
            whileGeneratingCode(true)

        when:
            new AbstractResourceLocatorPostProcessor(['/base']).postProcessBeanDefinitionRegistry(registry)

        then: 'a generated application reads its resources from its own contents, and the directory ' +
                'it was built in belongs to the machine that built it'
            registeredSearchLocations().isEmpty()
    }

    void 'the definition is abstract, so it is inherited rather than built'() {
        when:
            new AbstractResourceLocatorPostProcessor(['/base']).postProcessBeanDefinitionRegistry(registry)

        then:
            registry.getBeanDefinition(AbstractResourceLocatorPostProcessor.BEAN_NAME).abstract
    }

    void 'a definition that is already registered is kept'() {
        given:
            RootBeanDefinition existing = new RootBeanDefinition()
            existing.abstract = true
            registry.registerBeanDefinition(AbstractResourceLocatorPostProcessor.BEAN_NAME, existing)

        when:
            new AbstractResourceLocatorPostProcessor(['/base']).postProcessBeanDefinitionRegistry(registry)

        then: 'which is how an application overrides where its resources are looked for'
            registry.getBeanDefinition(AbstractResourceLocatorPostProcessor.BEAN_NAME).is(existing)
    }

    void 'a locator inheriting nowhere to look still finds a packaged resource'() {
        given: 'what a child inherits from the template while the code is being written out'
            DefaultResourceLocator locator = new DefaultResourceLocator()
            locator.setResourceLoader(new DefaultResourceLoader())
            locator.setSearchLocations([])

        expect: 'the directories are somewhere to look on the machine that built it, and what is ' +
                'left when there are none is the application reading its own contents'
            locator.findResourceForURI('classpath:grails-banner.txt')?.exists()
    }
}

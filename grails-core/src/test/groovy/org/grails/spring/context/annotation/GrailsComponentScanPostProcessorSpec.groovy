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
package org.grails.spring.context.annotation

import org.springframework.beans.factory.support.DefaultListableBeanFactory
import spock.lang.Specification

import grails.plugins.GrailsPluginManager

class GrailsComponentScanPostProcessorSpec extends Specification {

    DefaultListableBeanFactory registry = new DefaultListableBeanFactory()

    void 'no packages to scan means no bean definitions are registered'() {
        given:
        GrailsComponentScanPostProcessor processor = new GrailsComponentScanPostProcessor([], null)

        when:
        processor.postProcessBeanDefinitionRegistry(registry)

        then:
        registry.beanDefinitionCount == 0
    }

    void 'a null package list is treated the same as an empty one'() {
        given:
        GrailsComponentScanPostProcessor processor = new GrailsComponentScanPostProcessor(null, null)

        when:
        processor.postProcessBeanDefinitionRegistry(registry)

        then:
        registry.beanDefinitionCount == 0
    }

    void 'scanning a real package with no plugin manager registers its annotated components'() {
        given:
        GrailsComponentScanPostProcessor processor =
                new GrailsComponentScanPostProcessor(['org.grails.spring.context.annotation.fixture'], null)

        when:
        processor.postProcessBeanDefinitionRegistry(registry)

        then:
        registry.containsBeanDefinition('scanFixtureComponent')
    }

    void 'scanning applies any type filter contributed by the plugin manager'() {
        given:
        GrailsPluginManager pluginManager = Stub(GrailsPluginManager) {
            getTypeFilters() >> []
        }
        GrailsComponentScanPostProcessor processor =
                new GrailsComponentScanPostProcessor(['org.grails.spring.context.annotation.fixture'], pluginManager)

        when:
        processor.postProcessBeanDefinitionRegistry(registry)

        then: 'the default @Component filter still applies since the contributed filter list is empty'
        registry.containsBeanDefinition('scanFixtureComponent')
    }

    void 'postProcessBeanFactory is a no-op'() {
        given:
        GrailsComponentScanPostProcessor processor = new GrailsComponentScanPostProcessor([], null)

        expect:
        processor.postProcessBeanFactory(registry) == null
    }

}

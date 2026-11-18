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
package org.grails.plugins.sitemesh3

import org.sitemesh.webmvc.SiteMeshViewResolver

import org.springframework.beans.factory.config.BeanDefinition
import org.springframework.beans.factory.support.DefaultListableBeanFactory
import org.springframework.beans.factory.support.GenericBeanDefinition
import org.springframework.mock.web.MockServletContext
import org.springframework.web.servlet.ViewResolver
import org.springframework.web.servlet.view.InternalResourceViewResolver

import spock.lang.Specification

class Sitemesh3ViewResolverDefinitionPostProcessorSpec extends Specification {

    Sitemesh3ViewResolverDefinitionPostProcessor postProcessor = new Sitemesh3ViewResolverDefinitionPostProcessor()

    private static DefaultListableBeanFactory registryWithSiteMeshBeans() {
        DefaultListableBeanFactory registry = new DefaultListableBeanFactory()
        registry.registerBeanDefinition('contentProcessor',
                new GenericBeanDefinition(beanClass: CaptureAwareContentProcessor))
        GenericBeanDefinition selector = new GenericBeanDefinition(beanClass: Sitemesh3LayoutFinder, lazyInit: true)
        selector.constructorArgumentValues.addIndexedArgumentValue(0, null)
        registry.registerBeanDefinition('decoratorSelector', selector)
        registry.registerSingleton('servletContext', new MockServletContext())
        registry
    }

    private static GenericBeanDefinition viewResolverDefinition(Class<?> beanClass = InternalResourceViewResolver, boolean lazy = true) {
        new GenericBeanDefinition(beanClass: beanClass, lazyInit: lazy)
    }

    void "rewrites the jspViewResolver definition into the decorating resolver with the original as inner bean"() {
        given:
        DefaultListableBeanFactory registry = registryWithSiteMeshBeans()
        registry.registerBeanDefinition('jspViewResolver', viewResolverDefinition())

        when:
        postProcessor.postProcessBeanDefinitionRegistry(registry)
        BeanDefinition rewritten = registry.getBeanDefinition('jspViewResolver')

        then:
        rewritten.beanClassName == GrailsSiteMeshViewResolver.name
        rewritten.lazyInit
        rewritten.primary
        rewritten.constructorArgumentValues.getIndexedArgumentValue(0, null).value instanceof BeanDefinition
        ((BeanDefinition) rewritten.constructorArgumentValues.getIndexedArgumentValue(0, null).value)
                .beanClassName == InternalResourceViewResolver.name
    }

    void "the lazy-init flag of the original definition is preserved"() {
        given:
        DefaultListableBeanFactory registry = registryWithSiteMeshBeans()
        registry.registerBeanDefinition('jspViewResolver', viewResolverDefinition(InternalResourceViewResolver, false))

        when:
        postProcessor.postProcessBeanDefinitionRegistry(registry)

        then:
        !registry.getBeanDefinition('jspViewResolver').lazyInit
    }

    void "the definition is left alone when the SiteMesh beans are not registered"() {
        given: "a context without the SiteMesh beans, like a unit-test context"
        DefaultListableBeanFactory registry = new DefaultListableBeanFactory()
        if (hasContentProcessor) {
            registry.registerBeanDefinition('contentProcessor',
                    new GenericBeanDefinition(beanClass: CaptureAwareContentProcessor))
        }
        if (hasDecoratorSelector) {
            registry.registerBeanDefinition('decoratorSelector',
                    new GenericBeanDefinition(beanClass: Sitemesh3LayoutFinder))
        }
        registry.registerBeanDefinition('jspViewResolver', viewResolverDefinition())

        when:
        postProcessor.postProcessBeanDefinitionRegistry(registry)

        then:
        registry.getBeanDefinition('jspViewResolver').beanClassName == InternalResourceViewResolver.name

        where:
        hasContentProcessor | hasDecoratorSelector
        false               | false
        true                | false
        false               | true
    }

    void "a missing jspViewResolver definition is not an error"() {
        given:
        DefaultListableBeanFactory registry = registryWithSiteMeshBeans()

        when:
        postProcessor.postProcessBeanDefinitionRegistry(registry)

        then:
        noExceptionThrown()
        !registry.containsBeanDefinition('jspViewResolver')
    }

    void "a definition that already decorates is left unwrapped"() {
        given:
        DefaultListableBeanFactory registry = registryWithSiteMeshBeans()
        registry.registerBeanDefinition('jspViewResolver', viewResolverDefinition(alreadyDecorating))

        when:
        postProcessor.postProcessBeanDefinitionRegistry(registry)

        then:
        registry.getBeanDefinition('jspViewResolver').beanClassName == alreadyDecorating.name

        where: "this module's wrapper, or any custom SiteMeshViewResolver"
        alreadyDecorating << [GrailsSiteMeshViewResolver, SiteMeshViewResolver]
    }

    void "a consumer that force-initializes the lazy resolver before any post-processor runs still gets the decorating resolver"() {
        given: "a registry processed at definition level, with no bean post-processors registered at all"
        DefaultListableBeanFactory registry = registryWithSiteMeshBeans()
        registry.registerBeanDefinition('jspViewResolver', viewResolverDefinition())
        postProcessor.postProcessBeanDefinitionRegistry(registry)

        when: "an early component collects every ViewResolver, the way ContentNegotiatingViewResolver does while initializing"
        Map<String, ViewResolver> captured = registry.getBeansOfType(ViewResolver)

        then: "the captured instance decorates - the raw resolver is impossible to observe"
        captured['jspViewResolver'] instanceof GrailsSiteMeshViewResolver
        ((SiteMeshViewResolver) captured['jspViewResolver']).innerViewResolver instanceof InternalResourceViewResolver
    }
}

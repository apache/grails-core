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

package org.grails.plugins.web


import org.grails.web.servlet.view.GrailsLayoutViewResolver
import org.springframework.beans.BeansException
import org.springframework.beans.MutablePropertyValues
import org.springframework.beans.factory.config.BeanDefinition
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory
import org.springframework.beans.factory.config.RuntimeBeanReference
import org.springframework.beans.factory.support.BeanDefinitionRegistry
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor
import org.springframework.beans.factory.support.GenericBeanDefinition
import org.springframework.core.Ordered

/**
 * This BeanDefinitionRegistryPostProcessor replaces the existing jspViewResolver bean with EmbeddedGrailsLayoutViewResolver
 * and moves the previous jspViewResolver bean configuration as an inner bean of EmbeddedGrailsLayoutViewResolver to be used as
 * the innerViewResolver of it.
 *
 * Scaffolding plugin replaces jspViewResolver with it's own implementation and this solution makes it easier to customize
 * the inner view resolver.
 *
 *
 * @author Lari Hotari
 * @since 2.4.0
 * @see org.grails.web.servlet.view.EmbeddedGrailsLayoutViewResolver*
 */
class GrailsLayoutViewResolverPostProcessor implements BeanDefinitionRegistryPostProcessor, Ordered {

    private static final String GRAILS_VIEW_RESOLVER_BEAN_NAME = "jspViewResolver"
    private static final String GROOVY_PAGE_LAYOUT_FINDER_BEAN_NAME = "groovyPageLayoutFinder"
    int order = 0
    Class<?> layoutViewResolverClass = GrailsLayoutViewResolver
    String layoutViewResolverBeanParentName = null
    boolean markBeanPrimary = true

    boolean enabled = true

    @Override
    void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
    }

    @Override
    void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        if (enabled && registry.containsBeanDefinition(GRAILS_VIEW_RESOLVER_BEAN_NAME)) {
            BeanDefinition previousViewResolver = registry.getBeanDefinition(GRAILS_VIEW_RESOLVER_BEAN_NAME);
            registry.removeBeanDefinition(GRAILS_VIEW_RESOLVER_BEAN_NAME);

            GenericBeanDefinition beanDefinition = new GenericBeanDefinition()
            beanDefinition.beanClass = layoutViewResolverClass
            if (layoutViewResolverBeanParentName) {
                beanDefinition.parentName = layoutViewResolverBeanParentName
            }
            if (markBeanPrimary) {
                beanDefinition.primary = true
            }
            final MutablePropertyValues propertyValues = beanDefinition.getPropertyValues()
            propertyValues.addPropertyValue('innerViewResolver', previousViewResolver)
            propertyValues.addPropertyValue('groovyPageLayoutFinder', new RuntimeBeanReference((String) GROOVY_PAGE_LAYOUT_FINDER_BEAN_NAME, false))
            registry.registerBeanDefinition(GRAILS_VIEW_RESOLVER_BEAN_NAME, beanDefinition)
        }
    }
}

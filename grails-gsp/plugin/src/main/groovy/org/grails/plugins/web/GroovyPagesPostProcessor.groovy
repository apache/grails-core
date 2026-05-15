/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  'License'); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  'AS IS' BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

package org.grails.plugins.web

import org.springframework.beans.BeansException
import org.springframework.beans.factory.support.BeanDefinitionRegistry
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor
import org.springframework.beans.factory.support.GenericBeanDefinition
import org.springframework.core.Ordered

import org.grails.web.servlet.view.GroovyPageViewResolver

/**
 * Registers a jspViewResolver bean definition if one does not already exist.
 */
class GroovyPagesPostProcessor implements BeanDefinitionRegistryPostProcessor, Ordered {

    private static final String JSP_VIEW_RESOLVER_BEAN_NAME = 'jspViewResolver'
    public static final int ORDER = 0

    int order = ORDER

    @Override
    void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        if (!registry.containsBeanDefinition(JSP_VIEW_RESOLVER_BEAN_NAME)) {
            def beanDefinition = new GenericBeanDefinition().tap {
                beanClass = GroovyPageViewResolver
                parentName = 'abstractViewResolver'
                lazyInit = true
            }
            registry.registerBeanDefinition(JSP_VIEW_RESOLVER_BEAN_NAME, beanDefinition)
        }
    }
}

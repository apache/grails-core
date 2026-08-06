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

package org.grails.spring.beans;

import java.util.List;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.core.PriorityOrdered;

/**
 * Registers {@code abstractGrailsResourceLocator}, the abstract parent definition that
 * resource-locator beans inherit their search locations from.
 *
 * <p>The definition carries a single property and no bean class, so it exists purely to be
 * inherited with {@code bean.parent = 'abstractGrailsResourceLocator'} — third-party plugins
 * do so, asset-pipeline's {@code assetResourceLocator} among them, which makes it part of the
 * core plugin's public surface.</p>
 *
 * <p>A classless, abstract definition has no equivalent in {@code BeanRegistry.Spec}, whose
 * {@code registerBean} always takes a class, nor on a {@code @Bean} method. Contributing it
 * through a {@link BeanDefinitionRegistryPostProcessor} reaches the underlying registry, where
 * it can be expressed directly, so the core plugin needs no bean builder DSL for it.</p>
 *
 * @since 8.0
 */
public class AbstractResourceLocatorPostProcessor implements BeanDefinitionRegistryPostProcessor, PriorityOrdered {

    public static final String BEAN_NAME = "abstractGrailsResourceLocator";

    private final List<String> searchLocations;

    public AbstractResourceLocatorPostProcessor(List<String> searchLocations) {
        this.searchLocations = searchLocations;
    }

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        if (registry.containsBeanDefinition(BEAN_NAME)) {
            return;
        }
        GenericBeanDefinition definition = new GenericBeanDefinition();
        definition.setAbstract(true);
        definition.getPropertyValues().add("searchLocations", this.searchLocations);
        registry.registerBeanDefinition(BEAN_NAME, definition);
    }

    /**
     * Ordered first, because a child definition naming this one as its parent cannot be merged
     * until it exists, and merging happens as soon as anything resolves beans by type.
     */
    @Override
    public int getOrder() {
        return HIGHEST_PRECEDENCE;
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        // the definition is contributed in postProcessBeanDefinitionRegistry
    }

}

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
package grails.spring;

import java.util.Map;

import groovy.lang.Closure;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;

/**
 * BeanRegistryAdapter backed by the existing BeanBuilder implementation.
 *
 * @since 8.1
 */
public class BeanBuilderBeanRegistryAdapter implements BeanRegistryAdapter {

    private final BeanBuilder beanBuilder;

    public BeanBuilderBeanRegistryAdapter() {
        this(new BeanBuilder());
    }

    public BeanBuilderBeanRegistryAdapter(BeanBuilder beanBuilder) {
        this.beanBuilder = beanBuilder;
    }

    public BeanBuilder getBeanBuilder() {
        return beanBuilder;
    }

    @Override
    public BeanRegistryAdapter beans(Closure<?> closure) {
        beanBuilder.beans(closure);
        return this;
    }

    @Override
    public Map<String, BeanDefinition> getBeanDefinitions() {
        return beanBuilder.getBeanDefinitions();
    }

    @Override
    public void registerBeans(BeanDefinitionRegistry registry) {
        beanBuilder.registerBeans(registry);
    }
}

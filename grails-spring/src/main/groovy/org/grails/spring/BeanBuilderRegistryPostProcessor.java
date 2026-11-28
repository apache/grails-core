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
package org.grails.spring;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

import groovy.lang.Binding;
import groovy.lang.Closure;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;

import grails.spring.BeanBuilder;

/**
 * Registers the bean definitions a {@link BeanBuilder} closure declares, for a library whose beans are
 * written in that DSL. The closure is asked for, and run, when Spring runs this post-processor, and its
 * definitions are registered with the registry the post-processor is given, replacing a definition of the
 * same name, as the closure's own bean builder would.
 *
 * <p>It is {@link PriorityOrdered} with {@link Ordered#HIGHEST_PRECEDENCE}, so registered as a bean ahead
 * of the configuration classes it runs before Spring reads them: an auto-configuration's
 * {@code @ConditionalOnMissingBean} then backs off from the beans it registers. Post-processors of the
 * same order run in the order their beans were registered.</p>
 *
 * @since 9.0
 */
public class BeanBuilderRegistryPostProcessor implements BeanDefinitionRegistryPostProcessor, PriorityOrdered {

    private final ApplicationContext parent;

    private final ClassLoader classLoader;

    private final Map<String, Object> variables;

    private final Supplier<Closure<?>> definitions;

    /**
     * @param parent the context a bean builder reference to a parent bean resolves against, or {@code null}
     * @param classLoader the class loader bean classes named by a string are loaded with
     * @param variables the variables the closure reads from the bean builder's binding
     * @param definitions supplies the closure, or {@code null} for no beans, when the post-processor runs
     */
    public BeanBuilderRegistryPostProcessor(ApplicationContext parent, ClassLoader classLoader,
            Map<String, Object> variables, Supplier<Closure<?>> definitions) {
        this.parent = parent;
        this.classLoader = classLoader;
        this.variables = new LinkedHashMap<>(variables);
        this.definitions = definitions;
    }

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        Closure<?> closure = definitions.get();
        if (closure == null) {
            return;
        }
        BeanBuilder beanBuilder = new BeanBuilder(parent, new DefaultRuntimeSpringConfiguration(), classLoader);
        Binding binding = new Binding();
        variables.forEach(binding::setVariable);
        beanBuilder.setBinding(binding);
        beanBuilder.beans(closure);
        beanBuilder.getSpringConfig().registerBeansWithRegistry(registry);
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}

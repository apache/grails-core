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
package org.apache.grails.core.aot;

import org.jspecify.annotations.Nullable;

import org.springframework.beans.BeansException;
import org.springframework.beans.PropertyValues;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.annotation.CommonAnnotationBeanPostProcessor;

/**
 * Injects through {@code @Resource}, and calls {@code @PostConstruct} and {@code @PreDestroy} methods on, the
 * beans the code generated ahead of time did not handle, and leaves alone the ones it did -- the beans it
 * created that are of the type their definitions declare: it has injected those and recorded their
 * initialization and destruction methods on their definitions.
 *
 * @since 9.0
 * @see GeneratedBeanDefinitions
 */
public class GeneratedArtifactsCommonAnnotationBeanPostProcessor extends CommonAnnotationBeanPostProcessor {

    @Nullable
    private BeanFactory beanFactory;

    @Override
    public void setBeanFactory(BeanFactory beanFactory) {
        super.setBeanFactory(beanFactory);
        this.beanFactory = beanFactory;
    }

    @Override
    public void postProcessMergedBeanDefinition(RootBeanDefinition beanDefinition, Class<?> beanType, String beanName) {
        if (!GeneratedBeanDefinitions.isInjectedByGeneratedCode(beanFactory, beanName, beanType)) {
            super.postProcessMergedBeanDefinition(beanDefinition, beanType, beanName);
        }
    }

    @Override
    public PropertyValues postProcessProperties(PropertyValues pvs, Object bean, String beanName) throws BeansException {
        if (GeneratedBeanDefinitions.isInjectedByGeneratedCode(beanFactory, beanName, bean.getClass())) {
            return pvs;
        }
        return super.postProcessProperties(pvs, bean, beanName);
    }

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        if (GeneratedBeanDefinitions.isInjectedByGeneratedCode(beanFactory, beanName, bean.getClass())) {
            return bean;
        }
        return super.postProcessBeforeInitialization(bean, beanName);
    }

    @Override
    public void postProcessBeforeDestruction(Object bean, String beanName) throws BeansException {
        if (!GeneratedBeanDefinitions.isInjectedByGeneratedCode(beanFactory, beanName, bean.getClass())) {
            super.postProcessBeforeDestruction(bean, beanName);
        }
    }

}

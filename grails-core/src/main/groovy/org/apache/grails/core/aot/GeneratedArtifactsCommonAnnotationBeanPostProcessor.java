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
 * Injects through {@code @Resource} what the code generated ahead of time did not: an instance that code
 * created only through the members its class adds to the type the definition names, and every other bean in
 * full.
 *
 * <p>It calls {@code @PostConstruct} and {@code @PreDestroy} methods as Spring's own does, for every instance.
 * The names the generated code recorded for them are taken off its definitions when this is registered, see
 * {@link GeneratedBeanDefinitions#leaveAnnotatedLifecycleMethodsToProcessor}.</p>
 *
 * @since 8.0.1
 * @see GeneratedBeanDefinitions
 */
public class GeneratedArtifactsCommonAnnotationBeanPostProcessor extends CommonAnnotationBeanPostProcessor {

    /**
     * Spring's own, as the code was generated with: it finds the members that code injected, and injects in full
     * another instance of a generated bean.
     */
    private final CommonAnnotationBeanPostProcessor generatingProcessor = new CommonAnnotationBeanPostProcessor();

    @Nullable
    private BeanFactory beanFactory;

    @Override
    public void setBeanFactory(BeanFactory beanFactory) {
        super.setBeanFactory(beanFactory);
        this.generatingProcessor.setBeanFactory(beanFactory);
        this.beanFactory = beanFactory;
    }

    @Override
    public void postProcessMergedBeanDefinition(RootBeanDefinition beanDefinition, Class<?> beanType, String beanName) {
        GeneratedBeanDefinitions.markMembersInjectedByGeneratedCode(beanDefinition, beanType, generatingProcessor);
        super.postProcessMergedBeanDefinition(beanDefinition, beanType, beanName);
    }

    @Override
    public void resetBeanDefinition(String beanName) {
        super.resetBeanDefinition(beanName);
        generatingProcessor.resetBeanDefinition(beanName);
    }

    @Override
    public PropertyValues postProcessProperties(PropertyValues pvs, Object bean, String beanName) throws BeansException {
        if (GeneratedBeanDefinitions.isAnotherInstanceOfGeneratedBean(beanFactory, beanName, bean)) {
            return generatingProcessor.postProcessProperties(pvs, bean, beanName);
        }
        return super.postProcessProperties(pvs, bean, beanName);
    }

}

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
import org.springframework.beans.factory.annotation.AutowiredAnnotationBeanPostProcessor;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.annotation.CommonAnnotationBeanPostProcessor;

/**
 * Injects through {@code @Autowired}, {@code @Value} and {@code @Inject} what the code generated ahead of time
 * did not: an instance that code created only through the members its class adds to the type the definition
 * names, and every other bean in full.
 *
 * <p>Another instance of a generated bean is injected in full as Spring injects any bean: the common processor
 * comes first, and a member it injects, one annotated with {@code @Resource} as well, is left to it.</p>
 *
 * @since 8.0.1
 * @see GeneratedBeanDefinitions
 */
public class GeneratedArtifactsAutowiredAnnotationBeanPostProcessor extends AutowiredAnnotationBeanPostProcessor {

    /**
     * Spring's own, as the code was generated with: it finds the members that code injected, and injects in full
     * another instance of a generated bean.
     */
    private final AutowiredAnnotationBeanPostProcessor generatingProcessor = new AutowiredAnnotationBeanPostProcessor();

    /**
     * Spring's common processor, which processes a definition before this kind does: it finds the members it
     * injects itself, which this kind leaves to it, such as a field annotated for both.
     */
    private final CommonAnnotationBeanPostProcessor commonProcessor = new CommonAnnotationBeanPostProcessor();

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
            // The class of the instance itself, a CGLIB subclass included: the injection looks the members up by it.
            Class<?> beanClass = bean.getClass();
            RootBeanDefinition processedInOrder = new RootBeanDefinition(beanClass);
            commonProcessor.postProcessMergedBeanDefinition(processedInOrder, beanClass, "");
            generatingProcessor.postProcessMergedBeanDefinition(processedInOrder, beanClass, beanName);
            return generatingProcessor.postProcessProperties(pvs, bean, beanName);
        }
        return super.postProcessProperties(pvs, bean, beanName);
    }

}

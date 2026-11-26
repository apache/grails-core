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
package org.grails.plugin.cache

import java.lang.reflect.Field

import groovy.transform.CompileStatic

import org.springframework.beans.factory.BeanFactory
import org.springframework.beans.factory.BeanFactoryAware
import org.springframework.beans.factory.annotation.Value
import org.springframework.beans.factory.config.BeanPostProcessor
import org.springframework.beans.factory.config.ConfigurableBeanFactory
import org.springframework.util.ReflectionUtils

/**
 * Records the dependency of cache-aware beans on the named cache manager for lifecycle ordering.
 * Spring's expression-based field injection does not record this dependency.
 */
@CompileStatic
class GrailsCacheManagerDependencyRegistrar implements BeanPostProcessor, BeanFactoryAware {

    private ConfigurableBeanFactory beanFactory

    @Override
    void setBeanFactory(BeanFactory beanFactory) {
        this.beanFactory = (ConfigurableBeanFactory) beanFactory
    }

    @Override
    Object postProcessBeforeInitialization(Object bean, String beanName) {
        if (bean instanceof GrailsCacheManagerAware) {
            Field field = ReflectionUtils.findField(bean.getClass(), GrailsCacheManagerAliasRegistrar.ALIAS)
            if (field?.isAnnotationPresent(Value)) {
                ReflectionUtils.makeAccessible(field)
                if (ReflectionUtils.getField(field, bean) != null) {
                    beanFactory.registerDependentBean(GrailsCacheManagerAliasRegistrar.CACHE_MANAGER_BEAN_NAME, beanName)
                }
            }
        }
        return bean
    }
}

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

import groovy.transform.CompileStatic

import org.springframework.beans.BeansException
import org.springframework.beans.factory.config.BeanFactoryPostProcessor
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory

/**
 * Names the cache manager called {@code grailsCacheManager} after the field a cache-aware class has it
 * injected into, so that Spring takes it where it cannot otherwise choose.
 *
 * <p>{@link GrailsCacheManagerAware} injects a {@code CacheManager} by type. Beside a second cache
 * manager, such as the one the Spring Security plugin defines when {@code cacheUsers} is set, Spring
 * cannot choose by type, and falls back to the bean named like the field. A trait's field is renamed
 * in the class that implements it, so no bean matched, and the application failed to start. With this
 * alias the renamed field matches {@code grailsCacheManager}. Where Spring could choose, which is the
 * only cache manager or the one marked primary, nothing changes.</p>
 *
 * <p>The alias is registered after every bean definition is in, so a {@code grailsCacheManager} that a
 * cache provider plugin or the application defines gets it too, and it is registered only where there
 * is a bean of that name and the alias is free.</p>
 *
 * @since 8.0.1
 */
@CompileStatic
class GrailsCacheManagerAliasRegistrar implements BeanFactoryPostProcessor {

    static final String CACHE_MANAGER_BEAN_NAME = 'grailsCacheManager'

    /** The name the trait's field has in each class that implements it. */
    static final String ALIAS = "${GrailsCacheManagerAware.name.replace('.', '_')}__${CACHE_MANAGER_BEAN_NAME}".toString()

    @Override
    void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        if (beanFactory.containsBean(CACHE_MANAGER_BEAN_NAME) && !beanFactory.containsBean(ALIAS)) {
            beanFactory.registerAlias(CACHE_MANAGER_BEAN_NAME, ALIAS)
        }
    }
}

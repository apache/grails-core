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

import java.util.concurrent.atomic.AtomicBoolean

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import org.springframework.beans.BeansException
import org.springframework.beans.PropertyValues
import org.springframework.beans.factory.BeanFactoryUtils
import org.springframework.beans.factory.NoSuchBeanDefinitionException
import org.springframework.beans.factory.config.BeanDefinition
import org.springframework.beans.factory.config.BeanFactoryPostProcessor
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory
import org.springframework.beans.factory.config.InstantiationAwareBeanPostProcessor
import org.springframework.beans.factory.support.AbstractBeanDefinition
import org.springframework.cache.CacheManager
import org.springframework.core.AliasRegistry
import org.springframework.core.annotation.OrderUtils

/**
 * Names the cache manager called {@code grailsCacheManager} after the field a cache-aware class has it
 * injected into, so that Spring takes it where it cannot otherwise choose.
 *
 * <p>{@link GrailsCacheManagerAware} injects a {@code CacheManager} by type. Beside a second cache
 * manager, such as the one the Spring Security plugin defines when {@code cacheUsers} is set, Spring
 * cannot choose by type, and falls back to the bean named like the field. A trait's field is renamed
 * in the class that implements it, so no bean matched, and the application failed to start. With this
 * alias the renamed field matches {@code grailsCacheManager}. Where Spring could choose, which is the
 * only cache manager, or one marked primary or with a priority, nothing changes.</p>
 *
 * <p>Spring takes a bean named like the field before one with a higher priority, so the alias is left
 * out where another cache manager is marked primary or has a priority, to keep Spring's choice there.
 * Otherwise a {@code grailsCacheManager} created from a cache-aware bean, which Spring gave the other
 * cache manager, would be asked for while it is being created, and fail.</p>
 *
 * <p>No bean is created here, so the priority is read from the type the bean definitions declare. A
 * factory method declared to return a {@code CacheManager} hides the priority of the object it returns,
 * which Spring reads once that bean exists. So the check runs again before the first cache-aware bean
 * is injected, when the cache managers created by then have the type of their object, and the alias is
 * removed where one of them has a priority. Spring sees the priority of the same cache managers when it
 * injects that bean, and every cache-aware bean gets the same cache manager.</p>
 *
 * <p>The alias is registered after every bean definition is in, so a {@code grailsCacheManager} that a
 * cache provider plugin or the application defines gets it too, and it is registered only where there
 * is a bean of that name and the alias is free.</p>
 *
 * @since 8.0.1
 */
@Slf4j
@CompileStatic
class GrailsCacheManagerAliasRegistrar implements BeanFactoryPostProcessor {

    static final String CACHE_MANAGER_BEAN_NAME = 'grailsCacheManager'

    /** The name the trait's field has in each class that implements it. */
    static final String ALIAS = "${GrailsCacheManagerAware.name.replace('.', '_')}__${CACHE_MANAGER_BEAN_NAME}".toString()

    @Override
    void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        if (beanFactory.containsBean(CACHE_MANAGER_BEAN_NAME) && !beanFactory.containsBean(ALIAS) &&
                !anotherPreferred(beanFactory, false)) {
            beanFactory.registerAlias(CACHE_MANAGER_BEAN_NAME, ALIAS)
            beanFactory.addBeanPostProcessor(new PriorityOfCreatedCacheManagers(beanFactory))
        }
    }

    /**
     * Whether Spring prefers another cache manager over {@code grailsCacheManager}, as primary or by priority.
     *
     * @param allowFactoryBeanInit whether a factory bean may be created to tell the type of its object, as
     * Spring does when it injects
     */
    private static boolean anotherPreferred(ConfigurableListableBeanFactory beanFactory, boolean allowFactoryBeanInit) {
        BeanFactoryUtils.beanNamesForTypeIncludingAncestors(beanFactory, CacheManager, true, false).any { String name ->
            name != CACHE_MANAGER_BEAN_NAME && !(CACHE_MANAGER_BEAN_NAME in beanFactory.getAliases(name)) &&
                    preferredBySpring(beanFactory, name, allowFactoryBeanInit)
        }
    }

    /**
     * Whether Spring prefers the cache manager when it injects one by type. One registered as a singleton
     * has no bean definition, and only the priority of its class. One that is not a candidate for
     * injection by type is never preferred. The type of a cache manager that exists is that of its object.
     */
    private static boolean preferredBySpring(ConfigurableListableBeanFactory beanFactory, String name,
                                             boolean allowFactoryBeanInit) {
        try {
            BeanDefinition definition = definitionOf(beanFactory, name)
            if (definition != null) {
                if (!definition.autowireCandidate ||
                        (definition instanceof AbstractBeanDefinition && !((AbstractBeanDefinition) definition).defaultCandidate)) {
                    return false
                }
                if (definition.primary) {
                    return true
                }
            }
            Class<?> type = beanFactory.getType(name, allowFactoryBeanInit)
            return type != null && OrderUtils.getPriority(type) != null
        }
        catch (BeansException e) {
            // a definition that cannot be read fails when its bean is created, not here
            log.debug('Could not tell whether Spring prefers the cache manager {}', name, e)
            return false
        }
    }

    private static BeanDefinition definitionOf(ConfigurableListableBeanFactory beanFactory, String name) {
        try {
            return beanFactory.getMergedBeanDefinition(name)
        }
        catch (NoSuchBeanDefinitionException ignored) {
            return null
        }
    }

    /**
     * Before the first cache-aware bean is injected, removes the alias where a cache manager created by
     * then has a priority its declared type did not show. It runs ahead of the injection, as it is added
     * before the context registers its own bean post-processors, and only once, so that every cache-aware
     * bean gets the cache manager the first one gets.
     */
    @CompileStatic
    private static class PriorityOfCreatedCacheManagers implements InstantiationAwareBeanPostProcessor {

        private final ConfigurableListableBeanFactory beanFactory

        private final AtomicBoolean checked = new AtomicBoolean()

        PriorityOfCreatedCacheManagers(ConfigurableListableBeanFactory beanFactory) {
            this.beanFactory = beanFactory
        }

        @Override
        PropertyValues postProcessProperties(PropertyValues pvs, Object bean, String beanName) throws BeansException {
            if (bean instanceof GrailsCacheManagerAware && checked.compareAndSet(false, true) &&
                    anotherPreferred(beanFactory, true)) {
                AliasRegistry aliases = (AliasRegistry) beanFactory
                aliases.removeAlias(ALIAS)
            }
            return pvs
        }
    }
}

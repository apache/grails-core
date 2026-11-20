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

import java.util.concurrent.ConcurrentHashMap

import groovy.transform.CompileStatic

import org.springframework.beans.BeansException
import org.springframework.beans.factory.BeanFactory
import org.springframework.beans.factory.BeanFactoryAware
import org.springframework.beans.factory.config.BeanDefinitionHolder
import org.springframework.beans.factory.config.BeanFactoryPostProcessor
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory
import org.springframework.beans.factory.config.DependencyDescriptor
import org.springframework.beans.factory.support.AutowireCandidateResolver
import org.springframework.beans.factory.support.DefaultListableBeanFactory
import org.springframework.beans.factory.support.MergedBeanDefinitionPostProcessor
import org.springframework.beans.factory.support.RootBeanDefinition
import org.springframework.cache.CacheManager
import org.springframework.core.ResolvableType

/**
 * Gives a cache-aware class the cache manager named {@code grailsCacheManager} where Spring cannot choose
 * the cache manager it injects, and leaves Spring's choice as it is everywhere else.
 *
 * <p>{@link GrailsCacheManagerAware} injects a {@code CacheManager} by type. Beside a second cache
 * manager, such as the one the Spring Security plugin defines when {@code cacheUsers} is set, Spring
 * cannot choose by type, and falls back to the bean named like the field. A trait's field is renamed in
 * the class that implements it, so no bean matched, and the application failed to start.</p>
 *
 * <p>This wraps the bean factory's autowire candidate resolver. Spring asks the resolver, before it
 * resolves a dependency, for a proxy to inject in its place; for the trait's field the resolver answers
 * with the field's resolution itself. It is Spring's own, for the bean being injected, with one change:
 * where Spring would fail because it cannot choose, and {@code grailsCacheManager} is one of the cache
 * managers it cannot choose between, it takes {@code grailsCacheManager}. Everywhere else Spring chooses
 * as it did: the only cache manager, the one marked primary or the one with the highest priority, judged
 * at that moment, leaving out the bean being injected. The bean is registered as depending on the cache
 * managers the resolution took, as the injection registers it, and the cache manager taken is remembered
 * for the next injection of that bean, as the injection remembers it, so that a prototype bean resolves
 * the field once, until the bean's definition is registered again or removed, when the injection forgets
 * it too. Any other injection of a cache manager is left alone.</p>
 *
 * @since 8.0.1
 */
@CompileStatic
class GrailsCacheManagerFallback implements BeanFactoryPostProcessor {

    static final String CACHE_MANAGER_BEAN_NAME = 'grailsCacheManager'

    /** The name the trait's field has in each class that implements it. */
    static final String FIELD_NAME = "${GrailsCacheManagerAware.name.replace('.', '_')}__${CACHE_MANAGER_BEAN_NAME}".toString()

    @Override
    void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        if (beanFactory instanceof DefaultListableBeanFactory) {
            DefaultListableBeanFactory listable = (DefaultListableBeanFactory) beanFactory
            if (!(listable.autowireCandidateResolver instanceof CandidateResolver)) {
                CandidateResolver resolver = new CandidateResolver(listable.autowireCandidateResolver)
                listable.autowireCandidateResolver = resolver
                // told when a bean definition is registered again or removed, as the injection is
                listable.addBeanPostProcessor(resolver)
            }
        }
    }

    /** Whether the dependency is the trait's cache manager field. */
    static boolean isTraitField(DependencyDescriptor descriptor) {
        descriptor.field != null && descriptor.field.name == FIELD_NAME && descriptor.field.type == CacheManager &&
                GrailsCacheManagerAware.isAssignableFrom(descriptor.field.declaringClass)
    }

    /**
     * The bean factory's resolver, resolving the trait's field with the fall back to
     * {@code grailsCacheManager}.
     */
    @CompileStatic
    static class CandidateResolver implements AutowireCandidateResolver, BeanFactoryAware, MergedBeanDefinitionPostProcessor {

        private final AutowireCandidateResolver delegate

        private DefaultListableBeanFactory beanFactory

        /**
         * The cache manager each bean's field was given, by bean name and injection point, where the
         * injection would remember it: one bean, of the field's type.
         */
        private final Map<List<Object>, String> remembered = new ConcurrentHashMap<List<Object>, String>()

        CandidateResolver(AutowireCandidateResolver delegate) {
            this.delegate = delegate
        }

        @Override
        void setBeanFactory(BeanFactory beanFactory) throws BeansException {
            this.beanFactory = beanFactory instanceof DefaultListableBeanFactory ? (DefaultListableBeanFactory) beanFactory : null
            if (delegate instanceof BeanFactoryAware) {
                ((BeanFactoryAware) delegate).beanFactory = beanFactory
            }
        }

        @Override
        Object getLazyResolutionProxyIfNecessary(DependencyDescriptor descriptor, String beanName) {
            Object proxy = delegate.getLazyResolutionProxyIfNecessary(descriptor, beanName)
            if (proxy != null || beanFactory == null || descriptor instanceof FallbackDescriptor || !isTraitField(descriptor)) {
                return proxy
            }
            List<Object> injectionPoint = Arrays.<Object>asList(beanName, descriptor)
            String rememberedName = remembered.get(injectionPoint)
            if (rememberedName != null && beanFactory.containsBean(rememberedName) &&
                    beanFactory.isTypeMatch(rememberedName, descriptor.dependencyType)) {
                // as the injection resolves a field it has resolved before, by the name of the bean it took
                return beanFactory.getBean(rememberedName, descriptor.dependencyType)
            }
            FallbackDescriptor fallback = new FallbackDescriptor(descriptor, beanFactory)
            Set<String> autowiredBeanNames = new LinkedHashSet<String>()
            Object value = beanFactory.doResolveDependency(fallback, beanName, autowiredBeanNames, null)
            if (fallback.fellBack) {
                autowiredBeanNames << CACHE_MANAGER_BEAN_NAME
            }
            if (beanName != null) {
                // as the injection registers the bean it injects, which this answer takes the place of
                for (String autowiredBeanName : autowiredBeanNames) {
                    if (beanFactory.containsBean(autowiredBeanName)) {
                        beanFactory.registerDependentBean(autowiredBeanName, beanName)
                    }
                }
            }
            if (value != null && autowiredBeanNames.size() == 1) {
                String autowiredBeanName = autowiredBeanNames.iterator().next()
                if (beanFactory.containsBean(autowiredBeanName) && beanFactory.isTypeMatch(autowiredBeanName, descriptor.dependencyType)) {
                    remembered.putIfAbsent(injectionPoint, autowiredBeanName)
                }
            }
            return value
        }

        @Override
        void postProcessMergedBeanDefinition(RootBeanDefinition beanDefinition, Class<?> beanType, String beanName) {
        }

        /** Forgets what the bean's fields were given, as the injection forgets its own record of them. */
        @Override
        void resetBeanDefinition(String beanName) {
            remembered.keySet().removeIf { List<Object> injectionPoint -> injectionPoint[0] == beanName }
        }

        @Override
        boolean isAutowireCandidate(BeanDefinitionHolder bdHolder, DependencyDescriptor descriptor) {
            delegate.isAutowireCandidate(bdHolder, descriptor)
        }

        @Override
        boolean isRequired(DependencyDescriptor descriptor) {
            delegate.isRequired(descriptor)
        }

        @Override
        boolean hasQualifier(DependencyDescriptor descriptor) {
            delegate.hasQualifier(descriptor)
        }

        @Override
        String getSuggestedName(DependencyDescriptor descriptor) {
            delegate.getSuggestedName(descriptor)
        }

        @Override
        Object getSuggestedValue(DependencyDescriptor descriptor) {
            delegate.getSuggestedValue(descriptor)
        }

        @Override
        Class<?> getLazyResolutionProxyClass(DependencyDescriptor descriptor, String beanName) {
            delegate.getLazyResolutionProxyClass(descriptor, beanName)
        }

        @Override
        AutowireCandidateResolver cloneIfNecessary() {
            new CandidateResolver(delegate.cloneIfNecessary())
        }
    }

    /** The trait's field, falling back to {@code grailsCacheManager} where Spring cannot choose. */
    @CompileStatic
    private static class FallbackDescriptor extends DependencyDescriptor {

        private final BeanFactory beanFactory

        boolean fellBack

        FallbackDescriptor(DependencyDescriptor original, BeanFactory beanFactory) {
            super(original)
            this.beanFactory = beanFactory
        }

        @Override
        Object resolveNotUnique(ResolvableType type, Map<String, Object> matchingBeans) throws BeansException {
            if (!matchingBeans.containsKey(CACHE_MANAGER_BEAN_NAME)) {
                return super.resolveNotUnique(type, matchingBeans)
            }
            fellBack = true
            Object candidate = matchingBeans.get(CACHE_MANAGER_BEAN_NAME)
            // created here if it is not yet, as Spring creates the candidate it chooses
            candidate instanceof Class ? resolveCandidate(CACHE_MANAGER_BEAN_NAME, CacheManager, beanFactory) : candidate
        }
    }
}

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
import org.springframework.beans.factory.BeanFactory
import org.springframework.beans.factory.BeanFactoryAware
import org.springframework.beans.factory.NoUniqueBeanDefinitionException
import org.springframework.beans.factory.config.BeanDefinitionHolder
import org.springframework.beans.factory.config.BeanFactoryPostProcessor
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory
import org.springframework.beans.factory.config.DependencyDescriptor
import org.springframework.beans.factory.config.SingletonBeanRegistry
import org.springframework.beans.factory.support.AutowireCandidateResolver
import org.springframework.beans.factory.support.DefaultListableBeanFactory
import org.springframework.cache.CacheManager
import org.springframework.cache.support.NoOpCacheManager
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
 * <p>This wraps the bean factory's autowire candidate resolver. When Spring resolves the trait's field,
 * the resolver first runs Spring's own resolution of it, which creates no cache manager that Spring would
 * not create to compare them. Where Spring can choose, the resolver steps aside, and Spring resolves the
 * field as it did: the only cache manager, the one marked primary or the one with the highest priority,
 * judged at that moment, with the objects of the cache managers created by then, the products of factory
 * beans included. Only where Spring would fail because it cannot choose, and {@code grailsCacheManager}
 * is one of the cache managers it cannot choose between, is the field given {@code grailsCacheManager}.
 * Any other injection of a cache manager is left alone.</p>
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
                listable.autowireCandidateResolver = new CandidateResolver(listable.autowireCandidateResolver)
            }
        }
    }

    /** Whether the dependency is the trait's cache manager field. */
    static boolean isTraitField(DependencyDescriptor descriptor) {
        descriptor.field != null && descriptor.field.name == FIELD_NAME && descriptor.field.type == CacheManager &&
                GrailsCacheManagerAware.isAssignableFrom(descriptor.field.declaringClass)
    }

    /**
     * The bean factory's resolver, with the trait's field given {@code grailsCacheManager} where Spring
     * cannot choose.
     */
    @CompileStatic
    static class CandidateResolver implements AutowireCandidateResolver, BeanFactoryAware {

        private final AutowireCandidateResolver delegate

        private ConfigurableListableBeanFactory beanFactory

        CandidateResolver(AutowireCandidateResolver delegate) {
            this.delegate = delegate
        }

        @Override
        void setBeanFactory(BeanFactory beanFactory) throws BeansException {
            this.beanFactory = beanFactory instanceof ConfigurableListableBeanFactory ?
                    (ConfigurableListableBeanFactory) beanFactory : null
            if (delegate instanceof BeanFactoryAware) {
                ((BeanFactoryAware) delegate).beanFactory = beanFactory
            }
        }

        @Override
        Object getSuggestedValue(DependencyDescriptor descriptor) {
            Object suggested = delegate.getSuggestedValue(descriptor)
            if (suggested != null || beanFactory == null || descriptor instanceof Probe || !isTraitField(descriptor)) {
                return suggested
            }
            Probe probe = new Probe(descriptor)
            try {
                beanFactory.resolveDependency(probe, null)
            }
            catch (NoUniqueBeanDefinitionException ignored) {
                // Spring rejects the candidates before it would fall back, as with two marked primary
                return null
            }
            probe.cannotChooseBesideGrails ? beanFactory.getBean(CACHE_MANAGER_BEAN_NAME, CacheManager) : null
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
        Object getLazyResolutionProxyIfNecessary(DependencyDescriptor descriptor, String beanName) {
            delegate.getLazyResolutionProxyIfNecessary(descriptor, beanName)
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

    /**
     * Spring's resolution of the trait's field, which tells whether Spring can choose. It creates the
     * cache managers Spring creates to compare them, those that exist and the products of factory beans
     * that exist, and not the one it would choose, which Spring creates when it resolves the field.
     */
    @CompileStatic
    private static class Probe extends DependencyDescriptor {

        /** Stands for the cache manager Spring would choose, which the probe does not create. */
        private static final CacheManager NOT_CREATED = new NoOpCacheManager()

        boolean cannotChooseBesideGrails

        Probe(DependencyDescriptor original) {
            super(original)
        }

        @Override
        Object resolveNotUnique(ResolvableType type, Map<String, Object> matchingBeans) throws BeansException {
            if (matchingBeans.containsKey(CACHE_MANAGER_BEAN_NAME)) {
                cannotChooseBesideGrails = true
                return NOT_CREATED
            }
            super.resolveNotUnique(type, matchingBeans)
        }

        @Override
        Object resolveCandidate(String beanName, Class<?> requiredType, BeanFactory beanFactory) throws BeansException {
            boolean exists = beanFactory instanceof SingletonBeanRegistry &&
                    ((SingletonBeanRegistry) beanFactory).containsSingleton(beanName)
            exists ? super.resolveCandidate(beanName, requiredType, beanFactory) : NOT_CREATED
        }
    }
}

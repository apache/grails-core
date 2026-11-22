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

import java.lang.ref.Reference
import java.lang.ref.ReferenceQueue
import java.lang.ref.WeakReference
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
 * managers it cannot choose between, it takes {@code grailsCacheManager}, unless that resolves to null.
 * Everywhere else Spring chooses as it did: the only cache manager, the one marked primary or the one
 * with the highest priority, judged at that moment, leaving out the bean being injected. Any other
 * injection of a cache manager is left alone.</p>
 *
 * <p>As Spring's own resolution of the field would, the resolver registers the bean as depending on the
 * cache manager it took, and remembers that cache manager for the injection point Spring resolved,
 * which Spring passes again for the next injection of the bean, as for a prototype's next instance.
 * Spring's field injection remembers its own choice the same way, until the bean's definition is reset;
 * then it resolves the field with a new injection point, which this resolver has not seen. The
 * injection that Spring AOT generates passes a new one every time, and so resolves every time, as it
 * did. Where the cache manager remembered fails, the field is resolved again, as Spring resolves it
 * again.</p>
 *
 * <p>Where the field resolves to nothing, the resolver cannot answer with that, and Spring resolves the
 * field once more. That resolution finds no candidate, as the resolver answers that none is one for the
 * injection point it has just resolved, so nothing is created again and the field stays empty, as with
 * Spring's own single resolution.</p>
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
     * The bean factory's resolver, resolving the trait's field with the fall back to
     * {@code grailsCacheManager}.
     */
    @CompileStatic
    static class CandidateResolver implements AutowireCandidateResolver, BeanFactoryAware {

        private final AutowireCandidateResolver delegate

        private DefaultListableBeanFactory beanFactory

        /**
         * The name of the cache manager taken for each injection point Spring resolved, where Spring would
         * remember it: one bean, of the field's type. Held as long as Spring holds the injection point.
         */
        private final Map<InjectionPointKey, String> remembered = new ConcurrentHashMap<InjectionPointKey, String>()

        private final ReferenceQueue<Object> forgotten = new ReferenceQueue<Object>()

        /**
         * The injection point this thread has just resolved to nothing, which Spring resolves once more:
         * no bean is a candidate for it then, until Spring asks whether it is required, which ends that
         * resolution.
         */
        private final ThreadLocal<WeakReference<DependencyDescriptor>> resolvedToNothing =
                new ThreadLocal<WeakReference<DependencyDescriptor>>()

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
            if (proxy != null || beanFactory == null || descriptor instanceof FallbackDescriptor ||
                    descriptor instanceof RememberedDescriptor || !isTraitField(descriptor)) {
                return proxy
            }
            resolvedToNothing.remove()
            Object shortcut = descriptor.resolveShortcut(beanFactory)
            if (shortcut != null) {
                // as the first step of Spring's resolution, for an injection point resolved in advance
                return shortcut
            }
            forgetCollected()
            InjectionPointKey injectionPoint = new InjectionPointKey(descriptor, null)
            String rememberedName = remembered.get(injectionPoint)
            if (rememberedName != null) {
                try {
                    return keepNothing(descriptor,
                            beanFactory.doResolveDependency(new RememberedDescriptor(descriptor, rememberedName), beanName, null, null))
                }
                catch (BeansException ignored) {
                    // as the injection, which resolves the field again where the bean it remembers fails
                    remembered.remove(injectionPoint, rememberedName)
                }
            }
            FallbackDescriptor fallback = new FallbackDescriptor(descriptor, beanFactory)
            Set<String> autowiredBeanNames = new LinkedHashSet<String>()
            Object value = beanFactory.doResolveDependency(fallback, beanName, autowiredBeanNames, null)
            if (fallback.fellBack) {
                autowiredBeanNames << CACHE_MANAGER_BEAN_NAME
            }
            if (beanName != null && (value != null || descriptor.required)) {
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
                    remembered.put(new InjectionPointKey(descriptor, forgotten), autowiredBeanName)
                }
            }
            return keepNothing(descriptor, value)
        }

        /** Where the resolution found nothing, makes Spring's resolution after it find nothing too. */
        private Object keepNothing(DependencyDescriptor descriptor, Object value) {
            if (value == null) {
                resolvedToNothing.set(new WeakReference<DependencyDescriptor>(descriptor))
            }
            value
        }

        /** Whether the dependency is the one this thread has just resolved to nothing, or Spring's fallback match of it. */
        private boolean resolvedToNothing(DependencyDescriptor descriptor) {
            DependencyDescriptor nothing = resolvedToNothing.get()?.get()
            nothing != null && (nothing.is(descriptor) ||
                    (descriptor.fallbackMatchAllowed() && descriptor.field != null && descriptor.field == nothing.field &&
                            descriptor.dependencyType == nothing.dependencyType))
        }

        /** Drops what was remembered for injection points Spring no longer holds. */
        private void forgetCollected() {
            Reference<?> reference
            while ((reference = forgotten.poll()) != null) {
                remembered.remove(reference)
            }
        }

        @Override
        boolean isAutowireCandidate(BeanDefinitionHolder bdHolder, DependencyDescriptor descriptor) {
            !resolvedToNothing(descriptor) && delegate.isAutowireCandidate(bdHolder, descriptor)
        }

        @Override
        boolean isRequired(DependencyDescriptor descriptor) {
            if (resolvedToNothing.get()?.get()?.is(descriptor)) {
                // the end of Spring's resolution after the resolver's, which found no candidate
                resolvedToNothing.remove()
            }
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

    /**
     * The identity of an injection point Spring passed, held weakly, so that an injection point is
     * remembered as long as Spring holds it, and one equal to it is not taken for it.
     */
    @CompileStatic
    private static final class InjectionPointKey extends WeakReference<Object> {

        private final int hash

        InjectionPointKey(DependencyDescriptor injectionPoint, ReferenceQueue<Object> queue) {
            super(injectionPoint, queue)
            this.hash = System.identityHashCode(injectionPoint)
        }

        @Override
        int hashCode() {
            hash
        }

        @Override
        boolean equals(Object other) {
            if (this.is(other)) {
                return true
            }
            if (!(other instanceof InjectionPointKey)) {
                return false
            }
            Object injectionPoint = get()
            injectionPoint != null && injectionPoint.is(((InjectionPointKey) other).get())
        }
    }

    /** The trait's field, resolved to the cache manager remembered for it, as Spring resolves a shortcut. */
    @CompileStatic
    private static class RememberedDescriptor extends DependencyDescriptor {

        private final String beanName

        RememberedDescriptor(DependencyDescriptor original, String beanName) {
            super(original)
            this.beanName = beanName
        }

        @Override
        Object resolveShortcut(BeanFactory beanFactory) throws BeansException {
            beanFactory.getBean(beanName, dependencyType)
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
            Object candidate = matchingBeans.get(CACHE_MANAGER_BEAN_NAME)
            // created here if it is not yet, as Spring creates the candidate it chooses
            Object grailsCacheManager = candidate instanceof Class ?
                    resolveCandidate(CACHE_MANAGER_BEAN_NAME, CacheManager, beanFactory) : candidate
            // Spring's null bean, which equals null: no cache manager to fall back to
            if (grailsCacheManager == null || grailsCacheManager.equals(null)) {
                return super.resolveNotUnique(type, matchingBeans)
            }
            fellBack = true
            grailsCacheManager
        }
    }
}

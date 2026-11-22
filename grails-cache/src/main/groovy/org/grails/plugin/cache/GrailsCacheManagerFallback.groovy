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

import java.lang.annotation.Annotation
import java.lang.reflect.AnnotatedElement
import java.lang.reflect.Field
import java.lang.reflect.Member
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

import groovy.transform.CompileStatic

import org.springframework.aot.AotDetector
import org.springframework.beans.BeansException
import org.springframework.beans.PropertyValues
import org.springframework.beans.TypeConverter
import org.springframework.beans.factory.BeanFactory
import org.springframework.beans.factory.BeanFactoryAware
import org.springframework.beans.factory.InjectionPoint
import org.springframework.beans.factory.UnsatisfiedDependencyException
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.AutowiredAnnotationBeanPostProcessor
import org.springframework.beans.factory.config.BeanDefinition
import org.springframework.beans.factory.config.BeanDefinitionHolder
import org.springframework.beans.factory.config.BeanFactoryPostProcessor
import org.springframework.beans.factory.config.BeanPostProcessor
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory
import org.springframework.beans.factory.config.DependencyDescriptor
import org.springframework.beans.factory.config.InstantiationAwareBeanPostProcessor
import org.springframework.beans.factory.support.AutowireCandidateResolver
import org.springframework.beans.factory.support.DefaultListableBeanFactory
import org.springframework.beans.factory.support.MergedBeanDefinitionPostProcessor
import org.springframework.beans.factory.support.RootBeanDefinition
import org.springframework.cache.CacheManager
import org.springframework.core.Ordered
import org.springframework.core.PriorityOrdered
import org.springframework.core.ResolvableType
import org.springframework.util.ReflectionUtils

/**
 * Gives a cache-aware class the cache manager named {@code grailsCacheManager} where Spring cannot choose
 * the cache manager it injects, and leaves Spring's choice as it is everywhere else.
 *
 * <p>{@link GrailsCacheManagerAware} injects a {@code CacheManager} by type. Beside a second cache
 * manager, such as the one the Spring Security plugin defines when {@code cacheUsers} is set, Spring
 * cannot choose by type, and falls back to the bean named like the field. A trait's field is renamed in
 * the class that implements it, so no bean matched, and the application failed to start.</p>
 *
 * <p>This injects the trait's field in place of Spring's {@code AutowiredAnnotationBeanPostProcessor},
 * which leaves alone a member another post-processor has registered on the bean definition, as it does for
 * {@code @Resource}. A class has the field once for each class of its hierarchy that implements the trait:
 * the cache transformations leave it to the first, but a subclass may implement it again. A post-processor
 * added first registers on each bean definition every copy of the field and every member Spring injects
 * after the first: the autowired methods of the class that has it, and the fields and methods of its
 * subclasses. A post-processor ordered right after Spring's then injects them, once Spring has injected
 * the members before them: each copy of the field, and after it, with an injection of Spring's own, the
 * members up to the next copy, so that each member is injected in the order Spring injected it, and an
 * autowired method sees the field of its class set.
 * It injects the field as Spring's does: with one resolution of the field by the bean factory, for the
 * bean being injected, registering the bean as depending on what it took, remembering that for the bean's
 * next injection until the bean's definition is reset, and resolving it again once where what it remembers
 * fails. The one change is in that resolution: where Spring would fail because it cannot choose, and the
 * bean named {@code grailsCacheManager}, or a bean with that alias, is one of the cache managers it cannot
 * choose between, it takes that bean, unless it is Spring's null bean. Everywhere else Spring chooses as
 * it did. Any other injection of a cache manager is left alone.</p>
 *
 * <p>The injection that Spring's AOT processing generates injects every member, the field included, in
 * that order, whatever is registered on the bean definition. Where the application runs that generated
 * code, the bean factory's autowire candidate resolver answers the field's resolution instead, with the
 * same resolution, which falls back where Spring cannot choose; the generated code resolves the field each
 * time, so nothing is remembered. One difference remains there: where that resolution finds no cache
 * manager, Spring resolves the field once more, which creates again only a prototype cache manager whose
 * factory method returns null.</p>
 *
 * @since 8.0.1
 */
@CompileStatic
class GrailsCacheManagerFallback implements BeanFactoryPostProcessor {

    static final String CACHE_MANAGER_BEAN_NAME = 'grailsCacheManager'

    /** The bean that injects the trait's field, among Spring's own post-processors. */
    static final String INJECTION_BEAN_NAME = 'org.grails.plugin.cache.internalTraitFieldInjection'

    /**
     * The annotations whose members Spring's own post-processors that run before its autowiring, for
     * {@code @Resource} and for persistence, take before it; a member with one is left to them.
     */
    private static final Set<String> TAKEN_BEFORE_AUTOWIRING = ['jakarta.annotation.Resource', 'jakarta.ejb.EJB',
            'jakarta.xml.ws.WebServiceRef', 'jakarta.persistence.PersistenceContext', 'jakarta.persistence.PersistenceUnit'] as Set<String>

    /** The name the trait's field has in each class that implements it. */
    static final String FIELD_NAME = "${GrailsCacheManagerAware.name.replace('.', '_')}__${CACHE_MANAGER_BEAN_NAME}".toString()

    @Override
    void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        if (beanFactory instanceof DefaultListableBeanFactory) {
            DefaultListableBeanFactory listable = (DefaultListableBeanFactory) beanFactory
            if (AotDetector.useGeneratedArtifacts()) {
                // the generated code injects the bean, and resolves the field through the resolver
                if (!(listable.autowireCandidateResolver instanceof GeneratedInjectionFallback)) {
                    listable.autowireCandidateResolver = new GeneratedInjectionFallback(listable.autowireCandidateResolver)
                }
                return
            }
            if (!listable.beanPostProcessors.any { BeanPostProcessor processor -> processor instanceof TraitFieldRegistrar }) {
                // added before Spring registers its own, so that it registers the field on the bean definition first
                listable.addBeanPostProcessor(new TraitFieldRegistrar(listable))
            }
            if (!listable.containsBeanDefinition(INJECTION_BEAN_NAME)) {
                RootBeanDefinition injection = new RootBeanDefinition(TraitFieldInjection)
                injection.role = BeanDefinition.ROLE_INFRASTRUCTURE
                listable.registerBeanDefinition(INJECTION_BEAN_NAME, injection)
            }
        }
    }

    /** The trait's cache manager field in the class, or null if the class does not have it. */
    static Field traitField(Class<?> type) {
        Field field = type == null ? null : ReflectionUtils.findField(type, FIELD_NAME, CacheManager)
        isTraitField(field) ? field : null
    }

    /** Each copy of the trait's cache manager field the class has, a superclass's first. */
    static List<Field> traitFields(Class<?> type) {
        List<Field> fields = []
        for (Class<?> declaring = type; declaring != null && declaring != Object; declaring = declaring.superclass) {
            Field field = declaring.declaredFields.find { Field candidate -> candidate.name == FIELD_NAME }
            if (isTraitField(field)) {
                fields.add(0, field)
            }
        }
        fields
    }

    /** Whether the field is a copy of the trait's cache manager field, in the class that declares it. */
    static boolean isTraitField(Field field) {
        field != null && field.type == CacheManager && GrailsCacheManagerAware.isAssignableFrom(field.declaringClass) &&
                field.isAnnotationPresent(Autowired)
    }

    /**
     * Registers each copy of the trait's field, and the members Spring injects after the first, on each bean
     * definition that has it, before Spring's injection sees them.
     */
    @CompileStatic
    static class TraitFieldRegistrar implements MergedBeanDefinitionPostProcessor {

        private final DefaultListableBeanFactory beanFactory

        /** The injection of each bean's copies of the field, as Spring keeps the injection metadata of each bean. */
        final Map<String, TraitFieldInjections> elements = new ConcurrentHashMap<String, TraitFieldInjections>()

        /**
         * Spring's injection, of the members it injects after each copy of the field up to the next: the
         * first of them for the members after the first copy, and so on.
         */
        final List<AutowiredAnnotationBeanPostProcessor> afterFields = new CopyOnWriteArrayList<AutowiredAnnotationBeanPostProcessor>()

        TraitFieldRegistrar(DefaultListableBeanFactory beanFactory) {
            this.beanFactory = beanFactory
        }

        @Override
        void postProcessMergedBeanDefinition(RootBeanDefinition beanDefinition, Class<?> beanType, String beanName) {
            List<Field> fields = traitFields(beanType).findAll { Field field -> !beanDefinition.isExternallyManagedConfigMember(field) }
            if (fields.empty) {
                return
            }
            // the members Spring injects, in the order it injects them, as it registers them on a definition
            RootBeanDefinition all = new RootBeanDefinition()
            afterField(0).postProcessMergedBeanDefinition(all, beanType, beanName)
            List<Member> members = new ArrayList<Member>(all.externallyManagedConfigMembers)
            List<Field> injected = fields.findAll { Field field -> field in members }.sort { Field field -> members.indexOf(field) }
            List<TraitFieldInjection.Step> steps = []
            injected.eachWithIndex { Field field, int index ->
                int end = index + 1 < injected.size() ? members.indexOf(injected[index + 1]) : members.size()
                List<Member> after = members.subList(members.indexOf(field) + 1, end).findAll { Member member ->
                    !beanDefinition.isExternallyManagedConfigMember(member) && !takenBeforeAutowiring(member)
                }
                AutowiredAnnotationBeanPostProcessor afterThisField = null
                if (!after.empty) {
                    // Spring's injection of only those, for this bean
                    afterThisField = afterField(index)
                    RootBeanDefinition onlyAfter = new RootBeanDefinition()
                    members.each { Member member ->
                        if (!(member in after)) {
                            onlyAfter.registerExternallyManagedConfigMember(member)
                        }
                    }
                    afterThisField.postProcessMergedBeanDefinition(onlyAfter, beanType, beanName)
                }
                Autowired autowired = field.getAnnotation(Autowired)
                steps << new TraitFieldInjection.Step(new TraitFieldElement(field, autowired.required(), beanFactory), afterThisField)
                beanDefinition.registerExternallyManagedConfigMember(field)
                after.each { Member member -> beanDefinition.registerExternallyManagedConfigMember(member) }
            }
            if (!steps.empty) {
                elements.put(beanName, new TraitFieldInjections(beanType, steps))
            }
        }

        @Override
        void resetBeanDefinition(String beanName) {
            elements.remove(beanName)
            afterFields.each { AutowiredAnnotationBeanPostProcessor afterField -> afterField.resetBeanDefinition(beanName) }
        }

        /** Spring's injection of the members after the copy of the field at that place in a class's hierarchy. */
        private synchronized AutowiredAnnotationBeanPostProcessor afterField(int index) {
            while (afterFields.size() <= index) {
                AutowiredAnnotationBeanPostProcessor afterField = new AutowiredAnnotationBeanPostProcessor()
                afterField.beanFactory = beanFactory
                afterFields << afterField
            }
            afterFields[index]
        }

        /** Whether a post-processor of Spring's that sees the definition after this one takes the member first. */
        private static boolean takenBeforeAutowiring(Member member) {
            ((AnnotatedElement) member).annotations.any { Annotation annotation ->
                annotation.annotationType().name in TAKEN_BEFORE_AUTOWIRING
            }
        }
    }

    /** The injection of one bean's copies of the trait's field, each followed by the members after it. */
    @CompileStatic
    static class TraitFieldInjections {

        /** The class the bean's injection was found for. */
        final Class<?> beanType

        final List<TraitFieldInjection.Step> steps

        TraitFieldInjections(Class<?> beanType, List<TraitFieldInjection.Step> steps) {
            this.beanType = beanType
            this.steps = steps
        }
    }

    /**
     * Injects the trait's field of each bean whose definition {@link TraitFieldRegistrar} registered it on,
     * ordered right after Spring's {@code AutowiredAnnotationBeanPostProcessor}, which injects the bean's
     * other fields first.
     */
    @CompileStatic
    static class TraitFieldInjection implements MergedBeanDefinitionPostProcessor, InstantiationAwareBeanPostProcessor,
            PriorityOrdered, BeanFactoryAware {

        private DefaultListableBeanFactory beanFactory

        @Override
        void setBeanFactory(BeanFactory beanFactory) throws BeansException {
            this.beanFactory = beanFactory instanceof DefaultListableBeanFactory ? (DefaultListableBeanFactory) beanFactory : null
        }

        /** Right after {@code AutowiredAnnotationBeanPostProcessor}, at {@code Ordered.LOWEST_PRECEDENCE - 2}. */
        @Override
        int getOrder() {
            Ordered.LOWEST_PRECEDENCE - 1
        }

        /** Among the post-processors Spring keeps after the others, as its own injection is. */
        @Override
        void postProcessMergedBeanDefinition(RootBeanDefinition beanDefinition, Class<?> beanType, String beanName) {
        }

        @Override
        PropertyValues postProcessProperties(PropertyValues pvs, Object bean, String beanName) throws BeansException {
            TraitFieldRegistrar registrar = (TraitFieldRegistrar) beanFactory?.beanPostProcessors?.find { BeanPostProcessor processor ->
                processor instanceof TraitFieldRegistrar
            }
            TraitFieldInjections injections = registrar == null || beanName == null ? null : registrar.elements.get(beanName)
            // for another class Spring builds its injection again and injects every member itself
            if (injections != null && bean.getClass() == injections.beanType) {
                for (Step step : injections.steps) {
                    step.field.inject(bean, beanName)
                    step.afterField?.postProcessProperties(pvs, bean, beanName)
                }
            }
            return pvs
        }

        /** A copy of the trait's field, and Spring's injection of the members after it, if there are any. */
        @CompileStatic
        static class Step {

            final TraitFieldElement field

            final AutowiredAnnotationBeanPostProcessor afterField

            Step(TraitFieldElement field, AutowiredAnnotationBeanPostProcessor afterField) {
                this.field = field
                this.afterField = afterField
            }
        }
    }

    /**
     * The injection of the trait's field of one bean, as {@code AutowiredAnnotationBeanPostProcessor}
     * injects a field: resolved once, then by the name of the bean it took, and once again where that
     * fails.
     */
    @CompileStatic
    static class TraitFieldElement {

        final Field field

        private final boolean required

        private final DefaultListableBeanFactory beanFactory

        private volatile boolean cached

        private volatile Object cachedFieldValue

        TraitFieldElement(Field field, boolean required, DefaultListableBeanFactory beanFactory) {
            this.field = field
            this.required = required
            this.beanFactory = beanFactory
        }

        void inject(Object bean, String beanName) {
            Object value
            if (cached) {
                try {
                    value = resolveCachedArgument(beanName, cachedFieldValue)
                }
                catch (BeansException ignored) {
                    // as the injection, which resolves the field again where what it remembers fails
                    cached = false
                    value = resolveFieldValue(bean, beanName)
                }
            }
            else {
                value = resolveFieldValue(bean, beanName)
            }
            if (value != null) {
                ReflectionUtils.makeAccessible(field)
                field.set(bean, value)
            }
        }

        private Object resolveFieldValue(Object bean, String beanName) {
            FallbackDescriptor descriptor = new FallbackDescriptor(field, required, beanFactory)
            descriptor.containingClass = bean.getClass()
            Set<String> autowiredBeanNames = new LinkedHashSet<String>(2)
            TypeConverter typeConverter = beanFactory.typeConverter
            Object value
            try {
                value = beanFactory.resolveDependency(descriptor, beanName, autowiredBeanNames, typeConverter)
            }
            catch (BeansException e) {
                throw new UnsatisfiedDependencyException(null, beanName, new InjectionPoint(field), e)
            }
            if (descriptor.fellBackTo != null) {
                autowiredBeanNames << descriptor.fellBackTo
            }
            synchronized (this) {
                if (!cached) {
                    if (value != null || required) {
                        Object cachedValue = descriptor
                        registerDependentBeans(beanName, autowiredBeanNames)
                        if (value != null && autowiredBeanNames.size() == 1) {
                            String autowiredBeanName = autowiredBeanNames.iterator().next()
                            if (beanFactory.containsBean(autowiredBeanName) && beanFactory.isTypeMatch(autowiredBeanName, field.type)) {
                                cachedValue = new ShortcutDescriptor(descriptor, autowiredBeanName)
                            }
                        }
                        cachedFieldValue = cachedValue
                        cached = true
                    }
                    else {
                        cachedFieldValue = null
                    }
                }
            }
            value
        }

        private Object resolveCachedArgument(String beanName, Object cachedArgument) {
            cachedArgument instanceof DependencyDescriptor ?
                    beanFactory.resolveDependency((DependencyDescriptor) cachedArgument, beanName, null, null) : cachedArgument
        }

        private void registerDependentBeans(String beanName, Set<String> autowiredBeanNames) {
            if (beanName != null) {
                for (String autowiredBeanName : autowiredBeanNames) {
                    if (beanFactory.containsBean(autowiredBeanName)) {
                        beanFactory.registerDependentBean(autowiredBeanName, beanName)
                    }
                }
            }
        }
    }

    /**
     * The bean factory's autowire candidate resolver, answering the resolution of the trait's field by the
     * injection that Spring's AOT processing generates, with the resolution that falls back to
     * {@code grailsCacheManager}. That code asks for the field each time, so the answer is not remembered.
     */
    @CompileStatic
    static class GeneratedInjectionFallback implements AutowireCandidateResolver, BeanFactoryAware {

        private final AutowireCandidateResolver delegate

        private DefaultListableBeanFactory beanFactory

        GeneratedInjectionFallback(AutowireCandidateResolver delegate) {
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
            Field field = descriptor.field
            if (proxy != null || beanFactory == null || descriptor instanceof FallbackDescriptor || !isTraitField(field)) {
                return proxy
            }
            FallbackDescriptor fallback = new FallbackDescriptor(descriptor, beanFactory)
            Set<String> autowiredBeanNames = new LinkedHashSet<String>(1)
            // the generated code's resolution, but for the descriptor; it registers the beans that resolution took
            Object value = beanFactory.doResolveDependency(fallback, beanName, autowiredBeanNames, beanFactory.typeConverter)
            if (fallback.fellBackTo != null) {
                autowiredBeanNames << fallback.fellBackTo
            }
            if (beanName != null) {
                for (String autowiredBeanName : autowiredBeanNames) {
                    if (beanFactory.containsBean(autowiredBeanName)) {
                        beanFactory.registerDependentBean(autowiredBeanName, beanName)
                    }
                }
            }
            value
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
            new GeneratedInjectionFallback(delegate.cloneIfNecessary())
        }
    }

    /** The trait's field resolved by the name of the bean it took before, as Spring's shortcut is. */
    @CompileStatic
    private static class ShortcutDescriptor extends DependencyDescriptor {

        private final String shortcut

        ShortcutDescriptor(DependencyDescriptor original, String shortcut) {
            super(original)
            this.shortcut = shortcut
        }

        @Override
        Object resolveShortcut(BeanFactory beanFactory) throws BeansException {
            beanFactory.getBean(shortcut, dependencyType)
        }
    }

    /** The trait's field, falling back to {@code grailsCacheManager} where Spring cannot choose. */
    @CompileStatic
    private static class FallbackDescriptor extends DependencyDescriptor {

        private final transient DefaultListableBeanFactory beanFactory

        /** The name of the bean the resolution fell back to, if it did. */
        String fellBackTo

        FallbackDescriptor(Field field, boolean required, DefaultListableBeanFactory beanFactory) {
            super(field, required)
            this.beanFactory = beanFactory
        }

        /** The descriptor the generated code resolves, with the class it is resolved for. */
        FallbackDescriptor(DependencyDescriptor original, DefaultListableBeanFactory beanFactory) {
            super(original)
            this.beanFactory = beanFactory
        }

        /** Looks a bean up by the field's name first, as Spring does for a plain field's descriptor. */
        @Override
        boolean usesStandardBeanLookup() {
            true
        }

        @Override
        Object resolveNotUnique(ResolvableType type, Map<String, Object> matchingBeans) throws BeansException {
            String name = matchingBeans.keySet().find { String candidate ->
                candidate == CACHE_MANAGER_BEAN_NAME || CACHE_MANAGER_BEAN_NAME in beanFactory.getAliases(candidate)
            }
            if (name == null) {
                return super.resolveNotUnique(type, matchingBeans)
            }
            Object candidate = matchingBeans.get(name)
            // created here if it is not yet, as Spring creates the candidate it chooses
            Object grailsCacheManager = candidate instanceof Class ? resolveCandidate(name, dependencyType, beanFactory) : candidate
            // Spring's null bean, which equals null: no cache manager to fall back to
            if (grailsCacheManager == null || grailsCacheManager.equals(null)) {
                return super.resolveNotUnique(type, matchingBeans)
            }
            fellBackTo = name
            grailsCacheManager
        }
    }
}

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

import java.util.function.Supplier

import jakarta.annotation.Priority

import org.springframework.beans.BeansException
import org.springframework.beans.factory.FactoryBean
import org.springframework.beans.factory.InjectionPoint
import org.springframework.beans.factory.NoUniqueBeanDefinitionException
import org.springframework.beans.factory.UnsatisfiedDependencyException
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.AutowiredAnnotationBeanPostProcessor
import org.springframework.beans.factory.aot.AutowiredFieldValueResolver
import org.springframework.beans.factory.config.BeanDefinition
import org.springframework.beans.factory.config.BeanDefinitionCustomizer
import org.springframework.beans.factory.config.DependencyDescriptor
import org.springframework.beans.factory.support.AbstractBeanDefinition
import org.springframework.beans.factory.support.AutowireCandidateResolver
import org.springframework.beans.factory.support.DefaultListableBeanFactory
import org.springframework.beans.factory.support.RegisteredBean
import org.springframework.beans.factory.support.RootBeanDefinition
import org.springframework.cache.CacheManager
import org.springframework.cache.concurrent.ConcurrentMapCacheManager
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.AnnotationConfigUtils
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Scope
import org.springframework.context.support.GenericApplicationContext
import spock.lang.Specification

import grails.plugin.cache.CustomCacheKeyGenerator
import grails.plugin.cache.GrailsCacheKeyGenerator

/**
 * Which cache manager a cache-aware class is injected with, with the fallback to {@code grailsCacheManager},
 * against the oracle of 8.0.0: the same class, in the same context, without it.
 *
 * <p>The matrix crosses the ways a bean named {@code grailsCacheManager} can be defined with none, one
 * or two other cache managers, each defined in one of the ways a bean is chosen or passed over for
 * injection. One way to define {@code grailsCacheManager} creates it from the cache-aware bean, which so
 * has a cache manager injected while {@code grailsCacheManager} is being created, and another makes it
 * cache-aware itself, which Spring leaves out of its own candidates. Of the other cache
 * managers, some have a priority Spring sees only on their object: one declared as a plain
 * {@code CacheManager}, the product of a factory bean that names only {@code CacheManager} as its type,
 * and one the cache-aware bean's key generator creates while that bean is injected; another is a factory
 * bean that makes a new cache manager each time it is asked. The one deliberate
 * difference: where Spring could not choose, which failed startup, the class gets
 * {@code grailsCacheManager}, unless another cache manager is preferred, as primary or by priority, where
 * Spring rejects the tie itself. Otherwise the choice, and every failure, is 8.0.0's: what the cache-aware
 * bean, three instances of a prototype cache-aware bean and a cache-aware {@code grailsCacheManager} get,
 * the beans the cache-aware bean is registered as depending on, and how many of each cache manager are
 * created, other than {@code grailsCacheManager}.</p>
 */
class GrailsCacheManagerFallbackMatrixSpec extends Specification {

    private static final String NAMED = GrailsCacheManagerFallback.CACHE_MANAGER_BEAN_NAME

    private static final List<String> NAMED_KINDS = ['absent', 'plain', 'primary', 'lazy', 'notCandidate', 'wrongType', 'cyclic',
            'cacheAware', 'fallback', 'notDefault', 'priority', 'nullBean']

    private static final List<String> OTHER_KINDS = ['plain', 'primary', 'lazy', 'notCandidate', 'fallback', 'notDefault',
            'priority', 'priorityOnObject', 'factoryBean', 'createdByKeyGenerator', 'prototypeFactoryBean']

    private static final List<String> PREFERRED_KINDS = ['primary', 'priority', 'priorityOnObject', 'factoryBean',
            'createdByKeyGenerator']

    void 'the fallback applies to the field the trait has in a class that implements it'() {
        expect:
        CacheAware.declaredFields*.name.contains(GrailsCacheManagerFallback.FIELD_NAME)
        GrailsCacheManagerFallback.isTraitField(new DependencyDescriptor(
                CacheAware.getDeclaredField(GrailsCacheManagerFallback.FIELD_NAME), false))
        !GrailsCacheManagerFallback.isTraitField(new DependencyDescriptor(OwnInjection.getDeclaredField('someCacheManager'), false))
    }

    void 'the cache manager a cache-aware class gets is 8.0.0\'s, except where it failed with none preferred'() {
        given:
        List<Map<String, String>> rows = arrangements()
        assert rows.size() == NAMED_KINDS.size() * (1 + OTHER_KINDS.size() + OTHER_KINDS.size() * OTHER_KINDS.size())

        expect: 'every arrangement, each in a context of its own, with the fallback and without it'
        List<String> mismatches = []
        rows.each { Map<String, String> row ->
            Map<String, Object> was = run(row, false)
            Map<String, Object> now = run(row, true)
            boolean namedUsable = row[NAMED] in ['plain', 'primary', 'lazy', 'cacheAware', 'fallback', 'priority']
            boolean anotherPreferred = row.any { String name, String kind -> name != NAMED && kind in PREFERRED_KINDS }
            // a context that does not start has failed before the cache-aware bean, where it cannot fall back
            boolean fallsBack = was.failed && !was.failedAtStartup && namedUsable && !anotherPreferred
            Object expected = fallsBack ? NAMED : (was.failed ? 'failed' : was.chosen)
            Object got = now.failed ? 'failed' : now.chosen
            Map<String, Integer> wasCreated = ((List<String>) was.created).countBy { it }
            String overCreated = ((List<String>) now.created).countBy { it }.find { String name, Integer count ->
                count > (wasCreated[name] ?: 0) + (fallsBack && name == NAMED ? 1 : 0)
            }?.key
            // the prototypes as in 8.0.0, and where 8.0.0 failed before them, as the probe's own resolution
            Map<String, Integer> prototypesMayCreate = ((fallsBack ? now.created : was.createdByPrototypes) ?: []).countBy { it }
            String overCreatedByPrototypes = ((List<String>) (now.createdByPrototypes ?: [])).countBy { it }
                    .find { String name, Integer count -> count > (prototypesMayCreate[name] ?: 0) }?.key
            if (got != expected) {
                mismatches << "${row}: got ${got}, expected ${expected} (8.0.0: ${was.failed ? 'failed' : was.chosen})".toString()
            }
            else if (overCreated) {
                mismatches << "${row}: created ${now.created}, where 8.0.0 created ${was.created}".toString()
            }
            else if (overCreatedByPrototypes) {
                mismatches << "${row}: prototypes created ${now.createdByPrototypes}, where 8.0.0 created ${was.createdByPrototypes}".toString()
            }
            else if (!now.failed && now.prototypes != (fallsBack ? [NAMED] * 3 : was.prototypes)) {
                mismatches << "${row}: prototypes got ${now.prototypes}, where in 8.0.0 they got ${was.prototypes}".toString()
            }
            else if (!now.failed && !was.failed && now.self != was.self) {
                mismatches << "${row}: grailsCacheManager got ${now.self}, where in 8.0.0 it got ${was.self}".toString()
            }
            else if (!now.failed && (fallsBack ? ((List<String>) now.dependencies - ['customCacheKeyGenerator']) != [NAMED] :
                    now.dependencies != was.dependencies)) {
                mismatches << "${row}: depends on ${now.dependencies}, where 8.0.0 depended on ${was.dependencies}".toString()
            }
        }
        mismatches == []
    }

    void 'cache managers registered as singletons, without a bean definition, are judged as Spring judges them'() {
        given:
        CacheManager preferred = new HighPriorityCacheManager()
        GenericApplicationContext besidePriority = context([(NAMED): 'plain'], [:], [], true) { GenericApplicationContext it ->
            it.beanFactory.registerSingleton('preferred', preferred)
        }
        GenericApplicationContext besidePlain = context([(NAMED): 'plain'], [:], [], true) { GenericApplicationContext it ->
            it.beanFactory.registerSingleton('other', new ConcurrentMapCacheManager())
        }

        expect: 'the one with a priority, as Spring chooses it, and grailsCacheManager where Spring cannot choose'
        besidePriority.getBean('probe', CacheAware).grailsCacheManager.is(preferred)
        besidePlain.getBean('probe', CacheAware).grailsCacheManager.is(besidePlain.getBean(NAMED))

        cleanup:
        besidePriority?.close()
        besidePlain?.close()
    }

    void 'a factory bean of new cache managers makes one for the injection, as in 8.0.0'() {
        given: 'such a factory created first, beside a grailsCacheManager marked primary'
        Map<String, Object> was = run([factory: 'prototypeFactoryBean', (NAMED): 'primary'], false)
        Map<String, Object> now = run([factory: 'prototypeFactoryBean', (NAMED): 'primary'], true)

        expect: 'it is asked once for the injection, with the fallback as without it, and grailsCacheManager is injected'
        ((List<String>) was.created).count('factory') == 1
        ((List<String>) now.created).count('factory') == 1
        now.chosen == NAMED && was.chosen == NAMED
    }

    void 'prototype beans resolve their cache manager once, as in 8.0.0, so a factory bean of new cache managers is asked once'() {
        given: 'such a factory created first, beside a grailsCacheManager marked primary, and three prototype cache-aware beans'
        Map<String, Object> was = run([factory: 'prototypeFactoryBean', (NAMED): 'primary'], false)
        Map<String, Object> now = run([factory: 'prototypeFactoryBean', (NAMED): 'primary'], true)

        expect: 'the factory is asked once for the first prototype, then the choice is reused'
        was.prototypes == [NAMED] * 3 && now.prototypes == [NAMED] * 3
        ((List<String>) now.createdByPrototypes).count('factory') == ((List<String>) was.createdByPrototypes).count('factory')
        ((List<String>) now.createdByPrototypes).count('factory') == 1
    }

    void 'prototype beans given grailsCacheManager where Spring cannot choose keep it, and create nothing more'() {
        given: 'a factory bean of new cache managers and grailsCacheManager, which Spring cannot choose between'
        Map<String, Object> now = run([factory: 'prototypeFactoryBean', (NAMED): 'plain'], true)

        expect: 'the first prototype asks the factory once, and the rest reuse the choice'
        now.chosen == NAMED
        now.prototypes == [NAMED] * 3
        ((List<String>) now.createdByPrototypes).count('factory') == 1
    }

    void 'a prototype bean registered again resolves its cache manager afresh, as in 8.0.0, where #arrangement#where'() {
        when: 'a prototype is injected, a cache manager marked primary is added, and the prototype is registered again'
        List<String> was = copied ? reloadedWithCopiedResolver(row, false) : reloaded(row, false)
        List<String> now = copied ? reloadedWithCopiedResolver(row, true) : reloaded(row, true)

        then: 'the choice is kept until the prototype is registered again, and then made afresh'
        now == expected

        and: 'as in 8.0.0, which kept its choice the same way; where it could not choose, it kept nothing to keep'
        was == (row.first == 'plain' ? ['failed', 'preferred', 'preferred'] : expected)

        where:
        arrangement                                 | row                                | copied
        'Spring chooses grailsCacheManager'         | [(NAMED): 'plain']                 | false
        'it is grailsCacheManager by the fallback'  | [(NAMED): 'plain', first: 'plain'] | false
        'Spring chooses grailsCacheManager'         | [(NAMED): 'plain']                 | true
        'it is grailsCacheManager by the fallback'  | [(NAMED): 'plain', first: 'plain'] | true
        expected = [NAMED, NAMED, 'preferred']
        where = copied ? ', in another bean factory with a copy of the resolver' : ''
    }

    /**
     * As {@link #reloaded}, in a bean factory of its own, with its own injection and a copy of the context's
     * resolver, as {@code cloneIfNecessary} makes one for another bean factory.
     */
    private List<String> reloadedWithCopiedResolver(Map<String, String> row, boolean withFallback) {
        Map<String, CacheManager> instances = [:]
        GenericApplicationContext context = context([:], instances, [], withFallback)
        DefaultListableBeanFactory factory = new DefaultListableBeanFactory()
        factory.autowireCandidateResolver = context.defaultListableBeanFactory.autowireCandidateResolver.cloneIfNecessary()
        AutowiredAnnotationBeanPostProcessor injection = new AutowiredAnnotationBeanPostProcessor()
        injection.beanFactory = factory
        factory.addBeanPostProcessor(injection)
        Closure<RootBeanDefinition> cacheManager = { String name, boolean primary ->
            RootBeanDefinition definition = new RootBeanDefinition(CacheManager, { ->
                CacheManager instance = new ConcurrentMapCacheManager()
                instances[name] = instance
                instance
            } as Supplier<CacheManager>)
            definition.primary = primary
            definition
        }
        Closure<RootBeanDefinition> prototype = { ->
            RootBeanDefinition definition = new RootBeanDefinition(CacheAware)
            definition.scope = BeanDefinition.SCOPE_PROTOTYPE
            definition
        }
        row.keySet().each { String name -> factory.registerBeanDefinition(name, cacheManager(name, false)) }
        factory.registerBeanDefinition('prototype', prototype())
        Closure<String> inject = { ->
            try {
                nameOf(((CacheAware) factory.getBean('prototype')).grailsCacheManager, instances)
            }
            catch (BeansException ignored) {
                'failed'
            }
        }
        try {
            assert (factory.autowireCandidateResolver instanceof GrailsCacheManagerFallback.CandidateResolver) == withFallback
            List<String> got = [inject()]
            factory.registerBeanDefinition('preferred', cacheManager('preferred', true))
            got << inject()
            factory.registerBeanDefinition('prototype', prototype())
            got << inject()
            return got
        }
        finally {
            factory.destroySingletons()
            context.close()
        }
    }

    /** What a prototype gets: at first, after a primary cache manager is added, and after it is registered again. */
    private List<String> reloaded(Map<String, String> row, boolean withFallback) {
        Map<String, CacheManager> instances = [:]
        GenericApplicationContext context = context(row, instances, [], withFallback)
        Closure<String> inject = { ->
            try {
                nameOf(((CacheAware) context.getBean('prototype')).grailsCacheManager, instances)
            }
            catch (BeansException ignored) {
                'failed'
            }
        }
        try {
            List<String> got = [inject()]
            context.registerBean('preferred', CacheManager, { ->
                CacheManager instance = new ConcurrentMapCacheManager()
                instances.preferred = instance
                instance
            } as Supplier<CacheManager>, { BeanDefinition definition -> definition.primary = true } as BeanDefinitionCustomizer)
            got << inject()
            context.registerBean('prototype', CacheAware,
                    { BeanDefinition definition -> definition.scope = BeanDefinition.SCOPE_PROTOTYPE } as BeanDefinitionCustomizer)
            got << inject()
            return got
        }
        finally {
            context.close()
        }
    }

    void 'a remembered cache manager that is removed and replaced is replaced in the memory too, as in 8.0.0'() {
        when: 'a prototype gets the only cache manager, which is removed for another, and then a primary one is added'
        List<String> was = replaced(false)
        List<String> now = replaced(true)

        then: 'the replacement is remembered in its place, and kept when the primary one is added'
        was == ['first', 'second', 'second']
        now == was
    }

    /** What a prototype gets: from first, from second after first is removed, and after a primary one is added. */
    private List<String> replaced(boolean withFallback) {
        Map<String, CacheManager> instances = [:]
        GenericApplicationContext context = context([first: 'plain'], instances, [], withFallback)
        Closure<String> inject = { -> nameOf(((CacheAware) context.getBean('prototype')).grailsCacheManager, instances) }
        Closure<Void> register = { String name, boolean primary ->
            context.registerBean(name, CacheManager, { ->
                CacheManager instance = new ConcurrentMapCacheManager()
                instances[name] = instance
                instance
            } as Supplier<CacheManager>, { BeanDefinition definition -> definition.primary = primary } as BeanDefinitionCustomizer)
            null
        }
        try {
            List<String> got = [inject()]
            context.removeBeanDefinition('first')
            register('second', false)
            got << inject()
            register('third', true)
            got << inject()
            return got
        }
        finally {
            context.close()
        }
    }

    void 'a remembered prototype cache manager made for its injection point gets one, as in 8.0.0'() {
        given: 'a prototype cache manager whose factory method takes the injection point, and a prototype cache-aware bean'
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()
        context.addBeanFactoryPostProcessor(new GrailsCacheManagerFallback())
        context.register(PerInjectionPoint)
        context.registerBean('prototype', CacheAware,
                { BeanDefinition definition -> definition.scope = BeanDefinition.SCOPE_PROTOTYPE } as BeanDefinitionCustomizer)
        context.refresh()

        when: 'the prototype is injected again, from what is remembered'
        List<CacheManager> got = (1..3).collect { ((CacheAware) context.getBean('prototype')).grailsCacheManager }

        then: 'each injection made one, for the field'
        got.every { it instanceof PerInjectionPointCacheManager && ((PerInjectionPointCacheManager) it).field?.name == GrailsCacheManagerFallback.FIELD_NAME }
        got*.hashCode().unique().size() == 3

        cleanup:
        context?.close()
    }

    void 'the injection Spring AOT generates resolves the field every time, as in 8.0.0'() {
        given: 'a factory bean of new cache managers beside a primary grailsCacheManager, as in the prototype features'
        Map<String, Object> was = aotResolved(false)
        Map<String, Object> now = aotResolved(true)

        expect: 'each of three resolutions asks the factory, with the fallback as without it'
        was.chosen == [NAMED] * 3 && now.chosen == [NAMED] * 3
        ((List<String>) was.created).count('factory') == 3
        ((List<String>) now.created).count('factory') == 3
    }

    /** Resolves the trait's field three times as AOT-generated code does, with a new injection point each time. */
    private Map<String, Object> aotResolved(boolean withFallback) {
        Map<String, CacheManager> instances = [:]
        List<String> created = []
        GenericApplicationContext context = context([factory: 'prototypeFactoryBean', (NAMED): 'primary'], instances, created, withFallback)
        try {
            int startup = created.size()
            RegisteredBean prototype = RegisteredBean.of(context.defaultListableBeanFactory, 'prototype')
            List<String> chosen = (1..3).collect {
                nameOf((CacheManager) AutowiredFieldValueResolver.forField(GrailsCacheManagerFallback.FIELD_NAME).resolve(prototype), instances)
            }
            return [chosen: chosen, created: new ArrayList<String>(created.subList(startup, created.size()))]
        }
        finally {
            context.close()
        }
    }

    void 'a prototype cache manager that makes nothing keeps the injection empty, as in 8.0.0'() {
        when: 'a prototype cache-aware bean is injected three times beside a prototype cache manager that makes nothing twice'
        Map<String, Object> was = emptyFirst(false)
        Map<String, Object> now = emptyFirst(true)

        then: 'Spring asks twice for the first injection, getting nothing, which stays the injection, and once for each later one'
        was == [got: [null, 'made 3', 'made 4'], asked: 4]
        now == was
    }

    /** Injects a prototype cache-aware bean three times beside a prototype cache manager that makes nothing the first two times. */
    private Map<String, Object> emptyFirst(boolean withFallback) {
        int asked = 0
        GenericApplicationContext context = context([:], [:], [], withFallback) { GenericApplicationContext it ->
            it.registerBean('sometimes', CacheManager, { ->
                asked++
                asked <= 2 ? null : new NamedCacheManager("made ${asked}".toString())
            } as Supplier<CacheManager>, { BeanDefinition definition -> definition.scope = BeanDefinition.SCOPE_PROTOTYPE } as BeanDefinitionCustomizer)
        }
        try {
            List<String> got = (1..3).collect { ((NamedCacheManager) ((CacheAware) context.getBean('prototype')).grailsCacheManager)?.label }
            return [got: got, asked: asked]
        }
        finally {
            context.close()
        }
    }

    void 'a cache manager injected anywhere else is left as Spring resolves it'() {
        given: 'two cache managers Spring cannot choose between, and a bean of the application\'s own that injects one'
        GenericApplicationContext context = context([(NAMED): 'plain', first: 'plain'], [:], [], true) { GenericApplicationContext it ->
            it.registerBean('own', OwnInjection, { BeanDefinition definition -> definition.lazyInit = true } as BeanDefinitionCustomizer)
        }

        when:
        context.getBean('own')

        then: 'it fails as it did, while the cache-aware class gets grailsCacheManager'
        UnsatisfiedDependencyException e = thrown()
        e.cause instanceof NoUniqueBeanDefinitionException
        context.getBean('probe', CacheAware).grailsCacheManager.is(context.getBean(NAMED))

        cleanup:
        context?.close()
    }

    void 'the resolver is installed once, and copied as itself'() {
        given:
        GenericApplicationContext context = context([(NAMED): 'plain'], [:], [], true)

        when: 'post-processed again'
        new GrailsCacheManagerFallback().postProcessBeanFactory(context.beanFactory)
        AutowireCandidateResolver resolver = context.defaultListableBeanFactory.autowireCandidateResolver

        then:
        resolver instanceof GrailsCacheManagerFallback.CandidateResolver
        !(resolver.@delegate instanceof GrailsCacheManagerFallback.CandidateResolver)
        resolver.cloneIfNecessary() instanceof GrailsCacheManagerFallback.CandidateResolver

        cleanup:
        context?.close()
    }

    private Map<String, Object> run(Map<String, String> row, boolean withFallback) {
        Map<String, CacheManager> instances = [:]
        List<String> created = []
        List<String> atStartup = []
        Map<String, Object> result = [failed: false, failedAtStartup: false]
        GenericApplicationContext started = null
        try {
            try {
                // a cyclic grailsCacheManager has the probe injected while the context starts
                started = context(row, instances, created, withFallback)
            }
            catch (BeansException e) {
                result.failedAtStartup = true
                throw e
            }
            atStartup.addAll(created)
            CacheManager chosen = ((CacheAware) started.getBean('probe')).grailsCacheManager
            result.chosen = nameOf(chosen, instances)
            if (row[NAMED] == 'cacheAware') {
                result.self = nameOf(((GrailsCacheManagerAware) started.getBean(NAMED)).grailsCacheManager, instances)
            }
            result.dependencies = (started.beanFactory.getDependenciesForBean('probe') as List<String>).sort()
            int beforePrototypes = created.size()
            result.prototypes = (1..3).collect { nameOf(((CacheAware) started.getBean('prototype')).grailsCacheManager, instances) }
            result.createdByPrototypes = new ArrayList<String>(created.subList(beforePrototypes, created.size()))
            created.subList(beforePrototypes, created.size()).clear()
        }
        catch (BeansException ignored) {
            result.failed = true
        }
        finally {
            started?.close()
        }
        // what was created after startup, each creation counted, as a list difference would drop repeats
        result.created = new ArrayList<String>(created.subList(atStartup.size(), created.size()))
        result
    }

    private static String nameOf(CacheManager cacheManager, Map<String, CacheManager> instances) {
        cacheManager == null ? null : instances.find { String name, CacheManager instance -> instance.is(cacheManager) }?.key
    }

    private GenericApplicationContext context(Map<String, String> row, Map<String, CacheManager> instances,
                                              List<String> created, boolean withFallback,
                                              Closure<?> beforeRefresh = null) {
        GenericApplicationContext context = new GenericApplicationContext()
        AnnotationConfigUtils.registerAnnotationConfigProcessors(context)
        if (withFallback) {
            context.addBeanFactoryPostProcessor(new GrailsCacheManagerFallback())
        }
        row.each { String name, String kind ->
            if (kind == 'wrongType') {
                context.registerBean(name, String, { -> 'not a cache manager' } as Supplier<String>)
                return
            }
            Class<? extends CacheManager> type = kind in ['priority', 'priorityOnObject', 'factoryBean', 'createdByKeyGenerator'] ?
                    HighPriorityCacheManager : (kind == 'cacheAware' ? CacheAwareCacheManager : ConcurrentMapCacheManager)
            Supplier<CacheManager> supplier = { ->
                if (kind == 'cyclic') {
                    context.getBean('probe')
                }
                created << name
                // a factory method that returns null makes Spring's null bean
                CacheManager instance = kind == 'nullBean' ? null : type.getDeclaredConstructor().newInstance()
                instances[name] = instance
                instance
            } as Supplier<CacheManager>
            if (kind in ['factoryBean', 'prototypeFactoryBean']) {
                boolean singleton = kind == 'factoryBean'
                context.registerBean(name, ProductFactoryBean, { -> new ProductFactoryBean(supplier, singleton) } as Supplier<ProductFactoryBean>)
                return
            }
            // declared as a plain CacheManager, as a factory method would declare it, so only the object has the priority
            Class<? extends CacheManager> declared = kind in ['priorityOnObject', 'createdByKeyGenerator'] ? CacheManager : type
            BeanDefinitionCustomizer customizer = { BeanDefinition definition ->
                AbstractBeanDefinition bean = (AbstractBeanDefinition) definition
                bean.primary = kind == 'primary'
                bean.lazyInit = kind in ['lazy', 'createdByKeyGenerator']
                bean.autowireCandidate = kind != 'notCandidate'
                bean.fallback = kind == 'fallback'
                bean.defaultCandidate = kind != 'notDefault'
            } as BeanDefinitionCustomizer
            context.registerBean(name, (Class<CacheManager>) declared, supplier, customizer)
        }
        BeanDefinitionCustomizer lazy = { BeanDefinition definition -> definition.lazyInit = true } as BeanDefinitionCustomizer
        List<String> forKeyGenerator = row.findAll { String name, String kind -> kind == 'createdByKeyGenerator' }*.key
        if (forKeyGenerator) {
            // injected into the cache-aware bean before its cache manager, and creating cache managers when it is
            context.registerBean('customCacheKeyGenerator', GrailsCacheKeyGenerator, { ->
                forKeyGenerator.each { String name -> context.getBean(name) }
                new CustomCacheKeyGenerator()
            } as Supplier<GrailsCacheKeyGenerator>, lazy)
        }
        context.registerBean('probe', CacheAware, lazy)
        context.registerBean('prototype', CacheAware,
                { BeanDefinition definition -> definition.scope = BeanDefinition.SCOPE_PROTOTYPE } as BeanDefinitionCustomizer)
        beforeRefresh?.call(context)
        context.refresh()
        context
    }

    private static List<Map<String, String>> arrangements() {
        List<Map<String, String>> others = [[:]]
        OTHER_KINDS.each { String first -> others << [first: first] }
        OTHER_KINDS.each { String first -> OTHER_KINDS.each { String second -> others << [first: first, second: second] } }
        NAMED_KINDS.collectMany { String named ->
            others.collect { Map<String, String> other -> named == 'absent' ? other : ([(NAMED): named] + other) }
        }
    }

    static class CacheAware implements GrailsCacheManagerAware {
    }

    /** A bean of the application's own that injects a cache manager by type, as the trait does. */
    static class OwnInjection {
        @Autowired(required = false)
        CacheManager someCacheManager
    }

    @Priority(1)
    static class HighPriorityCacheManager extends ConcurrentMapCacheManager {
    }

    /** A cache manager that says which time it was made. */
    static class NamedCacheManager extends ConcurrentMapCacheManager {
        final String label

        NamedCacheManager(String label) {
            this.label = label
        }
    }

    /** A cache manager made for the injection point it is injected into. */
    static class PerInjectionPointCacheManager extends ConcurrentMapCacheManager {
        java.lang.reflect.Field field
    }

    @Configuration(proxyBeanMethods = false)
    static class PerInjectionPoint {
        @Bean
        @Scope(BeanDefinition.SCOPE_PROTOTYPE)
        static CacheManager perInjectionPoint(InjectionPoint injectionPoint) {
            new PerInjectionPointCacheManager(field: injectionPoint.field)
        }
    }

    /** A grailsCacheManager that is cache-aware itself, which Spring leaves out of the candidates for its own field. */
    static class CacheAwareCacheManager extends ConcurrentMapCacheManager implements GrailsCacheManagerAware {
    }

    /** A factory bean that names only CacheManager as its type, making one cache manager or a new one each time. */
    static class ProductFactoryBean implements FactoryBean<CacheManager> {

        private final Supplier<CacheManager> product

        private final boolean singleton

        ProductFactoryBean(Supplier<CacheManager> product, boolean singleton) {
            this.product = product
            this.singleton = singleton
        }

        @Override
        boolean isSingleton() {
            singleton
        }

        @Override
        CacheManager getObject() {
            product.get()
        }

        @Override
        Class<?> getObjectType() {
            CacheManager
        }
    }
}

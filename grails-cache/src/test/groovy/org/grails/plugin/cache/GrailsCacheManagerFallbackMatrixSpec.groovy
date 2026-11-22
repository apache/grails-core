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
import java.nio.file.Files
import java.nio.file.Path
import java.util.function.Supplier

import javax.tools.JavaCompiler
import javax.tools.StandardJavaFileManager
import javax.tools.ToolProvider

import groovy.io.FileType

import jakarta.annotation.Priority
import jakarta.annotation.Resource

import org.springframework.aot.AotDetector
import org.springframework.aot.generate.ClassNameGenerator
import org.springframework.aot.generate.DefaultGenerationContext
import org.springframework.aot.generate.FileSystemGeneratedFiles
import org.springframework.aot.hint.RuntimeHints
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates
import org.springframework.beans.BeansException
import org.springframework.beans.factory.BeanFactory
import org.springframework.beans.factory.FactoryBean
import org.springframework.beans.factory.InjectionPoint
import org.springframework.beans.factory.NoUniqueBeanDefinitionException
import org.springframework.beans.factory.ObjectFactory
import org.springframework.beans.factory.UnsatisfiedDependencyException
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.beans.factory.config.AutowireCapableBeanFactory
import org.springframework.beans.factory.config.BeanDefinition
import org.springframework.beans.factory.config.BeanDefinitionCustomizer
import org.springframework.beans.factory.support.AbstractBeanDefinition
import org.springframework.beans.factory.support.DefaultListableBeanFactory
import org.springframework.beans.factory.support.RootBeanDefinition
import org.springframework.cache.CacheManager
import org.springframework.cache.concurrent.ConcurrentMapCacheManager
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.AnnotationConfigUtils
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.aot.ApplicationContextAotGenerator
import org.springframework.context.annotation.Scope
import org.springframework.context.support.GenericApplicationContext
import org.springframework.core.SpringProperties
import org.springframework.javapoet.ClassName
import spock.lang.Specification

import grails.plugin.cache.CustomCacheKeyGenerator
import grails.plugin.cache.GrailsCacheKeyGenerator

import org.apache.grails.core.aot.GeneratedArtifactsAutowiredAnnotationBeanPostProcessor
import org.apache.grails.core.aot.GeneratedArtifactsCommonAnnotationBeanPostProcessor

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
        GrailsCacheManagerFallback.traitField(CacheAware)?.name == GrailsCacheManagerFallback.FIELD_NAME
        GrailsCacheManagerFallback.traitField(OwnInjection) == null
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

    void 'a prototype bean registered again resolves its cache manager afresh, as in 8.0.0, where #arrangement'() {
        when: 'a prototype is injected, a cache manager marked primary is added, and the prototype is registered again'
        List<String> was = reloaded(row, false)
        List<String> now = reloaded(row, true)

        then: 'the choice is kept until the prototype is registered again, and then made afresh'
        now == expected

        and: 'as in 8.0.0, which kept its choice the same way; where it could not choose, it kept nothing to keep'
        was == (row.first == 'plain' ? ['failed', 'preferred', 'preferred'] : expected)

        where:
        arrangement                                 | row
        'Spring chooses grailsCacheManager'         | [(NAMED): 'plain']
        'it is grailsCacheManager by the fallback'  | [(NAMED): 'plain', first: 'plain']
        expected = [NAMED, NAMED, 'preferred']
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

    void 'a remembered cache manager that is removed is replaced, and the replacement kept, as in 8.0.0, #arrangement'() {
        when: 'a prototype gets a cache manager, which is removed, and then a primary one is added'
        Map<String, Object> was = replaced(row, replacement, false)
        Map<String, Object> now = replaced(row, replacement, true)

        then: 'the one taken in its place is remembered, depended on, and kept when the primary one is added'
        was == expected
        now == was

        where:
        arrangement                                     | row                                  | replacement
        'when it was the only one'                      | [first: 'plain']                     | 'second'
        'when it was primary beside grailsCacheManager' | [(NAMED): 'plain', first: 'primary'] | null
        expected = [got: ['first', replacement ?: NAMED, replacement ?: NAMED], dependsOn: true]
    }

    /**
     * What a prototype gets: from first; after first is removed, from what is left or from the replacement
     * registered; and after a primary one is added. Also whether the bean taken in first's place has the
     * prototype as a dependent.
     */
    private Map<String, Object> replaced(Map<String, String> row, String replacement, boolean withFallback) {
        Map<String, CacheManager> instances = [:]
        GenericApplicationContext context = context(row, instances, [], withFallback)
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
            if (replacement) {
                register(replacement, false)
            }
            got << inject()
            register('third', true)
            got << inject()
            return [got: got, dependsOn: 'prototype' in context.beanFactory.getDependentBeans(replacement ?: NAMED)]
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

    void 'run from the code Spring\'s AOT processing generates, the field falls back as in a context that is not: #subject.simpleName beside #others'() {
        when: 'the context processed ahead of time, and the code it generates compiled and run'
        Map<String, Object> was = aheadOfTime(subject, others, false)
        Map<String, Object> now = aheadOfTime(subject, others, true)

        then: 'Spring\'s generated injection injects the field, after the trait\'s other field and before the class\'s method'
        now.injection.indexOf('__customCacheKeyGenerator') < now.injection.indexOf("forField(\"${GrailsCacheManagerFallback.FIELD_NAME}\")")
        now.injection.indexOf("forField(\"${GrailsCacheManagerFallback.FIELD_NAME}\")") < now.injection.indexOf('"init"')
        now.fieldHinted

        and: 'the resolution of the field falls back where 8.0.0 failed, and is 8.0.0\'s elsewhere; the code generated for a bean depends on each candidate it gets from the bean factory'
        was.result == wasExpected
        now.result == nowExpected

        and: 'the bean factory\'s resolver answers it, installed once'
        now.resolver == [installed: true, once: true, copied: true]
        !was.resolver?.installed

        where:
        subject       | others                                                 || wasExpected                                                       || nowExpected
        MethodInClass | [:]                                                    || [seen: NAMED, got: NAMED, copies: [NAMED], dependencies: [NAMED]]  || wasExpected
        MethodInClass | [other: OtherCacheManager]                             || [failed: true] || [seen: NAMED, got: NAMED, copies: [NAMED], dependencies: [NAMED, 'other']]
        MethodInClass | [other: OtherCacheManager, primary: OtherCacheManager] || [seen: 'primary', got: 'primary', copies: ['primary'], dependencies: [NAMED, 'other', 'primary']] || wasExpected
        // the generated code finds a field by its name, so it sets the subclass's copy of it twice, the first time before
        // the superclass's method, and never the superclass's copy, as in 8.0.0
        AwareAgain    | [:]                                                    || [seen: NAMED, got: NAMED, copies: [null, NAMED], dependencies: [NAMED]] || wasExpected
        AwareAgain    | [other: OtherCacheManager]                             || [failed: true] || [seen: NAMED, got: NAMED, copies: [null, NAMED], dependencies: [NAMED, 'other']]
        AwareAgain    | [other: OtherCacheManager, primary: OtherCacheManager] || [seen: 'primary', got: 'primary', copies: [null, 'primary'], dependencies: [NAMED, 'other', 'primary']] || wasExpected
    }

    private Map<String, Object> aheadOfTime(Class<? extends MethodInClass> subject, Map<String, Class<?>> others, boolean withFallback,
                                            boolean withGrailsProcessors = false,
                                            Closure<Map<String, Object>> afterStart = null) {
        Path dir = Files.createTempDirectory('grails-cache-aot')
        try {
            GenericApplicationContext processed = new GenericApplicationContext()
            AnnotationConfigUtils.registerAnnotationConfigProcessors(processed)
            if (withFallback) {
                processed.registerBean(GrailsCacheManagerFallback)
            }
            processed.registerBean(NAMED, ConcurrentMapCacheManager)
            others.each { String name, Class<?> type ->
                processed.registerBean(name, (Class<Object>) type,
                        { BeanDefinition definition -> definition.primary = name == 'primary' } as BeanDefinitionCustomizer)
            }
            processed.registerBean('probe', (Class<Object>) subject)
            RuntimeHints hints = new RuntimeHints()
            DefaultGenerationContext generation = new DefaultGenerationContext(new ClassNameGenerator(ClassName.get(GrailsCacheManagerFallbackMatrixSpec)),
                    new FileSystemGeneratedFiles(dir.resolve('sources')), hints)
            ClassName initializer = new ApplicationContextAotGenerator().processAheadOfTime(processed, generation)
            generation.writeGeneratedContent()
            List<File> sources = []
            dir.resolve('sources').toFile().eachFileRecurse(FileType.FILES) { File file -> if (file.name.endsWith('.java')) sources << file }
            Path classes = Files.createDirectories(dir.resolve('classes'))
            JavaCompiler compiler = ToolProvider.systemJavaCompiler
            StandardJavaFileManager files = compiler.getStandardFileManager(null, null, null)
            assert compiler.getTask(null, files, null, ['-d', classes.toString(), '-classpath', System.getProperty('java.class.path'), '-proc:none'],
                    null, files.getJavaFileObjectsFromFiles(sources)).call()
            files.close()
            Map<String, Object> result = [
                    injection: sources.find { File file -> file.name == "GrailsCacheManagerFallbackMatrixSpec_${subject.simpleName}__Autowiring.java".toString() }.text,
                    fieldHinted: copiesOfTheField(subject).every { Field copy -> RuntimeHintsPredicates.reflection().onField(copy).test(hints) }]
            URLClassLoader loader = new URLClassLoader([classes.toUri().toURL()] as URL[], getClass().classLoader)
            GenericApplicationContext started = new GenericApplicationContext()
            started.classLoader = loader
            SpringProperties.setProperty(AotDetector.AOT_ENABLED, 'true')
            try {
                ((ApplicationContextInitializer<GenericApplicationContext>) loader.loadClass(initializer.reflectionName())
                        .getDeclaredConstructor().newInstance()).initialize(started)
                if (withGrailsProcessors) {
                    // as Grails registers them again where it runs on generated code, for what that code did not inject
                    started.registerBean(AnnotationConfigUtils.AUTOWIRED_ANNOTATION_PROCESSOR_BEAN_NAME,
                            GeneratedArtifactsAutowiredAnnotationBeanPostProcessor)
                    started.registerBean(AnnotationConfigUtils.COMMON_ANNOTATION_PROCESSOR_BEAN_NAME,
                            GeneratedArtifactsCommonAnnotationBeanPostProcessor)
                    started.registerBean('late', MethodInClass,
                            { BeanDefinition definition -> definition.scope = BeanDefinition.SCOPE_PROTOTYPE } as BeanDefinitionCustomizer)
                }
                started.refresh()
                Map<String, CacheManager> instances = ([NAMED] + others.keySet()).collectEntries { String name ->
                    [(name): started.getBean(name, CacheManager)]
                }
                MethodInClass probe = started.getBean('probe', MethodInClass)
                assert probe.initCalls == 1
                if (afterStart != null) {
                    result.afterStart = afterStart(started, instances)
                }
                List<Field> copies = copiesOfTheField(subject)
                copies.each { Field copy -> copy.accessible = true }
                result.result = [seen: nameOf(probe.seen, instances), got: nameOf(probe.grailsCacheManager, instances),
                        copies: copies.collect { Field copy -> nameOf((CacheManager) copy.get(probe), instances) },
                        dependencies: (started.beanFactory.getDependenciesForBean('probe') as List<String>).sort()]
                // post-processed again, the resolver is not wrapped twice, and a copy of it falls back too
                Object resolver = started.defaultListableBeanFactory.autowireCandidateResolver
                new GrailsCacheManagerFallback().postProcessBeanFactory(started.beanFactory)
                result.resolver = [installed: resolver instanceof GrailsCacheManagerFallback.GeneratedInjectionFallback,
                        once: started.defaultListableBeanFactory.autowireCandidateResolver.is(resolver),
                        copied: started.defaultListableBeanFactory.autowireCandidateResolver.cloneIfNecessary()
                                instanceof GrailsCacheManagerFallback.GeneratedInjectionFallback]
            }
            catch (BeansException ignored) {
                result.result = [failed: true]
            }
            finally {
                SpringProperties.setProperty(AotDetector.AOT_ENABLED, null)
                started.close()
                loader.close()
            }
            result
        }
        finally {
            dir.toFile().deleteDir()
        }
    }

    void 'a prototype cache manager that makes nothing keeps the injection empty, as in 8.0.0, #arrangement'() {
        when: 'a prototype cache-aware bean is injected three times beside a prototype cache manager that makes nothing twice'
        Map<String, Object> was = emptyFirst(row, primary, false)
        Map<String, Object> now = emptyFirst(row, primary, true)

        then: 'Spring asks twice for the first injection, getting nothing, which stays the injection, and once for each later one'
        was == [got: [null, 'made 3', 'made 4'], asked: 4]
        now == was

        where:
        arrangement                                                  | row                | primary
        'alone'                                                      | [:]                | false
        'marked primary beside grailsCacheManager' | [(NAMED): 'plain'] | true
    }

    /** Injects a prototype cache-aware bean three times beside a prototype cache manager that makes nothing the first two times. */
    private Map<String, Object> emptyFirst(Map<String, String> row, boolean primary, boolean withFallback) {
        int asked = 0
        GenericApplicationContext context = context(row, [:], [], withFallback) { GenericApplicationContext it ->
            it.registerBean('sometimes', CacheManager, { ->
                asked++
                asked <= 2 ? null : new NamedCacheManager("made ${asked}".toString())
            } as Supplier<CacheManager>, { BeanDefinition definition ->
                definition.scope = BeanDefinition.SCOPE_PROTOTYPE
                definition.primary = primary
            } as BeanDefinitionCustomizer)
        }
        try {
            List<String> got = (1..3).collect { ((NamedCacheManager) ((CacheAware) context.getBean('prototype')).grailsCacheManager)?.label }
            return [got: got, asked: asked]
        }
        finally {
            context.close()
        }
    }

    void 'beside a dependency registered as resolvable, the field is resolved as in 8.0.0, #arrangement'() {
        when: 'a prototype cache-aware bean is injected three times'
        Map<String, Object> was = withResolvable(row, registered, primaryEmpty, false)
        Map<String, Object> now = withResolvable(row, registered, primaryEmpty, true)

        then: 'what it gets and how often the dependency is asked'
        was == oracle
        now == (expected ?: oracle)

        where:
        arrangement                                                                | row                                | registered    | primaryEmpty | oracle                                          | expected
        'a cache manager made empty at first, with no bean'                        | [:]                                | 'emptyFirst'  | false        | [got: [null, 'made 2', 'made 3'], asked: 3]     | null
        'a cache manager made empty at first, beside grailsCacheManager'           | [(NAMED): 'plain']                 | 'emptyFirst'  | false        | [got: [NAMED, NAMED, NAMED], asked: 1]          | null
        'a cache manager made empty at first, beside a primary one making nothing' | [(NAMED): 'plain']                 | 'emptyFirst'  | true         | [got: [null, null, null], asked: 3]             | null
        'a cache manager made empty at first, where it falls back'                 | [(NAMED): 'plain', first: 'plain'] | 'emptyFirst'  | false        | [got: ['failed', 'failed', 'failed'], asked: 3] | [got: [NAMED, NAMED, NAMED], asked: 1]
        'a cache manager made each time, beside grailsCacheManager alone'          | [(NAMED): 'plain']                 | 'eachTime'    | false        | [got: ['failed', 'failed', 'failed'], asked: 3] | [got: [NAMED, NAMED, NAMED], asked: 1]
        'a factory that makes nothing, where it falls back'                        | [(NAMED): 'plain', first: 'plain'] | 'alwaysEmpty' | false        | [got: ['failed', 'failed', 'failed'], asked: 3] | [got: [NAMED, NAMED, NAMED], asked: 1]
        'an object that is no cache manager, where it falls back'                  | [(NAMED): 'plain', first: 'plain'] | 'notOne'      | false        | [got: ['failed', 'failed', 'failed'], asked: 0] | [got: [NAMED, NAMED, NAMED], asked: 0]
    }

    /** Injects a prototype cache-aware bean three times, with a dependency registered as resolvable. */
    private Map<String, Object> withResolvable(Map<String, String> row, String registered, boolean primaryEmpty, boolean withFallback) {
        Map<String, CacheManager> instances = [:]
        CacheManagerFactory factory = new CacheManagerFactory(makes: registered)
        GenericApplicationContext context = context(row, instances, [], withFallback) { GenericApplicationContext it ->
            if (registered == 'notOne') {
                // registered for every type, which Spring leaves out for a cache manager
                it.beanFactory.registerResolvableDependency(Object, new Object())
            }
            else {
                it.beanFactory.registerResolvableDependency(CacheManager, factory)
            }
            if (primaryEmpty) {
                // a prototype marked primary whose factory method returns null, so the field resolves to nothing
                it.registerBean('empty', CacheManager, { -> null } as Supplier<CacheManager>, { BeanDefinition definition ->
                    definition.scope = BeanDefinition.SCOPE_PROTOTYPE
                    definition.primary = true
                } as BeanDefinitionCustomizer)
            }
        }
        try {
            List<String> got = (1..3).collect {
                try {
                    CacheManager cacheManager = ((CacheAware) context.getBean('prototype')).grailsCacheManager
                    cacheManager instanceof NamedCacheManager ? ((NamedCacheManager) cacheManager).label : nameOf(cacheManager, instances)
                }
                catch (BeansException ignored) {
                    'failed'
                }
            }
            return [got: got, asked: factory.asked]
        }
        finally {
            context.close()
        }
    }

    void 'a field keeps the cache manager it remembers when grailsCacheManager is added, as in 8.0.0'() {
        when: 'a prototype gets the only cache manager, then grailsCacheManager is added'
        List<String> was = shortcutKept(false)
        List<String> now = shortcutKept(true)

        then: 'Spring\'s own record of the field still gives it the first one'
        was == ['first', 'first']
        now == was
    }

    private List<String> shortcutKept(boolean withFallback) {
        Map<String, CacheManager> instances = [:]
        GenericApplicationContext context = context([first: 'plain'], instances, [], withFallback)
        try {
            List<String> got = [nameOf(((CacheAware) context.getBean('prototype')).grailsCacheManager, instances)]
            context.registerBean(NAMED, CacheManager, { ->
                CacheManager instance = new ConcurrentMapCacheManager()
                instances[NAMED] = instance
                instance
            } as Supplier<CacheManager>)
            got << nameOf(((CacheAware) context.getBean('prototype')).grailsCacheManager, instances)
            return got
        }
        finally {
            context.close()
        }
    }

    void 'a remembered cache manager that fails is resolved again once, and the failure kept, as in 8.0.0'() {
        when: 'a primary prototype cache manager beside grailsCacheManager is made at the first and fourth attempt only'
        Map<String, Object> was = failingOnce(false)
        Map<String, Object> now = failingOnce(true)

        then: 'the second injection fails after the remembered one and one more resolution fail, as Spring retries once'
        was == [got: ['made 1', 'failed'], attempts: 3]
        now == was
    }

    private Map<String, Object> failingOnce(boolean withFallback) {
        int attempts = 0
        GenericApplicationContext context = context([(NAMED): 'plain'], [:], [], withFallback) { GenericApplicationContext it ->
            it.registerBean('flaky', CacheManager, { ->
                attempts++
                if (attempts in [2, 3]) {
                    throw new IllegalStateException("the store is down at attempt ${attempts}")
                }
                new NamedCacheManager("made ${attempts}".toString())
            } as Supplier<CacheManager>, { BeanDefinition definition ->
                definition.scope = BeanDefinition.SCOPE_PROTOTYPE
                definition.primary = true
            } as BeanDefinitionCustomizer)
        }
        try {
            List<String> got = (1..2).collect {
                try {
                    ((NamedCacheManager) ((CacheAware) context.getBean('prototype')).grailsCacheManager).label
                }
                catch (BeansException ignored) {
                    'failed'
                }
            }
            return [got: got, attempts: attempts]
        }
        finally {
            context.close()
        }
    }

    void 'a bean named like the field is taken by that name, before any other is looked at, as in 8.0.0'() {
        when: 'the application names its cache manager like the field, beside a factory bean that fails to make one'
        Map<String, Object> was = namedLikeTheField(false)
        Map<String, Object> now = namedLikeTheField(true)

        then: 'Spring takes it by the name and never asks the factory bean'
        was == [got: 'chosen', asked: 0]
        now == was
    }

    private Map<String, Object> namedLikeTheField(boolean withFallback) {
        Map<String, CacheManager> instances = [:]
        FailingFactoryBean failing = new FailingFactoryBean()
        GenericApplicationContext context = context([chosen: 'plain', (NAMED): 'plain'], instances, [], withFallback) { GenericApplicationContext it ->
            it.registerAlias('chosen', GrailsCacheManagerFallback.FIELD_NAME)
            it.registerBean('failing', FailingFactoryBean, { -> failing } as Supplier<FailingFactoryBean>)
        }
        try {
            return [got: nameOf(((CacheAware) context.getBean('prototype')).grailsCacheManager, instances), asked: failing.asked]
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

    void 'a cache-aware class\'s own injection runs where it ran in 8.0.0 around its cache manager: #subject.simpleName beside #others'() {
        when:
        Map<String, Object> was = ownInjection(subject, others, false)
        Map<String, Object> now = ownInjection(subject, others, true)

        then: 'what the class\'s own injection sees of the cache manager, in two instances of it, and what the class gets'
        was == wasExpected
        now == nowExpected

        where:
        subject          | others                                         || wasExpected               || nowExpected
        MethodInClass    | [(NAMED): 'plain']                             || [seen: [NAMED] * 2, got: NAMED]  || wasExpected
        MethodInClass    | [(NAMED): 'plain', first: 'plain']             || [failed: true]                   || [seen: [NAMED] * 2, got: NAMED]
        MethodInClass    | [(NAMED): 'plain', first: 'primary']           || [seen: ['first'] * 2, got: 'first'] || wasExpected
        MethodInSubclass | [(NAMED): 'plain']                             || [seen: [NAMED] * 2, got: NAMED]  || wasExpected
        MethodInSubclass | [(NAMED): 'plain', first: 'plain']             || [failed: true]                   || [seen: [NAMED] * 2, got: NAMED]
        TraitBelowMethod | [(NAMED): 'plain']                             || [seen: [null] * 2, got: NAMED]   || wasExpected
        TraitBelowMethod | [(NAMED): 'plain', first: 'plain']             || [failed: true]                   || [seen: [null] * 2, got: NAMED]
        OwnField         | [(NAMED): 'plain', first: 'createdByOwnField'] || [seen: [null] * 2, got: 'first'] || wasExpected
        FieldInSubclass  | [(NAMED): 'plain', first: 'createdByOwnField'] || [failed: true]                   || [seen: [null] * 2, got: NAMED]
        ByResourceName   | [(NAMED): 'plain', first: 'primary']           || [seen: [NAMED] * 2, got: 'first'] || wasExpected
        InheritsMethod   | [(NAMED): 'plain']                             || [seen: [NAMED] * 2, got: NAMED]  || wasExpected
        InheritsMethod   | [(NAMED): 'plain', first: 'plain']             || [failed: true]                   || [seen: [NAMED] * 2, got: NAMED]
        AwareAgain       | [(NAMED): 'plain']                             || [seen: [null] * 2, got: NAMED]   || wasExpected
        AwareAgain       | [(NAMED): 'plain', first: 'plain']             || [failed: true]                   || [seen: [null] * 2, got: NAMED]
        AwareAgain       | [(NAMED): 'plain', first: 'primary']           || [seen: [null] * 2, got: 'first'] || wasExpected
        AgainWithMethod  | [(NAMED): 'plain']                             || [seen: [NAMED] * 2, got: NAMED]  || wasExpected
        AgainWithMethod  | [(NAMED): 'plain', first: 'plain']             || [failed: true]                   || [seen: [NAMED] * 2, got: NAMED]
    }

    void 'a cached subclass of a cached class, compiled #mode, inherits an autowired method that sees its cache manager, beside #others'() {
        given: 'the cache transformations leave the trait to the superclass, so the class has the field once'
        Class<?> subclass = compileCachedSubclass(mode)
        assert copiesOfTheField(subclass)*.declaringClass*.simpleName == ['CachedBase']

        when:
        Map<String, Object> was = cachedSubclass(subclass, others, false)
        Map<String, Object> now = cachedSubclass(subclass, others, true)

        then: 'the inherited method runs after the field is set, and the methods of both classes cache with grailsCacheManager'
        was == wasExpected
        now == [copies: [NAMED], cachedIn: [base: NAMED, sub: NAMED], calls: 2, warmedWith: NAMED]

        where:
        mode          | others                  || wasExpected
        'statically'  | [:]                     || [copies: [NAMED], cachedIn: [base: NAMED, sub: NAMED], calls: 2, warmedWith: NAMED]
        'statically'  | [cacheManager: 'plain'] || [failed: true]
        'dynamically' | [:]                     || [copies: [NAMED], cachedIn: [base: NAMED, sub: NAMED], calls: 2, warmedWith: NAMED]
        'dynamically' | [cacheManager: 'plain'] || [failed: true]
    }

    /** Each copy of the trait's field in the class's hierarchy, a superclass's first. */
    private static List<Field> copiesOfTheField(Class<?> type) {
        List<Field> copies = []
        for (Class<?> declaring = type; declaring != null; declaring = declaring.superclass) {
            Field copy = declaring.declaredFields.find { Field field -> field.name == GrailsCacheManagerFallback.FIELD_NAME }
            if (copy != null) {
                copies.add(0, copy)
            }
        }
        copies
    }

    private Map<String, Object> cachedSubclass(Class<?> subclass, Map<String, String> others, boolean withFallback) {
        Map<String, CacheManager> instances = [:]
        GenericApplicationContext started = null
        try {
            started = context([(NAMED): 'plain'] + others, instances, [], withFallback) { GenericApplicationContext it ->
                it.registerBean('service', (Class<Object>) subclass)
            }
            Object service = started.getBean('service')
            List<Field> copies = copiesOfTheField(subclass)
            copies.each { Field copy -> copy.accessible = true }
            2.times {
                service.invokeMethod('base', 1)
                service.invokeMethod('sub', 1)
            }
            return [copies: copies.collect { Field copy -> nameOf((CacheManager) copy.get(service), instances) },
                    cachedIn: ['base', 'sub'].collectEntries { String cache ->
                        [(cache): instances.find { String name, CacheManager manager -> manager.cacheNames.contains(cache) }?.key]
                    },
                    calls: service.getProperty('calls'),
                    warmedWith: nameOf((CacheManager) service.getProperty('warmedWith'), instances)]
        }
        catch (BeansException ignored) {
            return [failed: true]
        }
        finally {
            started?.close()
        }
    }

    private static Class<?> compileCachedSubclass(String mode) {
        new GroovyShell().evaluate("""
import grails.plugin.cache.Cacheable
import groovy.transform.CompileStatic
import org.springframework.beans.factory.BeanFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.cache.CacheManager

${mode == 'statically' ? '@CompileStatic' : ''}
class CachedBase {
    int calls
    CacheManager warmedWith

    @Autowired
    void warm(BeanFactory beanFactory) {
        warmedWith = grailsCacheManager
    }

    @Cacheable('base')
    int base(int x) { calls++; x }
}

${mode == 'statically' ? '@CompileStatic' : ''}
class CachedSubclass extends CachedBase {
    @Cacheable('sub')
    int sub(int x) { calls++; x }
}
return CachedSubclass
""") as Class<?>
    }

    private Map<String, Object> ownInjection(Class<?> subject, Map<String, String> others, boolean withFallback) {
        Map<String, CacheManager> instances = [:]
        GenericApplicationContext started = null
        try {
            started = context(others, instances, [], withFallback) { GenericApplicationContext it ->
                it.registerBean('subject', (Class<Object>) subject,
                        { BeanDefinition definition -> definition.scope = BeanDefinition.SCOPE_PROTOTYPE } as BeanDefinitionCustomizer)
            }
            List<Object> beans = (1..2).collect { started.getBean('subject') }
            // each initializer ran once on each instance
            beans.each { Object bean ->
                ['initCalls', 'calls'].each { String count ->
                    if (bean.hasProperty(count)) {
                        assert bean.getProperty(count) == 1
                    }
                }
            }
            Map<String, Object> result = [seen: beans.collect { Object bean -> nameOf(((Seeing) bean).seen, instances) },
                    got: nameOf(((GrailsCacheManagerAware) beans.last()).grailsCacheManager, instances)]
            // a superclass's copy of the field, where the class implements the trait again, gets the same
            List<Field> copies = copiesOfTheField(subject)
            copies.each { Field copy -> copy.accessible = true }
            assert copies.collect { Field copy -> nameOf((CacheManager) copy.get(beans.last()), instances) }.unique() == [result.got]
            result
        }
        catch (BeansException ignored) {
            return [failed: true]
        }
        finally {
            started?.close()
        }
    }

    void 'run from generated code with Grails\' processors, a bean registered as it starts keeps the cache manager it remembers, as in 8.0.0'() {
        when: 'a prototype registered beside the generated beans is injected, a cache manager marked primary is added, and it is injected again'
        Closure<Map<String, Object>> addPrimary = { GenericApplicationContext started, Map<String, CacheManager> instances ->
            MethodInClass first = started.getBean('late', MethodInClass)
            started.registerBean('addedPrimary', OtherCacheManager,
                    { BeanDefinition definition -> definition.primary = true } as BeanDefinitionCustomizer)
            instances.addedPrimary = started.getBean('addedPrimary', CacheManager)
            MethodInClass second = started.getBean('late', MethodInClass)
            [first: nameOf(first.grailsCacheManager, instances), second: nameOf(second.grailsCacheManager, instances),
             calls: [first.initCalls, second.initCalls]]
        }
        Map<String, Object> was = aheadOfTime(MethodInClass, [:], false, true, addPrimary)
        Map<String, Object> now = aheadOfTime(MethodInClass, [:], true, true, addPrimary)

        then: 'Grails\' injection remembers the cache manager by name, and the generated bean is injected once'
        was.afterStart == [first: NAMED, second: NAMED, calls: [1, 1]]
        now.afterStart == was.afterStart
        now.result.got == NAMED
    }

    void 'a prototype whose supplier makes instances of other classes is injected once each, as in 8.0.0, beside #others'() {
        when:
        Map<String, Object> was = shifting(others, false)
        Map<String, Object> now = shifting(others, true)

        then: 'Spring finds the members again for each class, and the fallback with it'
        was == wasExpected
        now == [got: [NAMED, null, NAMED], calls: [1, 1, 1]]

        where:
        others                             || wasExpected
        [(NAMED): 'plain']                 || [got: [NAMED, null, NAMED], calls: [1, 1, 1]]
        [(NAMED): 'plain', first: 'plain'] || [failed: true]
    }

    private Map<String, Object> shifting(Map<String, String> others, boolean withFallback) {
        Map<String, CacheManager> instances = [:]
        List<Class<?>> classes = [MethodInClass, OtherWithMethod, MethodInClass]
        int made = 0
        GenericApplicationContext started = null
        try {
            started = context(others, instances, [], withFallback) { GenericApplicationContext it ->
                it.registerBean('shifting', Object, { -> classes[made++ % classes.size()].getDeclaredConstructor().newInstance() } as Supplier<Object>,
                        { BeanDefinition definition -> definition.scope = BeanDefinition.SCOPE_PROTOTYPE } as BeanDefinitionCustomizer)
            }
            List<Object> beans = (1..3).collect { started.getBean('shifting') }
            return [got: beans.collect { Object bean -> bean instanceof GrailsCacheManagerAware ? nameOf(bean.grailsCacheManager, instances) : null },
                    calls: beans*.getProperty('initCalls')]
        }
        catch (BeansException ignored) {
            return [failed: true]
        }
        finally {
            started?.close()
        }
    }

    void 'an instance the bean factory autowires, as GORM autowires a data service, falls back as a bean does: #how beside #others'() {
        when:
        Map<String, Object> was = autowired(how, others, false)
        Map<String, Object> now = autowired(how, others, true)

        then:
        was == wasExpected
        now == [seen: [NAMED] * 2, got: [NAMED] * 2, calls: [1, 1]]

        where:
        how                      | others                             || wasExpected
        'autowireBean'           | [(NAMED): 'plain']                 || [seen: [NAMED] * 2, got: [NAMED] * 2, calls: [1, 1]]
        'autowireBean'           | [(NAMED): 'plain', first: 'plain'] || [failed: true]
        'autowireBeanProperties' | [(NAMED): 'plain']                 || [seen: [NAMED] * 2, got: [NAMED] * 2, calls: [1, 1]]
        'autowireBeanProperties' | [(NAMED): 'plain', first: 'plain'] || [failed: true]
    }

    private Map<String, Object> autowired(String how, Map<String, String> others, boolean withFallback) {
        Map<String, CacheManager> instances = [:]
        GenericApplicationContext started = null
        try {
            started = context(others, instances, [], withFallback)
            List<MethodInClass> beans = (1..2).collect {
                MethodInClass bean = new MethodInClass()
                if (how == 'autowireBean') {
                    started.autowireCapableBeanFactory.autowireBean(bean)
                }
                else {
                    started.autowireCapableBeanFactory.autowireBeanProperties(bean, AutowireCapableBeanFactory.AUTOWIRE_NO, false)
                }
                bean
            }
            return [seen: beans.collect { MethodInClass bean -> nameOf(bean.seen, instances) },
                    got: beans.collect { MethodInClass bean -> nameOf(bean.grailsCacheManager, instances) }, calls: beans*.initCalls]
        }
        catch (BeansException ignored) {
            return [failed: true]
        }
        finally {
            started?.close()
        }
    }

    void 'Spring\'s injection keeps the members its own configuration ignores, and gives a member it injects without @Resource\'s processor its place: #subject.simpleName'() {
        when:
        Map<String, Object> was = configured(subject, others, false)
        Map<String, Object> now = configured(subject, others, true)

        then:
        was == wasExpected
        now == nowExpected

        where:
        subject             | others                             || wasExpected                        || nowExpected
        ValueAfterTheField  | [(NAMED): 'plain']                 || [seen: NAMED, valued: null]        || wasExpected
        ValueAfterTheField  | [(NAMED): 'plain', first: 'plain'] || [failed: true]                     || [seen: NAMED, valued: null]
        ResourceAndAutowired | [(NAMED): 'plain']                || [seen: NAMED, valued: null]        || wasExpected
        ResourceAndAutowired | [(NAMED): 'plain', first: 'plain'] || [failed: true]                    || [seen: NAMED, valued: null]
    }

    /**
     * Spring's injection configured for {@code @Autowired} only, for a class with a {@code @Value} method, and
     * without the post-processor for {@code @Resource}, for a class with a method annotated for both.
     */
    private Map<String, Object> configured(Class<?> subject, Map<String, String> others, boolean withFallback) {
        Map<String, CacheManager> instances = [:]
        GenericApplicationContext started = null
        try {
            started = context(others, instances, [], withFallback) { GenericApplicationContext it ->
                if (subject == ValueAfterTheField) {
                    it.getBeanDefinition(AnnotationConfigUtils.AUTOWIRED_ANNOTATION_PROCESSOR_BEAN_NAME).propertyValues
                            .add('autowiredAnnotationTypes', [Autowired] as Set)
                }
                else {
                    it.removeBeanDefinition(AnnotationConfigUtils.COMMON_ANNOTATION_PROCESSOR_BEAN_NAME)
                }
                it.registerBean('subject', (Class<Object>) subject,
                        { BeanDefinition definition -> definition.lazyInit = true } as BeanDefinitionCustomizer)
            }
            Object bean = started.getBean('subject')
            assert bean.getProperty('initCalls') == 1
            return [seen: nameOf((CacheManager) bean.getProperty('seen'), instances), valued: bean.getProperty('valued')]
        }
        catch (BeansException ignored) {
            return [failed: true]
        }
        finally {
            started?.close()
        }
    }

    void 'the bean named grailsCacheManager is the one a child bean factory sees by that name: #where'() {
        when:
        Map<String, Object> was = parentAndChild(shadowed, false)
        Map<String, Object> now = parentAndChild(shadowed, true)

        then: 'where the child names another bean so, its parent\'s alias is not the bean of that name'
        was == [failed: true]
        now == nowExpected

        where:
        where                                    | shadowed || nowExpected
        'the parent\'s alias'                    | false    || [got: 'parentCache']
        'shadowed by a bean of the child\'s own' | true     || [failed: true]
    }

    private Map<String, Object> parentAndChild(boolean shadowed, boolean withFallback) {
        GenericApplicationContext parent = new GenericApplicationContext()
        parent.registerBean('parentCache', ConcurrentMapCacheManager)
        parent.registerAlias('parentCache', NAMED)
        parent.refresh()
        GenericApplicationContext child = new GenericApplicationContext(parent)
        try {
            AnnotationConfigUtils.registerAnnotationConfigProcessors(child)
            if (withFallback) {
                child.addBeanFactoryPostProcessor(new GrailsCacheManagerFallback())
            }
            child.registerBean('other', ConcurrentMapCacheManager)
            if (shadowed) {
                child.registerBean(NAMED, ConcurrentMapCacheManager,
                        { BeanDefinition definition -> definition.autowireCandidate = false } as BeanDefinitionCustomizer)
            }
            child.registerBean('probe', CacheAware, { BeanDefinition definition -> definition.lazyInit = true } as BeanDefinitionCustomizer)
            child.refresh()
            CacheManager got = child.getBean('probe', CacheAware).grailsCacheManager
            return [got: got.is(parent.getBean('parentCache')) ? 'parentCache' : (got.is(child.getBean('other')) ? 'other' : NAMED)]
        }
        catch (BeansException ignored) {
            return [failed: true]
        }
        finally {
            child.close()
            parent.close()
        }
    }

    void 'the injection is installed once'() {
        given:
        GenericApplicationContext context = context([(NAMED): 'plain'], [:], [], true)

        when: 'post-processed again'
        new GrailsCacheManagerFallback().postProcessBeanFactory(context.beanFactory)

        then: 'one registrar ahead of Spring\'s own injection, and one injection right after it'
        List<String> processors = context.defaultListableBeanFactory.beanPostProcessors*.getClass()*.simpleName
        processors.count('TraitFieldRegistrar') == 1
        processors.indexOf('TraitFieldRegistrar') < processors.indexOf('AutowiredAnnotationBeanPostProcessor')
        processors.count('TraitFieldInjection') == 1
        processors.indexOf('TraitFieldInjection') == processors.indexOf('AutowiredAnnotationBeanPostProcessor') + 1

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
            Class<? extends CacheManager> type = kind in ['priority', 'priorityOnObject', 'factoryBean', 'createdByKeyGenerator', 'createdByOwnField'] ?
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
            Class<? extends CacheManager> declared = kind in ['priorityOnObject', 'createdByKeyGenerator', 'createdByOwnField'] ? CacheManager : type
            BeanDefinitionCustomizer customizer = { BeanDefinition definition ->
                AbstractBeanDefinition bean = (AbstractBeanDefinition) definition
                bean.primary = kind == 'primary'
                bean.lazyInit = kind in ['lazy', 'createdByKeyGenerator', 'createdByOwnField']
                bean.autowireCandidate = kind != 'notCandidate'
                bean.fallback = kind == 'fallback'
                bean.defaultCandidate = kind != 'notDefault'
            } as BeanDefinitionCustomizer
            context.registerBean(name, (Class<CacheManager>) declared, supplier, customizer)
        }
        BeanDefinitionCustomizer lazy = { BeanDefinition definition -> definition.lazyInit = true } as BeanDefinitionCustomizer
        List<String> forOwnField = row.findAll { String name, String kind -> kind == 'createdByOwnField' }*.key
        context.registerBean('ownDependency', OwnDependency, { ->
            forOwnField.each { String name -> context.getBean(name) }
            new OwnDependency()
        } as Supplier<OwnDependency>, lazy)
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

    /** What a class's own injection saw of its cache manager. */
    interface Seeing {
        CacheManager getSeen()
    }

    /** A cache-aware class with an autowired method that reads its cache manager. */
    static class MethodInClass implements GrailsCacheManagerAware, Seeing {
        CacheManager seen
        int initCalls

        @Autowired
        void init(BeanFactory beanFactory) {
            initCalls++
            seen = grailsCacheManager
        }
    }

    /** The subclass of a cache-aware class, with an autowired method that reads its cache manager. */
    static class MethodInSubclass extends CacheAware implements Seeing {
        CacheManager seen
        int initCalls

        @Autowired
        void init(BeanFactory beanFactory) {
            initCalls++
            seen = grailsCacheManager
        }
    }

    /** A subclass of a cache-aware class that inherits its autowired method. */
    static class InheritsMethod extends MethodInClass {
    }

    /** A subclass of a cache-aware class that implements the trait again, so it has the field twice. */
    static class AwareAgain extends MethodInClass implements GrailsCacheManagerAware {
    }

    /** Implementing the trait again, with an autowired method of its own after its copy of the field. */
    static class AgainWithMethod extends MethodInClass implements GrailsCacheManagerAware {
        CacheManager seenBySubclass
        int calls

        /** What the first call saw, as a method injected once sees it. */
        @Autowired
        void again(BeanFactory beanFactory) {
            if (calls++ == 0) {
                seenBySubclass = grailsCacheManager
            }
        }

        @Override
        CacheManager getSeen() {
            seenBySubclass
        }
    }

    /** A class with an autowired method, whose subclass is cache-aware. */
    static class MethodAbove implements Seeing {
        CacheManager seen
        int initCalls

        @Autowired
        void init(BeanFactory beanFactory) {
            initCalls++
            seen = ((GrailsCacheManagerAware) this).grailsCacheManager
        }
    }

    static class TraitBelowMethod extends MethodAbove implements GrailsCacheManagerAware {
    }

    /** A cache-aware class with an autowired field of its own, whose bean creates a cache manager. */
    static class OwnField implements GrailsCacheManagerAware, Seeing {
        @Autowired
        OwnDependency ownDependency

        CacheManager getSeen() {
            null
        }
    }

    /** The subclass of a cache-aware class, with an autowired field of its own, whose bean creates a cache manager. */
    static class FieldInSubclass extends CacheAware implements Seeing {
        @Autowired
        OwnDependency ownDependency

        CacheManager getSeen() {
            null
        }
    }

    static class OwnDependency {
    }

    /** A class that is not cache-aware, with an autowired method. */
    static class OtherWithMethod {
        int initCalls

        @Autowired
        void init(BeanFactory beanFactory) {
            initCalls++
        }
    }

    /** A cache-aware class with a {@code @Value} method after the field, and an autowired method that reads it. */
    static class ValueAfterTheField implements GrailsCacheManagerAware {
        CacheManager seen
        String valued
        int initCalls

        @Autowired
        void init(BeanFactory beanFactory) {
            initCalls++
            seen = grailsCacheManager
        }

        @Value('a value')
        void valued(String value) {
            valued = value
        }
    }

    /** A cache-aware class with a method annotated for both {@code @Resource} and {@code @Autowired}, reading the field. */
    static class ResourceAndAutowired implements GrailsCacheManagerAware {
        CacheManager seen
        String valued
        int initCalls

        @Resource
        @Autowired
        void init(BeanFactory beanFactory) {
            initCalls++
            seen = grailsCacheManager
        }
    }

    /** A cache-aware class with a method both injected by name with {@code @Resource} and autowired. */
    static class ByResourceName implements GrailsCacheManagerAware, Seeing {
        CacheManager seen

        @Resource(name = 'grailsCacheManager')
        @Autowired
        void named(CacheManager cacheManager) {
            seen = cacheManager
        }
    }

    /** A bean of the application's own that injects a cache manager by type, as the trait does. */
    static class OwnInjection {
        @Autowired(required = false)
        CacheManager someCacheManager
    }

    /** A second cache manager of a class of its own. */
    static class OtherCacheManager extends ConcurrentMapCacheManager {
    }

    @Priority(1)
    static class HighPriorityCacheManager extends ConcurrentMapCacheManager {
    }

    /** A resolvable dependency that makes a new cache manager each time it is asked, nothing the first time, or nothing. */
    static class CacheManagerFactory implements ObjectFactory<CacheManager> {
        int asked
        String makes

        @Override
        CacheManager getObject() {
            asked++
            if (makes == 'alwaysEmpty' || (makes == 'emptyFirst' && asked == 1)) {
                return null
            }
            new NamedCacheManager("made ${asked}".toString())
        }
    }

    /** A factory bean, created at startup, that fails each time it is asked for its cache manager. */
    static class FailingFactoryBean implements FactoryBean<CacheManager> {
        int asked

        @Override
        CacheManager getObject() {
            asked++
            throw new IllegalStateException('the store is down')
        }

        @Override
        Class<?> getObjectType() {
            CacheManager
        }
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

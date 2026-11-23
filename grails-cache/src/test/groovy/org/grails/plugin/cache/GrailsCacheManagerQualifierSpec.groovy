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

import org.springframework.aot.AotDetector
import org.springframework.aot.generate.DefaultGenerationContext
import org.springframework.aot.generate.FileSystemGeneratedFiles
import org.springframework.aot.generate.NameGenerator
import org.springframework.aot.hint.RuntimeHints
import org.springframework.beans.BeansException
import org.springframework.beans.factory.BeanFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.config.BeanDefinition
import org.springframework.beans.factory.config.BeanDefinitionCustomizer
import org.springframework.cache.CacheManager
import org.springframework.cache.concurrent.ConcurrentMapCacheManager
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.annotation.AnnotationConfigUtils
import org.springframework.context.aot.ApplicationContextAotGenerator
import org.springframework.context.support.GenericApplicationContext
import org.springframework.core.SpringProperties
import org.springframework.javapoet.ClassName

/**
 * Which cache manager a cache-aware class compiled against this version is injected with: the bean named
 * {@code grailsCacheManager}, or the bean with that alias, which the trait's field is qualified with, and no other.
 *
 * <p>Over the arrangements of {@link CacheManagerArrangementsSpec}, what the class, three instances of a prototype
 * of it, and a cache-aware {@code grailsCacheManager} get depends only on how {@code grailsCacheManager} is defined,
 * with the alias registrar and without it: no other cache manager, primary, with a {@code @Priority} or not, is
 * taken or created for the field.</p>
 */
class GrailsCacheManagerQualifierSpec extends CacheManagerArrangementsSpec {

    /** What the class gets, by how {@code grailsCacheManager} is defined. */
    private static final Map<String, String> GETS = [
            absent: null, plain: NAMED, primary: NAMED, lazy: NAMED, notCandidate: null, wrongType: null,
            // created from the cache-aware bean, which asks for it while it is created
            cyclic: 'failed',
            // Spring injects a bean into itself where it is the only candidate, as for any field
            cacheAware: NAMED,
            fallback: NAMED, notDefault: NAMED, priority: NAMED,
            // Spring's null bean, which a factory method that returns null makes
            nullBean: null]

    @Override
    protected Class<?> getProbeType() {
        CacheAware
    }

    void 'the trait\'s field is qualified with the name grailsCacheManager'() {
        given:
        Field field = CacheAware.getDeclaredField(GrailsCacheManagerAliasRegistrar.ALIAS)

        expect:
        field.getAnnotation(Qualifier).value() == NAMED
        field.getAnnotation(Autowired).required() == false
    }

    void 'the cache manager a cache-aware class gets depends only on how grailsCacheManager is defined'() {
        given:
        List<Map<String, String>> rows = arrangements()
        assert rows.size() == NAMED_KINDS.size() * (1 + OTHER_KINDS.size() + OTHER_KINDS.size() * OTHER_KINDS.size())
        assert GETS.keySet() == NAMED_KINDS as Set

        expect: 'every arrangement, each in a context of its own, with the alias registrar and without it'
        List<String> mismatches = []
        rows.each { Map<String, String> row ->
            String expected = GETS[row[NAMED] ?: 'absent']
            [true, false].each { boolean withAlias ->
                Map<String, Object> now = run(row, withAlias)
                Object got = now.failed ? 'failed' : now.chosen
                // what the context creates after it starts: grailsCacheManager where it is lazy, and the cache managers
                // the key generator creates for itself, and nothing else
                List<String> mayCreate = [NAMED] + row.findAll { String name, String kind -> kind == 'createdByKeyGenerator' }*.key
                if (got != expected) {
                    mismatches << "${row} (alias ${withAlias}): got ${got}, expected ${expected}".toString()
                }
                else if (!((List<String>) now.created).every { String name -> name in mayCreate }) {
                    mismatches << "${row} (alias ${withAlias}): created ${now.created}".toString()
                }
                else if (!now.failed && (now.prototypes != [expected] * 3 || now.createdByPrototypes != [])) {
                    mismatches << "${row} (alias ${withAlias}): prototypes got ${now.prototypes} and created ${now.createdByPrototypes}".toString()
                }
                else if (!now.failed && ((List<String>) now.dependencies - ['customCacheKeyGenerator']) != (expected ? [NAMED] : [])) {
                    mismatches << "${row} (alias ${withAlias}): depends on ${now.dependencies}".toString()
                }
                else if (row[NAMED] == 'cacheAware' && now.self != NAMED) {
                    mismatches << "${row} (alias ${withAlias}): grailsCacheManager got ${now.self}".toString()
                }
            }
        }
        mismatches == []
    }

    void 'an autowired method of a cache-aware class sees grailsCacheManager beside a primary cache manager: #subject.simpleName'() {
        given:
        Map<String, CacheManager> instances = [:]
        GenericApplicationContext context = context([(NAMED): 'plain', first: 'primary'], instances, [], true) { GenericApplicationContext it ->
            it.registerBean('subject', (Class<Object>) subject)
        }

        when:
        Seeing bean = context.getBean('subject', Seeing)

        then: 'Spring injects the field among the class\'s fields, before its methods and its subclasses\' members'
        nameOf(bean.seen, instances) == seen
        nameOf(((GrailsCacheManagerAware) bean).grailsCacheManager, instances) == NAMED
        bean.initCalls == 1

        cleanup:
        context?.close()

        where:
        subject          || seen
        MethodInClass    || NAMED
        InheritsMethod   || NAMED
        MethodInSubclass || NAMED
        // a superclass's methods run before a subclass's fields, the trait's field among them
        TraitBelowMethod || null
    }

    void 'an instance the bean factory autowires, as GORM autowires a data service, gets grailsCacheManager beside a primary one'() {
        given:
        Map<String, CacheManager> instances = [:]
        GenericApplicationContext context = context([(NAMED): 'plain', first: 'primary'], instances, [], true)
        MethodInClass bean = new MethodInClass()

        when:
        context.autowireCapableBeanFactory.autowireBean(bean)

        then:
        nameOf(bean.grailsCacheManager, instances) == NAMED
        nameOf(bean.seen, instances) == NAMED

        cleanup:
        context?.close()
    }

    void 'a cache manager that a cache provider plugin defines under another name with the alias grailsCacheManager is used'() {
        given:
        CacheManager provided = new ConcurrentMapCacheManager()
        CacheManager primary = new ConcurrentMapCacheManager()
        GenericApplicationContext context = new GenericApplicationContext()
        AnnotationConfigUtils.registerAnnotationConfigProcessors(context)
        context.registerBean('providedCacheManager', CacheManager, { -> provided } as Supplier<CacheManager>)
        context.registerAlias('providedCacheManager', NAMED)
        context.registerBean('primaryCacheManager', CacheManager, { -> primary } as Supplier<CacheManager>,
                { BeanDefinition definition -> definition.primary = true } as BeanDefinitionCustomizer)
        context.registerBean('probe', CacheAware)
        context.refresh()

        expect:
        context.getBean('probe', CacheAware).grailsCacheManager.is(provided)

        cleanup:
        context?.close()
    }

    void 'a cache manager registered as a resolvable dependency, which Spring applies no qualifier to, is passed over only for grailsCacheManager: #where'() {
        given:
        CacheManager registered = new ConcurrentMapCacheManager()
        Map<String, CacheManager> instances = [registered: registered]
        GenericApplicationContext context = context(row, instances, [], withAlias) { GenericApplicationContext it ->
            it.beanFactory.registerResolvableDependency(CacheManager, registered)
        }

        expect:
        nameOf(context.getBean('probe', CacheAware).grailsCacheManager, instances) == expected

        cleanup:
        context?.close()

        where:
        where                                       | row                | withAlias || expected
        'beside grailsCacheManager, with the alias' | [(NAMED): 'plain'] | true      || NAMED
        'beside grailsCacheManager, without it'     | [(NAMED): 'plain'] | false     || NAMED
        'where there is none of the name'           | [:]                | true      || 'registered'
    }

    void 'run from the code Spring\'s AOT processing generates, the field is grailsCacheManager beside a primary cache manager'() {
        when: 'the context processed ahead of time, and the code it generates compiled and run'
        Map<String, Object> result = aheadOfTime([other: OtherCacheManager, primary: OtherCacheManager])

        then: 'the generated injection resolves the qualified field among the class\'s other members'
        result.injection.contains("forField(\"${GrailsCacheManagerAliasRegistrar.ALIAS}\")")
        result.seen == NAMED
        result.got == NAMED
    }

    /** Processes a context ahead of time, compiles the code it generates, and starts a context from it. */
    private Map<String, Object> aheadOfTime(Map<String, Class<?>> others) {
        Path dir = Files.createTempDirectory('grails-cache-aot')
        try {
            GenericApplicationContext processed = new GenericApplicationContext()
            AnnotationConfigUtils.registerAnnotationConfigProcessors(processed)
            processed.registerBean(GrailsCacheManagerAliasRegistrar)
            processed.registerBean(NAMED, ConcurrentMapCacheManager)
            others.each { String name, Class<?> type ->
                processed.registerBean(name, (Class<Object>) type,
                        { BeanDefinition definition -> definition.primary = name == 'primary' } as BeanDefinitionCustomizer)
            }
            processed.registerBean('probe', MethodInClass)
            DefaultGenerationContext generation = new DefaultGenerationContext(new NameGenerator(ClassName.get(GrailsCacheManagerQualifierSpec)),
                    new FileSystemGeneratedFiles(dir.resolve('sources')), new RuntimeHints())
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
            Map<String, Object> result = [injection: sources.find { File file -> file.name.endsWith('_MethodInClass__Autowiring.java') }.text]
            URLClassLoader loader = new URLClassLoader([classes.toUri().toURL()] as URL[], getClass().classLoader)
            GenericApplicationContext started = new GenericApplicationContext()
            started.classLoader = loader
            SpringProperties.setProperty(AotDetector.AOT_ENABLED, 'true')
            try {
                ((ApplicationContextInitializer<GenericApplicationContext>) loader.loadClass(initializer.reflectionName())
                        .getDeclaredConstructor().newInstance()).initialize(started)
                started.refresh()
                Map<String, CacheManager> instances = ([NAMED] + others.keySet()).collectEntries { String name ->
                    [(name): started.getBean(name, CacheManager)]
                }
                MethodInClass probe = started.getBean('probe', MethodInClass)
                result.seen = nameOf(probe.seen, instances)
                result.got = nameOf(probe.grailsCacheManager, instances)
            }
            catch (BeansException ignored) {
                result.failed = true
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

    /** A class the cache transformations make cache-aware, compiled against this version. */
    static class CacheAware implements GrailsCacheManagerAware {
    }

    /** What a class's own injection saw of its cache manager. */
    interface Seeing {
        CacheManager getSeen()

        int getInitCalls()
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

    /** A subclass of a cache-aware class that inherits its autowired method. */
    static class InheritsMethod extends MethodInClass {
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

    /** A second cache manager of a class of its own. */
    static class OtherCacheManager extends ConcurrentMapCacheManager {
    }
}

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

import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.Phases
import org.codehaus.groovy.tools.GroovyClass
import org.springframework.beans.BeansException
import org.springframework.beans.factory.FactoryBean
import org.springframework.beans.factory.config.BeanDefinition
import org.springframework.beans.factory.config.BeanDefinitionCustomizer
import org.springframework.beans.factory.support.AbstractBeanDefinition
import org.springframework.cache.CacheManager
import org.springframework.cache.concurrent.ConcurrentMapCacheManager
import org.springframework.context.annotation.AnnotationConfigUtils
import org.springframework.context.support.GenericApplicationContext
import spock.lang.Specification

import grails.plugin.cache.CustomCacheKeyGenerator
import grails.plugin.cache.GrailsCacheKeyGenerator

/**
 * The arrangements of cache managers the matrix specs inject a cache-aware class beside, and how they inject it.
 *
 * <p>They cross the ways a bean named {@code grailsCacheManager} can be defined with none, one or two other
 * cache managers, each defined in one of the ways a bean is chosen or passed over for injection. One way to define
 * {@code grailsCacheManager} creates it from the cache-aware bean, which so has a cache manager injected while
 * {@code grailsCacheManager} is being created, and another makes it cache-aware itself. Of the other cache
 * managers, some have a priority Spring sees only on their object: one declared as a plain {@code CacheManager},
 * the product of a factory bean that names only {@code CacheManager} as its type, and one the cache-aware bean's
 * key generator creates while that bean is injected; another is a factory bean that makes a new cache manager each
 * time it is asked.</p>
 */
abstract class CacheManagerArrangementsSpec extends Specification {

    protected static final String NAMED = GrailsCacheManagerAliasRegistrar.CACHE_MANAGER_BEAN_NAME

    protected static final List<String> NAMED_KINDS = ['absent', 'plain', 'primary', 'lazy', 'notCandidate', 'wrongType', 'cyclic',
            'cacheAware', 'fallback', 'notDefault', 'priority', 'nullBean']

    protected static final List<String> OTHER_KINDS = ['plain', 'primary', 'lazy', 'notCandidate', 'fallback', 'notDefault',
            'priority', 'priorityOnObject', 'factoryBean', 'createdByKeyGenerator', 'prototypeFactoryBean']

    /** The cache-aware class the arrangements create as the bean {@code probe} and the prototype {@code prototype}. */
    protected abstract Class<?> getProbeType()

    protected Map<String, Object> run(Map<String, String> row, boolean withAlias) {
        Map<String, CacheManager> instances = [:]
        List<String> created = []
        List<String> atStartup = []
        Map<String, Object> result = [failed: false, failedAtStartup: false]
        GenericApplicationContext started = null
        try {
            try {
                // a cyclic grailsCacheManager has the probe injected while the context starts
                started = context(row, instances, created, withAlias)
            }
            catch (BeansException e) {
                result.failedAtStartup = true
                throw e
            }
            atStartup.addAll(created)
            CacheManager chosen = ((GrailsCacheManagerAware) started.getBean('probe')).grailsCacheManager
            result.chosen = nameOf(chosen, instances)
            if (row[NAMED] == 'cacheAware') {
                result.self = nameOf(((GrailsCacheManagerAware) started.getBean(NAMED)).grailsCacheManager, instances)
            }
            result.dependencies = (started.beanFactory.getDependenciesForBean('probe') as List<String>).sort()
            int beforePrototypes = created.size()
            result.prototypes = (1..3).collect { nameOf(((GrailsCacheManagerAware) started.getBean('prototype')).grailsCacheManager, instances) }
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

    protected static String nameOf(CacheManager cacheManager, Map<String, CacheManager> instances) {
        cacheManager == null ? null : instances.find { String name, CacheManager instance -> instance.is(cacheManager) }?.key
    }

    protected GenericApplicationContext context(Map<String, String> row, Map<String, CacheManager> instances,
                                              List<String> created, boolean withAlias,
                                              Closure<?> beforeRefresh = null) {
        GenericApplicationContext context = new GenericApplicationContext()
        AnnotationConfigUtils.registerAnnotationConfigProcessors(context)
        if (withAlias) {
            context.addBeanFactoryPostProcessor(new GrailsCacheManagerAliasRegistrar())
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
        context.registerBean('probe', (Class<Object>) probeType, lazy)
        context.registerBean('prototype', (Class<Object>) probeType,
                { BeanDefinition definition -> definition.scope = BeanDefinition.SCOPE_PROTOTYPE } as BeanDefinitionCustomizer)
        beforeRefresh?.call(context)
        context.refresh()
        context
    }

    protected static List<Map<String, String>> arrangements() {
        List<Map<String, String>> others = [[:]]
        OTHER_KINDS.each { String first -> others << [first: first] }
        OTHER_KINDS.each { String first -> OTHER_KINDS.each { String second -> others << [first: first, second: second] } }
        NAMED_KINDS.collectMany { String named ->
            others.collect { Map<String, String> other -> named == 'absent' ? other : ([(NAMED): named] + other) }
        }
    }

    /**
     * Compiles the source with the trait as Grails 8 has it, and loads only the class, so that the trait it runs
     * against is this one, as for a plugin compiled against Grails 8.
     */
    protected static Class<?> compiledAgainstGrails8(String source, String className) {
        String trait8 = CacheManagerArrangementsSpec.getResource('GrailsCacheManagerAware-8.x.groovy.txt').text
        CompilationUnit unit = new CompilationUnit(new CompilerConfiguration(), null, new GroovyClassLoader(CacheManagerArrangementsSpec.classLoader))
        unit.addSource('GrailsCacheManagerAware.groovy', trait8)
        unit.addSource(className.tokenize('.').last() + '.groovy', source)
        // up to class generation only, so that nothing is written to the working directory
        unit.compile(Phases.CLASS_GENERATION)
        Map<String, byte[]> classes = unit.classes.collectEntries { GroovyClass compiled -> [(compiled.name): compiled.bytes] }
        assert classes.keySet().any { String name -> name.startsWith('org.grails.plugin.cache.GrailsCacheManagerAware') }
        ClassLoader loader = new ClassLoader(CacheManagerArrangementsSpec.classLoader) {
            @Override
            protected Class<?> findClass(String name) {
                byte[] bytes = name.startsWith('org.grails.plugin.cache.') ? null : classes[name]
                if (bytes == null) {
                    throw new ClassNotFoundException(name)
                }
                defineClass(name, bytes, 0, bytes.length)
            }
        }
        loader.loadClass(className)
    }

    @Priority(1)
    static class HighPriorityCacheManager extends ConcurrentMapCacheManager {
    }

    /** A grailsCacheManager that is cache-aware itself, which Spring leaves out of the candidates for its own field. */
    static class CacheAwareCacheManager extends ConcurrentMapCacheManager implements GrailsCacheManagerAware {
    }

    /** A bean a cache-aware class's own field is injected with, whose creation creates cache managers. */
    static class OwnDependency {
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

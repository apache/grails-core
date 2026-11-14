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

import ch.qos.logback.classic.Level
import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.tools.GroovyClass
import org.springframework.beans.BeansException
import org.springframework.beans.factory.ObjectFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.AutowiredAnnotationBeanPostProcessor
import org.springframework.beans.factory.config.BeanDefinition
import org.springframework.beans.factory.config.BeanDefinitionCustomizer
import org.springframework.beans.factory.config.Scope
import org.springframework.beans.factory.support.AbstractBeanDefinition
import org.springframework.beans.factory.support.DefaultListableBeanFactory
import org.springframework.beans.factory.support.RootBeanDefinition
import org.springframework.beans.factory.support.ScopeNotActiveException
import org.springframework.cache.CacheManager
import org.springframework.cache.concurrent.ConcurrentMapCacheManager
import org.springframework.context.annotation.AnnotationConfigUtils
import org.springframework.context.support.GenericApplicationContext
import spock.lang.Shared
import spock.lang.Specification

import org.apache.grails.core.testing.support.LogCapture

/**
 * Which cache manager a cache-aware class gets, against the oracle of what Spring injected into the
 * trait's {@code @Autowired(required = false) CacheManager} field before the trait chose it itself.
 * The matrix crosses the ways a bean named {@code grailsCacheManager} can be defined with none, one
 * or two other cache managers, each defined in one of the ways a bean is chosen or passed over for
 * injection, all in one context; the features after it cover the arrangements it does not.
 *
 * <p>The deliberate differences: a cache manager named {@code grailsCacheManager} is used whenever
 * there is one, as the plugin and its tag library use it by name; and where Spring cannot choose, which
 * failed startup, there is none. Either way no cache manager is created that the injection would not
 * have created, other than the one returned.</p>
 */
class GrailsCacheManagerLookupMatrixSpec extends Specification {

    private static final List<String> NAMED = ['absent', 'plain', 'primary', 'lazy', 'notCandidate', 'wrongType']

    private static final List<String> OTHER = ['plain', 'primary', 'lazy', 'notCandidate', 'fallback', 'notDefault', 'priority']

    @Shared
    List<Map<String, String>> rows = arrangements()

    void 'the cache manager a cache-aware class gets matches what Spring injected, except where deliberate'() {
        expect: 'every arrangement, each run against the oracle in a context of its own'
        List<String> mismatches = []
        rows.each { Map<String, String> row ->
            Map<String, Object> oracle = run(row, Injected)
            Map<String, Object> lookup = run(row, CacheAware)
            Object expected
            if (row.containsKey(GrailsCacheManagerLookup.BEAN_NAME) && row[GrailsCacheManagerLookup.BEAN_NAME] != 'wrongType') {
                expected = GrailsCacheManagerLookup.BEAN_NAME
            }
            else {
                expected = oracle.failed ? null : oracle.chosen
            }
            List<String> allowedToCreate = ((List<String>) oracle.created) + (expected ? [expected as String] : [])
            if (lookup.failed) {
                mismatches << "${row}: the lookup failed: ${lookup.failure}".toString()
            }
            else if (lookup.chosen != expected) {
                mismatches << "${row}: got ${lookup.chosen}, expected ${expected} (Spring injected ${oracle.failed ? 'nothing, it failed' : oracle.chosen})".toString()
            }
            else if (!allowedToCreate.containsAll((List<String>) lookup.created)) {
                mismatches << "${row}: created ${lookup.created}, where the injection created ${oracle.created}".toString()
            }
        }
        mismatches == []
    }

    void 'the matrix covers the arrangements it says it does'() {
        expect: 'six arrangements of the named one, and none, one or two of the seven others'
        rows.size() == NAMED.size() * (1 + OTHER.size() + OTHER.size() * OTHER.size())
    }

    void 'where Spring cannot choose, the class does not cache, and a warning says why'() {
        given: 'two cache managers, neither primary nor named grailsCacheManager, which failed startup when injected'
        GenericApplicationContext context = context([first: 'plain', second: 'plain'], [:])
        LogCapture log = new LogCapture(GrailsCacheManagerLookup, Level.WARN)

        when:
        CacheManager found = context.getBean('probe', CacheAware).grailsCacheManager

        then: 'it names the class and the cache managers, in Spring\'s words'
        found == null
        log.events.size() == 1
        log.events[0].formattedMessage.startsWith("${CacheAware.name} does not cache: there is no cache manager named grailsCacheManager")
        log.events[0].formattedMessage.contains('first,second')

        cleanup:
        log?.close()
        context?.close()
    }

    void 'two cache managers marked primary are reported in Spring\'s words, rather than as a list it does not give'() {
        given:
        GenericApplicationContext context = context([first: 'primary', second: 'primary'], [:])
        LogCapture log = new LogCapture(GrailsCacheManagerLookup, Level.WARN)

        when:
        CacheManager found = context.getBean('probe', CacheAware).grailsCacheManager

        then:
        found == null
        log.events.size() == 1
        log.events[0].formattedMessage.contains("more than one 'primary' bean found")
        !log.events[0].formattedMessage.contains('null')

        cleanup:
        log?.close()
        context?.close()
    }

    void 'a primary cache manager passed over for grailsCacheManager is reported once'() {
        given: 'an application cache manager marked primary, and two cache-aware beans'
        GenericApplicationContext context = context([grailsCacheManager: 'plain', redis: 'primary'], [:])
        context.registerBean('another', CacheAware, { -> new CacheAware() } as Supplier<CacheAware>)
        LogCapture log = new LogCapture(GrailsCacheManagerLookup, Level.INFO)

        when:
        context.getBean('probe', CacheAware).grailsCacheManager
        context.getBean('another', CacheAware).grailsCacheManager

        then:
        log.events*.formattedMessage == ['Grails caches with the cache manager named grailsCacheManager, not with redis, ' +
                'which Spring prefers as primary or by priority. Define the one to cache with as grailsCacheManager.']

        cleanup:
        log?.close()
        context?.close()
    }

    void 'a cache manager passed over that Spring prefers by priority, or that is primary in a parent context, is reported'() {
        given:
        GenericApplicationContext parent = context([redis: 'primary'], [:])
        GenericApplicationContext child = context([grailsCacheManager: 'plain', hazelcast: 'priority'], [:], [], CacheAware, parent)
        LogCapture log = new LogCapture(GrailsCacheManagerLookup, Level.INFO)

        when:
        child.getBean('probe', CacheAware).grailsCacheManager

        then:
        log.events.size() == 1
        log.events[0].formattedMessage.contains('not with hazelcast, redis,')

        cleanup:
        log?.close()
        child?.close()
        parent?.close()
    }

    void 'a cache manager registered as a singleton beside grailsCacheManager is reported by its priority, and fails nothing'() {
        given: 'cache managers with no bean definition, one of a class with a priority'
        GenericApplicationContext context = context([grailsCacheManager: 'plain'], [:])
        context.beanFactory.registerSingleton('plainSingleton', new ConcurrentMapCacheManager())
        context.beanFactory.registerSingleton('prioritySingleton', new HighPriorityCacheManager())
        LogCapture log = new LogCapture(GrailsCacheManagerLookup, Level.INFO)

        when:
        CacheManager found = context.getBean('probe', CacheAware).grailsCacheManager

        then:
        found.is(context.getBean('grailsCacheManager'))
        log.events*.formattedMessage == ['Grails caches with the cache manager named grailsCacheManager, not with ' +
                'prioritySingleton, which Spring prefers as primary or by priority. Define the one to cache with as grailsCacheManager.']

        cleanup:
        log?.close()
        context?.close()
    }

    void 'a cache manager is not chosen for being named like a parameter of the injection method'() {
        given: 'a cache-aware class compiled keeping parameter names, as the Grails Gradle plugin compiles an application'
        Class<?> compiled = compiledWithParameterNames('''
            package names
            class NamedCacheAware implements org.grails.plugin.cache.GrailsCacheManagerAware {
            }
        ''', 'names.NamedCacheAware')

        and: 'cache managers named as such a parameter might be, none primary, which Spring matches by name only then'
        GenericApplicationContext context = context(
                [cacheManagers: 'plain', cacheManager: 'plain', candidates: 'plain', beanFactory: 'plain'], [:], [], compiled)

        expect: 'none is chosen for its name, as none was for the field the trait had, whose name matched no bean'
        context.getBean('probe').grailsCacheManager == null

        cleanup:
        context?.close()
    }

    void 'a primary cache manager that Spring would not inject is not reported as passed over'() {
        given: 'one excluded from injection, and one that is not a default candidate'
        GenericApplicationContext context = context(
                [grailsCacheManager: 'plain', first: 'primary+notCandidate', second: 'primary+notDefault'], [:])
        LogCapture log = new LogCapture(GrailsCacheManagerLookup, Level.INFO)

        when:
        context.getBean('probe', CacheAware).grailsCacheManager

        then:
        log.events == []

        cleanup:
        log?.close()
        context?.close()
    }

    void 'in a bean factory that is not an application context, the cache manager is still the one Spring injects'() {
        given: 'a bare bean factory, which does not inject itself, processing @Autowired'
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory()
        beanFactory.addBeanPostProcessor(new AutowiredAnnotationBeanPostProcessor(beanFactory: beanFactory))
        CacheManager only = new ConcurrentMapCacheManager()
        beanFactory.registerSingleton('cacheManager', only)
        beanFactory.registerBeanDefinition('probe', new RootBeanDefinition(CacheAware))

        expect:
        beanFactory.getBean('probe', CacheAware).grailsCacheManager.is(only)
    }

    void 'a cache manager whose scope is not active fails the bean, as its injection did'() {
        given: 'the only cache manager, in a scope with no active instance, with no proxy'
        GenericApplicationContext context = new GenericApplicationContext()
        AnnotationConfigUtils.registerAnnotationConfigProcessors(context)
        context.beanFactory.registerScope('inactive', new Scope() {
            Object get(String name, ObjectFactory<?> factory) {
                throw new ScopeNotActiveException(name, 'inactive', new IllegalStateException('no active instance'))
            }
            Object remove(String name) { null }
            void registerDestructionCallback(String name, Runnable callback) { }
            Object resolveContextualObject(String key) { null }
            String getConversationId() { null }
        })
        BeanDefinitionCustomizer inactive = { BeanDefinition definition -> definition.scope = 'inactive' } as BeanDefinitionCustomizer
        context.registerBean('cacheManager', CacheManager, { -> new ConcurrentMapCacheManager() } as Supplier<CacheManager>, inactive)
        BeanDefinitionCustomizer lazy = { BeanDefinition definition -> definition.lazyInit = true } as BeanDefinitionCustomizer
        context.registerBean('injected', Injected, lazy)
        context.registerBean('probe', CacheAware, lazy)
        context.refresh()

        when:
        context.getBean('injected')

        then: 'the oracle'
        thrown(BeansException)

        when:
        context.getBean('probe')

        then:
        BeansException e = thrown()
        e.mostSpecificCause instanceof IllegalStateException

        cleanup:
        context?.close()
    }

    void 'no primary cache manager is reported where grailsCacheManager is an alias of the primary one'() {
        given: 'the primary cache manager, under another name, aliased as grailsCacheManager'
        GenericApplicationContext context = context([redis: 'primary'], [:])
        context.registerAlias('redis', 'grailsCacheManager')
        LogCapture log = new LogCapture(GrailsCacheManagerLookup, Level.INFO)

        when:
        context.getBean('probe', CacheAware).grailsCacheManager

        then:
        log.events == []

        cleanup:
        log?.close()
        context?.close()
    }

    void 'the cache manager is chosen when the bean is created, as an injected one was'() {
        given: 'a bean created in a context with no cache manager'
        GenericApplicationContext context = context([:], [:])
        CacheAware aware = context.getBean('probe', CacheAware)

        when: 'one is defined before the bean is first asked for its cache manager'
        context.registerBean('later', CacheManager, { -> new ConcurrentMapCacheManager() } as Supplier<CacheManager>)

        then: 'it would be found, but the choice was made when the bean was created'
        context.getBean('later') instanceof CacheManager
        aware.grailsCacheManager == null

        cleanup:
        context.close()
    }

    void 'a class compiled against the trait 8.0.0 shipped works with this one'() {
        given: 'a cached class compiled together with that trait, of which only the class is loaded beside this one'
        Class<?> compiled = compiledAgainst800('''
            package compat
            class OldCacheAware implements org.grails.plugin.cache.GrailsCacheManagerAware {
            }
        ''', 'compat.OldCacheAware')

        expect: 'created on its own, it has no cache manager, rather than throwing'
        compiled.getDeclaredConstructor().newInstance().grailsCacheManager == null

        when: 'created as a bean, it is injected through the field it declares, as with 8.0.0'
        CacheManager only = new ConcurrentMapCacheManager()
        GenericApplicationContext context = new GenericApplicationContext()
        AnnotationConfigUtils.registerAnnotationConfigProcessors(context)
        context.registerBean('cacheManager', CacheManager, { -> only } as Supplier<CacheManager>)
        context.registerBean('old', (Class<Object>) compiled)
        context.refresh()

        then:
        context.getBean('old').grailsCacheManager.is(only)

        cleanup:
        context?.close()
    }

    /** Compiles the source against this trait, keeping parameter names, and loads it. */
    private Class<?> compiledWithParameterNames(String source, String className) {
        CompilerConfiguration configuration = new CompilerConfiguration()
        configuration.parameters = true
        new GroovyClassLoader(getClass().classLoader, configuration).with {
            parseClass(source)
            loadClass(className)
        }
    }

    /**
     * Compiles the source with the trait as 8.0.0 shipped it, and loads only the class, so that the
     * trait it runs against is this one, as for a plugin compiled against 8.0.0.
     */
    private Class<?> compiledAgainst800(String source, String className) {
        String trait800 = getClass().getResource('GrailsCacheManagerAware-8.0.0.groovy.txt').text
        CompilationUnit unit = new CompilationUnit(new CompilerConfiguration(), null, new GroovyClassLoader(getClass().classLoader))
        unit.addSource('GrailsCacheManagerAware.groovy', trait800)
        unit.addSource('OldCacheAware.groovy', source)
        unit.compile()
        Map<String, byte[]> classes = unit.classes.collectEntries { GroovyClass compiled -> [(compiled.name): compiled.bytes] }
        assert classes.keySet().any { it.startsWith('org.grails.plugin.cache.GrailsCacheManagerAware') }
        ClassLoader loader = new ClassLoader(getClass().classLoader) {
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

    private Map<String, Object> run(Map<String, String> row, Class<?> probe) {
        Map<String, CacheManager> instances = [:]
        List<String> created = []
        GenericApplicationContext context = context(row, instances, created, probe)
        List<String> atStartup = new ArrayList<>(created)
        Map<String, Object> result = [failed: false]
        try {
            Object bean = context.getBean('probe')
            CacheManager chosen = probe == Injected ? ((Injected) bean).injected : ((CacheAware) bean).grailsCacheManager
            result.chosen = chosen == null ? null : instances.find { String name, CacheManager instance -> instance.is(chosen) }?.key
        }
        catch (BeansException e) {
            result.failed = true
            result.failure = e.message
        }
        result.created = created - atStartup
        context.close()
        result
    }

    private GenericApplicationContext context(Map<String, String> row, Map<String, CacheManager> instances,
                                              List<String> created = [], Class<?> probe = CacheAware,
                                              GenericApplicationContext parent = null) {
        GenericApplicationContext context = new GenericApplicationContext(parent)
        AnnotationConfigUtils.registerAnnotationConfigProcessors(context)
        row.each { String name, String kind ->
            if (kind == 'wrongType') {
                context.registerBean(name, String, { -> 'not a cache manager' } as Supplier<String>)
                return
            }
            Set<String> attributes = kind.split(/\+/) as Set<String>
            Class<? extends CacheManager> type = 'priority' in attributes ? HighPriorityCacheManager : ConcurrentMapCacheManager
            Supplier<CacheManager> supplier = { ->
                created << name
                CacheManager instance = type.getDeclaredConstructor().newInstance()
                instances[name] = instance
                instance
            } as Supplier<CacheManager>
            BeanDefinitionCustomizer customizer = { BeanDefinition definition ->
                AbstractBeanDefinition bean = (AbstractBeanDefinition) definition
                bean.primary = 'primary' in attributes
                bean.lazyInit = 'lazy' in attributes
                bean.autowireCandidate = !('notCandidate' in attributes)
                bean.fallback = 'fallback' in attributes
                bean.defaultCandidate = !('notDefault' in attributes)
            } as BeanDefinitionCustomizer
            context.registerBean(name, (Class<CacheManager>) type, supplier, customizer)
        }
        BeanDefinitionCustomizer lazy = { BeanDefinition definition -> definition.lazyInit = true } as BeanDefinitionCustomizer
        context.registerBean('probe', (Class<Object>) probe, lazy)
        context.refresh()
        context
    }

    private static List<Map<String, String>> arrangements() {
        List<Map<String, String>> others = [[:]]
        OTHER.each { String first -> others << [first: first] }
        OTHER.each { String first -> OTHER.each { String second -> others << [first: first, second: second] } }
        NAMED.collectMany { String named ->
            others.collect { Map<String, String> other ->
                named == 'absent' ? other : ([(GrailsCacheManagerLookup.BEAN_NAME): named] + other)
            }
        }
    }

    /** What Spring injected into the trait's field, under a name that, like the trait's renamed field, matches no bean. */
    static class Injected {

        @Autowired(required = false)
        CacheManager injected
    }

    static class CacheAware implements GrailsCacheManagerAware {
    }

    @Priority(1)
    static class HighPriorityCacheManager extends ConcurrentMapCacheManager {
    }
}

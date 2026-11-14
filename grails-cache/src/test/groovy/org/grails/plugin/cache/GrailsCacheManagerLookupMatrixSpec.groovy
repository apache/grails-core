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
import org.springframework.beans.BeansException
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.config.BeanDefinition
import org.springframework.beans.factory.config.BeanDefinitionCustomizer
import org.springframework.beans.factory.support.AbstractBeanDefinition
import org.springframework.cache.CacheManager
import org.springframework.cache.concurrent.ConcurrentMapCacheManager
import org.springframework.context.annotation.AnnotationConfigUtils
import org.springframework.context.support.GenericApplicationContext
import spock.lang.Shared
import spock.lang.Specification

import org.apache.grails.core.testing.support.LogCapture

/**
 * Which cache manager a cache-aware class gets, for every arrangement of cache managers, against the
 * oracle of what Spring injected into the trait's {@code @Autowired(required = false) CacheManager}
 * field before it looked the cache manager up.
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

        then:
        found == null
        log.events*.formattedMessage == ["${CacheAware.name} does not cache: there is no cache manager named grailsCacheManager, " +
                'and Spring cannot choose among [first, second]. Mark one of them primary, or define the one to cache with as ' +
                'grailsCacheManager.'] as List<String>

        cleanup:
        log?.close()
        context?.close()
    }

    void 'the first answer stands, as an injected cache manager did, so a lookup that finds none is not repeated'() {
        given: 'a context with no cache manager'
        GenericApplicationContext context = context([:], [:])
        CacheAware aware = context.getBean('probe', CacheAware)

        when: 'one is defined after the first lookup'
        CacheManager first = aware.grailsCacheManager
        context.registerBean('later', CacheManager, { -> new ConcurrentMapCacheManager() } as Supplier<CacheManager>)

        then: 'it would be found, but the lookup is not made again'
        first == null
        context.getBean('later') instanceof CacheManager
        aware.grailsCacheManager == null

        cleanup:
        context.close()
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
                                              List<String> created = [], Class<?> probe = CacheAware) {
        GenericApplicationContext context = new GenericApplicationContext()
        AnnotationConfigUtils.registerAnnotationConfigProcessors(context)
        row.each { String name, String kind ->
            if (kind == 'wrongType') {
                context.registerBean(name, String, { -> 'not a cache manager' } as Supplier<String>)
                return
            }
            Class<? extends CacheManager> type = kind == 'priority' ? HighPriorityCacheManager : ConcurrentMapCacheManager
            Supplier<CacheManager> supplier = { ->
                created << name
                CacheManager instance = type.getDeclaredConstructor().newInstance()
                instances[name] = instance
                instance
            } as Supplier<CacheManager>
            BeanDefinitionCustomizer customizer = { BeanDefinition definition ->
                AbstractBeanDefinition bean = (AbstractBeanDefinition) definition
                bean.primary = kind == 'primary'
                bean.lazyInit = kind == 'lazy'
                bean.autowireCandidate = kind != 'notCandidate'
                bean.fallback = kind == 'fallback'
                bean.defaultCandidate = kind != 'notDefault'
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

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
import org.springframework.beans.factory.config.BeanDefinition
import org.springframework.beans.factory.config.BeanDefinitionCustomizer
import org.springframework.beans.factory.support.AbstractBeanDefinition
import org.springframework.cache.CacheManager
import org.springframework.cache.concurrent.ConcurrentMapCacheManager
import org.springframework.context.annotation.AnnotationConfigUtils
import org.springframework.context.support.GenericApplicationContext
import spock.lang.Specification

/**
 * Which cache manager a cache-aware class is injected with, with the alias of {@code grailsCacheManager},
 * against the oracle of 8.0.0: the same class, in the same context, without it.
 *
 * <p>The matrix crosses the ways a bean named {@code grailsCacheManager} can be defined with none, one
 * or two other cache managers, each defined in one of the ways a bean is chosen or passed over for
 * injection. The deliberate differences: where Spring could not choose, which failed startup, the class
 * gets {@code grailsCacheManager}, unless two cache managers are marked primary, which Spring rejects
 * before it looks at names; and where it chose a cache manager by its {@code @Priority} beside
 * {@code grailsCacheManager}, the name now comes first. Otherwise the choice, and every failure, is
 * 8.0.0's, and no cache manager is created that 8.0.0 did not create, other than {@code grailsCacheManager}.</p>
 */
class GrailsCacheManagerAliasMatrixSpec extends Specification {

    private static final String NAMED = GrailsCacheManagerAliasRegistrar.CACHE_MANAGER_BEAN_NAME

    private static final List<String> NAMED_KINDS = ['absent', 'plain', 'primary', 'lazy', 'notCandidate', 'wrongType']

    private static final List<String> OTHER_KINDS = ['plain', 'primary', 'lazy', 'notCandidate', 'fallback', 'notDefault', 'priority']

    void 'the alias is the name the trait\'s field has in a class that implements it'() {
        expect:
        CacheAware.declaredFields*.name.contains(GrailsCacheManagerAliasRegistrar.ALIAS)
    }

    void 'the cache manager a cache-aware class gets is 8.0.0\'s, except where it failed or chose by priority'() {
        given:
        List<Map<String, String>> rows = arrangements()
        assert rows.size() == NAMED_KINDS.size() * (1 + OTHER_KINDS.size() + OTHER_KINDS.size() * OTHER_KINDS.size())

        expect: 'every arrangement, each in a context of its own, with the alias and without it'
        List<String> mismatches = []
        rows.each { Map<String, String> row ->
            Map<String, Object> was = run(row, false)
            Map<String, Object> now = run(row, true)
            boolean namedUsable = row[NAMED] in ['plain', 'primary', 'lazy']
            // Spring rejects two primary candidates before it looks at names, as it did in 8.0.0
            boolean twoPrimaries = row.values().count { it == 'primary' } > 1
            boolean choseByPriority = !was.failed && was.chosen != NAMED && row[was.chosen as String] == 'priority'
            Object expected = (namedUsable && !twoPrimaries && (was.failed || choseByPriority)) ? NAMED :
                    (was.failed ? 'failed' : was.chosen)
            Object got = now.failed ? 'failed' : now.chosen
            List<String> allowedToCreate = ((List<String>) was.created) + [NAMED]
            if (got != expected) {
                mismatches << "${row}: got ${got}, expected ${expected} (8.0.0: ${was.failed ? 'failed' : was.chosen})".toString()
            }
            else if (!allowedToCreate.containsAll((List<String>) now.created)) {
                mismatches << "${row}: created ${now.created}, where 8.0.0 created ${was.created}".toString()
            }
        }
        mismatches == []
    }

    void 'no alias is registered where there is no grailsCacheManager, or where its name is taken'() {
        given:
        GenericApplicationContext withNone = context([first: 'plain'], [:], [], true)
        GenericApplicationContext taken = context([(NAMED): 'plain'], [:], [], false)
        taken.beanFactory.registerAlias('first', GrailsCacheManagerAliasRegistrar.ALIAS)
        taken.registerBean('first', CacheManager, { -> new ConcurrentMapCacheManager() } as Supplier<CacheManager>)

        when:
        new GrailsCacheManagerAliasRegistrar().postProcessBeanFactory(taken.beanFactory)

        then:
        !withNone.beanFactory.isAlias(GrailsCacheManagerAliasRegistrar.ALIAS)
        taken.beanFactory.getAliases('first').contains(GrailsCacheManagerAliasRegistrar.ALIAS)
        !taken.beanFactory.getAliases(NAMED).contains(GrailsCacheManagerAliasRegistrar.ALIAS)

        cleanup:
        withNone?.close()
        taken?.close()
    }

    private Map<String, Object> run(Map<String, String> row, boolean withAlias) {
        Map<String, CacheManager> instances = [:]
        List<String> created = []
        GenericApplicationContext context = context(row, instances, created, withAlias)
        List<String> atStartup = new ArrayList<>(created)
        Map<String, Object> result = [failed: false]
        try {
            CacheManager chosen = ((CacheAware) context.getBean('probe')).grailsCacheManager
            result.chosen = chosen == null ? null : instances.find { String name, CacheManager instance -> instance.is(chosen) }?.key
        }
        catch (BeansException ignored) {
            result.failed = true
        }
        result.created = created - atStartup
        context.close()
        result
    }

    private GenericApplicationContext context(Map<String, String> row, Map<String, CacheManager> instances,
                                              List<String> created, boolean withAlias) {
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
        context.registerBean('probe', CacheAware, lazy)
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

    @Priority(1)
    static class HighPriorityCacheManager extends ConcurrentMapCacheManager {
    }
}

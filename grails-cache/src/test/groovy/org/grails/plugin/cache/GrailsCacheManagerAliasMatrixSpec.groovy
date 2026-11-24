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
import java.util.function.Supplier

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.cache.CacheManager
import org.springframework.cache.concurrent.ConcurrentMapCacheManager
import org.springframework.context.support.GenericApplicationContext

/**
 * Which cache manager a cache-aware class compiled against Grails 8 is injected with on this version, with the
 * alias of {@code grailsCacheManager}, against the oracle of 8.0.0: the same class, in the same context, without
 * it. Such a class keeps the trait's field as Grails 8 compiled it, without the qualifier, until it is compiled
 * again, and gets the behaviour of Grails 8.0.1.
 *
 * <p>Where 8.0.0 failed with none of the other cache managers preferred, the class gets {@code grailsCacheManager}.
 * The known differences: where 8.0.0 failed, or chose a cache manager by a priority only its object has, and none
 * is preferred by its definition, the result may be what {@code grailsCacheManager} gives, and where it is created
 * from the cache-aware bean, a failed startup. Otherwise the choice, and every failure, is 8.0.0's: what the
 * cache-aware bean, three instances of a prototype cache-aware bean and a cache-aware {@code grailsCacheManager}
 * get, the beans the cache-aware bean depends on, and how many of each cache manager are created.</p>
 */
class GrailsCacheManagerAliasMatrixSpec extends CacheManagerArrangementsSpec {

    /** A cache-aware class compiled against the trait as Grails 8 has it. */
    private static final Class<?> COMPILED_AGAINST_GRAILS_8 = compiledAgainstGrails8('''
        package compat
        class CompiledAgainstGrails8 implements org.grails.plugin.cache.GrailsCacheManagerAware {
        }
    ''', 'compat.CompiledAgainstGrails8')

    /** A cache manager preferred by its definition, which the registrar sees and leaves the alias out for. */
    private static final List<String> PREFERRED_BY_DEFINITION = ['primary', 'priority']

    /** A cache manager whose priority only its object has, which the registrar cannot see. */
    private static final List<String> PRIORITY_ON_OBJECT = ['priorityOnObject', 'factoryBean', 'createdByKeyGenerator']

    @Override
    protected Class<?> getProbeType() {
        COMPILED_AGAINST_GRAILS_8
    }

    void 'the alias is the name the trait\'s field has in a class compiled against Grails 8, which has no qualifier'() {
        given:
        Field field = COMPILED_AGAINST_GRAILS_8.getDeclaredField(GrailsCacheManagerAliasRegistrar.ALIAS)

        expect:
        field.type == CacheManager
        !field.isAnnotationPresent(Qualifier)
        !field.isAnnotationPresent(GrailsCacheManagerQualifier)
    }

    void 'the cache manager a cache-aware class gets is 8.0.0\'s, except where it failed with none preferred, and the known differences'() {
        given:
        List<Map<String, String>> rows = arrangements()
        assert rows.size() == NAMED_KINDS.size() * (1 + OTHER_KINDS.size() + OTHER_KINDS.size() * OTHER_KINDS.size())

        expect: 'every arrangement, each in a context of its own, with the alias and without it'
        List<String> mismatches = []
        rows.each { Map<String, String> row ->
            Map<String, Object> was = run(row, false)
            Map<String, Object> now = run(row, true)
            boolean namedUsable = row[NAMED] in ['plain', 'primary', 'lazy', 'cacheAware', 'fallback', 'priority']
            boolean preferredByDefinition = row.any { String name, String kind -> name != NAMED && kind in PREFERRED_BY_DEFINITION }
            boolean priorityOnObject = !preferredByDefinition &&
                    row.any { String name, String kind -> name != NAMED && kind in PRIORITY_ON_OBJECT }
            Object wasResult = was.failed ? 'failed' : was.chosen
            // a context that does not start has failed before the cache-aware bean, where it cannot fall back
            boolean fallsBack = was.failed && !was.failedAtStartup && namedUsable && !preferredByDefinition && !priorityOnObject
            Object got = now.failed ? 'failed' : now.chosen
            // the known differences: Spring takes grailsCacheManager by the alias before a cache manager whose priority
            // only its object has, which 8.0.0 chose where that one existed first, as it does where 8.0.0 failed; it gives
            // what grailsCacheManager gives, Spring's null bean nothing, and a grailsCacheManager created from the
            // cache-aware bean is asked for while it is created
            boolean mayDiffer = !preferredByDefinition && row[NAMED] != null && (was.failed || priorityOnObject)
            List<Object> allowed = [fallsBack ? NAMED : wasResult]
            if (mayDiffer && !fallsBack) {
                allowed << (row[NAMED] == 'nullBean' ? null : NAMED)
                if (row[NAMED] == 'cyclic') {
                    allowed << 'failed'
                }
            }
            Object expected = got in allowed ? got : allowed[0]
            boolean differs = expected != wasResult
            Map<String, Integer> wasCreated = ((List<String>) was.created).countBy { it }
            String overCreated = ((List<String>) now.created).countBy { it }.find { String name, Integer count ->
                count > (wasCreated[name] ?: 0) + (differs && name == NAMED ? 1 : 0)
            }?.key
            // the prototypes as in 8.0.0, and where 8.0.0 failed before them, as the probe's own resolution
            Map<String, Integer> prototypesMayCreate = ((differs ? now.created : was.createdByPrototypes) ?: []).countBy { it }
            String overCreatedByPrototypes = ((List<String>) (now.createdByPrototypes ?: [])).countBy { it }
                    .find { String name, Integer count -> count > (prototypesMayCreate[name] ?: 0) }?.key
            if (got != expected) {
                mismatches << "${row}: got ${got}, expected ${expected} (8.0.0: ${was.failed ? 'failed' : was.chosen})".toString()
            }
            // where the context now fails at another stage than in 8.0.0, what it created counts from another point
            else if (overCreated && !(now.failed && was.failed != null && now.failedAtStartup != was.failedAtStartup)) {
                mismatches << "${row}: created ${now.created}, where 8.0.0 created ${was.created}".toString()
            }
            else if (overCreatedByPrototypes) {
                mismatches << "${row}: prototypes created ${now.createdByPrototypes}, where 8.0.0 created ${was.createdByPrototypes}".toString()
            }
            else if (!now.failed && now.prototypes != (differs ? [expected] * 3 : was.prototypes)) {
                mismatches << "${row}: prototypes got ${now.prototypes}, where in 8.0.0 they got ${was.prototypes}".toString()
            }
            else if (!now.failed && !was.failed && !differs && now.self != was.self) {
                mismatches << "${row}: grailsCacheManager got ${now.self}, where in 8.0.0 it got ${was.self}".toString()
            }
            else if (!now.failed && (differs ? !(((List<String>) now.dependencies - ['customCacheKeyGenerator']) in [[NAMED], []]) :
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
        besidePriority.getBean('probe', GrailsCacheManagerAware).grailsCacheManager.is(preferred)
        besidePlain.getBean('probe', GrailsCacheManagerAware).grailsCacheManager.is(besidePlain.getBean(NAMED))

        cleanup:
        besidePriority?.close()
        besidePlain?.close()
    }

    void 'no alias is registered where there is no grailsCacheManager, where its name is taken, or beside a preferred singleton'() {
        given:
        GenericApplicationContext withNone = context([first: 'plain'], [:], [], true)
        GenericApplicationContext taken = context([(NAMED): 'plain'], [:], [], false)
        taken.beanFactory.registerAlias('first', GrailsCacheManagerAliasRegistrar.ALIAS)
        taken.registerBean('first', CacheManager, { -> new ConcurrentMapCacheManager() } as Supplier<CacheManager>)

        and: 'cache managers registered as singletons, which have no bean definition, one of a class with a priority'
        GenericApplicationContext besidePriority = context([(NAMED): 'plain'], [:], [], false)
        besidePriority.beanFactory.registerSingleton('preferred', new HighPriorityCacheManager())
        GenericApplicationContext besidePlain = context([(NAMED): 'plain'], [:], [], false)
        besidePlain.beanFactory.registerSingleton('other', new ConcurrentMapCacheManager())

        when:
        [taken, besidePriority, besidePlain, besidePlain].each { GenericApplicationContext context ->
            new GrailsCacheManagerAliasRegistrar().postProcessBeanFactory(context.beanFactory)
        }

        then:
        !withNone.beanFactory.isAlias(GrailsCacheManagerAliasRegistrar.ALIAS)
        taken.beanFactory.getAliases('first').contains(GrailsCacheManagerAliasRegistrar.ALIAS)
        !taken.beanFactory.getAliases(NAMED).contains(GrailsCacheManagerAliasRegistrar.ALIAS)
        !besidePriority.beanFactory.isAlias(GrailsCacheManagerAliasRegistrar.ALIAS)

        and: 'a singleton without a priority leaves the alias to be registered, once however often it is post-processed'
        besidePlain.beanFactory.getAliases(NAMED) as List == [GrailsCacheManagerAliasRegistrar.ALIAS]

        cleanup:
        withNone?.close()
        taken?.close()
        besidePriority?.close()
        besidePlain?.close()
    }

}

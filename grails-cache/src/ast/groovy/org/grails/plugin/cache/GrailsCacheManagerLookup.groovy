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

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import org.springframework.beans.factory.BeanFactory
import org.springframework.beans.factory.BeanFactoryUtils
import org.springframework.beans.factory.NoSuchBeanDefinitionException
import org.springframework.beans.factory.NoUniqueBeanDefinitionException
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.config.BeanDefinition
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory
import org.springframework.beans.factory.support.AbstractBeanDefinition
import org.springframework.cache.CacheManager
import org.springframework.core.annotation.OrderUtils

/**
 * Chooses the cache manager a {@link GrailsCacheManagerAware} class caches with, when Spring creates
 * it.
 *
 * <p>The bean named {@code grailsCacheManager} comes first, as the plugin creates its caches in it
 * and its tag library is wired to it by name. A cache manager Spring would have preferred, as primary
 * or by priority, is passed over for it, which is logged once for each bean factory. Without one of
 * that name, the cache manager is the one Spring resolves for the {@code ObjectProvider} it is given,
 * which is how it resolved the trait's {@code @Autowired(required = false)} field: the only
 * candidate, or the one marked primary or of the highest priority, with lazy beans, fallback beans,
 * beans that are not autowire candidates and scoped beans treated as that injection treated them,
 * and only the cache manager chosen created. Where Spring cannot choose, which failed startup when
 * the field was injected, there is no cache manager, and a warning gives Spring's reason.</p>
 *
 * @since 8.0.1
 */
@Slf4j
@CompileStatic
final class GrailsCacheManagerLookup {

    static final String BEAN_NAME = 'grailsCacheManager'

    /** The bean factories a preferred cache manager passed over has been reported for. */
    private static final Set<BeanFactory> REPORTED = Collections.synchronizedSet(
            Collections.newSetFromMap(new WeakHashMap<BeanFactory, Boolean>()))

    private GrailsCacheManagerLookup() {
    }

    /**
     * @param beanFactory where the cache manager named {@code grailsCacheManager} is looked up, or null
     * where there is none to look it up in
     * @param cacheManagers the cache manager Spring would inject
     * @param owner the class that caches with it, which a warning names
     * @return the cache manager, or null if there is none or Spring cannot choose one
     */
    static CacheManager find(BeanFactory beanFactory, ObjectProvider<CacheManager> cacheManagers, Class<?> owner) {
        if (beanFactory != null && beanFactory.containsBean(BEAN_NAME) && beanFactory.isTypeMatch(BEAN_NAME, CacheManager)) {
            reportPreferredPassedOver(beanFactory)
            return beanFactory.getBean(BEAN_NAME, CacheManager)
        }
        try {
            // getObject rather than getIfAvailable, which would answer null for a scoped cache manager
            // whose scope is not active, where the injection failed
            return cacheManagers.getObject()
        }
        catch (NoUniqueBeanDefinitionException e) {
            log.warn('{} does not cache: there is no cache manager named {}, and Spring cannot choose one. {} ' +
                    'Define the one to cache with as {}.', owner.name, BEAN_NAME, e.message, BEAN_NAME)
            return null
        }
        catch (NoSuchBeanDefinitionException ignored) {
            return null
        }
    }

    private static void reportPreferredPassedOver(BeanFactory beanFactory) {
        if (!log.infoEnabled || !(beanFactory instanceof ConfigurableListableBeanFactory) || REPORTED.contains(beanFactory)) {
            return
        }
        ConfigurableListableBeanFactory listable = (ConfigurableListableBeanFactory) beanFactory
        List<String> preferred = BeanFactoryUtils.beanNamesForTypeIncludingAncestors(listable, CacheManager, true, false)
                .findAll { String name ->
                    name != BEAN_NAME && !(BEAN_NAME in listable.getAliases(name)) && preferredBySpring(listable, name)
                }
        if (REPORTED.add(beanFactory) && preferred) {
            log.info('Grails caches with the cache manager named {}, not with {}, which Spring prefers as primary or ' +
                    'by priority. Define the one to cache with as {}.', BEAN_NAME, preferred.join(', '), BEAN_NAME)
        }
    }

    /** Whether Spring would have preferred the cache manager when injecting one by type. */
    private static boolean preferredBySpring(ConfigurableListableBeanFactory beanFactory, String name) {
        BeanDefinition definition = beanFactory.getMergedBeanDefinition(name)
        if (!definition.autowireCandidate ||
                (definition instanceof AbstractBeanDefinition && !((AbstractBeanDefinition) definition).defaultCandidate)) {
            return false
        }
        if (definition.primary) {
            return true
        }
        Class<?> type = beanFactory.getType(name, false)
        type != null && OrderUtils.getPriority(type) != null
    }
}

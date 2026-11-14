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

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import org.springframework.beans.factory.BeanFactory
import org.springframework.beans.factory.NoUniqueBeanDefinitionException
import org.springframework.beans.factory.config.AutowireCapableBeanFactory
import org.springframework.beans.factory.config.DependencyDescriptor
import org.springframework.cache.CacheManager

/**
 * Finds the cache manager a {@link GrailsCacheManagerAware} class caches with.
 *
 * <p>The bean named {@code grailsCacheManager} comes first, as the plugin creates its caches in it
 * and its tag library is wired to it by name. Without one, the cache manager is resolved exactly as
 * Spring resolves a {@code CacheManager} field marked {@code @Autowired(required = false)}, which is
 * how the trait used to receive it: the only candidate, or the one marked primary or of the highest
 * priority, with lazy beans, fallback beans and beans that are not autowire candidates treated as
 * that injection treats them, and only the cache manager chosen created. Where Spring cannot choose,
 * which failed startup when the trait was injected, there is no cache manager, and a warning says
 * why.</p>
 *
 * @since 8.0.1
 */
@Slf4j
@CompileStatic
final class GrailsCacheManagerLookup {

    static final String BEAN_NAME = 'grailsCacheManager'

    /** The injection point the trait declared, which is resolved as Spring resolved it. */
    private static final Field INJECTION_POINT = InjectionPoint.getDeclaredField(BEAN_NAME)

    private GrailsCacheManagerLookup() {
    }

    /**
     * @param beanFactory where the cache manager is looked up
     * @param owner the class that caches with it, which a warning names
     * @return the cache manager, or null if there is none or Spring cannot choose one
     */
    static CacheManager find(BeanFactory beanFactory, Class<?> owner) {
        if (beanFactory.containsBean(BEAN_NAME) && beanFactory.isTypeMatch(BEAN_NAME, CacheManager)) {
            return beanFactory.getBean(BEAN_NAME, CacheManager)
        }
        if (!(beanFactory instanceof AutowireCapableBeanFactory)) {
            return beanFactory.getBeanProvider(CacheManager).getIfUnique()
        }
        try {
            return (CacheManager) ((AutowireCapableBeanFactory) beanFactory)
                    .resolveDependency(new DependencyDescriptor(INJECTION_POINT, false), null)
        }
        catch (NoUniqueBeanDefinitionException e) {
            log.warn('{} does not cache: there is no cache manager named {}, and Spring cannot choose among {}. ' +
                    'Mark one of them primary, or define the one to cache with as {}.',
                    owner.name, BEAN_NAME, e.beanNamesFound, BEAN_NAME)
            return null
        }
    }

    @CompileStatic
    private static class InjectionPoint {

        CacheManager grailsCacheManager
    }
}

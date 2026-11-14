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

import org.springframework.beans.factory.BeanFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.cache.CacheManager

import grails.plugin.cache.CustomCacheKeyGenerator
import grails.plugin.cache.GrailsCacheKeyGenerator

/**
 * A trait for classes that are cache aware
 *
 * @since 4.0
 * @author Graeme Rocher
 */
@CompileStatic
trait GrailsCacheManagerAware {

    @Autowired(required = false)
    private GrailsCacheKeyGenerator customCacheKeyGenerator = new CustomCacheKeyGenerator()

    /**
     * Set by {@link #injectGrailsCacheManager} rather than injected. The trait keeps exactly the fields
     * it had, so that a class compiled against an earlier version, which declares them itself, works
     * with this one.
     */
    private CacheManager grailsCacheManager

    /**
     * Called by Spring when the class is created as a bean, where the trait's field used to be
     * injected: another cache manager, such as the one the Spring Security plugin caches users with
     * when {@code cacheUsers} is set, made that injection ambiguous, and the context failed to start,
     * as Spring falls back to matching the field's name and a trait's field is renamed in the class
     * that implements it. See {@link GrailsCacheManagerLookup} for which cache manager is chosen.
     *
     * <p>Where Spring cannot otherwise choose, it takes the bean named like the parameter, which the
     * trait's renamed field never matched, so the parameters are named as no bean would be.</p>
     *
     * @param grailsCacheManagerLookupBeanFactory where the cache manager named {@code grailsCacheManager}
     * is looked up; a provider, as a bean factory that is not an application context does not inject
     * itself
     * @param grailsCacheManagerLookupCandidates the cache manager Spring would inject, where there is
     * none of that name
     */
    @Autowired(required = false)
    void injectGrailsCacheManager(ObjectProvider<BeanFactory> grailsCacheManagerLookupBeanFactory,
                                  ObjectProvider<CacheManager> grailsCacheManagerLookupCandidates) {
        grailsCacheManager = GrailsCacheManagerLookup.find(grailsCacheManagerLookupBeanFactory.getIfAvailable(),
                grailsCacheManagerLookupCandidates, getClass())
    }

    /**
     * @return The cache manager this class caches with, or null if it has none
     */
    CacheManager getGrailsCacheManager() {
        return grailsCacheManager
    }

    /**
     * @return The custom key generator, or null if it isn't present
     */
    GrailsCacheKeyGenerator getCustomCacheKeyGenerator() {
        return customCacheKeyGenerator
    }
}

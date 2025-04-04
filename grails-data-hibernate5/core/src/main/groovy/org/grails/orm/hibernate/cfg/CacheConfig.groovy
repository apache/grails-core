/*
 * Copyright 2003-2007 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.orm.hibernate.cfg

import groovy.transform.AutoClone
import groovy.transform.CompileStatic
import groovy.transform.builder.Builder
import groovy.transform.builder.SimpleStrategy
import org.springframework.beans.MutablePropertyValues
import org.springframework.validation.DataBinder

/**
 * Defines the cache configuration.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@AutoClone
@CompileStatic
@Builder(builderStrategy = SimpleStrategy, prefix = '')
class CacheConfig implements Cloneable {
    static final List USAGE_OPTIONS = ['read-only', 'read-write','nonstrict-read-write','transactional']
    static final List INCLUDE_OPTIONS = ['all', 'non-lazy']

    /**
     * The cache usage
     */
    String usage = "read-write"
    /**
     * Whether caching is enabled
     */
    boolean enabled = false
    /**
     * What to include in caching
     */
    String include = "all"

    /**
     * Configures a new CacheConfig instance
     *
     * @param config The configuration
     * @return The new instance
     */
    static CacheConfig configureNew(@DelegatesTo(CacheConfig) Closure config) {
        CacheConfig cacheConfig = new CacheConfig()
        return configureExisting(cacheConfig, config)
    }

    /**
     * Configures an existing CacheConfig instance
     *
     * @param config The configuration
     * @return The new instance
     */
    static CacheConfig configureExisting(CacheConfig cacheConfig, Map config) {
        DataBinder dataBinder = new DataBinder(cacheConfig)
        dataBinder.bind(new MutablePropertyValues(config))
        return cacheConfig
    }
    /**
     * Configures an existing PropertyConfig instance
     *
     * @param config The configuration
     * @return The new instance
     */
    static CacheConfig configureExisting(CacheConfig cacheConfig, @DelegatesTo(CacheConfig) Closure config) {
        config.setDelegate(cacheConfig)
        config.setResolveStrategy(Closure.DELEGATE_ONLY)
        config.call()
        return cacheConfig
    }
}

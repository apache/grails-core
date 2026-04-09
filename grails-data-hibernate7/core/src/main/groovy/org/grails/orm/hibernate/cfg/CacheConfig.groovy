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

    @AutoClone
    @CompileStatic
    static class Usage implements Cloneable {

        public static final Usage READ_ONLY = new Usage('read-only')
        public static final Usage READ_WRITE = new Usage('read-write')
        public static final Usage NONSTRICT_READ_WRITE = new Usage('nonstrict-read-write')
        public static final Usage TRANSACTIONAL = new Usage('transactional')

        private final String value

        Usage(String value) {
            this.value = value
        }

        @Override
        String toString() {
            return value
        }

        @Override
        boolean equals(Object o) {
            if (this.is(o)) return true
            if (o == null || getClass() != o.getClass()) return false
            Usage usage = (Usage) o
            return value == usage.value
        }

        @Override
        int hashCode() {
            return value != null ? value.hashCode() : 0
        }

        static List<Usage> values() {
            [READ_ONLY, READ_WRITE, NONSTRICT_READ_WRITE, TRANSACTIONAL]
        }

        static Usage of(Object value) {
            if (value instanceof Usage) return value
            String str = value?.toString()
            if (!str) return null
            Usage found = values().find { it.value.equalsIgnoreCase(str) }
            if (found) return found
            return new Usage(str)
        }
    }

    @AutoClone
    @CompileStatic
    static class Include implements Cloneable {

        public static final Include ALL = new Include('all')
        public static final Include NON_LAZY = new Include('non-lazy')

        private final String value

        Include(String value) {
            this.value = value
        }

        @Override
        String toString() {
            return value
        }

        @Override
        boolean equals(Object o) {
            if (this.is(o)) return true
            if (o == null || getClass() != o.getClass()) return false
            Include include = (Include) o
            return value == include.value
        }

        @Override
        int hashCode() {
            return value != null ? value.hashCode() : 0
        }

        static List<Include> values() {
            [ALL, NON_LAZY]
        }

        static Include of(Object value) {
            if (value instanceof Include) return value
            String str = value?.toString()
            if (!str) return null
            Include found = values().find { it.value.equalsIgnoreCase(str) }
            if (found) return found
            return new Include(str)
        }
    }

    static final List<String> USAGE_OPTIONS = Usage.values().collect { it.toString() }
    static final List<String> INCLUDE_OPTIONS = Include.values().collect { it.toString() }

    /**
     * The cache usage
     */
    Usage usage = Usage.READ_WRITE
    /**
     * Whether caching is enabled
     */
    boolean enabled = false
    /**
     * What to include in caching
     */
    Include include = Include.ALL

    void setUsage(Object usage) {
        Usage u = Usage.of(usage)
        if (u != null) {
            this.usage = u
        }
    }

    void setInclude(Object include) {
        Include i = Include.of(include)
        if (i != null) {
            this.include = i
        }
    }

    CacheConfig usage(Object usage) {
        setUsage(usage)
        return this
    }

    CacheConfig include(Object include) {
        setInclude(include)
        return this
    }

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

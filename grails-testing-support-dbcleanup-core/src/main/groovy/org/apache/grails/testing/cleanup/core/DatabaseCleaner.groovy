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
package org.apache.grails.testing.cleanup.core

import javax.sql.DataSource

import groovy.transform.CompileStatic
import org.codehaus.groovy.runtime.InvokerHelper

import org.springframework.context.ApplicationContext
import org.springframework.util.ClassUtils

/**
 * SPI interface for database-specific cleanup implementations.
 *
 * <p>Implementations are discovered via the {@link java.util.ServiceLoader} mechanism.
 * Each implementation must declare the database type it supports via {@link #databaseType()}.
 * The type identifier must be unique across all implementations on the classpath; if two
 * implementations declare the same type, the framework will throw an error at startup.</p>
 *
 * <p>Implementations must be registered in
 * {@code META-INF/services/org.apache.grails.testing.cleanup.core.DatabaseCleaner}.</p>
 */
@CompileStatic
trait DatabaseCleaner {

    /**
     * Returns the database type that this cleaner supports (e.g., {@code "h2"}, {@code "mysql"},
     * {@code "postgresql"}). This value is used to match cleaners to data sources at runtime and
     * must be unique across all {@link DatabaseCleaner} implementations on the classpath.
     *
     * @return a non-null, non-empty string identifying the database type
     */
    abstract String databaseType()

    /**
     * Returns {@code true} if this cleaner supports the given {@link DataSource}.
     * The framework calls this method to determine which cleaner to use for each datasource.
     *
     * @param dataSource the datasource to check
     * @return {@code true} if this cleaner can handle the given datasource
     */
    abstract boolean supports(DataSource dataSource)

    /**
     * Performs the cleanup (truncation) of all tables in the given datasource.
     *
     * @param applicationContext the application context
     * @param dataSource the datasource to clean
     * @return statistics about what was cleaned
     */
    abstract DatabaseCleanupStats cleanup(ApplicationContext applicationContext, DataSource dataSource)

    /**
     * Optional callback to evict the Hibernate second-level cache after database cleanup.
     * This is necessary for tests that rely on Hibernate's caching behavior and want to
     * ensure a clean state after truncation. Implementations can check for the presence
     * of Hibernate and evict the cache if applicable.
     *
     * @param applicationContext the application context
     */
    void cleanupCacheLayer(ApplicationContext applicationContext) {
        // Clear the 2nd layer cache if it exists
        if (ClassUtils.isPresent('org.hibernate.SessionFactory', this.getClass().classLoader)) {
            def sessionFactory = applicationContext.getBean(
                    'sessionFactory',
                    Class.forName('org.hibernate.SessionFactory')
            )
            def cache = InvokerHelper.getProperty(sessionFactory, 'cache')
            if (cache) {
                InvokerHelper.invokeMethod(cache, 'evictAllRegions', null)
            }
        }
    }
}

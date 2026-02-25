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
import groovy.util.logging.Slf4j

import org.springframework.context.ApplicationContext

/**
 * Context that holds the discovered {@link DatabaseCleaner} implementations and
 * provides access to the application's data sources for cleanup operations.
 *
 * <p>Multiple cleaners can be registered (one per database type). When performing cleanup,
 * the context matches each datasource to an appropriate cleaner using either an explicit
 * database type from the {@link DatasourceCleanupMapping} or auto-discovery via
 * {@link DatabaseCleaner#supports(DataSource)}. If a datasource is marked for cleanup
 * but no matching cleaner is found, an error is thrown.</p>
 */
@Slf4j
@CompileStatic
class DatabaseCleanupContext {

    private ApplicationContext applicationContext
    private final Map<String, DatabaseCleaner> cleanersByType

    DatabaseCleanupContext(List<DatabaseCleaner> cleaners) {
        cleanersByType = createCleanersMap(cleaners)
    }

    private static Map<String, DatabaseCleaner> createCleanersMap(List<DatabaseCleaner> cleaners) {
        def typeMap = [:] as Map<String, DatabaseCleaner>
        cleaners.each {
            def type = it.databaseType()?.trim()
            if (!type) {
                throw new IllegalStateException(
                        "DatabaseCleaner implementation $it.class.name " +
                        'returned a null or empty databaseType()'
                )
            }
            def existing = typeMap[type]
            if (existing) {
                throw new IllegalStateException(
                        "Duplicate databaseType '$type' declared by both " +
                        "$existing.class.name and $it.class.name. " +
                        'Each DatabaseCleaner must declare a unique databaseType.'
                )
            }

            typeMap[type] = it
            log.debug(
                    'Discovered DatabaseCleaner implementation: {} (type: {})',
                    it.class.name,
                    type
            )
        }
        typeMap.asImmutable()
    }

    /**
     * @return the Spring {@link ApplicationContext}, or null if not yet resolved
     */
    ApplicationContext getApplicationContext() {
        applicationContext
    }

    /**
     * Sets the application context. Called by the interceptor when the test's
     * Spring context becomes available.
     */
    void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext
    }

    /**
     * Performs cleanup on data sources found in the application context using the
     * provided mapping from the {@link DatabaseCleanup} annotation.
     *
     * <p>If the mapping specifies explicit database types for data sources, those types
     * are used to look up the cleaner directly. Otherwise, auto-discovery via
     * {@link DatabaseCleaner#supports(DataSource)} is used.</p>
     *
     * @param mapping the parsed annotation value describing which data sources to clean
     *        and optionally which cleaner types to use
     * @return a list of {@link DatabaseCleanupStats}, one per datasource cleaned
     * @throws IllegalStateException if a datasource marked for cleanup has no
     *         matching {@link DatabaseCleaner} on the classpath, if an explicitly
     *         specified database type has no matching cleaner, or if a specified
     *         datasource bean does not exist in the application context
     */
    List<DatabaseCleanupStats> performCleanup(DatasourceCleanupMapping mapping) {
        if (!applicationContext) {
            throw new IllegalStateException(
                    'Cannot perform database cleanup: ApplicationContext is not available'
            )
        }
        if (!cleanersByType) {
            throw new IllegalStateException(
                    'Cannot perform database cleanup: no DatabaseCleaner implementations found'
            )
        }

        def allDataSources = applicationContext.getBeansOfType(DataSource)
        def allStats = [] as List<DatabaseCleanupStats>

        if (mapping.cleanAll) {
            // Clean all data sources using auto-discovery
            allDataSources.each { beanName, dataSource ->
                def cleaner = findCleanerFor(dataSource)
                if (!cleaner) {
                    throw new IllegalStateException(
                            'No DatabaseCleaner implementation found that supports ' +
                            "datasource '$beanName'. Ensure that a database-specific " +
                            'cleanup library (e.g., grails-testing-support-dbcleanup-h2) ' +
                            'is on the classpath for each database type used in your tests. ' +
                            "Available cleaners: ${cleanersByType.values()*.databaseType()}"
                    )
                }

                log.debug(
                        'Cleaning up datasource: {} (using {} cleaner)',
                        beanName,
                        cleaner.databaseType()
                )
                def stats = cleaner.cleanup(applicationContext, dataSource)
                stats.datasourceName = beanName
                if (stats.tableRowCounts) {
                    log.debug(
                            'Cleaned {} tables from datasource {}',
                            stats.tableRowCounts.size(),
                            beanName
                    )
                }
                allStats << stats
            }
        } else {
            // Clean specific data sources per the mapping entries
            mapping.entries.each {
                def dsName = it.datasourceName
                def dataSource = allDataSources[dsName]
                if (!dataSource) {
                    throw new IllegalStateException(
                            "Datasource '$dsName' specified in @DatabaseCleanup " +
                            'was not found in the application context. ' +
                            "Available datasources: ${allDataSources.keySet()}"
                    )
                }

                def cleaner = it.hasExplicitType() ?
                        cleanersByType[it.databaseType] :
                        findCleanerFor(dataSource)

                if (!cleaner) {
                    if (it.hasExplicitType()) {
                        throw new IllegalStateException(
                                "No DatabaseCleaner found for database type '$it.databaseType' " +
                                "specified in @DatabaseCleanup for datasource '$dsName'. " +
                                "Available cleaner types: ${cleanersByType.keySet()}"
                        )
                    }
                    throw new IllegalStateException(
                            "No DatabaseCleaner implementation found that supports datasource '$dsName'. " +
                            'Ensure that a database-specific cleanup library ' +
                            '(e.g., grails-testing-support-dbcleanup-h2) is on the classpath, ' +
                            "or specify the database type explicitly: '$dsName:type'. " +
                            "Available cleaners: ${cleanersByType.values()*.databaseType()}"
                    )
                }

                log.debug(
                        'Cleaning up datasource: {} (using {} cleaner)',
                        dsName,
                        cleaner.databaseType()
                )
                def stats = cleaner.cleanup(applicationContext, dataSource)
                stats.datasourceName = dsName

                if (stats.tableRowCounts) {
                    log.debug(
                            'Cleaned {} tables from datasource {}',
                            stats.tableRowCounts.size(),
                            dsName
                    )
                }

                allStats << stats
            }
        }

        allStats
    }

    /**
     * Finds a {@link DatabaseCleaner} that supports the given datasource via auto-discovery.
     *
     * @param dataSource the datasource to match
     * @return the matching cleaner, or {@code null} if none supports it
     */
    private DatabaseCleaner findCleanerFor(DataSource dataSource) {
        cleanersByType.values().find { it.supports(dataSource) }
    }
}

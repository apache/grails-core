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

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import org.spockframework.runtime.extension.AbstractMethodInterceptor
import org.spockframework.runtime.extension.IMethodInvocation

import org.springframework.context.ApplicationContext

/**
 * Spock method interceptor that performs database cleanup after tests annotated
 * with {@link DatabaseCleanup}. Supports both class-level and method-level annotations.
 *
 * <p>During setup, this interceptor eagerly resolves the {@link ApplicationContext} from
 * the {@link TestContextHolderListener} ThreadLocal and stores it on the
 * {@link DatabaseCleanupContext}. After cleanup completes, the ThreadLocal is cleared.</p>
 *
 * <p>When datasource entries are specified in the annotation (with optional database type
 * mappings), only those data sources are cleaned using the specified or auto-discovered
 * cleaners. Otherwise, all datas ources are cleaned.</p>
 */
@Slf4j
@CompileStatic
class DatabaseCleanupInterceptor extends AbstractMethodInterceptor {

    private final DatabaseCleanupContext context
    private final boolean classLevelCleanup
    private final DatasourceCleanupMapping mapping
    private final ApplicationContextResolver resolver

    /**
     * @param context the cleanup context containing the cleaners and configuration
     * @param classLevelCleanup if true, cleanup runs after every test method;
     *        if false, only after methods annotated with @DatabaseCleanup
     * @param mapping the parsed datasource-to-type mapping from the class-level annotation;
     *        for method-level cleanup, the method's own annotation values are parsed at runtime
     * @param resolver the strategy for resolving the ApplicationContext from test instances
     */
    DatabaseCleanupInterceptor(
            DatabaseCleanupContext context,
            boolean classLevelCleanup,
            DatasourceCleanupMapping mapping,
            ApplicationContextResolver resolver
    ) {
        this.context = context
        this.classLevelCleanup = classLevelCleanup
        this.mapping = mapping
        this.resolver = resolver
    }

    @Override
    void interceptSetupMethod(IMethodInvocation invocation) throws Throwable {
        try {
            invocation.proceed()
        }
        finally {
            ensureApplicationContext(invocation)
        }
    }

    @Override
    void interceptCleanupMethod(IMethodInvocation invocation) throws Throwable {
        try {
            invocation.proceed()
        }
        finally {
            try {
                def methodMapping = invocation.
                        feature?.
                        featureMethod?.
                        isAnnotationPresent(DatabaseCleanup)
                if (!classLevelCleanup && !methodMapping) {
                    return
                }
                log.debug(
                        'Performing database cleanup after test method: {}',
                        invocation.feature?.name ?: 'unknown'
                )
                def selectedMapping = methodMapping ?
                        getMethodMapping(invocation)
                        : mapping
                long startTime = System.currentTimeMillis()
                def stats = context.performCleanup(selectedMapping)
                logStats(stats, startTime)
            }
            finally {
                TestContextHolderListener.CURRENT.remove()
            }
        }
    }

    /**
     * Gets the parsed mapping from the method-level @DatabaseCleanup annotation.
     */
    private static DatasourceCleanupMapping getMethodMapping(IMethodInvocation invocation) {
        def annotation = invocation.
                feature?.
                featureMethod?.
                getAnnotation(DatabaseCleanup)
        annotation ?
                DatasourceCleanupMapping.parse(annotation.value()) :
                DatasourceCleanupMapping.parse(new String[0])
    }

    /**
     * Resolves the ApplicationContext from the test instance and sets it on the context
     * if not already set.
     *
     * @throws IllegalStateException if the ApplicationContext cannot be resolved
     */
    private void ensureApplicationContext(IMethodInvocation invocation) {
        if (context.applicationContext) {
            return
        }

        def appCtx = resolver.resolve(invocation)
        if (appCtx) {
            context.applicationContext = appCtx
        }
        else {
            throw new IllegalStateException(
                'Could not resolve ApplicationContext from test instance. ' +
                'Ensure the spec is annotated with @Integration.'
            )
        }
    }

    /**
     * Logs cleanup statistics and overall timing information for the cleanup operation.
     *
     * @param statsList the list of cleanup statistics from individual data sources
     * @param overallStartTime the overall start time of the cleanup operation
     */
    private static void logStats(List<DatabaseCleanupStats> statsList, long overallStartTime) {
        if (DatabaseCleanupStats.debugEnabled) {
            long overallEndTime = System.currentTimeMillis()
            long overallDuration = overallEndTime - overallStartTime

            def separator = '=========================================================='
            def startTimeFormatted = DatabaseCleanupStats.formatTime(overallStartTime)
            def endTimeFormatted = DatabaseCleanupStats.formatTime(overallEndTime)

            println(separator)
            println('Overall Cleanup Timing')
            println("Start Time: $startTimeFormatted")
            println("End Time:   $endTimeFormatted")
            println("Duration:   $overallDuration ms")
            println(separator)

            statsList.each {
                if (it.tableRowCounts) {
                    println(it.toFormattedReport())
                }
            }
        }
    }
}

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

import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target

/**
 * Annotation to indicate that database tables should be truncated after test execution.
 *
 * <p>When applied at the class level, all test methods in the specification will have
 * their database cleaned up after each test and after the spec completes.
 * When applied at the method level, only the annotated test methods will trigger cleanup.</p>
 *
 * <p>The optional {@link #value()} attribute specifies which datasource bean names to clean
 * and optionally which database type (cleaner) to use. Each entry can be:</p>
 * <ul>
 *   <li>{@code "datasourceName"} — clean the named datasource, auto-discovering the
 *       appropriate {@link DatabaseCleaner} via
 *       {@link DatabaseCleaner#supports(javax.sql.DataSource)}</li>
 *   <li>{@code "datasourceName:databaseType"} — clean the named datasource using the
 *       {@link DatabaseCleaner} that declares the specified
 *       {@link DatabaseCleaner#databaseType()}</li>
 * </ul>
 *
 * <p>If the value is empty (the default), all datasources in the application context
 * will be cleaned using auto-discovery.</p>
 *
 * <p>The {@link #resolver()} attribute allows users to specify a custom
 * {@link ApplicationContextResolver} implementation for tests that store the
 * {@link org.springframework.context.ApplicationContext} in a non-standard location.
 * The default resolver, {@link DefaultApplicationContextResolver}, covers the common
 * cases (Groovy property, field reflection, and Spring TestContextManager).</p>
 *
 * <p>Example usage:</p>
 * <pre>
 * // Clean all datasources after every test (auto-discover cleaners)
 * &#64;DatabaseCleanup
 * class MySpec extends Specification { ... }
 *
 * // Clean only specific datasources (auto-discover cleaners)
 * &#64;DatabaseCleanup(['dataSource', 'dataSource_secondary'])
 * class MySpec extends Specification { ... }
 *
 * // Clean specific datasources with explicit cleaner types
 * &#64;DatabaseCleanup(['dataSource:h2', 'dataSource_pg:postgresql'])
 * class MySpec extends Specification { ... }
 *
 * // Mixed: some explicit, some auto-discovered
 * &#64;DatabaseCleanup(['dataSource:h2', 'dataSource_other'])
 * class MySpec extends Specification { ... }
 *
 * // Clean after a specific test method
 * &#64;DatabaseCleanup
 * void "my test"() { ... }
 *
 * // Use a custom ApplicationContext resolver
 * &#64;DatabaseCleanup(resolver = MyCustomResolver)
 * class MySpec extends Specification { ... }
 *
 * // Clean only once after the entire spec finishes (class-level only)
 * &#64;DatabaseCleanup(cleanupAfterSpec = true)
 * class MySpec extends Specification { ... }
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target([ElementType.TYPE, ElementType.METHOD])
@interface DatabaseCleanup {

    /**
     * The datasource entries to clean up. Each entry can be a plain datasource bean name
     * (e.g., {@code "dataSource"}) or a datasource-to-type mapping
     * (e.g., {@code "dataSource:h2"}). If empty (the default), all data sources found
     * in the application context will be cleaned using auto-discovery.
     *
     * @return an array of datasource entries
     */
    String[] value() default []

    /**
     * The {@link ApplicationContextResolver} implementation to use for resolving the
     * {@link org.springframework.context.ApplicationContext} from the test instance.
     *
     * <p>The default implementation, {@link DefaultApplicationContextResolver}, covers
     * the common cases: Groovy property access, field reflection, and Spring
     * TestContextManager. Override this to provide a custom resolution strategy for
     * tests that store the context in a non-standard way.</p>
     *
     * @return the resolver class to use
     */
    Class<? extends ApplicationContextResolver> resolver() default DefaultApplicationContextResolver

    /**
     * When {@code true}, database cleanup is deferred until after the entire spec finishes
     * ({@code cleanupSpec}) instead of running after each individual test method.
     *
     * <p>This attribute is only valid on class-level annotations. Setting it to {@code true}
     * on a method-level annotation will result in an {@link IllegalStateException} at
     * spec visit time.</p>
     *
     * <p>This is useful for specs where test methods build on each other's data, or where
     * per-test cleanup is too expensive and a single cleanup at the end is sufficient.</p>
     *
     * @return {@code true} to defer cleanup until after the spec finishes; defaults to {@code false}
     */
    boolean cleanupAfterSpec() default false
}

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

import org.spockframework.runtime.extension.IGlobalExtension
import org.spockframework.runtime.model.SpecInfo

import org.springframework.boot.test.context.SpringBootTest

/**
 * Spock global extension that detects the {@link DatabaseCleanup} annotation on test classes
 * and methods, and registers the {@link DatabaseCleanupInterceptor} to perform database
 * cleanup after annotated tests.
 *
 * <p>This extension requires a Spring application context provided by the SpringBootTest infrastructure</p>
 *
 * <p>The extension uses the {@link ServiceLoader} mechanism to discover all available
 * {@link DatabaseCleaner} implementations at startup. It validates that each implementation
 * declares a unique {@link DatabaseCleaner#databaseType() databaseType}; if duplicates are
 * found, an error is thrown immediately.</p>
 *
 * <p>At runtime, when cleanup is triggered for a datasource, the framework verifies that
 * a matching cleaner exists for that datasource's database type. If no matching cleaner
 * is found, an error is raised.</p>
 *
 * <p>By default, validation of misplaced {@code @DatabaseCleanup} annotations on non-feature
 * methods (e.g., {@code setup()}, {@code cleanup()}) is disabled for performance reasons.
 * Set the system property {@code grails.testing.cleanup.validate=true} to opt into this check:</p>
 * <pre>-Dgrails.testing.cleanup.validate=true</pre>
 */
@Slf4j
@CompileStatic
class DatabaseCleanupExtension implements IGlobalExtension {

    /**
     * System property that enables validation of {@code @DatabaseCleanup} placement on
     * non-feature methods. Set to {@code "true"} to enable:
     * {@code -Dgrails.testing.cleanup.validate=true}
     */
    static final String VALIDATE_PROPERTY = 'grails.testing.cleanup.validate'

    private DatabaseCleanupContext context

    /**
     * Returns {@code true} if the {@code grails.testing.cleanup.validate} system property
     * is set to {@code "true"}.
     */
    static boolean isValidateEnabled() {
        Boolean.getBoolean(VALIDATE_PROPERTY)
    }

    @Override
    void start() {
        def cleanerLoader = ServiceLoader.load(DatabaseCleaner)
        def cleaners = cleanerLoader.toList()

        if (cleaners.isEmpty()) {
            log.debug('No DatabaseCleaner implementations found on classpath')
            return
        }

        context = new DatabaseCleanupContext(cleaners)
    }

    @Override
    void visitSpec(SpecInfo spec) {
        if (!context) {
            return
        }

        // Without an application context, there can be no database
        boolean integrationEnvironment =
                spec.isAnnotationPresent(SpringBootTest)
                        && spec.getAnnotation(SpringBootTest).webEnvironment() in [
                            SpringBootTest.WebEnvironment.DEFINED_PORT,
                            SpringBootTest.WebEnvironment.RANDOM_PORT
                        ]
        if (!integrationEnvironment) {
            if (hasDatabaseCleanupAnnotation(spec)) {
                throw new IllegalStateException(
                    '@DatabaseCleanup requires an environment with an ApplicationContext. ' +
                    'Add @Integration or define the web environment on @SpringBootTest. ' +
                    "Spec: $spec.name")
            }
            return
        }

        boolean classAnnotated = spec.isAnnotationPresent(DatabaseCleanup)
        if (classAnnotated) {
            def annotation = spec.getAnnotation(DatabaseCleanup)
            def mapping = DatasourceCleanupMapping.parse(annotation.value())
            def resolver = createResolver(annotation.resolver())
            boolean afterSpec = annotation.cleanupAfterSpec()
            def interceptor = new DatabaseCleanupInterceptor(context, true, afterSpec, mapping, resolver)
            spec.addSetupInterceptor(interceptor)
            spec.addCleanupInterceptor(interceptor)
            if (afterSpec) {
                spec.addCleanupSpecInterceptor(interceptor)
                log.debug('Registered DatabaseCleanupInterceptor for spec: {} (class-level, cleanupAfterSpec)', spec.name)
            }
            else {
                log.debug('Registered DatabaseCleanupInterceptor for spec: {} (class-level)', spec.name)
            }
            return
        }

        // Validate that @DatabaseCleanup is not placed on non-feature methods (setup, cleanup, etc.)
        // This validation is opt-in via -Dgrails.testing.cleanup.validate=true for performance reasons
        if (validateEnabled) {
            validateNoAnnotationOnNonFeatureMethods(spec)
        }

        // Check for method-level annotations on feature methods
        boolean hasMethodAnnotation = spec.features.any {
            it.featureMethod.isAnnotationPresent(DatabaseCleanup)
        }

        if (hasMethodAnnotation) {
            // Validate that no method-level annotation uses cleanupAfterSpec = true
            validateNoMethodLevelCleanupAfterSpec(spec)

            // For method-level, pass a clean-all mapping as default; the interceptor reads
            // each method's own annotation at runtime.
            // Use the default resolver; the interceptor will read the method-level resolver at runtime.
            def defaultMapping = DatasourceCleanupMapping.parse(new String[0])
            def defaultResolver = new DefaultApplicationContextResolver()
            def interceptor = new DatabaseCleanupInterceptor(context, false, false, defaultMapping, defaultResolver)
            spec.addSetupInterceptor(interceptor)
            spec.addCleanupInterceptor(interceptor)
            log.debug('Registered DatabaseCleanupInterceptor for spec: {} (method-level)', spec.name)
        }
    }

    /**
     * Creates an instance of the specified {@link ApplicationContextResolver} class.
     *
     * @param resolverClass the resolver class to instantiate
     * @return a new resolver instance
     */
    private static ApplicationContextResolver createResolver(Class<? extends ApplicationContextResolver> resolverClass) {
        try {
            resolverClass.getDeclaredConstructor().newInstance()
        }
        catch (Exception e) {
            throw new IllegalStateException(
                    'Failed to instantiate ApplicationContextResolver: ' +
                    "$resolverClass.name. Ensure it has a no-arg constructor.",
                    e
            )
        }
    }

    /**
     * Validates that {@link DatabaseCleanup} is not placed on non-feature methods such as
     * setup(), cleanup(), setupSpec(), or cleanupSpec(). The annotation is only valid on
     * Spock feature methods (test methods) or at the class level.
     *
     * <p>This validation is opt-in and only runs when the system property
     * {@code grails.testing.cleanup.validate} is set to {@code "true"}, since iterating
     * over all declared methods via reflection has a performance cost.</p>
     *
     * @param spec the spec to validate
     * @throws IllegalStateException if @DatabaseCleanup is found on a non-feature method
     */
    private static void validateNoAnnotationOnNonFeatureMethods(SpecInfo spec) {
        // Collect all feature method names for comparison
        def featureMethodNames = spec.features*.name as Set<String>

        // Check all declared methods in the spec class for misplaced annotations
        // This necessarily uses spec.reflection since we need to scan raw Java methods
        // that are not exposed as Spock features
        def invalid = spec.reflection.declaredMethods.find {
            it.isAnnotationPresent(DatabaseCleanup) && !featureMethodNames.contains(it.name)
        }
        if (invalid) {
            throw new IllegalStateException(
                    "@DatabaseCleanup annotation on method '$invalid.name' " +
                    "in $spec.reflection.name is not valid. @DatabaseCleanup " +
                    'can only be applied to Spock feature methods (test methods) ' +
                    'or at the class level. It cannot be applied to setup(), ' +
                    'cleanup(), setupSpec(), or cleanupSpec() methods.'
            )
        }
    }

    /**
     * Validates that no method-level {@link DatabaseCleanup} annotation has
     * {@code cleanupAfterSpec = true}. This attribute is only valid at the class level.
     *
     * @param spec the spec to validate
     * @throws IllegalStateException if a method-level annotation has cleanupAfterSpec = true
     */
    private static void validateNoMethodLevelCleanupAfterSpec(SpecInfo spec) {
        def invalid = spec.features.find {
            def method = it.featureMethod
            method.isAnnotationPresent(DatabaseCleanup) &&
                    method.getAnnotation(DatabaseCleanup).cleanupAfterSpec()
        }
        if (invalid) {
            throw new IllegalStateException(
                    "@DatabaseCleanup(cleanupAfterSpec = true) on method '${invalid.featureMethod.name}' " +
                    "in ${spec.name} is not valid. The cleanupAfterSpec attribute " +
                    'can only be used on class-level @DatabaseCleanup annotations.'
            )
        }
    }

    /**
     * Checks whether the spec has any {@link DatabaseCleanup} annotation, either at the
     * class level or on any feature method.
     */
    private static boolean hasDatabaseCleanupAnnotation(SpecInfo spec) {
        spec.isAnnotationPresent(DatabaseCleanup) ||
                spec.features.any { it.featureMethod.isAnnotationPresent(DatabaseCleanup) }
    }
}

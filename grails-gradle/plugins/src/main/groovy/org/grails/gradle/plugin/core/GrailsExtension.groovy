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
package org.grails.gradle.plugin.core

import groovy.transform.CompileStatic

import org.apache.tools.ant.taskdefs.condition.Os
import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.provider.Property

import grails.util.Environment

/**
 * A extension to the Gradle plugin to configure Grails settings
 *
 * @author Graeme Rocher
 * @since 3.0
 */
@CompileStatic
class GrailsExtension {

    Project project
    PluginDefiner pluginDefiner

    GrailsExtension(Project project) {
        this.project = project
        this.pluginDefiner = new PluginDefiner(project)
        this.indy = project.objects.property(Boolean).convention(false)
        this.preserveParameterNames = project.objects.property(Boolean).convention(true)
        this.autoApplyBom = project.objects.property(Boolean).convention(true)
    }

    /**
     * Whether to invoke native2ascii on resource bundles
     */
    boolean native2ascii = !Os.isFamily(Os.FAMILY_WINDOWS)

    /**
     * Whether to use Ant to do the conversion
     */
    boolean native2asciiAnt = false

    /**
     * Whether assets should be packaged in META-INF/assets for plugins
     */
    boolean packageAssets = true

    /**
     * Whether java.time.* package should be a default import package
     */
    boolean importJavaTime = false

    /**
     * Whether grails annotation packages and common validation annotations should be default import packages.
     * When enabled, automatically imports:
     * - jakarta.validation.constraints.*
     * - grails.gorm.annotation.* (if grails-datamapping-core is in classpath)
     * - grails.plugin.scaffolding.annotation.* (if grails-scaffolding is in classpath)
     */
    boolean importGrailsCommonAnnotations = false

    /**
     * Custom star imports to add to Groovy compilation configuration.
     * Users can add their own package imports that will be combined with
     * imports added by importJavaTime and importGrailsCommonAnnotations flags.
     */
    List<String> starImports = []

    /**
     * @deprecated The Spring Dependency Management plugin has been replaced with Gradle's native
     * {@code platform()} support plus lightweight property-based version overrides
     * supplied by the {@code org.apache.grails.gradle.bom-property-overrides} plugin.
     * Set version overrides in {@code gradle.properties} or via {@code ext['property.name']}
     * instead. For backward compatibility, setting this property to {@code false} is still
     * honored as {@code autoApplyBom = false} so existing opt-outs (which previously prevented
     * the Spring Dependency Management BOM from being applied) continue to disable the
     * automatic {@code platform(grails-bom)} application. New builds should set
     * {@link #autoApplyBom} directly.
     */
    @Deprecated
    boolean springDependencyManagement = true

    /**
     * Backward-compatible setter for the deprecated {@link #springDependencyManagement} flag.
     * Disabling it maps onto {@code autoApplyBom = false} so projects that still opt out via
     * {@code grails { springDependencyManagement = false }} do not unexpectedly receive
     * {@code platform(grails-bom)} after the migration away from the Spring Dependency
     * Management plugin.
     */
    @Deprecated
    void setSpringDependencyManagement(boolean enabled) {
        this.@springDependencyManagement = enabled
        if (!enabled) {
            this.autoApplyBom.set(false)
        }
    }

    /**
     * Whether the Micronaut auto-setup should run when the `grails-micronaut` plugin is detected.
     * When enabled, the Grails Gradle plugin:
     * <ul>
     *   <li>validates that `grails-micronaut-bom` is applied as `enforcedPlatform` and fails the build
     *       at configuration time with an actionable error if not (`grails-micronaut-bom` is the single
     *       source of truth for the Micronaut platform version);</li>
     *   <li>configures the Spring Boot `bootJar`/`bootWar` tasks to use the {@code CLASSIC} loader
     *       implementation (required for {@code java -jar} compatibility with the Micronaut-Spring
     *       integration).</li>
     * </ul>
     * Disabling this is rarely appropriate; consumer projects should normally apply the BOM as
     * `enforcedPlatform` so that the Micronaut platform cannot override grails-bom-managed versions.
     */
    boolean micronautAutoSetup = true

    /**
     * Whether to enable Groovy's invokedynamic (indy) bytecode instruction for dynamic Groovy method dispatch.
     * Disabled by default to improve performance (see GitHub issue #15293).
     * When enabled, Groovy uses JVM invokedynamic instead of traditional callsite caching.
     * To enable invokedynamic in build.gradle: grails { indy = true }
     */
    final Property<Boolean> indy

    void setIndy(boolean enabled) {
        this.indy.set(enabled)
    }

    /**
     * Keep method and constructor parameter names in class files, allowing frameworks such as Spring to use parameter
     * names for dependency resolution, including autowiring by name without requiring annotations such as @Qualifier.
     */
    final Property<Boolean> preserveParameterNames

    /**
     * Whether the Grails Gradle plugin should automatically apply the {@code grails-bom}
     * as a Gradle {@code platform()} on every declarable project configuration
     * (and apply the {@code org.apache.grails.gradle.bom-property-overrides} plugin
     * for property-based version overrides).
     *
     * <p>Defaults to {@code true}, which matches the behaviour of every Grails 7 release:
     * the BOM is always applied so that the framework's curated managed-dependency set
     * is the source of truth for the application.</p>
     *
     * <p>Disable this only when you intentionally want to manage Grails dependencies
     * yourself - for example, when consuming Grails modules from a different curated
     * platform and you need to declare the BOM by hand (and apply
     * {@code org.apache.grails.gradle.bom-property-overrides} explicitly if you still
     * want {@code gradle.properties} / {@code ext['...']} overrides).</p>
     *
     * <pre>
     * grails {
     *     autoApplyBom = false
     * }
     * </pre>
     *
     * @since 8.0
     */
    final Property<Boolean> autoApplyBom

    DependencyHandler getPlugins() {
        if (pluginDefiner == null) {
            pluginDefiner = new PluginDefiner(project)
        }

        pluginDefiner
    }

    /**
     * Allows defining plugins in the available scopes
     */
    void plugins(@DelegatesTo(DependencyHandler) Closure configureClosure) {
        if (pluginDefiner == null) {
            pluginDefiner = new PluginDefiner(project)
        }
        pluginDefiner.grailsRun = developmentRun
        configureClosure.delegate = plugins
        configureClosure.resolveStrategy = Closure.DELEGATE_FIRST
        configureClosure.call()
    }

    boolean isDevelopmentRun() {
        boolean devMode = Environment.developmentEnvironmentAvailable && Environment.developmentMode
        if (!devMode) {
            return false
        }

        project.gradle.startParameter.taskNames.any { String taskName -> taskName in ['bootRun', 'console'] } || project.hasProperty('force.grails.exploded')
    }
}

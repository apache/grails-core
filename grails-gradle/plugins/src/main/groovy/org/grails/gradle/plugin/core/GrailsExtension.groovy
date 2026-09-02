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
 * @since 3.0
 */
@CompileStatic
class GrailsExtension {

    Project project
    PluginDefiner pluginDefiner

    /**
     * The default Grails BOM artifact name applied when {@link #bom} is not
     * overridden. Resolved as {@code org.apache.grails:grails-bom:$grailsVersion}.
     */
    static final String DEFAULT_BOM = 'grails-bom'

    GrailsExtension(Project project) {
        this.project = project
        this.pluginDefiner = new PluginDefiner(project)
        this.indy = project.objects.property(Boolean).convention(false)
        this.preserveParameterNames = project.objects.property(Boolean).convention(true)
        this.compileStatic = project.objects.newInstance(GrailsCompileStaticOptions)
        this.gorm = project.objects.newInstance(GrailsGormOptions)
        this.i18n = project.objects.newInstance(GrailsI18nOptions)
        this.cliAutoProvision = project.objects.property(Boolean).convention(project.provider {
            def fromProperty = project.findProperty('grailsCliAutoProvision')
            fromProperty == null ? Boolean.TRUE : Boolean.parseBoolean(fromProperty as String)
        })
        this.legacyCommandSupport = project.objects.property(Boolean).convention(project.provider {
            def fromProperty = project.findProperty('grailsLegacyCommandSupport')
            fromProperty == null ? Boolean.FALSE : Boolean.parseBoolean(fromProperty as String)
        })
        this.bom = project.objects.property(String)
        // Use set() rather than convention() so that clearing the value (bom = null,
        // or the deprecated springDependencyManagement = false) results in no BOM being
        // applied, instead of silently falling back to the convention.
        this.bom.set(DEFAULT_BOM)
    }

    /**
     * Whether the plugin auto-provisions the CLI tier onto the {@code grailsCli} configuration.
     * When enabled (the default), the plugin adds {@code org.apache.grails:grails-core-cli} (the
     * command contract) and {@code org.apache.grails:grails-console} (the command runner), and
     * discovers every companion {@code -cli} artifact advertised by the application's dependency
     * graph — a plugin advertises its companion through the {@code Grails-Cli-Artifact} manifest
     * attribute of its runtime jar. Plugin commands (e.g. the {@code dbm-*} or scaffolding
     * commands) therefore work with no additional build configuration. Opt out with
     * {@code grails { cliAutoProvision = false }} (or the {@code grailsCliAutoProvision} project
     * property) and declare `grailsCli` dependencies explicitly.
     */
    final Property<Boolean> cliAutoProvision

    /**
     * Whether the plugin auto-provisions the Grails 7 application-command compatibility bridge
     * ({@code org.apache.grails:grails-core-cli-legacy}) onto the execution-only
     * {@code grailsCliLegacy} configuration. Disabled by default. When enabled <strong>and</strong>
     * {@link #cliAutoProvision} is also enabled, unchanged Grails 7 command plugins keep working
     * without a re-release. Opt in with {@code grails { legacyCommandSupport = true }} (or the
     * {@code grailsLegacyCommandSupport} project property) while leaving modern CLI companion
     * auto-provisioning alone, or disable both with {@code cliAutoProvision = false}.
     */
    final Property<Boolean> legacyCommandSupport

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
     * Whether grails annotation packages and common validation annotations should be default import packages.
     * When enabled, automatically imports:
     * - jakarta.validation.constraints.*
     * - grails.gorm.annotation.*
     * - grails.plugin.scaffolding.annotation.*
     */
    boolean importGrailsCommonAnnotations = false

    /**
     * Custom star imports to add to Groovy compilation configuration.
     * Users can add their own package imports that will be combined with
     * imports added by the importGrailsCommonAnnotations flag.
     */
    List<String> starImports = []

    /**
     * Lazy opt-ins for compiling controllers, services and tag libraries with {@code @GrailsCompileStatic}
     * automatically, configured through the nested {@code compileStatic} block. Each flag is a lazy
     * {@link Property} (read at compile time, not configuration time) and defaults to {@code false}:
     *
     * <pre>
     * grails {
     *     compileStatic {
     *         controllers = true
     *         services = true
     *         tagLibs = true
     *     }
     * }
     * </pre>
     *
     * Use {@code compileStatic { all = true }} as a shortcut to enable all three at once. A per-class
     * {@code @CompileDynamic} (or {@code @CompileStatic}/{@code @GrailsCompileStatic}) annotation always
     * wins over these defaults.
     */
    final GrailsCompileStaticOptions compileStatic

    /**
     * Configures the nested {@link #compileStatic} opt-ins.
     *
     * @param configureClosure a closure applied to the {@link GrailsCompileStaticOptions}
     */
    void compileStatic(@DelegatesTo(value = GrailsCompileStaticOptions, strategy = Closure.DELEGATE_FIRST) Closure<?> configureClosure) {
        configureClosure.delegate = this.compileStatic
        configureClosure.resolveStrategy = Closure.DELEGATE_FIRST
        configureClosure.call()
    }

    /**
     * GORM compilation options, configured through the nested {@code gorm} block:
     *
     * <pre>
     * grails {
     *     gorm {
     *         defaultIdType = 'native'
     *     }
     * }
     * </pre>
     *
     * @since 8.0
     */
    final GrailsGormOptions gorm

    /**
     * Configures the nested {@link #gorm} options.
     *
     * @param configureClosure a closure applied to the {@link GrailsGormOptions}
     * @since 8.0
     */
    void gorm(@DelegatesTo(value = GrailsGormOptions, strategy = Closure.DELEGATE_FIRST) Closure<?> configureClosure) {
        configureClosure.delegate = this.gorm
        configureClosure.resolveStrategy = Closure.DELEGATE_FIRST
        configureClosure.call()
    }

    /**
     * Message-bundle options, configured through the nested {@code i18n} block. Only needed to
     * disambiguate base names that cannot be inferred from file names alone.
     */
    final GrailsI18nOptions i18n

    /**
     * Configures the nested {@link #i18n} options.
     *
     * @param configureClosure a closure applied to the {@link GrailsI18nOptions}
     */
    void i18n(@DelegatesTo(value = GrailsI18nOptions, strategy = Closure.DELEGATE_FIRST) Closure<?> configureClosure) {
        configureClosure.delegate = this.i18n
        configureClosure.resolveStrategy = Closure.DELEGATE_FIRST
        configureClosure.call()
    }

    /**
     * @deprecated The Spring Dependency Management plugin has been replaced with Gradle's native
     * {@code platform()} support plus lightweight property-based version overrides
     * supplied by the {@code org.apache.grails.gradle.bom-property-overrides} plugin.
     * Set version overrides in {@code gradle.properties} or via {@code ext['property.name']}
     * instead. For backward compatibility, setting this property to {@code false} is still
     * honored as {@code bom = null} so existing opt-outs (which previously prevented
     * the Spring Dependency Management BOM from being applied) continue to disable the
     * automatic Grails BOM {@code platform()} application. New builds should set
     * {@link #bom} directly.
     */
    @Deprecated
    boolean springDependencyManagement = true

    /**
     * Backward-compatible setter for the deprecated {@link #springDependencyManagement} flag.
     * Disabling it clears {@link #bom} (equivalent to {@code grails { bom = null }}) so projects
     * that still opt out via {@code grails { springDependencyManagement = false }} do not
     * unexpectedly receive the Grails BOM {@code platform()} after the migration away from the
     * Spring Dependency Management plugin.
     */
    @Deprecated
    void setSpringDependencyManagement(boolean enabled) {
        this.@springDependencyManagement = enabled
        if (!enabled) {
            this.bom.set((String) null)
        }
    }

    /**
     * Whether the Micronaut auto-setup should run when the `grails-micronaut` plugin is detected.
     * When enabled, the Grails Gradle plugin:
     * <ul>
     *   <li>validates that a Micronaut-compatible Grails BOM is applied as `enforcedPlatform` and fails
     *       the build at configuration time with an actionable error if not;</li>
     *   <li>keeps Micronaut dependency management aligned with the Grails BOM variant selected by the
     *       application.</li>
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
     * The Grails BOM that the Grails Gradle plugin automatically applies as a Gradle
     * {@code platform()} (or {@code enforcedPlatform()} for the Micronaut variants) on every
     * declarable project configuration, alongside the
     * {@code org.apache.grails.gradle.bom-property-overrides} plugin for property-based
     * version overrides.
     *
     * <p>The value is the BOM <strong>artifact name</strong> within the
     * {@code org.apache.grails} group; the plugin resolves it as
     * {@code org.apache.grails:$bom:$grailsVersion}. Exactly one BOM is ever applied.</p>
     *
     * <p>Defaults to {@code grails-bom}. Set it to a different curated variant when the
     * application needs that BOM instead - for example:</p>
     *
     * <pre>
     * grails {
     *     bom = 'grails-micronaut-bom'   // applied as an enforcedPlatform
     * }
     * </pre>
     *
     * <p>Set it to {@code null} (or use the deprecated
     * {@code grails { springDependencyManagement = false }}) to suppress the automatic BOM
     * application entirely - for example when you want to declare the
     * {@code platform()}/{@code enforcedPlatform()} by hand (and apply
     * {@code org.apache.grails.gradle.bom-property-overrides} explicitly if you still
     * want {@code gradle.properties} / {@code ext['...']} overrides):</p>
     *
     * <pre>
     * grails {
     *     bom = null
     * }
     * </pre>
     *
     * <p>The Micronaut variants ({@code grails-micronaut-bom},
     * {@code grails-hibernate5-micronaut-bom}, and {@code grails-hibernate7-micronaut-bom})
     * are applied as an {@code enforcedPlatform}
     * because the Micronaut platform would otherwise override their managed versions via
     * conflict resolution. All other BOMs are applied as a regular {@code platform}.</p>
     *
     * @since 8.0
     */
    final Property<String> bom

    /**
     * DSL setter for {@link #bom}. A {@code null} or blank value clears the property so that
     * no BOM is applied automatically; any other value is used verbatim as the BOM artifact name.
     */
    void setBom(String value) {
        String trimmed = value?.trim()
        if (trimmed) {
            this.bom.set(trimmed)
        }
        else {
            this.bom.set((String) null)
        }
    }

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

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
package org.grails.gradle.plugin.bom

import java.util.function.Function

import groovy.transform.CompileStatic
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.attributes.Category

/**
 * Gradle plugin that enables Maven-style property-based version overrides
 * for {@code platform()} BOMs.
 *
 * <p>This is the BOM-agnostic replacement for the Spring Dependency
 * Management plugin's property-override feature. The plugin is shipped as
 * part of {@code grails-gradle-plugins} but can be applied to any project
 * (Grails or otherwise) that consumes a BOM published with version
 * property references in its {@code <dependencyManagement>} block:</p>
 *
 * <pre>
 * plugins {
 *     id 'org.apache.grails.gradle.bom-property-overrides'
 * }
 *
 * dependencies {
 *     implementation platform('com.example:my-bom:1.0.0')
 * }
 *
 * // gradle.properties or build.gradle
 * ext['slf4j.version'] = '2.0.13'
 * </pre>
 *
 * <h2>How it works</h2>
 * <ol>
 *   <li>Auto-detects all {@code platform()} / {@code enforcedPlatform()}
 *       dependencies declared on the project's configurations (configurable
 *       via {@link BomPropertyOverridesExtension#autoDetect}).</li>
 *   <li>Resolves each BOM POM in a detached configuration, parses the
 *       {@code <properties>} block and the
 *       {@code <dependencyManagement>} entries, and recursively follows
 *       {@code <scope>import</scope>} BOMs.</li>
 *   <li>Computes each managed artifact's version twice - once with the BOM's
 *       default properties and once with the project's property overrides
 *       (from {@code gradle.properties} or {@code ext['property.name']})
 *       applied, including to imported-BOM selector versions. Any artifact
 *       whose effective version differs from its default version is recorded
 *       as an override.</li>
 *   <li>Applies each override as a <strong>strict</strong> dependency
 *       constraint on the project's declarable configurations, so the override
 *       wins over the {@code require} constraints contributed by
 *       {@code platform()} even when it downgrades a managed version.</li>
 * </ol>
 *
 * <p>The plugin does <strong>not</strong> declare any platforms itself.
 * Consumers (or other plugins like {@code grails-app}) remain responsible
 * for declaring the {@code platform()} dependencies; this plugin only
 * adds the property-override layer on top.</p>
 *
 * @since 8.0
 * @see BomManagedVersions
 * @see BomPropertyOverridesExtension
 */
@CompileStatic
class BomPropertyOverridesPlugin implements Plugin<Project> {

    /**
     * The plugin id, exposed as a constant for programmatic application
     * (e.g. {@code project.plugins.apply(BomPropertyOverridesPlugin.PLUGIN_ID)}).
     */
    static final String PLUGIN_ID = 'org.apache.grails.gradle.bom-property-overrides'

    @Override
    void apply(Project project) {
        def extension = project.extensions.create(
                BomPropertyOverridesExtension.EXTENSION_NAME,
                BomPropertyOverridesExtension,
                project.objects
        )

        // We wait until afterEvaluate to scan the project's declared platforms
        // and resolve their POMs, because the user typically declares
        // platform() dependencies and configures the bomPropertyOverrides
        // extension in the same build.gradle that applies this plugin.
        //
        // The afterEvaluate callback itself is a configuration-time callback,
        // not serialised into the configuration cache. We capture Gradle
        // services (ConfigurationContainer, DependencyHandler) and a property
        // lookup function at the boundary of this callback and hand them to
        // the resolver, so the resulting BomManagedVersions instance carries
        // no Project reference. The per-configuration eachDependency closures
        // installed by applyOverrides() capture only the resulting
        // Map<String, String> of overrides, which is fully serialisable and
        // safe for the configuration cache.
        //
        // Verified with `./gradlew --configuration-cache`: zero CC warnings
        // originate from this plugin or from BomManagedVersions.
        project.afterEvaluate {
            applyOverrides(
                    project.configurations,
                    project.dependencies,
                    { String name -> project.hasProperty(name) ? project.property(name)?.toString() : null } as Function<String, String>,
                    extension
            )
        }
    }

    /**
     * Resolves the configured BOMs and applies any version overrides found
     * to all project configurations. Takes captured Gradle services rather
     * than a {@link Project} so that the resolve path holds no
     * configuration-cache-hostile state. Visible for testing.
     */
    static void applyOverrides(ConfigurationContainer configurations,
                               DependencyHandler dependencies,
                               Function<String, String> propertyLookup,
                               BomPropertyOverridesExtension extension) {
        def bomCoordinates = new LinkedHashSet<String>()

        if (extension.autoDetect.get()) {
            bomCoordinates.addAll(detectDeclaredBoms(configurations))
        }

        for (String explicit : extension.boms.get()) {
            if (explicit) {
                bomCoordinates.add(explicit)
            }
        }

        if (bomCoordinates.isEmpty()) {
            return
        }

        def managedVersions = BomManagedVersions.resolve(configurations, dependencies, propertyLookup, bomCoordinates)
        if (!managedVersions.hasOverrides()) {
            return
        }

        // Apply overrides as strict constraints on every declarable configuration,
        // mirroring where the platform(grails-bom) constraints are contributed.
        // Resolvable/consumable configurations inherit the constraints through the
        // declarable configurations they extend.
        configurations.configureEach { Configuration conf ->
            if (conf.canBeDeclared) {
                managedVersions.applyTo(dependencies, conf.name)
            }
        }
    }

    /**
     * Scans every configuration for declared {@code platform()} or
     * {@code enforcedPlatform()} dependencies and returns their coordinates.
     * Takes a {@link ConfigurationContainer} rather than a {@link Project}
     * so the call path stays free of Project references. Visible for testing.
     */
    static Set<String> detectDeclaredBoms(ConfigurationContainer configurations) {
        def coordinates = new LinkedHashSet<String>()

        configurations.each { Configuration conf ->
            for (Dependency dep : conf.dependencies) {
                if (!(dep instanceof ModuleDependency)) {
                    continue
                }
                if (!isPlatformDependency((ModuleDependency) dep)) {
                    continue
                }
                def group = dep.group
                def name = dep.name
                def version = dep.version
                if (group && name && version) {
                    coordinates.add("${group}:${name}:${version}" as String)
                }
            }
        }

        return coordinates
    }

    private static boolean isPlatformDependency(ModuleDependency dep) {
        def categoryAttr = dep.attributes.getAttribute(Category.CATEGORY_ATTRIBUTE)
        if (categoryAttr == null) {
            return false
        }
        def category = categoryAttr.toString()
        return category == Category.REGULAR_PLATFORM || category == Category.ENFORCED_PLATFORM
    }
}

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

import groovy.transform.CompileStatic
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.attributes.Category

/**
 * Standalone Gradle plugin that enables Maven-style property-based version
 * overrides for {@code platform()} BOMs.
 *
 * <p>This is the BOM-agnostic, generically reusable extraction of the
 * property-override mechanism that historically lived inside the Spring
 * Dependency Management plugin. Apply it to any project that consumes a
 * BOM published with version property references in its
 * {@code <dependencyManagement>} block:</p>
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
 *   <li>For every property the BOM declares, checks whether the project
 *       has a property with the same name (via {@code gradle.properties}
 *       or {@code ext['property.name']}). If so, applies the override at
 *       resolution time using
 *       {@link Configuration#getResolutionStrategy()}'s
 *       {@code eachDependency} hook.</li>
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
        BomPropertyOverridesExtension extension = project.extensions.create(
                BomPropertyOverridesExtension.EXTENSION_NAME,
                BomPropertyOverridesExtension,
                project.objects
        )

        project.afterEvaluate {
            applyOverrides(project, extension)
        }
    }

    /**
     * Resolves the configured BOMs and applies any version overrides found
     * to all project configurations. Visible for testing.
     */
    static void applyOverrides(Project project, BomPropertyOverridesExtension extension) {
        Set<String> bomCoordinates = new LinkedHashSet<>()

        if (extension.autoDetect.get()) {
            bomCoordinates.addAll(detectDeclaredBoms(project))
        }

        for (String explicit : extension.boms.get()) {
            if (explicit) {
                bomCoordinates.add(explicit)
            }
        }

        if (bomCoordinates.isEmpty()) {
            return
        }

        BomManagedVersions managedVersions = BomManagedVersions.resolve(project, bomCoordinates)
        if (!managedVersions.hasOverrides()) {
            return
        }

        project.configurations.configureEach { Configuration conf ->
            managedVersions.applyTo(conf)
        }
    }

    /**
     * Scans every configuration for declared {@code platform()} or
     * {@code enforcedPlatform()} dependencies and returns their coordinates.
     * Visible for testing.
     */
    static Set<String> detectDeclaredBoms(Project project) {
        Set<String> coordinates = new LinkedHashSet<>()

        project.configurations.each { Configuration conf ->
            for (Dependency dep : conf.dependencies) {
                if (!(dep instanceof ModuleDependency)) {
                    continue
                }
                if (!isPlatformDependency((ModuleDependency) dep)) {
                    continue
                }
                String group = dep.group
                String name = dep.name
                String version = dep.version
                if (group && name && version) {
                    coordinates.add("${group}:${name}:${version}" as String)
                }
            }
        }

        return coordinates
    }

    private static boolean isPlatformDependency(ModuleDependency dep) {
        Object categoryAttr = dep.attributes.getAttribute(Category.CATEGORY_ATTRIBUTE)
        if (categoryAttr == null) {
            return false
        }
        String category = categoryAttr.toString()
        return category == Category.REGULAR_PLATFORM || category == Category.ENFORCED_PLATFORM
    }
}

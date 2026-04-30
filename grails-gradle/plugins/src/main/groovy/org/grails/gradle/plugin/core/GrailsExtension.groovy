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
import org.gradle.util.internal.ConfigureUtil

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
     * Whether the spring dependency management plugin should be applied by default
     */
    boolean springDependencyManagement = true

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
        ConfigureUtil.configure(configureClosure, plugins)
    }

    boolean isDevelopmentRun() {
        boolean devMode = Environment.developmentEnvironmentAvailable && Environment.developmentMode
        if (!devMode) {
            return false
        }

        project.gradle.startParameter.taskNames.any { String taskName -> taskName in ['bootRun', 'console'] } || project.hasProperty('force.grails.exploded')
    }
}

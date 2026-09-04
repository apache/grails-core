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
package org.grails.gradle.plugin.commands

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic

import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.component.AdhocComponentWithVariants
import org.gradle.api.component.ConfigurationVariantDetails
import org.gradle.api.publish.tasks.GenerateModuleMetadata

/**
 * Publication support for projects whose variants depend on a cli companion artifact declared
 * with a capability ({@code project(':other') { capabilities { requireCapability('g:other-cli') } }}).
 *
 * Inside a build (or composite) Gradle resolves such a dependency against the owning project's
 * cli feature variant. In <em>published</em> metadata, however, the capability request must not
 * survive: the companion is published under its own coordinate ({@code <artifactId>-cli}) and the
 * owner's Gradle Module Metadata only points at it through capability-gated {@code available-at}
 * redirects. A consumer resolving the owner with a capability request from a Maven repository
 * selects the owner's cli variant <em>and</em> its redirect target — two nodes providing the same
 * capability — and resolution fails with a capability self-conflict. Published metadata must
 * therefore record the plain companion coordinate, never the capability request.
 *
 * @since 8.0
 */
@CompileStatic
final class CliPublishingSupport {

    private static final String REWRITE_REGISTERED_PROPERTY = 'org.grails.cli.publishing.rewriteRegistered'

    private CliPublishingSupport() {
    }

    /**
     * Configures a library that consumes a cli tier in its <em>main</em> variants (e.g.
     * {@code api project(':grails-core') { capabilities { requireCapability('org.apache.grails:grails-core-cli') } }})
     * so its published pom and Gradle Module Metadata record the companion coordinate
     * ({@code grails-core-cli}) instead of the capability request.
     */
    static void publishCliDependenciesAsCompanionCoordinates(Project project) {
        AdhocComponentWithVariants javaComponent = (AdhocComponentWithVariants) project.components.getByName('java')
        Configuration runtimeClasspath = project.configurations.getByName('runtimeClasspath')
        for (String elements : ['apiElements', 'runtimeElements']) {
            javaComponent.withVariantsFromConfiguration(project.configurations.getByName(elements)) { ConfigurationVariantDetails details ->
                configureDependencyMapping(project, details, runtimeClasspath)
            }
        }
        rewritePublishedCliCapabilityDependencies(project)
    }

    /**
     * Rewrites the generated Gradle Module Metadata of every publication in the project so a
     * dependency declared with a cli-companion capability request
     * ({@code module + requireCapability('<group>:<module>-cli')}) is published as a plain
     * dependency on the companion coordinate ({@code <group>:<module>-cli}). Dependency mapping
     * ({@code publishResolvedCoordinates}) already does this for the pom, but Gradle preserves
     * the capability request in the module metadata, which consumers cannot resolve against the
     * published component tree. Registration is idempotent per project.
     */
    static void rewritePublishedCliCapabilityDependencies(Project project) {
        if (project.extensions.extraProperties.has(REWRITE_REGISTERED_PROPERTY)) {
            return
        }
        project.extensions.extraProperties.set(REWRITE_REGISTERED_PROPERTY, true)
        project.tasks.withType(GenerateModuleMetadata).configureEach { GenerateModuleMetadata task ->
            task.doLast(new Action<Task>() {
                @Override
                void execute(Task t) {
                    rewriteModuleFile(((GenerateModuleMetadata) t).outputFile.get().asFile,
                            knownCompanionCapabilities(project))
                }
            })
        }
    }

    /**
     * The companion capabilities advertised by the projects of this build ({@code group:artifactId}),
     * including customized companion coordinates ({@code cliArtifact { artifactId = ... }}) that the
     * default {@code <module>-cli} naming convention cannot recognize. Computed at execution time,
     * when every project has been evaluated.
     */
    private static Set<String> knownCompanionCapabilities(Project project) {
        Set<String> capabilities = [] as Set
        for (Project subproject : project.rootProject.allprojects) {
            def cliArtifactId = subproject.findProperty('cliArtifactId')
            if (cliArtifactId) {
                capabilities.add("${subproject.group}:${cliArtifactId}" as String)
            }
        }
        capabilities
    }

    /**
     * Publish resolved variant-level coordinates for the given variant so a dependency on another
     * project's cli tier (declared with a capability inside the build) lands in the pom as the
     * {@code -cli} coordinate rather than the primary one, which Maven consumers could not follow.
     *
     * Dependency mapping has not been promoted to Gradle's public API yet — it is only reachable
     * via {@code ConfigurationVariantDetailsInternal}; fall back silently when the internal
     * contract changes. Revisit when https://github.com/gradle/gradle/issues/26163 stabilizes.
     */
    @CompileDynamic
    static void configureDependencyMapping(Project project, ConfigurationVariantDetails details, Configuration resolutionSource) {
        try {
            details.dependencyMapping {
                it.publishResolvedCoordinates.set(true)
                it.fromResolutionOf(resolutionSource)
            }
        }
        catch (Throwable e) {
            project.logger.info('Dependency mapping is unavailable on this Gradle version; cli dependencies will be published with component-level coordinates in the pom.', e)
        }
    }

    @CompileDynamic
    private static void rewriteModuleFile(File moduleFile, Set<String> knownCompanionCapabilities) {
        if (!moduleFile.file) {
            return
        }
        Map<String, Object> metadata = (Map<String, Object>) new JsonSlurper().parse(moduleFile, 'UTF-8')
        boolean changed = false
        metadata.variants?.each { Map<String, Object> variant ->
            variant.dependencies?.each { Map<String, Object> dependency ->
                List<Map<String, Object>> capabilities = (List<Map<String, Object>>) dependency.requestedCapabilities
                // a companion is recognized by the `<module>-cli` naming convention, or — for a
                // customized companion coordinate — by matching a capability advertised by a
                // project of this build; unrelated capability requests (e.g. an ehcache flavor)
                // must survive untouched
                if (capabilities?.size() == 1
                        && capabilities[0].group == dependency.group
                        && (capabilities[0].name == "${dependency.module}-cli" as String
                                || "${capabilities[0].group}:${capabilities[0].name}" as String in knownCompanionCapabilities)) {
                    dependency.module = capabilities[0].name
                    dependency.remove('requestedCapabilities')
                    changed = true
                }
            }
        }
        if (changed) {
            moduleFile.setText(JsonOutput.prettyPrint(JsonOutput.toJson(metadata)), 'UTF-8')
        }
    }
}

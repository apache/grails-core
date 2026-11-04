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

package org.apache.grails.buildsrc

import groovy.transform.CompileStatic

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.DependencyConstraint
import org.gradle.api.artifacts.VersionConstraint
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.provider.Provider

/**
 * Validates that transitive dependencies do not replace versions what the
 * BOM (grails-bom or grails-gradle-bom) defines. This covers both direct BOM
 * constraints and inherited platform constraints (e.g., from spring-boot-dependencies).
 *
 * <p>The plugin auto-detects which BOM is in use by scanning the project's
 * dependency declarations. Projects without a BOM are silently skipped.</p>
 *
 * <p>Usage: apply {@code org.apache.grails.buildsrc.dependency-validator} in the project's
 * {@code plugins} block, then run {@code ./gradlew validateDependencyVersions}.</p>
 */
@CompileStatic
class GrailsDependencyValidatorPlugin implements Plugin<Project> {

    static final String VALIDATE_TASK_NAME = 'validateDependencyVersions'

    static final String VALIDATE_RUNTIME_TASK_NAME = 'validateProductionClasspath'

    /**
     * Project ext property holding {@code "group:name"} keys (name may be {@code *}) that must never
     * appear on the project's {@code runtimeClasspath}. Used to keep developer-only tooling - the
     * console, the shell and their terminal libraries - out of what an application ships.
     */
    static final String FORBIDDEN_RUNTIME_EXT = 'forbiddenRuntimeCoordinates'
    /**
     * Project ext property name that holds a collection of {@code "group:name"} keys
     * to exempt from version validation. Use this when a deliberate override is applied
     * (e.g. via {@code resolutionStrategy.useVersion}) and the divergence from the BOM
     * is intentional.
     */
    static final String ALLOWED_OVERRIDES_EXT = 'allowedBomOverrides'

    /**
     * Project ext property name that holds a collection of {@code "group:name"} keys whose dynamic
     * version requests are tolerated. Use this only for a request that cannot be rewritten, with the
     * reason commented next to it.
     */
    static final String ALLOWED_DYNAMIC_VERSIONS_EXT = 'allowedDynamicVersions'

    /** Opts a project out of {@link #VALIDATE_TASK_NAME}; see {@link GradleUtils#isOptedOut}. */
    static final String SKIP_PROPERTY = 'skipDependencyValidation'

    private static final Set<String> BOM_PROJECT_NAMES = ['grails-bom', 'grails-gradle-bom', 'grails-base-bom', 'grails-hibernate5-bom', 'grails-hibernate7-bom', 'grails-neo4j-bom'].toSet()

    /**
     * Configuration names that pull in a Grails BOM purely as build tooling rather than as part
     * of the project's published dependency graph, and are therefore ignored when detecting the
     * project's single Grails BOM. The shared {@code gradle/docs-dependencies.gradle} script adds
     * {@code platform(grails-bom)} to the {@code documentation} configuration only to resolve the
     * groovydoc tooling versions; a project that selects a non-default BOM variant (e.g.
     * {@code grails-hibernate7-bom}) for its real configurations
     * still receives {@code grails-bom} here, which must not be misreported as a second,
     * conflicting BOM.
     */
    private static final Set<String> BOM_DETECTION_EXCLUDED_CONFIGURATIONS = ['documentation'].toSet()

    @Override
    void apply(Project project) {
        project.plugins.withId('java') {
            registerDependencyVersionValidation(project)
            registerProductionClasspathValidation(project)
        }
    }

    /**
     * Registers {@link #VALIDATE_TASK_NAME}, wired entirely from providers.
     *
     * <p>Inspecting the dependency graph needs the project, so it happens while the task's inputs are
     * computed - after the build scripts have run, and before any task executes - rather than from a
     * {@code doLast} block. The BOM is detected and its configurations found, resolved and walked once,
     * in a memoized provider of a {@link BomDependencyGraph}; the modules that walk resolved are what
     * the BOM probing starts from, and every task input is mapped off that one result. The task itself
     * then only compares the values it was given.</p>
     *
     * <p>The provider short-circuits when the project opts out, so a skipped project never pays for
     * the resolution or the BOM probing.</p>
     */
    private static void registerDependencyVersionValidation(Project project) {
        Provider<BomDependencyGraph> graph = project.provider({
            if (GradleUtils.isOptedOut(project, SKIP_PROPERTY)) {
                return BomDependencyGraph.NONE
            }
            String bomPath = detectBomPath(project)
            if (bomPath == null) {
                return BomDependencyGraph.NONE
            }
            String bomName = bomPath.substring(bomPath.lastIndexOf(':') + 1)
            Set<Configuration> configurations = findBomConfigurations(project, bomName)
            // no BOM-bearing configuration means there is nothing to compare against
            if (configurations.isEmpty()) {
                return BomDependencyGraph.NONE
            }
            BomDependencyGraph walked = BomDependencyGraph.collect(project.path, bomPath, configurations)
            walked.withBomVersions(collectBomVersions(project, bomPath, walked.resolvedVersions.keySet()))
        }.memoize())

        project.tasks.register(VALIDATE_TASK_NAME, ValidateDependencyVersionsTask) {
            ValidateDependencyVersionsTask task ->
                task.group = 'verification'
                task.description = 'Validates that no transitive dependency upgrades a version beyond what the BOM manages, and that none is requested with a dynamic version.'
                task.projectName.set(project.name)
                task.bomPath.set(graph.map { BomDependencyGraph it -> it.bomPath })
                task.bomVersions.set(graph.map { BomDependencyGraph it -> it.bomVersions })
                task.resolvedVersions.set(graph.map { BomDependencyGraph it -> it.resolvedVersions })
                task.dynamicVersionRequests.set(graph.map { BomDependencyGraph it -> it.dynamicVersionRequests })
                task.resolutionWarnings.set(graph.map { BomDependencyGraph it -> it.resolutionWarnings })
                task.allowedOverrides.set(project.provider { GradleUtils.extStrings(project, ALLOWED_OVERRIDES_EXT) })
                task.allowedDynamicVersions.set(project.provider { GradleUtils.extStrings(project, ALLOWED_DYNAMIC_VERSIONS_EXT) })
        }
    }

    /**
     * Registers {@link #VALIDATE_RUNTIME_TASK_NAME}. Like {@link #VALIDATE_TASK_NAME}, it is invoked
     * explicitly by CI rather than hung off {@code check}.
     *
     * <p>Both inputs are providers, so the ext property a project declares in its own build script is
     * read when the value is needed rather than at plugin-apply time, and nothing reaches back to the
     * project once the task runs. The task skips itself when the project declares no forbidden
     * coordinates or has no resolvable {@code runtimeClasspath}.</p>
     */
    private static void registerProductionClasspathValidation(Project project) {
        Provider<Set<String>> forbiddenCoordinates = project.provider {
            Object configured = project.findProperty(FORBIDDEN_RUNTIME_EXT)
            configured == null
                    ? Collections.<String> emptySet()
                    : ((Collection<?>) configured).collect { it.toString() }.toSet()
        }

        Provider<ResolvedComponentResult> rootComponent = project.provider {
            Configuration runtimeClasspath = project.configurations.findByName('runtimeClasspath')
            runtimeClasspath?.canBeResolved ? runtimeClasspath : null
        }.flatMap { Configuration it -> it.incoming.resolutionResult.rootComponent }

        project.tasks.register(VALIDATE_RUNTIME_TASK_NAME, ValidateProductionClasspathTask) {
            ValidateProductionClasspathTask task ->
                task.group = 'verification'
                task.description = 'Fails if developer-only tooling reaches the production runtime classpath.'
                task.projectPath.set(project.path)
                task.forbiddenCoordinates.set(forbiddenCoordinates)
                task.rootComponent.set(rootComponent)
                task.onlyIf {
                    !task.forbiddenCoordinates.get().isEmpty() && task.rootComponent.present
                }
        }
    }

    /**
     * Scans the project's configurations to find which BOM project is in use.
     *
     * <p>Exactly one Grails BOM is expected on a project: the BOMs are split by
     * integration, so a project selects a single
     * variant. This method returns the path of the one declared Grails BOM, {@code null}
     * when none is declared, and fails the build when more than one distinct Grails BOM
     * is found (which indicates a misconfiguration - e.g. layering grails-bom and
     * grails-hibernate7-bom on the same project).</p>
     *
     * <p>Build-tooling configurations that pull in a BOM purely to resolve their own tool
     * versions (see {@link #BOM_DETECTION_EXCLUDED_CONFIGURATIONS}) are skipped, so the shared
     * {@code documentation} configuration's {@code grails-bom} does not conflict with a variant
     * BOM a project selects for its real dependencies.</p>
     */
    static String detectBomPath(Project project) {
        Set<String> bomPaths = new LinkedHashSet<>()

        for (Configuration config : project.configurations) {
            if (BOM_DETECTION_EXCLUDED_CONFIGURATIONS.contains(config.name)) {
                continue
            }
            for (Dependency dep : config.dependencies) {
                if (!BOM_PROJECT_NAMES.contains(dep.name)) {
                    continue
                }
                def bomProject = project.rootProject.findProject(":${dep.name}" as String)
                if (bomProject == null) {
                    continue
                }
                bomPaths.add(bomProject.path)
            }
        }

        if (bomPaths.isEmpty()) {
            return null
        }
        if (bomPaths.size() > 1) {
            throw new GradleException(
                    "Project '${project.name}' declares more than one Grails BOM (${bomPaths.join(', ')}). " +
                            'Exactly one Grails BOM may be applied; the BOMs are split by integration, ' +
                            'so a project must select a single variant.'
            )
        }

        bomPaths.first()
    }

    /**
     * Collects all BOM-managed dependency versions in two phases:
     * <ol>
     *   <li>Direct constraints from the BOM project's {@code api} configuration</li>
     *   <li>Inherited platform constraints (e.g., spring-boot-dependencies) via per-module probing of
     *   the {@code "group:name"} keys the BOM-bearing configurations resolved</li>
     * </ol>
     */
    private static Map<String, String> collectBomVersions(Project project, String bomPath, Set<String> resolvedModules) {
        Map<String, String> bomVersions = new LinkedHashMap<>()

        // Phase 1: Direct constraints from BOM
        Project bomProject = project.rootProject.findProject(bomPath)
        if (bomProject == null) {
            return bomVersions
        }

        Configuration bomApi = bomProject.configurations.findByName('api')
        if (bomApi != null) {
            for (DependencyConstraint c : bomApi.allDependencyConstraints) {
                String version = getConstraintVersion(c)
                if (version != null && !version.isEmpty()) {
                    bomVersions.put("${c.group}:${c.name}" as String, version)
                }
            }
        }

        // Phase 2: Probe inherited platform constraints for modules not in direct constraints
        Set<String> toProbe = new LinkedHashSet<>(resolvedModules)
        toProbe.removeAll(bomVersions.keySet())

        if (!toProbe.isEmpty()) {
            Dependency bomPlatform = project.dependencies.platform(
                    project.dependencies.project(Collections.singletonMap('path', bomPath))
            )

            for (String groupModule : toProbe) {
                try {
                    Dependency moduleDep = project.dependencies.create(groupModule)
                    Configuration probe = project.configurations.detachedConfiguration(bomPlatform, moduleDep)
                    for (ResolvedComponentResult component : probe.incoming.resolutionResult.allComponents) {
                        if (component.id instanceof ModuleComponentIdentifier) {
                            ModuleComponentIdentifier mid = (ModuleComponentIdentifier) component.id
                            String key = "${mid.group}:${mid.module}" as String
                            if (key == groupModule) {
                                bomVersions.put(key, mid.version)
                            }
                        }
                    }
                } catch (Exception ignored) {
                    // Module not managed by any BOM - skip
                }
            }
        }

        return bomVersions
    }

    private static String getConstraintVersion(DependencyConstraint constraint) {
        String version = constraint.version
        if (version == null || version.isEmpty()) {
            VersionConstraint vc = constraint.versionConstraint
            version = vc.requiredVersion
            if (version == null || version.isEmpty()) {
                version = vc.strictVersion
            }
            if (version == null || version.isEmpty()) {
                version = vc.preferredVersion
            }
        }
        return version
    }

    /**
     * Finds all resolvable configurations that have the BOM in their dependency chain.
     */
    private static Set<Configuration> findBomConfigurations(Project project, String bomName) {
        Set<Configuration> result = new LinkedHashSet<>()
        for (Configuration config : project.configurations) {
            if (!config.canBeResolved) {
                continue
            }
            if (configurationHasBom(config, bomName)) {
                result.add(config)
            }
        }
        return result
    }

    private static boolean configurationHasBom(Configuration config, String bomName) {
        return configurationHasBom(config, bomName, new HashSet<String>())
    }

    private static boolean configurationHasBom(Configuration config, String bomName, Set<String> visited) {
        if (!visited.add(config.name)) {
            return false
        }
        for (Dependency dep : config.dependencies) {
            if (dep.name == bomName) {
                return true
            }
        }
        for (Configuration parent : config.extendsFrom) {
            if (configurationHasBom(parent, bomName, visited)) {
                return true
            }
        }
        false
    }
}

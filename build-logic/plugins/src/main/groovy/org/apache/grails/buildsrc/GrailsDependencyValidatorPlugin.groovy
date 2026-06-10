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
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.DependencyConstraint
import org.gradle.api.artifacts.VersionConstraint
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedComponentResult

/**
 * Validates that transitive dependencies do not replace versions what the
 * BOM (grails-bom or grails-gradle-bom) defines. This covers both direct BOM
 * constraints and inherited platform constraints (e.g., from spring-boot-dependencies).
 *
 * <p>The plugin auto-detects which BOM is in use by scanning the project's
 * dependency declarations. Projects without a BOM are silently skipped.</p>
 *
 * <p>Usage: Apply via {@link CompilePlugin} (automatic) or directly, then run
 * {@code ./gradlew validateDependencyVersions}.</p>
 */
@CompileStatic
class GrailsDependencyValidatorPlugin implements Plugin<Project> {

    static final String VALIDATE_TASK_NAME = 'validateDependencyVersions'
    /**
     * Project ext property name that holds a collection of {@code "group:name"} keys
     * to exempt from version validation. Use this when a deliberate override is applied
     * (e.g. via {@code resolutionStrategy.useVersion}) and the divergence from the BOM
     * is intentional.
     */
    static final String ALLOWED_OVERRIDES_EXT = 'allowedBomOverrides'

    private static final Set<String> BOM_PROJECT_NAMES = ['grails-bom', 'grails-gradle-bom', 'grails-base-bom', 'grails-hibernate5-bom', 'grails-hibernate7-bom', 'grails-micronaut-bom', 'grails-hibernate5-micronaut-bom', 'grails-hibernate7-micronaut-bom'].toSet()

    @Override
    void apply(Project project) {
        project.plugins.withId('java') {
            project.tasks.register(VALIDATE_TASK_NAME) { Task task ->
                task.group = 'verification'
                task.description = 'Validates that no transitive dependency upgrades a version beyond what the BOM manages.'
                task.onlyIf { Task t -> !shouldSkip(t.project) }
                task.doLast { Task t -> validateDependencies(project) }
            }
        }
    }

    /**
     * Returns true when the project should skip dependency validation. Honors a
     * {@code skipDependencyValidation} project property (any non-null, non-false value)
     * and an {@code ext.skipDependencyValidation} extra property. This lets specific
     * projects opt out via {@code ext.skipDependencyValidation = true} when they have
     * unresolvable BOM conflicts (e.g. Micronaut platform overrides).
     */
    private static boolean shouldSkip(Project project) {
        if (!project.hasProperty('skipDependencyValidation')) {
            return false
        }
        Object value = project.findProperty('skipDependencyValidation')
        if (value == null) {
            // -PskipDependencyValidation with no value is treated as truthy
            return true
        }
        return Boolean.parseBoolean(value.toString())
    }

    private static void validateDependencies(Project project) {
        String bomPath = detectBomPath(project)
        if (bomPath == null) {
            return
        }

        String bomName = bomPath.substring(bomPath.lastIndexOf(':') + 1)
        Set<Configuration> bomConfigurations = findBomConfigurations(project, bomName)
        if (bomConfigurations.isEmpty()) {
            return
        }

        Map<String, String> bomVersions = collectBomVersions(project, bomPath, bomConfigurations)
        if (bomVersions.isEmpty()) {
            project.logger.warn('No BOM versions collected for project \'{}\'. Skipping validation.', project.name)
            return
        }

        Set<String> allowedOverrides = resolveAllowedOverrides(project)
        Map<String, String> resolvedVersions = collectResolvedVersions(bomConfigurations)
        List<String> violations = new ArrayList<>()

        for (Map.Entry<String, String> entry : resolvedVersions.entrySet()) {
            if (allowedOverrides.contains(entry.key)) {
                continue
            }
            String bomVersion = bomVersions.get(entry.key)
            if (bomVersion != null && bomVersion != entry.value) {
                violations.add("  ${entry.key} - resolved ${entry.value}, expected ${bomVersion}" as String)
            }
        }

        if (!violations.isEmpty()) {
            String message = "Dependency version validation failed for project '${project.name}'.\n" +
                    "The following dependencies resolved to versions different from the BOM (${bomPath}):\n\n" +
                    violations.join('\n') + '\n\n' +
                    'A transitive dependency is upgrading these versions.\n' +
                    'To fix, update the dependency version in dependencies.gradle or add an exclusion in the build file.\n' +
                    "For intentional overrides, add the coordinate to project.ext.${ALLOWED_OVERRIDES_EXT} (e.g. as a Set<String> of 'group:name' keys)."
            throw new GradleException(message)
        }
    }

    /**
     * Resolves the set of {@code "group:name"} coordinates the project has marked as
     * intentional version overrides, via the {@link #ALLOWED_OVERRIDES_EXT} ext property.
     * Accepts a {@link Collection} or a single {@link CharSequence}. Unknown types are
     * silently ignored.
     */
    private static Set<String> resolveAllowedOverrides(Project project) {
        if (!project.extensions.extraProperties.has(ALLOWED_OVERRIDES_EXT)) {
            return Collections.emptySet()
        }
        Object raw = project.extensions.extraProperties.get(ALLOWED_OVERRIDES_EXT)
        if (raw instanceof CharSequence) {
            return Collections.singleton(raw.toString())
        }
        if (raw instanceof Collection) {
            Set<String> result = new LinkedHashSet<>()
            for (Object item : (Collection<?>) raw) {
                if (item != null) {
                    result.add(item.toString())
                }
            }
            return result
        }
        return Collections.emptySet()
    }

    /**
     * Scans the project's configurations to find which BOM project is in use.
     *
     * <p>Exactly one Grails BOM is expected on a project: the BOMs are split by
     * integration (default / hibernate5 / micronaut), so a project selects a single
     * variant. This method returns the path of the one declared Grails BOM, {@code null}
     * when none is declared, and fails the build when more than one distinct Grails BOM
     * is found (which indicates a misconfiguration - e.g. layering grails-bom and
     * grails-micronaut-bom on the same project).</p>
     */
    static String detectBomPath(Project project) {
        Set<String> bomPaths = new LinkedHashSet<>()

        for (Configuration config : project.configurations) {
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
                            'Exactly one Grails BOM may be applied; the BOMs are split by integration ' +
                            '(default / hibernate5 / micronaut), so a project must select a single variant.'
            )
        }

        bomPaths.first()
    }

    /**
     * Collects all BOM-managed dependency versions in two phases:
     * <ol>
     *   <li>Direct constraints from the BOM project's {@code api} configuration</li>
     *   <li>Inherited platform constraints (e.g., spring-boot-dependencies) via per-module probing</li>
     * </ol>
     */
    private static Map<String, String> collectBomVersions(Project project, String bomPath, Set<Configuration> bomConfigurations) {
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
        Set<String> resolvedModules = collectAllResolvedModuleKeys(bomConfigurations)
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

    /**
     * Collects the resolved version for each external module across the given configurations.
     */
    private static Map<String, String> collectResolvedVersions(Set<Configuration> configurations) {
        Map<String, String> versions = new LinkedHashMap<>()
        for (Configuration config : configurations) {
            try {
                for (ResolvedComponentResult component : config.incoming.resolutionResult.allComponents) {
                    if (component.id instanceof ModuleComponentIdentifier) {
                        ModuleComponentIdentifier mid = (ModuleComponentIdentifier) component.id
                        versions.put("${mid.group}:${mid.module}" as String, mid.version)
                    }
                }
            } catch (Exception ignored) {
                // Skip configurations that fail to resolve
            }
        }
        return versions
    }

    private static Set<String> collectAllResolvedModuleKeys(Set<Configuration> configurations) {
        Set<String> modules = new LinkedHashSet<>()
        for (Configuration config : configurations) {
            try {
                for (ResolvedComponentResult component : config.incoming.resolutionResult.allComponents) {
                    if (component.id instanceof ModuleComponentIdentifier) {
                        ModuleComponentIdentifier mid = (ModuleComponentIdentifier) component.id
                        modules.add("${mid.group}:${mid.module}" as String)
                    }
                }
            } catch (Exception ignored) {
                // Skip
            }
        }
        return modules
    }
}

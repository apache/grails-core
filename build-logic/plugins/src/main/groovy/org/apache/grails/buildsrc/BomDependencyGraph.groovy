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

import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.VersionConstraint
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.result.DependencyResult
import org.gradle.api.artifacts.result.ResolutionResult
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.UnresolvedDependencyResult

/**
 * What {@code validateDependencyVersions} checks, collected in one walk of a project's BOM-bearing
 * configurations: the version each external module resolved to, and every dependency edge requested
 * with a dynamic version. {@link GrailsDependencyValidatorPlugin} computes it once per project in a
 * memoized provider and maps each task input off it, so the configurations are found, resolved and
 * walked a single time; the modules that walk resolved are also where the BOM probing starts.
 *
 * @since 8.0
 */
@CompileStatic
class BomDependencyGraph {

    /** The graph of a project that has no BOM to validate against. */
    static final BomDependencyGraph NONE = new BomDependencyGraph('', [:], [:], [:], [])

    /** Marks a declarer that is a build script of this build rather than a published module. */
    static final String BUILD_SCRIPT_MARKER = ' (declared in a build script)'

    /** Marks a request that is a dependency constraint, which an exclusion cannot remove. */
    static final String CONSTRAINT_MARKER = ' (constraint)'

    /** Path of the BOM in use, or empty when there is none. */
    final String bomPath

    /** Versions the BOM manages, including those inherited from platforms it imports. */
    final Map<String, String> bomVersions

    /** {@code "group:name"} to the version the configurations resolved it to. */
    final Map<String, String> resolvedVersions

    /**
     * {@code "group:name:version"} of every request made with a dynamic version to the declarers
     * of that request, each suffixed with the markers that apply to it.
     */
    final Map<String, String> dynamicVersionRequests

    /**
     * One message per configuration or dependency the walk could not resolve, so part of the graph was
     * never checked. The task logs them at warn level from its action, so they show on every run and
     * not only on the one that computed the inputs and stored the configuration cache entry.
     */
    final List<String> resolutionWarnings

    private BomDependencyGraph(String bomPath, Map<String, String> bomVersions, Map<String, String> resolvedVersions,
                               Map<String, String> dynamicVersionRequests, List<String> resolutionWarnings) {
        this.bomPath = bomPath
        this.bomVersions = bomVersions
        this.resolvedVersions = resolvedVersions
        this.dynamicVersionRequests = dynamicVersionRequests
        this.resolutionWarnings = resolutionWarnings
    }

    /**
     * Resolves the given configurations once and reads both the selected versions and the dynamic
     * requests off each resolution result. The BOM versions are not known yet, because probing them
     * starts from the modules that resolved; {@link #withBomVersions} adds them.
     *
     * <p>Resolution does not fail the build here. A configuration whose resolution throws is skipped,
     * and a dependency that does not resolve, as every one in a repository Gradle disabled after an
     * earlier error does, is walked no further, so nothing below it is checked. Both are recorded in
     * {@link #resolutionWarnings} with the configuration's name, so a validation that passed only
     * because part of the graph was never seen says so.</p>
     */
    static BomDependencyGraph collect(String projectPath, String bomPath, Set<Configuration> configurations) {
        Map<String, String> resolved = new LinkedHashMap<>()
        Map<String, Set<String>> dynamic = new TreeMap<>()
        List<String> warnings = []
        for (Configuration config : configurations) {
            try {
                ResolutionResult result = config.incoming.resolutionResult
                for (ResolvedComponentResult component : result.allComponents) {
                    if (component.id instanceof ModuleComponentIdentifier) {
                        ModuleComponentIdentifier id = (ModuleComponentIdentifier) component.id
                        resolved.put("${id.group}:${id.module}" as String, id.version)
                    }
                }
                for (DependencyResult dependency : result.allDependencies) {
                    if (dependency instanceof UnresolvedDependencyResult) {
                        warnings.add("Could not resolve ${dependency.requested.displayName} in configuration '${config.name}' " +
                                "of project '${projectPath}', so its dependencies were not checked: ${firstLine(dependency.failure)}" as String)
                    }
                    String request = dynamicRequest(dependency)
                    if (request != null) {
                        dynamic.computeIfAbsent(request) { String key -> new TreeSet<String>() }.add(declarer(dependency))
                    }
                }
            } catch (Exception e) {
                warnings.add("Skipped configuration '${config.name}' of project '${projectPath}' " +
                        "because it failed to resolve: ${firstLine(e)}" as String)
            }
        }
        Map<String, String> requests = new LinkedHashMap<>()
        for (Map.Entry<String, Set<String>> entry : dynamic.entrySet()) {
            requests.put(entry.key, entry.value.join(', '))
        }
        new BomDependencyGraph(bomPath, [:], resolved, requests, warnings)
    }

    /** This graph with the versions the BOM manages, probed from the modules the walk resolved. */
    BomDependencyGraph withBomVersions(Map<String, String> bomVersions) {
        new BomDependencyGraph(bomPath, bomVersions, resolvedVersions, dynamicVersionRequests, resolutionWarnings)
    }

    /**
     * Whether a version string is one Gradle resolves by listing the available versions: a range
     * ({@code [1.0,2.0)}, {@code ]1.0,2.0[}, {@code (,2.0]}), a prefix ({@code 1.+}, {@code +}) or a
     * {@code latest.<status>} selector. A range always opens with a bracket, so a fixed version that
     * merely contains one, or a comma, is not mistaken for one.
     */
    static boolean isDynamicVersion(String version) {
        if (!version) {
            return false
        }
        version.startsWith('latest.') || version.endsWith('+') || version.matches(/[\[\](].*/)
    }

    /** The requested {@code "group:name:version"} when the version is dynamic, otherwise {@code null}. */
    private static String dynamicRequest(DependencyResult dependency) {
        if (!(dependency.requested instanceof ModuleComponentSelector)) {
            return null
        }
        ModuleComponentSelector selector = (ModuleComponentSelector) dependency.requested
        VersionConstraint constraint = selector.versionConstraint
        String dynamic = [constraint.strictVersion, constraint.requiredVersion, constraint.preferredVersion]
                .find { String version -> isDynamicVersion(version) }
        dynamic == null ? null : "${selector.group}:${selector.module}:${dynamic}" as String
    }

    /** The component that makes the request, marked when it is a build script or when the request is a constraint. */
    private static String declarer(DependencyResult dependency) {
        String from = dependency.from.id.displayName
        if (dependency.from.id instanceof ProjectComponentIdentifier) {
            from += BUILD_SCRIPT_MARKER
        }
        if (dependency.constraint) {
            from += CONSTRAINT_MARKER
        }
        from
    }

    /**
     * The first line of the root cause's message: Gradle wraps a repository error or a failing resolve
     * hook in a chain of exceptions that each repeat the configuration, and the lines after the first
     * list the locations searched.
     */
    private static String firstLine(Throwable failure) {
        Throwable cause = failure
        while (cause.cause != null && cause.cause !== cause) {
            cause = cause.cause
        }
        String message = cause.message?.trim()
        message ? message.readLines().first() : cause.class.simpleName
    }
}

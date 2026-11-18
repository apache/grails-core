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

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

/**
 * Fails when a transitive dependency resolves to a version other than the one the BOM manages, or
 * when any dependency is requested with a dynamic version. Both checks run before the task fails, so
 * one failure reports everything there is to fix.
 *
 * <p>The task compares maps of {@code "group:name" -> version} and nothing more. They are supplied
 * as providers by {@link GrailsDependencyValidatorPlugin}, so the dependency graph is inspected while
 * the task's inputs are computed rather than from inside the task action - the task holds no reference
 * to the project and stays compatible with the configuration cache.</p>
 *
 * @since 8.0
 */
@CompileStatic
abstract class ValidateDependencyVersionsTask extends DefaultTask {

    /** Names the project in the failure message. */
    @Input
    abstract Property<String> getProjectName()

    /** Path of the BOM in use, or empty when the project has none to validate against. */
    @Input
    abstract Property<String> getBomPath()

    /** Versions the BOM manages, including those inherited from platforms it imports. */
    @Input
    abstract MapProperty<String, String> getBomVersions()

    /** Versions the project's BOM-bearing configurations actually resolved to. */
    @Input
    abstract MapProperty<String, String> getResolvedVersions()

    /** Coordinates exempted from validation because the divergence is deliberate. */
    @Input
    abstract SetProperty<String> getAllowedOverrides()

    /**
     * {@code "group:name:version"} of every request made with a dynamic version to the declarers of
     * that request; see {@link BomDependencyGraph#dynamicVersionRequests}.
     */
    @Input
    abstract MapProperty<String, String> getDynamicVersionRequests()

    /** {@code "group:name"} keys whose dynamic version requests are tolerated. */
    @Input
    abstract SetProperty<String> getAllowedDynamicVersions()

    /**
     * What the walk of the configurations could not resolve, and therefore never checked; see
     * {@link BomDependencyGraph#resolutionWarnings}. Logged here rather than where it was found, so a
     * run that reuses the configuration cache entry still shows it.
     */
    @Input
    abstract ListProperty<String> getResolutionWarnings()

    @TaskAction
    void validate() {
        for (String warning : resolutionWarnings.get()) {
            logger.warn(warning)
        }

        String bom = bomPath.get()
        if (!bom) {
            return
        }

        List<String> reports = []
        Map<String, String> dynamic = dynamicRequests()
        if (!dynamic.isEmpty()) {
            reports.add(dynamicVersionReport(dynamic))
        }

        Map<String, String> managed = bomVersions.get()
        if (managed.isEmpty()) {
            logger.warn('No BOM versions collected for project \'{}\'. Skipping the comparison against the BOM.', projectName.get())
        } else {
            List<String> violations = bomViolations(managed)
            if (!violations.isEmpty()) {
                reports.add(bomMismatchReport(bom, violations))
            }
        }

        if (!reports.isEmpty()) {
            throw new GradleException("Dependency version validation failed for project '${projectName.get()}'.\n" + reports.join('\n\n'))
        }
    }

    /** The dynamic version requests that no {@link #getAllowedDynamicVersions} entry tolerates. */
    private Map<String, String> dynamicRequests() {
        Set<String> allowedDynamic = allowedDynamicVersions.get()
        dynamicVersionRequests.get().findAll { String request, String declarers ->
            !allowedDynamic.any { String allowed -> request.startsWith(allowed + ':') }
        }
    }

    /** The resolved versions that differ from the BOM's, except the {@link #getAllowedOverrides}. */
    private List<String> bomViolations(Map<String, String> managed) {
        Set<String> allowed = allowedOverrides.get()
        List<String> violations = []
        for (Map.Entry<String, String> entry : resolvedVersions.get().entrySet()) {
            if (allowed.contains(entry.key)) {
                continue
            }
            String bomVersion = managed.get(entry.key)
            if (bomVersion != null && bomVersion != entry.value) {
                violations.add("  ${entry.key} - resolved ${entry.value}, expected ${bomVersion}" as String)
            }
        }
        violations
    }

    private static String dynamicVersionReport(Map<String, String> dynamic) {
        List<String> lines = dynamic.collect { String request, String declarers -> "  ${declarers} -> ${request}" as String }
        'The following dependencies are requested with a dynamic version (a range, a prefix such as 1.+, or latest.*):\n\n' +
                lines.join('\n') + '\n\n' +
                'A dynamic version makes every resolution list the versions available in the repository once the listing cache expires,\n' +
                'so the build depends on the repository answering even when every artifact is cached, and the selected version moves over time.\n' +
                'To fix a request' + BomDependencyGraph.BUILD_SCRIPT_MARKER + ', pin the version in that build script or use the BOM-managed coordinate.\n' +
                'To fix a request made by a published module, exclude the module from that dependency and declare the BOM-managed coordinate next to it.\n' +
                'An exclusion does not remove a request' + BomDependencyGraph.CONSTRAINT_MARKER + ': rewrite it with a component metadata rule\n' +
                '(components.withModule(...) { allVariants { withDependencyConstraints { ... } } }).\n' +
                "For a dynamic version that must stay, add its 'group:name' to project.ext.${GrailsDependencyValidatorPlugin.ALLOWED_DYNAMIC_VERSIONS_EXT} with the reason."
    }

    private static String bomMismatchReport(String bom, List<String> violations) {
        "The following dependencies resolved to versions different from the BOM (${bom}):\n\n" +
                violations.join('\n') + '\n\n' +
                'A transitive dependency is upgrading these versions.\n' +
                'To fix, update the dependency version in dependencies.gradle or add an exclusion in the build file.\n' +
                "For intentional overrides, add the coordinate to project.ext.${GrailsDependencyValidatorPlugin.ALLOWED_OVERRIDES_EXT} (e.g. as a Set<String> of 'group:name' keys)."
    }
}

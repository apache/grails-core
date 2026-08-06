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
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

/**
 * Fails when a transitive dependency resolves to a version other than the one the BOM manages.
 *
 * <p>The task compares two maps of {@code "group:name" -> version} and nothing more. Both are supplied
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

    @TaskAction
    void validate() {
        String bom = bomPath.get()
        if (!bom) {
            return
        }

        Map<String, String> managed = bomVersions.get()
        if (managed.isEmpty()) {
            logger.warn('No BOM versions collected for project \'{}\'. Skipping validation.', projectName.get())
            return
        }

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

        if (!violations.isEmpty()) {
            throw new GradleException(
                    "Dependency version validation failed for project '${projectName.get()}'.\n" +
                            "The following dependencies resolved to versions different from the BOM (${bom}):\n\n" +
                            violations.join('\n') + '\n\n' +
                            'A transitive dependency is upgrading these versions.\n' +
                            'To fix, update the dependency version in dependencies.gradle or add an exclusion in the build file.\n' +
                            "For intentional overrides, add the coordinate to project.ext.${GrailsDependencyValidatorPlugin.ALLOWED_OVERRIDES_EXT} (e.g. as a Set<String> of 'group:name' keys).")
        }
    }
}

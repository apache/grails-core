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
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

import org.apache.maven.model.Dependency
import org.apache.maven.model.Model

/**
 * Fails when a BOM's published POM carries a version property that does not need to exist, or
 * overrides a version its parent BOM manages in a way that cannot be overridden consistently.
 *
 * <p>Every version property the BOM owns must be published, that is, used by at least one entry of
 * the POM. A property no entry uses changes nothing for a consumer who sets it.</p>
 *
 * <p>For every module the POM manages that a parent BOM also manages:</p>
 * <ul>
 *   <li>the version must be written as the property the parent controls that module with, so a
 *   consumer setting that one property moves the version in both BOMs. A version the parent writes
 *   literally only moves with the property the parent itself is imported with, so a pin of it has
 *   no consistent form and is reported without a rename to make; and</li>
 *   <li>the version must differ from the parent's, since a pin that repeats the parent's version
 *   only makes the BOM larger.</li>
 * </ul>
 *
 * <p>The parent side arrives as two maps computed by {@link GrailsBomPropertyValidatorPlugin} while
 * the task's inputs are resolved, so the task holds no reference to the project.</p>
 *
 * @since 8.0
 */
@CompileStatic
abstract class ValidateBomPropertiesTask extends DefaultTask {

    /** Names the project in the failure message. */
    @Input
    abstract Property<String> getProjectName()

    /** The POM the BOM publishes; there is nothing to check when the project publishes none. */
    @Optional
    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    abstract RegularFileProperty getPomFile()

    /** The parent BOMs as {@code group:artifact:version}. */
    @Input
    abstract ListProperty<String> getParentBoms()

    /** {@code group:artifact} to the version the parent BOMs manage it at. */
    @Input
    abstract MapProperty<String, String> getParentVersions()

    /** {@code group:artifact} to the parent-level property controlling its version, or empty. */
    @Input
    abstract MapProperty<String, String> getParentProperties()

    /** The version properties the BOM owns, with their versions; each one must be published. */
    @Input
    abstract MapProperty<String, String> getVersionProperties()

    /** Version properties the BOM owns that may go unpublished. */
    @Input
    abstract SetProperty<String> getUnusedVersionExemptions()

    /** Properties, or {@code group:artifact} keys, allowed to differ from the parent's property name. */
    @Input
    abstract SetProperty<String> getPropertyNameExemptions()

    /** Properties, or {@code group:artifact} keys, allowed to repeat the parent's version. */
    @Input
    abstract SetProperty<String> getRedundantVersionExemptions()

    @TaskAction
    void validate() {
        if (!pomFile.present || (parentBoms.get().isEmpty() && versionProperties.get().isEmpty())) {
            return
        }

        Model bom = PomVersions.read(pomFile.get().asFile)
        Map<String, String> bomProperties = PomVersions.properties(bom)
        List<Dependency> managed = PomVersions.managedDependencies(bom)

        Map<String, String> managedVersions = parentVersions.get()
        Map<String, String> managedProperties = parentProperties.get()
        Set<String> nameExemptions = propertyNameExemptions.get()
        Set<String> redundantExemptions = redundantVersionExemptions.get()

        Set<String> parentKeys = parentBoms.get().collect { String coordinates ->
            coordinates.substring(0, coordinates.lastIndexOf(':'))
        }.toSet()
        // a module the parent writes literally is moved by the property the parent itself is imported with
        String parentImportProperty = managed.findResult { Dependency dependency ->
            PomVersions.isImport(dependency) && PomVersions.key(dependency, bomProperties) in parentKeys
                    ? PomVersions.propertyReference(dependency.version)
                    : null
        } ?: ''

        Map<String, List<String>> misnamed = new TreeMap<>()
        Map<String, List<String>> literal = new TreeMap<>()
        Map<String, List<String>> redundant = new TreeMap<>()
        for (Dependency dependency : managed) {
            String key = PomVersions.key(dependency, bomProperties)
            String parentVersion = key != null ? managedVersions.get(key) : null
            if (parentVersion == null || key in parentKeys) {
                continue
            }
            String property = PomVersions.propertyReference(dependency.version)
            String ownProperty = managedProperties.get(key)
            String parentProperty = ownProperty ?: parentImportProperty
            String version = PomVersions.interpolate(dependency.version, bomProperties)

            if (parentProperty && property != parentProperty && !isExempt(nameExemptions, property, key)) {
                String declared = property ? "\${${property}}" : "the literal version ${version}"
                if (ownProperty) {
                    misnamed.computeIfAbsent("${declared} should be \${${parentProperty}}" as String) { [] }.add(key)
                } else {
                    literal.computeIfAbsent("${declared}, where only \${${parentProperty}} moves the parent's version" as String) { [] }.add(key)
                }
            }
            if (version == parentVersion && !isExempt(redundantExemptions, property, key)) {
                String pin = property ? "${property} = ${version}" : version
                redundant.computeIfAbsent(pin) { [] }.add(key)
            }
        }

        Map<String, List<String>> unused = new TreeMap<>()
        Set<String> unusedExemptions = unusedVersionExemptions.get()
        versionProperties.get().each { String property, String version ->
            if (!bom.properties.containsKey(property) && !unusedExemptions.contains(property)) {
                unused.put("${property} = ${version}" as String, [])
            }
        }

        if (unused.isEmpty() && misnamed.isEmpty() && literal.isEmpty() && redundant.isEmpty()) {
            return
        }

        StringBuilder message = new StringBuilder("BOM property validation failed for project '${projectName.get()}'.\n")
        if (!unused.isEmpty()) {
            message.append('\nThese version properties are defined for this BOM, but no entry it publishes uses them:\n')
            appendGroups(message, unused)
            message.append('\nRemove them from dependencies.gradle, or add the dependency that should use them - its key ')
                    .append('in the dependency map must reduce to the property name.\n')
        }
        if (!misnamed.isEmpty() || !literal.isEmpty() || !redundant.isEmpty()) {
            message.append("\nParent BOMs: ${parentBoms.get().join(', ')}\n")
        }
        if (!misnamed.isEmpty()) {
            message.append('\nThese versions override a parent BOM version under a different property name:\n')
            appendGroups(message, misnamed)
            message.append('\nAn override must reuse the property name the parent BOM uses, so that setting that one ')
                    .append('property moves the version in both BOMs. Rename the version key in dependencies.gradle.\n')
        }
        if (!literal.isEmpty()) {
            message.append('\nThese versions override a version the parent BOM writes literally:\n')
            appendGroups(message, literal)
            message.append('\nOnly the property the parent BOM is imported with moves such a version, and a pin under that ')
                    .append('property would repeat the parent\'s version. Remove the pin from dependencies.gradle and ')
                    .append('inherit the parent BOM\'s version, or add a documented exemption.\n')
        }
        if (!redundant.isEmpty()) {
            message.append('\nThese versions repeat the version the parent BOM already manages:\n')
            appendGroups(message, redundant)
            message.append('\nRemove the pin from dependencies.gradle and inherit the parent BOM\'s version.\n')
        }
        message.append('\nFor a documented, deliberate exception, add the property to ')
                .append("project.ext.${GrailsBomPropertyValidatorPlugin.UNUSED_VERSION_EXEMPTIONS_EXT}, ")
                .append("project.ext.${GrailsBomPropertyValidatorPlugin.PROPERTY_NAME_EXEMPTIONS_EXT} or ")
                .append("project.ext.${GrailsBomPropertyValidatorPlugin.REDUNDANT_VERSION_EXEMPTIONS_EXT} ")
                .append('(the last two also take a group:name key).\n')
        throw new GradleException(message.toString())
    }

    private static boolean isExempt(Set<String> exemptions, String property, String key) {
        exemptions.contains(key) || (property != null && exemptions.contains(property))
    }

    private static void appendGroups(StringBuilder message, Map<String, List<String>> groups) {
        groups.each { String heading, List<String> keys ->
            message.append("  ${heading}\n")
            keys.each { String key -> message.append("      ${key}\n") }
        }
    }
}

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

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.provider.Provider
import org.gradle.api.publish.maven.tasks.GenerateMavenPom
import org.gradle.api.tasks.TaskProvider

/**
 * Validates the version properties a BOM ({@code java-platform}) project publishes: each one the BOM
 * owns must be used, and an override of a version a parent BOM (e.g. spring-boot-dependencies)
 * manages must use the parent's property name and change the version.
 *
 * <p>Usage: apply {@code org.apache.grails.buildsrc.bom-property-validator} in the BOM's
 * {@code plugins} block, then run {@code ./gradlew validateBomProperties}. Like
 * {@code validateDependencyVersions}, the task is run by CI rather than hung off {@code check}.</p>
 *
 * @see ValidateBomPropertiesTask
 * @since 8.0
 */
@CompileStatic
class GrailsBomPropertyValidatorPlugin implements Plugin<Project> {

    static final String VALIDATE_TASK_NAME = 'validateBomProperties'

    /**
     * Opts a project out of {@link #VALIDATE_TASK_NAME}; see {@link GradleUtils#isOptedOut}. Separate
     * from {@code skipDependencyValidation}, which only opts out of {@code validateDependencyVersions}.
     */
    static final String SKIP_PROPERTY = 'skipBomPropertyValidation'

    /**
     * Project ext property holding the {@code group:artifact:version} coordinates of the third-party
     * BOMs a BOM project inherits from, which {@link #VALIDATE_TASK_NAME} validates it against.
     */
    static final String PARENT_BOMS_EXT = 'parentBoms'

    /**
     * Project ext property holding the version map a BOM project owns, as {@code property -> version}.
     * {@link #VALIDATE_TASK_NAME} fails for any of these properties the published POM does not use.
     */
    static final String VERSION_PROPERTIES_EXT = 'bomVersionProperties'

    /**
     * Project ext property holding the owned version properties that may go unpublished, for example
     * a version only build scripts read. Each entry needs a documented reason.
     */
    static final String UNUSED_VERSION_EXEMPTIONS_EXT = 'bomUnusedVersionExemptions'

    /**
     * Project ext property holding the version properties (or {@code group:name} keys) that may
     * override a parent BOM version under a different property name. Each entry needs a documented
     * reason.
     */
    static final String PROPERTY_NAME_EXEMPTIONS_EXT = 'bomPropertyNameExemptions'

    /**
     * Project ext property holding the version properties (or {@code group:name} keys) that may pin
     * the same version a parent BOM manages. Each entry needs a documented reason.
     */
    static final String REDUNDANT_VERSION_EXEMPTIONS_EXT = 'bomRedundantVersionExemptions'

    private static final String POM_TASK_NAME = 'generatePomFileForMavenPublication'

    @Override
    void apply(Project project) {
        project.plugins.withId('java-platform') {
            registerBomPropertyValidation(project)
        }
    }

    /**
     * Registers {@link #VALIDATE_TASK_NAME}, wired entirely from providers.
     *
     * <p>Resolving and walking the parent BOMs needs the project, so it happens once, while the task's
     * inputs are computed. The task itself then only compares the published POM with what it was
     * given.</p>
     *
     * <p>Every provider short-circuits when the project opts out through {@link #SKIP_PROPERTY}, so a
     * skipped project never resolves its parent BOMs, and the task finds nothing to check.</p>
     */
    private static void registerBomPropertyValidation(Project project) {
        Provider<List<String>> parentBoms = project.provider {
            GradleUtils.isOptedOut(project, SKIP_PROPERTY)
                    ? Collections.<String> emptyList()
                    : GradleUtils.extStrings(project, PARENT_BOMS_EXT).toList()
        }
        Provider<Map<String, String>> versionProperties = project.provider {
            GradleUtils.isOptedOut(project, SKIP_PROPERTY)
                    ? Collections.<String, String> emptyMap()
                    : extVersions(project, VERSION_PROPERTIES_EXT)
        }
        Provider<ParentBomVersions> parentModel = project.provider({
            ParentBomVersions.resolve(parentBoms.get()) { String coordinates -> resolvePom(project, coordinates) }
        }.memoize())

        project.tasks.register(VALIDATE_TASK_NAME, ValidateBomPropertiesTask) { ValidateBomPropertiesTask task ->
            task.group = 'verification'
            task.description = 'Validates that every version property the BOM owns is used, and that it overrides a parent BOM version only under the parent\'s property name and with a different version.'
            task.projectName.set(project.name)
            task.parentBoms.set(parentBoms)
            task.parentVersions.set(parentModel.map { ParentBomVersions model -> model.versions })
            task.parentProperties.set(parentModel.map { ParentBomVersions model -> model.properties })
            task.versionProperties.set(versionProperties)
            task.unusedVersionExemptions.set(project.provider { GradleUtils.extStrings(project, UNUSED_VERSION_EXEMPTIONS_EXT) })
            task.propertyNameExemptions.set(project.provider { GradleUtils.extStrings(project, PROPERTY_NAME_EXEMPTIONS_EXT) })
            task.redundantVersionExemptions.set(project.provider { GradleUtils.extStrings(project, REDUNDANT_VERSION_EXEMPTIONS_EXT) })
            if (project.tasks.names.contains(POM_TASK_NAME)) {
                TaskProvider<GenerateMavenPom> generatePom = project.tasks.named(POM_TASK_NAME, GenerateMavenPom)
                task.pomFile.fileProvider(generatePom.map { GenerateMavenPom pomTask -> pomTask.destination })
                task.dependsOn(generatePom)
            }
        }
    }

    private static File resolvePom(Project project, String coordinates) {
        Configuration pom = project.configurations.detachedConfiguration(project.dependencies.create("${coordinates}@pom" as String))
        pom.transitive = false
        pom.singleFile
    }

    /**
     * Returns the {@code property -> version} map held by one of the project's own extra properties.
     * Anything but a {@link Map} is silently ignored.
     */
    private static Map<String, String> extVersions(Project project, String name) {
        Object raw = project.extensions.extraProperties.has(name) ? project.extensions.extraProperties.get(name) : null
        if (!(raw instanceof Map)) {
            return Collections.emptyMap()
        }
        Map<String, String> result = new LinkedHashMap<>()
        ((Map<?, ?>) raw).each { Object property, Object version ->
            if (property != null) {
                result.put(property.toString(), version?.toString() ?: '')
            }
        }
        result
    }
}

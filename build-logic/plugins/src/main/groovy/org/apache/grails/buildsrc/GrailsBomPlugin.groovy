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

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.plugins.JavaPlatformExtension
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider

import org.apache.grails.gradle.tasks.bom.ExtractDependenciesTask
import org.apache.grails.gradle.tasks.bom.ExtractedDependencyConstraint
import org.apache.grails.gradle.tasks.bom.PropertyNameCalculator

@CompileStatic
class GrailsBomPlugin implements Plugin<Project> {

    private static final String BASE_CONSTRAINTS_FILE = 'grails-bom-constraints.adoc'

    private static final Map<String, String> GRADLE_BUILD_PROJECTS = [
            'grails-gradle-plugins': 'org.apache.grails',
            'grails-gradle-model'  : 'org.apache.grails.gradle',
            'grails-gradle-common' : 'org.apache.grails.gradle',
            'grails-gradle-tasks'  : 'org.apache.grails',
    ]

    @Override
    void apply(Project project) {
        applyDependencyDefinitions(project)
        configureCoordinates(project)
        configureExtraProperties(project)
        configurePomCustomization(project)

        project.pluginManager.apply('java-platform')
        project.extensions.configure(JavaPlatformExtension) { JavaPlatformExtension extension ->
            extension.allowDependencies()
        }
        configureBomDependencies(project)
        configureExtractConstraints(project)
        configureSnapshotValidation(project)
    }

    private static void applyDependencyDefinitions(Project project) {
        // Unlike the former buildscript application, these definitions are available in the
        // project context after this plugin is applied, not in earlier buildscript contexts.
        project.apply([from: project.rootProject.layout.projectDirectory.file('dependencies.gradle')])
    }

    private static void configureCoordinates(Project project) {
        project.version = project.property('projectVersion')
        project.group = 'org.apache.grails'
    }

    private static void configureExtraProperties(Project project) {
        project.extensions.extraProperties.set('isReleaseBuild', System.getenv('GRAILS_PUBLISH_RELEASE') == 'true')
        project.extensions.extraProperties.set(
                'isPublishedExternal',
                System.getenv().containsKey('NEXUS_PUBLISH_STAGING_PROFILE_ID')
        )
        project.extensions.extraProperties.set('gradleBuildProjects', new LinkedHashMap<String, String>(GRADLE_BUILD_PROJECTS))
    }

    private static void configureBomDependencies(Project project) {
        project.configurations.register('bomDependencies') { Configuration configuration ->
            configuration.canBeResolved = true
            configuration.transitive = true
            configuration.extendsFrom(project.configurations.named('api').get())
        }
    }

    private static void configureExtractConstraints(Project project) {
        project.tasks.register(
                'extractConstraints',
                ExtractDependenciesTask
        ) { ExtractDependenciesTask task ->
            task.captureProjectServices(project.dependencies, project.configurations)
            task.setConfiguration(project.configurations.named('bomDependencies'))
            task.destination.set(project.layout.buildDirectory.file(constraintsFileName(project)))
            task.platformDefinitions.set(project.provider { getStringMap(project, 'combinedPlatforms') })
            task.definitions.set(project.provider { getStringMap(project, 'combinedDependencies') })
            task.projectName.set(project.name)
            task.versions.set(project.provider { getStringMap(project, 'combinedVersions') })

            project.rootProject.subprojects.each { Project subproject ->
                project.evaluationDependsOn(subproject.path)
            }
            task.projectArtifactIds.set(project.provider { projectArtifactIds(project) })
            task.forcedGroupPrefixes.set(['org.apache.grails.profiles': 'grails-profile'])
            task.projectCoordinateProperties.set(project.provider { projectCoordinateProperties(project) })
            task.dependsOn(project.tasks.named('generateMetadataFileForMavenPublication'),
                    project.tasks.named('generatePomFileForMavenPublication'))
        }
    }

    private static String constraintsFileName(Project project) {
        project.name == 'grails-base-bom' ? BASE_CONSTRAINTS_FILE : "${project.name}-constraints.adoc"
    }

    private static Map<String, String> projectArtifactIds(Project project) {
        Map<String, String> artifactIdMappings = [:]
        project.rootProject.subprojects.each { Project subproject ->
            artifactIdMappings[subproject.name] = (subproject.findProperty('pomArtifactId') ?: subproject.name).toString()
            if (project.name == 'grails-base-bom') {
                String cliArtifactId = subproject.findProperty('cliArtifactId') as String
                if (cliArtifactId) {
                    artifactIdMappings[cliArtifactId] = cliArtifactId
                }
            }
        }

        getGradleBuildProjects(project).each { String dependencyName, String dependencyGroup ->
            artifactIdMappings[dependencyName] = dependencyName
        }

        artifactIdMappings
    }

    private static Map<String, String> projectCoordinateProperties(Project project) {
        Map<String, String> projectCoordinates = [:]
        project.rootProject.subprojects.each { Project subproject ->
            String artifactId = (subproject.findProperty('pomArtifactId') ?: subproject.name).toString()
            String baseVersionName = artifactId.replaceAll('[.]', '-')
            projectCoordinates["${subproject.group}:${artifactId}:${subproject.version}".toString()] = baseVersionName
            if (project.name == 'grails-base-bom') {
                String cliArtifactId = subproject.findProperty('cliArtifactId') as String
                if (cliArtifactId) {
                    projectCoordinates["${subproject.group}:${cliArtifactId}:${subproject.version}".toString()] = cliArtifactId
                }
            }
        }

        getGradleBuildProjects(project).each { String dependencyName, String dependencyGroup ->
            projectCoordinates["${dependencyGroup}:${dependencyName}:${project.version}".toString()] = dependencyName
        }

        projectCoordinates
    }

    private static void configureSnapshotValidation(Project project) {
        Provider<? extends List<String>> snapshotDependencies = project.provider {
            List<String> snapshots = []
            project.configurations.each { Configuration configuration ->
                configuration.allDependencies.each { Dependency dependency ->
                    if (dependency.version && dependency.version.contains('-SNAPSHOT')) {
                        snapshots.add("${dependency.group}:${dependency.name} (${dependency.version})".toString())
                    }
                }
            }
            snapshots
        }
        TaskProvider<Task> validateNoSnapshotDependencies = project.tasks.register('validateNoSnapshotDependencies') { Task task ->
            task.group = 'publishing'
            task.description = 'Validates that no snapshot dependencies are present in the project when performing a release.'

            task.doLast {
                if (snapshotDependencies.get()) {
                    throw new GradleException("Releases cannot have a snapshot dependency: ${snapshotDependencies.get().first()}")
                }
            }
        }

        if (project.extensions.extraProperties.get('isReleaseBuild') &&
                project.extensions.extraProperties.get('isPublishedExternal')) {
            project.afterEvaluate {
                ['generateMetadataFileForMavenPublication', 'generatePomFileForMavenPublication'].each { String taskName ->
                    project.tasks.named(taskName).configure { Task task ->
                        task.dependsOn(validateNoSnapshotDependencies)
                    }
                }
            }
        }
    }

    private static void configurePomCustomization(Project project) {
        Provider<PropertyNameCalculator> bomPropertyNames = project.provider {
            PropertyNameCalculator calculator = new PropertyNameCalculator(
                    getStringMap(project, 'combinedPlatforms'),
                    getStringMap(project, 'combinedDependencies'),
                    getStringMap(project, 'combinedVersions'))
            calculator.addForcedGroupPrefix('org.apache.grails.profiles', 'grails-profile')
            calculator.addProjects(project.rootProject.subprojects)
            getGradleBuildProjects(project).each { String artifactId, String dependencyGroup ->
                calculator.addProject(dependencyGroup, artifactId, project.version.toString(), artifactId)
            }
            if (project.name == 'grails-base-bom') {
                project.rootProject.subprojects.each { Project subproject ->
                    String cliArtifactId = subproject.findProperty('cliArtifactId') as String
                    if (cliArtifactId) {
                        calculator.addProject(subproject.group.toString(), cliArtifactId, subproject.version.toString(), cliArtifactId)
                    }
                }
            }
            calculator
        }
        project.extensions.extraProperties.set('pomCustomization', createPomCustomization(bomPropertyNames))
    }

    @CompileDynamic
    private static Closure createPomCustomization(Provider<PropertyNameCalculator> bomPropertyNames) {
        { xml ->
            def root = xml.asNode()

            def propertiesNode = root.properties ? root.properties[0] : root.appendNode('properties')

            def depMgmt = root.dependencyManagement?.getAt(0)
            def deps = depMgmt?.dependencies?.getAt(0)
            if (deps) {
                PropertyNameCalculator propertyNameCalculator = bomPropertyNames.get()

                Map<String, String> pomProperties = [:]
                deps.dependency.each { dep ->
                    String groupId = dep.groupId.text().trim()
                    String artifactId = dep.artifactId.text().trim()
                    boolean isBom = dep.scope.text().trim() == 'import'

                    String inlineVersion = dep.version.text().trim()
                    if (inlineVersion == 'null') {
                        inlineVersion = null
                    }

                    if (inlineVersion) {
                        ExtractedDependencyConstraint extractedConstraint = propertyNameCalculator.calculate(
                                groupId,
                                artifactId,
                                inlineVersion,
                                isBom
                        )
                        if (extractedConstraint?.versionPropertyReference) {
                            dep.version[0].value = extractedConstraint.versionPropertyReference
                            pomProperties.put(extractedConstraint.versionPropertyName, inlineVersion)
                        }
                    } else {
                        throw new GradleException("Dependency $groupId:$artifactId does not have a version.")
                    }
                }

                for (Map.Entry<String, String> property : pomProperties.entrySet()) {
                    propertiesNode.appendNode(property.key, property.value)
                }
            }
        }
    }

    @SuppressWarnings('unchecked')
    private static Map<String, String> getStringMap(Project project, String propertyName) {
        (Map<String, String>) project.extensions.extraProperties.get(propertyName)
    }

    private static Map<String, String> getGradleBuildProjects(Project project) {
        getStringMap(project, 'gradleBuildProjects')
    }
}

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

package org.apache.grails.gradle.tasks.bom

import io.spring.gradle.dependencymanagement.org.apache.maven.model.Model
import io.spring.gradle.dependencymanagement.org.apache.maven.model.io.xpp3.MavenXpp3Reader
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.artifacts.*
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.artifacts.result.DependencyResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.internal.artifacts.dependencies.DefaultProjectDependencyConstraint
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*

import java.util.regex.Pattern

/**
 * Grails Bom files define their dependencies in a series of maps, this task takes those maps and generates an
 * asciidoc file containing all of the resolve dependencies and their versions in the bom.
 */
@CacheableTask
abstract class ExtractDependenciesTask extends DefaultTask {
    @InputFiles
    @Classpath
    abstract ConfigurableFileCollection getDependencyArtifacts()

    @OutputFile
    abstract RegularFileProperty getDestination()

    @Input
    abstract MapProperty<String,String> getVersions()

    @Input
    abstract Property<String> getConfigurationName()

    @Input
    abstract MapProperty<String, String> getPlatformDefinitions()

    @Input
    abstract MapProperty<String, String> getProjectArtifactIds()

    @Input
    abstract MapProperty<String, String> getDefinitions()

    void setConfiguration(NamedDomainObjectProvider<Configuration> config) {
        dependencyArtifacts.from(config)
        configurationName.set(config.name)
    }

    ExtractDependenciesTask() {
        doFirst {
            if(!project.pluginManager.hasPlugin('java-platform')) {
                throw new GradleException("The 'java-platform' plugin must be applied to the project to use this task.")
            }
        }
    }

    @TaskAction
    void generate() {
        File outputFile = destination.get().asFile
        outputFile.parentFile.mkdirs()

        Map<CoordinateHolder, ExtractedDependencyConstraint> constraints = [:]
        PropertyNameCalculator propertyNameCalculator = new PropertyNameCalculator(
                getPlatformDefinitions().get(),
                getDefinitions().get(),
                getVersions().get()
        )

        Configuration configuration = project.configurations.named(configurationName.get()).get()
        if(!configuration.canBeResolved) {
            throw new GradleException("The configuration ${configuration.name} must be resolvable to use this task.")
        }

        populateExplicitConstraints(configuration, constraints, propertyNameCalculator)

        Map<CoordinateHolder, List<CoordinateHolder>> exclusions = determineExclusions(configuration)
        populateInheritedConstraints(configuration, exclusions, constraints, propertyNameCalculator)

        List<String> lines = generateAsciiDoc(constraints)
        destination.get().asFile.withWriter { writer ->
            writer.writeLine '[cols="1,1,1,1,1,1", options="header"]'
            writer.writeLine '|==='
            writer.writeLine '| Index | Group | Artifact | Version | Property Name | Source'
            lines.each { line ->
                writer.writeLine(line)
            }
            writer.writeLine '|==='
        }
    }

    private List<String> generateAsciiDoc(Map<CoordinateHolder, ExtractedDependencyConstraint> constraints) {
        List lines = []
        constraints.values().sort { ExtractedDependencyConstraint a, ExtractedDependencyConstraint b -> a.groupId <=> b.groupId ?: a.artifactId <=> b.artifactId }.withIndex().each {
            int position = it.v2 + 1
            lines << "| ${position} | ${it.v1.groupId} | ${it.v1.artifactId} | ${it.v1.version} | ${it.v1.versionPropertyReference ?: ''} | ${it.v1.source} "
        }
        lines
    }

    private populateExplicitConstraints(Configuration configuration,
                                        Map<CoordinateHolder, ExtractedDependencyConstraint> constraints,
                                        PropertyNameCalculator propertyNameCalculator) {
        Map<String, String> artifactIdMappings = getProjectArtifactIds().get()
        configuration.getAllDependencyConstraints().all { DependencyConstraint constraint ->
            String groupId = constraint.module.group as String
            String artifactId = constraint.module.name as String
            String artifactVersion = constraint.version as String

            //TODO: need to look for project property ? or manually find the project?
            if (constraint instanceof DefaultProjectDependencyConstraint) {
                if(artifactIdMappings.containsKey(constraint.name)) {
                    artifactId = artifactIdMappings.get(constraint.name)
                }
            }

            ExtractedDependencyConstraint extractConstraint = propertyNameCalculator.calculate(groupId, artifactId, artifactVersion, false) ?: new ExtractedDependencyConstraint(groupId: groupId, artifactId: artifactId, version: artifactVersion)
            extractConstraint.source = constraint.name // this will be the project name
            constraints.put(new CoordinateHolder(groupId: extractConstraint.groupId, artifactId: extractConstraint.artifactId), extractConstraint)
        }
    }

    private Map<CoordinateHolder, List<CoordinateHolder>> determineExclusions(Configuration configuration) {
        Map<CoordinateHolder, List<CoordinateHolder>> exclusions = [:].withDefault { [] }
        for (Dependency dep  : configuration.allDependencies) {
            if (dep instanceof ModuleDependency) {
                CoordinateHolder foundCoordinate = new CoordinateHolder(groupId: dep.group, artifactId: dep.name)
                dep.excludeRules.each { ExcludeRule exclusionRule ->
                    CoordinateHolder exclusion = new CoordinateHolder(groupId: exclusionRule.group, artifactId: exclusionRule.module)
                    exclusions.get(foundCoordinate).add(exclusion)
                }
            }
        }
        exclusions
    }

    private void populateInheritedConstraints(Configuration configuration, Map<CoordinateHolder, List<CoordinateHolder>> exclusions, Map<CoordinateHolder, ExtractedDependencyConstraint> constraints, PropertyNameCalculator propertyNameCalculator) {
        for (DependencyResult result  : configuration.incoming.resolutionResult.allDependencies) {
            if(!(result instanceof ResolvedDependencyResult)) {
                throw new GradleException("Dependencies should be resolved prior to running this task.")
            }

            ResolvedDependencyResult dep = (ResolvedDependencyResult) result
            ModuleComponentSelector moduleComponentSelector = dep.requested as ModuleComponentSelector

            // Any non-constraint via api dependency should *always* be a platform dependency, so expand each of those
            CoordinateVersionHolder bomCoordinate = new CoordinateVersionHolder(
                    groupId: moduleComponentSelector.group,
                    artifactId: moduleComponentSelector.module,
                    version: moduleComponentSelector.version
            )

            // fetch the BOM as a pom file so it can be expanded
            ExtractedDependencyConstraint constraint = propertyNameCalculator.calculate(bomCoordinate.groupId, bomCoordinate.artifactId, bomCoordinate.version, true)
            constraint.source = bomCoordinate.artifactId
            constraints.put(bomCoordinate.toCoordinateHolder(), constraint)

            List<CoordinateHolder> exclusionRules = exclusions.get(bomCoordinate.toCoordinateHolder())
            populatePlatformDependencies(bomCoordinate, exclusionRules, constraints)
        }
    }

    Properties populatePlatformDependencies(CoordinateVersionHolder bomCoordinates, List<CoordinateHolder> exclusionRules, Map<CoordinateHolder, ExtractedDependencyConstraint> constraints, boolean error = true, int level = 0) {
        Dependency bomDependency = project.dependencies.create("${bomCoordinates.coordinates}@pom")
        Configuration dependencyConfiguration = project.configurations.detachedConfiguration(bomDependency)
        File bomPomFile = dependencyConfiguration.singleFile

        MavenXpp3Reader reader = new MavenXpp3Reader()
        Model model = reader.read(new FileReader(bomPomFile))

        Properties versionProperties = new Properties()
        if(model.parent) {
            // Need to populate the parent bom if it's present first
            CoordinateVersionHolder parentBom = new CoordinateVersionHolder(
                    groupId: model.parent.groupId,
                    artifactId: model.parent.artifactId,
                    version: model.parent.version
            )
            populatePlatformDependencies(parentBom, exclusionRules, constraints,false, level + 1)?.entrySet()?.each { Map.Entry<Object, Object> entry ->
                versionProperties.put(entry.key, entry.value)
            }
        }
        model.properties.entrySet().each { Map.Entry<Object, Object> entry ->
            versionProperties.put(entry.key, entry.value)
        }
        versionProperties.put('project.groupId', bomCoordinates.groupId)
        versionProperties.put('project.version', bomCoordinates.version)

        if (model.dependencyManagement && model.dependencyManagement.dependencies) {
            for (io.spring.gradle.dependencymanagement.org.apache.maven.model.Dependency depItem : model.dependencyManagement.dependencies) {
                CoordinateHolder baseCoordinates = new CoordinateHolder(
                        groupId: depItem.groupId,
                        artifactId: depItem.artifactId
                )

                CoordinateHolder resolvedCoordinates = new CoordinateHolder(
                        groupId: resolveMavenProperty(baseCoordinates.coordinatesWithoutVersion, depItem.groupId, versionProperties),
                        artifactId: resolveMavenProperty(baseCoordinates.coordinatesWithoutVersion, depItem.artifactId, versionProperties)
                )

                if (!constraints.containsKey(resolvedCoordinates)) {
                    boolean isExcluded = exclusionRules.any { CoordinateHolder excludedCoordinate ->
                        if (excludedCoordinate.groupId && excludedCoordinate.artifactId) {
                            return resolvedCoordinates == excludedCoordinate
                        }

                        if (excludedCoordinate.groupId && !excludedCoordinate.artifactId) {
                            return depItem.groupId == excludedCoordinate.groupId
                        }

                        if (!excludedCoordinate.groupId && excludedCoordinate.artifactId) {
                            return depItem.artifactId == excludedCoordinate.artifactId
                        }

                        false
                    }

                    if (!isExcluded) {
                        String resolvedVersion = resolveMavenProperty(resolvedCoordinates.coordinatesWithoutVersion, depItem.version, versionProperties)
                        String propertyName = depItem.version.contains('$') ? depItem.version : null
                        ExtractedDependencyConstraint constraint = new ExtractedDependencyConstraint(
                                groupId: resolvedCoordinates.groupId, artifactId: resolvedCoordinates.artifactId,
                                version: resolvedVersion, versionPropertyReference: propertyName, source: bomCoordinates.artifactId
                        )
                        if (depItem.scope == 'import') {
                            constraints.put(resolvedCoordinates, constraint)

                            CoordinateVersionHolder resolvedBomCoordinates = new CoordinateVersionHolder(
                                    groupId: resolvedCoordinates.groupId,
                                    artifactId: resolvedCoordinates.artifactId,
                                    version: resolvedVersion
                            )
                            populatePlatformDependencies(resolvedBomCoordinates, exclusionRules, constraints, error, level + 1)
                        } else {
                            constraints.put(resolvedCoordinates,constraint)
                        }
                    }
                }
            }
        } else {
            if (error) {
                // only the boms we directly include need to error since we expect a dependency management;
                // parent boms are sometimes use to share properties so we need to not error on these cases
                throw new GradleException("BOM ${bomCoordinates.coordinates} has no dependencyManagement section.")
            }
        }

        versionProperties
    }

    private String resolveMavenProperty(String errorDescription, String dynamicVersion, Map properties, int maxIterations = 10) {
        Pattern dynamicPattern = ~/\$\{([^}]+)\}/
        String expandedVersion = dynamicVersion

        int iterations = 0
        while ((expandedVersion =~ dynamicPattern).find() && iterations < maxIterations) {
            expandedVersion = expandedVersion.replaceAll(dynamicPattern) { String fullMatch, String propName ->
                String replacement = properties[propName] as String
                return replacement ? replacement : fullMatch
            }
            iterations++
        }

        if ((expandedVersion =~ dynamicPattern).find()) {
            logger.warn("Reached max iterations for {} while resolving properties in: {}", errorDescription, dynamicVersion)
        }

        expandedVersion
    }
}

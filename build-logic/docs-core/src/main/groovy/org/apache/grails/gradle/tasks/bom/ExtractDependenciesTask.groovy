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

import java.util.regex.Pattern
import javax.xml.parsers.DocumentBuilderFactory

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.DependencyConstraint
import org.gradle.api.artifacts.ExcludeRule
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.artifacts.component.ProjectComponentSelector
import org.gradle.api.artifacts.result.DependencyResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.NodeList

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
    abstract MapProperty<String, String> getVersions()

    @Input
    abstract Property<String> getConfigurationName()

    @Input
    abstract MapProperty<String, String> getPlatformDefinitions()

    @Input
    abstract MapProperty<String, String> getProjectArtifactIds()

    @Input
    abstract MapProperty<String, String> getDefinitions()

    @Input
    abstract Property<String> getProjectName()

    @Input
    abstract MapProperty<String, String> getForcedGroupPrefixes()

    @Input
    abstract MapProperty<String, String> getProjectCoordinateProperties()

    // Captured at configuration time to avoid deprecated Task.project access at execution time.
    // See: https://docs.gradle.org/current/userguide/configuration_cache.html#config_cache:requirements:use_project_during_execution
    @Internal
    DependencyHandler dependencyHandler

    @Internal
    ConfigurationContainer configurationContainer

    /**
     * When {@code true}, transitive platform dependencies that are not explicitly
     * registered in {@code combinedPlatforms} / {@code dependencies.gradle} will be
     * auto-registered in {@link PropertyNameCalculator} instead of causing a build
     * failure.  This is required for BOMs that import an external platform via the
     * gradle module format (e.g. {@code micronaut-platform}) which itself imports
     * many sub-BOMs that Grails does not manage directly.
     *
     * <p>Defaults to {@code false} so that existing BOMs (grails-bom, grails-base-bom)
     * retain the strict validation that every platform dependency has a known property
     * name in {@code dependencies.gradle}.</p>
     */
    @Input
    abstract Property<Boolean> getAutoRegisterTransitivePlatforms()

    void setConfiguration(NamedDomainObjectProvider<Configuration> config) {
        dependencyArtifacts.from(config)
        configurationName.set(config.name)
    }

    ExtractDependenciesTask() {
        autoRegisterTransitivePlatforms.convention(false)
    }

    /**
     * Captures project-scoped services at configuration time so they can be used
     * at execution time without accessing the deprecated Task.project property.
     */
    void captureProjectServices(DependencyHandler dependencies, ConfigurationContainer configurations) {
        this.dependencyHandler = dependencies
        this.configurationContainer = configurations
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
        for (Map.Entry<String, String> entry : getForcedGroupPrefixes().get().entrySet()) {
            propertyNameCalculator.addForcedGroupPrefix(entry.key, entry.value)
        }
        for (Map.Entry<String, String> entry : getProjectCoordinateProperties().get().entrySet()) {
            CoordinateVersionHolder coordinates = CoordinateVersionHolder.create(entry.key)
            propertyNameCalculator.addProject(coordinates.groupId, coordinates.artifactId, coordinates.version, entry.value)
        }

        Configuration configuration = configurationContainer.named(configurationName.get()).get()
        if (!configuration.canBeResolved) {
            throw new GradleException("The configuration ${configuration.name} must be resolvable to use this task.")
        }

        populateExplicitConstraints(configuration, constraints, propertyNameCalculator)

        Map<CoordinateHolder, List<CoordinateHolder>> exclusions = determineExclusions(configuration)
        populateInheritedConstraints(configuration, exclusions, constraints, propertyNameCalculator)

        List<String> lines = generateAsciiDoc(constraints)
        destination.get().asFile.withWriter { writer ->
            writer.writeLine('[cols="1,1,1,1,1,1", options="header"]')
            writer.writeLine('|===')
            writer.writeLine('| Index | Group | Artifact | Version | Property Name | Source')
            lines.each { line ->
                writer.writeLine(line)
            }
            writer.writeLine('|===')
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

            if (artifactIdMappings.containsKey(constraint.name)) {
                artifactId = artifactIdMappings.get(constraint.name)
            }

            ExtractedDependencyConstraint extractConstraint = propertyNameCalculator.calculate(groupId, artifactId, artifactVersion, false) ?: new ExtractedDependencyConstraint(groupId: groupId, artifactId: artifactId, version: artifactVersion)
            extractConstraint.source = getProjectName().get()

            CoordinateHolder coordinates = new CoordinateHolder(groupId: extractConstraint.groupId, artifactId: extractConstraint.artifactId)
            constraints.put(coordinates, extractConstraint)
        }
    }

    private Map<CoordinateHolder, List<CoordinateHolder>> determineExclusions(Configuration configuration) {
        Map<CoordinateHolder, List<CoordinateHolder>> exclusions = [:].withDefault { [] }
        for (Dependency dep : configuration.allDependencies) {
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
        for (DependencyResult result : configuration.incoming.resolutionResult.allDependencies) {
            if (!(result instanceof ResolvedDependencyResult)) {
                throw new GradleException('Dependencies should be resolved prior to running this task.')
            }

            ResolvedDependencyResult dep = (ResolvedDependencyResult) result

            // Skip project dependencies (e.g. platform(project(':grails-bom'))) since their
            // constraints are already captured through the explicit constraints population
            if (dep.requested instanceof ProjectComponentSelector) {
                continue
            }

            ModuleComponentSelector moduleComponentSelector = dep.requested as ModuleComponentSelector

            // Any non-constraint via api dependency should *always* be a platform dependency, so expand each of those
            CoordinateVersionHolder bomCoordinate = new CoordinateVersionHolder(
                    groupId: moduleComponentSelector.group,
                    artifactId: moduleComponentSelector.module,
                    version: moduleComponentSelector.version
            )

            // fetch the BOM as a pom file so it can be expanded
            ExtractedDependencyConstraint constraint = propertyNameCalculator.calculate(bomCoordinate.groupId, bomCoordinate.artifactId, bomCoordinate.version, true)
            if (!constraint) {
                if (autoRegisterTransitivePlatforms.get()) {
                    // Auto-register the transitive platform so it can be documented and expanded,
                    // but without a version property reference since the version is managed by the
                    // parent platform (e.g. micronaut-platform), not by a Grails property.
                    constraint = new ExtractedDependencyConstraint(bomCoordinate.coordinates)
                } else {
                    throw new GradleException("Failed to find a property name for BOM dependency: ${bomCoordinate.coordinates}. All platform dependencies must have a property name defined meeting the naming requirements.")
                }
            }
            constraint.source = bomCoordinate.artifactId
            constraints.put(bomCoordinate.toCoordinateHolder(), constraint)

            List<CoordinateHolder> exclusionRules = exclusions.get(bomCoordinate.toCoordinateHolder())
            populatePlatformDependencies(bomCoordinate, exclusionRules, constraints)
        }
    }

    Properties populatePlatformDependencies(CoordinateVersionHolder bomCoordinates, List<CoordinateHolder> exclusionRules, Map<CoordinateHolder, ExtractedDependencyConstraint> constraints, boolean error = true, int level = 0) {
        Dependency bomDependency = dependencyHandler.create("${bomCoordinates.coordinates}@pom")
        Configuration dependencyConfiguration = configurationContainer.detachedConfiguration(bomDependency)
        dependencyConfiguration.transitive = false
        File bomPomFile = dependencyConfiguration.singleFile

        Document doc = parsePom(bomPomFile)
        Properties versionProperties = new Properties()

        // Parent POM populated first so its properties can be overridden by the child
        CoordinateVersionHolder parentBom = readParentCoordinates(doc)
        if (parentBom) {
            populatePlatformDependencies(parentBom, exclusionRules, constraints, false, level + 1)?.entrySet()?.each { Map.Entry<Object, Object> entry ->
                versionProperties.put(entry.key, entry.value)
            }
        }

        readProperties(doc).each { String name, String value ->
            versionProperties.put(name, value)
        }
        versionProperties.put('project.groupId', bomCoordinates.groupId)
        versionProperties.put('project.version', bomCoordinates.version)

        List<ManagedDependency> managedDependencies = readManagedDependencies(doc)
        if (managedDependencies.isEmpty()) {
            if (error) {
                // only the boms we directly include need to error since we expect a dependency management;
                // parent boms are sometimes use to share properties so we need to not error on these cases
                throw new GradleException("BOM ${bomCoordinates.coordinates} has no dependencyManagement section.")
            }
            return versionProperties
        }

        for (ManagedDependency depItem : managedDependencies) {
            CoordinateHolder baseCoordinates = new CoordinateHolder(
                    groupId: depItem.groupId,
                    artifactId: depItem.artifactId
            )

            CoordinateHolder resolvedCoordinates = new CoordinateHolder(
                    groupId: resolveMavenProperty(baseCoordinates.coordinatesWithoutVersion, depItem.groupId, versionProperties),
                    artifactId: resolveMavenProperty(baseCoordinates.coordinatesWithoutVersion, depItem.artifactId, versionProperties)
            )

            if (constraints.containsKey(resolvedCoordinates)) {
                continue
            }

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

            if (isExcluded) {
                continue
            }

            String resolvedVersion = resolveMavenProperty(resolvedCoordinates.coordinatesWithoutVersion, depItem.version, versionProperties)
            String propertyName = depItem.version?.contains('$') ? depItem.version : null
            ExtractedDependencyConstraint constraint = new ExtractedDependencyConstraint(
                    groupId: resolvedCoordinates.groupId, artifactId: resolvedCoordinates.artifactId,
                    version: resolvedVersion, versionPropertyReference: propertyName, source: bomCoordinates.artifactId
            )
            constraints.put(resolvedCoordinates, constraint)

            if (depItem.scope == 'import') {
                CoordinateVersionHolder resolvedBomCoordinates = new CoordinateVersionHolder(
                        groupId: resolvedCoordinates.groupId,
                        artifactId: resolvedCoordinates.artifactId,
                        version: resolvedVersion
                )
                populatePlatformDependencies(resolvedBomCoordinates, exclusionRules, constraints, error, level + 1)
            }
        }

        versionProperties
    }

    /**
     * Parses a BOM POM file using the JDK's built-in {@link DocumentBuilderFactory}.
     * XML parsing is hardened against XXE / XInclude attacks.
     */
    private static Document parsePom(File pomFile) {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance()
        factory.setNamespaceAware(false)
        factory.setValidating(false)
        factory.setXIncludeAware(false)
        factory.setFeature('http://apache.org/xml/features/nonvalidating/load-external-dtd', false)
        factory.setFeature('http://xml.org/sax/features/external-general-entities', false)
        factory.setFeature('http://xml.org/sax/features/external-parameter-entities', false)
        return factory.newDocumentBuilder().parse(pomFile)
    }

    private static CoordinateVersionHolder readParentCoordinates(Document doc) {
        NodeList parentNodes = doc.documentElement.getElementsByTagName('parent')
        if (parentNodes.length == 0) {
            return null
        }
        // Only consider <parent> elements that are direct children of <project>;
        // a nested parent (e.g. inside <dependencyManagement>) would be a malformed POM.
        Element parent = null
        for (int i = 0; i < parentNodes.length; i++) {
            Element candidate = (Element) parentNodes.item(i)
            if (candidate.parentNode == doc.documentElement) {
                parent = candidate
                break
            }
        }
        if (parent == null) {
            return null
        }
        String groupId = childText(parent, 'groupId')
        String artifactId = childText(parent, 'artifactId')
        String version = childText(parent, 'version')
        if (!groupId || !artifactId || !version) {
            return null
        }
        return new CoordinateVersionHolder(groupId: groupId, artifactId: artifactId, version: version)
    }

    private static Map<String, String> readProperties(Document doc) {
        Map<String, String> result = [:]
        NodeList propsNodes = doc.documentElement.getElementsByTagName('properties')
        if (propsNodes.length == 0) {
            return result
        }
        // Only consider the top-level <properties>, not properties nested inside other elements
        Element props = null
        for (int i = 0; i < propsNodes.length; i++) {
            Element candidate = (Element) propsNodes.item(i)
            if (candidate.parentNode == doc.documentElement) {
                props = candidate
                break
            }
        }
        if (props == null) {
            return result
        }
        NodeList children = props.childNodes
        for (int i = 0; i < children.length; i++) {
            if (children.item(i) instanceof Element) {
                Element prop = (Element) children.item(i)
                String name = prop.tagName
                String value = prop.textContent?.trim()
                if (name && value != null) {
                    result.put(name, value)
                }
            }
        }
        return result
    }

    private static List<ManagedDependency> readManagedDependencies(Document doc) {
        List<ManagedDependency> result = []
        NodeList depMgmtNodes = doc.documentElement.getElementsByTagName('dependencyManagement')
        if (depMgmtNodes.length == 0) {
            return result
        }
        // Only the top-level <dependencyManagement> on <project>
        Element depMgmt = null
        for (int i = 0; i < depMgmtNodes.length; i++) {
            Element candidate = (Element) depMgmtNodes.item(i)
            if (candidate.parentNode == doc.documentElement) {
                depMgmt = candidate
                break
            }
        }
        if (depMgmt == null) {
            return result
        }
        NodeList depsNodes = depMgmt.getElementsByTagName('dependencies')
        if (depsNodes.length == 0) {
            return result
        }
        Element depsEl = (Element) depsNodes.item(0)
        NodeList depNodes = depsEl.getElementsByTagName('dependency')
        for (int i = 0; i < depNodes.length; i++) {
            Element dep = (Element) depNodes.item(i)
            result.add(new ManagedDependency(
                    groupId: childText(dep, 'groupId'),
                    artifactId: childText(dep, 'artifactId'),
                    version: childText(dep, 'version'),
                    scope: childText(dep, 'scope')
            ))
        }
        return result
    }

    private static String childText(Element parent, String childTagName) {
        NodeList children = parent.getElementsByTagName(childTagName)
        if (children.length == 0) {
            return null
        }
        // Only consider direct children, not nested same-named tags
        for (int i = 0; i < children.length; i++) {
            Element candidate = (Element) children.item(i)
            if (candidate.parentNode == parent) {
                return candidate.textContent?.trim()
            }
        }
        return null
    }

    /**
     * Plain data carrier mirroring the four fields we used from Spring DM's
     * shaded Maven model {@code Dependency}. Kept private and minimal so the
     * task has no external Maven-model dependency.
     */
    private static class ManagedDependency {
        String groupId
        String artifactId
        String version
        String scope
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
            logger.warn('Reached max iterations for {} while resolving properties in: {}', errorDescription, dynamicVersion)
        }

        expandedVersion
    }
}

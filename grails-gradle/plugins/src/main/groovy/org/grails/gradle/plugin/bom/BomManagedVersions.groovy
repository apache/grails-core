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
package org.grails.gradle.plugin.bom

import java.util.function.Function

import groovy.transform.CompileStatic
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.DependencyResolveDetails
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.w3c.dom.Document
import org.w3c.dom.Element

import javax.xml.parsers.DocumentBuilderFactory

/**
 * Lightweight replacement for the Spring Dependency Management plugin's
 * version property override feature.
 *
 * <p>Parses BOM POM files to build a mapping of Maven property names
 * (e.g., {@code slf4j.version}) to the artifacts they control. At
 * dependency resolution time, checks whether the user has overridden
 * any of these properties via {@code ext['property.name']} in
 * {@code build.gradle} or via {@code gradle.properties}, and applies
 * those overrides using Gradle's {@code ResolutionStrategy.eachDependency()}.</p>
 *
 * <p>Gradle's native {@code platform()} mechanism handles the base
 * BOM import and default version management. This class only adds the
 * one feature Gradle lacks: property-based version customization
 * (see <a href="https://github.com/gradle/gradle/issues/9160">Gradle #9160</a>).</p>
 *
 * <p>This is the underlying utility used by the
 * {@code org.apache.grails.gradle.bom-property-overrides} plugin
 * (registered in {@code grails-gradle-plugins}). It is BOM-agnostic and
 * can be used directly with any BOM that follows the Maven
 * {@code <properties>} convention for managed versions.</p>
 *
 * @since 8.0
 */
@CompileStatic
class BomManagedVersions {

    private static final Logger LOG = Logging.getLogger(BomManagedVersions)
    private static final int MAX_PROPERTY_INTERPOLATION_DEPTH = 10

    private final Map<String, String> versionOverrides = new LinkedHashMap<>()

    /**
     * Resolves a single BOM via captured Gradle services rather than a
     * {@link Project} reference. Preferred for config-cache discipline:
     * callers capture services once (typically inside a single
     * {@code afterEvaluate} block) and never leak a {@link Project}
     * reference into the override map that lives on past configuration time.
     *
     * @param configurations the project's configuration container, captured at apply/afterEvaluate time
     * @param dependencies the project's dependency handler, captured at apply/afterEvaluate time
     * @param propertyLookup function returning the project's property value as a String, or {@code null} if unset
     * @param bomCoordinates the BOM coordinates in {@code group:artifact:version} format
     * @return a BomManagedVersions instance containing any version overrides to apply
     */
    static BomManagedVersions resolve(ConfigurationContainer configurations,
                                      DependencyHandler dependencies,
                                      Function<String, String> propertyLookup,
                                      String bomCoordinates) {
        return resolve(configurations, dependencies, propertyLookup, [bomCoordinates])
    }

    /**
     * Resolves multiple BOMs via captured Gradle services. The result is a
     * plain data carrier (a {@code Map<String, String>} of version overrides
     * inside {@link BomManagedVersions}) that holds no {@link Project}
     * reference, so it can be safely captured by per-configuration
     * {@code eachDependency} closures and survive configuration-cache
     * serialization.
     *
     * @param configurations the project's configuration container, captured at apply/afterEvaluate time
     * @param dependencies the project's dependency handler, captured at apply/afterEvaluate time
     * @param propertyLookup function returning the project's property value as a String, or {@code null} if unset
     * @param bomCoordinatesList list of BOM coordinates in {@code group:artifact:version} format
     * @return a BomManagedVersions instance containing any version overrides to apply
     */
    static BomManagedVersions resolve(ConfigurationContainer configurations,
                                      DependencyHandler dependencies,
                                      Function<String, String> propertyLookup,
                                      Collection<String> bomCoordinatesList) {
        def instance = new BomManagedVersions()

        def bomProperties = new LinkedHashMap<String, String>()
        def propertyToArtifacts = new LinkedHashMap<String, List<String>>()
        def processed = new HashSet<String>()

        for (String bomCoordinates : bomCoordinatesList) {
            def parts = bomCoordinates?.split(':')
            if (parts == null || parts.length != 3) {
                LOG.warn('Invalid BOM coordinates: {}', bomCoordinates)
                continue
            }
            processBom(configurations, dependencies, parts[0], parts[1], parts[2],
                    bomProperties, propertyToArtifacts, processed)
        }

        for (def entry : propertyToArtifacts.entrySet()) {
            def propertyName = entry.key
            def overrideVersion = propertyLookup.apply(propertyName)
            if (overrideVersion != null) {
                def defaultVersion = bomProperties.get(propertyName)

                if (overrideVersion != defaultVersion) {
                    for (def artifactKey : entry.value) {
                        instance.versionOverrides.put(artifactKey, overrideVersion)
                    }
                    LOG.lifecycle(
                        'BOM version override: {} = {} (BOM default: {})',
                        propertyName, overrideVersion, defaultVersion ?: 'unknown'
                    )
                }
            }
        }

        if (!instance.versionOverrides.isEmpty()) {
            LOG.info('BOM property overrides: {} version override(s) will be applied', instance.versionOverrides.size())
        }

        return instance
    }

    /**
     * Convenience overload that captures services from the given {@link Project}.
     * Production callers should prefer the services-based overload above so the
     * resolve path never sees a {@link Project} reference. This overload is
     * primarily useful for tests and ad-hoc usage.
     *
     * @param project the Gradle project (services are extracted at call time)
     * @param bomCoordinates the BOM coordinates in {@code group:artifact:version} format
     */
    static BomManagedVersions resolve(Project project, String bomCoordinates) {
        return resolve(project, [bomCoordinates])
    }

    /**
     * Convenience overload that captures services from the given {@link Project}.
     * Production callers should prefer the services-based overload above so the
     * resolve path never sees a {@link Project} reference. This overload is
     * primarily useful for tests and ad-hoc usage.
     *
     * @param project the Gradle project (services are extracted at call time)
     * @param bomCoordinatesList list of BOM coordinates in {@code group:artifact:version} format
     */
    static BomManagedVersions resolve(Project project, Collection<String> bomCoordinatesList) {
        return resolve(
            project.configurations,
            project.dependencies,
            { String name -> project.hasProperty(name) ? project.property(name)?.toString() : null } as Function<String, String>,
            bomCoordinatesList
        )
    }

    /**
     * Applies version overrides to a Gradle configuration's resolution strategy.
     *
     * @param configuration the configuration to apply overrides to
     */
    void applyTo(Configuration configuration) {
        if (versionOverrides.isEmpty()) {
            return
        }

        def overrides = this.versionOverrides
        configuration.resolutionStrategy.eachDependency { DependencyResolveDetails details ->
            def key = "${details.requested.group}:${details.requested.name}" as String
            def override = overrides.get(key)
            if (override != null) {
                details.useVersion(override)
                details.because('BOM version override via project property')
            }
        }
    }

    /**
     * Returns whether any version overrides were detected.
     */
    boolean hasOverrides() {
        return !versionOverrides.isEmpty()
    }

    /**
     * Returns an unmodifiable view of the version overrides.
     * Keys are {@code group:artifact}, values are the override version strings.
     */
    Map<String, String> getOverrides() {
        return Collections.unmodifiableMap(versionOverrides)
    }

    /**
     * Parses a BOM POM file and extracts the property-to-artifact mapping.
     * This method does not follow imported BOMs recursively - it only processes
     * the given file. Intended for testing and direct POM inspection.
     *
     * @param pomFile the BOM POM file to parse
     * @param bomProperties output map to receive property name to default value mappings
     * @param propertyToArtifacts output map to receive property name to artifact coordinate mappings
     */
    static void parseBomFile(File pomFile, Map<String, String> bomProperties, Map<String, List<String>> propertyToArtifacts) {
        def doc = parseXml(pomFile)
        if (doc == null) {
            return
        }
        extractProperties(doc, bomProperties)

        def depMgmtNodes = doc.getElementsByTagName('dependencyManagement')
        if (depMgmtNodes.length == 0) {
            return
        }
        def depMgmt = (Element) depMgmtNodes.item(0)
        def dependenciesNodes = depMgmt.getElementsByTagName('dependencies')
        if (dependenciesNodes.length == 0) {
            return
        }
        def dependenciesElement = (Element) dependenciesNodes.item(0)
        def depNodes = dependenciesElement.getElementsByTagName('dependency')

        for (int i = 0; i < depNodes.length; i++) {
            def dep = (Element) depNodes.item(i)
            def depGroupId = getChildText(dep, 'groupId')
            def depArtifactId = getChildText(dep, 'artifactId')
            def depVersion = getChildText(dep, 'version')

            if (!depGroupId || !depArtifactId || !depVersion) {
                continue
            }

            if (depVersion.contains('${')) {
                def propertyName = extractPropertyName(depVersion)
                if (propertyName) {
                    def artifactKey = "${depGroupId}:${depArtifactId}" as String
                    propertyToArtifacts.computeIfAbsent(propertyName) { new ArrayList<String>() }.add(artifactKey)
                }
            }
        }
    }

    private static void processBom(
        ConfigurationContainer configurations,
        DependencyHandler dependencies,
        String group, String artifact, String version,
        Map<String, String> bomProperties,
        Map<String, List<String>> propertyToArtifacts,
        Set<String> processed
    ) {
        def bomKey = "${group}:${artifact}:${version}" as String
        if (!processed.add(bomKey)) {
            return
        }

        def pomFile = resolvePomFile(configurations, dependencies, group, artifact, version)
        if (pomFile == null) {
            return
        }

        def doc = parseXml(pomFile)
        if (doc == null) {
            return
        }

        extractProperties(doc, bomProperties)
        processManagedDependencies(doc, configurations, dependencies, bomProperties, propertyToArtifacts, processed)
    }

    private static File resolvePomFile(ConfigurationContainer configurations,
                                       DependencyHandler dependencies,
                                       String group, String artifact, String version) {
        try {
            def detached = configurations.detachedConfiguration(
                dependencies.create("${group}:${artifact}:${version}@pom" as String)
            )
            detached.transitive = false
            return detached.singleFile
        }
        catch (Exception e) {
            LOG.info('Could not resolve BOM POM: {}:{}:{} - {}', group, artifact, version, e.message)
            return null
        }
    }

    private static Document parseXml(File pomFile) {
        try {
            def factory = DocumentBuilderFactory.newInstance()
            factory.setNamespaceAware(false)
            factory.setValidating(false)
            factory.setXIncludeAware(false)
            factory.setFeature('http://apache.org/xml/features/nonvalidating/load-external-dtd', false)
            factory.setFeature('http://xml.org/sax/features/external-general-entities', false)
            factory.setFeature('http://xml.org/sax/features/external-parameter-entities', false)
            return factory.newDocumentBuilder().parse(pomFile)
        }
        catch (Exception e) {
            LOG.warn('Failed to parse BOM POM: {} - {}', pomFile.name, e.message)
            return null
        }
    }

    private static void extractProperties(Document doc, Map<String, String> bomProperties) {
        def propertiesNodes = doc.getElementsByTagName('properties')
        if (propertiesNodes.length == 0) {
            return
        }

        def propertiesElement = (Element) propertiesNodes.item(0)
        def children = propertiesElement.childNodes
        for (int i = 0; i < children.length; i++) {
            if (children.item(i) instanceof Element) {
                def prop = (Element) children.item(i)
                def name = prop.tagName
                def value = prop.textContent?.trim()
                if (name && value) {
                    bomProperties.put(name, value)
                }
            }
        }
    }

    private static void processManagedDependencies(
        Document doc,
        ConfigurationContainer configurations,
        DependencyHandler dependencies,
        Map<String, String> bomProperties,
        Map<String, List<String>> propertyToArtifacts,
        Set<String> processed
    ) {
        def depMgmtNodes = doc.getElementsByTagName('dependencyManagement')
        if (depMgmtNodes.length == 0) {
            return
        }

        def depMgmt = (Element) depMgmtNodes.item(0)
        def dependenciesNodes = depMgmt.getElementsByTagName('dependencies')
        if (dependenciesNodes.length == 0) {
            return
        }

        def dependenciesElement = (Element) dependenciesNodes.item(0)
        def depNodes = dependenciesElement.getElementsByTagName('dependency')

        for (int i = 0; i < depNodes.length; i++) {
            def dep = (Element) depNodes.item(i)
            def depGroupId = getChildText(dep, 'groupId')
            def depArtifactId = getChildText(dep, 'artifactId')
            def depVersion = getChildText(dep, 'version')
            def depScope = getChildText(dep, 'scope')

            if (!depGroupId || !depArtifactId) {
                continue
            }

            if ('import' == depScope) {
                def resolvedVersion = interpolateProperties(depVersion, bomProperties)
                if (resolvedVersion) {
                    processBom(configurations, dependencies, depGroupId, depArtifactId, resolvedVersion,
                        bomProperties, propertyToArtifacts, processed)
                }
                continue
            }

            if (depVersion && depVersion.contains('${')) {
                def propertyName = extractPropertyName(depVersion)
                if (propertyName) {
                    def artifactKey = "${depGroupId}:${depArtifactId}" as String
                    propertyToArtifacts.computeIfAbsent(propertyName) { new ArrayList<String>() }.add(artifactKey)
                }
            }
        }
    }

    private static String extractPropertyName(String versionStr) {
        if (versionStr == null) {
            return null
        }
        int start = versionStr.indexOf('${')
        int end = versionStr.indexOf('}', start)
        if (start >= 0 && end > start) {
            return versionStr.substring(start + 2, end)
        }
        return null
    }

    private static String interpolateProperties(String value, Map<String, String> properties) {
        if (value == null || !value.contains('${')) {
            return value
        }

        def result = value
        int maxIterations = MAX_PROPERTY_INTERPOLATION_DEPTH
        while (result.contains('${') && maxIterations-- > 0) {
            def propertyName = extractPropertyName(result)
            if (propertyName == null) {
                break
            }
            def resolved = properties.get(propertyName)
            if (resolved == null) {
                break
            }
            result = result.replace("\${${propertyName}}" as String, resolved)
        }
        return result
    }

    private static String getChildText(Element parent, String childTagName) {
        def children = parent.getElementsByTagName(childTagName)
        if (children.length == 0) {
            return null
        }
        return children.item(0).textContent?.trim()
    }
}

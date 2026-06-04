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
import org.apache.maven.model.Dependency
import org.apache.maven.model.Model
import org.apache.maven.model.Parent
import org.apache.maven.model.io.xpp3.MavenXpp3Reader
import org.gradle.api.Project
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging

/**
 * Lightweight replacement for the Spring Dependency Management plugin's
 * version property override feature.
 *
 * <p>Parses BOM POM files to determine the version every managed artifact
 * resolves to, both with the BOM's default {@code <properties>} values and
 * with the project's overrides applied (via {@code ext['property.name']} in
 * {@code build.gradle} or via {@code gradle.properties}). Any artifact whose
 * effective version differs from the BOM default becomes a version override.</p>
 *
 * <p>Overrides are applied as <strong>strict</strong> dependency constraints
 * (see {@link #applyTo(DependencyHandler, String)}). A strict constraint wins
 * over the {@code require} constraints contributed by Gradle's native
 * {@code platform()} mechanism, so an override is honored even when it
 * <em>downgrades</em> a managed version - a plain
 * {@code ResolutionStrategy.eachDependency()} / {@code useVersion()} hook
 * would lose to the platform's higher version during conflict resolution.</p>
 *
 * <p>Because the effective version is computed by re-resolving imported
 * ({@code <scope>import</scope>}) BOMs with the project's property overrides
 * applied, overriding a property that selects an imported BOM's version
 * (for example {@code spring-boot.version}) re-imports that BOM and pulls in
 * its updated managed-dependency set.</p>
 *
 * <p>POMs are parsed with Maven's own {@code org.apache.maven:maven-model}
 * library ({@link MavenXpp3Reader} into a {@link Model}) so that parent-POM
 * inheritance, {@code <properties>} extraction and {@code <scope>import</scope>}
 * resolution mirror upstream Maven rather than a bespoke XML approximation.
 * Each POM's {@code <properties>} are scoped to that POM (and its parents),
 * matching Maven's model rather than leaking property values across unrelated
 * imported BOMs.</p>
 *
 * <p>Gradle's native {@code platform()} mechanism handles the base BOM import
 * and default version management. This class only adds the one feature Gradle
 * lacks: property-based version customization
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

    /** A property resolver that never overrides anything (BOM defaults only). */
    private static final Function<String, String> NO_OVERRIDES = { String name -> null } as Function<String, String>

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
        resolve(configurations, dependencies, propertyLookup, [bomCoordinates])
    }

    /**
     * Resolves multiple BOMs via captured Gradle services. The result is a
     * plain data carrier (a {@code Map<String, String>} of version overrides
     * inside {@link BomManagedVersions}) that holds no {@link Project}
     * reference, so it can be safely captured by per-configuration
     * constraint declarations and survive configuration-cache serialization.
     *
     * <p>The override set is computed as the difference between two resolutions
     * of the BOM tree: one using the BOM's default property values, and one
     * using the project's property overrides. Any managed artifact whose
     * effective version differs from its default version is recorded as an
     * override.</p>
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

        def defaultVersions = computeManagedVersions(
                configurations, dependencies, bomCoordinatesList, NO_OVERRIDES)
        def effectiveVersions = computeManagedVersions(
                configurations, dependencies, bomCoordinatesList, propertyLookup)

        for (def entry : effectiveVersions.entrySet()) {
            def artifactKey = entry.key
            def effectiveVersion = entry.value
            def defaultVersion = defaultVersions.get(artifactKey)

            if (effectiveVersion != null && effectiveVersion != defaultVersion) {
                instance.versionOverrides.put(artifactKey, effectiveVersion)
                LOG.info(
                    'BOM version override: {} = {} (BOM default: {})',
                    artifactKey, effectiveVersion, defaultVersion ?: 'unknown'
                )
            }
        }

        if (!instance.versionOverrides.isEmpty()) {
            LOG.lifecycle(
                'BOM property overrides: {} version override(s) will be applied',
                instance.versionOverrides.size()
            )
        }

        instance
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
        resolve(project, [bomCoordinates])
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
        resolve(
            project.configurations,
            project.dependencies,
            { String name -> project.hasProperty(name) ? project.property(name)?.toString() : null } as Function<String, String>,
            bomCoordinatesList
        )
    }

    /**
     * Applies the detected version overrides to the given configuration as
     * strict dependency constraints.
     *
     * <p>Strict constraints are used deliberately: a plain {@code platform()}
     * contributes {@code require} constraints, and a soft override (e.g.
     * {@code useVersion()}) would lose to a higher {@code require} version
     * during conflict resolution. A strict constraint overrides {@code require},
     * so the project's chosen version wins in both directions (upgrade and
     * downgrade).</p>
     *
     * @param dependencies the project's dependency handler
     * @param configurationName the name of the configuration to add constraints to
     */
    void applyTo(DependencyHandler dependencies, String configurationName) {
        if (versionOverrides.isEmpty()) {
            return
        }

        versionOverrides.each { String coordinate, String version ->
            dependencies.constraints.add(configurationName, coordinate) {
                it.version { it.strictly(version) }
                it.because('BOM version override via project property')
            }
        }
    }

    /**
     * Returns whether any version overrides were detected.
     */
    boolean hasOverrides() {
        !versionOverrides.isEmpty()
    }

    /**
     * Returns an unmodifiable view of the version overrides.
     * Keys are {@code group:artifact}, values are the override version strings.
     */
    Map<String, String> getOverrides() {
        Collections.unmodifiableMap(versionOverrides)
    }

    /**
     * Parses a BOM POM file and extracts the property-to-artifact mapping.
     * This method does not follow imported BOMs (or parent POMs) recursively -
     * it only processes the given file. Intended for testing and direct POM
     * inspection.
     *
     * @param pomFile the BOM POM file to parse
     * @param bomProperties output map to receive property name to default value mappings
     * @param propertyToArtifacts output map to receive property name to artifact coordinate mappings
     */
    static void parseBomFile(File pomFile, Map<String, String> bomProperties, Map<String, List<String>> propertyToArtifacts) {
        def model = parseModel(pomFile)
        if (model == null) {
            return
        }
        extractProperties(model, bomProperties)

        def managed = managedDependencies(model)
        for (Dependency dep : managed) {
            def depGroupId = dep.groupId
            def depArtifactId = dep.artifactId
            def depVersion = dep.version

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

    /**
     * Walks the BOM tree and returns the effective version of every managed
     * artifact, resolving property references (and imported-BOM versions) with
     * the supplied {@code propertyResolver} taking precedence over each POM's
     * own {@code <properties>} defaults.
     */
    private static Map<String, String> computeManagedVersions(
        ConfigurationContainer configurations,
        DependencyHandler dependencies,
        Collection<String> bomCoordinatesList,
        Function<String, String> propertyResolver
    ) {
        def artifactVersions = new LinkedHashMap<String, String>()
        def processed = new HashSet<String>()

        for (String bomCoordinates : bomCoordinatesList) {
            def parts = bomCoordinates?.split(':')
            if (parts == null || parts.length != 3) {
                LOG.warn('Invalid BOM coordinates: {}', bomCoordinates)
                continue
            }
            processBom(configurations, dependencies, parts[0], parts[1], parts[2],
                    propertyResolver, artifactVersions, processed)
        }

        artifactVersions
    }

    private static void processBom(
        ConfigurationContainer configurations,
        DependencyHandler dependencies,
        String group, String artifact, String version,
        Function<String, String> propertyResolver,
        Map<String, String> artifactVersions,
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

        def model = parseModel(pomFile)
        if (model == null) {
            return
        }

        // Build this BOM's property context, scoped to the BOM and its parent
        // chain (parent properties first so the child can override them), then
        // the Maven built-in project coordinates. Properties are NOT shared
        // across unrelated imported BOMs - each imported BOM resolves its own
        // managed versions against its own property scope, matching Maven.
        def bomProperties = new LinkedHashMap<String, String>()
        populateProperties(configurations, dependencies, model, bomProperties, new HashSet<String>())
        bomProperties.put('project.groupId', group)
        bomProperties.put('project.version', version)

        processManagedDependencies(model, configurations, dependencies, propertyResolver, bomProperties, artifactVersions, processed)
    }

    /**
     * Merges the {@code <properties>} of the given model and its parent POM
     * chain into {@code bomProperties}. Parent properties are added first so a
     * child POM's properties override an inherited value, matching Maven's
     * parent-inheritance semantics.
     */
    private static void populateProperties(
        ConfigurationContainer configurations,
        DependencyHandler dependencies,
        Model model,
        Map<String, String> bomProperties,
        Set<String> processedParents
    ) {
        Parent parent = model.parent
        if (parent != null && parent.groupId && parent.artifactId && parent.version) {
            def parentKey = "${parent.groupId}:${parent.artifactId}:${parent.version}" as String
            if (processedParents.add(parentKey)) {
                def parentPom = resolvePomFile(configurations, dependencies, parent.groupId, parent.artifactId, parent.version)
                if (parentPom != null) {
                    def parentModel = parseModel(parentPom)
                    if (parentModel != null) {
                        populateProperties(configurations, dependencies, parentModel, bomProperties, processedParents)
                    }
                }
            }
        }
        extractProperties(model, bomProperties)
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

    private static Model parseModel(File pomFile) {
        InputStream input = null
        try {
            input = pomFile.newInputStream()
            return new MavenXpp3Reader().read(input)
        }
        catch (Exception e) {
            LOG.warn('Failed to parse BOM POM: {} - {}', pomFile.name, e.message)
            return null
        }
        finally {
            input?.close()
        }
    }

    private static void extractProperties(Model model, Map<String, String> bomProperties) {
        for (Map.Entry<Object, Object> entry : model.properties.entrySet()) {
            def name = entry.key?.toString()
            def value = entry.value?.toString()?.trim()
            if (name && value) {
                bomProperties.put(name, value)
            }
        }
    }

    private static List<Dependency> managedDependencies(Model model) {
        def depMgmt = model.dependencyManagement
        if (depMgmt == null) {
            return Collections.<Dependency> emptyList()
        }
        depMgmt.dependencies ?: Collections.<Dependency> emptyList()
    }

    private static void processManagedDependencies(
        Model model,
        ConfigurationContainer configurations,
        DependencyHandler dependencies,
        Function<String, String> propertyResolver,
        Map<String, String> bomProperties,
        Map<String, String> artifactVersions,
        Set<String> processed
    ) {
        def managed = managedDependencies(model)
        if (managed.isEmpty()) {
            return
        }

        // Record this BOM's own managed versions first and defer imported BOMs, so a
        // BOM's direct entries take precedence over the entries it imports (matching
        // Maven's dependencyManagement resolution, where the importing POM wins).
        // Every entry with a resolvable version is recorded - not just ${property}
        // references - so that switching an imported BOM (e.g. via an overridden
        // spring-boot.version) also picks up that BOM's hardcoded managed versions.
        // The two-pass diff in resolve() discards versions that are identical with and
        // without overrides, so recording literal versions never produces spurious
        // overrides.
        List<Dependency> importedBoms = new ArrayList<>()
        for (Dependency dep : managed) {
            if (!dep.groupId || !dep.artifactId) {
                continue
            }

            if ('import' == dep.scope) {
                importedBoms.add(dep)
                continue
            }

            def resolvedVersion = resolveVersion(dep.version, propertyResolver, bomProperties)
            if (resolvedVersion) {
                def artifactKey = "${dep.groupId}:${dep.artifactId}" as String
                artifactVersions.putIfAbsent(artifactKey, resolvedVersion)
            }
        }

        for (Dependency importedBom : importedBoms) {
            def resolvedVersion = resolveVersion(importedBom.version, propertyResolver, bomProperties)
            if (resolvedVersion) {
                processBom(configurations, dependencies, importedBom.groupId, importedBom.artifactId, resolvedVersion,
                    propertyResolver, artifactVersions, processed)
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

    /**
     * Interpolates {@code ${property}} references in a version string. Each
     * property is resolved using the {@code propertyResolver} first (so project
     * overrides win), falling back to the BOM's own {@code <properties>}. Returns
     * {@code null} when the value cannot be fully resolved.
     */
    private static String resolveVersion(String value, Function<String, String> propertyResolver, Map<String, String> bomProperties) {
        if (value == null) {
            return null
        }
        if (!value.contains('${')) {
            return value
        }

        def result = value
        int maxIterations = MAX_PROPERTY_INTERPOLATION_DEPTH
        while (result.contains('${') && maxIterations-- > 0) {
            def propertyName = extractPropertyName(result)
            if (propertyName == null) {
                break
            }
            def resolved = propertyResolver.apply(propertyName)
            if (resolved == null) {
                resolved = bomProperties.get(propertyName)
            }
            if (resolved == null) {
                break
            }
            result = result.replace("\${${propertyName}}" as String, resolved)
        }
        result.contains('${') ? null : result
    }
}

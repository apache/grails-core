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
package org.apache.grails.forge.core.pack

import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import org.grails.forge.application.ApplicationType

/**
 * The descriptor of a feature pack, {@code META-INF/grails-forge/feature.yml}: what a plugin adds to a
 * generated application, as data. It is made by {@link PackDescriptorParser}, which checks it. Neither
 * the descriptor nor anything reached from it can be changed, and two descriptors read from the same
 * text are equal.
 *
 * @since 9.0
 */
@CompileStatic
@ToString(includeNames = true, includePackage = false)
final class PackDescriptor {

    /** The descriptor format this version of Forge reads */
    public static final int FORMAT = 1

    /** The template API this version of Forge offers to the templates of a pack */
    public static final int TEMPLATE_API = 1

    final String name
    final String title
    final String description
    final String category
    final String documentation
    final String grailsCompatibility
    final String forgeCompatibility
    final Set<ApplicationType> applicationTypes
    final List<String> requires
    final List<String> conflicts
    final String oneOf
    final List<PackDependency> dependencies
    final List<PackDependency> platforms
    final List<PackBuildPlugin> buildPlugins
    final Map<String, String> buildProperties
    final Map<String, String> buildExtensions
    final Map<String, Object> configuration
    final List<PackFile> files
    final String readme
    final String instructions
    /** A link to what the feature brings in that is not of Grails, or {@code null} */
    final String thirdPartyDocumentation

    @SuppressWarnings('ParameterCount')
    PackDescriptor(String name, String title, String description, String category, String documentation,
                   String grailsCompatibility, String forgeCompatibility, Set<ApplicationType> applicationTypes,
                   List<String> requires, List<String> conflicts, String oneOf, List<PackDependency> dependencies,
                   List<PackDependency> platforms, List<PackBuildPlugin> buildPlugins, Map<String, String> buildProperties,
                   Map<String, String> buildExtensions, Map<String, Object> configuration, List<PackFile> files,
                   String readme, String instructions) {
        this(name, title, description, category, documentation, grailsCompatibility, forgeCompatibility, applicationTypes, requires,
                conflicts, oneOf, dependencies, platforms, buildPlugins, buildProperties, buildExtensions, configuration, files, readme,
                instructions, null)
    }

    @SuppressWarnings('ParameterCount')
    PackDescriptor(String name, String title, String description, String category, String documentation,
                   String grailsCompatibility, String forgeCompatibility, Set<ApplicationType> applicationTypes,
                   List<String> requires, List<String> conflicts, String oneOf, List<PackDependency> dependencies,
                   List<PackDependency> platforms, List<PackBuildPlugin> buildPlugins, Map<String, String> buildProperties,
                   Map<String, String> buildExtensions, Map<String, Object> configuration, List<PackFile> files,
                   String readme, String instructions, String thirdPartyDocumentation) {
        this.thirdPartyDocumentation = thirdPartyDocumentation
        this.name = name
        this.title = title
        this.description = description
        this.category = category
        this.documentation = documentation
        this.grailsCompatibility = grailsCompatibility
        this.forgeCompatibility = forgeCompatibility
        this.applicationTypes = Collections.unmodifiableSet(new LinkedHashSet<ApplicationType>(applicationTypes))
        this.requires = List.copyOf(requires)
        this.conflicts = List.copyOf(conflicts)
        this.oneOf = oneOf
        this.dependencies = List.copyOf(dependencies)
        this.platforms = List.copyOf(platforms)
        this.buildPlugins = List.copyOf(buildPlugins)
        this.buildProperties = Collections.unmodifiableMap(new LinkedHashMap<String, String>(buildProperties))
        this.buildExtensions = Collections.unmodifiableMap(new LinkedHashMap<String, String>(buildExtensions))
        this.configuration = (Map<String, Object>) frozen(configuration)
        this.files = List.copyOf(files)
        this.readme = readme
        this.instructions = instructions
    }

    private List<Object> state() {
        return [name, title, description, category, documentation, grailsCompatibility, forgeCompatibility, applicationTypes,
                requires, conflicts, oneOf, dependencies, platforms, buildPlugins, buildProperties, buildExtensions,
                configuration, files, readme, instructions, thirdPartyDocumentation] as List<Object>
    }

    /**
     * Two descriptors are equal when they say the same, value for value: in the configuration the whole
     * number {@code 1} and the fraction {@code 1.0} are different values, as they are in the YAML.
     */
    @Override
    boolean equals(Object other) {
        // compared by Java, whose equals tells 1 from 1.0; Groovy's own comparison of lists does not
        return other instanceof PackDescriptor && Objects.equals(state(), ((PackDescriptor) other).state())
    }

    @Override
    int hashCode() {
        return Objects.hashCode(state())
    }

    /**
     * A copy of a tree of maps, lists and single values in which no map or list can be changed. The
     * parser only lets immutable single values into the tree.
     */
    private static Object frozen(Object value) {
        if (value instanceof Map) {
            Map<String, Object> copy = new LinkedHashMap<>()
            ((Map<String, Object>) value).each { String key, Object each -> copy.put(key, frozen(each)) }
            return Collections.unmodifiableMap(copy)
        }
        if (value instanceof List) {
            return Collections.unmodifiableList(((List<Object>) value).collect { Object each -> frozen(each) })
        }
        return value
    }

    /**
     * A dependency or a platform the pack adds to the build.
     */
    @CompileStatic
    @EqualsAndHashCode
    @ToString(includeNames = true, includePackage = false)
    static final class PackDependency {

        /**
         * The Gradle configuration of the dependency, a key of {@link PackDescriptorParser#SCOPES}, or
         * {@code null} for a platform
         */
        final String scope
        final String groupId
        final String artifactId
        final String version
        /** Each {@code group} or {@code group:artifact} */
        final List<String> excludes
        /**
         * Whether the group and version are those Forge knows for the artifact. Only a pack Forge has
         * in itself can say so: a feature of the framework names its versions in one place.
         */
        final boolean versionOfForge

        PackDependency(String scope, String groupId, String artifactId, String version, List<String> excludes) {
            this(scope, groupId, artifactId, version, excludes, false)
        }

        PackDependency(String scope, String groupId, String artifactId, String version, List<String> excludes, boolean versionOfForge) {
            this.versionOfForge = versionOfForge
            this.scope = scope
            this.groupId = groupId
            this.artifactId = artifactId
            this.version = version
            this.excludes = List.copyOf(excludes)
        }
    }

    /**
     * A Gradle plugin the pack applies to the build.
     */
    @CompileStatic
    @EqualsAndHashCode
    @ToString(includeNames = true, includePackage = false)
    static final class PackBuildPlugin {

        final String id
        final String version

        PackBuildPlugin(String id, String version) {
            this.id = id
            this.version = version
        }
    }

    /**
     * A file the pack adds to the application: a template or a file copied as it is.
     */
    @CompileStatic
    @EqualsAndHashCode
    @ToString(includeNames = true, includePackage = false)
    static final class PackFile {

        /** The path of the file in the templates of the pack, with {@code /} between its steps */
        final String from
        /** The path of the file in the application, with {@code /} between its steps */
        final String to

        PackFile(String from, String to) {
            this.from = from
            this.to = to
        }
    }
}

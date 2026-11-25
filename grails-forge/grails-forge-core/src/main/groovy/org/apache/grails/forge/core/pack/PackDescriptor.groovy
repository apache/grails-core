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

    /** The latest template API this version of Forge offers to the templates of a pack, see {@link PackModel#APIS} */
    public static final int TEMPLATE_API = 1

    /** The template API the templates of the pack are written against, one of {@link PackModel#APIS} */
    final int templateApi
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
    /**
     * For a pack Forge has in itself: the artifacts whose versions its templates are given, each with the
     * version to use when Forge knows none for it
     */
    final Map<String, String> versionsOfForge
    /**
     * For a pack Forge has in itself: the text it adds to the build file as it is, by the place it is
     * added at, a key of {@link PackDescriptorParser#ANCHORS}
     */
    final Map<String, String> buildBlocks
    /** For a pack Forge has in itself: the links it adds to the readme, each a line of its own */
    final List<PackLink> readmeLinks
    /** For a pack Forge has in itself: how the feature is selected and what it gives beside other features; otherwise {@link PackOfForge#NONE} */
    final PackOfForge ofForge
    /**
     * For a preset, a pack of the kind {@code preset}: its type of application and its options; otherwise
     * {@code null}, which is how a feature pack is told from a preset. The features a preset asks for are
     * its {@link #requires}. A preset is given to a create command with {@code --preset} and is not
     * selected as a feature.
     */
    final PackPreset preset
    /** The options the feature takes, which whoever creates the application gives and the templates and conditions read */
    final List<PackParameter> parameters

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
        this(name, title, description, category, documentation, grailsCompatibility, forgeCompatibility, applicationTypes, requires,
                conflicts, oneOf, dependencies, platforms, buildPlugins, buildProperties, buildExtensions, configuration, files, readme,
                instructions, thirdPartyDocumentation, [:], [:], [])
    }

    @SuppressWarnings('ParameterCount')
    PackDescriptor(String name, String title, String description, String category, String documentation,
                   String grailsCompatibility, String forgeCompatibility, Set<ApplicationType> applicationTypes,
                   List<String> requires, List<String> conflicts, String oneOf, List<PackDependency> dependencies,
                   List<PackDependency> platforms, List<PackBuildPlugin> buildPlugins, Map<String, String> buildProperties,
                   Map<String, String> buildExtensions, Map<String, Object> configuration, List<PackFile> files,
                   String readme, String instructions, String thirdPartyDocumentation, Map<String, String> versionsOfForge,
                   Map<String, String> buildBlocks, List<PackLink> readmeLinks) {
        this(name, title, description, category, documentation, grailsCompatibility, forgeCompatibility, applicationTypes, requires,
                conflicts, oneOf, dependencies, platforms, buildPlugins, buildProperties, buildExtensions, configuration, files, readme,
                instructions, thirdPartyDocumentation, versionsOfForge, buildBlocks, readmeLinks, TEMPLATE_API)
    }

    @SuppressWarnings('ParameterCount')
    PackDescriptor(String name, String title, String description, String category, String documentation,
                   String grailsCompatibility, String forgeCompatibility, Set<ApplicationType> applicationTypes,
                   List<String> requires, List<String> conflicts, String oneOf, List<PackDependency> dependencies,
                   List<PackDependency> platforms, List<PackBuildPlugin> buildPlugins, Map<String, String> buildProperties,
                   Map<String, String> buildExtensions, Map<String, Object> configuration, List<PackFile> files,
                   String readme, String instructions, String thirdPartyDocumentation, Map<String, String> versionsOfForge,
                   Map<String, String> buildBlocks, List<PackLink> readmeLinks, int templateApi) {
        this(name, title, description, category, documentation, grailsCompatibility, forgeCompatibility, applicationTypes, requires,
                conflicts, oneOf, dependencies, platforms, buildPlugins, buildProperties, buildExtensions, configuration, files, readme,
                instructions, thirdPartyDocumentation, versionsOfForge, buildBlocks, readmeLinks, templateApi, PackOfForge.NONE)
    }

    @SuppressWarnings('ParameterCount')
    PackDescriptor(String name, String title, String description, String category, String documentation,
                   String grailsCompatibility, String forgeCompatibility, Set<ApplicationType> applicationTypes,
                   List<String> requires, List<String> conflicts, String oneOf, List<PackDependency> dependencies,
                   List<PackDependency> platforms, List<PackBuildPlugin> buildPlugins, Map<String, String> buildProperties,
                   Map<String, String> buildExtensions, Map<String, Object> configuration, List<PackFile> files,
                   String readme, String instructions, String thirdPartyDocumentation, Map<String, String> versionsOfForge,
                   Map<String, String> buildBlocks, List<PackLink> readmeLinks, int templateApi, PackOfForge ofForge) {
        this(name, title, description, category, documentation, grailsCompatibility, forgeCompatibility, applicationTypes, requires,
                conflicts, oneOf, dependencies, platforms, buildPlugins, buildProperties, buildExtensions, configuration, files, readme,
                instructions, thirdPartyDocumentation, versionsOfForge, buildBlocks, readmeLinks, templateApi, ofForge, null)
    }

    @SuppressWarnings('ParameterCount')
    PackDescriptor(String name, String title, String description, String category, String documentation,
                   String grailsCompatibility, String forgeCompatibility, Set<ApplicationType> applicationTypes,
                   List<String> requires, List<String> conflicts, String oneOf, List<PackDependency> dependencies,
                   List<PackDependency> platforms, List<PackBuildPlugin> buildPlugins, Map<String, String> buildProperties,
                   Map<String, String> buildExtensions, Map<String, Object> configuration, List<PackFile> files,
                   String readme, String instructions, String thirdPartyDocumentation, Map<String, String> versionsOfForge,
                   Map<String, String> buildBlocks, List<PackLink> readmeLinks, int templateApi, PackOfForge ofForge, PackPreset preset) {
        this(name, title, description, category, documentation, grailsCompatibility, forgeCompatibility, applicationTypes, requires,
                conflicts, oneOf, dependencies, platforms, buildPlugins, buildProperties, buildExtensions, configuration, files, readme,
                instructions, thirdPartyDocumentation, versionsOfForge, buildBlocks, readmeLinks, templateApi, ofForge, preset, [])
    }

    @SuppressWarnings('ParameterCount')
    PackDescriptor(String name, String title, String description, String category, String documentation,
                   String grailsCompatibility, String forgeCompatibility, Set<ApplicationType> applicationTypes,
                   List<String> requires, List<String> conflicts, String oneOf, List<PackDependency> dependencies,
                   List<PackDependency> platforms, List<PackBuildPlugin> buildPlugins, Map<String, String> buildProperties,
                   Map<String, String> buildExtensions, Map<String, Object> configuration, List<PackFile> files,
                   String readme, String instructions, String thirdPartyDocumentation, Map<String, String> versionsOfForge,
                   Map<String, String> buildBlocks, List<PackLink> readmeLinks, int templateApi, PackOfForge ofForge, PackPreset preset,
                   List<PackParameter> parameters) {
        this.parameters = List.copyOf(parameters)
        this.preset = preset
        this.ofForge = ofForge
        this.templateApi = templateApi
        this.versionsOfForge = Collections.unmodifiableMap(new LinkedHashMap<String, String>(versionsOfForge))
        this.buildBlocks = Collections.unmodifiableMap(new LinkedHashMap<String, String>(buildBlocks))
        this.readmeLinks = List.copyOf(readmeLinks)
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
                configuration, files, readme, instructions, thirdPartyDocumentation, versionsOfForge, buildBlocks, readmeLinks,
                templateApi, ofForge, preset, parameters] as List<Object>
    }

    /**
     * @return the {@code kind} of the descriptor: {@code feature} or {@code preset}
     */
    String getKind() {
        return preset != null ? PackPreset.KIND : 'feature'
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
    static Object frozen(Object value) {
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
        /** Each {@code group:name}: the capabilities that select the variant of the dependency, none for its default variant */
        final List<String> capabilities
        /**
         * Whether the group and version are those Forge knows for the artifact. Only a pack Forge has
         * in itself can say so: a feature of the framework names its versions in one place.
         */
        final boolean versionOfForge
        /** For a pack Forge has in itself: what has to hold of the application for the dependency to be there */
        final PackCondition when

        PackDependency(String scope, String groupId, String artifactId, String version, List<String> excludes) {
            this(scope, groupId, artifactId, version, excludes, false)
        }

        PackDependency(String scope, String groupId, String artifactId, String version, List<String> excludes, boolean versionOfForge) {
            this(scope, groupId, artifactId, version, excludes, versionOfForge, PackCondition.ALWAYS)
        }

        PackDependency(String scope, String groupId, String artifactId, String version, List<String> excludes, boolean versionOfForge,
                       PackCondition when) {
            this(scope, groupId, artifactId, version, excludes, versionOfForge, when, [])
        }

        @SuppressWarnings('ParameterCount')
        PackDependency(String scope, String groupId, String artifactId, String version, List<String> excludes, boolean versionOfForge,
                       PackCondition when, List<String> capabilities) {
            this.when = when
            this.versionOfForge = versionOfForge
            this.scope = scope
            this.groupId = groupId
            this.artifactId = artifactId
            this.version = version
            this.excludes = List.copyOf(excludes)
            this.capabilities = List.copyOf(capabilities)
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
        /**
         * The artifact of the plugin, when its version is the one Forge knows for that artifact, or
         * {@code null}. Only a pack Forge has in itself can name one.
         */
        final String artifactOfForge
        /** For a pack Forge has in itself: whether the plugin is applied with {@code apply plugin:}, as the plugins of Grails are */
        final boolean apply
        /** For a pack Forge has in itself: what has to hold of the application for the plugin to be applied */
        final PackCondition when

        PackBuildPlugin(String id, String version) {
            this(id, version, null)
        }

        PackBuildPlugin(String id, String version, String artifactOfForge) {
            this(id, version, artifactOfForge, false, PackCondition.ALWAYS)
        }

        PackBuildPlugin(String id, String version, String artifactOfForge, boolean apply, PackCondition when) {
            this.apply = apply
            this.when = when
            this.id = id
            this.version = version
            this.artifactOfForge = artifactOfForge
        }
    }

    /**
     * A link a pack Forge has in itself adds to the readme of the application.
     */
    @CompileStatic
    @EqualsAndHashCode
    @ToString(includeNames = true, includePackage = false)
    static final class PackLink {

        final String label
        final String href

        PackLink(String label, String href) {
            this.label = label
            this.href = href
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
        /** Whether a file whose name ends with {@code .gsp} is a template; a pack Forge has in itself can say it is not */
        final boolean asTemplate
        /** For a pack Forge has in itself: whether the file is one that is run */
        final boolean executable
        /** For a pack Forge has in itself: what has to hold of the application for the file to be written */
        final PackCondition when

        PackFile(String from, String to) {
            this(from, to, true, false, PackCondition.ALWAYS)
        }

        PackFile(String from, String to, boolean template, boolean executable, PackCondition when) {
            this.from = from
            this.to = to
            this.asTemplate = template
            this.executable = executable
            this.when = when
        }

        /**
         * @return whether the file is rendered, and not written as it is
         */
        boolean isTemplate() {
            return asTemplate && Pack.isTemplate(from)
        }
    }
}

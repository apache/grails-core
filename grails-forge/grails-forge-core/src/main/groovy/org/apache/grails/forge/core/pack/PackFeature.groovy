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
import java.nio.charset.StandardCharsets
import java.util.function.Function
import java.util.function.Supplier
import org.apache.grails.forge.core.pack.PackDescriptor.PackBuildPlugin
import org.apache.grails.forge.core.pack.PackDescriptor.PackDependency
import org.apache.grails.forge.core.pack.PackDescriptor.PackFile
import org.apache.grails.forge.core.pack.PackDescriptor.PackLink
import org.grails.forge.application.ApplicationType
import org.grails.forge.application.generator.GeneratorContext
import org.grails.forge.build.dependencies.Dependency
import org.grails.forge.build.gradle.GradlePlugin
import org.grails.forge.feature.Feature
import org.grails.forge.feature.FeatureContext
import org.grails.forge.feature.OneOfFeature
import org.grails.forge.feature.config.Configuration
import org.grails.forge.template.DefaultTemplateRenderer
import org.grails.forge.template.Writable

/**
 * The feature a pack describes. It does what a feature written as a class does, from the data of the
 * descriptor: it brings in the features it requires, and adds its dependencies, build plugins, build
 * properties and extensions, configuration, files and readme to the application. A file whose name
 * in the pack ends with {@code .gsp} is a template, rendered with {@link PackModel}; any other file is
 * written as it is.
 *
 * <p>A pack adds to an application and does not change what another feature gave it. Where a pack and
 * another feature set the same build property, plugin, dependency or setting differently, generation
 * is refused, whichever of the two is applied first: what the pack added is looked at again once every
 * feature is applied.</p>
 *
 * @since 9.0
 */
@CompileStatic
class PackFeature implements Feature {

    private static final String TASKS = 'tasks.'
    private static final String ENVIRONMENTS = 'environments.'

    private final Pack pack
    private final Function<String, Optional<Feature>> features
    private final Supplier<PackPages> pages

    /**
     * @param pack the pack
     * @param features finds a feature the application can have by its name
     * @param pages renders the templates of the pack
     */
    protected PackFeature(Pack pack, Function<String, Optional<Feature>> features, Supplier<PackPages> pages) {
        this.pack = pack
        this.features = features
        this.pages = pages
    }

    /**
     * @param pack a pack
     * @param features finds a feature the application can have by its name
     * @param pages renders the templates of the pack
     * @return the feature of the pack, which belongs to an exclusive group when the descriptor names one
     */
    static PackFeature of(Pack pack, Function<String, Optional<Feature>> features, Supplier<PackPages> pages) {
        return pack.descriptor.oneOf == null ? new PackFeature(pack, features, pages) : new Exclusive(pack, features, pages)
    }

    Pack getPack() {
        return pack
    }

    @Override
    String getName() {
        return pack.descriptor.name
    }

    @Override
    String getTitle() {
        return pack.descriptor.title
    }

    @Override
    String getDescription() {
        return pack.descriptor.description
    }

    @Override
    String getCategory() {
        return pack.descriptor.category
    }

    @Override
    String getDocumentation() {
        return pack.descriptor.documentation
    }

    @Override
    String getThirdPartyDocumentation() {
        return pack.descriptor.thirdPartyDocumentation
    }

    @Override
    boolean supports(ApplicationType applicationType) {
        return pack.descriptor.applicationTypes.contains(applicationType)
    }

    /**
     * @return the features the descriptor requires, which are selected with this one before the features
     * nothing has chosen are given their defaults
     */
    @Override
    List<String> getDependentFeatures() {
        return pack.descriptor.requires
    }

    @Override
    void processSelectedFeatures(FeatureContext featureContext) {
        // the requirements are selected when the features are; this is for a context made another way
        for (String required : pack.descriptor.requires) {
            if (!featureContext.isPresent(required)) {
                Feature feature = features.apply(required)
                        .filter { Feature found -> found.supports(featureContext.getApplicationType()) }
                        .orElseThrow {
                            new IllegalArgumentException("The feature ${name} requires the feature ${required}, which there is not for this type of application")
                        }
                featureContext.addFeature(feature)
            }
        }
    }

    @Override
    void apply(GeneratorContext generatorContext) {
        PackDescriptor descriptor = pack.descriptor
        for (String conflict : descriptor.conflicts) {
            if (generatorContext.getFeatures().contains(conflict)) {
                throw new IllegalArgumentException("The feature ${name} cannot be used with the feature ${conflict}")
            }
        }
        List<PackDependency> declared = descriptor.dependencies + descriptor.platforms
        declared.each { PackDependency dependency -> checkVersion(generatorContext, dependency) }
        for (PackDependency dependency : descriptor.dependencies) {
            generatorContext.addDependency(toDependency(generatorContext, dependency, false))
        }
        for (PackDependency platform : descriptor.platforms) {
            generatorContext.addDependency(toDependency(generatorContext, platform, true))
        }
        for (PackBuildPlugin plugin : descriptor.buildPlugins) {
            // with an artifact, as a compiled feature of the framework looks the version of its plugin up
            generatorContext.addBuildPlugin(plugin.artifactOfForge == null ?
                    GradlePlugin.builder().id(plugin.id).version(plugin.version).build() :
                    GradlePlugin.builder().id(plugin.id).lookupArtifactId(plugin.artifactOfForge).build())
        }
        descriptor.buildProperties.each { String property, String value ->
            checkProperty(generatorContext, property, value)
            generatorContext.getBuildProperties().put(property, value)
        }
        descriptor.buildExtensions.each { String extension, String body ->
            // tasks.bootRun configures the task bootRun; a longer name, such as tasks.withType, is a block of its own
            if (extension.startsWith(TASKS) && extension.indexOf('.', TASKS.length()) < 0) {
                generatorContext.getBuildContributions().task(extension.substring(TASKS.length()), body)
            } else {
                generatorContext.getBuildContributions().extension(extension, body)
            }
        }
        descriptor.buildBlocks.each { String anchor, String text ->
            generatorContext.getBuildContributions().block(PackDescriptorParser.ANCHORS.get(anchor), text)
        }
        for (PackLink link : descriptor.readmeLinks) {
            generatorContext.addHelpLink(link.label, link.href)
        }
        Map<String, Object> settings = new LinkedHashMap<>()
        flatten(descriptor.configuration, '', settings)
        settings.each { String setting, Object value ->
            checkSetting(generatorContext, setting, value)
            generatorContext.getConfiguration().put(setting, value)
        }
        // every destination before the first template: a pack that cannot write one of its files runs none
        for (PackFile file : descriptor.files) {
            checkDestination(generatorContext, file)
        }
        long written = 0
        for (PackFile file : descriptor.files) {
            written += addFile(generatorContext, file)
            if (written > PackLoader.MAX_PACK_BYTES) {
                throw new IllegalArgumentException("The feature ${name} writes more than the ${PackLoader.MAX_PACK_BYTES} bytes " +
                        'the files of a feature pack can have together')
            }
        }
        if (descriptor.readme != null) {
            // a paragraph of its own, whatever it ends with
            String readme = descriptor.readme.stripTrailing() + '\n\n'
            generatorContext.addHelpTemplate({ OutputStream out -> out.write(readme.getBytes(StandardCharsets.UTF_8)) } as Writable)
        }
        // a feature applied after this one could have replaced what it added, without a word
        generatorContext.afterFeaturesApplied {
            declared.each { PackDependency dependency -> checkVersion(generatorContext, dependency) }
            descriptor.buildProperties.each { String property, String value -> checkProperty(generatorContext, property, value) }
            settings.each { String setting, Object value -> checkSetting(generatorContext, setting, value) }
        }
    }

    private Dependency.Builder toDependency(GeneratorContext generatorContext, PackDependency declared, boolean platform) {
        Dependency.Builder dependency = Dependency.builder()
        if (declared.versionOfForge) {
            // as a compiled feature of the framework looks its version up
            dependency.groupId(declared.groupId).lookupArtifactId(declared.artifactId)
        } else {
            dependency.groupId(declared.groupId).artifactId(declared.artifactId).version(declared.version)
        }
        dependency.scope(PackDescriptorParser.SCOPES.get(platform ? 'implementation' : declared.scope))
                .pom(platform)
        for (String exclusion : declared.excludes) {
            int colon = exclusion.indexOf(':')
            dependency.exclude(colon < 0 ? exclusion : exclusion.substring(0, colon), colon < 0 ? null : exclusion.substring(colon + 1))
        }
        return dependency
    }

    /**
     * A dependency the pack gives a version must not be in the application in another version, or
     * without one, in whatever scope: a pack does not pin what the application leaves to its platform.
     */
    private void checkVersion(GeneratorContext generatorContext, PackDependency declared) {
        if (declared.version == null) {
            return
        }
        Dependency other = generatorContext.getDependencies().find { Dependency each ->
            each.getGroupId() == declared.groupId && each.getArtifactId() == declared.artifactId && each.getVersion() != declared.version
        }
        if (other != null) {
            throw new IllegalArgumentException("The feature ${name} declares ${declared.groupId}:${declared.artifactId} in version " +
                    "${declared.version}, and it is also declared ${other.getVersion() == null ? 'without a version' : 'in version ' + other.getVersion()}")
        }
    }

    private void checkProperty(GeneratorContext generatorContext, String property, String value) {
        String other = generatorContext.getBuildProperties().get(property)
        if (other != null && other != value) {
            throw new IllegalArgumentException("The feature ${name} sets the build property ${property} to '${value}', " +
                    "and another feature sets it to '${other}'")
        }
    }

    /**
     * The settings of a mapping by their whole names, as features set the configuration. A list is the
     * value of its setting. What the descriptor has under {@code environments} keeps that name in front
     * of it, which is how the configuration has the settings of one environment.
     */
    private static void flatten(Map<String, Object> from, String prefix, Map<String, Object> into) {
        from.each { String key, Object value ->
            if (value instanceof Map) {
                // a mapping without settings sets nothing
                flatten((Map<String, Object>) value, prefix + key + '.', into)
            } else {
                into.put(prefix + key, value)
            }
        }
    }

    /**
     * A setting is not set to two values, and is not a value for one feature and a group of settings
     * for another, such as {@code a.b} and {@code a.b.c}. The setting of one environment is compared
     * with what the configuration has for that environment, in whichever of its two places.
     */
    private void checkSetting(GeneratorContext generatorContext, String setting, Object value) {
        checkSetting(generatorContext.getConfiguration(), setting, setting, value)
        if (setting.startsWith(ENVIRONMENTS)) {
            int end = setting.indexOf('.', ENVIRONMENTS.length())
            Configuration ofEnvironment = end < 0 ? null : generatorContext.getConfiguration(setting.substring(ENVIRONMENTS.length(), end))
            if (ofEnvironment != null) {
                checkSetting(ofEnvironment, setting, setting.substring(end + 1), value)
            }
        }
    }

    private void checkSetting(Configuration configuration, String setting, String inConfiguration, Object value) {
        Map<String, Object> others = new LinkedHashMap<>()
        flatten(configuration, '', others)
        others.each { String other, Object otherValue ->
            if (other == inConfiguration && !Objects.equals(otherValue, value)) {
                throw new IllegalArgumentException("The feature ${name} sets ${setting}, which another feature sets to another value")
            }
            if (other.startsWith(inConfiguration + '.') || inConfiguration.startsWith(other + '.')) {
                throw new IllegalArgumentException("The feature ${name} sets ${setting}, and another feature sets a setting of the same name " +
                        'but one of the two is a value where the other is a group of settings')
            }
        }
    }

    /**
     * The destination of a file was checked when the descriptor was read, with its placeholders in it.
     * Here they have their values, which come from the name the application was given, so the path that
     * is written is checked again.
     */
    private void checkDestination(GeneratorContext generatorContext, PackFile file) {
        String written = DefaultTemplateRenderer.resolvePath(file.to, generatorContext.getProject().getProperties())
        if (!PackDescriptorParser.isPathBelowRoot(written)) {
            throw new IllegalArgumentException("The feature ${name} would write the file ${file.to} to ${written}, which is not a " +
                    'path a feature pack can write for this application')
        }
    }

    /**
     * @return the bytes the file has in the application
     */
    private int addFile(GeneratorContext generatorContext, PackFile file) {
        byte[] content = pack.content(file.from)
        if (Pack.isTemplate(file.from)) {
            // rendered here and not when the file is written: every feature is chosen by now, and a
            // template that fails does so before anything of the application is written
            String from = file.from.length() > 120 ? file.from.substring(0, 120) + '...' : file.from
            content = pages.get().render(new String(content, StandardCharsets.UTF_8), "${from} of the feature ${name}".toString(),
                    PackModel.of(generatorContext, pack.descriptor), PackLoader.MAX_FILE_BYTES)
        }
        generatorContext.addTemplate("pack:${name}:${file.to}".toString(), new PackContent(file.to, content))
        return content.length
    }

    /**
     * The feature of a pack whose descriptor names an exclusive group.
     */
    static class Exclusive extends PackFeature implements OneOfFeature {

        protected Exclusive(Pack pack, Function<String, Optional<Feature>> features, Supplier<PackPages> pages) {
            super(pack, features, pages)
        }

        @Override
        String getGroup() {
            return getPack().descriptor.oneOf
        }

        /**
         * @return {@code null}: the features of a group a pack names share no type
         */
        @Override
        Class<?> getFeatureClass() {
            return null
        }
    }
}

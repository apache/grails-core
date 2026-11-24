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
import org.grails.forge.feature.DefaultFeature
import org.grails.forge.feature.Feature
import org.grails.forge.feature.FeatureContext
import org.grails.forge.feature.OneOfFeature
import org.grails.forge.feature.config.ApplicationConfiguration
import org.grails.forge.feature.config.Configuration
import org.grails.forge.options.Options
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
 * <p>The feature of a pack Forge has in itself also takes part in the selection as the descriptor says
 * ({@link PackOfForge}): it can be a default, be hidden, have a place among the features, add other
 * features, and give some of what it gives only beside other features. It is a feature of the
 * framework like one written as a class, and like that one it is not held to what the other features
 * set: the pack that is given to a generation is, and is the one a refusal names.</p>
 *
 * @since 9.0
 */
@CompileStatic
class PackFeature implements Feature {

    /** The exclusive group of the database drivers, whose settings a Grails Data implementation can take */
    public static final String DATABASE_DRIVERS = 'database-driver'

    /** What is between the coordinates of a plugin and the name of a feature in a full name, {@code group:artifact#name} */
    public static final String FULL_NAME_SEPARATOR = '#'

    private static final String TASKS = 'tasks.'
    private static final String ENVIRONMENTS = 'environments.'
    private static final String TEST_ENVIRONMENT = 'test'

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
     * and is a default when the descriptor says when
     */
    static PackFeature of(Pack pack, Function<String, Optional<Feature>> features, Supplier<PackPages> pages) {
        boolean byDefault = pack.descriptor.ofForge.byDefault != null
        if (pack.descriptor.oneOf == null) {
            return byDefault ? new ByDefault(pack, features, pages) : new PackFeature(pack, features, pages)
        }
        return byDefault ? new ExclusiveByDefault(pack, features, pages) : new Exclusive(pack, features, pages)
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
    int getOrder() {
        return pack.descriptor.ofForge.order
    }

    /**
     * @return whether the feature is selected by its name and listed: a preset is neither, it is given
     * to a create command with {@code --preset}
     */
    @Override
    boolean isVisible() {
        return pack.descriptor.ofForge.visible && !pack.descriptor.preset
    }

    @Override
    boolean supports(ApplicationType applicationType) {
        return pack.descriptor.applicationTypes.contains(applicationType)
    }

    /**
     * @return the features the descriptor requires, which are selected with this one before the features
     * nothing has chosen are given their defaults. A full name is the name of the feature it names; one
     * that names no feature that was given stays as it is, and is refused when it is looked up
     */
    @Override
    List<String> getDependentFeatures() {
        return pack.descriptor.requires.collect { String required ->
            pluginOf(required) == null ? required : features.apply(required).map { Feature found -> found.getName() }.orElse(required)
        }
    }

    /**
     * @param id a feature as the {@code requires}, {@code features} and {@code conflicts} of a descriptor
     * name it: by its name, or by its full name, {@code group:artifact#name}
     * @return the {@code group:artifact} of the plugin of a full name, or {@code null} for a name
     */
    static String pluginOf(String id) {
        int at = id.indexOf(FULL_NAME_SEPARATOR)
        return at < 0 ? null : id.substring(0, at)
    }

    /**
     * @param id a feature by its name or by its full name, see {@link #pluginOf}
     * @return the name of the feature
     */
    static String nameOf(String id) {
        return id.substring(id.indexOf(FULL_NAME_SEPARATOR) + 1)
    }

    /**
     * @param id a feature by its name or by its full name, see {@link #pluginOf}
     * @param feature a feature of the application
     * @return whether the feature is the one that is named: a name is the feature of that name, and a full
     * name is the feature of that name of the pack that was taken from the jar of that plugin, whichever
     * version of it that was. The pack of a directory or of a file is of no plugin
     */
    static boolean isNamed(String id, Feature feature) {
        String plugin = pluginOf(id)
        if (plugin == null) {
            return feature.getName() == id
        }
        if (!(feature instanceof PackFeature) || feature.getName() != nameOf(id)) {
            return false
        }
        PackArtifact artifact = ((PackFeature) feature).getPack().artifact
        return artifact != null && artifact.coordinates.substring(0, Math.max(artifact.coordinates.lastIndexOf(':'), 0)) == plugin
    }

    /**
     * @param feature the name of a feature
     * @param required a feature it requires, by its name or by its full name, see {@link #pluginOf}, that is not there
     * @return what to say of it
     */
    static String requiredNotThere(String feature, String required) {
        String plugin = pluginOf(required)
        if (plugin == null) {
            return "The feature ${feature} requires the feature ${required}, which there is not for this type of application"
        }
        return "The feature ${feature} requires the feature ${nameOf(required)} of the plugin ${plugin} (${required}), and no feature pack of that plugin " +
                'with that feature was given for this type of application'
    }

    @Override
    List<String> getFeaturesReliedOn(ApplicationType applicationType) {
        PackCondition.Facts facts = PackCondition.of(applicationType, (Options) null, Collections.<Feature>emptyList())
        return pack.descriptor.ofForge.reliesOn.findAll { PackOfForge.Addition each -> each.when.test(facts) }
                .collect { PackOfForge.Addition each -> each.feature }
    }

    protected boolean isDefaultFor(ApplicationType applicationType, Options options, Set<Feature> selectedFeatures) {
        PackCondition byDefault = pack.descriptor.ofForge.byDefault
        return byDefault != null && byDefault.test(PackCondition.of(applicationType, options, selectedFeatures))
    }

    @Override
    void processSelectedFeatures(FeatureContext featureContext) {
        Integer jdk = pack.descriptor.ofForge.requiresJdk
        if (jdk != null && featureContext.getJavaVersion().majorVersion() < jdk) {
            throw new IllegalArgumentException("${name} requires JDK ${jdk} or later (selected: JDK ${featureContext.getJavaVersion().majorVersion()}).")
        }
        // the requirements are selected when the features are; this is for a context made another way
        for (String required : getDependentFeatures()) {
            if (!featureContext.isPresent(required)) {
                Feature feature = features.apply(required)
                        .filter { Feature found -> found.supports(featureContext.getApplicationType()) }
                        .orElseThrow { new IllegalArgumentException(requiredNotThere(name, required)) }
                featureContext.addFeature(feature)
            }
        }
        PackCondition.Facts facts = PackCondition.of(featureContext)
        for (PackOfForge.Addition addition : pack.descriptor.ofForge.adds) {
            // asked one after the other: what one addition brings in, the next one sees
            if (!featureContext.isPresent(addition.feature) && addition.when.test(facts)) {
                // whatever types of application the feature is offered for, as a feature written as a class adds another
                featureContext.addFeature(features.apply(addition.feature).orElseThrow {
                    new IllegalStateException("The feature ${name} adds the feature ${addition.feature}, which Forge has not")
                })
            }
        }
    }

    @Override
    void apply(GeneratorContext generatorContext) {
        PackDescriptor descriptor = pack.descriptor
        PackOfForge ofForge = descriptor.ofForge
        PackCondition.Facts facts = PackCondition.of(generatorContext)
        for (String conflict : descriptor.conflicts) {
            Feature other = generatorContext.getFeatures().getFeatures().find { Feature each -> isNamed(conflict, each) }
            if (other != null) {
                throw new IllegalArgumentException("The feature ${name} cannot be used with the feature ${other.getName()}")
            }
        }
        // a pack of Forge is a feature of the framework: what it sets is not compared with what another feature sets
        boolean held = !pack.isBundled()
        List<PackDependency> declared = held ? descriptor.dependencies + descriptor.platforms : Collections.<PackDependency>emptyList()
        declared.each { PackDependency dependency -> checkVersion(generatorContext, dependency) }
        for (PackDependency dependency : descriptor.dependencies) {
            if (!dependency.when.test(facts)) {
                continue
            }
            if (dependency.scope == PackDescriptorParser.BUILDSCRIPT) {
                generatorContext.addBuildscriptDependency(toDependency(generatorContext, dependency, false))
            } else {
                generatorContext.addDependency(toDependency(generatorContext, dependency, false))
            }
        }
        for (PackDependency platform : descriptor.platforms) {
            generatorContext.addDependency(toDependency(generatorContext, platform, true))
        }
        for (PackBuildPlugin plugin : descriptor.buildPlugins) {
            if (!plugin.when.test(facts)) {
                continue
            }
            GradlePlugin.Builder builder = GradlePlugin.builder().id(plugin.id)
            // with an artifact, as a compiled feature of the framework looks the version of its plugin up
            builder = plugin.artifactOfForge == null ? builder.version(plugin.version) : builder.lookupArtifactId(plugin.artifactOfForge)
            generatorContext.addBuildPlugin(plugin.apply ? builder.useApplyPlugin(true).build() : builder.build())
        }
        for (String comment : ofForge.propertyComments) {
            generatorContext.getBuildProperties().addComment(comment)
        }
        descriptor.buildProperties.each { String property, String value ->
            if (held) {
                checkProperty(generatorContext, property, value)
            }
            generatorContext.getBuildProperties().put(property, value)
        }
        descriptor.buildExtensions.each { String extension, String body -> addExtension(generatorContext, extension, body) }
        ofForge.buildExtensionParts.each { String extension, List<PackOfForge.Part> parts ->
            for (PackOfForge.Part part : parts) {
                if (part.when.test(facts)) {
                    addExtension(generatorContext, extension, part.text)
                }
            }
        }
        descriptor.buildBlocks.each { String anchor, String text ->
            generatorContext.getBuildContributions().block(PackDescriptorParser.ANCHORS.get(anchor), text)
        }
        for (PackLink link : descriptor.readmeLinks) {
            generatorContext.addHelpLink(link.label, link.href)
        }
        Map<String, Object> settings = new LinkedHashMap<>()
        if (ofForge.dataSourceOfDriver) {
            flatten(dataSourceOfDriver(generatorContext), '', settings)
        }
        flatten(descriptor.configuration, '', settings)
        for (PackOfForge.Settings each : ofForge.settings) {
            if (each.environmentFile == null && each.when.test(facts)) {
                flatten(each.values, '', settings)
            }
        }
        settings.each { String setting, Object value ->
            if (held) {
                checkSetting(generatorContext, setting, value)
            }
            generatorContext.getConfiguration().put(setting, value)
        }
        for (PackOfForge.Settings each : ofForge.settings) {
            if (each.environmentFile != null && each.when.test(facts)) {
                Map<String, Object> ofEnvironment = new LinkedHashMap<>()
                flatten(each.values, '', ofEnvironment)
                configurationOf(generatorContext, each.environmentFile).putAll(ofEnvironment)
            }
        }
        List<PackFile> files = descriptor.files.findAll { PackFile file -> file.when.test(facts) }
        // every destination before the first template: a pack that cannot write one of its files runs none. Where a
        // pack of Forge writes is checked by the generator, as it is for every file of the framework
        for (PackFile file : held ? files : Collections.<PackFile>emptyList()) {
            checkDestination(generatorContext, file)
        }
        long written = 0
        for (PackFile file : files) {
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
        if (held) {
            // a feature applied after this one could have replaced what it added, without a word
            generatorContext.afterFeaturesApplied {
                declared.each { PackDependency dependency -> checkVersion(generatorContext, dependency) }
                descriptor.buildProperties.each { String property, String value -> checkProperty(generatorContext, property, value) }
                settings.each { String setting, Object value -> checkSetting(generatorContext, setting, value) }
            }
        }
    }

    // tasks.bootRun configures the task bootRun; a longer name, such as tasks.withType, is a block of its own
    private static void addExtension(GeneratorContext generatorContext, String extension, String body) {
        if (extension.startsWith(TASKS) && extension.indexOf('.', TASKS.length()) < 0 && extension.indexOf('(') < 0) {
            generatorContext.getBuildContributions().task(extension.substring(TASKS.length()), body)
        } else {
            generatorContext.getBuildContributions().extension(extension, body)
        }
    }

    /**
     * The settings the database driver of the application gives the Grails Data implementation that
     * connects through it.
     */
    private Map<String, Object> dataSourceOfDriver(GeneratorContext generatorContext) {
        for (Feature feature : generatorContext.getFeatures().getFeatures()) {
            if (feature instanceof PackFeature && ((PackFeature) feature).pack.descriptor.oneOf == DATABASE_DRIVERS) {
                Map<String, Object> dataSource = ((PackFeature) feature).pack.descriptor.ofForge.dataSource
                if (dataSource == null) {
                    throw new IllegalStateException("The feature ${name} takes the data source of the database driver, and the feature " +
                            "${feature.getName()} names none")
                }
                return dataSource
            }
        }
        throw new IllegalStateException("The feature ${name} takes the data source of the database driver, and the application has no database driver")
    }

    // the file of the test environment is a resource of the tests, as the framework has it
    private static Configuration configurationOf(GeneratorContext generatorContext, String environment) {
        return generatorContext.getConfiguration(environment, environment == TEST_ENVIRONMENT ?
                ApplicationConfiguration.testConfig() : new ApplicationConfiguration(environment))
    }

    private Dependency.Builder toDependency(GeneratorContext generatorContext, PackDependency declared, boolean platform) {
        Dependency.Builder dependency = Dependency.builder()
        if (declared.versionOfForge) {
            // as a compiled feature of the framework looks its version up; the group is the one Forge knows,
            // and the one the descriptor names has to be that group and not only look like it
            String known = generatorContext.resolveCoordinate(declared.artifactId).getGroupId()
            if (known != declared.groupId) {
                throw new IllegalStateException("The feature ${name} names ${declared.groupId}:${declared.artifactId} for a version of " +
                        "Forge, and Forge knows the artifact as ${known}:${declared.artifactId}")
            }
            dependency.groupId(declared.groupId).lookupArtifactId(declared.artifactId)
        } else {
            dependency.groupId(declared.groupId).artifactId(declared.artifactId).version(declared.version)
        }
        dependency.scope(PackDescriptorParser.SCOPES_OF_FORGE.get(platform ? 'implementation' : declared.scope))
                .pom(platform)
        for (String exclusion : declared.excludes) {
            int colon = exclusion.indexOf(':')
            dependency.exclude(colon < 0 ? exclusion : exclusion.substring(0, colon), colon < 0 ? null : exclusion.substring(colon + 1))
        }
        for (String capability : declared.capabilities) {
            dependency.requireCapability(capability)
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
        if (file.isTemplate()) {
            // rendered here and not when the file is written: every feature is chosen by now, and a
            // template that fails does so before anything of the application is written
            String from = file.from.length() > 120 ? file.from.substring(0, 120) + '...' : file.from
            content = pages.get().render(name, new String(content, StandardCharsets.UTF_8), "${from} of the feature ${name}".toString(),
                    pack.descriptor.templateApi, PackModel.of(generatorContext, pack.descriptor), PackLoader.MAX_FILE_BYTES)
        }
        generatorContext.addTemplate("pack:${name}:${file.to}".toString(), new PackContent(file.to, content, file.executable, !pack.isBundled()))
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

    /**
     * The feature of a pack of Forge whose descriptor says when it is selected without being named.
     */
    static class ByDefault extends PackFeature implements DefaultFeature {

        protected ByDefault(Pack pack, Function<String, Optional<Feature>> features, Supplier<PackPages> pages) {
            super(pack, features, pages)
        }

        @Override
        boolean shouldApply(ApplicationType applicationType, Options options, Set<Feature> selectedFeatures) {
            return isDefaultFor(applicationType, options, selectedFeatures)
        }
    }

    /**
     * The feature of a pack of Forge that is the default of the exclusive group its descriptor names,
     * when its descriptor says.
     */
    static class ExclusiveByDefault extends Exclusive implements DefaultFeature {

        protected ExclusiveByDefault(Pack pack, Function<String, Optional<Feature>> features, Supplier<PackPages> pages) {
            super(pack, features, pages)
        }

        @Override
        boolean shouldApply(ApplicationType applicationType, Options options, Set<Feature> selectedFeatures) {
            return isDefaultFor(applicationType, options, selectedFeatures)
        }
    }
}

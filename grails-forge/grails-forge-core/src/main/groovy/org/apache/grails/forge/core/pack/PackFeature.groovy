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
import org.apache.grails.forge.core.pack.PackDescriptor.PackBuildPlugin
import org.apache.grails.forge.core.pack.PackDescriptor.PackDependency
import org.apache.grails.forge.core.pack.PackDescriptor.PackFile
import org.grails.forge.application.ApplicationType
import org.grails.forge.application.generator.GeneratorContext
import org.grails.forge.build.dependencies.Dependency
import org.grails.forge.build.gradle.GradlePlugin
import org.grails.forge.feature.Feature
import org.grails.forge.feature.FeatureContext
import org.grails.forge.feature.OneOfFeature
import org.grails.forge.feature.config.ApplicationConfiguration
import org.grails.forge.feature.config.Configuration
import org.grails.forge.template.DefaultTemplateRenderer
import org.grails.forge.template.Writable

/**
 * The feature a pack describes. It does what a feature written as a class does, from the data of the
 * descriptor: it brings in the features it requires, and adds its dependencies, build plugins, build
 * properties and extensions, configuration, files and readme to the application.
 *
 * @since 9.0
 */
@CompileStatic
class PackFeature implements Feature {

    private static final String ENVIRONMENTS = 'environments'
    private static final String TASKS = 'tasks.'

    private final Pack pack
    private final Function<String, Optional<Feature>> features

    /**
     * @param pack the pack
     * @param features finds a feature the application can have by its name
     */
    protected PackFeature(Pack pack, Function<String, Optional<Feature>> features) {
        this.pack = pack
        this.features = features
    }

    /**
     * @param pack a pack
     * @param features finds a feature the application can have by its name
     * @return the feature of the pack, which belongs to an exclusive group when the descriptor names one
     */
    static PackFeature of(Pack pack, Function<String, Optional<Feature>> features) {
        return pack.descriptor.oneOf == null ? new PackFeature(pack, features) : new Exclusive(pack, features)
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
    boolean supports(ApplicationType applicationType) {
        return pack.descriptor.applicationTypes.contains(applicationType)
    }

    @Override
    void processSelectedFeatures(FeatureContext featureContext) {
        for (String required : pack.descriptor.requires) {
            if (!featureContext.isPresent(required)) {
                Feature feature = features.apply(required).orElseThrow {
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
        for (PackDependency dependency : descriptor.dependencies) {
            generatorContext.addDependency(toDependency(dependency, false))
        }
        for (PackDependency platform : descriptor.platforms) {
            generatorContext.addDependency(toDependency(platform, true))
        }
        for (PackBuildPlugin plugin : descriptor.buildPlugins) {
            generatorContext.addBuildPlugin(GradlePlugin.builder().id(plugin.id).version(plugin.version).build())
        }
        descriptor.buildProperties.each { String property, String value ->
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
        configure(generatorContext)
        for (PackFile file : descriptor.files) {
            addFile(generatorContext, file)
        }
        if (descriptor.readme != null) {
            String readme = descriptor.readme
            generatorContext.addHelpTemplate({ OutputStream out -> out.write(readme.getBytes(StandardCharsets.UTF_8)) } as Writable)
        }
    }

    private static Dependency.Builder toDependency(PackDependency declared, boolean platform) {
        Dependency.Builder dependency = Dependency.builder()
                .groupId(declared.groupId)
                .artifactId(declared.artifactId)
                .scope(PackDescriptorParser.SCOPES.get(platform ? 'implementation' : declared.scope))
                .version(declared.version)
                .pom(platform)
        for (String exclusion : declared.excludes) {
            int colon = exclusion.indexOf(':')
            dependency.exclude(colon < 0 ? exclusion : exclusion.substring(0, colon), colon < 0 ? null : exclusion.substring(colon + 1))
        }
        return dependency
    }

    /**
     * The configuration of the descriptor is for every environment, apart from what is under
     * {@code environments}, which is for the environment it is under.
     */
    private void configure(GeneratorContext generatorContext) {
        pack.descriptor.configuration.each { String key, Object value ->
            if (key == ENVIRONMENTS && value instanceof Map) {
                ((Map<String, Object>) value).each { String environment, Object settings ->
                    // the test environment is configured among the test resources, as the framework does
                    ApplicationConfiguration ofEnvironment = environment == 'test' ? ApplicationConfiguration.testConfig() :
                            new ApplicationConfiguration(environment)
                    put(generatorContext.getConfiguration(environment, ofEnvironment), '', (Map<String, Object>) settings)
                }
            } else {
                put(generatorContext.getConfiguration(), '', [(key): value] as Map<String, Object>)
            }
        }
    }

    /**
     * Features set the configuration by the whole name of a setting, so the mappings of a descriptor are
     * turned into such names. A list is the value of its setting.
     */
    private void put(Configuration configuration, String prefix, Map<String, Object> settings) {
        settings.each { String key, Object value ->
            String setting = prefix + key
            if (value instanceof Map && !((Map) value).isEmpty()) {
                put(configuration, setting + '.', (Map<String, Object>) value)
            } else if (configuration.containsKey(setting) && !Objects.equals(configuration.get(setting), value)) {
                throw new IllegalArgumentException("The feature ${name} sets ${setting}, which another feature has set to another value")
            } else {
                configuration.put(setting, value)
            }
        }
    }

    /**
     * The destination of a file was checked when the descriptor was read, with its placeholders in it.
     * Here they have their values, which come from the name the application was given, so the path that
     * is written is checked again.
     */
    private void addFile(GeneratorContext generatorContext, PackFile file) {
        String written = DefaultTemplateRenderer.resolvePath(file.to, generatorContext.getProject().getProperties())
        if (!PackDescriptorParser.isPathBelowRoot(written)) {
            throw new IllegalArgumentException("The feature ${name} would write the file ${file.to} to ${written}, which is not a " +
                    'path a feature pack can write for this application')
        }
        generatorContext.addTemplate("pack:${name}:${file.to}".toString(), new PackContent(file.to, pack.content(file.from)))
    }

    /**
     * The feature of a pack whose descriptor names an exclusive group.
     */
    static class Exclusive extends PackFeature implements OneOfFeature {

        protected Exclusive(Pack pack, Function<String, Optional<Feature>> features) {
            super(pack, features)
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

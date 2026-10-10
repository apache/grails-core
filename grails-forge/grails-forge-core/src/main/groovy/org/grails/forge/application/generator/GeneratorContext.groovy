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
package org.grails.forge.application.generator

import groovy.transform.CompileStatic
import jakarta.annotation.Nonnull
import jakarta.annotation.Nullable
import org.grails.forge.application.ApplicationType
import org.grails.forge.application.OperatingSystem
import org.grails.forge.application.Project
import org.grails.forge.build.BuildPlugin
import org.grails.forge.build.BuildProperties
import org.grails.forge.build.dependencies.Coordinate
import org.grails.forge.build.dependencies.CoordinateResolver
import org.grails.forge.build.dependencies.Dependency
import org.grails.forge.build.dependencies.DependencyContext
import org.grails.forge.build.dependencies.LookupFailedException
import org.grails.forge.build.dependencies.Scope
import org.grails.forge.build.gradle.GradleRepository
import org.grails.forge.feature.Feature
import org.grails.forge.feature.Features
import org.grails.forge.feature.build.gradle.GradleBuildSrc
import org.grails.forge.feature.config.ApplicationConfiguration
import org.grails.forge.feature.config.BootstrapConfiguration
import org.grails.forge.feature.config.Configuration
import org.grails.forge.options.DevelopmentReloading
import org.grails.forge.options.GormImpl
import org.grails.forge.options.JdkVersion
import org.grails.forge.options.Language
import org.grails.forge.options.Options
import org.grails.forge.options.ServletImpl
import org.grails.forge.options.TestFramework
import org.grails.forge.options.TestViewProvider
import org.grails.forge.template.Template
import org.grails.forge.template.Writable
import org.grails.forge.util.VersionInfo
import org.grails.forge.template.GspTemplate
import org.grails.forge.template.GspView

/**
 * A context object used when generating projects.
 *
 * @author graemerocher
 * @since 6.0.0
 */
@CompileStatic
class GeneratorContext implements DependencyContext {

    private final Project project
    private final OperatingSystem operatingSystem
    private final CoordinateResolver coordinateResolver
    private final BuildProperties buildProperties = new BuildProperties()
    private final ApplicationConfiguration configuration = new ApplicationConfiguration()
    private final Map<String, ApplicationConfiguration> applicationEnvironmentConfiguration = new LinkedHashMap<>()
    private final Map<String, BootstrapConfiguration> bootstrapEnvironmentConfiguration = new LinkedHashMap<>()
    private final BootstrapConfiguration bootstrapConfiguration = new BootstrapConfiguration()
    private final Set<Configuration> otherConfiguration = new HashSet<>()

    private final Map<String, Template> templates = new LinkedHashMap<>()
    private final List<Writable> helpTemplates = new ArrayList<>(8)
    private final ApplicationType command
    private final Features features
    private final Options options
    private final Set<Dependency> dependencies = new HashSet<>()
    private final Set<Dependency> buildscriptDependencies = new HashSet<>()
    private final Set<GradleRepository> buildRepositories
    private final Set<GradleRepository> repositories

    private final Set<BuildPlugin> buildPlugins = new HashSet<>()

    GeneratorContext(Project project,
                            ApplicationType type,
                            Options options,
                            @Nullable OperatingSystem operatingSystem,
                            Set<Feature> features,
                            CoordinateResolver coordinateResolver) {
        this.command = type
        this.project = project
        this.operatingSystem = operatingSystem
        this.coordinateResolver = coordinateResolver
        this.features = new Features(this, features, options)
        this.options = options

        String grailsVersion = VersionInfo.getGrailsVersion()
        Set<GradleRepository> repositories = GradleRepository.getDefaultRepositories(grailsVersion)
        this.buildRepositories = repositories
        this.repositories = repositories
        buildProperties.put('grailsVersion', grailsVersion)
    }

    /**
     * Adds a template.
     * @param name The name of the template
     * @param template The template
     */
    void addTemplate(String name, Template template) {
        templates.put(name, template)
    }

    /**
     * Adds a template.
     * @param name The name of the template
     */
    void removeTemplate(String name) {
        templates.remove(name)
    }

    /**
     * Adds a template which will be consolidated into a single help file.
     *
     * @param writable The template
     */
    void addHelpTemplate(Writable writable) {
        helpTemplates.add(writable)
    }

    /**
     * Ads a Link to a single help file
     * @param label Link's label
     * @param href Link's uri
     */
    void addHelpLink(String label, String href) {
        addHelpTemplate(GspView.of('/forge/feature/other/template/markdownLink.gsp', [label: label, href: href]))
    }

    /**
     * @return The build properties
     */
    @Nonnull BuildProperties getBuildProperties() {
        return buildProperties
    }

    /**
     * @return The configuration
     */
    @Nonnull ApplicationConfiguration getConfiguration() {
        return configuration
    }

    /**
     * @param env the application environment value
     *
     * @return The configuration
     */
    @Nullable ApplicationConfiguration getConfiguration(String env) {
        return applicationEnvironmentConfiguration.get(env)
    }

    @Nonnull ApplicationConfiguration getConfiguration(String env, ApplicationConfiguration defaultConfig) {
        return applicationEnvironmentConfiguration.computeIfAbsent(env, (key) -> defaultConfig)
    }

    /**
     * @param env the application environment value
     * @return The configuration
     */
    @Nullable BootstrapConfiguration getBootstrapConfiguration(String env) {
        return bootstrapEnvironmentConfiguration.get(env)
    }

    @Nonnull BootstrapConfiguration getBootstrapConfiguration(String env, BootstrapConfiguration defaultConfig) {
        return bootstrapEnvironmentConfiguration.computeIfAbsent(env, (key) -> defaultConfig)
    }

    /**
     * @return The bootstrap config
     */
    @Nonnull BootstrapConfiguration getBootstrapConfiguration() {
        return bootstrapConfiguration
    }

    void addConfiguration(@Nonnull Configuration configuration) {
        otherConfiguration.add(configuration)
    }

    @Nonnull Set<Configuration> getAllConfigurations() {
        Set<Configuration> allConfigurations = new HashSet<>()
        allConfigurations.add(configuration)
        allConfigurations.add(bootstrapConfiguration)
        allConfigurations.addAll(applicationEnvironmentConfiguration.values())
        allConfigurations.addAll(bootstrapEnvironmentConfiguration.values())
        allConfigurations.addAll(otherConfiguration)
        return allConfigurations
    }

    /**
     * @return The templates
     */
    @Nonnull Map<String, Template> getTemplates() {
        return Collections.unmodifiableMap(templates)
    }

    /**
     * @return The templates
     */
    @Nonnull List<Writable> getHelpTemplates() {
        return Collections.unmodifiableList(helpTemplates)
    }

    /**
     * @return The development reloading
     */
    @Nonnull
    DevelopmentReloading getDevelopmentReloading() {
        return options.getDevelopmentReloading()
    }

    /**
     * @return The Gorm Implementation
     */
    @Nonnull GormImpl getGorm() {
        return options.getGormImpl()
    }

    /**
     * @return The Servlet Implementation
     */
    @Nonnull ServletImpl getServlet() {
        return options.getServletImpl()
    }

    /**
     * @return The project
     */
    @Nonnull Project getProject() {
        return project
    }

    /**
     * @return The application type
     */
    @Nonnull ApplicationType getApplicationType() {
        return command
    }

    /**
     * @return The selected features
     */
    @Nonnull Features getFeatures() {
        return features
    }

    /**
     * @return The JDK version
     */
    @Nonnull JdkVersion getJdkVersion() {
        return options.getJavaVersion()
    }

    /**
     * @return The current OS
     */
    @Nullable OperatingSystem getOperatingSystem() {
        return operatingSystem
    }

    void applyFeatures() {
        List<Feature> features = new ArrayList<>(this.features.getFeatures())
        features.sort(Comparator.comparingInt(Feature::getOrder))

        for (Feature feature: features) {
            feature.apply(this)
        }
    }

    boolean isFeaturePresent(Class<? extends Feature> feature) {
        return features.isFeaturePresent(feature)
    }

    <T extends Feature> Optional<T> getFeature(Class<T> feature) {
        return features.getFeature(feature)
    }

    <T extends Feature> T getRequiredFeature(Class<T> feature) {
        return features.getRequiredFeature(feature)
    }

    String getSourcePath(String path) {
        return Language.DEFAULT_OPTION.getSourcePath(path)
    }

    String getTestSourcePath(String path) {
        return TestFramework.SPOCK.getSourcePath(path)
    }

    String getIntegrationTestSourcePath(String path) {
        return TestFramework.SPOCK.getIntegrationSourcePath(path)
    }

    GspView parseView(GspView javaTemplate, GspView groovyTemplate) {
        return groovyTemplate
    }

    void addTemplate(String name, String path, TestViewProvider testViewProvider) {
        GspView view = testViewProvider.findView(TestFramework.SPOCK)
        if (view != null) {
            addTemplate(name, new GspTemplate(path, view))
        }
    }

    void addTemplate(String templateName,
                            String triggerFile,
                            GspView javaTemplate,
                            GspView groovyTemplate) {
        GspView view = parseView(javaTemplate, groovyTemplate)
        addTemplate(templateName, new GspTemplate(triggerFile, view))
    }

    @Override
    void addDependency(@Nonnull Dependency dependency) {
        if (dependency.requiresLookup()) {
            Coordinate coordinate = coordinateResolver.resolve(dependency.getArtifactId())
                    .orElseThrow(() -> new LookupFailedException(dependency.getArtifactId()))
            this.dependencies.add(dependency.resolved(coordinate))
        } else {
            this.dependencies.add(dependency)
        }
    }

    @Override
    void addBuildscriptDependency(@Nonnull Dependency dependency) {
        if (dependency.requiresLookup()) {
            Coordinate coordinate = coordinateResolver.resolve(dependency.getArtifactId())
                    .orElseThrow(() -> new LookupFailedException(dependency.getArtifactId()))
            addBuildscriptDependencyBasedOnFeatures(dependency.resolved(coordinate))
        } else {
            addBuildscriptDependencyBasedOnFeatures(dependency)
        }
    }

    private void addBuildscriptDependencyBasedOnFeatures(@Nonnull Dependency dependency) {
        if (getFeature(GradleBuildSrc).isPresent()) {
            // for buildSrc/build.gradle with initial scope
            this.buildscriptDependencies.add(dependency)
        } else {
            // for main build.gradle with classpath scope
            this.buildscriptDependencies.add(dependency.scope(Scope.CLASSPATH))
        }
    }

    @Override
    @Nonnull
    Set<Dependency> getDependencies() {
        return dependencies
    }

    @Override
    @Nonnull
    Set<Dependency> getBuildscriptDependencies() {
        return buildscriptDependencies
    }

    @Override
    @Nonnull
    Set<GradleRepository> getRepositories() {
        return repositories
    }

    @Override
    @Nonnull
    Set<GradleRepository> getBuildRepositories() {
        return buildRepositories
    }

    void addBuildPlugin(BuildPlugin buildPlugin) {
        if (buildPlugin.requiresLookup()) {
            this.buildPlugins.add(buildPlugin.resolved(coordinateResolver))
        } else {
            this.buildPlugins.add(buildPlugin)
        }
    }

    Coordinate resolveCoordinate(String artifactId) {
        return coordinateResolver.resolve(artifactId)
                    .orElseThrow(() -> new LookupFailedException(artifactId))
    }

    Set<BuildPlugin> getBuildPlugins() {
        return buildPlugins
    }
}

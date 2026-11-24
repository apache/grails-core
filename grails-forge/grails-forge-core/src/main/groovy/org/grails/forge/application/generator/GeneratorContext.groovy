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
import org.grails.forge.build.gradle.BuildFileContributions
import org.grails.forge.build.gradle.GradlePlugin
import org.grails.forge.build.gradle.GradleRepository
import org.grails.forge.feature.Feature
import org.grails.forge.feature.FeatureContext
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
    // in the order they were added: what is written of them is in an order that is said, see applyOnce
    private final Set<Configuration> otherConfiguration = new LinkedHashSet<>()

    private final Map<String, Template> templates = new LinkedHashMap<>()
    private final List<Writable> helpTemplates = new ArrayList<>(8)
    private final ApplicationType command
    private final Features features
    private final Options options
    // The dependencies and the plugins are kept in the order the features declare them. The build file
    // sorts the dependencies and puts the plugins by their order; what that leaves undecided stays as it
    // was declared, and not as a set of hashes would have it, which is another order for another number
    // of entries and for another run
    private final Set<Dependency> dependencies = new LinkedHashSet<>()
    private final Set<Dependency> buildscriptDependencies = new LinkedHashSet<>()
    private final Set<GradleRepository> buildRepositories
    private final Set<GradleRepository> repositories

    private final Set<BuildPlugin> buildPlugins = new LinkedHashSet<>()
    private final BuildFileContributions buildContributions = new BuildFileContributions()
    private boolean featuresApplied
    private Throwable applyFailure
    private final List<Runnable> checks = new ArrayList<>()
    private List<String> requestedFeatures = Collections.emptyList()
    private final List<String> excludedFeatures
    private final String repositoryOverride

    GeneratorContext(Project project,
                            ApplicationType type,
                            Options options,
                            @Nullable OperatingSystem operatingSystem,
                            Set<Feature> features,
                            CoordinateResolver coordinateResolver) {
        this(project, type, options, operatingSystem, features, coordinateResolver, Collections.<String>emptyList())
    }

    /**
     * @param excludedFeatures the names of the features that were excluded when the features were selected
     * @since 9.0
     */
    GeneratorContext(Project project,
                            ApplicationType type,
                            Options options,
                            @Nullable OperatingSystem operatingSystem,
                            Set<Feature> features,
                            CoordinateResolver coordinateResolver,
                            Collection<String> excludedFeatures) {
        this(project, type, options, operatingSystem, features, coordinateResolver, excludedFeatures, System.getenv('GRAILS_REPO_URL'))
    }

    /**
     * @param excludedFeatures the names of the features that were excluded when the features were selected
     * @param repositoryOverride the repositories the build is given in place of those of Grails, as
     * the environment variable {@code GRAILS_REPO_URL} has them, or {@code null} for those of Grails
     * @since 9.0
     */
    GeneratorContext(Project project,
                            ApplicationType type,
                            Options options,
                            @Nullable OperatingSystem operatingSystem,
                            Set<Feature> features,
                            CoordinateResolver coordinateResolver,
                            Collection<String> excludedFeatures,
                            @Nullable String repositoryOverride) {
        this.repositoryOverride = repositoryOverride
        this.excludedFeatures = Collections.unmodifiableList(new ArrayList<String>(new TreeSet<String>(excludedFeatures)))
        this.command = type
        this.project = project
        this.operatingSystem = operatingSystem
        this.coordinateResolver = coordinateResolver
        this.features = new Features(this, features, options)
        this.options = options

        String grailsVersion = VersionInfo.getGrailsVersion()
        this.buildRepositories = GradleRepository.getDefaultBuildRepositories(grailsVersion, repositoryOverride)
        this.repositories = GradleRepository.getDefaultRepositories(grailsVersion, repositoryOverride)
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
        Set<Configuration> allConfigurations = new LinkedHashSet<>()
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
     * @return the options the application was asked for with, as they were given or defaulted: what the
     * features are applied with. See {@link #getApplicationOptions()} for what the application has
     * @since 9.0
     */
    @Nonnull Options getOptions() {
        return options
    }

    /**
     * The options as the application has them. An option that was not given is a default, which gives
     * way to the features: an application that was asked for with the feature of Hibernate 7 and no
     * option has Hibernate 7 and not the default. This is what is recorded with the application and
     * what a template of a feature pack is told; the features themselves are applied with the options
     * as they were given or defaulted.
     *
     * @return the options, each naming what the application has
     * @since 9.0
     */
    @Nonnull Options getApplicationOptions() {
        Options applied = options
        Set<String> names = new HashSet<String>(features)
        if (!names.contains(options.getGormImpl().getName())) {
            // an application has one: no option says that there is none
            GormImpl present = GormImpl.values().find { GormImpl each -> names.contains(each.getName()) }
            if (present != null) {
                applied = applied.withGormImpl(present)
            }
        }
        ServletImpl servlet = options.getServletImpl()
        if (servlet == ServletImpl.NONE || !names.contains(servlet.getName())) {
            // none where the application has no servlet container, as a plugin has none
            ServletImpl present = ServletImpl.values().find { ServletImpl each -> each != ServletImpl.NONE && names.contains(each.getName()) }
            applied = applied.withServletImpl(present ?: ServletImpl.NONE)
        }
        Feature reloading = features.reloading()
        if (reloading == null) {
            applied = applied.withDevelopmentReloading(DevelopmentReloading.NONE)
        } else if (!features.getFeatures().any { Feature each -> DevelopmentReloading.ofFeature(each.getName()) == options.getDevelopmentReloading() }) {
            applied = applied.withDevelopmentReloading(DevelopmentReloading.ofFeature(reloading.getName()))
        }
        return applied
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
     * @return the names of the features that were asked for by name, without what they require and
     * without the defaults
     * @since 9.0
     */
    @Nonnull List<String> getRequestedFeatures() {
        return requestedFeatures
    }

    /**
     * @param requestedFeatures the names of the features that were asked for by name
     * @since 9.0
     */
    void setRequestedFeatures(@Nonnull List<String> requestedFeatures) {
        this.requestedFeatures = Collections.unmodifiableList(new ArrayList<String>(requestedFeatures))
    }

    /**
     * @return the names of the features that were excluded when the features were selected, sorted
     * @since 9.0
     */
    @Nonnull List<String> getExcludedFeatures() {
        return excludedFeatures
    }

    /**
     * @return the repositories the build was given in place of those of Grails, as the environment
     * variable {@code GRAILS_REPO_URL} has them, or {@code null}
     * @since 9.0
     */
    @Nullable String getRepositoryOverride() {
        return repositoryOverride
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

    /**
     * Applies the features to this context, in {@link FeatureContext#FEATURE_ORDER}. Each feature is applied
     * once: applying the features of a context a second time does nothing, or fails as the first time did.
     */
    void applyFeatures() {
        if (applyFailure != null) {
            // half of the features are applied: there is no application to generate from this context
            throw applyFailure
        }
        if (featuresApplied) {
            return
        }
        featuresApplied = true
        try {
            applyOnce()
        } catch (RuntimeException | Error e) {
            applyFailure = e
            throw e
        }
    }

    private void applyOnce() {
        List<Feature> features = new ArrayList<>(this.features.getFeatures())
        features.sort(FeatureContext.FEATURE_ORDER)

        for (Feature feature: features) {
            feature.apply(this)
        }
        for (Runnable check : checks) {
            check.run()
        }
    }

    /**
     * Has a check run once every feature is applied. A feature uses it to see that what it added is
     * still what it added, after the features applied later than it.
     *
     * @param check the check, which throws an {@link IllegalArgumentException} for what it finds
     * @since 9.0
     */
    void afterFeaturesApplied(Runnable check) {
        checks.add(check)
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
            addDeclared(this.dependencies, dependency.resolved(coordinate))
        } else {
            addDeclared(this.dependencies, dependency)
        }
    }

    /**
     * Adds a dependency unless the same one, by group, artifact and scope, is already declared. The one
     * declared first is kept, but two declarations of different versions or kinds, that exclude
     * different transitive dependencies, or that select different variants by their capabilities, are an
     * error, since keeping either would silently drop what the other asked for.
     */
    private static void addDeclared(Set<Dependency> declared, Dependency dependency) {
        if (declared.add(dependency)) {
            return
        }
        Dependency existing = declared.find { Dependency each -> each == dependency }
        if (existing.getVersion() != dependency.getVersion() || existing.isPom() != dependency.isPom() ||
                existing.isEnforced() != dependency.isEnforced()) {
            throw new IllegalArgumentException(
                    "The dependency ${dependency.getGroupId()}:${dependency.getArtifactId()} is declared twice, " +
                    "as ${describe(existing)} and as ${describe(dependency)}")
        }
        if (new HashSet<>(existing.getExclusions()) != new HashSet<>(dependency.getExclusions())) {
            throw new IllegalArgumentException(
                    "The dependency ${dependency.getGroupId()}:${dependency.getArtifactId()} is declared twice " +
                    "with different exclusions: ${existing.getExclusions()} and ${dependency.getExclusions()}")
        }
        if (new HashSet<>(existing.getCapabilities()) != new HashSet<>(dependency.getCapabilities())) {
            throw new IllegalArgumentException(
                    "The dependency ${dependency.getGroupId()}:${dependency.getArtifactId()} is declared twice " +
                    "with different capabilities: ${existing.getCapabilities()} and ${dependency.getCapabilities()}")
        }
    }

    private static String describe(Dependency dependency) {
        String kind = dependency.isPom() ? (dependency.isEnforced() ? 'an enforced platform' : 'a platform') : 'a dependency'
        return dependency.getVersion() == null ? "${kind} without a version" : "${kind} of version ${dependency.getVersion()}"
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
            addDeclared(this.buildscriptDependencies, dependency)
        } else {
            // for main build.gradle with classpath scope
            addDeclared(this.buildscriptDependencies, dependency.scope(Scope.CLASSPATH))
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

    /**
     * Applies a plugin to the build unless the same one is already applied. The one applied first is
     * kept, but applying one plugin in two versions, or once in the plugins block and once with
     * {@code apply plugin}, is an error, since keeping either would silently drop what the other asked for.
     */
    void addBuildPlugin(BuildPlugin buildPlugin) {
        BuildPlugin plugin = buildPlugin.requiresLookup() ? buildPlugin.resolved(coordinateResolver) : buildPlugin
        if (this.buildPlugins.add(plugin) || !(plugin instanceof GradlePlugin)) {
            return
        }
        GradlePlugin applied = (GradlePlugin) this.buildPlugins.find { BuildPlugin each -> each == plugin }
        String version = ((GradlePlugin) plugin).getVersion()
        if (applied.getVersion() != version) {
            throw new IllegalArgumentException("The build plugin ${applied.getId()} is applied twice, " +
                    "${applied.getVersion() == null ? 'without a version' : 'in version ' + applied.getVersion()} and " +
                    "${version == null ? 'without a version' : 'in version ' + version}")
        }
        if (applied.useApplyPlugin() != ((GradlePlugin) plugin).useApplyPlugin()) {
            throw new IllegalArgumentException("The build plugin ${applied.getId()} is applied twice, " +
                    'once in the plugins block and once with apply plugin')
        }
    }

    Coordinate resolveCoordinate(String artifactId) {
        return coordinateResolver.resolve(artifactId)
                    .orElseThrow(() -> new LookupFailedException(artifactId))
    }

    Set<BuildPlugin> getBuildPlugins() {
        return buildPlugins
    }

    /**
     * @return what the features add to the build file besides dependencies, plugins and repositories
     * @since 9.0
     */
    @Nonnull
    BuildFileContributions getBuildContributions() {
        return buildContributions
    }
}

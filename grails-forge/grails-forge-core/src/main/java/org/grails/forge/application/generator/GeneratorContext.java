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
package org.grails.forge.application.generator;

import com.fizzed.rocker.RockerModel;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import org.grails.forge.application.ApplicationType;
import org.grails.forge.application.OperatingSystem;
import org.grails.forge.application.Project;
import org.grails.forge.build.BuildPlugin;
import org.grails.forge.build.BuildProperties;
import org.grails.forge.build.dependencies.*;
import org.grails.forge.build.gradle.GradleRepository;
import org.grails.forge.feature.Feature;
import org.grails.forge.feature.Features;
import org.grails.forge.feature.build.gradle.GradleBuildSrc;
import org.grails.forge.feature.config.ApplicationConfiguration;
import org.grails.forge.feature.config.BootstrapConfiguration;
import org.grails.forge.feature.config.Configuration;
import org.grails.forge.feature.other.template.markdownLink;
import org.grails.forge.options.*;
import org.grails.forge.template.RockerTemplate;
import org.grails.forge.template.RockerWritable;
import org.grails.forge.template.Template;
import org.grails.forge.template.Writable;
import org.grails.forge.util.VersionInfo;

import java.util.*;

/**
 * A context object used when generating projects.
 *
 * @author graemerocher
 * @since 6.0.0
 */
public class GeneratorContext implements DependencyContext {

    private final Project project;
    private final OperatingSystem operatingSystem;
    private final CoordinateResolver coordinateResolver;
    private final BuildProperties buildProperties = new BuildProperties();
    private final ApplicationConfiguration configuration = new ApplicationConfiguration();
    private final Map<String, ApplicationConfiguration> applicationEnvironmentConfiguration = new LinkedHashMap<>();
    private final Map<String, BootstrapConfiguration> bootstrapEnvironmentConfiguration = new LinkedHashMap<>();
    private final BootstrapConfiguration bootstrapConfiguration = new BootstrapConfiguration();
    private final Set<Configuration> otherConfiguration = new HashSet<>();

    private final Map<String, Template> templates = new LinkedHashMap<>();
    private final List<Writable> helpTemplates = new ArrayList<>(8);
    private final ApplicationType command;
    private final Features features;
    private final Options options;
    private final Set<Dependency> dependencies = new HashSet<>();
    private final Set<Dependency> buildscriptDependencies = new HashSet<>();
    private final Set<GradleRepository> buildRepositories;
    private final Set<GradleRepository> repositories;

    private final Set<BuildPlugin> buildPlugins = new HashSet<>();

    public GeneratorContext(Project project,
                            ApplicationType type,
                            Options options,
                            @Nullable OperatingSystem operatingSystem,
                            Set<Feature> features,
                            CoordinateResolver coordinateResolver) {
        this.command = type;
        this.project = project;
        this.operatingSystem = operatingSystem;
        this.coordinateResolver = coordinateResolver;
        this.features = new Features(this, features, options);
        this.options = options;

        String grailsVersion = VersionInfo.getGrailsVersion();
        Set<GradleRepository> repositories = GradleRepository.getDefaultRepositories(grailsVersion);
        this.buildRepositories = repositories;
        this.repositories = repositories;
        buildProperties.put("grailsVersion", grailsVersion);
    }

    /**
     * Adds a template.
     * @param name The name of the template
     * @param template The template
     */
    public void addTemplate(String name, Template template) {
        templates.put(name, template);
    }

    /**
     * Adds a template.
     * @param name The name of the template
     */
    public void removeTemplate(String name) {
        templates.remove(name);
    }

    /**
     * Adds a template which will be consolidated into a single help file.
     *
     * @param writable The template
     */
    public void addHelpTemplate(Writable writable) {
        helpTemplates.add(writable);
    }

    /**
     * Ads a Link to a single help file
     * @param label Link's label
     * @param href Link's uri
     */
    public void addHelpLink(String label, String href) {
        addHelpTemplate(new RockerWritable(markdownLink.template(label, href)));
    }

    /**
     * @return The build properties
     */
    @NonNull public BuildProperties getBuildProperties() {
        return buildProperties;
    }

    /**
     * @return The configuration
     */
    @NonNull public ApplicationConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * @param env the application environment value
     *
     * @return The configuration
     */
    @Nullable public ApplicationConfiguration getConfiguration(String env) {
        return applicationEnvironmentConfiguration.get(env);
    }

    @NonNull public ApplicationConfiguration getConfiguration(String env, ApplicationConfiguration defaultConfig) {
        return applicationEnvironmentConfiguration.computeIfAbsent(env, (key) -> defaultConfig);
    }

    /**
     * @param env the application environment value
     * @return The configuration
     */
    @Nullable public BootstrapConfiguration getBootstrapConfiguration(String env) {
        return bootstrapEnvironmentConfiguration.get(env);
    }

    @NonNull public BootstrapConfiguration getBootstrapConfiguration(String env, BootstrapConfiguration defaultConfig) {
        return bootstrapEnvironmentConfiguration.computeIfAbsent(env, (key) -> defaultConfig);
    }

    /**
     * @return The bootstrap config
     */
    @NonNull public BootstrapConfiguration getBootstrapConfiguration() {
        return bootstrapConfiguration;
    }

    public void addConfiguration(@NonNull Configuration configuration) {
        otherConfiguration.add(configuration);
    }

    @NonNull public Set<Configuration> getAllConfigurations() {
        Set<Configuration> allConfigurations = new HashSet<>();
        allConfigurations.add(configuration);
        allConfigurations.add(bootstrapConfiguration);
        allConfigurations.addAll(applicationEnvironmentConfiguration.values());
        allConfigurations.addAll(bootstrapEnvironmentConfiguration.values());
        allConfigurations.addAll(otherConfiguration);
        return allConfigurations;
    }

    /**
     * @return The templates
     */
    @NonNull public Map<String, Template> getTemplates() {
        return Collections.unmodifiableMap(templates);
    }

    /**
     * @return The templates
     */
    @NonNull public List<Writable> getHelpTemplates() {
        return Collections.unmodifiableList(helpTemplates);
    }

    /**
     * @return The test framework
     */
    @NonNull
    public TestFramework getTestFramework() {
        return options.getTestFramework();
    }

    /**
     * @return The Gorm Implementation
     */
    @NonNull public GormImpl getGorm() {
        return options.getGormImpl();
    }

    /**
     * @return The Servlet Implementation
     */
    @NonNull public ServletImpl getServlet() {
        return options.getServletImpl();
    }

    /**
     * @return The project
     */
    @NonNull public Project getProject() {
        return project;
    }

    /**
     * @return The application type
     */
    @NonNull public ApplicationType getApplicationType() {
        return command;
    }

    /**
     * @return The selected features
     */
    @NonNull public Features getFeatures() {
        return features;
    }

    /**
     * @return The JDK version
     */
    @NonNull public JdkVersion getJdkVersion() {
        return options.getJavaVersion();
    }

    /**
     * @return The current OS
     */
    @Nullable public OperatingSystem getOperatingSystem() {
        return operatingSystem;
    }

    public void applyFeatures() {
        List<Feature> features = new ArrayList<>(this.features.getFeatures());
        features.sort(Comparator.comparingInt(Feature::getOrder));

        for (Feature feature: features) {
            feature.apply(this);
        }
    }

    public boolean isFeaturePresent(Class<? extends Feature> feature) {
        return features.isFeaturePresent(feature);
    }

    public <T extends Feature> Optional<T> getFeature(Class<T> feature) {
        return features.getFeature(feature);
    }

    public <T extends Feature> T getRequiredFeature(Class<T> feature) {
        return features.getRequiredFeature(feature);
    }

    public String getSourcePath(String path) {
        return Language.DEFAULT_OPTION.getSourcePath(path);
    }

    public String getTestSourcePath(String path) {
        return getTestFramework().getSourcePath(path, Language.DEFAULT_OPTION);
    }

    public String getIntegrationTestSourcePath(String path) {
        return getTestFramework().getIntegrationSourcePath(path, Language.DEFAULT_OPTION);
    }

    RockerModel parseModel(RockerModel javaTemplate,
                           RockerModel groovyTemplate) {
       return groovyTemplate;
    }

    public void addTemplate(String name, String path, TestRockerModelProvider testRockerModelProvider) {
        RockerModel rockerModel = testRockerModelProvider.findModel(Language.DEFAULT_OPTION, getTestFramework());
        if (rockerModel != null) {
            addTemplate(name, new RockerTemplate(path, rockerModel));
        }
    }

    public void addTemplate(String templateName,
                            String triggerFile,
                            RockerModel javaTemplate,
                            RockerModel groovyTemplate) {
        RockerModel rockerModel = parseModel(javaTemplate, groovyTemplate);
        addTemplate(templateName, new RockerTemplate(triggerFile, rockerModel));
    }

    @Override
    public void addDependency(@NonNull Dependency dependency) {
        if (dependency.requiresLookup()) {
            Coordinate coordinate = coordinateResolver.resolve(dependency.getArtifactId())
                    .orElseThrow(() -> new LookupFailedException(dependency.getArtifactId()));
            this.dependencies.add(dependency.resolved(coordinate));
        } else {
            this.dependencies.add(dependency);
        }
    }

    @Override
    public void addBuildscriptDependency(@NonNull Dependency dependency) {
        if (dependency.requiresLookup()) {
            Coordinate coordinate = coordinateResolver.resolve(dependency.getArtifactId())
                    .orElseThrow(() -> new LookupFailedException(dependency.getArtifactId()));
            addBuildscriptDependencyBasedOnFeatures(dependency.resolved(coordinate));
        } else {
            addBuildscriptDependencyBasedOnFeatures(dependency);
        }
    }

    private void addBuildscriptDependencyBasedOnFeatures(@NonNull Dependency dependency) {
        if (getFeature(GradleBuildSrc.class).isPresent()) {
            // for buildSrc/build.gradle with initial scope
            this.buildscriptDependencies.add(dependency);
        } else {
            // for main build.gradle with classpath scope
            this.buildscriptDependencies.add(dependency.scope(Scope.CLASSPATH));
        }
    }

    @Override
    @NonNull
    public Set<Dependency> getDependencies() {
        return dependencies;
    }

    @Override
    @NonNull
    public Set<Dependency> getBuildscriptDependencies() {
        return buildscriptDependencies;
    }

    @Override
    @NonNull
    public Set<GradleRepository> getRepositories() {
        return repositories;
    }

    @Override
    @NonNull
    public Set<GradleRepository> getBuildRepositories() {
        return buildRepositories;
    }

    public void addBuildPlugin(BuildPlugin buildPlugin) {
        if (buildPlugin.requiresLookup()) {
            this.buildPlugins.add(buildPlugin.resolved(coordinateResolver));
        } else {
            this.buildPlugins.add(buildPlugin);
        }
    }

    public Coordinate resolveCoordinate(String artifactId) {
        return coordinateResolver.resolve(artifactId)
                    .orElseThrow(() -> new LookupFailedException(artifactId));
    }

    public Set<BuildPlugin> getBuildPlugins() {
        return buildPlugins;
    }
}

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
package org.grails.gradle.plugin.commands

import groovy.transform.CompileStatic

import org.gradle.api.GradleException
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.DependencySet
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.artifacts.PublishArtifact
import org.gradle.api.component.AdhocComponentWithVariants
import org.gradle.api.component.ConfigurationVariantDetails
import org.gradle.api.component.SoftwareComponentFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.plugins.JavaPluginExtension

import javax.inject.Inject

import org.apache.grails.gradle.model.cli.ToolBuildNames
import org.grails.gradle.plugin.core.TestPhase
import org.grails.gradle.plugin.core.TestPhasesGradlePlugin

import org.apache.grails.gradle.publish.AdditionalPublication
import org.apache.grails.gradle.publish.GrailsPublishExtension

/**
 * Configures a project to ship its tools as companions under Maven coordinates of their own, the
 * producing side: {@code <artifactId>-cli} with the tools the build runs ({@code src/cli}, the
 * deprecated {@code grails-app/commands} and the scripts of {@code src/main/scripts}), and
 * {@code <artifactId>-cli-standalone} with the tools that run as a process of their own
 * ({@code src/standalone}). The code of tools and the dependencies only tools need stay off the
 * runtime class path of consuming applications. The Grails plugin Gradle plugin applies it to every
 * Grails plugin project; applying its id as well changes nothing. A project that is not a Grails
 * plugin, such as a library module of the framework, applies the id itself.
 *
 * Applying the plugin:
 * <ul>
 *   <li>makes the source sets {@code cli} ({@code src/cli}) and {@code standalone}
 *       ({@code src/standalone}), or reuses them when the Grails Gradle plugin has made them, and
 *       exposes each as a feature variant ({@code cli} and {@code cliStandalone});</li>
 *   <li>adds the tool API ({@code org.apache.grails:grails-core-cli}) and the plugin's own runtime
 *       classes to {@code cliApi}, and the tool API alone to {@code standaloneApi}, so tools compile
 *       with no additional configuration and are registered automatically in
 *       {@code META-INF/grails-cli.factories}; {@code -cli} depends on {@code -cli-standalone} when
 *       the plugin has both, and {@code -cli-standalone} never depends on the plugin;</li>
 *   <li>keeps the variants of the tools out of the plugin's default publication and exposes each
 *       companion through a software component of its own ({@code cli}, {@code cliStandalone});</li>
 *   <li>stamps the runtime jar with the {@code Grails-Cli-Artifact} and
 *       {@code Grails-Cli-Standalone-Artifact} manifest attributes, so the Grails Gradle plugin adds
 *       each companion to the scope of its kind ({@code grailsCli}, {@code grailsCliStandalone}) of a
 *       consuming application automatically;</li>
 *   <li>registers the companion publications with the Grails publish plugin
 *       ({@code org.apache.grails.gradle.grails-publish}) when it is applied.</li>
 * </ul>
 *
 * <p>A companion is made only when the project has sources of tools, which is decided when the build is
 * configured: {@code -cli-standalone} when there is a Groovy or Java file below {@code src/standalone};
 * {@code -cli} when there is one below {@code src/cli}, {@code grails-app/commands} or
 * {@code src/standalone}, or any file below {@code src/main/scripts}. A project whose tools are all
 * standalone tools therefore still publishes {@code -cli}, without classes of tools, depending on
 * {@code -cli-standalone}: a build that names {@code -cli} gets the standalone tools through it. Without
 * sources, the source set is there and empty, and the software component, the publication, the manifest
 * attribute and the extra property ({@code cliArtifactId}, {@code cliStandaloneArtifactId}) of the
 * companion are not made.</p>
 *
 * Configure through the {@code cliArtifact} extension:
 * <pre>
 * cliArtifact {
 *     automaticModuleName = 'com.example.myplugin.cli'
 *     // the default: the name above followed by .standalone
 *     standaloneAutomaticModuleName = 'com.example.myplugin.cli.standalone'
 * }
 * </pre>
 *
 * @since 8.0
 */
@CompileStatic
abstract class GrailsCliArtifactGradlePlugin implements Plugin<Project> {

    public static final String PLUGIN_ID = 'org.apache.grails.gradle.grails-plugin-cli'
    public static final String CLI_SOURCE_SET_NAME = ToolBuildNames.CLI_SOURCE_SET
    public static final String CLI_TEST_SOURCE_SET_NAME = 'testCli'

    /** The source set of the standalone tools, published as {@code -cli-standalone}. */
    public static final String STANDALONE_SOURCE_SET_NAME = ToolBuildNames.STANDALONE_SOURCE_SET

    /** Test phase for cli tests that need a booted application; sources in {@code src/integration-test-cli} */
    public static final String CLI_INTEGRATION_TEST_PHASE_NAME = 'integrationTestCli'
    public static final String CLI_INTEGRATION_TEST_SOURCE_FOLDER = 'src/integration-test-cli'

    /** Application config and fixtures shared with the application's own integration test phase */
    public static final String SHARED_INTEGRATION_TEST_RESOURCES = 'src/integration-test/resources'

    public static final String CLI_COMPONENT_NAME = 'cli'
    public static final String CLI_PUBLICATION_NAME = 'cli'

    /** The feature, the software component and the publication of the companion {@code -cli-standalone}. */
    public static final String CLI_STANDALONE_COMPONENT_NAME = 'cliStandalone'
    public static final String CLI_STANDALONE_PUBLICATION_NAME = 'cliStandalone'

    /** The runtime-jar manifest attribute advertising the companion cli coordinate */
    public static final String CLI_ARTIFACT_MANIFEST_ATTRIBUTE = ToolBuildNames.CLI_ARTIFACT_MANIFEST_ATTRIBUTE

    /** The runtime-jar manifest attribute advertising the companion cli-standalone coordinate */
    public static final String CLI_STANDALONE_ARTIFACT_MANIFEST_ATTRIBUTE = ToolBuildNames.CLI_STANDALONE_ARTIFACT_MANIFEST_ATTRIBUTE

    @Inject
    abstract SoftwareComponentFactory getSoftwareComponentFactory()

    @Override
    void apply(Project project) {
        if (!project.pluginManager.hasPlugin('java')) {
            throw new GradleException("The Grails cli-artifact plugin requires the `java` plugin (or a plugin that applies it, e.g. the Grails plugin plugin) to be applied to project `${project.name}` first.")
        }

        CliArtifactExtension extension = project.extensions.create('cliArtifact', CliArtifactExtension)
        extension.artifactId.convention(project.provider { "${project.name}${ToolBuildNames.CLI_ARTIFACT_SUFFIX}" as String })
        extension.standaloneArtifactId.convention(project.provider { "${project.name}${ToolBuildNames.CLI_STANDALONE_ARTIFACT_SUFFIX}" as String })
        extension.defaultDependencies.convention(true)
        extension.standaloneAutomaticModuleName.convention(extension.automaticModuleName.map { String name -> "${name}.standalone" as String })

        // the source sets every Grails project has for its tools, which the Grails Gradle plugin has made already where it is applied
        SourceSetContainer sourceSets = project.extensions.getByType(SourceSetContainer)
        SourceSet cliSourceSet = ToolSourceSets.cli(project)
        SourceSet standaloneSourceSet = ToolSourceSets.standalone(project)

        // -cli-standalone when src/standalone has a source; -cli when any folder of tools has one, so that a project
        // whose tools are all standalone tools still has the -cli a build may name, which then brings them
        boolean publishesStandalone = ToolSourceSets.hasStandaloneSources(project)
        boolean publishesCli = publishesStandalone || ToolSourceSets.hasCliSources(project)

        JavaPluginExtension java = project.extensions.getByType(JavaPluginExtension)
        // no explicit capability: the default (`${group}:${project.name}-cli`, `${group}:${project.name}-cli-standalone`)
        // is derived lazily from the project and matches the default published coordinate
        java.registerFeature(CLI_SOURCE_SET_NAME) { spec ->
            spec.usingSourceSet(cliSourceSet)
        }
        java.registerFeature(CLI_STANDALONE_COMPONENT_NAME) { spec ->
            spec.usingSourceSet(standaloneSourceSet)
        }

        Configuration cliApiElements = project.configurations.getByName(cliSourceSet.apiElementsConfigurationName)
        Configuration cliRuntimeElements = project.configurations.getByName(cliSourceSet.runtimeElementsConfigurationName)
        Configuration cliRuntimeClasspath = project.configurations.getByName(cliSourceSet.runtimeClasspathConfigurationName)
        Configuration standaloneApiElements = project.configurations.getByName(standaloneSourceSet.apiElementsConfigurationName)
        Configuration standaloneRuntimeElements = project.configurations.getByName(standaloneSourceSet.runtimeElementsConfigurationName)
        Configuration standaloneRuntimeClasspath = project.configurations.getByName(standaloneSourceSet.runtimeClasspathConfigurationName)

        // when the companion coordinate is customized, the variants additionally carry the custom
        // capability (`${group}:${artifactId}`), so an in-build consumer requiring the advertised
        // coordinate resolves regardless of the customization; the Gradle default capability is
        // kept for compatibility
        project.afterEvaluate {
            addCustomCapability(project, extension.artifactId, ToolBuildNames.CLI_ARTIFACT_SUFFIX, [cliApiElements, cliRuntimeElements])
            addCustomCapability(project, extension.standaloneArtifactId, ToolBuildNames.CLI_STANDALONE_ARTIFACT_SUFFIX,
                    [standaloneApiElements, standaloneRuntimeElements])
        }

        // tools compile out of the box: the tool API plus the plugin's own runtime classes and its own standalone tools
        // (computed lazily so `cliArtifact { defaultDependencies = false }` can opt out); a project without tools has
        // no companion, and its empty source sets need nothing
        project.configurations.named(cliSourceSet.apiConfigurationName).configure { Configuration cliApi ->
            cliApi.withDependencies { DependencySet dependencies ->
                if (publishesCli && extension.defaultDependencies.get()) {
                    dependencies.add(project.dependencies.create(project))
                    dependencies.add(project.dependencies.create('org.apache.grails:grails-core-cli'))
                    if (publishesStandalone) {
                        // the companion -cli-standalone of this very project, by the capability its feature always has
                        ModuleDependency standalone = (ModuleDependency) project.dependencies.create(project)
                        standalone.capabilities { capabilities ->
                            capabilities.requireCapability("${project.group}:${project.name}${ToolBuildNames.CLI_STANDALONE_ARTIFACT_SUFFIX}")
                        }
                        dependencies.add(standalone)
                    }
                }
            }
        }
        // a standalone tool is compiled against the tool API and nothing of the plugin
        project.configurations.named(standaloneSourceSet.apiConfigurationName).configure { Configuration standaloneApi ->
            standaloneApi.withDependencies { DependencySet dependencies ->
                if (publishesStandalone && extension.defaultDependencies.get()) {
                    dependencies.add(project.dependencies.create('org.apache.grails:grails-core-cli'))
                }
            }
        }

        // the plugin's default publication must carry no trace of the tools
        AdhocComponentWithVariants javaComponent = (AdhocComponentWithVariants) project.components.getByName('java')
        for (Configuration elements : [cliApiElements, cliRuntimeElements, standaloneApiElements, standaloneRuntimeElements]) {
            javaComponent.withVariantsFromConfiguration(elements) { ConfigurationVariantDetails details ->
                details.skip()
            }
        }

        if (publishesCli) {
            publishCompanion(project, CLI_COMPONENT_NAME, cliApiElements, cliRuntimeElements, cliRuntimeClasspath)
            // exported for tooling that maps a project to its companion coordinate (e.g. the Grails
            // Gradle plugin's discovery inside composite builds), and for the BOM of the framework
            exportArtifactId(project, ToolBuildNames.CLI_ARTIFACT_ID_PROPERTY, extension.artifactId)
            // consuming applications discover the companion through this runtime-jar attribute
            advertise(project, CLI_ARTIFACT_MANIFEST_ATTRIBUTE, extension.artifactId)
        }
        if (publishesStandalone) {
            publishCompanion(project, CLI_STANDALONE_COMPONENT_NAME, standaloneApiElements, standaloneRuntimeElements, standaloneRuntimeClasspath)
            exportArtifactId(project, ToolBuildNames.CLI_STANDALONE_ARTIFACT_ID_PROPERTY, extension.standaloneArtifactId)
            advertise(project, CLI_STANDALONE_ARTIFACT_MANIFEST_ATTRIBUTE, extension.standaloneArtifactId)
        }

        // dependency mapping fixes the pom only; the module metadata keeps the capability
        // request, which external consumers cannot resolve — rewrite it to the companion
        // coordinate (see CliPublishingSupport)
        CliPublishingSupport.rewritePublishedCliCapabilityDependencies(project)

        // Both cli phases are ordinary test phases: the plugin builds the source set from the phase's
        // source folder, inherits the module's test configurations, registers the Test task, wires it
        // into check, and contributes the results to the merged report.
        //
        // The cli tier needs its own phases rather than leaking into `test`, which must stay a faithful
        // stand-in for an application's production classpath: if a cli class or a cli-only dependency
        // (jline, jansi) reaches main, that has to surface as a failure instead of being silently
        // satisfied by cli wiring.
        project.pluginManager.apply(TestPhasesGradlePlugin)
        NamedDomainObjectContainer<TestPhase> testPhases =
                project.extensions.getByName(TestPhasesGradlePlugin.EXTENSION_NAME) as NamedDomainObjectContainer<TestPhase>

        // testCli's derived source folder is already src/test-cli, so it needs no override. A tool
        // boots an application the same way the shell does, so tests that need a real application context
        // get the second phase - never the application's own integrationTest phase, which has to keep the
        // production classpath shape.
        addTestPhase(project, testPhases, CLI_TEST_SOURCE_SET_NAME, null)
        addTestPhase(project, testPhases, CLI_INTEGRATION_TEST_PHASE_NAME, CLI_INTEGRATION_TEST_SOURCE_FOLDER)

        // On top of the shared test wiring, both phases see the classes of the tools they exercise and the
        // dependency buckets of the tools. The direction is one-way by design: a cli phase may read `test`,
        // but `test` never sees cli.
        for (String phaseName : [CLI_TEST_SOURCE_SET_NAME, CLI_INTEGRATION_TEST_PHASE_NAME]) {
            SourceSet phaseSourceSet = sourceSets.getByName(phaseName)
            project.dependencies.add(phaseSourceSet.implementationConfigurationName, cliSourceSet.output)
            project.dependencies.add(phaseSourceSet.implementationConfigurationName, standaloneSourceSet.output)
            project.configurations.named(phaseSourceSet.implementationConfigurationName).configure { Configuration it ->
                it.extendsFrom(
                        project.configurations.getByName(cliSourceSet.apiConfigurationName),
                        project.configurations.getByName(cliSourceSet.implementationConfigurationName),
                        project.configurations.getByName(standaloneSourceSet.apiConfigurationName),
                        project.configurations.getByName(standaloneSourceSet.implementationConfigurationName))
            }
            // compileOnly and the annotation processor path are part of the module's declared test
            // toolchain too: a spec moved verbatim out of src/test must still compile against whatever
            // `test` compiled against (Lombok-generated accessors, a type behind testCompileOnly), or the
            // move fails with an error that points nowhere near the cause
            project.configurations.named(phaseSourceSet.compileOnlyConfigurationName).configure { Configuration it ->
                it.extendsFrom(project.configurations.getByName('testCompileOnly'))
            }
            project.configurations.named(phaseSourceSet.annotationProcessorConfigurationName).configure { Configuration it ->
                it.extendsFrom(project.configurations.getByName('testAnnotationProcessor'))
            }
        }

        SourceSet cliIntegrationTestSourceSet = sourceSets.getByName(CLI_INTEGRATION_TEST_PHASE_NAME)
        // both integration phases boot the same application, so its configuration and fixtures are
        // shared rather than duplicated into the cli phase
        File sharedResources = project.file(SHARED_INTEGRATION_TEST_RESOURCES)
        if (sharedResources.directory) {
            cliIntegrationTestSourceSet.resources.srcDir(sharedResources)
        }

        nameCompanionJar(project, cliSourceSet, extension.artifactId, extension.automaticModuleName)
        nameCompanionJar(project, standaloneSourceSet, extension.standaloneArtifactId, extension.standaloneAutomaticModuleName)

        project.pluginManager.withPlugin('org.apache.grails.gradle.grails-publish') {
            project.extensions.configure(GrailsPublishExtension) { GrailsPublishExtension gpe ->
                if (publishesCli) {
                    gpe.additionalPublication(CLI_PUBLICATION_NAME) { AdditionalPublication publication ->
                        publication.artifactId.set(extension.artifactId)
                    }
                }
                if (publishesStandalone) {
                    gpe.additionalPublication(CLI_STANDALONE_PUBLICATION_NAME) { AdditionalPublication publication ->
                        publication.artifactId.set(extension.standaloneArtifactId)
                        publication.sourceSetName.set(STANDALONE_SOURCE_SET_NAME)
                    }
                }
            }
        }
        if (publishesCli || publishesStandalone) {
            project.afterEvaluate {
                if (!project.pluginManager.hasPlugin('org.apache.grails.gradle.grails-publish')
                        && project.pluginManager.hasPlugin('maven-publish')) {
                    project.logger.warn('Project {} publishes the companions of its tools but does not apply the Grails publish plugin (org.apache.grails.gradle.grails-publish). Apply it to publish them automatically, or configure publications for the `cli` and `cliStandalone` components manually.',
                            project.name)
                }
            }
        }
    }

    // a software component of its own for a companion; the secondary classes/resources variants are build-local and are not published
    private void publishCompanion(Project project, String componentName, Configuration apiElements, Configuration runtimeElements,
                                  Configuration runtimeClasspath) {
        AdhocComponentWithVariants component = softwareComponentFactory.adhoc(componentName)
        project.components.add(component)
        component.addVariantsFromConfiguration(apiElements) { ConfigurationVariantDetails details ->
            if (hasDirectoryArtifacts(details)) {
                details.skip()
                return
            }
            details.mapToMavenScope('compile')
            CliPublishingSupport.configureDependencyMapping(project, details, runtimeClasspath)
        }
        component.addVariantsFromConfiguration(runtimeElements) { ConfigurationVariantDetails details ->
            if (hasDirectoryArtifacts(details)) {
                details.skip()
                return
            }
            details.mapToMavenScope('runtime')
            CliPublishingSupport.configureDependencyMapping(project, details, runtimeClasspath)
        }
    }

    // the coordinate of a companion as an extra property of the project, there as soon as the plugin is applied and the
    // one the build script customized once it was evaluated
    private static void exportArtifactId(Project project, String propertyName, Property<String> artifactId) {
        project.extensions.extraProperties.set(propertyName, artifactId.get())
        project.afterEvaluate {
            project.extensions.extraProperties.set(propertyName, artifactId.get())
        }
    }

    private static void advertise(Project project, String attribute, Property<String> artifactId) {
        project.tasks.named('jar', Jar).configure { Jar jar ->
            jar.manifest.attributes((attribute): project.provider {
                "${project.group}:${artifactId.get()}" as String
            })
        }
    }

    private static void addCustomCapability(Project project, Property<String> artifactId, String suffix, List<Configuration> elements) {
        String customized = artifactId.get()
        if (customized != "${project.name}${suffix}" as String) {
            String capability = "${project.group}:${customized}:${project.version}"
            for (Configuration each : elements) {
                each.outgoing.capability(capability)
            }
        }
    }

    // the jar of a companion is the primary artifact of its own coordinate — published unclassified (a classifier
    // would leave the publication with pom packaging and no jar for Maven consumers); the base name keeps the local
    // file distinct from the main jar. Each companion has a module name of its own, as two jars cannot share one
    private static void nameCompanionJar(Project project, SourceSet sourceSet, Property<String> artifactId, Property<String> moduleName) {
        project.tasks.named(sourceSet.jarTaskName, Jar).configure { Jar jar ->
            jar.archiveBaseName.set(artifactId)
            jar.archiveClassifier.set('')
            jar.manifest.attributes('Automatic-Module-Name': moduleName.orElse(''))
            jar.doFirst {
                if (!moduleName.present) {
                    jar.manifest.attributes.remove('Automatic-Module-Name')
                }
            }
        }
    }

    /**
     * Adds a {@link TestPhase} to the container, optionally overriding the source folder its name would
     * otherwise derive.
     *
     * <p>The phase is configured before it is added, NOT through {@code create(name, action)}: the
     * container fires its {@code configureEach} handler - which is what turns {@code sourceFolderName}
     * into a source set - during {@code add()}, so anything a create-action sets arrives too late to be
     * read.</p>
     */
    private static void addTestPhase(Project project, NamedDomainObjectContainer<TestPhase> testPhases,
                                     String phaseName, String sourceFolder) {
        TestPhase phase = project.objects.newInstance(TestPhase, phaseName)
        if (sourceFolder != null) {
            phase.sourceFolderName.set(sourceFolder)
        }
        testPhases.add(phase)
    }

    /**
     * The secondary classes/resources variants of the elements configurations carry directory
     * artifacts — they exist for local inter-project consumption only and must not be published.
     */
    private static boolean hasDirectoryArtifacts(ConfigurationVariantDetails details) {
        details.configurationVariant.artifacts.any { PublishArtifact artifact ->
            artifact.type in ['java-classes-directory', 'java-resources-directory', 'directory']
        }
    }
}

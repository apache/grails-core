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
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.PublishArtifact
import org.gradle.api.component.AdhocComponentWithVariants
import org.gradle.api.component.ConfigurationVariantDetails
import org.gradle.api.component.SoftwareComponentFactory
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.plugins.JavaPluginExtension

import javax.inject.Inject

import org.apache.grails.gradle.publish.AdditionalPublication
import org.apache.grails.gradle.publish.GrailsPublishExtension

/**
 * Configures a Grails plugin project to ship its CLI commands as a companion artifact under its
 * own Maven coordinate ({@code <artifactId>-cli}), keeping command code and CLI-only dependencies
 * off the runtime classpath of consuming applications.
 *
 * Applying the plugin:
 * <ul>
 *   <li>creates a {@code cli} source set ({@code src/cli/groovy}) exposed as a feature variant;</li>
 *   <li>adds {@code org.apache.grails:grails-core-cli} (the command contract) and the plugin's own
 *       runtime classes to the cli compile classpath, so commands compile with no additional
 *       configuration and are registered automatically in {@code META-INF/grails-cli.factories};</li>
 *   <li>keeps the cli variants out of the plugin's default publication and exposes them through a
 *       dedicated {@code cli} software component;</li>
 *   <li>stamps the runtime jar with the {@code Grails-Cli-Artifact} manifest attribute, so the
 *       Grails Gradle plugin adds the companion to a consuming application's {@code grailsCli}
 *       configuration automatically;</li>
 *   <li>registers the companion publication with the Grails publish plugin
 *       ({@code org.apache.grails.gradle.grails-publish}) when it is applied.</li>
 * </ul>
 *
 * Configure through the {@code cliArtifact} extension:
 * <pre>
 * cliArtifact {
 *     automaticModuleName = 'com.example.myplugin.cli'
 * }
 * </pre>
 *
 * @since 8.0
 */
@CompileStatic
abstract class GrailsCliArtifactGradlePlugin implements Plugin<Project> {

    public static final String CLI_SOURCE_SET_NAME = 'cli'
    public static final String CLI_COMPONENT_NAME = 'cli'
    public static final String CLI_PUBLICATION_NAME = 'cli'

    /** The runtime-jar manifest attribute advertising the companion cli coordinate */
    public static final String CLI_ARTIFACT_MANIFEST_ATTRIBUTE = 'Grails-Cli-Artifact'

    @Inject
    abstract SoftwareComponentFactory getSoftwareComponentFactory()

    @Override
    void apply(Project project) {
        if (!project.pluginManager.hasPlugin('java')) {
            throw new GradleException("The Grails cli-artifact plugin requires the `java` plugin (or a plugin that applies it, e.g. the Grails plugin plugin) to be applied to project `${project.name}` first.")
        }

        CliArtifactExtension extension = project.extensions.create('cliArtifact', CliArtifactExtension)
        extension.artifactId.convention(project.provider { "${project.name}-cli" as String })
        extension.defaultDependencies.convention(true)

        SourceSetContainer sourceSets = project.extensions.getByType(SourceSetContainer)
        SourceSet cliSourceSet = sourceSets.create(CLI_SOURCE_SET_NAME)

        JavaPluginExtension java = project.extensions.getByType(JavaPluginExtension)
        java.registerFeature(CLI_SOURCE_SET_NAME) { spec ->
            // no explicit capability: the default (`${group}:${project.name}-cli:${version}`)
            // is derived lazily from the project and matches the default published coordinate
            spec.usingSourceSet(cliSourceSet)
        }

        Configuration cliApiElements = project.configurations.getByName('cliApiElements')
        Configuration cliRuntimeElements = project.configurations.getByName('cliRuntimeElements')
        Configuration cliRuntimeClasspath = project.configurations.getByName('cliRuntimeClasspath')

        // when the companion coordinate is customized, the cli variants additionally carry the
        // custom capability (`${group}:${artifactId}`), so an in-build consumer requiring the
        // advertised coordinate resolves regardless of the customization; the Gradle default
        // capability is kept for compatibility
        project.afterEvaluate {
            String artifactId = extension.artifactId.get()
            if (artifactId != "${project.name}-cli" as String) {
                String capability = "${project.group}:${artifactId}:${project.version}"
                cliApiElements.outgoing.capability(capability)
                cliRuntimeElements.outgoing.capability(capability)
            }
        }

        // commands compile out of the box: the contract plus the plugin's own runtime classes
        // (computed lazily so `cliArtifact { defaultDependencies = false }` can opt out)
        project.configurations.named('cliApi').configure { Configuration cliApi ->
            cliApi.withDependencies { dependencies ->
                if (extension.defaultDependencies.get()) {
                    dependencies.add(project.dependencies.create(project))
                    dependencies.add(project.dependencies.create('org.apache.grails:grails-core-cli'))
                }
            }
        }

        // exported for tooling that maps a project to its companion coordinate (e.g. the Grails
        // Gradle plugin's discovery inside composite builds)
        project.afterEvaluate {
            project.extensions.extraProperties.set('cliArtifactId', extension.artifactId.get())
        }

        // the plugin's default publication must carry no trace of the cli tier
        AdhocComponentWithVariants javaComponent = (AdhocComponentWithVariants) project.components.getByName('java')
        javaComponent.withVariantsFromConfiguration(cliApiElements) { ConfigurationVariantDetails details ->
            details.skip()
        }
        javaComponent.withVariantsFromConfiguration(cliRuntimeElements) { ConfigurationVariantDetails details ->
            details.skip()
        }

        // expose the cli variants as their own software component / coordinate; the secondary
        // classes/resources variants are build-local and must not be published
        AdhocComponentWithVariants cliComponent = softwareComponentFactory.adhoc(CLI_COMPONENT_NAME)
        project.components.add(cliComponent)
        cliComponent.addVariantsFromConfiguration(cliApiElements) { ConfigurationVariantDetails details ->
            if (hasDirectoryArtifacts(details)) {
                details.skip()
                return
            }
            details.mapToMavenScope('compile')
            CliPublishingSupport.configureDependencyMapping(project, details, cliRuntimeClasspath)
        }
        cliComponent.addVariantsFromConfiguration(cliRuntimeElements) { ConfigurationVariantDetails details ->
            if (hasDirectoryArtifacts(details)) {
                details.skip()
                return
            }
            details.mapToMavenScope('runtime')
            CliPublishingSupport.configureDependencyMapping(project, details, cliRuntimeClasspath)
        }

        // dependency mapping fixes the pom only; the module metadata keeps the capability
        // request, which external consumers cannot resolve — rewrite it to the companion
        // coordinate (see CliPublishingSupport)
        CliPublishingSupport.rewritePublishedCliCapabilityDependencies(project)

        // the plugin's tests exercise the cli classes through their public API: the cli output
        // and the cli dependency buckets flow into the test configurations (dependency-based, so
        // the wiring is immune to when test tasks snapshot their source set classpaths)
        for (String testConfiguration : ['testImplementation', 'integrationTestImplementation']) {
            project.configurations.matching { Configuration it -> it.name == testConfiguration }
                    .configureEach { Configuration it ->
                        project.dependencies.add(it.name, cliSourceSet.output)
                        it.extendsFrom(project.configurations.getByName('cliApi'), project.configurations.getByName('cliImplementation'))
                    }
        }

        project.tasks.named(cliSourceSet.jarTaskName, Jar).configure { Jar jar ->
            // the jar is the primary artifact of its own coordinate — publish it unclassified
            // (a `cli` classifier would leave the publication with pom packaging and no jar for
            // Maven consumers); the base name keeps the local file distinct from the main jar
            jar.archiveBaseName.set(extension.artifactId)
            jar.archiveClassifier.set('')
            jar.manifest.attributes('Automatic-Module-Name': extension.automaticModuleName.orElse(''))
            jar.doFirst {
                if (!extension.automaticModuleName.present) {
                    jar.manifest.attributes.remove('Automatic-Module-Name')
                }
            }
        }

        // consuming applications discover the companion through this runtime-jar attribute
        project.tasks.named('jar', Jar).configure { Jar jar ->
            jar.manifest.attributes((CLI_ARTIFACT_MANIFEST_ATTRIBUTE): project.provider {
                "${project.group}:${extension.artifactId.get()}" as String
            })
        }

        project.pluginManager.withPlugin('org.apache.grails.gradle.grails-publish') {
            project.extensions.configure(GrailsPublishExtension) { GrailsPublishExtension gpe ->
                gpe.additionalPublication(CLI_PUBLICATION_NAME) { AdditionalPublication publication ->
                    publication.artifactId.set(extension.artifactId)
                }
            }
        }
        project.afterEvaluate {
            if (!project.pluginManager.hasPlugin('org.apache.grails.gradle.grails-publish')
                    && project.pluginManager.hasPlugin('maven-publish')) {
                project.logger.warn('Project {} publishes a cli companion artifact but does not apply the Grails publish plugin (org.apache.grails.gradle.grails-publish). Apply it to publish `{}` automatically, or configure a publication for the `cli` component manually.',
                        project.name, extension.artifactId.get())
            }
        }
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

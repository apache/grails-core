/*
 * Copyright 2015-2025 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.gradle.plugin.publishing

import grails.util.GrailsNameUtils
import groovy.namespace.QName
import io.github.gradlenexus.publishplugin.InitializeNexusStagingRepository
import io.github.gradlenexus.publishplugin.NexusPublishPlugin
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.plugins.ExtensionContainer
import org.gradle.api.plugins.ExtraPropertiesExtension
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.plugins.PluginManager
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.bundling.Jar
import org.gradle.plugins.signing.Sign
import org.gradle.plugins.signing.SigningExtension
import org.gradle.plugins.signing.SigningPlugin
import org.grails.gradle.plugin.util.SourceSets

/**
 * A plugin to ease publishing Grails related artifacts - including source, groovydoc (as javadoc jars), and plugins
 *
 * @author Graeme Rocher
 * @author James Daugherty
 * @since 3.1
 */
class GrailsPublishGradlePlugin implements Plugin<Project> {

    public static String NEXUS_PUBLISH_PLUGIN_ID = 'io.github.gradle-nexus.publish-plugin'
    public static String MAVEN_PUBLISH_PLUGIN_ID = 'maven-publish'
    public static String SIGNING_PLUGIN_ID = 'signing'
    public static String ENVIRONMENT_VARIABLE_BASED_RELEASE = 'GRAILS_PUBLISH_RELEASE'
    public static String SNAPSHOT_PUBLISH_TYPE_PROPERTY = 'snapshotPublishType'
    public static String RELEASE_PUBLISH_TYPE_PROPERTY = 'releasePublishType'

    String getErrorMessage(String missingSetting) {
        return """No '$missingSetting' was specified. Please provide a valid publishing configuration. Example:

grailsPublish {
    websiteUrl = 'https://example.com/myplugin'
    license {
        name = 'Apache-2.0'
    }
    issueTrackerUrl = 'https://github.com/myname/myplugin/issues'
    vcsUrl = 'https://github.com/myname/myplugin'
    title = 'My plugin title'
    desc = 'My plugin description'
    developers = [johndoe: 'John Doe']
}

or

grailsPublish {
    githubSlug = 'foo/bar'
    license {
        name = 'Apache-2.0'
    }
    title = 'My plugin title'
    desc = 'My plugin description'
    developers = [johndoe: 'John Doe']
}

By default snapshotPublishType is set to MAVEN_PUBLISH and releasePublishType is set to NEXUS_PUBLISH.  These can be overridden by setting the associated property.

The credentials and connection url must be specified as a project property or an environment variable:

`MAVEN_PUBLISH` Environment Variables are:
    MAVEN_PUBLISH_USERNAME
    MAVEN_PUBLISH_PASSWORD
    MAVEN_PUBLISH_URL

`NEXUS_PUBLISH` Environment Variables are:
    NEXUS_PUBLISH_USERNAME
    NEXUS_PUBLISH_PASSWORD
    NEXUS_PUBLISH_URL
    NEXUS_PUBLISH_SNAPSHOT_URL
    NEXUS_PUBLISH_STAGING_PROFILE_ID

When using `NEXUS_PUBLISH`, the property `signing.secretKeyRingFile` must be set to the path of the GPG keyring file.

Note: if project properties are used, the properties must be defined prior to applying this plugin.
"""
    }

    @Override
    void apply(Project project) {
        project.rootProject.logger.lifecycle("Applying Grails Publish Gradle Plugin for `${project.name}`...");
        if (project.extensions.findByName('grailsPublish') == null) {
            project.extensions.add('grailsPublish', new GrailsPublishExtension())
        }
        final String nexusPublishUrl = project.findProperty('nexusPublishUrl') ?: System.getenv('NEXUS_PUBLISH_URL') ?: ''
        final String nexusPublishSnapshotUrl = project.findProperty('nexusPublishSnapshotUrl') ?: System.getenv('NEXUS_PUBLISH_SNAPSHOT_URL') ?: ''
        final String nexusPublishUsername = project.findProperty('nexusPublishUsername') ?: System.getenv('NEXUS_PUBLISH_USERNAME') ?: ''
        final String nexusPublishPassword = project.findProperty('nexusPublishPassword') ?: System.getenv('NEXUS_PUBLISH_PASSWORD') ?: ''
        final String nexusPublishStagingProfileId = project.findProperty('nexusPublishStagingProfileId') ?: System.getenv('NEXUS_PUBLISH_STAGING_PROFILE_ID') ?: ''

        final ExtraPropertiesExtension extraPropertiesExtension = project.extensions.findByType(ExtraPropertiesExtension)

        extraPropertiesExtension.setProperty('signing.keyId', project.findProperty('signing.keyId') ?: System.getenv('SIGNING_KEY'))
        extraPropertiesExtension.setProperty('signing.password', project.findProperty('signing.password') ?: System.getenv('SIGNING_PASSPHRASE'))
        extraPropertiesExtension.setProperty('signing.secretKeyRingFile', project.findProperty('signing.secretKeyRingFile') ?: System.getenv('SIGNING_KEYRING'))

        PublishType snapshotPublishType = project.hasProperty(SNAPSHOT_PUBLISH_TYPE_PROPERTY) ? PublishType.valueOf(project.property(SNAPSHOT_PUBLISH_TYPE_PROPERTY) as String) : PublishType.MAVEN_PUBLISH
        PublishType releasePublishType = project.hasProperty(RELEASE_PUBLISH_TYPE_PROPERTY) ? PublishType.valueOf(project.property(RELEASE_PUBLISH_TYPE_PROPERTY) as String) : PublishType.NEXUS_PUBLISH

        boolean isSnapshot, isRelease
        if (System.getenv(ENVIRONMENT_VARIABLE_BASED_RELEASE) != null) {
            // Detect release state based on environment variables instead of versions
            isRelease = Boolean.parseBoolean(System.getenv(ENVIRONMENT_VARIABLE_BASED_RELEASE))
            isSnapshot = !isRelease

            project.rootProject.logger.lifecycle("Environment Variable `$ENVIRONMENT_VARIABLE_BASED_RELEASE` detected - using variable instead of project version.")
        } else {
            String detectedVersion = (project.version == Project.DEFAULT_VERSION ? (project.findProperty('projectVersion') ?: Project.DEFAULT_VERSION) : project.version) as String
            if (detectedVersion == Project.DEFAULT_VERSION) {
                throw new IllegalStateException("Project ${project.name} has an unspecified version (neither `version` or the property `projectVersion` is defined). Release state cannot be determined.")
            }
            if (project.version == Project.DEFAULT_VERSION) {
                project.rootProject.logger.warn("Project ${project.name} does not have a version defined. Using the gradle property `projectVersion` to assume version is ${detectedVersion}.")
            }
            project.rootProject.logger.info("Version $detectedVersion detected for project ${project.name}")

            isSnapshot = detectedVersion.endsWith('SNAPSHOT')
            isRelease = !isSnapshot
        }

        if (isSnapshot) {
            project.rootProject.logger.info("Project ${project.name} will be a snapshot.")
        }
        if (isRelease) {
            project.rootProject.logger.info("Project ${project.name} will be a release.")
        }

        boolean useMavenPublish = (isSnapshot && snapshotPublishType == PublishType.MAVEN_PUBLISH) || (isRelease && releasePublishType == PublishType.MAVEN_PUBLISH)
        if (useMavenPublish) {
            project.rootProject.logger.info("Maven Publish is enabled for project ${project.name}")
        }
        boolean useNexusPublish = (isSnapshot && snapshotPublishType == PublishType.NEXUS_PUBLISH) || (isRelease && releasePublishType == PublishType.NEXUS_PUBLISH)
        if (useNexusPublish) {
            project.rootProject.logger.info("Nexus Publish is enabled for project ${project.name}")
        }

        // Required for the pom always
        final PluginManager projectPluginManager = project.pluginManager
        projectPluginManager.apply(MavenPublishPlugin)

        if (isRelease || useNexusPublish) {
            if (project.pluginManager.hasPlugin(SIGNING_PLUGIN_ID)) {
                project.rootProject.logger.debug("Signing Plugin already applied to project {}", project.name)
            } else {
                projectPluginManager.apply(SigningPlugin)
            }

            project.tasks.withType(Sign).configureEach { Sign task ->
                task.onlyIf { isRelease }
            }
        }

        if (useNexusPublish) {
            // The nexus plugin is special since it must always be applied to the root project.
            // Handle when multiple subprojects exist and grailsPublish is defined in each one instead of at the root.
            final PluginManager rootProjectPluginManager = project.rootProject.pluginManager
            boolean hasNexusPublishApplied = rootProjectPluginManager.hasPlugin(NEXUS_PUBLISH_PLUGIN_ID)
            if (hasNexusPublishApplied) {
                project.rootProject.logger.debug("Nexus Publish Plugin already applied to root project")
            } else {
                rootProjectPluginManager.apply(NexusPublishPlugin)
            }

            if (isRelease) {
                project.rootProject.tasks.withType(InitializeNexusStagingRepository).configureEach { InitializeNexusStagingRepository task ->
                    task.shouldRunAfter = project.tasks.withType(Sign)
                }
            }

            if (!hasNexusPublishApplied) {
                project.rootProject.nexusPublishing {
                    repositories {
                        sonatype {
                            if (nexusPublishUrl) {
                                nexusUrl = project.uri(nexusPublishUrl)
                            }
                            if (nexusPublishSnapshotUrl) {
                                snapshotRepositoryUrl = project.uri(nexusPublishSnapshotUrl)
                            }
                            username = nexusPublishUsername
                            password = nexusPublishPassword
                            stagingProfileId = nexusPublishStagingProfileId
                        }
                    }
                }
            }
        }

        project.afterEvaluate {
            final ExtensionContainer extensionContainer = project.extensions

            validateProjectPublishable(project as Project)
            project.publishing {
                if (useMavenPublish) {
                    final def mavenPublishUrl = project.findProperty('mavenPublishUrl') ?: System.getenv('MAVEN_PUBLISH_URL') ?: ''

                    // Validate as part of the task since we only want to fail the build when the publish actions are in the taskGraph
                    registerValidationTask(project, "requireMavenPublishUrl") {
                        // the maven publish url can technically be a directory so do not force to String type
                        if (!mavenPublishUrl) {
                            throw new RuntimeException('Could not locate a project property of `mavenPublishUrl` or an environment variable of `MAVEN_PUBLISH_URL`. A URL is required for maven publishing.')
                        }
                    }
                    System.setProperty('org.gradle.internal.publish.checksums.insecure', true as String)

                    repositories {
                        maven {
                            final String mavenPublishUsername = project.findProperty('mavenPublishUsername') ?: System.getenv('MAVEN_PUBLISH_USERNAME') ?: ''
                            final String mavenPublishPassword = project.findProperty('mavenPublishPassword') ?: System.getenv('MAVEN_PUBLISH_PASSWORD') ?: ''
                            if (mavenPublishUsername && mavenPublishPassword) {
                                credentials {
                                    username = mavenPublishUsername
                                    password = mavenPublishPassword
                                }
                            }
                            url = mavenPublishUrl
                        }
                    }
                }

                final GrailsPublishExtension gpe = extensionContainer.findByType(GrailsPublishExtension)
                publications {
                    maven(MavenPublication) {
                        artifactId gpe.artifactId ?: project.name
                        groupId gpe.groupId ?: project.group

                        doAddArtefact(project, delegate)
                        def extraArtefact = getDefaultExtraArtifact(project)
                        if (extraArtefact) {
                            artifact extraArtefact
                        }

                        pom.withXml {
                            Node pomNode = asNode()

                            // Prevent multiple dependencyManagement nodes
                            if (pomNode.dependencyManagement) {
                                pomNode.dependencyManagement[0].replaceNode {}
                            }

                            if (gpe != null) {
                                pomNode.children().last() + {
                                    def title = gpe.title ?: project.name
                                    delegate.name title
                                    delegate.description gpe.desc ?: title

                                    def websiteUrl = gpe.websiteUrl ?: gpe.githubSlug ? "https://github.com/$gpe.githubSlug" : ''
                                    if (!websiteUrl) {
                                        throw new RuntimeException(getErrorMessage('websiteUrl'))
                                    }
                                    delegate.url websiteUrl

                                    def license = gpe.license
                                    if (license != null) {
                                        def concreteLicense = GrailsPublishExtension.License.LICENSES.get(license.name)
                                        if (concreteLicense != null) {
                                            delegate.licenses {
                                                delegate.license {
                                                    delegate.name concreteLicense.name
                                                    delegate.url concreteLicense.url
                                                    delegate.distribution concreteLicense.distribution
                                                }
                                            }
                                        } else if (license.name && license.url) {
                                            delegate.licenses {
                                                delegate.license {
                                                    delegate.name license.name
                                                    delegate.url license.url
                                                    delegate.distribution license.distribution
                                                }
                                            }
                                        }
                                    } else {
                                        throw new RuntimeException(getErrorMessage('license'))
                                    }

                                    if (gpe.githubSlug) {
                                        delegate.scm {
                                            delegate.url "https://github.com/$gpe.githubSlug"
                                            delegate.connection "scm:git@github.com:${gpe.githubSlug}.git"
                                            delegate.developerConnection "scm:git@github.com:${gpe.githubSlug}.git"
                                        }
                                        delegate.issueManagement {
                                            delegate.system 'Github Issues'
                                            delegate.url "https://github.com/$gpe.githubSlug/issues"
                                        }
                                    } else {
                                        if (gpe.vcsUrl) {
                                            delegate.scm {
                                                delegate.url gpe.vcsUrl
                                                delegate.connection "scm:$gpe.vcsUrl"
                                                delegate.developerConnection "scm:$gpe.vcsUrl"
                                            }
                                        } else {
                                            throw new RuntimeException(getErrorMessage('vcsUrl'))
                                        }

                                        if (gpe.issueTrackerUrl) {
                                            delegate.issueManagement {
                                                delegate.system 'Issue Tracker'
                                                delegate.url gpe.issueTrackerUrl
                                            }
                                        } else {
                                            throw new RuntimeException(getErrorMessage('issueTrackerUrl'))
                                        }
                                    }

                                    if (gpe.developers) {
                                        delegate.developers {
                                            for (entry in gpe.developers.entrySet()) {
                                                delegate.developer {
                                                    delegate.id entry.key
                                                    delegate.name entry.value
                                                }
                                            }
                                        }
                                    } else {
                                        throw new RuntimeException(getErrorMessage('developers'))
                                    }
                                }

                            }

                            // fix dependencies without a version
                            def mavenPomNamespace = 'http://maven.apache.org/POM/4.0.0'
                            def dependenciesQName = new QName(mavenPomNamespace, 'dependencies')
                            def dependencyQName = new QName(mavenPomNamespace, 'dependency')
                            def versionQName = new QName(mavenPomNamespace, 'version')
                            def groupIdQName = new QName(mavenPomNamespace, 'groupId')
                            def artifactIdQName = new QName(mavenPomNamespace, 'artifactId')
                            def nodes = (pomNode.getAt(dependenciesQName) as NodeList)
                            if (nodes) {
                                def dependencyNodes = (nodes.first() as Node).getAt(dependencyQName)
                                dependencyNodes.findAll { dependencyNode ->
                                    def versionNodes = (dependencyNode as Node).getAt(versionQName)
                                    return versionNodes.size() == 0 || (versionNodes.first() as Node).text().isEmpty()
                                }.each { dependencyNode ->
                                    def groupId = ((dependencyNode as Node).getAt(groupIdQName).first() as Node).text()
                                    def artifactId = ((dependencyNode as Node).getAt(artifactIdQName).first() as Node).text()
                                    def resolvedArtifacts = project.configurations.compileClasspath.resolvedConfiguration.resolvedArtifacts +
                                            project.configurations.runtimeClasspath.resolvedConfiguration.resolvedArtifacts
                                    if (project.configurations.hasProperty('testFixturesCompileClasspath')) {
                                        resolvedArtifacts += project.configurations.testFixturesCompileClasspath.resolvedConfiguration.resolvedArtifacts +
                                                project.configurations.testFixturesRuntimeClasspath.resolvedConfiguration.resolvedArtifacts
                                    }
                                    def managedVersion = resolvedArtifacts.find {
                                        it.moduleVersion.id.group == groupId &&
                                                it.moduleVersion.id.name == artifactId
                                    }?.moduleVersion?.id?.version
                                    if (!managedVersion) {
                                        throw new RuntimeException("No version found for dependency $groupId:$artifactId.")
                                    }
                                    def versionNode = (dependencyNode as Node).getAt(versionQName)
                                    if (versionNode) {
                                        (versionNode.first() as Node).value = managedVersion
                                    } else {
                                        (dependencyNode as Node).appendNode('version', managedVersion)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (isRelease) {
                extensionContainer.configure(SigningExtension, {
                    it.required = isRelease
                    it.sign project.publishing.publications.maven
                })
            }

            addInstallTaskAliases(project)
        }
    }

    protected void addInstallTaskAliases(Project project) {
        final TaskContainer taskContainer = project.tasks
        def installTask = taskContainer.findByName('install')
        def publishToSonatypeTask = taskContainer.findByName('publishToSonatype')
        def closeAndReleaseSonatypeStagingRepositoryTask = taskContainer.findByName('closeAndReleaseSonatypeStagingRepository')
        def publishToMavenLocal = taskContainer.findByName('publishToMavenLocal')
        if (publishToSonatypeTask != null && taskContainer.findByName("publish${GrailsNameUtils.getClassName(defaultClassifier)}") == null) {
            taskContainer.register("publish${GrailsNameUtils.getClassName(defaultClassifier)}", { Task task ->
                task.dependsOn([publishToSonatypeTask, closeAndReleaseSonatypeStagingRepositoryTask])
                task.setGroup('publishing')
            })
        }
        if (installTask == null) {
            taskContainer.register('install', { Task task ->
                task.dependsOn(publishToMavenLocal)
                task.setGroup('publishing')
            })
        }
    }

    protected void registerValidationTask(Project project, String taskName, Closure c) {
        project.plugins.withId(MAVEN_PUBLISH_PLUGIN_ID) {
            TaskProvider<? extends Task> publishTask = project.tasks.named("publish")

            TaskProvider validateTask = project.tasks.register(taskName, c)

            publishTask.configure {
                it.dependsOn validateTask
            }
        }
    }

    protected void doAddArtefact(Project project, MavenPublication publication) {
        publication.from project.components.java

        if (project.extensions.findByType(GrailsPublishExtension).publishTestSources) {
            publication.artifact(project.tasks.named('testSourcesJar', Jar))
        }
    }

    protected Map<String, String> getDefaultExtraArtifact(Project project) {
        if (!project.sourceSets.main.hasProperty('groovy')) {
            return null
        }

        String pluginXml = "${project.sourceSets.main.groovy.getClassesDirectory().get().getAsFile()}/META-INF/grails-plugin.xml".toString()
        new File(pluginXml).exists() ? [
                source    : pluginXml,
                classifier: getDefaultClassifier(),
                extension : 'xml'
        ] : null
    }

    protected String getDefaultClassifier() {
        'plugin'
    }

    protected validateProjectPublishable(Project project) {
        if (!project.extensions.findByType(JavaPluginExtension)) {
            throw new RuntimeException("Grails Publish Plugin requires the Java Plugin to be applied to the project.")
        }
        project.extensions.configure(JavaPluginExtension) {
            it.withJavadocJar()
            it.withSourcesJar()
        }

        final TaskContainer taskContainer = project.tasks
        taskContainer.named('javadocJar', Jar).configure { Jar task ->
            project.rootProject.logger.info("Configuring javadocJar task for project {} to include groovydoc", project.name)
            Task groovyDocTask = taskContainer.findByName('groovydoc')
            if (groovyDocTask) {
                task.dependsOn groovyDocTask

                task.duplicatesStrategy = DuplicatesStrategy.INCLUDE
                task.from groovyDocTask.outputs
            }
        }

        taskContainer.named('sourcesJar', Jar).configure { Jar task ->
            SourceSetContainer sourceSets = SourceSets.findSourceSets(project)
            task.duplicatesStrategy = DuplicatesStrategy.INCLUDE
            // don't only include main, but any source set
            task.from sourceSets.collect { it.allSource }
        }

        project.tasks.register('testSourcesJar', Jar) {
            it.dependsOn('testClasses')
            it.from project.sourceSets.test.output
            it.archiveClassifier.set('tests')
        }

        SourceSetContainer sourceSets = SourceSets.findSourceSets(project)
        Collection<SourceSet> publishedSources = sourceSets.findAll { SourceSet sourceSet ->
            (
                    project.extensions.findByType(GrailsPublishExtension).publishTestSources ||
                            sourceSet.name != SourceSet.TEST_SOURCE_SET_NAME
            ) && !sourceSet.allSource.isEmpty()
        }
        if (!publishedSources) {
            throw new RuntimeException("Cannot apply Grails Publish Plugin. Project ${project.name} does not have anything to publish.")
        }

        registerValidationTask(project, "grailsPublishValidation") {
            Task groovyDocTask = project.tasks.findByName('groovydoc')
            if (groovyDocTask) {
                if (!groovyDocTask.enabled) {
                    throw new RuntimeException("Groovydoc task is disabled. Please enable it to ensure javadoc can be published correctly with the Grails Publish Plugin.")
                }
            }
        }
    }
}

